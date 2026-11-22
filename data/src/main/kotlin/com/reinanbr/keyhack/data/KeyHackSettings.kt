package com.reinanbr.keyhack.data

import android.content.Context
import android.content.SharedPreferences

/**
 * User preferences, shared by the settings app and the IME (same process).
 * Stored in a private SharedPreferences file that is excluded from backups.
 * The GIF API key is the user's own (BYOK) and never leaves this file except
 * in requests to the provider they chose.
 */
class KeyHackSettings(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)

    private fun str(key: String, default: String) = prefs.getString(key, default) ?: default

    /**
     * "simple": a chat keyboard (letters, emoji, GIFs, suggestions).
     * "advanced": adds the dev row (Esc, Tab, Ctrl, Alt, arrows), code symbols
     * on the top row and the PC layout button. Terminal apps are always advanced.
     */
    val mode: String get() = str(MODE, MODE_SIMPLE)
    val advanced: Boolean get() = mode == MODE_ADVANCED

    // Layout
    val layout: String get() = str(LAYOUT, "qwerty")
    val numberRow: Boolean get() = bool(NUMBER_ROW, true)
    val devRow: Boolean get() = bool(DEV_ROW, true)
    val devRowKeys: String get() = str(DEV_ROW_KEYS, DEFAULT_DEV_ROW)
    val keyHints: Boolean get() = bool(KEY_HINTS, true)
    val heightPercent: Int get() = prefs.getInt(HEIGHT, DEFAULT_HEIGHT)
    val longPressMs: Int get() = prefs.getInt(LONG_PRESS, DEFAULT_LONG_PRESS)
    val theme: String get() = str(THEME, "system")
    val keyPreview: Boolean get() = bool(KEY_PREVIEW, true)

    /** One-handed mode: "off", "left" or "right" (the side the keyboard sits on). */
    val oneHanded: String get() = str(ONE_HANDED, ONE_HANDED_OFF).takeIf { it in ONE_HANDED_SIDES } ?: ONE_HANDED_OFF
    val haptics: Boolean get() = bool(HAPTICS, true)

    /** Floating keyboard: a smaller keyboard the user drags anywhere over the app. */
    val floating: Boolean get() = bool(FLOATING, false)

    /** Where it floats, as fractions (0..1) of the room left beside and above it. */
    val floatX: Float get() = prefs.getFloat(FLOAT_X, DEFAULT_FLOAT_X).coerceIn(0f, 1f)
    val floatY: Float get() = prefs.getFloat(FLOAT_Y, DEFAULT_FLOAT_Y).coerceIn(0f, 1f)

    /** Slide across the letters to type a word. */
    val glide: Boolean get() = bool(GLIDE, true)

    /** Key vibration in ms; 0 = the system's keyboard haptic (silent when the system turns touch feedback off). */
    val vibrationMs: Int get() = prefs.getInt(VIBRATION_MS, 0).coerceIn(0, MAX_VIBRATION_MS)
    val sound: Boolean get() = bool(SOUND, false)

    // Typing
    val suggestions: Boolean get() = bool(SUGGESTIONS, true)
    val autocorrect: Boolean get() = bool(AUTOCORRECT, true)
    val autoCaps: Boolean get() = bool(AUTO_CAPS, true)
    val doubleSpacePeriod: Boolean get() = bool(DOUBLE_SPACE, true)
    val learning: Boolean get() = bool(LEARNING, true)

    /** Grammar and agreement fixes offered in the suggestion bar. */
    val grammar: Boolean get() = bool(GRAMMAR, true)
    val languages: List<String>
        get() = str(
            LANGUAGES,
            "en,pt",
        ).split(',').map { it.trim() }.filter { it in SUPPORTED_LANGUAGES }.ifEmpty { listOf("en") }

    // PC mode
    val pcApps: Set<String>
        get() = str(PC_APPS, DEFAULT_PC_APPS).split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val pcAutocorrectOff: Boolean get() = bool(PC_NO_AUTOCORRECT, true)

    // GIFs
    val gifEnabled: Boolean get() = bool(GIF_ENABLED, true)
    val gifProvider: String get() = str(GIF_PROVIDER, "giphy").takeIf { it in GIF_PROVIDERS } ?: "giphy"
    fun gifKey(provider: String): String = str(GIF_KEY_PREFIX + provider, "")

    // Clipboard history: opt-in, encrypted, unpinned items expire.
    val clipboardHistory: Boolean get() = bool(CLIPBOARD_HISTORY, false)
    val clipboardExpiryHours: Int
        get() = str(CLIPBOARD_EXPIRY, DEFAULT_CLIPBOARD_EXPIRY).toIntOrNull()?.takeIf {
            it.toString() in CLIPBOARD_EXPIRY_CHOICES
        } ?: DEFAULT_CLIPBOARD_EXPIRY.toInt()

    fun edit(block: SharedPreferences.Editor.() -> Unit) = prefs.edit().apply(block).apply()

    companion object {
        const val FILE = "keyhack_settings"
        const val MODE = "keyboard_mode"
        const val MODE_SIMPLE = "simple"
        const val MODE_ADVANCED = "advanced"
        const val LAYOUT = "layout"
        const val NUMBER_ROW = "number_row"
        const val DEV_ROW = "dev_row"
        const val DEV_ROW_KEYS = "dev_row_keys"
        const val KEY_HINTS = "key_hints"
        const val HEIGHT = "height_percent"
        const val LONG_PRESS = "long_press_ms"
        const val THEME = "theme"
        const val HAPTICS = "haptics"
        const val FLOATING = "floating"
        const val FLOAT_X = "float_x"
        const val FLOAT_Y = "float_y"
        const val DEFAULT_FLOAT_X = 0.5f
        const val DEFAULT_FLOAT_Y = 1f
        const val GLIDE = "glide_typing"
        const val VIBRATION_MS = "vibration_ms"
        const val MAX_VIBRATION_MS = 60
        const val KEY_PREVIEW = "key_preview"
        const val ONE_HANDED = "one_handed"
        const val ONE_HANDED_OFF = "off"
        val ONE_HANDED_SIDES = listOf(ONE_HANDED_OFF, "left", "right")
        const val SOUND = "sound"
        const val SUGGESTIONS = "suggestions"
        const val AUTOCORRECT = "autocorrect"
        const val AUTO_CAPS = "auto_caps"
        const val DOUBLE_SPACE = "double_space_period"
        const val LEARNING = "learning"
        const val GRAMMAR = "grammar_check"
        const val LANGUAGES = "languages"
        const val PC_APPS = "pc_apps"
        const val PC_NO_AUTOCORRECT = "pc_no_autocorrect"
        const val GIF_ENABLED = "gif_enabled"
        const val GIF_PROVIDER = "gif_provider"
        const val GIF_KEY_PREFIX = "gif_key_"

        const val CLIPBOARD_HISTORY = "clipboard_history"
        const val CLIPBOARD_EXPIRY = "clipboard_expiry_hours"
        const val DEFAULT_CLIPBOARD_EXPIRY = "1"
        val CLIPBOARD_EXPIRY_CHOICES = listOf("1", "24", "168")

        const val DEFAULT_DEV_ROW = "esc tab ctrl alt left down up right home end"
        const val DEFAULT_HEIGHT = 100
        const val DEFAULT_LONG_PRESS = 300
        const val DEFAULT_PC_APPS =
            "com.termux, com.sonelli.juicessh, com.server.auditor.ssh.client, org.connectbot, " +
                "com.foxdebug.acode, com.foxdebug.acodefree, jackpal.androidterm, com.offsec.nhterm"
        val LAYOUTS = listOf("qwerty", "abnt2", "azerty", "dvorak", "colemak", "pc")
        val SUPPORTED_LANGUAGES = listOf("en", "pt")

        // Tenor was removed: Google shut its API down on 2026-06-30.
        val GIF_PROVIDERS = listOf("giphy")
    }
}
