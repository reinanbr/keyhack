package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.emoji.Emoji
import com.reinanbr.keyhack.emoji.EmojiData
import com.reinanbr.keyhack.emoji.EmojiFont
import com.reinanbr.keyhack.emoji.Kaomoji
import com.reinanbr.keyhack.theme.KeyboardColors

/**
 * Emoji, kaomoji and symbol picker. Long-press an emoji that supports skin
 * tones to pick one; the choice is remembered per emoji.
 */
class EmojiPanel(context: Context, private val store: EmojiStore) : LinearLayout(context) {
    interface Listener {
        fun onEmoji(text: String)

        fun onEmojiBack()

        fun onEmojiSearch()

        fun onEmojiDelete()
    }

    var listener: Listener? = null
    var data: EmojiData? = null
        set(value) {
            field = value
            refresh()
        }

    private val tabs = StripView(context, TAB_HEIGHT_DP)
    private val grid = GridView(context)
    private val bottom = StripView(context, TAB_HEIGHT_DP)

    /** Kaomoji moods, shown under the tabs while the kaomoji tab is open. */
    private val moods = StripView(context, TAB_HEIGHT_DP)
    private var mood = Kaomoji.categories.first().id
    private val adapter = Adapter()
    private var tab = TAB_RECENT
    private var toneFor: Emoji? = null
    private var colors: KeyboardColors? = null

    // Declared before init: init reads them (property initializers run in order).
    private val emojiFace = EmojiFont.typeface(context)
    private val tabIcons =
        listOf(
            TAB_RECENT to "🕘", "smileys" to "😀", "people" to "👋", "nature" to "🐻", "food" to "🍔",
            "travel" to "🚗", "activities" to "⚽", "objects" to "💡", "symbols" to "🔣", "flags" to "🏁",
            TAB_KAOMOJI to "ツ", TAB_TEXT_SYMBOLS to "∑",
        )

    private var entries: List<String> = emptyList()
    private var emojiEntries: List<Emoji?> = emptyList()

