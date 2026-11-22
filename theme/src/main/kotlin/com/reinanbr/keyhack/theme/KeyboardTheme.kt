package com.reinanbr.keyhack.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.ColorInt

/** Colors used by the keyboard, the suggestion strip and the panels. */
data class KeyboardColors(
    @param:ColorInt val background: Int,
    @param:ColorInt val key: Int,
    @param:ColorInt val functionKey: Int,
    @param:ColorInt val pressedKey: Int,
    @param:ColorInt val label: Int,
    @param:ColorInt val secondaryLabel: Int,
    @param:ColorInt val accent: Int,
    @param:ColorInt val accentKey: Int,
    @param:ColorInt val onAccent: Int,
    /** Drawn just below each key (iOS style); 0 = no shadow. */
    @param:ColorInt val keyShadow: Int = 0,
) {
    /** True when labels are light, i.e. a dark theme. */
    val isDark: Boolean get() = luminance(label) > luminance(background)

    @Suppress("MagicNumber") // Integer approximation of Rec. 601 luma: 0.3 R + 0.6 G + 0.1 B.
    private fun luminance(c: Int): Int = (c shr 16 and 0xff) * 3 + (c shr 8 and 0xff) * 6 + (c and 0xff)
}

/** Geometry and type of the keys, which a theme can change along with its colors. */
data class KeyboardShape(
    val keyRadiusDp: Float = 6f,
    val keyGapDp: Float = 5f,
    /** Main label size; smaller labels scale with it. */
    val labelSp: Float = 22f,
    /** Small symbols in the key corners (what a swipe up types). */
    val cornerHints: Boolean = true,
    /** Height of the shadow under each key, drawn in [KeyboardColors.keyShadow]. */
    val keyShadowDp: Float = 0f,
)

/**
 * Built-in themes. "system" follows the device's dark mode; "dynamic" uses
 * Material You on Android 12+; "ios" is the iPhone keyboard look (light or
 * dark with the system); "custom" is the user's [CustomTheme].
 */
object KeyboardThemes {
    val ids = listOf("system", "dynamic", "light", "dark", "amoled", "ios", "custom")

    val DEFAULT_SHAPE = KeyboardShape()

    /** iPhone proportions: rounder, more spaced keys with a hairline shadow and no corner hints. */
    val IOS_SHAPE = KeyboardShape(keyRadiusDp = 5f, keyGapDp = 6f, labelSp = 23f, cornerHints = false, keyShadowDp = 1f)

    val IOS_LIGHT =
        KeyboardColors(
            background = 0xFFD1D4D9.toInt(),
            key = 0xFFFFFFFF.toInt(),
            functionKey = 0xFFADB3BC.toInt(),
            pressedKey = 0xFFBFC4CC.toInt(),
            label = 0xFF000000.toInt(),
            secondaryLabel = 0xFF6E6E73.toInt(),
            accent = 0xFF007AFF.toInt(),
            accentKey = 0xFF007AFF.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            keyShadow = 0xFF898A8D.toInt(),
        )

    val IOS_DARK =
        KeyboardColors(
            background = 0xFF262628.toInt(),
            key = 0xFF5C5C5F.toInt(),
            functionKey = 0xFF3A3A3C.toInt(),
            pressedKey = 0xFF77777B.toInt(),
            label = 0xFFFFFFFF.toInt(),
            secondaryLabel = 0xFFA1A1A6.toInt(),
            accent = 0xFF0A84FF.toInt(),
            accentKey = 0xFF0A84FF.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            keyShadow = 0xFF0B0B0C.toInt(),
        )

    fun shape(id: String): KeyboardShape = if (id == "ios") IOS_SHAPE else DEFAULT_SHAPE

    val LIGHT =
        KeyboardColors(
            background = 0xFFE8EAED.toInt(),
            key = 0xFFFFFFFF.toInt(),
            functionKey = 0xFFCBD0D6.toInt(),
            pressedKey = 0xFFAEB4BC.toInt(),
            label = 0xFF202124.toInt(),
            secondaryLabel = 0xFF6B7078.toInt(),
            accent = 0xFF1A73E8.toInt(),
            accentKey = 0xFF1A73E8.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
        )

    val DARK =
        KeyboardColors(
            background = 0xFF1F1F21.toInt(),
            key = 0xFF3B3C3F.toInt(),
            functionKey = 0xFF2B2C2F.toInt(),
            pressedKey = 0xFF5A5B5F.toInt(),
            label = 0xFFE8EAED.toInt(),
            secondaryLabel = 0xFF9AA0A6.toInt(),
            accent = 0xFF8AB4F8.toInt(),
            accentKey = 0xFF8AB4F8.toInt(),
            onAccent = 0xFF102040.toInt(),
        )

    val AMOLED =
        KeyboardColors(
            background = 0xFF000000.toInt(),
            key = 0xFF161618.toInt(),
            functionKey = 0xFF0C0C0D.toInt(),
            pressedKey = 0xFF333336.toInt(),
            label = 0xFFE8EAED.toInt(),
            secondaryLabel = 0xFF80868B.toInt(),
            accent = 0xFF4FC3F7.toInt(),
            accentKey = 0xFF0B5E7A.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
        )

    /** The colors for theme [id]; [custom] is the encoded [CustomTheme], used when [id] is "custom". */
    fun resolve(context: Context, id: String, custom: String = ""): KeyboardColors {
        val night =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return when (id) {
            "light" -> LIGHT
            "dark" -> DARK
            "amoled" -> AMOLED
            "ios" -> if (night) IOS_DARK else IOS_LIGHT
            "custom" -> CustomTheme.colors(custom) ?: system(night)
            "dynamic" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamic(context, night) else system(night)
            else -> system(night)
        }
    }

    private fun system(night: Boolean) = if (night) DARK else LIGHT

    /** Material You palette, laid out like Gboard's dynamic theme. */
    private fun dynamic(context: Context, night: Boolean): KeyboardColors {
        fun c(id: Int) = context.getColor(id)
        return if (night) {
            KeyboardColors(
                background = c(android.R.color.system_neutral1_900),
                key = c(android.R.color.system_neutral1_700),
                functionKey = c(android.R.color.system_neutral2_800),
                pressedKey = c(android.R.color.system_neutral1_500),
                label = c(android.R.color.system_neutral1_50),
                secondaryLabel = c(android.R.color.system_neutral2_300),
                accent = c(android.R.color.system_accent1_200),
                accentKey = c(android.R.color.system_accent1_300),
                onAccent = c(android.R.color.system_accent1_900),
            )
        } else {
            KeyboardColors(
                background = c(android.R.color.system_neutral1_100),
                key = c(android.R.color.system_neutral1_0),
                functionKey = c(android.R.color.system_accent2_100),
                pressedKey = c(android.R.color.system_neutral2_300),
                label = c(android.R.color.system_neutral1_900),
                secondaryLabel = c(android.R.color.system_neutral2_500),
                accent = c(android.R.color.system_accent1_600),
                accentKey = c(android.R.color.system_accent1_600),
                onAccent = c(android.R.color.system_accent1_0),
            )
        }
    }
}
