package com.reinanbr.keyhack.keyboard.model

import kotlin.math.abs
import kotlin.math.hypot

/**
 * One finger's path from a letter key, deciding whether it is glide typing
 * or a tap, a slip or a swipe up for the key's corner symbol.
 *
 * It becomes a glide once it has crossed into other letter keys: two of
 * them, or one after moving far enough. A short stroke straight up stays a
 * swipe up (the corner symbol), unless the finger ends on the middle of the
 * key above ("se": s, then up to e). Pixel units; [keyWidth] scales the
 * thresholds.
 */
class GlideStroke(val startKey: Int, x: Float, y: Float, private val keyWidth: Float) {
    private var xs = FloatArray(INITIAL)
    private var ys = FloatArray(INITIAL)
    private val downX = x
    private val downY = y

    var size = 0
        private set
    var length = 0f
        private set
    private val visited = HashSet<Int>()

    /** Decided: this stroke is glide typing. */
    var gliding = false
        private set

    init {
        append(x, y)
    }

    fun x(i: Int) = xs[i]

    fun y(i: Int) = ys[i]

    /**
     * Adds a point; [key] is the key under it and [letter] whether that is a
     * letter key. True when this point made the stroke a glide.
     */
    fun add(x: Float, y: Float, key: Int, letter: Boolean): Boolean {
        val d = hypot(x - xs[size - 1], y - ys[size - 1])
        if (d < MIN_STEP) return false
        append(x, y)
        length += d
        if (letter && key != startKey) visited += key
        if (gliding) return false
        val crossed = visited.size
        gliding = crossed >= 2 ||
            (crossed == 1 && (length > LONG * keyWidth || (length > SHORT * keyWidth && !mostlyUp(x, y))))
        return gliding
    }

    /**
     * At release: still a glide when the finger went straight up but stopped
     * on the middle of another letter key, [distanceToCenter] away from it
     * (null when not on a letter key, or on the start key).
     */
    fun glideOnRelease(distanceToCenter: Float?): Boolean =
        gliding || (visited.isNotEmpty() && distanceToCenter != null && distanceToCenter < CENTER * keyWidth)

    private fun mostlyUp(x: Float, y: Float) = downY - y > UP_RATIO * abs(x - downX)

    private fun append(x: Float, y: Float) {
        if (size == xs.size) {
            xs = xs.copyOf(size * 2)
            ys = ys.copyOf(size * 2)
        }
        xs[size] = x
        ys[size] = y
        size++
    }

    private companion object {
        const val INITIAL = 64
        const val MIN_STEP = 2f

        /** Path lengths, in key widths, after crossing into one other letter key. */
        const val SHORT = 0.8f
        const val LONG = 1.6f

        /** Ending this close to another key's center (key widths) makes a swipe up a glide. */
        const val CENTER = 0.4f
        const val UP_RATIO = 1.5f
    }
}
