package com.reinanbr.keyhack.keyboard.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.reinanbr.keyhack.keyboard.model.GlideStroke
import com.reinanbr.keyhack.keyboard.model.Key
import com.reinanbr.keyhack.keyboard.model.KeyAction
import com.reinanbr.keyhack.keyboard.model.KeyGeometry
import kotlin.math.hypot

/**
 * Glide typing for [KeyboardView]: one finger's stroke over the letters
 * ([GlideStroke] decides when it is a glide), its trail, and the path in
 * key units for the listener. Coordinates are the view's pixels; the keys
 * start [topOffset] below the view's top.
 */
internal class GlideInput(density: Float, private val topOffset: Float) {
    var enabled = false

    /** The pointer whose stroke this is, or -1. */
    var pointer = -1
        private set
    private var stroke: GlideStroke? = null
    private val path = Path()
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = TRAIL_DP * density
        }

    val gliding: Boolean get() = stroke?.gliding == true

    /** A finger lands on key [index]; it may become a glide when it is a letter and no other finger is down. */
    fun start(g: KeyGeometry, id: Int, index: Int, x: Float, y: Float, othersDown: Boolean) {
        cancel()
        if (!enabled || othersDown || !isLetter(g.keys[index].key)) return
        stroke = GlideStroke(index, x, y, g.width / g.layout.unitWidth)
        pointer = id
    }

    /** Adds a point; returns 1 when the stroke just became a glide, 0 while gliding, -1 when not (yet) one. */
    fun track(g: KeyGeometry, x: Float, y: Float): Int {
        val s = stroke ?: return NOT_GLIDING
        val under = g.keyIndexAt(x, y - topOffset)
        val became = s.add(x, y, under, under >= 0 && isLetter(g.keys[under].key))
        return when {
            became -> BECAME
            s.gliding -> GLIDING
            else -> NOT_GLIDING
        }
    }

    /** At release: is the stroke a glide? */
    fun endsAsGlide(g: KeyGeometry): Boolean {
        val s = stroke ?: return false
        val x = s.x(s.size - 1)
        val y = s.y(s.size - 1) - topOffset
        val under = g.keyIndexAt(x, y)
        val target = g.keys.getOrNull(under)?.takeIf { under != s.startKey && isLetter(it.key) }
        return s.glideOnRelease(target?.let { hypot(x - it.bounds.centerX, y - it.bounds.centerY) })
    }

    /** Ends the stroke and hands its path, in key units, to [send] (x in key widths, y in rows, aspect). */
    fun finish(g: KeyGeometry, send: (FloatArray, FloatArray, Float) -> Unit) {
        val s = stroke ?: return
        cancel()
        val unit = g.width / g.layout.unitWidth
        val unitH = g.height / g.layout.unitHeight
        send(FloatArray(s.size) { s.x(it) / unit }, FloatArray(s.size) { (s.y(it) - topOffset) / unitH }, unitH / unit)
    }

    /** Forgets the stroke; true when a trail was showing (the view should redraw). */
    fun cancel(): Boolean {
        val had = gliding
        stroke = null
        pointer = -1
        return had
    }

    fun draw(canvas: Canvas, color: Int) {
        val s = stroke?.takeIf { it.gliding } ?: return
        path.rewind()
        path.moveTo(s.x(0), s.y(0))
        for (i in 1 until s.size) path.lineTo(s.x(i), s.y(i))
        paint.color = color
        paint.alpha = TRAIL_ALPHA
        canvas.drawPath(path, paint)
    }

    private fun isLetter(key: Key): Boolean {
        val text = (key.action as? KeyAction.Character)?.text ?: return false
        return text.length == 1 && text[0].isLetter()
    }

    companion object {
        const val BECAME = 1
        const val GLIDING = 0
        const val NOT_GLIDING = -1
        private const val TRAIL_DP = 5f
        private const val TRAIL_ALPHA = 170
    }
}
