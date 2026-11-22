package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.theme.KeyboardColors

/**
 * The column beside the keyboard in one-handed mode: move the keyboard to the
 * other side, or go back to full width.
 */
class OneHandBar(context: Context) : LinearLayout(context) {
    fun interface Listener {
        /** [action] is [SWITCH_SIDE], [FULL_WIDTH] or [FLOAT]. */
        fun onOneHandAction(action: String)
    }

    var listener: Listener? = null

    private val switchSide = button("⇄", SWITCH_SIDE)
    private val fullWidth = button("⤢", FULL_WIDTH)
    private val float = button("⧉", FLOAT)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(switchSide, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(fullWidth, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(float, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Labels for TalkBack, from the IME's localized strings. */
    fun setDescriptions(switchSideLabel: String, fullWidthLabel: String, floatLabel: String) {
        switchSide.contentDescription = switchSideLabel
        fullWidth.contentDescription = fullWidthLabel
        float.contentDescription = floatLabel
    }

    fun applyColors(colors: KeyboardColors) {
        setBackgroundColor(colors.background)
        switchSide.setTextColor(colors.secondaryLabel)
        fullWidth.setTextColor(colors.secondaryLabel)
        float.setTextColor(colors.secondaryLabel)
    }

    private fun button(icon: String, action: String) = TextView(context).apply {
        text = icon
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ICON_SP)
        isClickable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            listener?.onOneHandAction(action)
        }
    }

    companion object {
        const val SWITCH_SIDE = "switch"
        const val FULL_WIDTH = "full"
        const val FLOAT = "float"
        private const val ICON_SP = 28f
    }
}
