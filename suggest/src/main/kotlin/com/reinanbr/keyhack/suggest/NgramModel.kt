package com.reinanbr.keyhack.suggest

import java.io.BufferedReader

/**
 * Next-word statistics for one language, built by tools/gen_ngrams.py from
 * conversation-style sentences: for a context of one word (bigram) or two
 * words (trigram), the most likely next words with a score of 1..255 (log of
 * the conditional probability; 255 = always, 170 = 10%, 85 = 1%).
 *
 * Rows stay as text until asked for, so loading is one pass and memory stays
 * close to the file size.
 */
class NgramModel private constructor(
    /** Language code ("pt", "en"); matches [Dictionary.language]. */
    val language: String,
    private val rows: HashMap<String, String>,
) {
    val size: Int get() = rows.size

    /** Likely next words after "[before] [previous]" (trigram), best first. */
    fun afterPair(before: String, previous: String): List<Pair<String, Int>> =
        parse(rows[Dictionary.key(before) + " " + Dictionary.key(previous)])

    /** Likely next words after [previous] (bigram), best first. */
    fun after(previous: String): List<Pair<String, Int>> = parse(rows[Dictionary.key(previous)])

    /** How well [word] fits after the context: the trigram score if known, else the bigram one; 0 if neither. */
    fun score(before: String?, previous: String, word: String): Int {
        val k = Dictionary.key(word)
        if (before != null) afterPair(before, previous).firstOrNull { it.first == k }?.let { return it.second }
        return after(previous).firstOrNull { it.first == k }?.second ?: 0
    }

    private fun parse(row: String?): List<Pair<String, Int>> {
        if (row.isNullOrEmpty()) return emptyList()
        return row.split(' ').mapNotNull { item ->
            val colon = item.lastIndexOf(':')
            if (colon <= 0) return@mapNotNull null
            val score = item.substring(colon + 1).toIntOrNull() ?: return@mapNotNull null
            item.substring(0, colon) to score.coerceIn(1, MAX_SCORE)
        }
    }

    companion object {
        const val MAX_SCORE = 255

        fun read(reader: BufferedReader, language: String = ""): NgramModel {
            val rows = HashMap<String, String>(INITIAL)
            reader.forEachLine { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val tab = line.indexOf('\t')
                if (tab > 0) rows[line.substring(0, tab)] = line.substring(tab + 1)
            }
            return NgramModel(language, rows)
        }

        private const val INITIAL = 1 shl 16
    }
}
