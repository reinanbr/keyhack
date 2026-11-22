package com.reinanbr.keyhack.suggest

import java.text.Normalizer
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.hypot

/**
 * A finger path drawn over the letters (glide typing), in key units: x in
 * key widths, y in rows, the same space as the layout's letter centers.
 * [aspect] is a row's height divided by a key's width, so distances can be
 * measured in key widths on both axes.
 */
class GlidePath(val xs: FloatArray, val ys: FloatArray, val aspect: Float) {
    init {
        require(xs.size == ys.size) { "xs and ys differ in size" }
    }
}

/**
 * The geometric half of glide typing, after SHARK² (Kristensson and Zhai,
 * 2004): a word's ideal path is the polyline through its letters' key
 * centers, and a drawn path matches a word as well as the two lie on top of
 * each other once both are resampled to the same number of points.
 *
 * Candidates come from the dictionary: words whose first and last letters
 * are near where the finger started and stopped, whose other letters the
 * path passes close to in order. Only those get the full comparison.
 * Frequency and context are the engine's job ([SuggestionEngine.glide]).
 */
internal class GlideDecoder {
    /** Letter -> key center in key units (x in key widths, y in rows). */
    var centers: Map<Char, Pair<Float, Float>> = emptyMap()

    private val indexes = WeakHashMap<Dictionary, Index>()

    /** The drawn path, resampled and scaled, with the centers in the same space. */
    class Prepared internal constructor(
        val xs: FloatArray,
        val ys: FloatArray,
        val keys: Map<Char, Pair<Float, Float>>,
        val starts: Set<Char>,
        val ends: Set<Char>,
    )

    /** Null when the path is too short to be a glide or there is no layout. */
    fun prepare(path: GlidePath): Prepared? {
        if (centers.isEmpty() || path.xs.size < 2) return null
        val keys = centers.mapValues { (_, c) -> c.first to c.second * path.aspect }
        val ys = FloatArray(path.ys.size) { path.ys[it] * path.aspect }
        if (length(path.xs, ys) < MIN_PATH) return null
        val (rx, ry) = resample(path.xs, ys)
        return Prepared(
            rx,
            ry,
            keys,
            near(keys, rx[0], ry[0]),
            near(keys, rx[SAMPLES - 1], ry[SAMPLES - 1]),
        )
    }

    /** Calls [sink] with each word of [dict] that fits [p] and its cost (mean distance, key widths). */
    fun forEachCandidate(p: Prepared, dict: Dictionary, sink: (word: Int, cost: Float) -> Unit) {
        val index = indexes.getOrPut(dict) { Index(dict) }
        for (s in p.starts) {
            for (e in p.ends) {
                val bucket = index.buckets[bucketKey(s, e)] ?: continue
                for (w in bucket) letterCost(p, index.letters[w])?.let { sink(w, it) }
            }
        }
    }

    /** The cost of [word] for [p], or null when it does not fit. For words outside the dictionaries. */
    fun cost(p: Prepared, word: String): Float? {
        val letters = lettersOf(word)
        if (letters.length < 2 || fold(letters[0]) !in p.starts || fold(letters.last()) !in p.ends) return null
        return letterCost(p, letters)
    }

    private fun letterCost(p: Prepared, letters: String): Float? {
        val (kx, ky) = keyPath(p, letters) ?: return null
        val n = kx.size
        if (hypot(kx[0] - p.xs[0], ky[0] - p.ys[0]) > END_RADIUS) return null
        if (hypot(kx[n - 1] - p.xs[SAMPLES - 1], ky[n - 1] - p.ys[SAMPLES - 1]) > END_RADIUS) return null
        if (!passesThrough(p, kx, ky)) return null
        val (ix, iy) = resample(kx, ky)
        var sum = 0f
        var weights = 0f
        for (i in 0 until SAMPLES) {
            // Where the finger starts and stops is drawn with more care than the turns.
            val w = if (i == 0 || i == SAMPLES - 1) END_WEIGHT else 1f
            sum += w * hypot(ix[i] - p.xs[i], iy[i] - p.ys[i])
            weights += w
        }
        val cost = sum / weights
        return if (cost <= MAX_COST) cost else null
    }

