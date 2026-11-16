package com.reinanbr.keyhack.keyboard.model

import java.util.Locale

/**
 * The single source of truth for what a character key emits and shows, so the
 * drawn label can never diverge from the committed text.
 */
fun Key.outputText(shifted: Boolean, locale: Locale = Locale.ROOT): String? {
    val action = action as? KeyAction.Character ?: return null
    if (!shifted) return action.text
    return action.shifted ?: action.text.uppercase(locale)
}

fun Key.displayLabel(shifted: Boolean, locale: Locale = Locale.ROOT): String = outputText(shifted, locale) ?: label

/** Long-press alternatives, shifted like the key itself. */
fun Key.popupFor(shifted: Boolean, locale: Locale = Locale.ROOT): List<String> =
    if (shifted && action is KeyAction.Character && action.shifted == null) {
        popup.map { it.uppercase(locale) }
    } else {
        popup
    }
