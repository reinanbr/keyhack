package com.reinanbr.keyhack.theme

/**
 * The user's own theme: five colors picked in settings, the rest derived
 * from them. Stored as one string, "#RRGGBB" values joined by commas in the
 * order of [Slot].
 */
object CustomTheme {
    /** The colors the user picks. */
    enum class Slot { BACKGROUND, KEY, FUNCTION_KEY, LABEL, ACCENT }

    /** Picks taken from a built-in theme, to start editing from. */
    fun picks(from: KeyboardColors): List<Int> =
        listOf(from.background, from.key, from.functionKey, from.label, from.accent)

    fun encode(picks: List<Int>): String = picks.joinToString(",") { "#%06X".format(it and RGB) }

    /** The five picks in [encoded], or null when it is not five colors. */
    fun decode(encoded: String): List<Int>? {
        val colors = encoded.split(',').map(::parseColor)
        if (colors.size != Slot.entries.size || colors.any { it == null }) return null
        return colors.filterNotNull()
    }

    /** "#RRGGBB" or "RRGGBB" as an opaque color, else null. */
    fun parseColor(text: String): Int? =
        text.trim().removePrefix("#").takeIf { it.length == HEX_DIGITS }?.toIntOrNull(HEX)?.or(OPAQUE)

    fun colors(encoded: String): KeyboardColors? = decode(encoded)?.let(::colors)

    /** The full palette for [picks] (see [Slot]). */
    fun colors(picks: List<Int>): KeyboardColors {
        fun pick(slot: Slot) = picks[slot.ordinal]
        val key = pick(Slot.KEY)
        val label = pick(Slot.LABEL)
        val accent = pick(Slot.ACCENT)
        return KeyboardColors(
            background = pick(Slot.BACKGROUND),
            key = key,
            functionKey = pick(Slot.FUNCTION_KEY),
            pressedKey = mix(key, label, PRESSED_MIX),
            label = label,
            secondaryLabel = mix(label, key, SECONDARY_MIX),
            accent = accent,
            accentKey = accent,
            onAccent = readableOn(accent),
        )
    }

    /** Black or white, whichever reads better on [color]. */
    fun readableOn(color: Int): Int = if (luminance(color) > LIGHT_LUMINANCE) BLACK else WHITE

    /** [a] moved [t] of the way toward [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = a shr shift and BYTE
            val y = b shr shift and BYTE
            return (x + (y - x) * t + HALF).toInt().coerceIn(0, BYTE) shl shift
        }
        return OPAQUE or ch(RED) or ch(GREEN) or ch(BLUE)
    }

    /** Relative luminance (0..1), per WCAG. */
    fun luminance(color: Int): Double {
        fun lin(shift: Int): Double {
            val c = (color shr shift and BYTE) / BYTE.toDouble()
            return if (c <= SRGB_KNEE) c / SRGB_SLOPE else Math.pow((c + SRGB_OFFSET) / (1 + SRGB_OFFSET), SRGB_GAMMA)
        }
        return LUM_R * lin(RED) + LUM_G * lin(GREEN) + LUM_B * lin(BLUE)
    }

    /** WCAG contrast ratio between two colors (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + CONTRAST_OFFSET) / (minOf(la, lb) + CONTRAST_OFFSET)
    }

    private const val RGB = 0xFFFFFF
    private const val OPAQUE = 0xFF000000.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val HEX = 16
    private const val HEX_DIGITS = 6
    private const val BYTE = 0xFF
    private const val HALF = 0.5f
    private const val RED = 16
    private const val GREEN = 8
    private const val BLUE = 0
    private const val PRESSED_MIX = 0.25f
    private const val SECONDARY_MIX = 0.45f
    private const val LIGHT_LUMINANCE = 0.4
    private const val SRGB_KNEE = 0.03928
    private const val SRGB_SLOPE = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_GAMMA = 2.4
    private const val LUM_R = 0.2126
    private const val LUM_G = 0.7152
    private const val LUM_B = 0.0722
    private const val CONTRAST_OFFSET = 0.05
}
