package com.reinanbr.keyhack.stickers

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure sizing rules for stickers, kept Android-free so they can be unit tested. */
object StickerGeometry {
    /** WhatsApp sticker canvas: 512x512 px. */
    const val SIZE = 512

    /** WhatsApp's limit for a static sticker file. */
    const val MAX_BYTES = 100_000

    /** WhatsApp's limit for an animated sticker file. */
    const val MAX_ANIMATED_BYTES = 500_000

    /** Blank border kept around the image, as WhatsApp recommends. */
    const val MARGIN = 16

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** Where a [w]x[h] image goes, scaled to fit and centered on the canvas. */
    fun fit(w: Int, h: Int, size: Int = SIZE, margin: Int = MARGIN): Box {
        require(w > 0 && h > 0) { "empty image" }
        val box = size - 2 * margin
        val scale = min(box.toFloat() / w, box.toFloat() / h)
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())
        val left = (size - sw) / 2
        val top = (size - sh) / 2
        return Box(left, top, left + sw, top + sh)
    }

    /** Power-of-two decoder subsampling: the decoded longer side stays below twice [target]. */
    fun sampleSize(w: Int, h: Int, target: Int = SIZE * 2): Int {
        var sample = 1
        while (max(w, h) / (sample * 2) >= target) sample *= 2
        return sample
    }
}
