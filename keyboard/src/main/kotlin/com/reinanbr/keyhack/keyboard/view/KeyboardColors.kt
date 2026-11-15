package com.reinanbr.keyhack.keyboard.view

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.reinanbr.keyhack.keyboard.R

/** Colors used by [KeyboardView]. M1 replaces the resource defaults with themes. */
data class KeyboardColors(
    @param:ColorInt val background: Int,
    @param:ColorInt val key: Int,
    @param:ColorInt val functionKey: Int,
    @param:ColorInt val pressedKey: Int,
    @param:ColorInt val label: Int,
    @param:ColorInt val accent: Int,
) {
    companion object {
        fun fromResources(context: Context) = KeyboardColors(
            background = ContextCompat.getColor(context, R.color.keyhack_kb_background),
            key = ContextCompat.getColor(context, R.color.keyhack_kb_key),
            functionKey = ContextCompat.getColor(context, R.color.keyhack_kb_function_key),
            pressedKey = ContextCompat.getColor(context, R.color.keyhack_kb_pressed_key),
            label = ContextCompat.getColor(context, R.color.keyhack_kb_label),
            accent = ContextCompat.getColor(context, R.color.keyhack_kb_accent),
        )
    }
}
