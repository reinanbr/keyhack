package com.reinanbr.keyhack.keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import com.reinanbr.keyhack.keyboard.model.Key
import com.reinanbr.keyhack.keyboard.model.KeyAction
import com.reinanbr.keyhack.keyboard.model.KeyGeometry
import com.reinanbr.keyhack.keyboard.model.KeyboardLayout
import com.reinanbr.keyhack.keyboard.model.Modifier
import com.reinanbr.keyhack.keyboard.model.PlacedKey
import com.reinanbr.keyhack.keyboard.model.ShiftState
import com.reinanbr.keyhack.keyboard.model.StickyState
import com.reinanbr.keyhack.keyboard.model.displayLabel
import com.reinanbr.keyhack.keyboard.model.popupFor
import com.reinanbr.keyhack.theme.KeyboardColors
import com.reinanbr.keyhack.theme.KeyboardShape
import com.reinanbr.keyhack.theme.KeyboardThemes
import kotlin.math.abs

/**
 * Canvas-drawn key grid. It reports actions to [listener] and holds no typed
 * text: the IME service owns the InputConnection.
 *
 * Gestures: tap; hold for long-press alternatives (slide to pick, release to
 * commit); hold Delete or an arrow to repeat; slide on the space bar to move
 * the cursor; slide Delete to the left to select whole words, released to
 * delete them; with [glideEnabled], slide across the letters to type a word
 * (glide typing). A second finger landing commits the first key (rollover).
 *
 * onDraw allocates nothing; geometry is rebuilt only on size/layout changes.
 */
class KeyboardView(context: Context) : View(context) {
    interface Listener {
        fun onKey(key: Key, action: KeyAction)

        fun onPopupText(text: String)

        /** Space-bar trackpad: [steps] cursor moves, positive = right. */
        fun onCursorSwipe(steps: Int)

        /** Shift pressed; it fires on press so keys typed while it is held come out shifted. */
        fun onShiftDown()

        /** The finger holding Shift was lifted. */
        fun onShiftUp()

        /** A held key repeats; [count] is 1 for the first repeat. The IME may speed up (words instead of letters). */
        fun onRepeat(key: Key, count: Int) = onKey(key, key.action)

        /** Delete dragged left: select the [words] words before the cursor (0: back to none). */
        fun onDeleteSwipe(words: Int) = Unit

        /** The finger dragging Delete lifted: delete what [onDeleteSwipe] selected. */
        fun onDeleteSwipeEnd() = Unit

        /**
         * A glide over the letters ended. The path is in key units (x in key
         * widths, y in rows, like [KeyboardLayout.letterCenters]); [aspect] is
         * a row's height over a key's width.
         */
        fun onGlide(xs: FloatArray, ys: FloatArray, aspect: Float) = Unit
    }

    var listener: Listener? = null

    /** Overlay used to draw long-press alternatives. */
    var popupView: KeyPopupView? = null

