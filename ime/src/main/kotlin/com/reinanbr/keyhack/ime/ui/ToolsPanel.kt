package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.reinanbr.keyhack.devtools.DevTool
import com.reinanbr.keyhack.theme.KeyboardColors

/** Editing actions that the tools panel offers besides [DevTool] transforms. */
enum class EditAction(val label: String) {
    SELECT_ALL("Select all"),
    SELECT_LINE("Select line"),
    COPY("Copy"),
    CUT("Cut"),
    PASTE("Paste"),
    UNDO("Undo"),
    REDO("Redo"),
    DOC_START("⇤ Start"),
    DOC_END("End ⇥"),
}

/**
 * Developer tools: clipboard/selection actions, then text transforms that act
 * on the selection (or the word before the cursor), then generators.
 */
class ToolsPanel(context: Context) : LinearLayout(context) {
    interface Listener {
        fun onEditAction(action: EditAction)

        fun onDevTool(tool: DevTool)

        fun onToolsBack()
    }

    var listener: Listener? = null

    private sealed interface Entry {
        val label: String

        data class Edit(val action: EditAction) : Entry {
            override val label get() = action.label
        }

        data class Tool(val tool: DevTool) : Entry {
            override val label get() = tool.label
        }
    }

    private val entries: List<Entry> = EditAction.entries.map {
        Entry.Edit(
            it,
        )
    } + DevTool.entries.map { Entry.Tool(it) }
    private val header = StripView(context, HEADER_DP)
    private val grid = GridView(context)
    private var colors: KeyboardColors? = null
    private val adapter =
        object : BaseAdapter() {
            override fun getCount() = entries.size

            override fun getItem(position: Int) = entries[position]

            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val d = resources.displayMetrics.density
                val tv =
                    convertView as? TextView ?: TextView(context).apply {
                        gravity = Gravity.CENTER
                        maxLines = 1
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (CELL_DP * d).toInt(),
                        )
                    }
                val e = entries[position]
                tv.text = e.label
                colors?.let { c ->
                    tv.setTextColor(c.label)
                    tv.background =
                        GradientDrawable().apply {
                            cornerRadius = RADIUS_DP * d
                            setColor(if (e is Entry.Edit) c.functionKey else c.key)
                        }
                }
                return tv
            }
        }

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val d = resources.displayMetrics.density
        val gap = (GAP_DP * d).toInt()
        grid.numColumns = COLUMNS
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.horizontalSpacing = gap
        grid.verticalSpacing = gap
        grid.setPadding(gap, gap, gap, gap)
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            when (val e = entries[pos]) {
                is Entry.Edit -> listener?.onEditAction(e.action)
                is Entry.Tool -> listener?.onDevTool(e.tool)
            }
        }
        header.listener = StripView.Listener { id -> if (id == "abc") listener?.onToolsBack() }
        header.show(
            listOf(
                StripView.Item("abc", "ABC", weight = 1f, bold = true),
                StripView.Item("", "</>  Dev tools", weight = 4f, dim = true),
            ),
        )
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        header.colors = c
        adapter.notifyDataSetChanged()
    }

    companion object {
        private const val HEADER_DP = 40f
        private const val CELL_DP = 40f
        private const val COLUMNS = 4
        private const val GAP_DP = 4f
        private const val RADIUS_DP = 6f
        private const val TEXT_SP = 13f
    }
}