    /** Key centers of [letters], consecutive repeats merged ("ss" is one stop); null if a letter has no key. */
    private fun keyPath(p: Prepared, letters: String): Pair<FloatArray, FloatArray>? {
        val xs = FloatArray(letters.length)
        val ys = FloatArray(letters.length)
        var n = 0
        var last: Pair<Float, Float>? = null
        for (c in letters) {
            val k = p.keys[c] ?: p.keys[fold(c)] ?: return null
            if (k == last) continue
            xs[n] = k.first
            ys[n] = k.second
            n++
            last = k
        }
        if (n < 2) return null
        return xs.copyOf(n) to ys.copyOf(n)
    }

    /** Each inner key is near the path, in order. */
    private fun passesThrough(p: Prepared, kx: FloatArray, ky: FloatArray): Boolean {
        var j = 0
        for (k in 1 until kx.size - 1) {
            while (j < SAMPLES && hypot(p.xs[j] - kx[k], p.ys[j] - ky[k]) > PASS_RADIUS) j++
            if (j == SAMPLES) return false
        }
        return true
    }

    private class Index(dict: Dictionary) {
        /** Lowercase letters of each word ("don't" -> "dont"); empty when it has fewer than two. */
        val letters: Array<String> = Array(dict.words.size) { lettersOf(dict.words[it]) }
        val buckets: Map<Int, IntArray>

        init {
            val lists = HashMap<Int, ArrayList<Int>>()
            for (w in letters.indices) {
                val l = letters[w]
                if (l.length < 2) continue
                lists.getOrPut(bucketKey(fold(l[0]), fold(l.last()))) { ArrayList() } += w
            }
            buckets = lists.mapValues { (_, v) -> v.toIntArray() }
        }
    }

    companion object {
        /** Points both paths are resampled to. */
        const val SAMPLES = 48

        /** Paths shorter than this (key widths) are taps, not glides. */
        private const val MIN_PATH = 0.6f

        /** How far from the first and last key the finger may start and stop. */
        private const val END_RADIUS = 1.1f

        /** How close the path must come to each inner letter. */
        private const val PASS_RADIUS = 1.0f
        private const val END_WEIGHT = 3f

        /** Words whose ideal path is further than this on average are dropped. */
        private const val MAX_COST = 1.2f

        private fun bucketKey(first: Char, last: Char) = (first.code shl Char.SIZE_BITS) or last.code

        fun lettersOf(word: String): String = word.lowercase(Locale.ROOT).filter { it.isLetter() }

        private val foldCache = HashMap<Char, Char>()

        /** "ã" -> "a", "ç" -> "c": the key a letter is typed on when the layout has no key of its own. */
        fun fold(c: Char): Char = synchronized(foldCache) {
            foldCache.getOrPut(c) { Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0] }
        }

        private fun near(keys: Map<Char, Pair<Float, Float>>, x: Float, y: Float): Set<Char> {
            val within = keys.filter { (_, c) -> hypot(c.first - x, c.second - y) <= END_RADIUS }.keys
            val set = within.ifEmpty {
                setOfNotNull(keys.minByOrNull { (_, c) -> hypot(c.first - x, c.second - y) }?.key)
            }
            return set.mapTo(HashSet()) { fold(it) }
        }

        private fun length(xs: FloatArray, ys: FloatArray): Float {
            var sum = 0f
            for (i in 1 until xs.size) sum += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            return sum
        }

        /** [SAMPLES] points evenly spaced along the polyline. */
        fun resample(xs: FloatArray, ys: FloatArray): Pair<FloatArray, FloatArray> {
            val ox = FloatArray(SAMPLES)
            val oy = FloatArray(SAMPLES)
            val total = length(xs, ys)
            if (total <= 0f) {
                ox.fill(xs[0])
                oy.fill(ys[0])
                return ox to oy
            }
            val step = total / (SAMPLES - 1)
            var seg = 1
            var segStart = 0f
            var segLen = hypot(xs[1] - xs[0], ys[1] - ys[0])
            for (i in 0 until SAMPLES) {
                val at = minOf(i * step, total)
                while (seg < xs.size - 1 && segStart + segLen < at) {
                    segStart += segLen
                    seg++
                    segLen = hypot(xs[seg] - xs[seg - 1], ys[seg] - ys[seg - 1])
                }
                val t = if (segLen > 0f) ((at - segStart) / segLen).coerceIn(0f, 1f) else 0f
                ox[i] = xs[seg - 1] + (xs[seg] - xs[seg - 1]) * t
                oy[i] = ys[seg - 1] + (ys[seg] - ys[seg - 1]) * t
            }
            return ox to oy
        }
    }
}