    init {
        orientation = VERTICAL
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(moods, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        moods.visibility = GONE
        moods.listener =
            StripView.Listener { id ->
                mood = id
                refresh()
                grid.setSelection(0)
            }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val d = resources.displayMetrics.density
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = (CELL_DP * d).toInt()
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.isVerticalScrollBarEnabled = false
        grid.setOnItemClickListener { _, _, pos, _ -> pick(pos) }
        grid.setOnItemLongClickListener { _, _, pos, _ -> longPick(pos) }
        tabs.listener = StripView.Listener { id -> onTab(id) }
        bottom.listener =
            StripView.Listener { id ->
                when (id) {
                    "abc" -> listener?.onEmojiBack()
                    "search" -> listener?.onEmojiSearch()
                    "space" -> listener?.onEmoji(" ")
                    "del" -> listener?.onEmojiDelete()
                }
            }
        bottom.show(
            listOf(
                StripView.Item("abc", "ABC", weight = 1.2f, bold = true),
                StripView.Item("search", "🔍", weight = 1f),
                StripView.Item("space", "␣", weight = 3f, dim = true),
                StripView.Item("del", "⌫", weight = 1.2f, large = true),
            ),
        )
        showTabs()
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        tabs.colors = c
        bottom.colors = c
        moods.colors = c
        adapter.notifyDataSetChanged()
    }

    private fun showTabs() {
        tabs.show(
            tabIcons.map { (id, icon) ->
                StripView.Item(id, icon, accent = id == tab, emoji = id != TAB_KAOMOJI && id != TAB_TEXT_SYMBOLS)
            },
        )
    }

    private fun onTab(id: String) {
        val tone = toneFor
        if (tone != null) {
            if (id.startsWith("tone:")) {
                val t = id.removePrefix("tone:").toInt()
                store.setTone(tone.value, t)
                listener?.onEmoji(tone.withTone(t))
                store.addRecent(tone.withTone(t))
            }
            toneFor = null
            showTabs()
            adapter.notifyDataSetChanged()
            return
        }
        tab = id
        showTabs()
        refresh()
        grid.setSelection(0)
    }

    fun refresh() {
        val d = data
        moods.visibility = if (tab == TAB_KAOMOJI) VISIBLE else GONE
        when (tab) {
            TAB_KAOMOJI -> {
                moods.show(Kaomoji.categories.map { StripView.Item(it.id, it.label, accent = it.id == mood) })
                val faces = Kaomoji.categories.firstOrNull { it.id == mood }?.faces.orEmpty()
                setEntries(faces.map { it to null }, wide = true)
            }

            TAB_TEXT_SYMBOLS -> setEntries(Kaomoji.symbols.map { it to null }, wide = false)

            TAB_RECENT -> {
                val recents = store.recents()
                setEntries(recents.map { r -> r to d?.find(r) }, wide = false)
            }

            else -> setEntries(
                d?.group(tab).orEmpty().filter(::renders).map { e -> e.withTone(store.tone(e.value)) to e },
                wide = false,
            )
        }
    }

    /** Hides emoji the emoji font cannot draw (newer than Unicode 18, or than this Android version). */
    private fun renders(e: Emoji): Boolean = EmojiFont.canDraw(context, e.value)

    private fun setEntries(list: List<Pair<String, Emoji?>>, wide: Boolean) {
        entries = list.map { it.first }
        emojiEntries = list.map { it.second }
        val d = resources.displayMetrics.density
        grid.columnWidth = ((if (wide) WIDE_CELL_DP else CELL_DP) * d).toInt()
        adapter.wide = wide
        adapter.notifyDataSetChanged()
    }

    private fun pick(pos: Int) {
        val text = entries.getOrNull(pos) ?: return
        listener?.onEmoji(text)
        if (tab != TAB_KAOMOJI && tab != TAB_TEXT_SYMBOLS) store.addRecent(text)
    }

    private fun longPick(pos: Int): Boolean {
        val e = emojiEntries.getOrNull(pos) ?: return false
        if (!e.toneable) return false
        toneFor = e
        tabs.show(
            (0..Emoji.TONES).map { t ->
                StripView.Item(
                    "tone:$t",
                    e.withTone(t),
                    emoji = true,
                    accent = store.tone(e.value) == t,
                )
            } +
                StripView.Item("tone:cancel", "✕", weight = 0.6f),
        )
        return true
    }

    private inner class Adapter : BaseAdapter() {
        var wide = false

        override fun getCount() = entries.size

        override fun getItem(position: Int) = entries[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv =
                convertView as? TextView ?: TextView(context).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    includeFontPadding = false
                }
            val d = resources.displayMetrics.density
            tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (CELL_HEIGHT_DP * d).toInt())
            if (wide) {
                // Long faces ("┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻") shrink to fit their cell instead of being cut off.
                val pad = (WIDE_PADDING_DP * d).toInt()
                tv.setPadding(pad, 0, pad, 0)
                tv.setAutoSizeTextTypeUniformWithConfiguration(
                    WIDE_MIN_SP.toInt(),
                    WIDE_TEXT_SP.toInt(),
                    1,
                    TypedValue.COMPLEX_UNIT_SP,
                )
            } else {
                tv.setPadding(0, 0, 0, 0)
                tv.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, EMOJI_SP)
            }
            tv.typeface = if (wide) null else emojiFace
            colors?.let { tv.setTextColor(it.label) }
            tv.text = entries[position]
            return tv
        }
    }

    companion object {
        const val TAB_RECENT = "recent"
        const val TAB_KAOMOJI = "kaomoji"
        const val TAB_TEXT_SYMBOLS = "textsym"
        private const val TAB_HEIGHT_DP = 40f
        private const val CELL_DP = 46f
        private const val WIDE_CELL_DP = 120f
        private const val CELL_HEIGHT_DP = 48f
        private const val EMOJI_SP = 26f
        private const val WIDE_TEXT_SP = 15f
        private const val WIDE_MIN_SP = 8f
        private const val WIDE_PADDING_DP = 4f
    }
}

/** Recently used emoji and per-emoji skin tones, kept in private preferences. */
class EmojiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("keyhack_emoji", Context.MODE_PRIVATE)

    fun recents(): List<String> = prefs.getString(
        RECENTS,
        null,
    )?.split('\n')?.filter { it.isNotEmpty() } ?: DEFAULT_RECENTS

    fun addRecent(text: String) {
        val list = (listOf(text) + recents().filter { it != text }).take(MAX_RECENTS)
        prefs.edit().putString(RECENTS, list.joinToString("\n")).apply()
    }

    fun tone(emoji: String): Int = prefs.getInt("tone:$emoji", 0)

    fun setTone(emoji: String, tone: Int) {
        prefs.edit().putInt("tone:$emoji", tone).apply()
    }

    private companion object {
        const val RECENTS = "recents"
        const val MAX_RECENTS = 40
        val DEFAULT_RECENTS = listOf("😂", "❤️", "👍", "😊", "🙏", "😭", "🔥", "😅", "🎉", "🤔", "👀", "✅")
    }
}
