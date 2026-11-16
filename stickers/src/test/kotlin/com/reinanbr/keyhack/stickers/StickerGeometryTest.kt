package com.reinanbr.keyhack.stickers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StickerGeometryTest {
    @Test
    fun wideImageFitsWidthAndIsCentered() {
        val box = StickerGeometry.fit(1000, 500)
        assertEquals(StickerGeometry.MARGIN, box.left)
        assertEquals(StickerGeometry.SIZE - StickerGeometry.MARGIN, box.right)
        assertEquals(StickerGeometry.SIZE, box.top + box.bottom, "vertically centered")
        assertEquals(240, box.height)
    }

    @Test
    fun tinyImageIsScaledUpToTheBox() {
        val box = StickerGeometry.fit(10, 10)
        assertEquals(StickerGeometry.SIZE - 2 * StickerGeometry.MARGIN, box.width)
        assertEquals(box.width, box.height)
    }

    @Test
    fun sampleSizeKeepsDecodesSmall() {
        assertEquals(1, StickerGeometry.sampleSize(800, 600))
        assertEquals(2, StickerGeometry.sampleSize(4000, 3000))
        for (side in listOf(1000, 3000, 8000, 20000)) {
            val decoded = side / StickerGeometry.sampleSize(side, 100)
            assertTrue(decoded < StickerGeometry.SIZE * 4, "$side -> $decoded")
        }
    }
}
