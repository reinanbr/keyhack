package com.reinanbr.keyhack.ime.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.reinanbr.keyhack.emoji.EmojiFont
import com.reinanbr.keyhack.theme.KeyboardColors
import com.reinanbr.keyhack.theme.KeyboardThemes

/**
 * A one-line strip of tappable items, drawn on a canvas: the suggestion bar,
 * the toolbar, panel tab bars and the in-keyboard search box all use it.
 */
class StripView(context: Context, private val heightDp: Float = DEFAULT_HEIGHT_DP) : View(context) {
    fun interface Listener {
        fun onItem(id: String)
    }

    data class Item(
        val id: String,
        val text: String,
        val weight: Float = 1f,
        val bold: Boolean = false,
        val accent: Boolean = false,
        val dim: Boolean = false,
        val large: Boolean = false,
        /** Drawn with the emoji font (large). */
        val emoji: Boolean = false,
    )

    var listener: Listener? = null

    var colors: KeyboardColors = KeyboardThemes.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    var dividers = false
        private set

    var items: List<Item> = emptyList()
        private set

    private var pressed = -1
    private val density = resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sp(TEXT_SP)
        }
    private val boldPaint = TextPaint(textPaint).apply { typeface = Typeface.DEFAULT_BOLD }
    private val largePaint = TextPaint(textPaint).apply { textSize = sp(LARGE_SP) }
    private val emojiPaint = TextPaint(largePaint).apply { typeface = EmojiFont.typeface(context) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun show(newItems: List<Item>, withDividers: Boolean = false) {
        items = newItems
        dividers = withDividers
        pressed = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (heightDp * density).toInt())
    }

    private fun totalWeight() = items.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(MIN_TOTAL_WEIGHT)

    private fun indexAt(x: Float): Int {
        val unit = width / totalWeight()
        var left = 0f
        for (i in items.indices) {
            left += items[i].weight * unit
            if (x < left) return i
        }
        return items.lastIndex
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colors.background)
        if (items.isEmpty()) return
        val unit = width / totalWeight()
        var left = 0f
        val pad = PAD_DP * density
        for (i in items.indices) {
            val item = items[i]
            val w = item.weight * unit
            rect.set(left + pad, pad, left + w - pad, height - pad)
            if (i == pressed || item.accent) {
                fill.color = if (item.accent && i != pressed) colors.accentKey else colors.pressedKey
                canvas.drawRoundRect(rect, RADIUS_DP * density, RADIUS_DP * density, fill)
            }
            val paint =
                when {
                    item.emoji -> emojiPaint
                    item.large -> largePaint
                    item.bold -> boldPaint
                    else -> textPaint
                }
            paint.color =
                when {
                    item.accent && i != pressed -> colors.onAccent
                    item.dim -> colors.secondaryLabel
                    else -> colors.label
                }
            if (item.text.isNotEmpty()) {
                val text = TextUtils.ellipsize(item.text, paint, rect.width() - pad, TextUtils.TruncateAt.END)
                val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(text, 0, text.length, rect.centerX(), baseline, paint)
            }
            if (dividers && i > 0) {
                fill.color = colors.functionKey
                canvas.drawRect(
                    left - density / 2,
                    height * DIVIDER_INSET,
                    left + density / 2,
                    height * (1 - DIVIDER_INSET),
                    fill,
                )
            }
            left += w
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = indexAt(event.x)
                invalidate()
            }

            MotionEvent.ACTION_MOVE ->
                if (pressed >= 0 && indexAt(event.x) != pressed) {
                    pressed = -1
                    invalidate()
                }

            MotionEvent.ACTION_UP -> {
                val i = pressed
                pressed = -1
                invalidate()
                val item = items.getOrNull(i)
                if (item != null && item.id.isNotEmpty()) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onItem(item.id)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
            }
        }
        return true
    }

    companion object {
        const val DEFAULT_HEIGHT_DP = 44f
        private const val TEXT_SP = 16f
        private const val LARGE_SP = 20f
        private const val PAD_DP = 3f
        private const val RADIUS_DP = 8f
        private const val DIVIDER_INSET = 0.3f
        private const val MIN_TOTAL_WEIGHT = 1e-3f
    }
}
