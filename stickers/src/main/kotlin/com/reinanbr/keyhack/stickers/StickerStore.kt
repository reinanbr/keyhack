package com.reinanbr.keyhack.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The user's sticker library: images they imported, converted to 512x512 WebP
 * (WhatsApp's sticker format) and kept in app-private storage, plus stickers
 * drawn from emoji on demand. Files are shared with the target app only
 * through the non-exported FileProvider, one URI at a time.
 *
 * Imported images are untrusted: the input is size-capped, decoded by the
 * platform decoder with subsampling, and re-encoded, so the original bytes
 * never leave this class. An animated GIF becomes an animated WebP sticker
 * (WhatsApp: at most 500 KB and 10 s); if it cannot fit, its first frame is
 * used as a still sticker.
 */
class StickerStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "stickers")
    private val emojiDir = File(app.cacheDir, "stickers")

    /** Imported stickers, newest first. */
    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty().sortedByDescending { it.lastModified() }

    fun delete(file: File) {
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
    }

    fun deleteAll() {
        list().forEach { it.delete() }
    }

    /** Imports one image from [uri] (photo picker). Blocking: call off the main thread. */
    fun import(uri: Uri): Result<File> = runCatching {
        val bytes = readCapped(uri)
        dir.mkdirs()
        val animated = if (isGif(bytes)) animatedSticker(bytes) else null
        if (animated != null) {
            val out = File(dir, "a_${System.currentTimeMillis()}_${(bytes.contentHashCode().toLong() and MASK)}$EXT")
            writeAtomic(out, animated)
            return@runCatching out
        }
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(StickerGeometry.sampleSize(info.size.width, info.size.height))
            }
        val sticker = toCanvas(bitmap)
        bitmap.recycle()
        val out = File(dir, "s_${System.currentTimeMillis()}_${(bytes.contentHashCode().toLong() and MASK)}$EXT")
        writeAtomic(out, encode(sticker))
        sticker.recycle()
        out
    }

    /** A sticker of [emoji], drawn once and cached. Blocking: call off the main thread. */
    fun emojiSticker(emoji: String): File {
        val name = MessageDigest.getInstance("SHA-1").digest(emoji.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(emojiDir, "e_$name$EXT")
        if (file.isFile) return file
        val size = StickerGeometry.SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * EMOJI_SCALE
        }
        // Shrink until the glyph fits inside the margins (flags and ZWJ sequences are wide).
        val box = (size - 2 * StickerGeometry.MARGIN).toFloat()
        val bounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        val fit = minOf(box / bounds.width().coerceAtLeast(1), box / bounds.height().coerceAtLeast(1), 1f)
        paint.textSize *= fit
        val fm = paint.fontMetrics
        Canvas(bitmap).drawText(emoji, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
        emojiDir.mkdirs()
        writeAtomic(file, encode(bitmap))
        bitmap.recycle()
        return file
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(app, "${app.packageName}.media", file)

    private fun readCapped(uri: Uri): ByteArray {
        val input = app.contentResolver.openInputStream(uri) ?: throw IOException("cannot open image")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(BUFFER)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_INPUT_BYTES) throw IOException("image too large")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    private fun isGif(b: ByteArray) = b.size > GIF_MAGIC.size && GIF_MAGIC.indices.all { b[it] == GIF_MAGIC[it] }

    /** An animated WebP of the GIF, or null when it is still, too big to decode, or cannot fit the limit. */
    @Suppress("DEPRECATION") // Movie is the platform's only frame-by-frame GIF decoder (Skia).
    private fun animatedSticker(bytes: ByteArray): ByteArray? {
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val w = movie.width()
        val h = movie.height()
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_GIF_PIXELS || movie.duration() <= 0) return null
        val duration = minOf(movie.duration(), MAX_ANIMATION_MS)
        // Fewer frames per second and lower quality until it fits.
        return ANIMATION_LADDER.firstNotNullOfOrNull { (interval, quality) ->
            renderAnimation(movie, duration, interval, quality)
        }
    }

    @Suppress("DEPRECATION")
    private fun renderAnimation(movie: Movie, duration: Int, interval: Int, quality: Int): ByteArray? {
        val size = StickerGeometry.SIZE
        val box = StickerGeometry.fit(movie.width(), movie.height())
        var cur = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        var prev = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val frames = ArrayList<AnimatedWebp.Frame>()
        var total = 0
        try {
            var t = 0
            while (t < duration) {
                cur.eraseColor(0)
                val canvas = Canvas(cur)
                canvas.translate(box.left.toFloat(), box.top.toFloat())
                canvas.scale(box.width.toFloat() / movie.width(), box.height.toFloat() / movie.height())
                movie.setTime(t)
                movie.draw(canvas, 0f, 0f, paint)
                val step = minOf(interval, duration - t)
                if (frames.isNotEmpty() && cur.sameAs(prev)) {
                    // Unchanged picture: show the previous frame longer instead of storing it again.
                    val last = frames.removeAt(frames.lastIndex)
                    frames += AnimatedWebp.Frame(last.webp, last.durationMs + step)
                } else {
                    val out = ByteArrayOutputStream()
                    cur.compress(webpFormat(), quality, out)
                    total += out.size()
                    if (total > StickerGeometry.MAX_ANIMATED_BYTES) return null
                    frames += AnimatedWebp.Frame(out.toByteArray(), step)
                    val swap = prev
                    prev = cur
                    cur = swap
                }
                t += interval
            }
            if (frames.size < 2) return null
            return AnimatedWebp.mux(frames, size, size).takeIf { it.size <= StickerGeometry.MAX_ANIMATED_BYTES }
        } finally {
            cur.recycle()
            prev.recycle()
        }
    }

    private fun toCanvas(src: Bitmap): Bitmap {
        val size = StickerGeometry.SIZE
        val box = StickerGeometry.fit(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(
            out,
        ).drawBitmap(
            src,
            null,
            Rect(box.left, box.top, box.right, box.bottom),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** WebP under WhatsApp's 100 KB limit, lowering quality as needed. */
    private fun encode(bitmap: Bitmap): ByteArray {
        var quality = START_QUALITY
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(webpFormat(), quality, out)
            if (out.size() <= StickerGeometry.MAX_BYTES || quality <= MIN_QUALITY) return out.toByteArray()
            quality -= QUALITY_STEP
        }
    }

    @Suppress("DEPRECATION") // WEBP is the only WebP format before Android 11.
    private fun webpFormat(): Bitmap.CompressFormat {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Bitmap.CompressFormat.WEBP_LOSSY
        return Bitmap.CompressFormat.WEBP
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) throw IOException("cannot save sticker")
    }

    companion object {
        /** WhatsApp's sticker MIME type; editors that take stickers list it in contentMimeTypes. */
        const val MIME_WHATSAPP = "image/webp.wasticker"
        const val MIME_WEBP = "image/webp"
        private const val EXT = ".webp"
        private const val MAX_INPUT_BYTES = 20 * 1024 * 1024
        private const val BUFFER = 64 * 1024
        private const val START_QUALITY = 90
        private const val MIN_QUALITY = 30
        private const val QUALITY_STEP = 10
        private const val EMOJI_SCALE = 0.8f
        private const val MASK = 0xffffffffL
        private val GIF_MAGIC = "GIF8".toByteArray(Charsets.US_ASCII)

        /** Movie draws from a full-size frame buffer: 2048x2048 ARGB is 16 MB. */
        private const val MAX_GIF_PIXELS = 2048L * 2048L
        private const val MAX_ANIMATION_MS = 10_000

        /** (ms between frames, WebP quality), tried in order. */
        private val ANIMATION_LADDER =
            listOf(80 to 70, 100 to 60, 100 to 45, 125 to 40, 160 to 35, 200 to 30, 250 to 25)
    }
}
