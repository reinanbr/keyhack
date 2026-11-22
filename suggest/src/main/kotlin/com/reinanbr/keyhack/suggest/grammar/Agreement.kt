package com.reinanbr.keyhack.suggest.grammar

import com.reinanbr.keyhack.suggest.grammar.PortugueseVerbs as Verbs

/**
 * Portuguese subject-verb agreement, shared by the grammar checker (a verb
 * already typed: "nós vai" -> "nós vamos") and the suggestion engine (the
 * verb being typed or predicted: after "nós não", "temos", never "tem").
 *
 * Only subject pronouns count ("eu", "nós", "a gente", "vocês"...): a noun
 * subject says too little about the person to steer suggestions safely.
 */
internal class Agreement(private val lexicon: Lexicon) {
    /** What a word becomes right after a subject. */
    sealed interface Fit {
        /** Not a verb form ("não", "me", "muito"): agreement has nothing to say. */
        data object NotVerb : Fit

        data object Agrees : Fit

        /** A verb in another person; [form] is the same verb and tense in the subject's person. */
        data class Becomes(val form: String) : Fit

        /** A verb in another person whose right form the dictionary does not know. */
        data object Wrong : Fit
    }

    /**
     * The person (PortugueseVerbs.S1..P3) a verb right after [keys] must be
     * in: "eles não me" -> P3, "a gente" -> S3; null when no subject pronoun
     * leads up to the end. [keys] are lowercase words, the last one last.
     */
    fun personAfter(keys: List<String>): Int? = subjectBefore(keys, keys.size)?.second

    /**
     * [word] (lowercase) after a subject in [person]. [strict] also fixes
     * "é" after "eu"/"tu"/"você"; the checker leaves "eu é que sei" alone.
     */
    fun fit(word: String, person: Int, strict: Boolean = true): Fit {
        if (word in NOT_VERBS || (!strict && word == "é" && person != Verbs.P1 && person != Verbs.P3)) {
            return Fit.NotVerb
        }
        val parses = Verbs.parse(word) { lexicon.freq(it) > 0 }
        if (parses.isEmpty()) return Fit.NotVerb
        if (parses.any { accepts(person, it.person) }) return Fit.Agrees
        val best = parses.maxBy { lexicon.freq(it.lemma) }
        val form = Verbs.forms(best.lemma)[best.tense]?.getOrNull(person)?.takeIf { lexicon.freq(it) >= 0 }
        return if (form == null) Fit.Wrong else Fit.Becomes(form)
    }

    companion object {
        /**
         * The subject pronoun before the word at [verbAt] in [keys] (adverbs
         * and clitic pronouns may sit between: "eles não te"), as (index of
         * its first word, person); null when there is none.
         */
        fun subjectBefore(keys: List<String>, verbAt: Int): Pair<Int, Int>? {
            var i = verbAt - 1
            var skipped = 0
            while (i >= 0 && keys[i] in VERB_ADVERBS && skipped < MAX_SKIPPED) {
                i--
                skipped++
            }
            val subject =
                when {
                    i < 0 -> null
                    keys[i] == "gente" && i > 0 && keys[i - 1] == "a" -> (i - 1) to Verbs.S3
                    else -> SUBJECTS[keys[i]]?.let { i to it }
                }
            // "para ele", "com eles", "eu e ele vamos": not the (whole) subject.
            return subject?.takeIf { (start, _) -> start == 0 || keys[start - 1] !in NOT_SUBJECT_AFTER }
        }

        /** "tu vai" is how most of Brazil speaks; it is left alone. */
        private fun accepts(subject: Int, verb: Int): Boolean =
            verb == subject || (subject == Verbs.S2 && verb == Verbs.S3)

        private const val MAX_SKIPPED = 3

        private val SUBJECTS =
            mapOf(
                "eu" to Verbs.S1,
                "tu" to Verbs.S2,
                "você" to Verbs.S3, "vc" to Verbs.S3, "cê" to Verbs.S3, "ele" to Verbs.S3, "ela" to Verbs.S3,
                "nós" to Verbs.P1,
                "vocês" to Verbs.P3, "vcs" to Verbs.P3, "eles" to Verbs.P3, "elas" to Verbs.P3,
            )

        /** Words that may sit between subject and verb: "eles não vão", "eu te amo". */
        private val VERB_ADVERBS =
            setOf(
                "não", "nao", "nunca", "já", "ja", "também", "tb", "tbm", "sempre", "ainda", "só", "me", "te",
                "se", "lhe", "lhes", "vos", "quase", "mesmo",
            )

        private val NOT_SUBJECT_AFTER =
            setOf(
                "para", "pra", "com", "de", "por", "sem", "entre", "a", "até", "sobre", "contra", "e", "ou", "nem",
                ",", "pelo", "pela", "do", "da", "ao", "em",
            )

        /** Function words that look like verb forms (parar, comer, sobrar, logar, ceder). */
        private val NOT_VERBS =
            setOf(
                "para", "como", "sobre", "entre", "logo", "cedo", "segundo", "contra", "mesmo", "menos", "nada",
                "tudo", "todo", "toda", "assim", "então", "durante", "conforme", "embora", "fora", "afora",
            )
    }
}
