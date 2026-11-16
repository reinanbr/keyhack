package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.ime.R
import com.reinanbr.keyhack.media.GifItem
import com.reinanbr.keyhack.media.GifProvider
import com.reinanbr.keyhack.media.GifRepository
import com.reinanbr.keyhack.theme.KeyboardColors
import java.nio.ByteBuffer

/**
 * GIF grid: trending, search results or recents. Previews are small animated
 * images; the full GIF is downloaded only when one is tapped.
 */
class GifPanel(context: Context, private val repo: GifRepository) : LinearLayout(context) {
    interface Listener {
        fun onGifPicked(item: GifItem)

        fun onGifBack()

        fun onGifSearch()
    }

    var listener: Listener? = null
    var provider: GifProvider? = null
    var locale: String = "en-US"

    private val header = StripView(context, HEADER_DP)
    private val grid = GridView(context)
    private val message = TextView(context)
    private val adapter = Adapter()
    private val items = ArrayList<GifItem>()
    private var query: String? = null
    private var next: String? = null
    private var loading = false
    private var showingRecents = false
    private var colors: KeyboardColors? = null
    private var generation = 0

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val frame = FrameLayout(context)
        frame.addView(
            grid,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        frame.addView(
            message,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        message.gravity = Gravity.CENTER
        val pad = (PAD_DP * resources.displayMetrics.density).toInt()
        message.setPadding(pad, pad, pad, pad)
        grid.numColumns = COLUMNS
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.horizontalSpacing = pad / 2
        grid.verticalSpacing = pad / 2
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> items.getOrNull(pos)?.let { listener?.onGifPicked(it) } }
        grid.setOnScrollListener(
            object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit

                override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                    if (total > 0 && first + visible >= total - COLUMNS * 2) loadMore()
                }
            },
        )
        header.listener =
            StripView.Listener { id ->
                when (id) {
                    "abc" -> listener?.onGifBack()
                    "search" -> listener?.onGifSearch()
                    "trending" -> load(null)
                    "recent" -> showRecents()
                }
            }
        updateHeader()
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        header.colors = c
        message.setTextColor(c.secondaryLabel)
    }

    private fun updateHeader() {
        val q = query
        header.show(
            listOf(
                StripView.Item("abc", "ABC", weight = 1f, bold = true),
                StripView.Item(
                    "search",
                    if (q.isNullOrBlank()) "🔍 " + context.getString(R.string.keyhack_gif_search_hint) else "🔍 $q",
                    weight = 3f,
                    dim = q.isNullOrBlank(),
                ),
                StripView.Item("trending", "🔥", weight = 0.8f, accent = q.isNullOrBlank() && !showingRecents),
                StripView.Item("recent", "🕘", weight = 0.8f, accent = showingRecents),
            ),
        )
    }

    fun showMessage(text: String?) {
        message.text = text.orEmpty()
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    /** Loads trending ([q] null) or search results. */
    fun load(q: String?) {
        val p = provider
        query = q
        showingRecents = false
        items.clear()
        next = null
        adapter.notifyDataSetChanged()
        updateHeader()
        if (p == null) {
            showMessage(context.getString(R.string.keyhack_gif_no_key))
            return
        }
        showMessage(context.getString(R.string.keyhack_gif_loading))
        fetch(p, null)
    }

    private fun loadMore() {
        val p = provider ?: return
        if (loading || showingRecents || next == null) return
        fetch(p, next)
    }

    private fun fetch(p: GifProvider, cursor: String?) {
        loading = true
        val gen = ++generation
        repo.page(p, query, cursor, locale) { result ->
            if (gen != generation) return@page
            loading = false
            result
                .onSuccess { page ->
                    items += page.items
                    next = page.next
                    adapter.notifyDataSetChanged()
                    showMessage(if (items.isEmpty()) context.getString(R.string.keyhack_gif_empty) else null)
                }.onFailure {
                    if (items.isEmpty()) showMessage(context.getString(R.string.keyhack_gif_error))
                }
        }
    }

    private fun showRecents() {
        generation++
        showingRecents = true
        loading = false
        items.clear()
        items += repo.recents()
        next = null
        adapter.notifyDataSetChanged()
        updateHeader()
        showMessage(if (items.isEmpty()) context.getString(R.string.keyhack_gif_no_recents) else null)
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val iv =
                convertView as? ImageView ?: ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            val item = items[position]
            val h = (CELL_DP * resources.displayMetrics.density).toInt()
            iv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            colors?.let { iv.setBackgroundColor(it.key) }
            iv.contentDescription = item.description
            if (iv.tag == item.preview.url) return iv
            (iv.drawable as? AnimatedImageDrawable)?.stop()
            iv.setImageDrawable(null)
            iv.tag = item.preview.url
            repo.preview(provider, item.preview.url) { bytes ->
                if (iv.tag != item.preview.url || bytes == null) return@preview
                val drawable = decode(bytes) ?: return@preview
                iv.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
            return iv
        }
    }

    private fun decode(bytes: ByteArray): Drawable? =
        runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) }.getOrNull()

    /** Stops animations and drops images (panel hidden). */
    fun release() {
        generation++
        for (i in 0 until grid.childCount) {
            val iv = grid.getChildAt(i) as? ImageView ?: continue
            (iv.drawable as? AnimatedImageDrawable)?.stop()
            iv.setImageDrawable(null)
            iv.tag = null
        }
    }

    companion object {
        private const val HEADER_DP = 40f
        private const val CELL_DP = 96f
        private const val COLUMNS = 3
        private const val PAD_DP = 8f
    }
}
