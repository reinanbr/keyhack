package com.reinanbr.keyhack.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
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
 * never leave this class.
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
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(StickerGeometry.sampleSize(info.size.width, info.size.height))
            }
        val sticker = toCanvas(bitmap)
        bitmap.recycle()
        dir.mkdirs()
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
    }
}
