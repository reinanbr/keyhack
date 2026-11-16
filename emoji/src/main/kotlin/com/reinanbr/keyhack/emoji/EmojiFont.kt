package com.reinanbr.keyhack.emoji

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build

/**
 * The bundled Noto Color Emoji font (Unicode 17.0, the version WhatsApp
 * supports), so every phone shows the same, current emoji. It is a COLRv1
 * font, which Android draws from 13 (API 33); older versions keep the system
 * emoji font. (Android 12 loads it and reports its glyphs but draws
 * nothing.) Characters the font lacks fall back to the system font.
 *
 * The font is also the version cap: [canDraw] hides emoji it cannot draw, so
 * nothing newer than what WhatsApp shows is offered.
 */
object EmojiFont {
    private const val PATH = "fonts/NotoColorEmoji.ttf"

    @Volatile private var loaded = false
    private var face: Typeface? = null
    private val probe = Paint()
    private val drawable = HashMap<String, Boolean>()

    /** The emoji typeface, or null to use the system font. Loads once (about 5 MB, memory-mapped). */
    fun typeface(context: Context): Typeface? {
        if (loaded) return face
        synchronized(this) {
            if (!loaded) {
                face = load(context)
                probe.typeface = face
                loaded = true
            }
        }
        return face
    }

    /** Whether [emoji] draws as one glyph with [typeface] (cached). */
    fun canDraw(context: Context, emoji: String): Boolean {
        typeface(context)
        return synchronized(drawable) { drawable.getOrPut(emoji) { probe.hasGlyph(emoji) } }
    }

    // COLRv1 rendering needs Android 13; Font.Builder itself needs 10.
    private fun load(context: Context): Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            val font = Font.Builder(context.applicationContext.assets, PATH).build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
                .setSystemFallback("sans-serif")
                .build()
        }.getOrNull()
    }
}
