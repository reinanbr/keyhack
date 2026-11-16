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
) {
    /** True when labels are light, i.e. a dark theme. */
    val isDark: Boolean get() = luminance(label) > luminance(background)

    @Suppress("MagicNumber") // Integer approximation of Rec. 601 luma: 0.3 R + 0.6 G + 0.1 B.
    private fun luminance(c: Int): Int = (c shr 16 and 0xff) * 3 + (c shr 8 and 0xff) * 6 + (c and 0xff)
}

/** Built-in themes. "system" follows the device's dark mode; "dynamic" uses Material You on Android 12+. */
object KeyboardThemes {
    val ids = listOf("system", "dynamic", "light", "dark", "amoled")

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

    fun resolve(context: Context, id: String): KeyboardColors {
        val night =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        return when (id) {
            "light" -> LIGHT
            "dark" -> DARK
            "amoled" -> AMOLED
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
