package com.reinanbr.keyhack.suggest

import java.text.Normalizer
import kotlin.math.hypot

/**
 * Edit costs for proximity-aware autocorrect. Substituting a neighboring key
 * or dropping an accent is cheap; an unrelated key costs a full edit.
 *
 * [centers] maps each letter to its key center in key units (from the
 * current layout), so the weights follow QWERTY, AZERTY, Dvorak...
 */
class EditCosts(centers: Map<Char, Pair<Float, Float>> = emptyMap()) {
    val missing = 1.0f
    val extra = 1.0f
    val transpose = TRANSPOSE

    private val near = HashSet<Int>()

    init {
        for ((a, pa) in centers) {
            for ((b, pb) in centers) {
                if (a != b && hypot(pa.first - pb.first, pa.second - pb.second) <= NEIGHBOR_DISTANCE) {
                    near += pairKey(a, b)
                }
            }
        }
    }

    private fun pairKey(a: Char, b: Char): Int = (a.code shl Char.SIZE_BITS) or b.code

    fun substitute(typed: Char, dict: Char): Float = when {
        typed == dict -> 0f
        fold(typed) == fold(dict) -> ACCENT
        pairKey(fold(typed), fold(dict)) in near -> NEIGHBOR
        else -> 1f
    }

    /** An extra typed character; a doubled letter ("helllo") is a cheaper slip. */
    fun extraTyped(typed: Char, previousTyped: Char): Float = if (typed == previousTyped) DOUBLED else extra

    companion object {
        const val ACCENT = 0.12f
        const val NEIGHBOR = 0.55f
        const val DOUBLED = 0.5f
        const val TRANSPOSE = 0.75f
        private const val NEIGHBOR_DISTANCE = 1.3f
        private const val TABLE = 0x250

        private val FOLD =
            CharArray(TABLE) { i ->
                val c = i.toChar()
                if (i < 0xC0) {
                    c
                } else {
                    Normalizer.normalize(
                        c.toString(),
                        Normalizer.Form.NFD,
                    ).firstOrNull()?.takeIf { it.code < 0x80 } ?: c
                }
            }

        /** Strips diacritics from Latin letters: ã -> a, ç -> c. */
        fun fold(c: Char): Char = if (c.code < TABLE) FOLD[c.code] else c

        fun fold(s: String): String = buildString(s.length) { s.forEach { append(fold(it)) } }
    }
}
