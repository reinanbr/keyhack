package com.reinanbr.keyhack.ime

import java.text.Normalizer
import java.util.Locale

/** Something the command palette can run: a panel, a toggle, an edit action, a dev tool or a snippet. */
internal data class PaletteEntry(val id: String, val label: String, val keywords: String = "")

/** Ranks palette entries for what was typed in the palette's search box. */
internal object CommandPalette {
    /**
     * Best matches first: the label starts with the query, then one of its
     * words does, then it contains the query, then a keyword does. Case and
     * accents are ignored ("area" finds "Área de transferência").
     */
    fun rank(query: String, entries: List<PaletteEntry>, limit: Int): List<PaletteEntry> {
        val q = fold(query.trim())
        if (q.isEmpty()) return entries.take(limit)
        return entries
            .mapNotNull { e -> score(q, e)?.let { e to it } }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(q: String, e: PaletteEntry): Int? {
        val label = fold(e.label).trimStart { !it.isLetterOrDigit() }
        return when {
            label.startsWith(q) -> PREFIX
            label.split(WORD_BREAK).any { it.startsWith(q) } -> WORD_PREFIX
            q in label -> INSIDE
            fold(e.keywords).split(WORD_BREAK).any { it.startsWith(q) } -> KEYWORD
            else -> null
        }
    }

    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).filter { it.code !in COMBINING }

    private const val PREFIX = 0
    private const val WORD_PREFIX = 1
    private const val INSIDE = 2
    private const val KEYWORD = 3
    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING = 0x300..0x36F
}
