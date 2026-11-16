package com.reinanbr.keyhack.stickers

import java.io.ByteArrayOutputStream

/**
 * Builds an animated WebP from still WebP frames (as written by
 * Bitmap.compress). Android can decode animated WebP but not encode it, so
 * this does the container part of the WebP spec by hand: each frame's image
 * chunks (ALPH + VP8, or VP8L) are moved into an ANMF chunk behind a VP8X and
 * an ANIM header. https://developers.google.com/speed/webp/docs/riff_container
 *
 * Pure Kotlin, no Android types, so it is unit tested on the JVM.
 */
object AnimatedWebp {
    /** One full-canvas frame: a still WebP file and how long it shows. */
    class Frame(val webp: ByteArray, val durationMs: Int)

    /** Muxes [frames] (each [width]x[height]) into one looping animated WebP. */
    fun mux(frames: List<Frame>, width: Int, height: Int): ByteArray {
        require(frames.isNotEmpty()) { "no frames" }
        require(width in 1..MAX_DIM && height in 1..MAX_DIM) { "bad canvas" }
        val body = ByteArrayOutputStream()
        body.writeChunk("VP8X", vp8x(width, height))
        body.writeChunk("ANIM", ByteArray(ANIM_SIZE)) // background transparent, loop forever
        for (f in frames) body.writeChunk("ANMF", anmf(f, width, height))
        val out = ByteArrayOutputStream()
        out.writeAscii("RIFF")
        out.writeLe(body.size() + FOURCC, U32)
        out.writeAscii("WEBP")
        body.writeTo(out)
        return out.toByteArray()
    }

    /** The image chunks (ALPH, VP8, VP8L) of a still WebP file, in order, as raw chunk bytes. */
    fun imageChunks(webp: ByteArray): ByteArray {
        require(webp.size >= HEADER && ascii(webp, 0) == "RIFF" && ascii(webp, RIFF_FORM) == "WEBP") { "not a WebP" }
        val out = ByteArrayOutputStream()
        var pos = HEADER
        while (pos + CHUNK_HEADER <= webp.size) {
            val tag = ascii(webp, pos)
            val size = readLe(webp, pos + FOURCC, U32)
            val end = pos + CHUNK_HEADER + size
            require(size >= 0 && end <= webp.size) { "truncated chunk" }
            val pad = minOf(size and 1, webp.size - end) // a final odd chunk may lack its pad byte
            if (tag == "ALPH" || tag == "VP8 " || tag == "VP8L") out.write(webp, pos, CHUNK_HEADER + size + pad)
            pos = end + pad
        }
        require(out.size() > 0) { "no image data" }
        return out.toByteArray()
    }

    private fun vp8x(width: Int, height: Int): ByteArray {
        val p = ByteArrayOutputStream()
        p.write(FLAG_ALPHA or FLAG_ANIMATION)
        p.writeLe(0, U24)
        p.writeLe(width - 1, U24)
        p.writeLe(height - 1, U24)
        return p.toByteArray()
    }

    private fun anmf(frame: Frame, width: Int, height: Int): ByteArray {
        val p = ByteArrayOutputStream()
        p.writeLe(0, U24) // x / 2
        p.writeLe(0, U24) // y / 2
        p.writeLe(width - 1, U24)
        p.writeLe(height - 1, U24)
        p.writeLe(frame.durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS), U24)
        // Each frame is a whole canvas: replace (no blending), never dispose.
        p.write(NO_BLEND)
        p.write(imageChunks(frame.webp))
        return p.toByteArray()
    }

    private fun ByteArrayOutputStream.writeChunk(tag: String, payload: ByteArray) {
        writeAscii(tag)
        writeLe(payload.size, U32)
        write(payload)
        if (payload.size and 1 == 1) write(0)
    }

    private fun ByteArrayOutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.US_ASCII))

    private fun ByteArrayOutputStream.writeLe(value: Int, bytes: Int) {
        for (i in 0 until bytes) write((value ushr (BYTE * i)) and BYTE_MASK)
    }

    internal fun readLe(b: ByteArray, at: Int, bytes: Int): Int {
        var v = 0
        for (i in 0 until bytes) v = v or ((b[at + i].toInt() and BYTE_MASK) shl (BYTE * i))
        return v
    }

    internal fun ascii(b: ByteArray, at: Int) = String(b, at, FOURCC, Charsets.US_ASCII)

    private const val FOURCC = 4
    private const val U24 = 3
    private const val U32 = 4
    private const val CHUNK_HEADER = 8
    private const val RIFF_FORM = 8
    private const val HEADER = 12
    private const val ANIM_SIZE = 6
    private const val FLAG_ALPHA = 0x10
    private const val FLAG_ANIMATION = 0x02
    private const val NO_BLEND = 0x02
    private const val BYTE = 8
    private const val BYTE_MASK = 0xff
    private const val MAX_DIM = 1 shl 24
    private const val MIN_DURATION_MS = 8
    private const val MAX_DURATION_MS = (1 shl 24) - 1
}
