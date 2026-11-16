package com.reinanbr.keyhack.suggest

import java.io.BufferedReader
import java.io.Writer

/**
 * On-device learning: how often each word and each word pair was typed.
 * Count-only: no timestamps, no sentences, nothing that reconstructs what was
 * written. Never fed from password fields or incognito sessions (the IME
 * decides; see EditorPolicy).
 */
class UserHistory(private val maxWords: Int = MAX_WORDS, private val maxPairs: Int = MAX_PAIRS) {
    private val words = HashMap<String, Int>()
    private val pairs = HashMap<String, HashMap<String, Int>>()
    private var pairCount = 0

    /** Words the user rejected an autocorrection for; treated as valid. */
    private val accepted = HashSet<String>()

    var dirty = false
        private set

    val wordCount: Int get() = words.size

    fun count(word: String): Int = words[Dictionary.key(word)] ?: 0

    /** A word typed at least [VALID_COUNT] times, or explicitly kept by the user, is valid. */
    fun isValid(word: String): Boolean {
        val k = Dictionary.key(word)
        return k in accepted || (words[k] ?: 0) >= VALID_COUNT
    }

    fun learn(word: String, previous: String?) {
        val k = Dictionary.key(word)
        if (k.isEmpty() || k.length > Dictionary.MAX_WORD) return
        words[k] = (words[k] ?: 0) + 1
        if (previous != null) {
            val p = Dictionary.key(previous)
            val next = pairs.getOrPut(p) { HashMap() }
            if (k !in next) pairCount++
            next[k] = (next[k] ?: 0) + 1
        }
        dirty = true
        if (words.size > maxWords || pairCount > maxPairs) prune()
    }

    /** The user undid an autocorrection or picked their literal word. */
    fun accept(word: String) {
        val k = Dictionary.key(word)
        if (k.isEmpty()) return
        accepted += k
        words[k] = maxOf(words[k] ?: 0, VALID_COUNT)
        dirty = true
    }

    fun pairCount(previous: String, word: String): Int = pairs[Dictionary.key(previous)]?.get(Dictionary.key(word)) ?: 0

    /** Most frequent followers of [previous]. */
    fun predict(previous: String, limit: Int): List<String> = followers(previous, limit).map { it.first }

    /** Most frequent followers of [previous], with how often each followed it. */
    fun followers(previous: String, limit: Int): List<Pair<String, Int>> = pairs[Dictionary.key(previous)]
        ?.entries
        ?.sortedByDescending { it.value }
        ?.take(limit)
        ?.map { it.key to it.value }
        .orEmpty()

    /** All learned words with counts, for fuzzy matching (small: capped at [maxWords]). */
    fun entries(): Set<Map.Entry<String, Int>> = words.entries

    fun clear() {
        words.clear()
        pairs.clear()
        accepted.clear()
        pairCount = 0
        dirty = true
    }

    private fun prune() {
        // Halve every count and drop what reaches zero: old habits fade first.
        words.replaceAll { _, c -> c / 2 }
        words.values.removeAll { it == 0 }
        pairCount = 0
        for (next in pairs.values) {
            next.replaceAll { _, c -> c / 2 }
            next.values.removeAll { it == 0 }
            pairCount += next.size
        }
        pairs.values.removeAll { it.isEmpty() }
    }

    fun write(out: Writer) {
        for ((w, c) in words) out.write("w\t$w\t$c\n")
        for (w in accepted) out.write("a\t$w\n")
        for ((p, next) in pairs) for ((w, c) in next) out.write("b\t$p\t$w\t$c\n")
        dirty = false
    }

    fun read(input: BufferedReader) {
        clear()
        input.forEachLine { line ->
            val f = line.split('\t')
            when {
                f.size == WORD_FIELDS && f[0] == "w" -> f[2].toIntOrNull()?.let { words[f[1]] = it }

                f.size == ACCEPTED_FIELDS && f[0] == "a" -> accepted += f[1]

                f.size == PAIR_FIELDS && f[0] == "b" ->
                    f[PAIR_FIELDS - 1].toIntOrNull()?.let {
                        pairs.getOrPut(f[1]) { HashMap() }[f[2]] = it
                        pairCount++
                    }
            }
        }
        dirty = false
    }

    companion object {
        private const val WORD_FIELDS = 3
        private const val ACCEPTED_FIELDS = 2
        private const val PAIR_FIELDS = 4
        const val VALID_COUNT = 3
        const val MAX_WORDS = 20_000
        const val MAX_PAIRS = 60_000
    }
}
