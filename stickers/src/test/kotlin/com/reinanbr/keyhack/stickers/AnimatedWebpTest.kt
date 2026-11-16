package com.reinanbr.keyhack.stickers

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnimatedWebpTest {
    private fun chunk(tag: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toByteArray())
        for (i in 0 until 4) out.write((payload.size ushr (8 * i)) and 0xff)
        out.write(payload)
        if (payload.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }

    private fun riff(vararg chunks: ByteArray): ByteArray {
        val body = chunks.fold(ByteArray(0)) { a, b -> a + b }
        val size = body.size + 4
        val header = "RIFF".toByteArray() + ByteArray(
            4,
        ) { ((size ushr (8 * it)) and 0xff).toByte() } + "WEBP".toByteArray()
        return header + body
    }

    /** Like a lossy WebP with alpha from Bitmap.compress: VP8X, ALPH (odd size, padded), VP8. */
    private val still =
        riff(chunk("VP8X", ByteArray(10)), chunk("ALPH", byteArrayOf(1, 2, 3)), chunk("VP8 ", byteArrayOf(4, 5, 6, 7)))

    private data class Chunk(val tag: String, val at: Int, val size: Int)

    private fun chunks(b: ByteArray, from: Int, to: Int): List<Chunk> {
        val list = ArrayList<Chunk>()
        var pos = from
        while (pos + 8 <= to) {
            val size = AnimatedWebp.readLe(b, pos + 4, 4)
            list += Chunk(AnimatedWebp.ascii(b, pos), pos + 8, size)
            pos += 8 + size + (size and 1)
        }
        assertEquals(to, pos, "chunks fill the range exactly")
        return list
    }

    @Test
    fun keepsOnlyImageChunks() {
        val img = AnimatedWebp.imageChunks(still)
        assertEquals(listOf("ALPH", "VP8 "), chunks(img, 0, img.size).map { it.tag })
    }

    @Test
    fun muxesAValidAnimatedContainer() {
        val webp = AnimatedWebp.mux(listOf(AnimatedWebp.Frame(still, 100), AnimatedWebp.Frame(still, 300)), 512, 512)
        assertEquals("RIFF", AnimatedWebp.ascii(webp, 0))
        assertEquals(webp.size - 8, AnimatedWebp.readLe(webp, 4, 4))
        assertEquals("WEBP", AnimatedWebp.ascii(webp, 8))
        val top = chunks(webp, 12, webp.size)
        assertEquals(listOf("VP8X", "ANIM", "ANMF", "ANMF"), top.map { it.tag })
        val vp8x = top[0]
        assertEquals(0x12, webp[vp8x.at].toInt(), "alpha + animation flags")
        assertEquals(511, AnimatedWebp.readLe(webp, vp8x.at + 4, 3))
        assertEquals(511, AnimatedWebp.readLe(webp, vp8x.at + 7, 3))
        assertEquals(0, AnimatedWebp.readLe(webp, top[1].at + 4, 2), "loops forever")
        val durations = top.drop(2).map { AnimatedWebp.readLe(webp, it.at + 12, 3) }
        assertEquals(listOf(100, 300), durations)
        val frame = top[2]
        assertEquals(511, AnimatedWebp.readLe(webp, frame.at + 6, 3))
        assertEquals(listOf("ALPH", "VP8 "), chunks(webp, frame.at + 16, frame.at + frame.size).map { it.tag })
    }

    @Test
    fun rejectsBadInput() {
        assertFailsWith<IllegalArgumentException> { AnimatedWebp.imageChunks("GIF89a....xx".toByteArray()) }
        val truncated = still.copyOf(still.size - 3)
        assertFailsWith<IllegalArgumentException> { AnimatedWebp.imageChunks(truncated) }
        assertFailsWith<IllegalArgumentException> { AnimatedWebp.mux(emptyList(), 512, 512) }
        assertTrue(AnimatedWebp.imageChunks(still).isNotEmpty())
        // An odd last chunk without its pad byte is still read.
        val unpadded = riff(chunk("VP8L", byteArrayOf(9, 9, 9))).let { it.copyOf(it.size - 1) }
        assertEquals(11, AnimatedWebp.imageChunks(unpadded).size)
    }
}
