package com.reinanbr.keyhack.keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import com.reinanbr.keyhack.keyboard.model.KeyAction
import com.reinanbr.keyhack.keyboard.model.KeyGeometry
import com.reinanbr.keyhack.keyboard.model.KeyboardLayout
import com.reinanbr.keyhack.keyboard.model.PlacedKey
import com.reinanbr.keyhack.keyboard.model.ShiftState
import com.reinanbr.keyhack.keyboard.model.displayLabel

/**
 * Canvas-drawn key grid. It reports [KeyAction]s to [listener] and holds no
 * typed text: the IME service owns the InputConnection.
 *
 * onDraw allocates nothing; geometry is rebuilt only on size/layout changes.
 */
class KeyboardView(context: Context) : View(context) {
    fun interface Listener {
        fun onKey(action: KeyAction)
    }

    var listener: Listener? = null

    var layout: KeyboardLayout? = null
        set(value) {
            field = value
            rebuildGeometry()
            requestLayout()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            accessibilityHelper.invalidateRoot()
        }

    /** Label for the Enter key, chosen by the IME from the editor's action. */
    var enterLabel: String = "⏎"
        set(value) {
            field = value
            invalidate()
        }

    var colors: KeyboardColors = KeyboardColors.fromResources(context)
        set(value) {
            field = value
            invalidate()
        }

    internal var geometry: KeyGeometry? = null
        private set

    private val density = resources.displayMetrics.density
    private val rowHeightPx = ROW_HEIGHT_DP * density
    private val gapPx = KEY_GAP_DP * density
    private val radiusPx = KEY_RADIUS_DP * density
    private val verticalPaddingPx = VERTICAL_PADDING_DP * density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sp(LABEL_SP)
        }
    private val smallLabelPaint =
        Paint(labelPaint).apply { textSize = sp(SMALL_LABEL_SP) }
    private val rect = RectF()

    /** Key index pressed by each active pointer id (-1 = none). */
    private val pressedByPointer = IntArray(MAX_POINTERS) { NO_KEY }

    private var repeatingKey = NO_KEY
    private val repeatRunnable =
        object : Runnable {
            override fun run() {
                val g = geometry ?: return
                if (repeatingKey == NO_KEY) return
                listener?.onKey(g.keys[repeatingKey].key.action)
                postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

    private val accessibilityHelper = KeyboardAccessibilityHelper(this)

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        isHapticFeedbackEnabled = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rows?.size ?: 0
        val height = (rows * rowHeightPx + 2 * verticalPaddingPx).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildGeometry()
    }

    private fun rebuildGeometry() {
        val l = layout
        geometry =
            if (l == null || width == 0 || height == 0) {
                null
            } else {
                KeyGeometry(l, width.toFloat(), height - 2 * verticalPaddingPx, gapPx)
            }
        cancelAllPresses()
        accessibilityHelper.invalidateRoot()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colors.background)
        val g = geometry ?: return
        val shifted = shiftState != ShiftState.OFF
        for (i in g.keys.indices) {
            val placed = g.keys[i]
            keyPaint.color = keyColor(placed, isPressed(i))
            rect.set(
                placed.bounds.left,
                placed.bounds.top + verticalPaddingPx,
                placed.bounds.right,
                placed.bounds.bottom + verticalPaddingPx,
            )
            canvas.drawRoundRect(rect, radiusPx, radiusPx, keyPaint)

            val text = labelFor(placed, shifted)
            if (text.isEmpty()) continue
            val paint = if (text.length > 1) smallLabelPaint else labelPaint
            paint.color = if (placed.key.action == KeyAction.Shift && shifted) colors.accent else colors.label
            val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, rect.centerX(), baseline, paint)
        }
    }

    internal fun labelFor(placed: PlacedKey, shifted: Boolean = shiftState != ShiftState.OFF): String =
        when (placed.key.action) {
            KeyAction.Enter -> enterLabel
            KeyAction.Shift -> if (shiftState == ShiftState.LOCKED) "⇪" else placed.key.label
            else -> placed.key.displayLabel(shifted)
        }

    private fun keyColor(placed: PlacedKey, pressed: Boolean): Int = when {
        pressed -> colors.pressedKey
        placed.key.action is KeyAction.Character || placed.key.action == KeyAction.Space -> colors.key
        else -> colors.functionKey
    }

    private fun isPressed(index: Int): Boolean = pressedByPointer.any { it == index }

    // Touch is handled per pointer so fast two-thumb typing never drops keys.
    // Accessibility clicks go through KeyboardAccessibilityHelper instead of
    // performClick, because a keyboard has one virtual view per key.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = geometry ?: return false
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        val tracked = pointerId < MAX_POINTERS
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> if (tracked) {
                val key = g.keyIndexAt(event.getX(index), event.getY(index) - verticalPaddingPx)
                pressedByPointer[pointerId] = key
                if (key != NO_KEY) onKeyDown(key)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (tracked) {
                val key = pressedByPointer[pointerId]
                pressedByPointer[pointerId] = NO_KEY
                if (key != NO_KEY) onKeyUp(key)
            }

            MotionEvent.ACTION_CANCEL -> cancelAllPresses()
        }
        return true
    }

    private fun onKeyDown(index: Int) {
        val g = geometry ?: return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (g.keys[index].key.repeats) {
            // Repeating keys fire on press, then repeat while held.
            listener?.onKey(g.keys[index].key.action)
            repeatingKey = index
            postDelayed(repeatRunnable, REPEAT_START_DELAY_MS)
        }
        invalidate()
    }

    private fun onKeyUp(index: Int) {
        val g = geometry ?: return
        if (index == repeatingKey) {
            stopRepeat()
        } else if (!g.keys[index].key.repeats) {
            listener?.onKey(g.keys[index].key.action)
        }
        invalidate()
    }

    /** Activates key [index] as if tapped; used by accessibility actions. */
    internal fun activateKey(index: Int) {
        val g = geometry ?: return
        listener?.onKey(g.keys.getOrNull(index)?.key?.action ?: return)
    }

    private fun stopRepeat() {
        repeatingKey = NO_KEY
        removeCallbacks(repeatRunnable)
    }

    private fun cancelAllPresses() {
        pressedByPointer.fill(NO_KEY)
        stopRepeat()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAllPresses()
        super.onDetachedFromWindow()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    internal val keyTopOffset: Float get() = verticalPaddingPx

    companion object {
        private const val NO_KEY = -1
        private const val MAX_POINTERS = 10
        private const val ROW_HEIGHT_DP = 54f
        private const val KEY_GAP_DP = 6f
        private const val KEY_RADIUS_DP = 6f
        private const val VERTICAL_PADDING_DP = 4f
        private const val LABEL_SP = 22f
        private const val SMALL_LABEL_SP = 15f
        private const val REPEAT_START_DELAY_MS = 400L
        private const val REPEAT_INTERVAL_MS = 50L
    }
}
