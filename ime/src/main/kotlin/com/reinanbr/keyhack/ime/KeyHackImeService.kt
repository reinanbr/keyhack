package com.reinanbr.keyhack.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.reinanbr.keyhack.clipboard.ClipItem
import com.reinanbr.keyhack.clipboard.ClipboardHistory
import com.reinanbr.keyhack.clipboard.ClipboardStore
import com.reinanbr.keyhack.commit.ContentCommitter
import com.reinanbr.keyhack.data.KeyHackSettings
import com.reinanbr.keyhack.devtools.DevTool
import com.reinanbr.keyhack.devtools.Snippet
import com.reinanbr.keyhack.devtools.SnippetStore
import com.reinanbr.keyhack.devtools.TextTransforms
import com.reinanbr.keyhack.emoji.EmojiData
import com.reinanbr.keyhack.emoji.EmojiFont
import com.reinanbr.keyhack.ime.ui.ClipboardPanel
import com.reinanbr.keyhack.ime.ui.EditAction
import com.reinanbr.keyhack.ime.ui.EmojiPanel
import com.reinanbr.keyhack.ime.ui.EmojiStore
import com.reinanbr.keyhack.ime.ui.FloatBar
import com.reinanbr.keyhack.ime.ui.GifPanel
import com.reinanbr.keyhack.ime.ui.GifStrip
import com.reinanbr.keyhack.ime.ui.OneHandBar
import com.reinanbr.keyhack.ime.ui.StickerPanel
import com.reinanbr.keyhack.ime.ui.StripView
import com.reinanbr.keyhack.ime.ui.ToolsPanel
import com.reinanbr.keyhack.keyboard.model.Command
import com.reinanbr.keyhack.keyboard.model.Key
import com.reinanbr.keyhack.keyboard.model.KeyAction
import com.reinanbr.keyhack.keyboard.model.KeyRow
import com.reinanbr.keyhack.keyboard.model.KeyboardLayout
import com.reinanbr.keyhack.keyboard.model.ModifierController
import com.reinanbr.keyhack.keyboard.model.ShiftController
import com.reinanbr.keyhack.keyboard.model.ShiftState
import com.reinanbr.keyhack.keyboard.model.outputText
import com.reinanbr.keyhack.keyboard.view.KeyPopupView
import com.reinanbr.keyhack.keyboard.view.KeyboardView
import com.reinanbr.keyhack.media.GifItem
import com.reinanbr.keyhack.media.GifNetwork
import com.reinanbr.keyhack.media.GifProvider
import com.reinanbr.keyhack.media.GifRepository
import com.reinanbr.keyhack.stickers.StickerStore
import com.reinanbr.keyhack.suggest.Candidate
import com.reinanbr.keyhack.suggest.GlidePath
import com.reinanbr.keyhack.suggest.Suggester
import com.reinanbr.keyhack.suggest.Suggestions
import com.reinanbr.keyhack.suggest.grammar.GrammarFix
import com.reinanbr.keyhack.theme.KeyboardColors
import com.reinanbr.keyhack.theme.KeyboardThemes
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import com.reinanbr.keyhack.keyboard.model.Modifier as Mod

/**
 * KeyHack's input method service: Gboard-style typing (suggestions,
 * autocorrect, emoji, GIFs) plus Hacker's-Keyboard-style PC keys.
 *
 * Privacy invariants (see README "Threat model"):
 *  - Typed text goes only to the focused editor's InputConnection. It is never
 *    logged or sent anywhere. The only network use is GIF search, and only the
 *    text typed into the GIF search box is sent, to the provider the user chose.
 *  - Learning stores word counts on-device only, and never in password
 *    fields, when the app asks for no personalized learning, or in incognito.
 */
