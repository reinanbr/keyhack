package com.reinanbr.keyhack.keyboard.model

/** What a key does when activated. The IME service maps these to InputConnection calls. */
sealed interface KeyAction {
    /** Commits [text]; letters are stored lowercase and shifted at commit time. */
    data class Character(val text: String) : KeyAction

    data object Shift : KeyAction

    data object Delete : KeyAction

    data object Enter : KeyAction

    data object Space : KeyAction
}

/**
 * One key. [width] is in key units, where a standard letter key is 1.0.
 * [label] is what is drawn; for [KeyAction.Character] keys it is shifted with
 * the text so the visible label always matches the output (anti-spoofing).
 */
data class Key(val label: String, val action: KeyAction, val width: Float = 1f) {
    init {
        require(width > 0f) { "key width must be positive" }
    }

    /** Whether this key repeats while held (only Delete in M0). */
    val repeats: Boolean get() = action == KeyAction.Delete
}

data class KeyRow(val keys: List<Key>) {
    val width: Float get() = keys.sumOf { it.width.toDouble() }.toFloat()
}

data class KeyboardLayout(val id: String, val rows: List<KeyRow>) {
    init {
        require(rows.isNotEmpty() && rows.all { it.keys.isNotEmpty() }) { "layout $id has an empty row" }
    }

    /** Width in key units of the widest row; narrower rows are centered. */
    val unitWidth: Float get() = rows.maxOf { it.width }
}
