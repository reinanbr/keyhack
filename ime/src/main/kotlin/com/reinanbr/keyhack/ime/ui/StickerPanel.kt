package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.ime.R
import com.reinanbr.keyhack.stickers.StickerStore
import com.reinanbr.keyhack.theme.KeyboardColors
import java.io.File
import java.util.concurrent.Executors

/**
 * Stickers: the user's imported library ("Mine") and emoji drawn as stickers.
 * Thumbnails decode off the main thread.
 */
class StickerPanel(context: Context, private val store: StickerStore) : LinearLayout(context) {
    sealed interface Sticker {
        data class Image(val file: File) : Sticker

        data class Emoji(val text: String) : Sticker
    }

    interface Listener {
        fun onStickerPicked(sticker: Sticker)

        fun onStickerBack()

        fun onStickerImport()
    }

    var listener: Listener? = null

    /** Emoji offered as stickers (recents first). */
    var emoji: List<String> = emptyList()

    private val header = StripView(context, HEADER_DP)
    private val grid = GridView(context)
    private val message = TextView(context)
    private val items = ArrayList<Sticker>()
    private val adapter = Adapter()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var tab = TAB_MINE
    private var colors: KeyboardColors? = null

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
        val pad = (PAD_DP * resources.displayMetrics.density).toInt()
        message.gravity = Gravity.CENTER
        message.setPadding(pad * 2, pad * 2, pad * 2, pad * 2)
        grid.numColumns = COLUMNS
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.horizontalSpacing = pad
        grid.verticalSpacing = pad
        grid.setPadding(pad, pad, pad, pad)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> items.getOrNull(pos)?.let { listener?.onStickerPicked(it) } }
        header.listener =
            StripView.Listener { id ->
                when (id) {
                    "abc" -> listener?.onStickerBack()

                    "add" -> listener?.onStickerImport()

                    else -> {
                        tab = id
                        refresh()
                    }
                }
            }
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        header.colors = c
        message.setTextColor(c.secondaryLabel)
        adapter.notifyDataSetChanged()
    }

    fun refresh() {
        header.show(
            listOf(
                StripView.Item("abc", "ABC", weight = 1f, bold = true),
                StripView.Item(
                    TAB_MINE,
                    context.getString(R.string.keyhack_sticker_mine),
                    weight = 1.6f,
                    accent = tab == TAB_MINE,
                ),
                StripView.Item(TAB_EMOJI, "☺", weight = 1f, large = true, accent = tab == TAB_EMOJI),
                StripView.Item("add", "＋", weight = 1f, large = true),
            ),
        )
        items.clear()
        if (tab == TAB_MINE) {
            items += store.list().map { Sticker.Image(it) }
        } else {
            items += emoji.map { Sticker.Emoji(it) }
        }
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        showMessage(if (tab == TAB_MINE && items.isEmpty()) context.getString(R.string.keyhack_sticker_empty) else null)
    }

    fun showMessage(text: String?) {
        message.text = text.orEmpty()
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h = (CELL_DP * resources.displayMetrics.density).toInt()
            return when (val item = items[position]) {
                is Sticker.Emoji -> {
                    val tv = convertView as? TextView ?: TextView(context).apply { gravity = Gravity.CENTER }
                    tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, EMOJI_SP)
                    tv.text = item.text
                    tv
                }

                is Sticker.Image -> {
                    val iv = convertView as? ImageView ?: ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    iv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
                    val path = item.file.path
                    if (iv.tag != path) {
                        iv.tag = path
                        iv.setImageDrawable(null)
                        io.execute {
                            val opts = BitmapFactory.Options().apply { inSampleSize = THUMB_SAMPLE }
                            val bmp = BitmapFactory.decodeFile(path, opts)
                            main.post { if (iv.tag == path) iv.setImageBitmap(bmp) }
                        }
                    }
                    iv
                }
            }
        }

        override fun getViewTypeCount() = 2

        override fun getItemViewType(position: Int) = if (items[position] is Sticker.Emoji) 0 else 1
    }

    private companion object {
        const val TAB_MINE = "mine"
        const val TAB_EMOJI = "emoji"
        const val HEADER_DP = 40f
        const val CELL_DP = 88f
        const val EMOJI_SP = 44f
        const val COLUMNS = 4
        const val PAD_DP = 6f
        const val THUMB_SAMPLE = 2
    }
}
