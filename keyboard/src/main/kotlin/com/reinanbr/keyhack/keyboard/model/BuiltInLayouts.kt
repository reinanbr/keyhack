package com.reinanbr.keyhack.keyboard.model

/**
 * Layouts compiled into the app. M1 moves these to validated JSON in :layouts;
 * this hard-coded QWERTY is the M0 bootstrap.
 */
object BuiltInLayouts {
    private fun letters(chars: String) = chars.map { Key(it.toString(), KeyAction.Character(it.toString())) }

    val QWERTY: KeyboardLayout =
        KeyboardLayout(
            id = "qwerty",
            rows =
            listOf(
                KeyRow(letters("qwertyuiop")),
                KeyRow(letters("asdfghjkl")),
                KeyRow(
                    listOf(Key("⇧", KeyAction.Shift, 1.5f)) +
                        letters("zxcvbnm") +
                        Key("⌫", KeyAction.Delete, 1.5f),
                ),
                KeyRow(
                    listOf(
                        Key(",", KeyAction.Character(","), 1.5f),
                        Key("", KeyAction.Space, 5.5f),
                        Key(".", KeyAction.Character("."), 1.5f),
                        Key("⏎", KeyAction.Enter, 1.5f),
                    ),
                ),
            ),
        )
}
