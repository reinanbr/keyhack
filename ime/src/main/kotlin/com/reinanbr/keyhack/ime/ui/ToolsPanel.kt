package com.reinanbr.keyhack.ime.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.reinanbr.keyhack.devtools.DevTool
import com.reinanbr.keyhack.devtools.Snippet
import com.reinanbr.keyhack.ime.R
import com.reinanbr.keyhack.theme.KeyboardColors

/** Editing actions that the tools panel offers besides [DevTool] transforms. */
enum class EditAction(@param:StringRes val label: Int) {
    SELECT_ALL(R.string.keyhack_edit_select_all),
    SELECT_LINE(R.string.keyhack_edit_select_line),
    COPY(R.string.keyhack_edit_copy),
    CUT(R.string.keyhack_edit_cut),
    PASTE(R.string.keyhack_edit_paste),
    UNDO(R.string.keyhack_edit_undo),
    REDO(R.string.keyhack_edit_redo),
    DOC_START(R.string.keyhack_edit_doc_start),
    DOC_END(R.string.keyhack_edit_doc_end),
}

/**
 * Developer tools, in two tabs: tools (clipboard/selection actions, text
 * transforms on the selection or the word before the cursor, generators)
 * and snippets (saved texts, one tap to insert). 🔍 opens the command
 * palette, which searches all of them.
 */
class ToolsPanel(context: Context) : LinearLayout(context) {
    interface Listener {
        fun onEditAction(action: EditAction)

        fun onDevTool(tool: DevTool)

        fun onSnippet(snippet: Snippet)

        /** Save the selected text (or the clipboard's) as a snippet. */
        fun onSnippetFromSelection()

        /** Open the settings screen, where snippets get triggers and edits. */
        fun onSnippetsEdit()

        fun onPaletteOpen()

        fun onToolsBack()
    }

    var listener: Listener? = null

    /** Snippets shown in the snippets tab. */
    var snippets: List<Snippet> = emptyList()
        set(value) {
            field = value
            if (tab == TAB_SNIPPETS) refresh()
        }

    private sealed interface Entry {
        fun label(context: Context): String

        data class Edit(val action: EditAction) : Entry {
            override fun label(context: Context) = context.getString(action.label)
        }

        data class Tool(val tool: DevTool) : Entry {
            override fun label(context: Context) = tool.label
        }

        data class Saved(val snippet: Snippet) : Entry {
            override fun label(context: Context): String {
                val text = snippet.text.replace('\n', ' ')
                return if (snippet.trigger.isEmpty()) text else "${snippet.trigger} · $text"
            }
        }

        data object AddSnippet : Entry {
            override fun label(context: Context) = context.getString(R.string.keyhack_snippet_add)
        }

        data object EditSnippets : Entry {
            override fun label(context: Context) = context.getString(R.string.keyhack_snippet_edit)
        }
    }

    private val toolEntries: List<Entry> = EditAction.entries.map {
        Entry.Edit(
            it,
        )
    } + DevTool.entries.map { Entry.Tool(it) }
    private val entries = ArrayList<Entry>(toolEntries)
    private var tab = TAB_TOOLS
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
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding((PAD_DP * d).toInt(), 0, (PAD_DP * d).toInt(), 0)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (CELL_DP * d).toInt(),
                        )
                    }
                val e = entries[position]
                tv.text = e.label(context)
                colors?.let { c ->
                    tv.setTextColor(c.label)
                    tv.background =
                        GradientDrawable().apply {
                            cornerRadius = RADIUS_DP * d
                            setColor(if (e is Entry.Tool || e is Entry.Saved) c.key else c.functionKey)
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
                is Entry.Saved -> listener?.onSnippet(e.snippet)
                Entry.AddSnippet -> listener?.onSnippetFromSelection()
                Entry.EditSnippets -> listener?.onSnippetsEdit()
            }
        }
        header.listener = StripView.Listener { id ->
            when (id) {
                "abc" -> listener?.onToolsBack()

                "search" -> listener?.onPaletteOpen()

                TAB_TOOLS, TAB_SNIPPETS -> {
                    tab = id
                    refresh()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        header.show(
            listOf(
                StripView.Item("abc", "ABC", weight = 1f, bold = true),
                StripView.Item(TAB_TOOLS, "</>", weight = 1.2f, bold = true, accent = tab == TAB_TOOLS),
                StripView.Item(
                    TAB_SNIPPETS,
                    "✎ " + context.getString(R.string.keyhack_snippets_title),
                    weight = 2f,
                    accent = tab == TAB_SNIPPETS,
                ),
                StripView.Item("search", "🔍", weight = 1f, large = true),
            ),
        )
        entries.clear()
        if (tab == TAB_TOOLS) {
            entries += toolEntries
            grid.numColumns = TOOL_COLUMNS
        } else {
            entries += Entry.AddSnippet
            entries += Entry.EditSnippets
            entries += snippets.map { Entry.Saved(it) }
            grid.numColumns = SNIPPET_COLUMNS
        }
        adapter.notifyDataSetChanged()
    }

    fun applyColors(c: KeyboardColors) {
        colors = c
        setBackgroundColor(c.background)
        header.colors = c
        adapter.notifyDataSetChanged()
    }

    companion object {
        private const val TAB_TOOLS = "tools"
        private const val TAB_SNIPPETS = "snippets"
        private const val HEADER_DP = 40f
        private const val CELL_DP = 40f
        private const val TOOL_COLUMNS = 4
        private const val SNIPPET_COLUMNS = 2
        private const val GAP_DP = 4f
        private const val PAD_DP = 6f
        private const val RADIUS_DP = 6f
        private const val TEXT_SP = 13f
    }
}
