package com.reinanbr.keyhack.keyboard.model

/** Sticky modifier keys (Shift has its own controller because it also changes labels). */
enum class Modifier { CTRL, ALT, META }

/** Actions handled by the IME rather than sent to the editor. */
enum class Command {
    EMOJI,
    SETTINGS,
    TOGGLE_PC,
    NEXT_IME,
    IME_PICKER,
    TOOLS,
    GIF,
    CLIPBOARD,
}

/** What a key does when activated. The IME service maps these to InputConnection calls. */
sealed interface KeyAction {
    /**
     * Commits [text]. Letters are stored lowercase and uppercased when shifted,
     * unless [shifted] gives an explicit shifted output (PC layout: 1 -> !).
     */
    data class Character(val text: String, val shifted: String? = null) : KeyAction

    data object Shift : KeyAction

    data object Delete : KeyAction

    data object Enter : KeyAction

    data object Space : KeyAction

    /** Switches to another layer of the keyboard ("symbols", "alpha", "fn", ...). */
    data class Layer(val target: String) : KeyAction

    data class Mod(val modifier: Modifier) : KeyAction

    /** Sends a raw Android key code (Esc, Tab, arrows, F1-F12...) with the active modifiers. */
    data class Code(val keyCode: Int, val name: String) : KeyAction

    data class Cmd(val command: Command) : KeyAction
}

/**
 * One key. [width] is in key units, where a standard letter key is 1.0.
 * [label] is what is drawn; for [KeyAction.Character] keys it is derived from
 * the output so the visible label always matches what is typed (anti-spoofing).
 * [popup] lists long-press alternatives; [hint] is drawn small in the corner.
 */
data class Key(
    val label: String,
    val action: KeyAction,
    val width: Float = 1f,
    val popup: List<String> = emptyList(),
    val hint: String? = null,
    val longPress: KeyAction? = null,
) {
    init {
        require(width > 0f) { "key width must be positive" }
    }

    /** Whether this key repeats while held. */
    val repeats: Boolean
        get() = action == KeyAction.Delete || (action is KeyAction.Code && action.name in REPEATING_CODES)

    /** Keys drawn with the function-key color. */
    val isFunction: Boolean
        get() = action !is KeyAction.Character && action != KeyAction.Space

    companion object {
        private val REPEATING_CODES = setOf("left", "right", "up", "down", "fdel", "pgup", "pgdn")
    }
}

/** A row of keys; [height] is relative to a normal row (1.0). */
data class KeyRow(val keys: List<Key>, val height: Float = 1f) {
    val width: Float get() = keys.sumOf { it.width.toDouble() }.toFloat()
}

data class KeyboardLayout(val id: String, val rows: List<KeyRow>) {
    init {
        require(rows.isNotEmpty() && rows.all { it.keys.isNotEmpty() }) { "layout $id has an empty row" }
    }

    /** Width in key units of the widest row; narrower rows are centered. */
    val unitWidth: Float get() = rows.maxOf { it.width }

    /** Sum of relative row heights. */
    val unitHeight: Float get() = rows.sumOf { it.height.toDouble() }.toFloat()

    fun withRowsOnTop(extra: List<KeyRow>): KeyboardLayout = if (extra.isEmpty()) this else copy(rows = extra + rows)

    /** Drops single-digit hints (shown by the number row instead); the digit stays a long-press option. */
    fun withoutDigitHints(): KeyboardLayout = copy(
        rows = rows.map { row ->
            row.copy(
                keys = row.keys.map { if (it.hint?.singleOrNull()?.isDigit() == true) it.copy(hint = null) else it },
            )
        },
    )

    /**
     * Gboard-style symbols on the letter keys: the n-th character key of letter
     * row r gets `hints[r][n]` as its corner hint (typed by a swipe up) and as
     * the first long-press option, followed by that symbol's own alternatives
     * from [popups] and the key's accents. With [replaceDigits] false, keys that
     * already show a digit hint keep it (the top row without a number row) and
     * get the symbol as their second long-press option.
     */
    fun withSymbolHints(
        hints: List<List<String>>,
        popups: Map<String, List<String>>,
        replaceDigits: Boolean,
    ): KeyboardLayout {
        if (hints.isEmpty()) return this
        val newRows = rows.mapIndexed { r, row ->
            val symbols = hints.getOrNull(r) ?: return@mapIndexed row
            var n = 0
            row.copy(
                keys = row.keys.map { key ->
                    if (key.action !is KeyAction.Character) return@map key
                    val symbol = symbols.getOrNull(n++) ?: return@map key
                    val oldHint = key.hint
                    val digit = oldHint?.takeIf { it.singleOrNull()?.isDigit() == true }
                    val accents = key.popup.filter { it != oldHint }
                    if (digit != null && !replaceDigits) {
                        // Keep the digit; the symbol becomes the second long-press option.
                        return@map key.copy(popup = (listOf(digit, symbol) + accents).distinct())
                    }
                    if (oldHint != null && digit == null) return@map key
                    val alternatives = popups[symbol].orEmpty().filter { it != symbol }
                    key.copy(hint = symbol, popup = (listOf(symbol) + accents + alternatives).distinct())
                },
            )
        }
        return copy(rows = newRows)
    }

    /** Letter keys and their centers in key units, for proximity-aware autocorrect. */
    fun letterCenters(): Map<Char, Pair<Float, Float>> {
        val out = HashMap<Char, Pair<Float, Float>>()
        val unit = unitWidth
        var y = 0f
        for (row in rows) {
            var x = (unit - row.width) / 2f
            for (key in row.keys) {
                val text = (key.action as? KeyAction.Character)?.text
                if (text != null && text.length == 1 && text[0].isLetter()) {
                    out[text[0]] = (x + key.width / 2f) to (y + row.height / 2f)
                }
                x += key.width
            }
            y += row.height
        }
        return out
    }
}
