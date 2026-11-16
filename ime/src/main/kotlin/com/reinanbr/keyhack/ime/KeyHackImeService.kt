package com.reinanbr.keyhack.ime

import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.reinanbr.keyhack.commit.ContentCommitter
import com.reinanbr.keyhack.data.KeyHackSettings
import com.reinanbr.keyhack.devtools.DevTool
import com.reinanbr.keyhack.devtools.TextTransforms
import com.reinanbr.keyhack.emoji.EmojiData
import com.reinanbr.keyhack.ime.ui.EditAction
import com.reinanbr.keyhack.ime.ui.EmojiPanel
import com.reinanbr.keyhack.ime.ui.EmojiStore
import com.reinanbr.keyhack.ime.ui.GifPanel
import com.reinanbr.keyhack.ime.ui.GifStrip
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
import com.reinanbr.keyhack.media.GifProvider
import com.reinanbr.keyhack.media.GifRepository
import com.reinanbr.keyhack.stickers.StickerStore
import com.reinanbr.keyhack.suggest.Candidate
import com.reinanbr.keyhack.suggest.Suggester
import com.reinanbr.keyhack.suggest.Suggestions
import com.reinanbr.keyhack.theme.KeyboardColors
import com.reinanbr.keyhack.theme.KeyboardThemes
import java.text.BreakIterator
import java.util.Locale
import java.util.concurrent.Executors
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
    ToolsPanel.Listener {
    private enum class Panel { KEYBOARD, EMOJI, GIF, STICKER, TOOLS }

    private enum class Search { NONE, EMOJI, GIF }

    private lateinit var settings: KeyHackSettings
    private lateinit var layouts: LayoutRepository
    private lateinit var suggester: Suggester
    private lateinit var gifs: GifRepository
    private lateinit var emojiStore: EmojiStore
    private lateinit var stickers: StickerStore
    private val stickerIo = Executors.newSingleThreadExecutor() // threads start on first use
    private val main = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var strip: StripView? = null
    private var searchResults: StripView? = null
    private var gifStrip: GifStrip? = null
    private var content: FrameLayout? = null
    private var keyboardView: KeyboardView? = null
    private var popup: KeyPopupView? = null
    private var emojiPanel: EmojiPanel? = null
    private var gifPanel: GifPanel? = null
    private var toolsPanel: ToolsPanel? = null
    private var stickerPanel: StickerPanel? = null
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
    private var previousWord: String? = null

    /** The word before [previousWord], for two-word context ("eu te" -> "amo"). */
    private var beforeWord: String? = null
    private var previousWordStale = true
    private var revert: Revert? = null
    private var autoSpace = false
    private var lastSpaceAt = 0L
    private var hasSelection = false
    private var settingsChanged = true

    private data class Revert(val original: String, val corrected: String, val separator: String)

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LEARNING_CLEAR_KEY) suggester.clearHistory() else settingsChanged = true
        }

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
        suggester.close()
        gifs.close()
        stickerIo.shutdown()
        main.removeCallbacksAndMessages(null)
        root = null
        keyboardView = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

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
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        column.addView(
            stripView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        column.addView(
            results,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val liveGifs = GifStrip(ctx, gifs).also {
            it.visibility = View.GONE
            it.listener = GifStrip.Listener(::onGifPicked)
        }
        column.addView(
            liveGifs,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        column.addView(
            frame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
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
            insets
        }
        root = rootView
        strip = stripView
        searchResults = results
        gifStrip = liveGifs
        content = frame
        keyboardView = kv
        popup = overlay
        emojiPanel = null
        gifPanel = null
        toolsPanel = null
        stickerPanel = null
        settingsChanged = true
        panel = Panel.KEYBOARD
        return rootView
    }

    private fun navBarInset(insets: WindowInsets): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.tappableElement()).bottom
    } else {
        @Suppress("DEPRECATION") // The only API before Android 11.
        insets.systemWindowInsetBottom
    }

    private fun applySettings() {
        settingsChanged = false
        colors = KeyboardThemes.resolve(this, settings.theme)
        keyboardView?.apply {
            colors = this@KeyHackImeService.colors
            showHints = settings.keyHints
            heightScale = settings.heightPercent / PERCENT
            hapticsEnabled = settings.haptics
            soundEnabled = settings.sound
            longPressMillis = settings.longPressMs.toLong()
        }
        strip?.colors = colors
        searchResults?.colors = colors
        popup?.colors = colors
        emojiPanel?.applyColors(colors)
        gifPanel?.applyColors(colors)
        toolsPanel?.applyColors(colors)
        root?.setBackgroundColor(colors.background)
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
        if (policy.suggestions && !policy.rawKeys && isWordChar(text)) {
            if (composing.isEmpty()) autoSpace = false
            composing.append(text)
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
            previousWord = null
            beforeWord = null
            beforeWord = null
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
            previousWord = null
            beforeWord = null
            beforeWord = null
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
        previousWord = null
        beforeWord = null
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

            Command.CLIPBOARD -> showMessage(getString(R.string.keyhack_msg_soon))
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
            val s = if (suggestions.typed == typed) suggestions else suggester.suggestNow(typed, prev, beforeWord)
            s?.autoCorrection?.let { out = it }
        }
        // Clear state before touching the editor: commitText may call back into
        // onUpdateSelection before it returns.
        resetComposing(finish = false)
        ownEdit { ic?.commitText(out + separator, 1) }
        revert = if (out != typed) Revert(typed, out, separator) else null
        if (policy.learning) suggester.learn(out, prev)
        beforeWord = prev
        previousWord = out
        previousWordStale = false
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
        return previousWord
    }

    private fun String.endsWithAny(set: Set<String>) = set.any { endsWith(it) }

    private fun requestSuggestions() {
        val typed = composing.toString()
        val prev = previousWord()
        suggester.suggest(typed, prev, beforeWord) { s ->
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
        stripShowsToolbar = true
        renderStrip()
        val prev = previousWord
        if (policy.suggestions && composing.isEmpty() && prev != null && !previousWordStale) {
            suggester.predict(prev, beforeWord) { p ->
                if (composing.isNotEmpty() || p.isEmpty()) return@predict
                predictions = p
                stripShowsToolbar = false
                renderStrip()
            }
        }
    }

    private fun renderStrip() {
        val s = strip ?: return
        if (search != Search.NONE) {
            val hint = getString(
                if (search == Search.GIF) R.string.keyhack_gif_search_hint else R.string.keyhack_emoji_search_hint,
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
            return
        }
        val words = if (composing.isNotEmpty()) suggestionWords() else predictions.map { WordSlot(it.word, false) }
        if (stripShowsToolbar || words.isEmpty()) {
            s.show(toolbarItems())
            return
        }
        val items = ArrayList<StripView.Item>()
        items += StripView.Item("toolbar", "‹", weight = 0.5f, large = true, dim = true)
        for (w in words) items += StripView.Item("w:" + w.word, w.label, weight = 1.5f, bold = w.bold)
        emojiForComposing()?.let { items += StripView.Item("x:$it", it, weight = EMOJI_SLOT_WEIGHT, large = true) }
        s.show(items, withDividers = true)
    }

    /** An emoji for the word being typed ("amor" -> ❤️), preferring ones the user sends often. */
    private fun emojiForComposing(): String? {
        if (composing.isEmpty() || !policy.suggestions) return null
        val found = emojiData?.forWord(composing.toString()).orEmpty()
        if (found.isEmpty()) return null
        val recents = emojiStore.recents()
        val toned = found.map { it.withTone(emojiStore.tone(it.value)) }
        return toned.minByOrNull { e -> recents.indexOf(e).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    /** Keeps the typed word and adds the emoji after it: "amor ❤️ ". */
    private fun pickEmojiSuggestion(emoji: String) {
        val ic = currentInputConnection ?: return
        val word = composing.toString()
        val prev = previousWord()
        resetComposing(finish = false)
        ownEdit { ic.commitText("$word $emoji ", 1) }
        if (policy.learning && word.isNotEmpty()) suggester.learn(word, prev)
        emojiStore.addRecent(emoji)
        previousWord = null
        beforeWord = null
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
        add(StripView.Item("tools", "</>", bold = true))
        add(StripView.Item("mode", "DEV", bold = true, accent = advanced))
        if (advanced) add(StripView.Item("pc", if (pcMode) "ABC" else "PC", bold = true, accent = pcMode))
        add(StripView.Item("incognito", "🕶", accent = incognito))
        add(StripView.Item("settings", "⚙", large = true))
        add(StripView.Item("hide", "⌄", large = true))
    }

    private fun onStripItem(id: String) {
        when {
            id.startsWith("w:") -> pickWord(id.substring(2))

            id.startsWith("x:") -> pickEmojiSuggestion(id.substring(2))

            id == "toolbar" -> {
                stripShowsToolbar = true
                renderStrip()
            }

            id == "emoji" -> onCommand(Command.EMOJI)

            id == "gif" -> onCommand(Command.GIF)

            id == "sticker" -> openStickers()

            id == "tools" -> onCommand(Command.TOOLS)

            id == "pc" -> togglePc()

            id == "mode" -> toggleMode()

            id == "incognito" -> {
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
        beforeWord = prev
        previousWord = word
        previousWordStale = false
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
                    toolsPanel = it
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
        }
        if (p == Panel.EMOJI) emojiPanel?.refresh()
        if (p == Panel.STICKER) {
            stickerPanel?.emoji = stickerEmoji()
            stickerPanel?.refresh()
        }
        view.visibility = View.VISIBLE
        strip?.visibility = if (p == Panel.KEYBOARD) View.VISIBLE else View.GONE
    }

    private fun currentGifProvider(): GifProvider? =
        GifProvider.create(settings.gifProvider, settings.gifKey(settings.gifProvider))

    private fun gifLocale(): String = if (languages().first() == "pt") "pt-BR" else "en-US"

    /** GIF search is the only network use (ADR 0004): never in incognito or password fields. */
    private fun gifAllowed(): Boolean = settings.gifEnabled && !incognito && policy.kind != FieldKind.PASSWORD

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
                    is StickerPanel.Sticker.Emoji -> stickers.emojiSticker(sticker.text)
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
        private const val GRAPHEME_LOOKBACK = 16
        private const val PREVIOUS_LOOKBACK = 64
        private const val EMOJI_SLOT_WEIGHT = 0.8f

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
