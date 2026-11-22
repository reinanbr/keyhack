package com.reinanbr.keyhack.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.reinanbr.keyhack.theme.KeyboardColors

/**
 * The handle on top of the floating keyboard: drag it to move the keyboard,
 * tap ▭ at its end to dock it back to the bottom.
 */
class FloatBar(context: Context) : View(context) {
    interface Listener {
        /** The finger moved by ([dx], [dy]) pixels since it went down. */
        fun onFloatDrag(dx: Float, dy: Float)

        fun onFloatDragEnd()

        fun onFloatDock()
    }

    var listener: Listener? = null
    var colors: KeyboardColors? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val grip = RectF()
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var downX = 0f
    private var downY = 0f
    private var onDock = false
    private var dragging = false

    init {
        contentDescription = context.getString(com.reinanbr.keyhack.ime.R.string.keyhack_float_handle)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HEIGHT_DP * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val c = colors ?: return
        canvas.drawColor(c.background)
        val w = GRIP_WIDTH_DP * density
        val h = GRIP_HEIGHT_DP * density
        grip.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        gripPaint.color = c.secondaryLabel
        canvas.drawRoundRect(grip, h / 2f, h / 2f, gripPaint)
        iconPaint.color = c.secondaryLabel
        iconPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, ICON_SP, resources.displayMetrics)
        val cx = width - DOCK_DP * density / 2f
        canvas.drawText(DOCK_ICON, cx, height / 2f - (iconPaint.ascent() + iconPaint.descent()) / 2f, iconPaint)
    }

    @SuppressLint("ClickableViewAccessibility") // Docking is also in the command palette and settings.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                onDock = event.x > width - DOCK_DP * density
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && dx * dx + dy * dy > SLOP_DP * SLOP_DP * density * density) dragging = true
                if (dragging) listener?.onFloatDrag(dx, dy)
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    listener?.onFloatDragEnd()
                } else if (onDock) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onFloatDock()
                }
            }

            MotionEvent.ACTION_CANCEL -> if (dragging) listener?.onFloatDragEnd()
        }
        return true
    }

    private companion object {
        const val HEIGHT_DP = 24f
        const val GRIP_WIDTH_DP = 40f
        const val GRIP_HEIGHT_DP = 4f
        const val DOCK_DP = 48f
        const val SLOP_DP = 4f
        const val ICON_SP = 15f
        const val DOCK_ICON = "▭"
    }
}
