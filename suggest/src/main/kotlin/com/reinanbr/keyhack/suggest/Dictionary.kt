package com.reinanbr.keyhack.suggest

import java.io.BufferedReader
import java.util.Locale
import java.util.PriorityQueue

/**
 * Main dictionary as a compact array trie keyed by lowercase words. Nodes are
 * laid out in preorder (a parent always precedes its children) in parallel
 * arrays, so a 170k-word dictionary costs a few MB and no per-node objects.
 *
 * Built from KeyHack's dictionary format (see tools/gen_dict.py).
 */
class Dictionary private constructor(
    val language: String,
    private val nodeChar: CharArray,
    private val firstChild: IntArray,
    private val nextSibling: IntArray,
    /** Word index for terminal nodes, -1 otherwise. */
    private val nodeWord: IntArray,
    /** Highest word frequency in the node's subtree, for best-first completion. */
    private val subtreeMax: IntArray,
    /** Display form of each word ("I", "Brasil", "don't"). */
    val words: Array<String>,
    val freqs: IntArray,
    /** Whitelisted autocorrections, typo (lowercase) -> correction. */
    val corrections: Map<String, String>,
) {
    val size: Int get() = words.size

    /** Index of [lowercase] in [words], or -1. */
    fun lookup(lowercase: String): Int {
        var node = ROOT
        for (c in lowercase) {
            node = child(node, c)
            if (node < 0) return -1
        }
        return nodeWord[node]
    }

    private fun child(node: Int, c: Char): Int {
        var n = firstChild[node]
        while (n >= 0) {
            if (nodeChar[n] == c) return n
            n = nextSibling[n]
        }
        return -1
    }

    /** Receives fuzzy matches from [search]. */
    fun interface Sink {
        /** [cost] is the weighted edit cost; [completion] when [word] extends the typed prefix. */
        fun accept(word: Int, cost: Float, completion: Boolean)
    }

    /**
     * Walks the trie with a weighted Damerau-Levenshtein row per depth, pruning
     * branches whose best alignment already costs more than [maxCost]. Words
     * within [maxCost] are reported; for prefixes within [completionCost] the
     * [completions] most frequent words of the subtree are reported too.
     */
    fun search(typed: String, costs: EditCosts, maxCost: Float, completionCost: Float, completions: Int, sink: Sink) {
        val n = typed.length
        if (n == 0 || n > MAX_WORD) return
        val rows = Array(MAX_WORD + 2) { FloatArray(n + 1) }
        for (j in 0..n) rows[0][j] = j * costs.extra
        val path = CharArray(MAX_WORD + 2)
        walk(firstChild[ROOT], 1, typed, costs, maxCost, completionCost, completions, rows, path, sink)
    }

    // The weighted Damerau-Levenshtein recurrence over the trie, kept in one
    // function so the hot loop has no call overhead.
    @Suppress("CyclomaticComplexMethod")
    private fun walk(
        start: Int,
        depth: Int,
        typed: String,
        costs: EditCosts,
        maxCost: Float,
        completionCost: Float,
        completions: Int,
        rows: Array<FloatArray>,
        path: CharArray,
        sink: Sink,
    ) {
        if (depth > MAX_WORD) return
        val n = typed.length
        var node = start
        while (node >= 0) {
            val ch = nodeChar[node]
            path[depth] = ch
            val prev = rows[depth - 1]
            val row = rows[depth]
            row[0] = prev[0] + costs.missing
            var best = row[0]
            for (j in 1..n) {
                val t = typed[j - 1]
                var v = prev[j - 1] + costs.substitute(t, ch)
                val miss = prev[j] + costs.missing
                if (miss < v) v = miss
                val extra = row[j - 1] + costs.extraTyped(t, if (j >= 2) typed[j - 2] else '\u0000')
                if (extra < v) v = extra
                if (j >= 2 && depth >= 2 && t == path[depth - 1] && typed[j - 2] == ch) {
                    val tr = rows[depth - 2][j - 2] + costs.transpose
                    if (tr < v) v = tr
                }
                row[j] = v
                if (v < best) best = v
            }
            val w = nodeWord[node]
            if (w >= 0 && row[n] <= maxCost) sink.accept(w, row[n], false)
            if (depth >= n && row[n] <= completionCost && completions > 0) {
                // Typed text is (nearly) a prefix of this branch: report its best words.
                // Only at the first depth where that holds, so subtrees are not repeated.
                if (depth == n || rows[depth - 1][n] > completionCost) collectTop(node, completions, row[n], sink)
            }
            // A transposition passes through a row one full edit worse than its
            // final cost, so allow that slack before pruning.
            if (best <= maxCost + TRANSPOSE_SLACK || (depth < n && best <= completionCost)) {
                walk(firstChild[node], depth + 1, typed, costs, maxCost, completionCost, completions, rows, path, sink)
            }
            node = nextSibling[node]
        }
    }

    private fun collectTop(node: Int, k: Int, cost: Float, sink: Sink) {
        // Best-first by subtree maximum: entries >= 0 are nodes, < 0 are words (~index).
        val queue = PriorityQueue<Long>(compareByDescending { it ushr SHIFT })
        queue += pack(subtreeMax[node], node)
        var found = 0
        while (found < k && queue.isNotEmpty()) {
            val e = queue.poll() ?: break
            val id = (e and MASK).toInt()
            if (id >= WORD_FLAG) {
                sink.accept(id - WORD_FLAG, cost, true)
                found++
                continue
            }
            val w = nodeWord[id]
            if (w >= 0 && freqs[w] > 0) queue += pack(freqs[w], WORD_FLAG + w)
            var c = firstChild[id]
            while (c >= 0) {
                if (subtreeMax[c] > 0) queue += pack(subtreeMax[c], c)
                c = nextSibling[c]
            }
        }
    }

    private fun pack(priority: Int, id: Int): Long = (priority.toLong() shl SHIFT) or id.toLong()

    companion object {
        private const val ROOT = 0
        private const val CORRECTION_FIELDS = 3
        const val MAX_WORD = 32
        private const val SHIFT = 32
        private const val MASK = 0xFFFFFFFFL
        private const val WORD_FLAG = 1 shl 30
        private const val TRANSPOSE_SLACK = 1f - EditCosts.TRANSPOSE

        fun key(word: String): String = word.lowercase(Locale.ROOT)

        /**
         * Reads tools/gen_dict.py output (already decompressed), plus any extra
         * lists in the same format (chat words, extra words, misspellings). A
         * word in several lists keeps its highest frequency.
         */
        fun read(language: String, reader: BufferedReader, vararg extra: BufferedReader): Dictionary {
            val entries = ArrayList<Pair<String, Int>>(INITIAL)
            val fixes = HashMap<String, String>()
            for (r in listOf(reader) + extra) readInto(r, entries, fixes)
            // A listed typo ("menas" -> "menos") is never a word, even if a wordlist has it.
            // Case fixes ("i" -> "I") keep the word.
            if (fixes.isNotEmpty()) {
                entries.removeAll { (w, _) -> fixes[key(w)]?.let { key(it) != key(w) } == true }
            }
            return build(language, entries, fixes)
        }

        private fun readInto(
            reader: BufferedReader,
            entries: MutableList<Pair<String, Int>>,
            fixes: MutableMap<String, String>,
        ) {
            reader.forEachLine { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val parts = line.split('\t')
                when {
                    parts.size == CORRECTION_FIELDS && parts[1] == "-" -> fixes[key(parts[0])] = parts[2]

                    parts.size == 2 -> {
                        val f = parts[1].toIntOrNull() ?: return@forEachLine
                        if (parts[0].length <= MAX_WORD) entries += parts[0] to f.coerceIn(0, MAX_FREQ)
                    }
                }
            }
        }

        fun build(
            language: String,
            entries: List<Pair<String, Int>>,
            fixes: Map<String, String> = emptyMap(),
        ): Dictionary {
            var sorted = entries.map { key(it.first) to it }
            if (sorted.zipWithNext().any { (a, b) -> a.first > b.first }) sorted = sorted.sortedBy { it.first }
            val b = Builder(sorted.size * 3)
            val words = ArrayList<String>(sorted.size)
            val freqs = ArrayList<Int>(sorted.size)
            var prev = ""
            val path = IntArray(MAX_WORD + 1)
            for ((k, entry) in sorted) {
                if (k.isEmpty() || k.length > MAX_WORD) continue
                if (k == prev) {
                    // Same key, different case ("us"/"US"): a lowercase form wins, so
                    // only words that exist capitalized alone ("Brasil") get case fixes.
                    val w = b.word[path[k.length - 1]]
                    if (entry.first == k || (words[w] != k && entry.second > freqs[w])) words[w] = entry.first
                    freqs[w] = maxOf(freqs[w], entry.second)
                    continue
                }
                var lcp = 0
                while (lcp < k.length && lcp < prev.length && k[lcp] == prev[lcp]) lcp++
                for (d in lcp until k.length) {
                    val parent = if (d == 0) ROOT else path[d - 1]
                    path[d] = b.add(parent, k[d])
                }
                b.word[path[k.length - 1]] = words.size
                words += entry.first
                freqs += entry.second
                prev = k
            }
            return b.finish(language, words.toTypedArray(), freqs.toIntArray(), fixes)
        }

        private const val INITIAL = 1 shl 17
        const val MAX_FREQ = 255
    }

    private class Builder(capacity: Int) {
        var chars = CharArray(capacity)
        var first = IntArray(capacity)
        var next = IntArray(capacity)
        var last = IntArray(capacity)
        var parent = IntArray(capacity)
        var word = IntArray(capacity)
        var count = 0

        init {
            newNode('\u0000', -1)
        }

        private fun newNode(c: Char, p: Int): Int {
            if (count == chars.size) grow()
            chars[count] = c
            first[count] = -1
            next[count] = -1
            last[count] = -1
            parent[count] = p
            word[count] = -1
            return count++
        }

        fun add(p: Int, c: Char): Int {
            val n = newNode(c, p)
            if (first[p] < 0) first[p] = n else next[last[p]] = n
            last[p] = n
            return n
        }

        private fun grow() {
            val size = chars.size * 2
            chars = chars.copyOf(size)
            first = first.copyOf(size)
            next = next.copyOf(size)
            last = last.copyOf(size)
            parent = parent.copyOf(size)
            word = word.copyOf(size)
        }

        fun finish(language: String, words: Array<String>, freqs: IntArray, fixes: Map<String, String>): Dictionary {
            val max = IntArray(count)
            for (i in count - 1 downTo 0) {
                val w = word[i]
                if (w >= 0 && freqs[w] > max[i]) max[i] = freqs[w]
                val p = parent[i]
                if (p >= 0 && max[i] > max[p]) max[p] = max[i]
            }
            return Dictionary(
                language,
                chars.copyOf(count),
                first.copyOf(count),
                next.copyOf(count),
                word.copyOf(count),
                max,
                words,
                freqs,
                fixes,
            )
        }
    }
}
