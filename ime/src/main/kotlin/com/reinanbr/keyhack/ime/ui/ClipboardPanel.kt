package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.clipboard.ClipItem
import com.reinanbr.keyhack.ime.R
import com.reinanbr.keyhack.theme.KeyboardColors

/**
 * Clipboard history: tap an item to paste it, long-press to pin or delete it.
 * When history is off, explains what turning it on means and offers to.
 */
class ClipboardPanel(context: Context) : LinearLayout(context) {
    interface Listener {
        fun onClipPicked(item: ClipItem)

        fun onClipPinned(item: ClipItem, pinned: Boolean)

        fun onClipDeleted(item: ClipItem)

        fun onClipboardClear()

        fun onClipboardEnable()

        fun onClipboardBack()
    }

    var listener: Listener? = null

    private val header = StripView(context, HEADER_DP)
    private val grid = GridView(context)
    private val message = TextView(context)
    private val enableButton = TextView(context)
    private val items = ArrayList<ClipItem>()
    private val adapter = Adapter()
    private var colors: KeyboardColors? = null
    private var selected: ClipItem? = null
    private var enabled = false

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val d = resources.displayMetrics.density
        val pad = (PAD_DP * d).toInt()
        val frame = FrameLayout(context)
        frame.addView(grid, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val info = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(
                enableButton,
                LayoutParams(LayoutParams.WRAP_CONTENT, (BUTTON_DP * d).toInt()).apply { topMargin = pad * 2 },
            )
        }
        frame.addView(info, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        message.gravity = Gravity.CENTER
        message.setPadding(pad * WIDE, pad, pad * WIDE, pad)
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, MESSAGE_SP)
        enableButton.gravity = Gravity.CENTER
        enableButton.setPadding(pad * WIDER, 0, pad * WIDER, 0)
        enableButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, MESSAGE_SP)
        enableButton.text = context.getString(R.string.keyhack_clipboard_turn_on)
        enableButton.setOnClickListener { listener?.onClipboardEnable() }
        grid.numColumns = COLUMNS
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.horizontalSpacing = pad
        grid.verticalSpacing = pad
        grid.setPadding(pad, pad, pad, pad)
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            val item = items.getOrNull(pos) ?: return@setOnItemClickListener
            if (selected != null) select(null) else listener?.onClipPicked(item)
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            items.getOrNull(pos)?.let { select(it) }
            true
        }
        header.listener = StripView.Listener(::onHeader)
    }

    private fun onHeader(id: String) {
        val item = selected
        when (id) {
            "abc" -> listener?.onClipboardBack()

            "clear" -> listener?.onClipboardClear()

            "cancel" -> select(null)

            "pin" -> if (item != null) {
                selected = null
                listener?.onClipPinned(item, !item.pinned)
            }

            "delete" -> if (item != null) {
                selected = null
                listener?.onClipDeleted(item)
            }
        }
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        header.colors = c
        message.setTextColor(c.secondaryLabel)
        enableButton.setTextColor(c.onAccent)
        enableButton.background = GradientDrawable().apply {
            cornerRadius = RADIUS_DP * resources.displayMetrics.density
            setColor(c.accentKey)
        }
        adapter.notifyDataSetChanged()
    }

    /** Redraws from [list]; [historyOn] false shows the turn-on explanation instead. */
    fun show(list: List<ClipItem>, historyOn: Boolean) {
        enabled = historyOn
        items.clear()
        if (historyOn) items += list
        selected = selected?.let { s -> items.firstOrNull { it.id == s.id } }
        adapter.notifyDataSetChanged()
        val text =
            when {
                !historyOn -> context.getString(R.string.keyhack_clipboard_off)
                items.isEmpty() -> context.getString(R.string.keyhack_clipboard_empty)
                else -> null
            }
        message.text = text.orEmpty()
        (message.parent as View).visibility = if (text == null) View.GONE else View.VISIBLE
        enableButton.visibility = if (historyOn) View.GONE else View.VISIBLE
        renderHeader()
    }

    private fun select(item: ClipItem?) {
        selected = item
        adapter.notifyDataSetChanged()
        renderHeader()
    }

    private fun renderHeader() {
        val item = selected
        val c = context
        header.show(
            if (item == null) {
                listOfNotNull(
                    StripView.Item("abc", "ABC", weight = 1f, bold = true),
                    StripView.Item("", "📋  " + c.getString(R.string.keyhack_clipboard_title), weight = 3f, dim = true),
                    if (enabled && items.any { !it.pinned }) {
                        StripView.Item("clear", c.getString(R.string.keyhack_clipboard_clear), weight = 1.4f)
                    } else {
                        null
                    },
                )
            } else {
                listOf(
                    StripView.Item("cancel", "✕", weight = 0.7f, large = true),
                    StripView.Item(
                        "pin",
                        c.getString(
                            if (item.pinned) R.string.keyhack_clipboard_unpin else R.string.keyhack_clipboard_pin,
                        ),
                        weight = 2f,
                        accent = true,
                    ),
                    StripView.Item("delete", c.getString(R.string.keyhack_clipboard_delete), weight = 2f),
                )
            },
        )
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val d = resources.displayMetrics.density
            val tv = convertView as? TextView ?: TextView(context).apply {
                maxLines = MAX_LINES
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                val p = (CELL_PAD_DP * d).toInt()
                setPadding(p, p / 2, p, p / 2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (CELL_DP * d).toInt())
            }
            val item = items[position]
            // Long copies are cut for display only; the whole text is pasted.
            val shown = item.text.take(PREVIEW_CHARS).replace('\n', ' ')
            tv.text = if (item.pinned) "📌 $shown" else shown
            colors?.let { c ->
                val isSelected = selected?.id == item.id
                tv.setTextColor(if (isSelected) c.onAccent else c.label)
                tv.background = GradientDrawable().apply {
                    cornerRadius = RADIUS_DP * d
                    setColor(
                        when {
                            isSelected -> c.accentKey
                            item.pinned -> c.functionKey
                            else -> c.key
                        },
                    )
                }
            }
            return tv
        }
    }

    private companion object {
        const val HEADER_DP = 40f
        const val CELL_DP = 68f
        const val CELL_PAD_DP = 10f
        const val BUTTON_DP = 44f
        const val COLUMNS = 2
        const val PAD_DP = 6f
        const val RADIUS_DP = 8f
        const val TEXT_SP = 14f
        const val MESSAGE_SP = 15f
        const val MAX_LINES = 3
        const val WIDE = 3
        const val WIDER = 4
        const val PREVIEW_CHARS = 300
    }
}
