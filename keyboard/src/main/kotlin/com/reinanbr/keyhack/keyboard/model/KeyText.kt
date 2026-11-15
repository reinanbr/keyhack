package com.reinanbr.keyhack.keyboard.model

import java.util.Locale

/**
 * The single source of truth for what a character key emits and shows, so the
 * drawn label can never diverge from the committed text.
 */
fun Key.outputText(shifted: Boolean, locale: Locale = Locale.ROOT): String? {
    val action = action as? KeyAction.Character ?: return null
    return if (shifted) action.text.uppercase(locale) else action.text
}

fun Key.displayLabel(shifted: Boolean, locale: Locale = Locale.ROOT): String = outputText(shifted, locale) ?: label