    var layout: KeyboardLayout? = null
        set(value) {
            if (field == value) return
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

    var modifierStates: Map<Modifier, StickyState> = emptyMap()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Label for the Enter key, chosen by the IME from the editor's action. */
    var enterLabel: String = "⏎"
        set(value) {
            field = value
            invalidate()
        }

    /** Small label drawn on the space bar (current languages). */
    var spaceLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** Layer keys whose target equals this are drawn as active (e.g. Fn while in the fn layer). */
    var activeLayer: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var colors: KeyboardColors = KeyboardThemes.LIGHT
        set(value) {
            field = value
            popupView?.colors = value
            invalidate()
        }

    /** Key geometry and label size from the theme. */
    var shape: KeyboardShape = KeyboardThemes.DEFAULT_SHAPE
        set(value) {
            if (field == value) return
            field = value
            applyShape()
            rebuildGeometry()
        }

    var showHints: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Row height multiplier from settings (0.7..1.4). */
    var heightScale: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** Show the pressed character enlarged above the key (needs [popupView]). */
    var keyPreview: Boolean = true
        set(value) {
            field = value
            if (!value) hidePreview()
        }

    var hapticsEnabled: Boolean = true

    /** Glide typing: sliding across the letters types a word (the IME decodes it). */
    var glideEnabled: Boolean
        get() = glide.enabled
        set(value) {
            glide.enabled = value
            if (!value) cancelGlide()
        }

    /** Key-press vibration length in ms; 0 uses the system's keyboard haptic (which follows its touch setting). */
    var vibrationMs: Int = 0
    var soundEnabled: Boolean = false
    var longPressMillis: Long = DEFAULT_LONG_PRESS_MS

    internal var geometry: KeyGeometry? = null
        private set

    private val density = resources.displayMetrics.density
    private var gapPx = shape.keyGapDp * density
    private var radiusPx = shape.keyRadiusDp * density
    private var shadowPx = shape.keyShadowDp * density
    private val verticalPaddingPx = VERTICAL_PADDING_DP * density
    private val swipeStepPx = SWIPE_STEP_DP * density
    private val swipeSlopPx = SWIPE_SLOP_DP * density
    private val flickSlopPx = FLICK_SLOP_DP * density
    private val deleteStepPx = DELETE_STEP_DP * density

    private val glide = GlideInput(density, verticalPaddingPx)
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sp(shape.labelSp)
        }
    private val smallLabelPaint = Paint(labelPaint).apply { textSize = sp(shape.labelSp * SMALL_LABEL_RATIO) }
    private val tinyLabelPaint = Paint(labelPaint).apply { textSize = sp(shape.labelSp * TINY_LABEL_RATIO) }
    private val hintPaint =
        Paint(labelPaint).apply {
            textSize = sp(HINT_SP)
            textAlign = Paint.Align.RIGHT
        }
    private val boldSmall = Paint(smallLabelPaint).apply { typeface = Typeface.DEFAULT_BOLD }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun applyShape() {
        gapPx = shape.keyGapDp * density
        radiusPx = shape.keyRadiusDp * density
        shadowPx = shape.keyShadowDp * density
        labelPaint.textSize = sp(shape.labelSp)
        smallLabelPaint.textSize = sp(shape.labelSp * SMALL_LABEL_RATIO)
        tinyLabelPaint.textSize = sp(shape.labelSp * TINY_LABEL_RATIO)
        boldSmall.textSize = smallLabelPaint.textSize
    }
    private val rect = RectF()
    private val audio = context.getSystemService(AudioManager::class.java)

    private class Pointer {
        var key = NO_KEY
        var downX = 0f
        var downY = 0f
        var lastStepX = 0f
        var consumed = false
        var popup = false
        var trackpad = false

        /** Swiped up on a key with a hint: types the hint instead of the key. */
        var flick = false

        /** Dragging Delete to the left; [deleteWords] words are selected. */
        var deleteSwipe = false
        var deleteWords = 0

        fun clear() {
            key = NO_KEY
            consumed = false
            popup = false
            trackpad = false
            flick = false
            deleteSwipe = false
            deleteWords = 0
        }
    }

    private val pointers = Array(MAX_POINTERS) { Pointer() }

    private var repeatingKey = NO_KEY
    private var repeatCount = 0
    private val repeatRunnable =
        object : Runnable {
            override fun run() {
                val g = geometry ?: return
                if (repeatingKey == NO_KEY) return
                val key = g.keys[repeatingKey].key
                listener?.onRepeat(key, ++repeatCount)
                postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

    private var longPressPointer = -1

    /** Pointer holding the Shift key, or -1. */
    private var shiftPointer = -1

    /** The finger whose key the preview balloon shows, or -1. */
    private var previewPointer = -1
    private val longPressRunnable = Runnable { onLongPress() }

    private val accessibilityHelper = KeyboardAccessibilityHelper(this)

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        isHapticFeedbackEnabled = true
    }

    private val rowHeightPx: Float get() = ROW_HEIGHT_DP * density * heightScale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val units = layout?.unitHeight ?: 0f
        val height = (units * rowHeightPx + 2 * verticalPaddingPx).toInt()
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
            val key = placed.key
            val sticky = stickyState(key)
            val accent = sticky != StickyState.OFF || key.action == KeyAction.Enter
            drawKeyBackground(canvas, placed, isPressed(i), accent)
            val fg = if (accent && !isPressed(i)) colors.onAccent else colors.label
            if (sticky == StickyState.LOCKED) {
                keyPaint.color = fg
                val bar = LOCK_BAR_DP * density
                // A short underline marks a locked modifier.
                canvas.drawRect(
                    rect.centerX() - LOCK_BAR_HALF_WIDTH * bar,
                    rect.bottom - LOCK_BAR_BOTTOM_GAP * bar - bar,
                    rect.centerX() + LOCK_BAR_HALF_WIDTH * bar,
                    rect.bottom - LOCK_BAR_BOTTOM_GAP * bar,
                    keyPaint,
                )
            }