@Suppress("TooManyFunctions", "LargeClass")
class KeyHackImeService :
    InputMethodService(),
    KeyboardView.Listener,
    EmojiPanel.Listener,
    GifPanel.Listener,
    StickerPanel.Listener,
    ToolsPanel.Listener,
    ClipboardPanel.Listener {
    private enum class Panel { KEYBOARD, EMOJI, GIF, STICKER, TOOLS, CLIPBOARD }

    private enum class Search { NONE, EMOJI, GIF, COMMAND }

    private lateinit var settings: KeyHackSettings
    private lateinit var layouts: LayoutRepository
    private lateinit var suggester: Suggester
    private lateinit var gifs: GifRepository
    private lateinit var emojiStore: EmojiStore
    private lateinit var stickers: StickerStore
    private lateinit var clipboard: ClipboardStore
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var snippets: SnippetStore

    /** The quick-paste chip was used or dismissed for this item. */
    private var dismissedClipId = 0L
    private val stickerIo = Executors.newSingleThreadExecutor() // threads start on first use
    private val main = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var strip: StripView? = null
    private var searchResults: StripView? = null
    private var gifStrip: GifStrip? = null
    private var content: FrameLayout? = null
    private var keyboardView: KeyboardView? = null
    private var popup: KeyPopupView? = null
    private var oneHandBar: OneHandBar? = null

    // Floating mode: the column holding strip, panels and keys; its drag
    // handle; the status bar height (the column stays below it); the
    // column's margins when a drag started.
    private var keyColumn: LinearLayout? = null
    private var floatBar: FloatBar? = null
    private var floatTopInset = 0
    private var floatDragStart: Pair<Int, Int>? = null
    private var emojiPanel: EmojiPanel? = null
    private var gifPanel: GifPanel? = null
    private var toolsPanel: ToolsPanel? = null
    private var stickerPanel: StickerPanel? = null
    private var clipboardPanel: ClipboardPanel? = null
    private var emojiData: EmojiData? = null

    private var colors: KeyboardColors = KeyboardThemes.LIGHT
    private val shift = ShiftController()
    private val modifiers = ModifierController()

    private var policy = EditorPolicy.policy(0, 0)
    private var editorPackage: String? = null
    private var layer = ALPHA
    private var pcOverride: Boolean? = null
    private var incognito = false
    private var panel = Panel.KEYBOARD
    private var search = Search.NONE
    private val searchQuery = StringBuilder()

    // Composition state. The word being typed is mirrored here and shown in
    // the editor as composing text, so autocorrect can replace it in place.
    private val composing = StringBuilder()
    private var suggestions: Suggestions = Suggestions.EMPTY
    private var predictions: List<Candidate> = emptyList()
    private var stripShowsToolbar = true

    /** A grammar or agreement fix for the words just typed, shown first in the bar. */
    private var grammarFix: GrammarFix? = null

    // Delete dragged left: the text before the cursor when the drag started,
    // how much each word count deletes, and the cursor (-1 if the editor does not say).
    private var swipeBefore: String? = null
    private var swipeSpans: List<Int> = emptyList()
    private var swipeAnchor = -1
    private var swipeWords = 0

    /** Text the last word deletion removed, offered back for a few seconds. */
    private var undoDelete: String? = null
    private var previousWord: String? = null

    /** The word before [previousWord], for two-word context ("eu te" -> "amo"). */
    private var beforeWord: String? = null

    /** Up to [EARLIER_WORDS] words before [beforeWord], oldest first, for agreement ("a gente não se" -> "vê"). */
    private var earlierWords: List<String> = emptyList()

    /** A few words the models expect next together ("obrigado" -> "por me ajudar"), shown with [predictions]. */
    private var predictedPhrase: String? = null
    private var previousWordStale = true
    private var revert: Revert? = null
    private var autoSpace = false

    /** The word being composed came from a glide: kept as is on space, deleted whole by backspace. */
    private var glided = false

    /** Bumped per glide, so a late result for an older glide is dropped. */
    private var glideToken = 0
    private var lastSpaceAt = 0L
    private var hasSelection = false
    private var settingsChanged = true

    private data class Revert(val original: String, val corrected: String, val separator: String)

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                key == LEARNING_CLEAR_KEY -> suggester.clearHistory()

                // Turning history off forgets everything it kept, pinned items included.
                key == KeyHackSettings.CLIPBOARD_HISTORY && !settings.clipboardHistory -> {
                    clipboard.wipe()
                    settingsChanged = true
                }

                else -> settingsChanged = true
            }
        }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
    private val clipboardChanged: () -> Unit = {
        if (panel == Panel.CLIPBOARD) refreshClipboardPanel()
    }
    private val snippetsChanged: () -> Unit = { toolsPanel?.snippets = snippets.all() }

    private val pcMode: Boolean
        get() = pcOverride ?: (settings.layout == "pc" || editorPackage in settings.pcApps)

    /** Advanced (programming/terminal) keyboard; see [KeyHackSettings.mode]. */
    private val advanced: Boolean
        get() = pcMode || settings.advanced

    override fun onCreate() {
        super.onCreate()
        settings = KeyHackSettings(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        layouts = LayoutRepository(assets)
        suggester = Suggester(this)
        gifs = GifRepository(this)
        emojiStore = EmojiStore(this)
        stickers = StickerStore(this)
        clipboard = ClipboardStore.get(this)
        clipboard.expiryMs = settings.clipboardExpiryHours * ClipboardHistory.HOUR_MS
        clipboard.addListener(clipboardChanged)
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        snippets = SnippetStore.get(this)
        snippets.addListener(snippetsChanged)
        Thread({
            val data = runCatching { assets.open("emoji.tsv").bufferedReader().use(EmojiData::read) }.getOrNull()
            main.post {
                emojiData = data
                emojiPanel?.data = data
            }
        }, "keyhack-emoji").start()
    }

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        clipboard.removeListener(clipboardChanged)
        snippets.removeListener(snippetsChanged)
        suggester.close()
        gifs.close()
        stickerIo.shutdown()
        main.removeCallbacksAndMessages(null)
        root = null
        keyboardView = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        // The framework sets the window back to wrap its content here.
        applyWindowHeight()
    }

    /**
     * Floating, the keyboard window covers the whole screen, transparent and
     * touchable only on the keys (onComputeInsets), so the keys can sit
     * anywhere and the app shows around them. Docked, it wraps the keys.
     * Both the window and the framework's input area wrap their content by
     * default; a full-height input view alone does not make them grow.
     */
    private fun applyWindowHeight() {
        val w = window?.window ?: return
        val h = if (settings.floating) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        if (w.attributes.height != h) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
        val area = w.findViewById<View>(android.R.id.inputArea) ?: return
        val lp = area.layoutParams ?: return
        if (lp.height != h) {
            lp.height = h
            area.layoutParams = lp
        }
    }

    // ---------------------------------------------------------------- views

    override fun onCreateInputView(): View {
        val ctx = this
        val stripView = StripView(ctx).also { it.listener = StripView.Listener(::onStripItem) }
        val results = StripView(ctx).also {
            it.visibility = View.GONE
            it.listener = StripView.Listener(::onSearchResult)
        }
        val kv =
            KeyboardView(ctx).also {
                it.listener = this
            }
        val frame = FrameLayout(ctx)
        frame.addView(
            kv,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val handBar = addOneHandBar(frame, kv)
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val bar = FloatBar(ctx).also {
            it.visibility = View.GONE
            it.listener = floatListener
        }
        addRow(column, bar)
        addRow(column, stripView)
        addRow(column, results)
        val liveGifs = GifStrip(ctx, gifs).also {
            it.visibility = View.GONE
            it.listener = GifStrip.Listener(::onGifPicked)
        }
        addRow(column, liveGifs)
        addRow(column, frame)
        val overlay = KeyPopupView(ctx)
        kv.popupView = overlay
        val rootView = FrameLayout(ctx)
        rootView.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        rootView.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        // Android 15+ draws the IME navigation bar (hide-keyboard and switcher
        // buttons, gesture handle) over the bottom of the input view. Keep the
        // keys above it, or taps on the bottom row hit "hide keyboard".
        rootView.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, 0, 0, navBarInset(insets))
            floatTopInset = statusBarInset(insets)
            insets
        }
        rootView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionFloating() }
        root = rootView
        keyColumn = column
        floatBar = bar
        strip = stripView
        searchResults = results
        gifStrip = liveGifs
        content = frame
        keyboardView = kv
        oneHandBar = handBar
        popup = overlay
        emojiPanel = null
        gifPanel = null
        toolsPanel = null
        stickerPanel = null
        clipboardPanel = null
        settingsChanged = true
        panel = Panel.KEYBOARD
        return rootView
    }

    private fun addRow(column: LinearLayout, view: View) = column.addView(
        view,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )

    /** The one-handed mode column, hidden until the mode is on (see [applyOneHanded]). */
    private fun addOneHandBar(frame: FrameLayout, kv: KeyboardView): OneHandBar {
        val bar = OneHandBar(this).also {
            it.visibility = View.GONE
            it.listener = OneHandBar.Listener(::onOneHandAction)
            it.setDescriptions(
                getString(R.string.keyhack_one_hand_switch),
                getString(R.string.keyhack_one_hand_full),
                getString(R.string.keyhack_float),
            )
        }
        frame.addView(bar, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT))
        // FrameLayout stretches a match_parent child only when it has two, so the
        // bar copies the keyboard's height instead.
        kv.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = bottom - top
            if (h > 0 && bar.layoutParams.height != h) {
                bar.post { bar.layoutParams = bar.layoutParams.also { it.height = h } }
            }
        }
        return bar
    }

    private fun statusBarInset(insets: WindowInsets): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insets.getInsets(WindowInsets.Type.statusBars()).top
    } else {
        @Suppress("DEPRECATION") // The only API before Android 11.
        insets.systemWindowInsetTop
    }

    private fun navBarInset(insets: WindowInsets): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.tappableElement()).bottom
    } else {
        @Suppress("DEPRECATION") // The only API before Android 11.
        insets.systemWindowInsetBottom
    }

    private fun applySettings() {
        settingsChanged = false
        colors = KeyboardThemes.resolve(this, settings.theme, settings.customTheme)
        keyboardView?.apply {
            colors = this@KeyHackImeService.colors
            showHints = settings.keyHints
            heightScale = settings.heightPercent / PERCENT
            hapticsEnabled = settings.haptics
            vibrationMs = settings.vibrationMs
            soundEnabled = settings.sound
            longPressMillis = settings.longPressMs.toLong()
            keyPreview = settings.keyPreview
            shape = KeyboardThemes.shape(settings.theme)
        }
        oneHandBar?.applyColors(colors)
        applyFloating()
        applyOneHanded()
        strip?.colors = colors
        searchResults?.colors = colors
        popup?.colors = colors
        emojiPanel?.applyColors(colors)
        gifPanel?.applyColors(colors)
        toolsPanel?.applyColors(colors)
        clipboardPanel?.applyColors(colors)
        clipboard.expiryMs = settings.clipboardExpiryHours * ClipboardHistory.HOUR_MS
        // Floating, only the rounded key column is painted (see applyFloating).
        root?.setBackgroundColor(if (settings.floating) Color.TRANSPARENT else colors.background)
        suggester.setLanguages(languages())
    }

    /** Dictionary languages, the active subtype's language first. */
    private fun languages(): List<String> {
        val subtype =
            runCatching {
                getSystemService(InputMethodManager::class.java).currentInputMethodSubtype?.languageTag?.take(2)
            }.getOrNull()
        val enabled = settings.languages
        return if (subtype != null && subtype in enabled) listOf(subtype) + (enabled - subtype) else enabled
    }

    // ------------------------------------------------------------ lifecycle

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        configure(info, restarting)
    }

    /** Derives the field policy and resets per-field state; also used when toggles change. */
    private fun configure(info: EditorInfo, restarting: Boolean) {
        if (settingsChanged) applySettings()
        if (info.packageName != editorPackage) pcOverride = null
        editorPackage = info.packageName
        policy =
            EditorPolicy.policy(
                info.inputType,
                info.imeOptions,
                suggestionsOn = settings.suggestions,
                autocorrectOn = settings.autocorrect && !(pcMode && settings.pcAutocorrectOff),
                learningOn = settings.learning,
                autoCapsOn = settings.autoCaps,
                incognito = incognito,
                pcMode = pcMode,
            )
        hasSelection = info.initialSelStart != info.initialSelEnd
        resetComposing(finish = false)
        if (!restarting) {
            shift.reset()
            modifiers.reset()
            layer = policy.initialLayer ?: ALPHA
            closeSearch()
            showPanel(Panel.KEYBOARD)
        }
        previousWordStale = true
        revert = null
        autoSpace = false
        renderKeyboard()
        updateAutoCaps()
        showDefaultStrip()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardView?.cancelAllPresses()
        gifPanel?.release()
        suggester.save()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
        // Our own edits can call back synchronously (same-process editors, like
        // the settings screen): those are not cursor moves by the user.
        if (ownEdits > 0) return
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // The user moved the cursor away from the word being typed.
            resetComposing(finish = true)
            previousWordStale = true
            revert = null
            showDefaultStrip()
        }
        if (composing.isEmpty()) {
            main.removeCallbacks(autoCapsRunnable)
            main.post(autoCapsRunnable)
            if (grammarFix != null) {
                main.removeCallbacks(grammarStaleCheck)
                main.post(grammarStaleCheck)
            }
        }
    }

    /** The cursor moved: keep the grammar chip only if the text it fixes is still right before the cursor. */
    private val grammarStaleCheck = Runnable {
        val fix = grammarFix ?: return@Runnable
        val expected = fix.original + fix.tail
        if (currentInputConnection?.getTextBeforeCursor(expected.length, 0)?.toString() != expected) {
            grammarFix = null
            renderStrip()
        }
    }

    private val autoCapsRunnable = Runnable { updateAutoCaps() }

    private var ownEdits = 0

    /** Runs an InputConnection edit made by the keyboard itself. */
    private inline fun <T> ownEdit(block: () -> T): T {
        ownEdits++
        try {
            return block()
        } finally {
            ownEdits--
        }
    }

    // --------------------------------------------------------------- layout

    private fun currentLayout(): KeyboardLayout {
        val pc = pcMode
        val id =
            when (layer) {
                ALPHA -> if (pc) "pc" else settings.layout.takeIf { it != "pc" } ?: "qwerty"
                else -> layer
            }
        val base = layouts.get(id)
        if (pc && (layer == ALPHA || layer == "fn")) return base
        val extra = ArrayList<KeyRow>()
        if (advanced && settings.devRow && layer != "numpad" && layer != "phone") {
            layouts.devRow(
                settings.devRowKeys,
            )?.let { extra += it }
        }
        var layout = base
        if (layer == ALPHA) {
            // Symbols on the letter keys (swipe up or long-press). With a number
            // row, the top row's digit hints become code symbols too.
            if (settings.numberRow) layouts.row("number-row")?.let { extra += it }
            val common = layouts.common
            // Simple mode keeps the chat symbols (rows 2-3); advanced adds code
            // symbols on the top row.
            val hints = if (advanced) common.symbolHints else listOf(emptyList<String>()) + common.symbolHints.drop(1)
            layout = layout.withSymbolHints(hints, common.popups, replaceDigits = advanced && settings.numberRow)
            if (!advanced && settings.numberRow) layout = layout.withoutDigitHints()
        }
        return layout.withRowsOnTop(extra)
    }

    private fun renderKeyboard() {
        val kv = keyboardView ?: return
        val layout = currentLayout()
        if (kv.layout != layout) {
            kv.layout = layout
            if (layer == ALPHA && !pcMode) suggester.setKeyCenters(layout.letterCenters())
        }
        kv.glideEnabled = glideAllowed()
        kv.activeLayer = layer
        kv.enterLabel = enterLabel()
        kv.spaceLabel = if (layer == ALPHA && !pcMode) {
            languages().joinToString(
                " · ",
            ) { it.uppercase(Locale.ROOT) }
        } else {
            ""
        }
        renderState()
    }

    private fun renderState() {
        keyboardView?.apply {
            shiftState = shift.displayState
            modifierStates = modifiers.snapshot()
        }
    }

    private fun enterLabel(): String {
        val res =
            when ((policy.enter as? EnterBehavior.Action)?.actionId) {
                EditorInfo.IME_ACTION_GO -> R.string.keyhack_enter_go
                EditorInfo.IME_ACTION_SEARCH -> R.string.keyhack_enter_search
                EditorInfo.IME_ACTION_SEND -> R.string.keyhack_enter_send
                EditorInfo.IME_ACTION_NEXT -> R.string.keyhack_enter_next
                EditorInfo.IME_ACTION_PREVIOUS -> R.string.keyhack_enter_previous
                EditorInfo.IME_ACTION_DONE -> R.string.keyhack_enter_done
                else -> return "⏎"
            }
        return getString(res)
    }

    // ---------------------------------------------------------------- keys

    override fun onKey(key: Key, action: KeyAction) {
        if (search != Search.NONE && onSearchKey(key, action)) return
        val ic = currentInputConnection ?: return
        when (action) {
            is KeyAction.Character -> onCharacter(key.outputText(shift.isShifted) ?: action.text)

            KeyAction.Space -> onSpace()

            // Touch goes through onShiftDown/onShiftUp; this is a tap from accessibility.
            KeyAction.Shift -> shift.onShiftTap(SystemClock.uptimeMillis())

            KeyAction.Delete -> onDelete()

            KeyAction.Enter -> onEnter()

            is KeyAction.Layer -> switchLayer(action.target)

            is KeyAction.Mod -> modifiers.onTap(action.modifier, SystemClock.uptimeMillis())

            is KeyAction.Code -> {
                commitComposing(autocorrect = false)
                KeySender.send(ic, action.keyCode, currentMods())
                afterKeySent()
            }

            is KeyAction.Cmd -> onCommand(action.command)
        }
        renderState()
    }

    override fun onPopupText(text: String) {
        if (search != Search.NONE) {
            appendSearch(text)
            return
        }
        onCharacter(text)
        renderState()
    }

    override fun onShiftDown() {
        shift.onShiftDown(SystemClock.uptimeMillis())
        renderState()
    }

    override fun onShiftUp() {
        shift.onShiftUp()
        renderState()
    }

    /** Held Delete: letters first, then whole words once it has been held a while. */
    override fun onRepeat(key: Key, count: Int) {
        val words = key.action == KeyAction.Delete && count >= WORD_DELETE_AFTER && composing.isEmpty() &&
            !policy.rawKeys && !modifiers.anyActive && !hasSelection
        if (!words) {
            onKey(key, key.action)
            return
        }
        if ((count - WORD_DELETE_AFTER) % WORD_DELETE_EVERY != 0) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(SWIPE_LOOKBACK, 0)?.toString() ?: return
        val len = WordSpans.deleteLengths(before, 1).firstOrNull() ?: return
        ic.deleteSurroundingText(len, 0)
        previousWordStale = true
        revert = null
        updateAutoCaps()
    }

    override fun onDeleteSwipe(words: Int) {
        val ic = currentInputConnection ?: return
        if (policy.rawKeys || modifiers.anyActive) return
        if (swipeBefore == null) {
            commitComposing(autocorrect = false)
            val before = ic.getTextBeforeCursor(SWIPE_LOOKBACK, 0)?.toString() ?: return
            swipeBefore = before
            swipeSpans = WordSpans.deleteLengths(before, MAX_SWIPE_WORDS)
            swipeAnchor = ic.getExtractedText(ExtractedTextRequest(), 0)
                ?.takeIf { it.selectionStart == it.selectionEnd && it.selectionStart >= 0 }
                ?.let { it.startOffset + it.selectionEnd } ?: -1
        }
        val before = swipeBefore ?: return
        swipeWords = words.coerceAtMost(swipeSpans.size)
        val len = if (swipeWords == 0) 0 else swipeSpans[swipeWords - 1]
        // Highlight what will go, like a selection; the editor shows it.
        if (swipeAnchor >= 0) ownEdit { ic.setSelection(swipeAnchor - len, swipeAnchor) }
        val preview = before.takeLast(len).trim().replace('\n', ' ')
        strip?.show(
            listOf(StripView.Item("", if (len == 0) "⌫" else "⌫  " + preview.takeLast(PREVIEW_CHARS), dim = true)),
        )
    }

    override fun onDeleteSwipeEnd() {
        val ic = currentInputConnection
        val before = swipeBefore
        val n = swipeWords
        swipeBefore = null
        swipeWords = 0
        if (ic == null || before == null) return
        if (n == 0) {
            if (swipeAnchor >= 0) ownEdit { ic.setSelection(swipeAnchor, swipeAnchor) }
            renderStrip()
            return
        }
        val len = swipeSpans[n - 1]
        ownEdit {
            // The selection made while dragging is replaced; without one, delete before the cursor.
            if (swipeAnchor >= 0) ic.commitText("", 1) else ic.deleteSurroundingText(len, 0)
        }
        undoDelete = before.takeLast(len)
        main.removeCallbacks(clearUndo)
        main.postDelayed(clearUndo, UNDO_MS)
        previousWordStale = true
        revert = null
        autoSpace = false
        updateAutoCaps()
        showDefaultStrip()
    }

    private val clearUndo = Runnable {
        undoDelete = null
        renderStrip()
    }

    private fun undoWordDelete() {
        val text = undoDelete ?: return
        undoDelete = null
        currentInputConnection?.commitText(text, 1)
        previousWordStale = true
        updateAutoCaps()
        showDefaultStrip()
    }

    // ----------------------------------------------------------- glide typing

    private fun glideAllowed() = settings.glide && layer == ALPHA && !pcMode && policy.suggestions && !policy.rawKeys

    override fun onGlide(xs: FloatArray, ys: FloatArray, aspect: Float) {
        val ic = currentInputConnection ?: return
        if (!glideAllowed() || search != Search.NONE || modifiers.anyActive) return
        // The word before goes first: a glided one as it is, a typed one as space would commit it.
        if (composing.isNotEmpty()) commitComposing(autocorrect = !glided)
        clearGrammarFix()
        revert = null
        undoDelete = null
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val space = before.isNotEmpty() && (before[0].isLetterOrDigit() || before[0] in SPACE_AFTER)
        if (space) ownEdit { ic.commitText(" ", 1) }
        autoSpace = false
        val token = ++glideToken
        suggester.glide(GlidePath(xs, ys, aspect), previousWord(), beforeWord) { found ->
            if (token == glideToken && composing.isEmpty()) showGlided(found, space)
        }
    }

    private fun showGlided(found: List<Candidate>, addedSpace: Boolean) {
        val ic = currentInputConnection ?: return
        if (found.isEmpty()) {
            // Nothing fits: take back the space added for the word.
            if (addedSpace && ic.getTextBeforeCursor(1, 0)?.toString() == " ") ic.deleteSurroundingText(1, 0)
            showMessage(getString(R.string.keyhack_glide_none))
            return
        }
        val words = found.map { glideCase(it.word) }.distinct()
        composing.append(words[0])
        glided = true
        ownEdit { ic.setComposingText(composing, 1) }
        shift.onCharacterCommitted()
        renderState()
        suggestions = Suggestions(words[0], true, words.map { Candidate(it, 0f, Candidate.Kind.CORRECTION) }, null)
        stripShowsToolbar = false
        renderStrip()
    }

    /** Shift capitalizes a glided word, caps lock uppercases it; otherwise the dictionary's form ("I", "Brasil"). */
    private fun glideCase(word: String): String = when {
        shift.state == ShiftState.LOCKED -> word.uppercase(Locale.getDefault())
        shift.isShifted -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        else -> word
    }

    override fun onCursorSwipe(steps: Int) {
        val ic = currentInputConnection ?: return
        commitComposing(autocorrect = false)
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val mods = currentMods()
        repeat(kotlin.math.abs(steps)) { KeySender.send(ic, code, mods) }
        revert = null
    }

    private fun currentMods() = KeySender.Mods(
        shift = shift.isUserShift,
        ctrl = modifiers.isActive(Mod.CTRL),
        alt = modifiers.isActive(Mod.ALT),
        meta = modifiers.isActive(Mod.META),
    )

    private fun afterKeySent() {
        modifiers.onKeySent()
        if (shift.state == ShiftState.ONCE || shift.isHeld) shift.onCharacterCommitted()
        revert = null
        autoSpace = false
        // The key (Ctrl+A, an arrow...) changed the text or the cursor, and the word being typed was
        // committed: suggestions for it are stale, and picking one would type over a selection.
        previousWordStale = true
        showDefaultStrip()
    }

    private fun switchLayer(target: String) {
        layer = if (target == "alpha") ALPHA else target
        if (layer != ALPHA) shift.reset()
        renderKeyboard()
    }

    private fun isWordChar(text: String): Boolean {
        val cp = text.codePointAt(0)
        if (Character.isLetter(cp)) return true
        return composing.isNotEmpty() && (text == "'" || text == "’" || text == "-")
    }

    private fun onCharacter(text: String) {
        val ic = currentInputConnection ?: return
        if (modifiers.anyActive) {
            // Ctrl/Alt/Meta + key: a real key event, as from a hardware keyboard.
            commitComposing(autocorrect = false)
            if (!KeySender.sendChar(ic, text, currentMods())) ic.commitText(text, 1)
            afterKeySent()
            return
        }
        revert = null
        undoDelete = null
        if (policy.suggestions && !policy.rawKeys && isWordChar(text)) {
            if (composing.isEmpty()) {
                autoSpace = false
                clearGrammarFix()
            }
            composing.append(text)
            glided = false
            ownEdit { ic.setComposingText(composing, 1) }
            shift.onCharacterCommitted()
            requestSuggestions()
            return
        }
        val hadWord = composing.isNotEmpty()
        commitComposing(autocorrect = text in AUTOCORRECT_TRIGGERS)
        if (autoSpace && text in ATTACHING_PUNCTUATION) {
            // "word ." -> "word. " after picking a suggestion.
            ic.deleteSurroundingText(1, 0)
            ic.commitText("$text ", 1)
        } else {
            ic.commitText(text, 1)
        }
        autoSpace = false
        shift.onCharacterCommitted()
        if (text in SENTENCE_END) {
            forgetWords()
        } else if (!hadWord) {
            previousWordStale = true
        }
        if (layer != ALPHA && text == "'" && layer.startsWith("symbols")) switchLayer(ALPHA)
        updateAutoCaps()
        showDefaultStrip()
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        if (modifiers.anyActive) {
            commitComposing(autocorrect = false)
            KeySender.send(ic, KeyEvent.KEYCODE_SPACE, currentMods())
            afterKeySent()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (composing.isNotEmpty()) {
            commitComposing(autocorrect = true, separator = " ")
        } else if (settings.doubleSpacePeriod && policy.kind == FieldKind.TEXT && now - lastSpaceAt < DOUBLE_SPACE_MS &&
            !autoSpace &&
            endsWithWordAndSpace(
                ic,
            )
        ) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            forgetWords()
            revert = null
        } else {
            ic.commitText(" ", 1)
            revert = null
        }
        autoSpace = false
        lastSpaceAt = now
        if (layer.startsWith("symbols")) switchLayer(ALPHA)
        updateAutoCaps()
        showDefaultStrip()
    }

    private fun endsWithWordAndSpace(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(2, 0) ?: return false
        return before.length == 2 && before[1] == ' ' && Character.isLetterOrDigit(before[0])
    }

    private fun onDelete() {
        val ic = currentInputConnection ?: return
        if (modifiers.anyActive) {
            commitComposing(autocorrect = false)
            KeySender.send(ic, KeyEvent.KEYCODE_DEL, currentMods())
            afterKeySent()
            return
        }
        autoSpace = false
        clearGrammarFix()
        val r = revert
        revert = null
        if (r != null && composing.isEmpty()) {
            val expected = r.corrected + r.separator
            if (ic.getTextBeforeCursor(expected.length, 0)?.toString() == expected) {
                // Backspace right after an autocorrection restores what was typed.
                ic.deleteSurroundingText(expected.length, 0)
                ic.commitText(r.original + r.separator, 1)
                suggester.accept(r.original)
                return
            }
        }
        when {
            glided && composing.isNotEmpty() -> {
                // A glided word goes in one backspace, as it came.
                resetComposing(finish = false)
                ownEdit { ic.setComposingText("", 1) }
                updateAutoCaps()
                showDefaultStrip()
            }

            composing.isNotEmpty() -> {
                val end = composing.length
                composing.delete(composing.offsetByCodePoints(end, -1), end)
                ownEdit { ic.setComposingText(composing, 1) }
                if (composing.isEmpty()) showDefaultStrip() else requestSuggestions()
            }

            policy.rawKeys -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)

            hasSelection -> ic.commitText("", 1)

            else -> {
                // Delete a whole grapheme (emoji with modifiers, flags, accents).
                val before = ic.getTextBeforeCursor(GRAPHEME_LOOKBACK, 0)?.toString().orEmpty()
                if (before.isEmpty()) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                } else {
                    val it = BreakIterator.getCharacterInstance()
                    it.setText(before)
                    val start = it.preceding(before.length).coerceAtLeast(0)
                    ic.deleteSurroundingText(before.length - start, 0)
                }
                previousWordStale = true
            }
        }
        if (composing.isEmpty()) updateAutoCaps()
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        if (modifiers.anyActive) {
            commitComposing(autocorrect = false)
            KeySender.send(ic, KeyEvent.KEYCODE_ENTER, currentMods())
            afterKeySent()
            return
        }
        commitComposing(autocorrect = true)
        autoSpace = false
        revert = null
        forgetWords()
        when (val enter = policy.enter) {
            is EnterBehavior.Action -> ic.performEditorAction(enter.actionId)

            EnterBehavior.NewLine -> if (policy.rawKeys) {
                sendDownUpKeyEvents(
                    KeyEvent.KEYCODE_ENTER,
                )
            } else {
                ic.commitText("\n", 1)
            }
        }
        updateAutoCaps()
        showDefaultStrip()
    }

    private fun onCommand(command: Command) {
        when (command) {
            Command.EMOJI -> {
                commitComposing(autocorrect = false)
                showPanel(Panel.EMOJI)
            }

            Command.GIF -> openGifs()

            Command.TOOLS -> {
                commitComposing(autocorrect = false)
                showPanel(Panel.TOOLS)
            }

            Command.SETTINGS -> openSettings()

            Command.TOGGLE_PC -> togglePc()

            Command.NEXT_IME -> switchToNextInputMethod(false)

            Command.IME_PICKER -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()

            Command.CLIPBOARD -> openClipboard()
        }
    }

    private fun togglePc() {
        commitComposing(autocorrect = false)
        pcOverride = !pcMode
        layer = ALPHA
        modifiers.reset()
        shift.reset()
        currentInputEditorInfo?.let { configure(it, true) }
        renderKeyboard()
        showDefaultStrip()
    }

    /** Simple (chat) <-> advanced (programming/terminal). Saved for every app. */
    private fun toggleMode() {
        commitComposing(autocorrect = false)
        val toAdvanced = !advanced
        settings.edit {
            putString(
                KeyHackSettings.MODE,
                if (toAdvanced) KeyHackSettings.MODE_ADVANCED else KeyHackSettings.MODE_SIMPLE,
            )
        }
        // Leaving advanced also leaves the PC layout, even in a terminal app.
        pcOverride = if (toAdvanced) null else false
        layer = ALPHA
        modifiers.reset()
        shift.reset()
        currentInputEditorInfo?.let { configure(it, true) }
        renderKeyboard()
        showDefaultStrip()
        showMessage(getString(if (toAdvanced) R.string.keyhack_msg_mode_advanced else R.string.keyhack_msg_mode_simple))
    }

    private fun openSettings() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    // ------------------------------------------------------- composition

    private fun resetComposing(finish: Boolean) {
        val had = composing.isNotEmpty()
        composing.setLength(0)
        glided = false
        suggestions = Suggestions.EMPTY
        suggester.cancel()
        if (finish && had) ownEdit { currentInputConnection?.finishComposingText() }
    }

    /** Commits the word being typed, replaced by its autocorrection when [autocorrect]. */
    private fun commitComposing(autocorrect: Boolean, separator: String = "") {
        val ic = currentInputConnection
        if (composing.isEmpty()) {
            if (separator.isNotEmpty()) ic?.commitText(separator, 1)
            return
        }
        val typed = composing.toString()
        val prev = previousWord()
        var out = typed
        if (autocorrect && policy.autocorrect) {
            val s = if (suggestions.typed == typed) {
                suggestions
            } else {
                suggester.suggestNow(typed, prev, beforeWord, earlierWords)
            }
            s?.autoCorrection?.let { out = it }
        }
        // Clear state before touching the editor: commitText may call back into
        // onUpdateSelection before it returns.
        resetComposing(finish = false)
        ownEdit { ic?.commitText(out + separator, 1) }
        revert = if (out != typed) Revert(typed, out, separator) else null
        if (policy.learning) suggester.learn(out, prev)
        pushWord(prev, out)
    }

    /** [word] was just written after [prev]: the context words move along by one. */
    private fun pushWord(prev: String?, word: String) {
        earlierWords = if (prev == null) {
            emptyList()
        } else {
            (earlierWords + listOfNotNull(beforeWord)).takeLast(EARLIER_WORDS)
        }
        beforeWord = prev
        previousWord = word
        previousWordStale = false
    }

    /** A new sentence (or field): no context words. */
    private fun forgetWords() {
        previousWord = null
        beforeWord = null
        earlierWords = emptyList()
    }

    /** The word before the cursor (and, in [beforeWord], the one before it), re-read from the editor when stale. */
    private fun previousWord(): String? {
        if (!previousWordStale) return previousWord
        previousWordStale = false
        val before = currentInputConnection?.getTextBeforeCursor(PREVIOUS_LOOKBACK, 0)?.toString().orEmpty()
        val text = before.dropLast(composing.length)
        val words = LAST_WORDS.find(text)?.takeIf { !text.trimEnd().endsWithAny(SENTENCE_END) }
        previousWord = words?.groupValues?.get(2)
        beforeWord = words?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        earlierWords = if (words == null || beforeWord == null) emptyList() else wordsBefore(text, words.range.first)
        return previousWord
    }

    /** Up to [EARLIER_WORDS] words that end right before [end] in [text], separated only by spaces, oldest first. */
    private fun wordsBefore(text: String, end: Int): List<String> {
        val out = ArrayList<String>()
        var rest = text.substring(0, end)
        while (out.size < EARLIER_WORDS) {
            val m = WORD_AT_END.find(rest) ?: break
            out.add(0, m.groupValues[1])
            rest = rest.substring(0, m.range.first)
        }
        return out
    }

    private fun String.endsWithAny(set: Set<String>) = set.any { endsWith(it) }

    private fun requestSuggestions() {
        val typed = composing.toString()
        val prev = previousWord()
        suggester.suggest(typed, prev, beforeWord, earlierWords) { s ->
            if (composing.toString() != s.typed) return@suggest
            suggestions = s
            stripShowsToolbar = false
            renderStrip()
        }
    }

    private fun updateAutoCaps() {
        if (!policy.autoCaps || composing.isNotEmpty()) {
            if (!policy.autoCaps) shift.applyAutoCaps(false)
            renderState()
            return
        }
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        shift.applyAutoCaps(caps != 0)
        renderState()
    }

    // ---------------------------------------------------------------- strip

    private fun showDefaultStrip() {
        predictions = emptyList()
        predictedPhrase = null
        stripShowsToolbar = true
        grammarFix = null
        renderStrip()
        requestGrammarCheck()
        val prev = previousWord
        if (policy.suggestions && composing.isEmpty() && prev != null && !previousWordStale) {
            suggester.predict(prev, beforeWord, earlierWords) { p, phrase ->
                if (composing.isNotEmpty() || p.isEmpty()) return@predict
                predictions = p
                predictedPhrase = phrase
                stripShowsToolbar = false
                renderStrip()
            }
        }
    }

    private fun grammarAllowed(): Boolean =
        settings.grammar && policy.suggestions && policy.kind == FieldKind.TEXT && !policy.rawKeys

    private fun requestGrammarCheck() {
        if (!grammarAllowed() || composing.isNotEmpty() || search != Search.NONE) {
            suggester.cancelGrammar()
            return
        }
        val text = currentInputConnection?.getTextBeforeCursor(GRAMMAR_LOOKBACK, 0)?.toString()
        if (text.isNullOrEmpty()) return
        suggester.checkGrammar(text) { fix ->
            if (fix == null || composing.isNotEmpty() || search != Search.NONE) return@checkGrammar
            grammarFix = fix
            stripShowsToolbar = false
            renderStrip()
        }
    }

    private fun clearGrammarFix() {
        suggester.cancelGrammar()
        if (grammarFix == null) return
        grammarFix = null
        renderStrip()
    }

    /** Replaces the words the fix is about, if they are still right before the cursor. */
    private fun applyGrammarFix() {
        val fix = grammarFix ?: return
        val ic = currentInputConnection ?: return
        grammarFix = null
        val expected = fix.original + fix.tail
        if (ic.getTextBeforeCursor(expected.length, 0)?.toString() == expected) {
            ownEdit {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(expected.length, 0)
                ic.commitText(fix.replacement + fix.tail, 1)
                ic.endBatchEdit()
            }
        }
        previousWordStale = true
        revert = null
        autoSpace = false
        updateAutoCaps()
        showDefaultStrip()
    }

    private fun renderStrip() {
        val s = strip ?: return
        if (search != Search.NONE) {
            renderSearchStrip(s)
            return
        }
        val undo = undoDelete
        if (undo != null && composing.isEmpty()) {
            s.show(
                listOf(
                    StripView.Item(
                        "undo_delete",
                        "↶  " + getString(R.string.keyhack_undo),
                        weight = UNDO_CHIP_WEIGHT,
                        accent = true,
                    ),
                    StripView.Item("", undo.trim().replace('\n', ' ').takeLast(PREVIEW_CHARS), weight = 3f, dim = true),
                ),
            )
            return
        }
        renderWordStrip(s)
    }

    private fun renderSearchStrip(s: StripView) {
        val hint = getString(
            when (search) {
                Search.GIF -> R.string.keyhack_gif_search_hint
                Search.COMMAND -> R.string.keyhack_palette_hint
                else -> R.string.keyhack_emoji_search_hint
            },
        )
        s.show(
            listOf(
                StripView.Item("search_back", "‹", weight = 0.6f, large = true),
                StripView.Item(
                    "search_field",
                    if (searchQuery.isEmpty()) "🔍 $hint" else "🔍 $searchQuery▏",
                    weight = 4f,
                    dim = searchQuery.isEmpty(),
                ),
                StripView.Item("search_clear", "✕", weight = 0.6f),
            ),
        )
    }

    private fun renderWordStrip(s: StripView) {
        val words = if (composing.isNotEmpty()) suggestionWords() else predictions.map { WordSlot(it.word, false) }
        val fix = grammarFix
        if (fix != null && composing.isEmpty() && !stripShowsToolbar) {
            // The fix first (✓ marks it), then the best next words.
            val items = ArrayList<StripView.Item>()
            items += StripView.Item("toolbar", "‹", weight = 0.5f, large = true, dim = true)
            items += StripView.Item("g", "✓ " + fix.replacement, weight = GRAMMAR_CHIP_WEIGHT, accent = true)
            for (w in words.take(2)) items += StripView.Item("w:" + w.word, w.label, weight = 1.3f)
            s.show(items, withDividers = true)
            return
        }
        val phrase = predictedPhrase
        if (phrase != null && composing.isEmpty() && !stripShowsToolbar) {
            // The phrase, then the best next words it does not start with.
            val first = phrase.substringBefore(' ')
            val items = ArrayList<StripView.Item>()
            items += StripView.Item("toolbar", "‹", weight = 0.5f, large = true, dim = true)
            items += StripView.Item("ph", phrase, weight = PHRASE_CHIP_WEIGHT)
            for (w in words.filter { it.word != first }.take(2)) {
                items += StripView.Item("w:" + w.word, w.label, weight = 1.3f)
            }
            s.show(items, withDividers = true)
            return
        }
        if (stripShowsToolbar || words.isEmpty()) {
            val clip = if (composing.isEmpty()) freshClip() else null
            if (clip != null) {
                // Just copied something: offer to paste it, as the first thing in the bar.
                s.show(
                    listOf(
                        StripView.Item(
                            "clip_paste",
                            "📋  " + clip.text.take(CHIP_CHARS).replace('\n', ' '),
                            weight = 4.4f,
                        ),
                        StripView.Item("clip_dismiss", "✕", weight = 0.6f, dim = true),
                    ),
                )
                return
            }
            s.show(toolbarItems())
            return
        }
        val items = ArrayList<StripView.Item>()
        items += StripView.Item("toolbar", "‹", weight = 0.5f, large = true, dim = true)
        snippetForComposing()?.let {
            items += StripView.Item(
                "snip",
                "✎ " + it.text.replace('\n', ' '),
                weight = SNIPPET_CHIP_WEIGHT,
                accent = true,
            )
        }
        for (w in words) items += StripView.Item("w:" + w.word, w.label, weight = 1.5f, bold = w.bold)
        // Emoji for the word being typed, or after a space for the word just finished.
        val emojiWord = if (composing.isNotEmpty()) composing.toString() else previousWord()
        val emojiLimit = if (composing.isNotEmpty()) EMOJI_WHILE_TYPING else EMOJI_AFTER_WORD
        for (e in emojiFor(emojiWord, emojiLimit)) {
            items += StripView.Item("x:$e", e, weight = EMOJI_SLOT_WEIGHT, emoji = true)
        }
        s.show(items, withDividers = true)
    }

    /**
     * Up to [limit] emoji for [word] ("amor" -> ❤️ 😍 🥰), the ones the user
     * sends often first, in the user's skin tone.
     */
    private fun emojiFor(word: String?, limit: Int): List<String> {
        if (word.isNullOrEmpty() || !policy.suggestions) return emptyList()
        val found =
            emojiData?.forWord(word, EMOJI_CANDIDATES).orEmpty().filter { EmojiFont.canDraw(this, it.value) }
        if (found.isEmpty()) return emptyList()
        val recents = emojiStore.recents()
        val toned = found.map { it.withTone(emojiStore.tone(it.value)) }
        // Stable: emoji never sent keep the chat list's order.
        return toned.sortedBy { e -> recents.indexOf(e).let { if (it < 0) Int.MAX_VALUE else it } }.take(limit)
    }

    /** Keeps the typed word and adds the emoji after it: "amor ❤️ "; after a space, just the emoji. */
    private fun pickEmojiSuggestion(emoji: String) {
        val ic = currentInputConnection ?: return
        val word = composing.toString()
        val prev = previousWord()
        resetComposing(finish = false)
        ownEdit { ic.commitText(if (word.isEmpty()) "$emoji " else "$word $emoji ", 1) }
        if (policy.learning && word.isNotEmpty()) suggester.learn(word, prev)
        emojiStore.addRecent(emoji)
        forgetWords()
        previousWordStale = false
        revert = null
        autoSpace = true
        updateAutoCaps()
        showDefaultStrip()
    }

    private data class WordSlot(val word: String, val bold: Boolean, val label: String = word)

    /** Three slots, Gboard style: the autocorrection (or best guess) in the middle. */
    private fun suggestionWords(): List<WordSlot> {
        val s = suggestions
        if (s.typed.isEmpty()) return emptyList()
        val others = s.candidates.drop(1).map { it.word }
        val auto = s.autoCorrection
        val typedLabel = if (s.typedIsWord) s.typed else "“${s.typed}”"
        val slots =
            when {
                auto != null -> listOf(
                    WordSlot(s.typed, false, typedLabel),
                    WordSlot(auto, true),
                    others.firstOrNull { it != auto }?.let { WordSlot(it, false) },
                )

                s.typedIsWord -> listOf(
                    others.getOrNull(0)?.let { WordSlot(it, false) },
                    WordSlot(s.typed, true),
                    others.getOrNull(1)?.let { WordSlot(it, false) },
                )

                else -> listOf(
                    WordSlot(s.typed, false, typedLabel),
                    others.getOrNull(0)?.let { WordSlot(it, false) },
                    others.getOrNull(1)?.let { WordSlot(it, false) },
                )
            }
        return slots.filterNotNull()
    }

    private fun toolbarItems(): List<StripView.Item> = buildList {
        add(StripView.Item("emoji", "☺", large = true))
        if (gifAllowed()) add(StripView.Item("gif", "GIF", bold = true))
        add(StripView.Item("sticker", "🖼", large = true))
        add(StripView.Item("clip", "📋", large = true))
        add(StripView.Item("tools", "</>", bold = true))
        add(StripView.Item("mode", "DEV", bold = true, accent = advanced))
        if (advanced) add(StripView.Item("pc", if (pcMode) "ABC" else "PC", bold = true, accent = pcMode))
        add(StripView.Item("onehand", "✋", accent = settings.oneHanded != KeyHackSettings.ONE_HANDED_OFF))
        add(StripView.Item("incognito", "🕶", accent = incognito))
        add(StripView.Item("settings", "⚙", large = true))
        add(StripView.Item("hide", "⌄", large = true))
    }

    private fun onStripItem(id: String) {
        when {
            id.startsWith("w:") -> pickWord(id.substring(2))

            id == "ph" -> pickPhrase()

            id.startsWith("x:") -> pickEmojiSuggestion(id.substring(2))

            id == "g" -> applyGrammarFix()

            id == "undo_delete" -> undoWordDelete()

            id == "snip" -> snippetForComposing()?.let { expandSnippet(it) }

            id == "toolbar" -> {
                stripShowsToolbar = true
                renderStrip()
            }

            id == "emoji" -> onCommand(Command.EMOJI)

            id == "gif" -> onCommand(Command.GIF)

            id == "sticker" -> openStickers()

            id == "clip" -> openClipboard()

            id == "clip_paste" -> freshClip()?.let { pasteClip(it) }

            id == "clip_dismiss" -> {
                dismissedClipId = freshClip()?.id ?: dismissedClipId
                renderStrip()
            }

            id == "tools" -> onCommand(Command.TOOLS)

            id == "pc" -> togglePc()

            id == "mode" -> toggleMode()

            id == "onehand" -> toggleOneHanded()

            id == "float" -> toggleFloating()

            id == "incognito" -> {
                // configure() forgets the word being typed; finish it in the editor first, under the
                // old policy (a word typed in incognito is still not learned), or the next paste or
                // pick would replace it.
                commitComposing(autocorrect = false)
                incognito = !incognito
                currentInputEditorInfo?.let { configure(it, true) }
                showMessage(
                    getString(if (incognito) R.string.keyhack_msg_incognito_on else R.string.keyhack_msg_incognito_off),
                )
            }

            id == "settings" -> openSettings()

            id == "hide" -> requestHideSelf(0)

            id == "search_back" -> closeSearchToPanel()

            id == "search_clear" -> {
                searchQuery.setLength(0)
                onSearchChanged()
            }
        }
    }

    private fun pickWord(word: String) {
        val ic = currentInputConnection ?: return
        val prev = previousWord()
        if (composing.isNotEmpty()) {
            val typed = composing.toString()
            if (word == typed) suggester.accept(typed)
        }
        resetComposing(finish = false)
        ownEdit { ic.commitText("$word ", 1) }
        if (policy.learning) suggester.learn(word, prev)
        pushWord(prev, word)
        revert = null
        autoSpace = true
        updateAutoCaps()
        showDefaultStrip()
    }

    /** Writes the suggested phrase; each of its words counts as picked, for learning and context. */
    private fun pickPhrase() {
        val text = predictedPhrase ?: return
        val ic = currentInputConnection ?: return
        var prev = previousWord()
        resetComposing(finish = false)
        ownEdit { ic.commitText("$text ", 1) }
        for (w in text.split(' ')) {
            if (policy.learning) suggester.learn(w, prev)
            pushWord(prev, w)
            prev = w
        }
        revert = null
        autoSpace = true
        updateAutoCaps()
        showDefaultStrip()
    }

    private val hideMessage = Runnable { renderStrip() }

    private fun showMessage(text: String) {
        strip?.show(listOf(StripView.Item("", text, dim = true)))
        main.removeCallbacks(hideMessage)
        main.postDelayed(hideMessage, MESSAGE_MS)
    }

    // --------------------------------------------------------------- panels

    private fun showPanel(p: Panel) {
        val frame = content ?: return
        val kv = keyboardView ?: return
        val height = kv.height.takeIf { it > 0 } ?: kv.measuredHeight
        if (panel == Panel.GIF && p != Panel.GIF) gifPanel?.release()
        panel = p
        for (i in 0 until frame.childCount) frame.getChildAt(i).visibility = View.GONE
        val view: View =
            when (p) {
                Panel.KEYBOARD -> kv

                Panel.EMOJI -> emojiPanel ?: EmojiPanel(this, emojiStore).also {
                    it.listener = this
                    it.data = emojiData
                    emojiPanel = it
                    frame.addView(it)
                }

                Panel.GIF -> gifPanel ?: GifPanel(this, gifs).also {
                    it.listener = this
                    gifPanel = it
                    frame.addView(it)
                }

                Panel.STICKER -> stickerPanel ?: StickerPanel(this, stickers).also {
                    it.listener = this
                    stickerPanel = it
                    frame.addView(it)
                }

                Panel.TOOLS -> toolsPanel ?: ToolsPanel(this).also {
                    it.listener = this
                    it.snippets = snippets.all()
                    toolsPanel = it
                    frame.addView(it)
                }

                Panel.CLIPBOARD -> clipboardPanel ?: ClipboardPanel(this).also {
                    it.listener = this
                    clipboardPanel = it
                    frame.addView(it)
                }
            }
        if (view !== kv) {
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height.coerceAtLeast(MIN_PANEL_PX),
            )
            emojiPanel?.applyColors(colors)
            gifPanel?.applyColors(colors)
            toolsPanel?.applyColors(colors)
            stickerPanel?.applyColors(colors)
            clipboardPanel?.applyColors(colors)
        }
        view.visibility = View.VISIBLE
        afterPanelShown(p)
    }

    private fun afterPanelShown(p: Panel) {
        when (p) {
            Panel.EMOJI -> emojiPanel?.refresh()

            Panel.STICKER -> {
                stickerPanel?.emoji = stickerEmoji()
                stickerPanel?.refresh()
            }

            Panel.KEYBOARD -> applyOneHanded()

            Panel.CLIPBOARD -> refreshClipboardPanel()

            else -> Unit
        }
        strip?.visibility = if (p == Panel.KEYBOARD) View.VISIBLE else View.GONE
    }

    /** Narrows the keyboard to one side, with [OneHandBar] in the space left over. */
    private fun applyOneHanded() {
        val kv = keyboardView ?: return
        val bar = oneHandBar ?: return
        // A floating keyboard is already small and movable.
        val side = if (settings.floating) KeyHackSettings.ONE_HANDED_OFF else settings.oneHanded
        val kvParams = kv.layoutParams as FrameLayout.LayoutParams
        val barParams = bar.layoutParams as FrameLayout.LayoutParams
        if (side == KeyHackSettings.ONE_HANDED_OFF) {
            kvParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            kvParams.gravity = Gravity.NO_GRAVITY
            bar.visibility = View.GONE
        } else {
            val full = resources.displayMetrics.widthPixels
            val keys = (full * ONE_HAND_FRACTION).toInt()
            val left = side == "left"
            kvParams.width = keys
            kvParams.gravity = if (left) Gravity.START else Gravity.END
            barParams.width = full - keys
            barParams.gravity = if (left) Gravity.END else Gravity.START
            bar.visibility = if (panel == Panel.KEYBOARD) View.VISIBLE else View.GONE
        }
        kv.layoutParams = kvParams
        bar.layoutParams = barParams
    }

    private fun onOneHandAction(action: String) {
        val side = settings.oneHanded
        if (action == OneHandBar.FLOAT) {
            toggleFloating()
            return
        }
        val next =
            when (action) {
                OneHandBar.SWITCH_SIDE -> if (side == "left") "right" else "left"
                else -> KeyHackSettings.ONE_HANDED_OFF
            }
        settings.edit { putString(KeyHackSettings.ONE_HANDED, next) }
        applyOneHanded()
    }

    /** Toolbar button: one-handed on the right (most people's thumb) or back to full width. */
    private fun toggleOneHanded() {
        val next = if (settings.oneHanded == KeyHackSettings.ONE_HANDED_OFF) "right" else KeyHackSettings.ONE_HANDED_OFF
        settings.edit { putString(KeyHackSettings.ONE_HANDED, next) }
        applyOneHanded()
        renderStrip()
    }

    // -------------------------------------------------------------- floating

    private fun toggleFloating() {
        settings.edit { putBoolean(KeyHackSettings.FLOATING, !settings.floating) }
        applyFloating()
        applyOneHanded()
        renderStrip()
    }

    /**
     * Floating: the input view takes the whole window, transparent, and the
     * key column floats in it at the saved position; [onComputeInsets] tells
     * the system that only the column takes touches and the app keeps its
     * full size. Docked: the column at the bottom, as usual.
     */
    private fun applyFloating() {
        val r = root ?: return
        val col = keyColumn ?: return
        val on = settings.floating
        applyWindowHeight()
        r.setBackgroundColor(if (on) Color.TRANSPARENT else colors.background)
        r.layoutParams?.let { lp ->
            val h = if (on) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != h) {
                lp.height = h
                r.layoutParams = lp
            }
        }
        val p = col.layoutParams as FrameLayout.LayoutParams
        if (on) {
            val screen = resources.displayMetrics
            p.width = minOf(
                (screen.widthPixels * FLOAT_WIDTH_FRACTION).toInt(),
                (FLOAT_MAX_WIDTH_DP * screen.density).toInt(),
            )
            p.gravity = Gravity.TOP or Gravity.START
        } else {
            p.width = ViewGroup.LayoutParams.MATCH_PARENT
            p.gravity = Gravity.NO_GRAVITY
            p.leftMargin = 0
            p.topMargin = 0
        }
        col.layoutParams = p
        floatBar?.visibility = if (on) View.VISIBLE else View.GONE
        floatBar?.colors = colors
        val d = resources.displayMetrics.density
        col.background = if (on) {
            GradientDrawable().apply {
                cornerRadius = FLOAT_RADIUS_DP * d
                setColor(this@KeyHackImeService.colors.background)
            }
        } else {
            null
        }
        col.clipToOutline = on
        col.elevation = if (on) FLOAT_ELEVATION_DP * d else 0f
        positionFloating()
    }

    /** Places the floating column from the saved fractions, once sizes are known. */
    private fun positionFloating() {
        if (!settings.floating || floatDragStart != null) return
        val r = root ?: return
        val col = keyColumn ?: return
        if (r.height == 0 || col.height == 0) return
        val (freeW, freeH) = floatRoom(r, col)
        setFloatMargins(
            col,
            (settings.floatX * freeW).roundToInt(),
            floatTopInset + (settings.floatY * freeH).roundToInt(),
        )
    }

    /** Room left beside and above the column (never negative). */
    private fun floatRoom(r: View, col: View): Pair<Int, Int> {
        val w = r.width - col.width
        val h = r.height - r.paddingBottom - floatTopInset - col.height
        return w.coerceAtLeast(0) to h.coerceAtLeast(0)
    }

    private fun setFloatMargins(col: View, left: Int, top: Int) {
        val p = col.layoutParams as FrameLayout.LayoutParams
        if (p.leftMargin == left && p.topMargin == top) return
        p.leftMargin = left
        p.topMargin = top
        col.layoutParams = p
    }

    private val floatListener =
        object : FloatBar.Listener {
            override fun onFloatDrag(dx: Float, dy: Float) {
                val r = root ?: return
                val col = keyColumn ?: return
                val start = floatDragStart ?: run {
                    val p = col.layoutParams as FrameLayout.LayoutParams
                    (p.leftMargin to p.topMargin).also { floatDragStart = it }
                }
                val (freeW, freeH) = floatRoom(r, col)
                setFloatMargins(
                    col,
                    (start.first + dx.roundToInt()).coerceIn(0, freeW),
                    (start.second + dy.roundToInt()).coerceIn(floatTopInset, floatTopInset + freeH),
                )
            }

            override fun onFloatDragEnd() {
                floatDragStart = null
                val r = root ?: return
                val col = keyColumn ?: return
                val (freeW, freeH) = floatRoom(r, col)
                val p = col.layoutParams as FrameLayout.LayoutParams
                val fx = if (freeW > 0) p.leftMargin.toFloat() / freeW else KeyHackSettings.DEFAULT_FLOAT_X
                val fy = if (freeH > 0) (p.topMargin - floatTopInset).toFloat() / freeH else 1f
                settings.edit {
                    putFloat(KeyHackSettings.FLOAT_X, fx.coerceIn(0f, 1f))
                    putFloat(KeyHackSettings.FLOAT_Y, fy.coerceIn(0f, 1f))
                }
            }

            override fun onFloatDock() = toggleFloating()
        }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val r = root ?: return
        val col = keyColumn ?: return
        if (!settings.floating || !r.isShown) return
        val loc = IntArray(2)
        r.getLocationInWindow(loc)
        val bottom = loc[1] + r.height
        // No room taken from the app: it keeps its size, the keyboard floats over it.
        outInsets.contentTopInsets = bottom
        outInsets.visibleTopInsets = bottom
        col.getLocationInWindow(loc)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(loc[0], loc[1], loc[0] + col.width, loc[1] + col.height)
    }

    private fun currentGifProvider(): GifProvider? =
        GifProvider.create(settings.gifProvider, settings.gifKey(settings.gifProvider))

    private fun gifLocale(): String = if (languages().first() == "pt") "pt-BR" else "en-US"

    /** GIF search is the only network use (ADR 0004): never in incognito or password fields. */
    private fun gifAllowed(): Boolean =
        GifNetwork.available && settings.gifEnabled && !incognito && policy.kind != FieldKind.PASSWORD

    private fun openGifs() {
        commitComposing(autocorrect = false)
        if (!gifAllowed()) {
            showMessage(getString(R.string.keyhack_gif_disabled))
            return
        }
        showPanel(Panel.GIF)
        val p = gifPanel ?: return
        p.provider = currentGifProvider()
        p.locale = gifLocale()
        p.load(null)
    }

    override fun onEmoji(text: String) {
        val ic = currentInputConnection ?: return
        commitComposing(autocorrect = false)
        ic.commitText(text, 1)
        revert = null
        autoSpace = false
    }

    override fun onEmojiBack() {
        showPanel(Panel.KEYBOARD)
        showDefaultStrip()
    }

    override fun onEmojiSearch() = openSearch(Search.EMOJI)

    override fun onEmojiDelete() = onDelete()

    override fun onGifBack() {
        showPanel(Panel.KEYBOARD)
        showDefaultStrip()
    }

    override fun onGifSearch() = openSearch(Search.GIF)

    override fun onGifPicked(item: GifItem) {
        val provider = gifPanel?.provider ?: currentGifProvider()
        showMessageOnGif(getString(R.string.keyhack_gif_sending))
        gifs.download(provider, item) { result ->
            result
                .onSuccess { uri -> sendGif(uri, item) }
                .onFailure { showMessageOnGif(getString(R.string.keyhack_gif_error)) }
        }
    }

    private fun showMessageOnGif(text: String) {
        if (search == Search.GIF) {
            showMessage(text)
            return
        }
        gifPanel?.showMessage(text)
        main.removeCallbacks(clearGifMessage)
        main.postDelayed(clearGifMessage, MESSAGE_MS)
    }

    private val clearGifMessage = Runnable { gifPanel?.showMessage(null) }

    // ------------------------------------------------------------- stickers

    private fun openStickers() {
        commitComposing(autocorrect = false)
        showPanel(Panel.STICKER)
    }

    /** Emoji offered as stickers: recents first, then faces, people and animals. */
    private fun stickerEmoji(): List<String> {
        val data = emojiData
        val groups = listOf("smileys", "people", "nature", "food").flatMap { g ->
            data?.group(g).orEmpty().map { it.withTone(emojiStore.tone(it.value)) }
        }
        return (emojiStore.recents() + groups).distinct().take(MAX_STICKER_EMOJI)
    }

    override fun onStickerPicked(sticker: StickerPanel.Sticker) {
        stickerPanel?.showMessage(getString(R.string.keyhack_gif_sending))
        stickerIo.execute {
            val file = runCatching {
                when (sticker) {
                    is StickerPanel.Sticker.Image -> sticker.file
                    is StickerPanel.Sticker.Emoji -> stickers.emojiSticker(sticker.text, EmojiFont.typeface(this))
                }
            }.getOrNull()
            main.post {
                if (file == null) {
                    stickerPanel?.showMessage(getString(R.string.keyhack_gif_error))
                    return@post
                }
                if (sticker is StickerPanel.Sticker.Emoji) emojiStore.addRecent(sticker.text)
                sendSticker(stickers.uriFor(file))
            }
        }
    }

    /** As a real sticker when the app takes them (WhatsApp), else as a WebP image, else via the share sheet. */
    private fun sendSticker(uri: Uri) {
        val ic = currentInputConnection
        val editor = currentInputEditorInfo
        val description = getString(R.string.keyhack_sticker)
        val sent = ic != null && editor != null &&
            listOf(StickerStore.MIME_WHATSAPP, StickerStore.MIME_WEBP).any { mime ->
                ContentCommitter.commit(ic, editor, uri, mime, description)
            }
        if (sent) {
            stickerPanel?.showMessage(null)
            return
        }
        stickerPanel?.showMessage(null)
        showMessage(getString(R.string.keyhack_gif_share_fallback))
        shareFallback(uri, StickerStore.MIME_WEBP, description)
    }

    override fun onStickerBack() {
        showPanel(Panel.KEYBOARD)
        showDefaultStrip()
    }

    override fun onStickerImport() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(EXTRA_IMPORT_STICKERS, true)
        startActivity(intent)
    }

    private fun sendGif(uri: Uri, item: GifItem) {
        val ic = currentInputConnection
        val editor = currentInputEditorInfo
        gifs.addRecent(item)
        if (ic != null && editor != null && ContentCommitter.commit(ic, editor, uri, GIF_MIME, item.description)) {
            gifPanel?.showMessage(null)
            return
        }
        // The app does not take GIFs from keyboards: hand it over through the share sheet.
        showMessageOnGif(getString(R.string.keyhack_gif_share_fallback))
        shareFallback(uri, GIF_MIME, item.description)
    }

    private fun shareFallback(uri: Uri, mime: String, description: String) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(description, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                editorPackage?.let { setPackage(it) }
            }
        val chooser = Intent.createChooser(
            send,
            null,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(chooser) }.onFailure {
            send.setPackage(null)
            runCatching {
                startActivity(
                    Intent.createChooser(
                        send,
                        null,
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
    }

    override fun onEditAction(action: EditAction) {
        val ic = currentInputConnection ?: return
        commitComposing(autocorrect = false)
        when (action) {
            EditAction.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)

            EditAction.COPY -> ic.performContextMenuAction(android.R.id.copy)

            EditAction.CUT -> ic.performContextMenuAction(android.R.id.cut)

            EditAction.PASTE -> ic.performContextMenuAction(android.R.id.paste)

            EditAction.UNDO -> KeySender.send(ic, KeyEvent.KEYCODE_Z, KeySender.Mods(ctrl = true))

            EditAction.REDO -> KeySender.send(ic, KeyEvent.KEYCODE_Z, KeySender.Mods(ctrl = true, shift = true))

            EditAction.DOC_START -> KeySender.send(ic, KeyEvent.KEYCODE_MOVE_HOME, KeySender.Mods(ctrl = true))

            EditAction.DOC_END -> KeySender.send(ic, KeyEvent.KEYCODE_MOVE_END, KeySender.Mods(ctrl = true))

            EditAction.SELECT_LINE -> {
                KeySender.send(ic, KeyEvent.KEYCODE_MOVE_HOME, KeySender.Mods())
                KeySender.send(ic, KeyEvent.KEYCODE_MOVE_END, KeySender.Mods(shift = true))
            }
        }
    }

    override fun onDevTool(tool: DevTool) {
        val ic = currentInputConnection ?: return
        commitComposing(autocorrect = false)
        if (!tool.needsInput) {
            ic.commitText(TextTransforms.apply(tool, ""), 1)
            return
        }
        val selected = ic.getSelectedText(0)?.toString()?.takeIf { it.isNotEmpty() }
        val input =
            selected ?: Regex(
                "(\\S+)$",
            ).find(ic.getTextBeforeCursor(TOKEN_LOOKBACK, 0)?.toString().orEmpty())?.groupValues?.get(1)
        if (input.isNullOrEmpty()) {
            showToolMessage(getString(R.string.keyhack_msg_select_first))
            return
        }
        val result =
            runCatching { TextTransforms.apply(tool, input) }.getOrElse {
                showToolMessage(it.message ?: getString(R.string.keyhack_msg_failed))
                return
            }
        if (tool == DevTool.COUNT) {
            showToolMessage(result)
            return
        }
        ic.beginBatchEdit()
        if (selected == null) ic.deleteSurroundingText(input.length, 0)
        ic.commitText(result, 1)
        ic.endBatchEdit()
        showToolMessage("${tool.label} ✓")
    }

    private fun showToolMessage(text: String) {
        showPanel(Panel.KEYBOARD)
        showMessage(text)
    }

    override fun onToolsBack() {
        showPanel(Panel.KEYBOARD)
        showDefaultStrip()
    }

    // ------------------------------------------------------------ clipboard

    /**
     * Keeps what the user copies, when history is on. Never in incognito or
     * sensitive fields, and never clips the source app marks sensitive
     * (password managers set ClipDescription.EXTRA_IS_SENSITIVE).
     */
    private fun captureClip() {
        if (!settings.clipboardHistory || incognito) return
        val info = currentInputEditorInfo
        if (info != null && EditorPolicy.isSensitive(info.inputType, info.imeOptions)) return
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        val description = clip.description
        if (description.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return
        if (!description.hasMimeType("text/*") || clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        clipboard.add(text)
        if (panel == Panel.KEYBOARD && composing.isEmpty() && search == Search.NONE) renderStrip()
    }

    /** The item copied in the last moments, for the quick-paste chip; never in password fields. */
    private fun freshClip(): ClipItem? {
        if (!settings.clipboardHistory || incognito || policy.kind == FieldKind.PASSWORD) return null
        return clipboard.latest(FRESH_CLIP_MS)?.takeIf { it.id != dismissedClipId }
    }

    private fun openClipboard() {
        commitComposing(autocorrect = false)
        showPanel(Panel.CLIPBOARD)
    }

    private fun refreshClipboardPanel() {
        clipboardPanel?.show(clipboard.items(), settings.clipboardHistory)
    }

    private fun pasteClip(item: ClipItem) {
        val ic = currentInputConnection ?: return
        commitComposing(autocorrect = false)
        dismissedClipId = item.id
        ic.commitText(item.text, 1)
        revert = null
        autoSpace = false
        previousWordStale = true
        updateAutoCaps()
        showDefaultStrip()
    }

    override fun onClipPicked(item: ClipItem) {
        showPanel(Panel.KEYBOARD)
        pasteClip(item)
    }

    override fun onClipPinned(item: ClipItem, pinned: Boolean) = clipboard.setPinned(item.id, pinned)

    override fun onClipDeleted(item: ClipItem) = clipboard.remove(item.id)

    override fun onClipboardClear() {
        clipboard.clearUnpinned()
        showPanel(Panel.KEYBOARD)
        showMessage(getString(R.string.keyhack_clipboard_cleared))
    }

    override fun onClipboardEnable() {
        settings.edit { putBoolean(KeyHackSettings.CLIPBOARD_HISTORY, true) }
        refreshClipboardPanel()
        // Whatever is on the clipboard now is the first item.
        captureClip()
    }

    override fun onClipboardBack() {
        showPanel(Panel.KEYBOARD)
        showDefaultStrip()
    }

    // ------------------------------------------------- snippets and palette

    private fun snippetForComposing(): Snippet? =
        if (composing.isEmpty() || !policy.suggestions) null else snippets.forTrigger(composing.toString())

    /** Replaces the typed trigger ("end") with its snippet. */
    private fun expandSnippet(snippet: Snippet) {
        val ic = currentInputConnection ?: return
        resetComposing(finish = false)
        ownEdit { ic.commitText(snippet.text, 1) }
        afterInsert()
    }

    private fun insertText(text: String) {
        val ic = currentInputConnection ?: return
        commitComposing(autocorrect = false)
        ic.commitText(text, 1)
        afterInsert()
    }

    private fun afterInsert() {
        revert = null
        autoSpace = false
        previousWordStale = true
        updateAutoCaps()
        showDefaultStrip()
    }

    override fun onSnippet(snippet: Snippet) {
        showPanel(Panel.KEYBOARD)
        insertText(snippet.text)
    }

    override fun onSnippetFromSelection() {
        val ic = currentInputConnection
        val selected = ic?.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
        val text = selected ?: clipboardText()
        if (text == null) {
            showToolMessage(getString(R.string.keyhack_snippet_select_first))
            return
        }
        showToolMessage(
            getString(if (snippets.add("", text)) R.string.keyhack_snippet_saved else R.string.keyhack_msg_failed),
        )
    }

    /** What is on the clipboard now, unless the app that copied it marked it sensitive. */
    private fun clipboardText(): String? {
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return null
        if (clip.description.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true || clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
    }

    override fun onSnippetsEdit() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(EXTRA_EDIT_SNIPPETS, true)
        startActivity(intent)
    }

    override fun onPaletteOpen() = openSearch(Search.COMMAND)

    /** Everything the palette can run, in the order shown before anything is typed. */
    private fun paletteEntries(): List<PaletteEntry> = buildList {
        add(PaletteEntry("p:clip", "📋 " + getString(R.string.keyhack_clipboard_title), "clipboard copy paste colar"))
        add(PaletteEntry("p:emoji", "☺ Emoji", "smiley"))
        if (gifAllowed()) add(PaletteEntry("p:gif", "GIF", "giphy"))
        add(PaletteEntry("p:sticker", "🖼 " + getString(R.string.keyhack_sticker), "sticker figurinha whatsapp"))
        for (s in snippets.all()) {
            add(PaletteEntry("s:${s.id}", "✎ " + s.text.replace('\n', ' ').take(PREVIEW_CHARS), s.trigger))
        }
        add(PaletteEntry("t:incognito", "🕶 " + getString(R.string.keyhack_cmd_incognito), "incognito privado"))
        add(PaletteEntry("t:mode", "DEV " + getString(R.string.keyhack_cmd_mode), "simple advanced dev terminal"))
        if (advanced) add(PaletteEntry("t:pc", "PC " + getString(R.string.keyhack_cmd_pc), "pc layout terminal"))
        add(PaletteEntry("t:onehand", "✋ " + getString(R.string.keyhack_cmd_onehand), "one hand"))
        add(PaletteEntry("t:float", "⧉ " + getString(R.string.keyhack_float), "floating flutuante move"))
        for (a in EditAction.entries) {
            add(PaletteEntry("e:${a.name}", getString(a.label), a.name.lowercase().replace('_', ' ')))
        }
        for (d in DevTool.entries) add(PaletteEntry("d:${d.name}", d.label, d.name.lowercase().replace('_', ' ')))
        add(PaletteEntry("a:settings", "⚙ " + getString(R.string.keyhack_cmd_settings), "settings preferences"))
        add(PaletteEntry("a:switch", "⌨ " + getString(R.string.keyhack_cmd_switch), "keyboard ime"))
        add(PaletteEntry("a:hide", "⌄ " + getString(R.string.keyhack_cmd_hide), "hide close"))
    }

    private fun runPaletteEntry(id: String, closed: Boolean = false) {
        if (!closed) {
            closeSearch()
            showPanel(Panel.KEYBOARD)
            renderStrip()
        }
        val arg = id.substringAfter(':')
        when (id.substringBefore(':')) {
            "p" -> when (arg) {
                "clip" -> openClipboard()
                "emoji" -> onCommand(Command.EMOJI)
                "gif" -> onCommand(Command.GIF)
                "sticker" -> openStickers()
            }

            "s" -> snippets.all().firstOrNull { it.id.toString() == arg }?.let { insertText(it.text) }

            "t" -> onStripItem(arg)

            "e" -> runCatching { EditAction.valueOf(arg) }.getOrNull()?.let(::onEditAction)

            "d" -> runCatching { DevTool.valueOf(arg) }.getOrNull()?.let(::onDevTool)

            "a" -> when (arg) {
                "settings" -> openSettings()
                "switch" -> onCommand(Command.IME_PICKER)
                "hide" -> requestHideSelf(0)
            }
        }
    }

    // --------------------------------------------------------------- search

    private fun openSearch(target: Search) {
        search = target
        searchQuery.setLength(0)
        layer = ALPHA
        shift.reset()
        modifiers.reset()
        showPanel(Panel.KEYBOARD)
        renderKeyboard()
        onSearchChanged()
    }

    private fun closeSearch() {
        search = Search.NONE
        searchQuery.setLength(0)
        searchResults?.visibility = View.GONE
        main.removeCallbacks(liveGifSearch)
        liveGifGeneration++
        gifStrip?.clear()
        gifStrip?.visibility = View.GONE
    }

    private var liveGifGeneration = 0

    /** Fetches results for the query typed so far (debounced, latest wins). */
    private val liveGifSearch = Runnable {
        val strip = gifStrip ?: return@Runnable
        val provider = currentGifProvider()
        if (provider == null) {
            strip.clear()
            strip.showMessage(getString(R.string.keyhack_gif_no_key))
            return@Runnable
        }
        val gen = ++liveGifGeneration
        strip.showMessage(getString(R.string.keyhack_gif_loading))
        gifs.page(provider, searchQuery.toString().ifBlank { null }, null, gifLocale()) { result ->
            if (gen != liveGifGeneration || search != Search.GIF) return@page
            result
                .onSuccess { page ->
                    strip.show(page.items, provider)
                    strip.showMessage(if (page.items.isEmpty()) getString(R.string.keyhack_gif_empty) else null)
                }.onFailure { strip.showMessage(getString(R.string.keyhack_gif_error)) }
        }
    }

    private fun closeSearchToPanel() {
        val target = search
        closeSearch()
        when (target) {
            Search.EMOJI -> showPanel(Panel.EMOJI)
            Search.GIF -> showPanel(Panel.GIF)
            Search.COMMAND -> showPanel(Panel.TOOLS)
            Search.NONE -> Unit
        }
        renderStrip()
    }

    /** Keys typed while a search box is open go to the box, never to the app. */
    private fun onSearchKey(key: Key, action: KeyAction): Boolean {
        when (action) {
            is KeyAction.Character -> appendSearch(key.outputText(shift.isShifted) ?: action.text)

            KeyAction.Space -> appendSearch(" ")

            KeyAction.Delete -> {
                if (searchQuery.isNotEmpty()) {
                    searchQuery.delete(searchQuery.offsetByCodePoints(searchQuery.length, -1), searchQuery.length)
                }
                onSearchChanged()
            }

            KeyAction.Enter -> submitSearch()

            KeyAction.Shift, is KeyAction.Layer -> return false

            else -> Unit
        }
        renderState()
        return true
    }

    private fun appendSearch(text: String) {
        if (searchQuery.length >= MAX_SEARCH) return
        searchQuery.append(text)
        shift.onCharacterCommitted()
        onSearchChanged()
    }

    private fun onSearchChanged() {
        renderStrip()
        if (search == Search.GIF) {
            gifStrip?.let {
                it.applyColors(colors)
                it.visibility = View.VISIBLE
            }
            main.removeCallbacks(liveGifSearch)
            main.postDelayed(liveGifSearch, LIVE_GIF_DELAY_MS)
        }
        val results = searchResults ?: return
        if (search == Search.COMMAND) {
            results.visibility = View.VISIBLE
            results.colors = colors
            results.show(
                CommandPalette.rank(searchQuery.toString(), paletteEntries(), PALETTE_RESULTS)
                    .map { StripView.Item("c:" + it.id, it.label) },
                withDividers = true,
            )
            return
        }
        if (search != Search.EMOJI) {
            results.visibility = View.GONE
            return
        }
        val found = emojiData?.search(searchQuery.toString(), EMOJI_RESULTS).orEmpty()
        results.visibility = View.VISIBLE
        results.colors = colors
        results.show(
            found.map {
                StripView.Item(
                    "e:" + it.withTone(emojiStore.tone(it.value)),
                    it.withTone(emojiStore.tone(it.value)),
                    large = true,
                )
            },
        )
    }

    private fun onSearchResult(id: String) {
        if (id.startsWith("c:")) {
            runPaletteEntry(id.substring(2))
            return
        }
        if (!id.startsWith("e:")) return
        val emoji = id.substring(2)
        onEmoji(emoji)
        emojiStore.addRecent(emoji)
    }

    private fun submitSearch() {
        val q = searchQuery.toString()
        val target = search
        closeSearch()
        when (target) {
            Search.GIF -> {
                showPanel(Panel.GIF)
                gifPanel?.load(q.ifBlank { null })
            }

            Search.EMOJI -> showPanel(Panel.EMOJI)

            // Enter runs the best match.
            Search.COMMAND -> {
                showPanel(Panel.KEYBOARD)
                CommandPalette.rank(q, paletteEntries(), 1).firstOrNull()?.let { runPaletteEntry(it.id, closed = true) }
            }

            Search.NONE -> Unit
        }
        renderStrip()
    }

    companion object {
        private const val ALPHA = "alpha"
        private const val LEARNING_CLEAR_KEY = "learning_clear"
        private const val GIF_MIME = "image/gif"
        private const val PERCENT = 100f
        private const val DOUBLE_SPACE_MS = 800L
        private const val MESSAGE_MS = 2500L
        private const val LIVE_GIF_DELAY_MS = 350L
        private const val MAX_STICKER_EMOJI = 160
        const val EXTRA_IMPORT_STICKERS = "com.reinanbr.keyhack.IMPORT_STICKERS"
        const val EXTRA_EDIT_SNIPPETS = "com.reinanbr.keyhack.EDIT_SNIPPETS"
        private const val SNIPPET_CHIP_WEIGHT = 2f
        private const val FLOAT_WIDTH_FRACTION = 0.8f
        private const val FLOAT_MAX_WIDTH_DP = 440
        private const val FLOAT_RADIUS_DP = 14f
        private const val FLOAT_ELEVATION_DP = 8f

        /** After these, a glided word gets a space before it. */
        private val SPACE_AFTER = setOf('.', ',', '!', '?', ';', ':')
        private const val PALETTE_RESULTS = 4
        private const val GRAPHEME_LOOKBACK = 16
        private const val PREVIOUS_LOOKBACK = 64
        private const val EMOJI_SLOT_WEIGHT = 0.65f

        /** Emoji in the bar: up to three for the word being typed, two after it (the next words need room). */
        private const val EMOJI_WHILE_TYPING = 3
        private const val EMOJI_AFTER_WORD = 2

        /** Emoji looked at before preferring the user's recents. */
        private const val EMOJI_CANDIDATES = 6
        private const val ONE_HAND_FRACTION = 0.82f
        private const val FRESH_CLIP_MS = 2 * 60 * 1000L
        private const val GRAMMAR_LOOKBACK = 200
        private const val SWIPE_LOOKBACK = 1000
        private const val MAX_SWIPE_WORDS = 40
        private const val PREVIEW_CHARS = 40
        private const val UNDO_MS = 5000L
        private const val UNDO_CHIP_WEIGHT = 1.6f

        /** Held Delete switches to words after this many repeats (~1.6 s), one word every few repeats. */
        private const val WORD_DELETE_AFTER = 24
        private const val WORD_DELETE_EVERY = 3
        private const val GRAMMAR_CHIP_WEIGHT = 2.6f
        private const val PHRASE_CHIP_WEIGHT = 2.4f
        private const val EARLIER_WORDS = 3
        private val WORD_AT_END = Regex("([\\p{L}'’-]+)\\s+$")
        private const val CHIP_CHARS = 60

        /** ClipDescription.EXTRA_IS_SENSITIVE (API 33); apps set it on older versions too. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        // "... before previous" at the end of the text; group 1 is empty when only one word is there.
        private val LAST_WORDS = Regex("(?:([\\p{L}'’-]+)\\s+)?([\\p{L}'’-]+)\\s*$")
        private const val TOKEN_LOOKBACK = 256
        private const val MAX_SEARCH = 64
        private const val EMOJI_RESULTS = 12
        private const val MIN_PANEL_PX = 400
        private val AUTOCORRECT_TRIGGERS = setOf(".", ",", "!", "?", ";", ":", ")", "]", "}", "\"")
        private val ATTACHING_PUNCTUATION = setOf(".", ",", "!", "?", ";", ":")
        private val SENTENCE_END = setOf(".", "!", "?")
    }
}
