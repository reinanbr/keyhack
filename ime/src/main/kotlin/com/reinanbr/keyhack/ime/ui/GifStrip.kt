package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.media.GifItem
import com.reinanbr.keyhack.media.GifProvider
import com.reinanbr.keyhack.media.GifRepository
import com.reinanbr.keyhack.theme.KeyboardColors
import java.nio.ByteBuffer

/**
 * One row of animated GIF previews shown above the keyboard while the user
 * types a GIF search, so results update as they type.
 */
class GifStrip(context: Context, private val repo: GifRepository) : FrameLayout(context) {
    fun interface Listener {
        fun onGifPicked(item: GifItem)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(row)
    }
    private val message = TextView(context).apply { gravity = Gravity.CENTER }
    private var colors: KeyboardColors? = null
    private var provider: GifProvider? = null

    init {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val pad = (PAD_DP * density).toInt()
        setPadding(pad, pad, pad, pad)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec((HEIGHT_DP * density).toInt(), MeasureSpec.EXACTLY),
        )
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        message.setTextColor(c.secondaryLabel)
    }

    fun showMessage(text: String?) {
        message.text = text.orEmpty()
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    /** Replaces the row with [items] from [from]. */
    fun show(items: List<GifItem>, from: GifProvider?) {
        clear()
        provider = from
        scroll.scrollTo(0, 0)
        val h = (HEIGHT_DP * density).toInt() - paddingTop - paddingBottom
        val gap = (PAD_DP * density).toInt()
        for (item in items.take(MAX_ITEMS)) {
            val w = if (item.preview.height > 0) h * item.preview.width / item.preview.height else h
            val iv = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = item.description
                colors?.let { setBackgroundColor(it.key) }
                setOnClickListener { listener?.onGifPicked(item) }
            }
            row.addView(iv, LinearLayout.LayoutParams(w.coerceIn(h / 2, h * 2), h).apply { marginEnd = gap })
            iv.tag = item.preview.url
            repo.preview(provider, item.preview.url) { bytes ->
                if (iv.tag != item.preview.url || bytes == null) return@preview
                val drawable =
                    runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) }
                        .getOrNull() ?: return@preview
                iv.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        }
    }

    /** Stops animations and drops the images. */
    fun clear() {
        for (i in 0 until row.childCount) {
            val iv = row.getChildAt(i) as? ImageView ?: continue
            (iv.drawable as? AnimatedImageDrawable)?.stop()
            iv.tag = null
        }
        row.removeAllViews()
    }

    private companion object {
        const val HEIGHT_DP = 96f
        const val PAD_DP = 4f
        const val MAX_ITEMS = 24
    }
}