            drawHint(canvas, key, shifted, accent)

            if (key.action == KeyAction.Space) {
                if (spaceLabel.isNotEmpty()) {
                    tinyLabelPaint.color = colors.secondaryLabel
                    drawCentered(canvas, spaceLabel, tinyLabelPaint)
                }
                continue
            }
            val text = visibleLabel(i, placed, shifted)
            if (text.isEmpty()) continue
            val paint =
                when {
                    text.codePointCount(0, text.length) <= 1 -> labelPaint
                    text.length <= 2 -> smallLabelPaint
                    key.action == KeyAction.Enter -> boldSmall
                    else -> tinyLabelPaint
                }
            paint.color = fg
            drawCentered(canvas, text, paint)
        }
        glide.draw(canvas, colors.accent)
    }

    /** The key's rounded rectangle (and its shadow, if the shape has one); leaves its bounds in [rect]. */
    private fun drawKeyBackground(canvas: Canvas, placed: PlacedKey, pressed: Boolean, accent: Boolean) {
        val key = placed.key
        keyPaint.color =
            when {
                pressed -> colors.pressedKey
                accent -> colors.accentKey
                key.isFunction -> colors.functionKey
                else -> colors.key
            }
        rect.set(
            placed.bounds.left,
            placed.bounds.top + verticalPaddingPx,
            placed.bounds.right,
            placed.bounds.bottom + verticalPaddingPx,
        )
        if (shadowPx > 0f && colors.keyShadow != 0) {
            shadowPaint.color = colors.keyShadow
            rect.offset(0f, shadowPx)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, shadowPaint)
            rect.offset(0f, -shadowPx)
        }
        canvas.drawRoundRect(rect, radiusPx, radiusPx, keyPaint)
    }

    /** The swipe-up symbol in the key's corner, unless the shape hides hints or Shift changes the key. */
    private fun drawHint(canvas: Canvas, key: Key, shifted: Boolean, accent: Boolean) {
        val hint = key.hint ?: return
        if (!showHints || !shape.cornerHints) return
        val action = key.action
        if (shifted && action is KeyAction.Character && action.shifted != null) return
        hintPaint.color = if (accent) colors.onAccent else colors.secondaryLabel
        canvas.drawText(
            hint,
            rect.right - HINT_INSET_DP * density,
            rect.top - hintPaint.ascent() + HINT_INSET_DP * density / 2,
            hintPaint,
        )
    }

    private fun drawCentered(canvas: Canvas, text: String, paint: Paint) {
        val baseline = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), baseline, paint)
    }

    private fun stickyState(key: Key): StickyState = when (val a = key.action) {
        KeyAction.Shift ->
            when (shiftState) {
                ShiftState.OFF -> StickyState.OFF
                ShiftState.ONCE -> StickyState.ONCE
                ShiftState.LOCKED -> StickyState.LOCKED
            }

        is KeyAction.Mod -> modifierStates[a.modifier] ?: StickyState.OFF

        is KeyAction.Layer -> if (a.target == "alpha" && activeLayer == "fn" &&
            key.label == "Fn"
        ) {
            StickyState.LOCKED
        } else {
            StickyState.OFF
        }

        else -> StickyState.OFF
    }

    internal fun labelFor(placed: PlacedKey, shifted: Boolean = shiftState != ShiftState.OFF): String =
        when (placed.key.action) {
            KeyAction.Enter -> enterLabel
            KeyAction.Shift -> if (shiftState == ShiftState.LOCKED) "⇪" else placed.key.label
            else -> placed.key.displayLabel(shifted)
        }

    private fun isPressed(index: Int): Boolean =
        pointers.withIndex().any { (id, p) -> p.key == index && !p.trackpad && !(id == glide.pointer && glide.gliding) }

    private fun flickedHint(index: Int): String? =
        if (pointers.any { it.key == index && it.flick }) geometry?.keys?.getOrNull(index)?.key?.hint else null

    /** While swiped up, a key previews the symbol it will type. */
    private fun visibleLabel(index: Int, placed: PlacedKey, shifted: Boolean): String =
        flickedHint(index) ?: labelFor(placed, shifted)

    // Touch is handled per pointer so fast two-thumb typing never drops keys.
    // Accessibility clicks go through KeyboardAccessibilityHelper instead of
    // performClick, because a keyboard has one virtual view per key.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = geometry ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                if (id >= MAX_POINTERS) return true
                rollover(except = id)
                val key = g.keyIndexAt(event.getX(index), event.getY(index) - verticalPaddingPx)
                val p = pointers[id]
                p.clear()
                p.key = key
                p.downX = event.getX(index)
                p.downY = event.getY(index)
                p.lastStepX = p.downX
                if (key != NO_KEY) {
                    val othersDown = pointers.withIndex().any { (other, q) -> other != id && q.key != NO_KEY }
                    glide.start(g, id, key, p.downX, p.downY, othersDown)
                    onKeyDown(id, key)
                }
            }

            MotionEvent.ACTION_MOVE ->
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id < MAX_POINTERS) onMove(id, event.getX(i), event.getY(i))
                }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                if (id < MAX_POINTERS) onKeyUp(id)
            }

            MotionEvent.ACTION_CANCEL -> cancelAllPresses()
        }
        return true
    }

    /** A new finger landed: commit keys still held by other fingers now, in order. */
    private fun rollover(except: Int) {
        val g = geometry ?: return
        endGlideFor(except)
        for (id in pointers.indices) {
            val p = pointers[id]
            if (id == except || p.key == NO_KEY || p.consumed || p.popup || p.trackpad) continue
            val key = g.keys[p.key].key
            if (key.action == KeyAction.Delete && !p.deleteSwipe && p.key == repeatingKey && repeatCount == 0) {
                // A tap on Delete not yet fired (it fires on release): fire it first.
                stopRepeat()
                fire(key)
                p.consumed = true
                continue
            }
            if (key.repeats || key.action !is KeyAction.Character) continue
            if (p.flick) key.hint?.let { listener?.onPopupText(it) } else fire(key)
            p.consumed = true
            if (longPressPointer == id) cancelKeyLongPress()
        }
    }

    private fun onKeyDown(pointerId: Int, index: Int) {
        val g = geometry ?: return
        val key = g.keys[index].key
        feedback()
        if (key.action == KeyAction.Shift) {
            pointers[pointerId].consumed = true
            shiftPointer = pointerId
            listener?.onShiftDown()
        } else if (key.repeats) {
            // Repeating keys repeat while held. Delete fires on release (or when it starts
            // repeating), so dragging it to delete words does not first delete a letter;
            // the others fire on press.
            if (key.action != KeyAction.Delete) {
                fire(key)
                pointers[pointerId].consumed = true
            }
            repeatingKey = index
            repeatCount = 0
            postDelayed(repeatRunnable, REPEAT_START_DELAY_MS)
        } else if (key.popup.isNotEmpty() || key.longPress != null) {
            cancelKeyLongPress()
            longPressPointer = pointerId
            postDelayed(longPressRunnable, longPressMillis)
        }
        updatePreview(pointerId)
        invalidate()
    }

    /** Balloon over the key under [pointerId]: characters only, and what a flick would type. */
    private fun updatePreview(pointerId: Int) {
        val g = geometry ?: return
        val popup = popupView ?: return
        val p = pointers[pointerId]
        if (!keyPreview || p.key == NO_KEY || p.popup || p.trackpad) return
        val placed = g.keys[p.key]
        if (placed.key.action !is KeyAction.Character) return
        val (ox, oy) = offsetInPopup()
        val b = placed.bounds
        popup.showPreview(
            visibleLabel(p.key, placed, shiftState != ShiftState.OFF),
            RectF(b.left + ox, b.top + verticalPaddingPx + oy, b.right + ox, b.bottom + verticalPaddingPx + oy),
        )
        previewPointer = pointerId
    }

    private fun hidePreview() {
        previewPointer = -1
        popupView?.hidePreview()
    }

    private fun onMove(pointerId: Int, x: Float, y: Float) {
        val g = geometry ?: return
        val p = pointers[pointerId]
        if (p.key == NO_KEY) return
        val popup = popupView
        if (p.popup && popup != null) {
            val (ox, oy) = offsetInPopup()
            popup.track(x + ox, y + oy)
            return
        }
        if (pointerId == glide.pointer && trackGlide(g, p, x, y)) return
        val key = g.keys[p.key].key
        if (key.action == KeyAction.Delete) {
            trackDeleteSwipe(p, x)
            return
        }
        if (!p.flick && !p.consumed && key.hint != null && key.action is KeyAction.Character &&
            p.downY - y > flickSlopPx
        ) {
            p.flick = true
            if (longPressPointer == pointerId) cancelKeyLongPress()
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (previewPointer == pointerId) updatePreview(pointerId)
            invalidate()
            return
        }
        if (key.action == KeyAction.Space && !p.consumed) trackSpaceSwipe(pointerId, p, x)
    }

    /** Sliding on the space bar moves the cursor, one step per [swipeStepPx]. */
    private fun trackSpaceSwipe(pointerId: Int, p: Pointer, x: Float) {
        if (!p.trackpad && abs(x - p.downX) > swipeSlopPx) {
            p.trackpad = true
            p.lastStepX = x
            if (longPressPointer == pointerId) cancelKeyLongPress()
            invalidate()
        }
        if (p.trackpad) {
            val steps = ((x - p.lastStepX) / swipeStepPx).toInt()
            if (steps != 0) {
                p.lastStepX += steps * swipeStepPx
                listener?.onCursorSwipe(steps)
            }
        }
    }

    /** A finger landing during a glide ends it, as if lifted. */
    private fun endGlideFor(newPointer: Int) {
        val gp = glide.pointer
        if (gp < 0 || gp == newPointer) return
        if (glide.gliding) {
            finishGlide()
            pointers[gp].consumed = true
        } else {
            cancelGlide()
        }
    }

    /** Feeds the glide; true while it is one, so the move does nothing else. */
    private fun trackGlide(g: KeyGeometry, p: Pointer, x: Float, y: Float): Boolean {
        if (p.consumed || p.popup) {
            cancelGlide()
            return false
        }
        val state = glide.track(g, x, y)
        if (state == GlideInput.BECAME) {
            p.flick = false
            if (longPressPointer == glide.pointer) cancelKeyLongPress()
            if (previewPointer == glide.pointer) hidePreview()
        }
        if (state != GlideInput.NOT_GLIDING) invalidate()
        return state != GlideInput.NOT_GLIDING
    }

    private fun finishGlide() {
        val g = geometry ?: return
        glide.finish(g) { xs, ys, aspect -> listener?.onGlide(xs, ys, aspect) }
        invalidate()
    }

    private fun cancelGlide() {
        if (glide.cancel()) invalidate()
    }

    /** Delete dragged left selects words, one more per [deleteStepPx]; dragging back unselects. */
    private fun trackDeleteSwipe(p: Pointer, x: Float) {
        val dx = p.downX - x
        if (!p.deleteSwipe) {
            if (dx <= swipeSlopPx) return
            p.deleteSwipe = true
            stopRepeat()
        }
        val words = ((dx - swipeSlopPx) / deleteStepPx).toInt().let { if (dx > swipeSlopPx) it + 1 else 0 }
        if (words != p.deleteWords) {
            p.deleteWords = words
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            listener?.onDeleteSwipe(words)
        }
    }

    private fun onKeyUp(pointerId: Int) {
        val g = geometry ?: return
        val p = pointers[pointerId]
        val index = p.key
        if (index == NO_KEY) return
        if (longPressPointer == pointerId) cancelKeyLongPress()
        if (shiftPointer == pointerId) releaseShift()
        if (previewPointer == pointerId) hidePreview()
        val key = g.keys[index].key
        val glided = pointerId == glide.pointer && !p.consumed && !p.popup && glide.endsAsGlide(g)
        if (pointerId == glide.pointer) {
            if (glided) finishGlide() else cancelGlide()
        }
        when {
            glided -> Unit

            p.deleteSwipe -> listener?.onDeleteSwipeEnd()

            p.popup -> {
                val text = popupView?.selectedText()
                popupView?.dismiss()
                if (text != null) listener?.onPopupText(text)
            }

            index == repeatingKey -> {
                val tapped = !p.consumed && repeatCount == 0
                stopRepeat()
                if (tapped) fire(key)
            }

            p.consumed || p.trackpad -> Unit

            p.flick -> key.hint?.let { listener?.onPopupText(it) }

            else -> fire(key)
        }
        p.clear()
        invalidate()
    }

    private fun onLongPress() {
        val g = geometry ?: return
        val id = longPressPointer
        longPressPointer = -1
        if (id < 0) return
        val p = pointers[id]
        if (p.key == NO_KEY || p.consumed || p.trackpad) return
        val placed = g.keys[p.key]
        val key = placed.key
        val alternatives = key.popupFor(shiftState != ShiftState.OFF)
        val popup = popupView
        if (alternatives.isNotEmpty() && popup != null) {
            val (ox, oy) = offsetInPopup()
            val b = placed.bounds
            popup.show(
                alternatives,
                RectF(b.left + ox, b.top + verticalPaddingPx + oy, b.right + ox, b.bottom + verticalPaddingPx + oy),
            )
            p.popup = true
            if (previewPointer == id) hidePreview()
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } else if (key.longPress != null) {
            p.consumed = true
            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onKey(key, key.longPress)
        }
    }

    private val loc = IntArray(2)

    private fun offsetInPopup(): Pair<Float, Float> {
        val popup = popupView ?: return 0f to 0f
        getLocationInWindow(loc)
        val kx = loc[0]
        val ky = loc[1]
        popup.getLocationInWindow(loc)
        return (kx - loc[0]).toFloat() to (ky - loc[1]).toFloat()
    }

    private fun fire(key: Key) {
        listener?.onKey(key, key.action)
    }

    // The app's manifest declares VIBRATE; this library's lint cannot see it.
    @SuppressLint("MissingPermission")
    private fun feedback() {
        if (hapticsEnabled) {
            val ms = vibrationMs
            if (ms > 0 && vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        if (soundEnabled) audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, SOUND_VOLUME)
    }

    /** Activates key [index] as if tapped; used by accessibility actions. */
    internal fun activateKey(index: Int) {
        val g = geometry ?: return
        fire(g.keys.getOrNull(index)?.key ?: return)
    }

    private fun cancelKeyLongPress() {
        longPressPointer = -1
        removeCallbacks(longPressRunnable)
    }

    private fun releaseShift() {
        shiftPointer = -1
        listener?.onShiftUp()
    }

    private fun stopRepeat() {
        repeatingKey = NO_KEY
        removeCallbacks(repeatRunnable)
    }

    fun cancelAllPresses() {
        if (shiftPointer >= 0) releaseShift()
        cancelGlide()
        // A cancelled drag deletes nothing.
        if (pointers.any { it.deleteSwipe }) {
            listener?.onDeleteSwipe(0)
            listener?.onDeleteSwipeEnd()
        }
        pointers.forEach { it.clear() }
        stopRepeat()
        cancelKeyLongPress()
        hidePreview()
        popupView?.dismiss()
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
        private const val ROW_HEIGHT_DP = 52f
        private const val VERTICAL_PADDING_DP = 3f
        private const val SMALL_LABEL_RATIO = 17f / 22f
        private const val TINY_LABEL_RATIO = 13f / 22f
        private const val HINT_SP = 10f
        private const val HINT_INSET_DP = 4f
        private const val LOCK_BAR_DP = 2f
        private const val FLICK_SLOP_DP = 22f
        private const val LOCK_BAR_HALF_WIDTH = 3f
        private const val LOCK_BAR_BOTTOM_GAP = 1.5f
        private const val SWIPE_STEP_DP = 10f
        private const val SWIPE_SLOP_DP = 14f
        private const val DELETE_STEP_DP = 28f
        private const val SOUND_VOLUME = 0.3f
        const val DEFAULT_LONG_PRESS_MS = 300L
        private const val REPEAT_START_DELAY_MS = 400L
        private const val REPEAT_INTERVAL_MS = 50L
    }
}
