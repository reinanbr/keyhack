package com.reinanbr.keyhack.backup

import com.reinanbr.keyhack.data.KeyHackSettings
import com.reinanbr.keyhack.theme.KeyboardThemes

/**
 * The settings a backup carries, each with its type and allowed values.
 * Anything else in a file is ignored, and so is any value that breaks its
 * rule: a backup can only set what the settings screen could.
 *
 * Left out on purpose: the GIF API key (a secret that only goes to its
 * provider) and internal signals such as "learning_clear".
 */
internal object SettingsSchema {
    sealed interface Rule {
        /** The value to store (Boolean, Int, Float or String), or null when [value] is not allowed. */
        fun check(value: Any?): Any?
    }

    private object Bool : Rule {
        override fun check(value: Any?) = value as? Boolean
    }

    private class IntIn(val range: IntRange) : Rule {
        override fun check(value: Any?): Any? {
            val d = (value as? Number)?.toDouble() ?: return null
            return d.toInt().takeIf { it.toDouble() == d && it in range }
        }
    }

    private class FloatIn(val range: ClosedFloatingPointRange<Float>) : Rule {
        override fun check(value: Any?): Any? = (value as? Number)?.toFloat()?.takeIf { it in range }
    }

    private class OneOf(val values: Collection<String>) : Rule {
        override fun check(value: Any?): Any? = (value as? String)?.takeIf { it in values }
    }

    private class Text(val max: Int) : Rule {
        override fun check(value: Any?): Any? = (value as? String)?.takeIf { it.length <= max }
    }

    /** A comma list of supported languages, at least one. */
    private object Languages : Rule {
        override fun check(value: Any?): Any? {
            val text = value as? String ?: return null
            val list = text.split(',').map { it.trim() }
            return text.takeIf { list.all { it in KeyHackSettings.SUPPORTED_LANGUAGES } }
        }
    }

    private const val SHORT_TEXT = 500
    private const val LONG_TEXT = 4000

    private val K = KeyHackSettings

    val rules: Map<String, Rule> =
        mapOf(
            K.MODE to OneOf(listOf(K.MODE_SIMPLE, K.MODE_ADVANCED)),
            K.LAYOUT to OneOf(K.LAYOUTS),
            K.NUMBER_ROW to Bool,
            K.DEV_ROW to Bool,
            K.DEV_ROW_KEYS to Text(SHORT_TEXT),
            K.KEY_HINTS to Bool,
            K.HEIGHT to IntIn(K.HEIGHT_RANGE),
            K.LONG_PRESS to IntIn(K.LONG_PRESS_RANGE),
            K.THEME to OneOf(KeyboardThemes.ids),
            K.CUSTOM_THEME to Text(SHORT_TEXT),
            K.KEY_PREVIEW to Bool,
            K.ONE_HANDED to OneOf(K.ONE_HANDED_SIDES),
            K.HAPTICS to Bool,
            K.FLOATING to Bool,
            K.FLOAT_X to FloatIn(0f..1f),
            K.FLOAT_Y to FloatIn(0f..1f),
            K.GLIDE to Bool,
            K.VIBRATION_MS to IntIn(0..K.MAX_VIBRATION_MS),
            K.SOUND to Bool,
            K.SUGGESTIONS to Bool,
            K.AUTOCORRECT to Bool,
            K.AUTO_CAPS to Bool,
            K.DOUBLE_SPACE to Bool,
            K.LEARNING to Bool,
            K.GRAMMAR to Bool,
            K.LANGUAGES to Languages,
            K.PC_APPS to Text(LONG_TEXT),
            K.PC_NO_AUTOCORRECT to Bool,
            K.GIF_ENABLED to Bool,
            K.GIF_PROVIDER to OneOf(K.GIF_PROVIDERS),
            K.CLIPBOARD_HISTORY to Bool,
            K.CLIPBOARD_EXPIRY to OneOf(K.CLIPBOARD_EXPIRY_CHOICES),
        )

    /** The allowed entries of [values] (stored preferences, or a parsed file). */
    fun filter(values: Map<String, *>): Map<String, Any> = buildMap {
        for ((key, rule) in rules) {
            rule.check(values[key])?.let { put(key, it) }
        }
    }
}
