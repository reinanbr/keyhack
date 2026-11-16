package com.reinanbr.keyhack.suggest

import java.util.Locale

/** One entry in the suggestion strip. */
data class Candidate(val word: String, val score: Float, val kind: Kind) {
    enum class Kind { TYPED, CORRECTION, COMPLETION, PREDICTION }
}

/**
 * Result for the word being typed. [candidates] are best first.
 * [autoCorrection] is what a separator (space, punctuation) commits instead of
 * the typed text, or null to keep it.
 */
data class Suggestions(
    val typed: String,
    val typedIsWord: Boolean,
    val candidates: List<Candidate>,
    val autoCorrection: String?,
) {
    companion object {
        val EMPTY = Suggestions("", false, emptyList(), null)
    }
}

/**
 * Autocorrect, completion and next-word prediction over one or more
 * dictionaries, next-word models from conversation sentences ([ngrams]) and
 * the user's history. Pure and synchronous: callers run it off the main
 * thread (see Suggester).
 *
 * Context is the word before the cursor ([previous]) and the one before it
 * ([before]); it ranks candidates ("eu te am" -> "amo" over "ano") and drives
 * predictions.
 */
class SuggestionEngine(
    var dictionaries: List<Dictionary>,
    val history: UserHistory,
    var costs: EditCosts = EditCosts(),
    var ngrams: List<NgramModel> = emptyList(),
) {
    fun suggest(typedRaw: String, previous: String?, limit: Int = DEFAULT_LIMIT, before: String? = null): Suggestions {
        if (typedRaw.isEmpty()) return Suggestions.EMPTY
        val typed = Dictionary.key(typedRaw)
        val n = typed.length
        val found = HashMap<String, Candidate>()

        fun offer(display: String, score: Float, kind: Candidate.Kind) {
            val k = Dictionary.key(display)
            val old = found[k]
            if (old == null || score > old.score) found[k] = Candidate(display, score, kind)
        }

        val maxCost = maxCost(n)
        val completionCost = if (n >= FUZZY_PREFIX_MIN) EditCosts.NEIGHBOR else EditCosts.ACCENT * 2
        var typedFreq = -1
        var whitelist: String? = null
        for ((i, dict) in dictionaries.withIndex()) {
            val primaryBonus = if (i == 0) PRIMARY_BONUS else 0f
            val exact = dict.lookup(typed)
            if (exact >= 0) typedFreq = maxOf(typedFreq, dict.freqs[exact])
            if (whitelist == null) whitelist = dict.corrections[typed]
            dict.search(typed, costs, maxCost, completionCost, COMPLETIONS) { w, cost, completion ->
                val f = dict.freqs[w]
                if (f <= 0 && cost > 0f) return@search
                val word = dict.words[w]
                val (score, kind) = dictionaryScore(f, cost, completion, (word.length - n).coerceAtLeast(0))
                offer(word, score + primaryBonus + contextBonus(before, previous, word), kind)
            }
        }
        offerContextWords(typed, previous, before, ::offer)
        whitelist?.let { offer(it, WHITELIST_SCORE, Candidate.Kind.CORRECTION) }
        offerUserWords(typed, previous, before, maxCost, ::offer)

        val userValid = history.isValid(typed)
        val typedIsWord = typedFreq >= 0 || userValid
        val ranked =
            found.values
                .sortedByDescending { it.score }
                .map { it.copy(word = matchCase(typedRaw, it.word)) }

        val auto =
            autoCorrection(typedRaw, typed, typedIsWord, whitelist, ranked)
                ?: accentFix(typed, typedFreq, userValid, ranked)
        val typedCandidate = Candidate(typedRaw, Float.MAX_VALUE, Candidate.Kind.TYPED)
        // The typed word in another case ("am" / "AM") is not an alternative.
        val list = (listOf(typedCandidate) + ranked.filter { Dictionary.key(it.word) != typed }).take(limit + 1)
        return Suggestions(typedRaw, typedIsWord, list, auto)
    }

    private fun dictionaryScore(
        freq: Int,
        cost: Float,
        completion: Boolean,
        extraLen: Int,
    ): Pair<Float, Candidate.Kind> = when {
        completion && extraLen > 0 ->
            freq - cost * COST_WEIGHT - COMPLETION_PENALTY - extraLen * LENGTH_PENALTY to Candidate.Kind.COMPLETION

        cost == 0f && extraLen == 0 -> freq + EXACT_BONUS to Candidate.Kind.TYPED

        cost == 0f -> freq + EXACT_BONUS to Candidate.Kind.CORRECTION

        else -> freq - cost * COST_WEIGHT to Candidate.Kind.CORRECTION
    }

    /**
     * Words the context predicts that start with what is typed ("eu te am" ->
     * "amo"), even when the dictionary has more frequent words with that prefix.
     */
    private fun offerContextWords(
        typed: String,
        previous: String?,
        before: String?,
        offer: (String, Float, Candidate.Kind) -> Unit,
    ) {
        if (previous == null || typed.isEmpty()) return
        for (model in ngrams) {
            val pair = if (before != null) model.afterPair(before, previous) else emptyList()
            val followers = pair + model.after(previous)
            for ((w, _) in followers) {
                if (w.length <= typed.length || !w.startsWith(typed)) continue
                val freq = dictionaries.maxOfOrNull { d -> d.lookup(w).let { if (it < 0) 0 else d.freqs[it] } } ?: 0
                if (freq <= 0) continue
                val extra = w.length - typed.length
                val score = freq - COMPLETION_PENALTY - extra * LENGTH_PENALTY + contextBonus(before, previous, w)
                offer(displayForm(w), score, Candidate.Kind.COMPLETION)
            }
        }
    }

    private fun offerUserWords(
        typed: String,
        previous: String?,
        before: String?,
        maxCost: Float,
        offer: (String, Float, Candidate.Kind) -> Unit,
    ) {
        for ((word, count) in history.entries()) {
            if (count < UserHistory.VALID_COUNT && !history.isValid(word)) continue
            val prefix = word.length > typed.length && word.startsWith(typed)
            val cost = if (prefix) 0f else weightedDistance(typed, word, maxCost)
            if (cost > maxCost) continue
            val f = (USER_BASE + count * USER_STEP).coerceAtMost(Dictionary.MAX_FREQ.toFloat())
            val kind = when {
                prefix -> Candidate.Kind.COMPLETION
                cost == 0f -> Candidate.Kind.TYPED
                else -> Candidate.Kind.CORRECTION
            }
            val penalty = if (prefix) COMPLETION_PENALTY + (word.length - typed.length) * LENGTH_PENALTY else 0f
            offer(word, f - cost * COST_WEIGHT - penalty + contextBonus(before, previous, word), kind)
        }
    }

    private fun autoCorrection(
        typedRaw: String,
        typed: String,
        typedIsWord: Boolean,
        whitelist: String?,
        ranked: List<Candidate>,
    ): String? {
        if (whitelist != null && !history.isValid(typed)) return matchCase(typedRaw, whitelist)
        if (typedIsWord) {
            // A valid word stays, but words that only exist capitalized get their case ("i" -> "I").
            return if (typedRaw == typed) caseOnlyFix(typed) else null
        }
        if (typed.length < 2) return null
        val top = ranked.firstOrNull()?.score ?: return null
        val best = ranked.firstOrNull { it.kind == Candidate.Kind.CORRECTION } ?: return null
        return best.word.takeIf { best.score >= AUTOCORRECT_MIN_SCORE && best.score >= top - AUTOCORRECT_MARGIN }
    }

    /**
     * A rare word whose accented form is far more common gets the accents, e.g.
     * "voce" (English, as in "sotto voce") -> "você" when typing in both
     * languages. Common words ("ate", "esta") are left alone.
     */
    private fun accentFix(typed: String, typedFreq: Int, userValid: Boolean, ranked: List<Candidate>): String? {
        if (userValid || typedFreq !in 0 until ACCENT_FIX_RARE) return null
        val folded = EditCosts.fold(typed)
        val variant =
            ranked.firstOrNull {
                it.kind == Candidate.Kind.CORRECTION && it.word.length == typed.length &&
                    Dictionary.key(it.word) != typed && EditCosts.fold(Dictionary.key(it.word)) == folded
            } ?: return null
        return variant.word.takeIf { variant.score >= typedFreq + ACCENT_FIX_MARGIN }
    }

    private fun caseOnlyFix(typed: String): String? {
        var fix: String? = null
        for (d in dictionaries) {
            val i = d.lookup(typed)
            if (i < 0) continue
            if (d.words[i] == typed || d.freqs[i] < CASE_FIX_MIN_FREQ) return null
            fix = d.words[i]
        }
        return fix
    }

    /**
     * Next words after "[before] [previous]": the user's own habits first, then
     * the conversation models. A language whose model knows the two-word
     * context outranks the others, so Portuguese and English do not mix.
     */
    fun predict(previous: String, before: String? = null, limit: Int = DEFAULT_LIMIT): List<Candidate> {
        val scores = HashMap<String, Float>()
        val pairs = if (before != null) ngrams.map { it.afterPair(before, previous) } else emptyList()
        val anyPair = pairs.any { it.isNotEmpty() }
        for ((i, model) in ngrams.withIndex()) {
            val pair = pairs.getOrNull(i).orEmpty()
            val weight = if (anyPair && pair.isEmpty()) OTHER_LANGUAGE else 1f
            val best = HashMap<String, Float>()
            for ((w, s) in pair) best[w] = s * TRIGRAM_WEIGHT
            for ((w, s) in model.after(previous)) best[w] = maxOf(best[w] ?: 0f, s.toFloat())
            for ((w, s) in best) scores[w] = maxOf(scores[w] ?: 0f, s * weight)
        }
        for ((w, count) in history.followers(previous, USER_PREDICTIONS)) {
            val k = Dictionary.key(w)
            scores[k] = (scores[k] ?: 0f) + USER_PREDICTION_BASE + count.coerceAtMost(PAIR_CAP) * USER_PREDICTION_STEP
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Candidate(displayForm(it.key), it.value, Candidate.Kind.PREDICTION) }
    }

    /** The dictionary's spelling of a lowercase key ("i" -> "I", "brasil" -> "Brasil"). */
    private fun displayForm(key: String): String {
        for (d in dictionaries) {
            val i = d.lookup(key)
            if (i >= 0) return d.words[i]
        }
        return key
    }

    /** Bonus for a candidate that fits the words before it: the user's pairs and the conversation models. */
    private fun contextBonus(before: String?, previous: String?, word: String): Float {
        if (previous == null) return 0f
        val user = history.pairCount(previous, word).coerceAtMost(PAIR_CAP) * PAIR_BONUS
        val corpus = ngrams.maxOfOrNull { it.score(before, previous, word) } ?: 0
        return user + corpus * CONTEXT_WEIGHT / NgramModel.MAX_SCORE
    }

    /** Plain weighted edit distance, for the (small) user word list. */
    private fun weightedDistance(a: String, b: String, cap: Float): Float {
        if (kotlin.math.abs(a.length - b.length) > cap + 1) return Float.MAX_VALUE
        var prev = FloatArray(b.length + 1) { it * costs.missing }
        var cur = FloatArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i * costs.extra
            var best = cur[0]
            for (j in 1..b.length) {
                cur[j] =
                    minOf(
                        prev[j - 1] + costs.substitute(a[i - 1], b[j - 1]),
                        prev[j] + costs.extra,
                        cur[j - 1] + costs.missing,
                    )
                if (cur[j] < best) best = cur[j]
            }
            if (best > cap) return Float.MAX_VALUE
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    companion object {
        const val DEFAULT_LIMIT = 3
        private const val COMPLETIONS = 4
        private const val FUZZY_PREFIX_MIN = 4
        private const val COST_WEIGHT = 45f
        private const val EXACT_BONUS = 25f
        private const val COMPLETION_PENALTY = 12f
        private const val LENGTH_PENALTY = 3f
        private const val PRIMARY_BONUS = 4f
        private const val WHITELIST_SCORE = 400f
        private const val USER_BASE = 120f
        private const val USER_STEP = 12f
        private const val PAIR_BONUS = 15f
        private const val PAIR_CAP = 4
        private const val AUTOCORRECT_MIN_SCORE = 40f
        private const val AUTOCORRECT_MARGIN = 20f
        private const val CASE_FIX_MIN_FREQ = 100
        private const val ACCENT_FIX_RARE = 80
        private const val ACCENT_FIX_MARGIN = 50f
        private const val CONTEXT_WEIGHT = 80f
        private const val TRIGRAM_WEIGHT = 1.15f
        private const val OTHER_LANGUAGE = 0.5f
        private const val USER_PREDICTIONS = 5
        private const val USER_PREDICTION_BASE = 150f
        private const val USER_PREDICTION_STEP = 40f

        /** Largest edit cost still worth suggesting for a typed word of [length]. Tuned by hand. */
        @Suppress("MagicNumber")
        fun maxCost(length: Int): Float = when {
            length <= 2 -> EditCosts.ACCENT * 2
            length <= 3 -> EditCosts.TRANSPOSE + 0.05f
            length <= 5 -> 1.15f
            length <= 8 -> 1.7f
            else -> 2.2f
        }

        /** Applies the typed word's capitalization: "Teh" -> "The", "TEH" -> "THE". */
        fun matchCase(typed: String, word: String, locale: Locale = Locale.ROOT): String {
            if (typed.isEmpty() || word.isEmpty()) return word
            if (typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() }) return word.uppercase(locale)
            if (typed[0].isUpperCase()) return word.replaceFirstChar { it.titlecase(locale) }
            return word
        }
    }
}
