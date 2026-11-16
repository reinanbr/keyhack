package com.reinanbr.keyhack.keyboard.model

/** Axis-aligned rectangle in pixels; plain floats so geometry is testable on the JVM. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class PlacedKey(
    val key: Key,
    val row: Int,
    /** Visible key cap, inset by the gap. */
    val bounds: Bounds,
    /** Touch target: the full cell, so taps in the gap still hit the nearest key. */
    val hitBounds: Bounds,
)

/** Lays a [KeyboardLayout] out in a pixel area and answers hit tests. */
class KeyGeometry(val layout: KeyboardLayout, val width: Float, val height: Float, gap: Float) {
    val keys: List<PlacedKey>

    /** Top edge of each row, plus the bottom edge of the last row. */
    private val rowEdges: FloatArray

    init {
        val unitH = height / layout.unitHeight
        val unit = width / layout.unitWidth
        val half = gap / 2f
        rowEdges = FloatArray(layout.rows.size + 1)
        var top = 0f
        val placed = ArrayList<PlacedKey>()
        layout.rows.forEachIndexed { r, row ->
            rowEdges[r] = top
            val rowHeight = row.height * unitH
            var x = (width - row.width * unit) / 2f
            row.keys.forEachIndexed { i, key ->
                val left = x
                val right = x + key.width * unit
                x = right
                // Stretch the first/last cell of centered rows to the edge so
                // edge taps still land on a key.
                val hitLeft = if (i == 0) 0f else left
                val hitRight = if (i == row.keys.lastIndex) width else right
                placed +=
                    PlacedKey(
                        key = key,
                        row = r,
                        bounds = Bounds(left + half, top + half, right - half, top + rowHeight - half),
                        hitBounds = Bounds(hitLeft, top, hitRight, top + rowHeight),
                    )
            }
            top += rowHeight
        }
        rowEdges[layout.rows.size] = top
        keys = placed
    }

    /** Index into [keys] of the key at ([x], [y]), clamping to the keyboard edges. */
    fun keyIndexAt(x: Float, y: Float): Int {
        var row = layout.rows.lastIndex
        for (r in 0 until layout.rows.size) {
            if (y < rowEdges[r + 1]) {
                row = r
                break
            }
        }
        // Keys are stored row by row, left to right: the first key in the row
        // whose cell ends past x is the hit (x past the edge -> last key).
        var last = -1
        for (i in keys.indices) {
            val k = keys[i]
            if (k.row != row) continue
            last = i
            if (x < k.hitBounds.right) return i
        }
        return last
    }
}
