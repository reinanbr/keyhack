package com.reinanbr.keyhack.keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.reinanbr.keyhack.theme.KeyboardColors
import com.reinanbr.keyhack.theme.KeyboardThemes
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay that draws long-press alternatives and the key-press preview
 * balloon above a key. It never takes
 * touches: the gesture stays with [KeyboardView], which moves the selection.
 * It must be placed over the whole input view so popups for the top row can
 * extend above the keyboard (over the suggestion strip).
 */
class KeyPopupView(context: Context) : View(context) {
    var colors: KeyboardColors = KeyboardThemes.LIGHT
        set(value) {
            field = value
            invalidate()
        }

    private var items: List<String> = emptyList()
    private var columns = 1
    private var selected = -1
    private val panel = RectF()
    private var cellW = 0f
    private var cellH = 0f

    private val density = resources.displayMetrics.density
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, resources.displayMetrics)
        }
    private val cell = RectF()

    // Key-press preview: the pressed key's label, enlarged above it.
    private var preview: String? = null
    private val balloon = RectF()
    private val previewPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, PREVIEW_SP, resources.displayMetrics)
        }

    /** Shows [label] in a balloon above the key at [anchor] (this view's coordinates). */
    fun showPreview(label: String, anchor: RectF) {
        preview = label
        val w = max(anchor.width() * PREVIEW_WIDTH, MIN_CELL_DP * density)
        val h = anchor.height() * PREVIEW_HEIGHT
        val left = (anchor.centerX() - w / 2f).coerceIn(0f, max(0f, width - w))
        val bottom = anchor.top - PADDING_DP * density
        balloon.set(left, max(0f, bottom - h), left + w, max(h, bottom))
        invalidate()
    }

    fun hidePreview() {
        if (preview == null) return
        preview = null
        invalidate()
    }

    val isShowing: Boolean get() = items.isNotEmpty()

    // Never ask for space: inside a wrap_content parent (the IME input view) a
    // default View would claim the whole screen and push the keyboard to the
    // top. FrameLayout re-measures match_parent children to its final size.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fun size(spec: Int) = if (MeasureSpec.getMode(spec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(spec) else 0
        setMeasuredDimension(size(widthMeasureSpec), size(heightMeasureSpec))
    }

    /** Shows [entries] above the key at [anchor] (in this view's coordinates). */
    fun show(entries: List<String>, anchor: RectF, initial: Int = 0) {
        items = entries
        columns = min(entries.size, MAX_COLUMNS)
        val rows = ceil(entries.size / columns.toFloat()).toInt()
        cellW = max(anchor.width(), MIN_CELL_DP * density)
        cellH = anchor.height()
        val w = cellW * columns
        val h = cellH * rows
        val left = (anchor.centerX() - cellW / 2f - cellW * (columns / 2)).coerceIn(0f, max(0f, width - w))
        val top = max(0f, anchor.top - h - PADDING_DP * density)
        panel.set(left, top, left + w, top + h)
        selected = initial.coerceIn(0, entries.lastIndex)
        invalidate()
    }

    /** Moves the selection to the cell under ([x], [y]); returns the selected text. */
    fun track(x: Float, y: Float): String? {
        if (items.isEmpty()) return null
        val col = ((x - panel.left) / cellW).toInt().coerceIn(0, columns - 1)
        val row = ((y - panel.top) / cellH).toInt().coerceIn(0, (items.size - 1) / columns)
        val index = (row * columns + col).coerceIn(0, items.lastIndex)
        if (index != selected) {
            selected = index
            invalidate()
        }
        return items[selected]
    }

    fun selectedText(): String? = items.getOrNull(selected)

    fun dismiss() {
        if (items.isEmpty()) return
        items = emptyList()
        selected = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val label = preview
        if (items.isEmpty() && label != null) drawPreview(canvas, label)
        if (items.isEmpty()) return
        val r = RADIUS_DP * density
        panelPaint.color = colors.key
        panelPaint.setShadowLayer(SHADOW_DP * density, 0f, density, SHADOW_COLOR)
        canvas.drawRoundRect(panel, r, r, panelPaint)
        selPaint.color = colors.accentKey
        for (i in items.indices) {
            val col = i % columns
            val row = i / columns
            cell.set(
                panel.left + col * cellW,
                panel.top + row * cellH,
                panel.left + (col + 1) * cellW,
                panel.top + (row + 1) * cellH,
            )
            if (i == selected) canvas.drawRoundRect(cell, r, r, selPaint)
            textPaint.color = if (i == selected) colors.onAccent else colors.label
            val baseline = cell.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(items[i], cell.centerX(), baseline, textPaint)
        }
    }

    private fun drawPreview(canvas: Canvas, label: String) {
        val r = RADIUS_DP * density
        panelPaint.color = colors.key
        panelPaint.setShadowLayer(SHADOW_DP * density, 0f, density, SHADOW_COLOR)
        canvas.drawRoundRect(balloon, r, r, panelPaint)
        previewPaint.color = colors.label
        val baseline = balloon.centerY() - (previewPaint.descent() + previewPaint.ascent()) / 2f
        canvas.drawText(label, balloon.centerX(), baseline, previewPaint)
    }

    companion object {
        private const val PREVIEW_SP = 30f
        private const val PREVIEW_WIDTH = 1.25f
        private const val PREVIEW_HEIGHT = 1.15f
        private const val MAX_COLUMNS = 8
        private const val MIN_CELL_DP = 36f
        private const val PADDING_DP = 4f
        private const val RADIUS_DP = 8f
        private const val SHADOW_DP = 4f
        private const val SHADOW_COLOR = 0x55000000
        private const val TEXT_SP = 22f
    }
}
