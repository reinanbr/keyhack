package com.reinanbr.keyhack.suggest.grammar

import com.reinanbr.keyhack.suggest.SuggestionEngine
import java.util.Locale
import com.reinanbr.keyhack.suggest.grammar.PortugueseVerbs as Verbs

/**
 * A suggested rewrite of the words just typed: the text before the cursor
 * ends with [original] followed by [tail] (spaces and punctuation typed
 * after it); tapping the fix turns [original] into [replacement].
 */
data class GrammarFix(val original: String, val replacement: String, val tail: String)

/** One language's dictionary and rules file. */
class LanguageGrammar internal constructor(
    val language: String,
    val lexicon: Lexicon,
    internal val rules: List<GrammarRule>,
) {
    companion object {
        /** [rulesText] is a grammar/<lang>.rules file; bad lines are skipped. */
        fun create(language: String, lexicon: Lexicon, rulesText: java.io.BufferedReader?): LanguageGrammar =
            LanguageGrammar(language, lexicon, rulesText?.let { GrammarRules.parse(it) }.orEmpty())
    }
}

/**
 * On-device grammar and agreement checks, run on the sentence being typed
 * each time a word is finished. Never rewrites text by itself: it returns a
 * [GrammarFix] for the suggestion bar.
 *
 * Checks, in order: the language's rules file (fixed expressions: "a muito
 * tempo" -> "há muito tempo"), then agreement in code: pronoun and verb
 * ("nós vai" -> "nós vamos"), plural determiner and noun ("os menino" ->
 * "os meninos"), "porque" in questions, and English "a"/"an". Only the
 * languages that own most of the sentence's words are checked, so English
 * rules never fire on Portuguese text.
 *
 * Pure and synchronous; callers run it off the main thread.
 */
class GrammarChecker(private val languages: List<LanguageGrammar>) {
    private data class Match(val start: Int, val replacement: String)

    fun check(text: String): GrammarFix? {
        val tailStart = tailStart(text) ?: return null
        val tail = text.substring(tailStart)
        val body = text.substring(0, tailStart)
        val sentenceStart = body.indexOfLast { it in SENTENCE_BREAKS } + 1
        val tokens = tokenize(body, sentenceStart)
        if (tokens.isEmpty() || tokens.last().end != body.length) return null
        for (lang in rankLanguages(tokens)) {
            val m = matchLanguage(lang, body, tokens, tail) ?: continue
            val original = body.substring(tokens[m.start].start)
            val replacement = keepCapital(original, m.replacement)
            if (replacement != original) return GrammarFix(original, replacement, tail)
        }
        return null
    }

    /** Where the spaces and punctuation after the last word start; null if the word is not finished. */
    private fun tailStart(text: String): Int? {
        var i = text.length
        while (i > 0 && text[i - 1].let { it.isWhitespace() || it in TAIL_PUNCTUATION }) i--
        val newLineInside = text.indexOf('\n', i).let { it >= 0 && it < text.length - 1 }
        return i.takeIf { it in 1 until text.length && !newLineInside }
    }

    private fun matchLanguage(lang: LanguageGrammar, body: String, tokens: List<Token>, tail: String): Match? {
        for (rule in lang.rules) {
            val start = rule.matchEnd(tokens, lang.lexicon)
            if (start >= 0) return Match(start, rule.render(tokens.subList(start, tokens.size)))
        }
        return when (lang.language) {
            "pt" ->
                verbAgreement(lang.lexicon, body, tokens) ?: nounPlural(lang.lexicon, body, tokens)
                    ?: porque(body, tokens, tail)

            "en" -> article(lang, body, tokens)

            else -> null
        }
    }

    /** Languages that own at least half as many of the sentence's words as the best one, best first. */
    private fun rankLanguages(tokens: List<Token>): List<LanguageGrammar> {
        if (languages.size <= 1) return languages
        val scores = languages.map { lang ->
            tokens.count { t ->
                val f = lang.lexicon.freq(t.key)
                f > 0 && languages.all { other -> other === lang || other.lexicon.freq(t.key) <= f }
            }
        }
        val best = scores.max()
        return languages.indices.filter {
            scores[it] * 2 >= best
        }.sortedByDescending { scores[it] }.map { languages[it] }
    }

    // ------------------------------------------------------------ Portuguese

    /** "nós vai" -> "nós vamos", "eles fala" -> "eles falam", "a gente fomos" -> "a gente foi". */
    private fun verbAgreement(lexicon: Lexicon, body: String, tokens: List<Token>): Match? {
        val verb = tokens.last()
        val (subjectStart, person) = Agreement.subjectBefore(tokens.map { it.key }, tokens.lastIndex) ?: return null
        val fit =
            Agreement(lexicon).fit(verb.key, person, strict = false, subjunctive = true) as? Agreement.Fit.Becomes
                ?: return null
        val prefix = body.substring(tokens[subjectStart].start, verb.start)
        return Match(subjectStart, prefix + SuggestionEngine.matchCase(verb.text, fit.form))
    }

    /** "os menino" -> "os meninos", "dois real" -> "dois reais", "três mês" -> "três meses". */
    private fun nounPlural(lexicon: Lexicon, body: String, tokens: List<Token>): Match? {
        val n = tokens.size
        if (n < 2) return null
        val det = tokens[n - 2]
        val noun = tokens[n - 1]
        val k = noun.key
        if (det.key !in PLURAL_DETERMINERS && !isPluralNumber(det.key)) return null
        // "eu os vi", "não as quero": a pronoun, not an article.
        if (det.key in CLITICS && tokens.getOrNull(n - DET_LOOKBACK)?.key in CLITIC_BEFORE) return null
        if (k in NOT_NOUNS || k.any { it.isDigit() } || lexicon.freq(k) <= 0) return null
        if ((k.endsWith("s") && !k.endsWith("ês")) || Verbs.isIrregularForm(k) || isInfinitive(lexicon, k)) return null
        val plural = pluralCandidates(k).filter { lexicon.freq(it) > 0 }.maxByOrNull { lexicon.freq(it) } ?: return null
        return Match(n - 2, body.substring(det.start, noun.start) + SuggestionEngine.matchCase(noun.text, plural))
    }

    private fun isInfinitive(lexicon: Lexicon, k: String): Boolean {
        val p1 = Verbs.forms(k)[Verbs.Tense.PRESENT]?.getOrNull(Verbs.P1) ?: return false
        return lexicon.freq(p1) > 0
    }

    private fun pluralCandidates(w: String): List<String> = when {
        w.endsWith("ão") -> listOf(w.dropLast(2) + "ões", w.dropLast(2) + "ães", w + "s")
        w.endsWith("ês") -> listOf(w.dropLast(2) + "eses")
        w.last() in VOWELS -> listOf(w + "s")
        w.endsWith("r") || w.endsWith("z") -> listOf(w + "es")
        w.endsWith("al") || w.endsWith("ul") -> listOf(w.dropLast(1) + "is")
        w.endsWith("ol") -> listOf(w.dropLast(2) + "óis")
        w.endsWith("el") -> listOf(w.dropLast(2) + "éis", w.dropLast(2) + "eis")
        w.endsWith("il") -> listOf(w.dropLast(1) + "s", w.dropLast(2) + "eis")
        w.endsWith("m") -> listOf(w.dropLast(1) + "ns")
        w.endsWith("n") -> listOf(w + "s", w + "es")
        else -> emptyList()
    }

    /** "Porque você não veio?" -> "Por que você não veio?"; "...não veio, porque?" -> "por quê?". */
    private fun porque(body: String, tokens: List<Token>, tail: String): Match? {
        if ('?' !in tail) return null
        val last = tokens.last()
        if (last.key == "porque") return Match(tokens.lastIndex, SuggestionEngine.matchCase(last.text, "por quê"))
        val first = tokens.first()
        if (first.key != "porque" || tokens.size < 2) return null
        return Match(0, SuggestionEngine.matchCase(first.text, "por que") + body.substring(first.end))
    }

    // --------------------------------------------------------------- English

    /** "a apple" -> "an apple", "an book" -> "a book"; "a university", "an hour" stay. */
    private fun article(lang: LanguageGrammar, body: String, tokens: List<Token>): Match? {
        val n = tokens.size
        if (n < 2) return null
        val art = tokens[n - 2]
        val word = tokens[n - 1]
        if (art.key != "a" && art.key != "an") return null
        // Acronyms ("an FBI agent") and digits are read letter by letter; leave them.
        if (word.text.length > 1 && word.text.all { it.isUpperCase() || it.isDigit() }) return null
        val k = word.key
        val f = lang.lexicon.freq(k)
        if (f <= 0 || languages.any { it !== lang && it.lexicon.freq(k) > f }) return null
        val wantsAn = startsWithVowelSound(k)
        val fixed = when {
            art.key == "a" && wantsAn -> "an"
            art.key == "an" && !wantsAn -> "a"
            else -> return null
        }
        return Match(n - 2, SuggestionEngine.matchCase(art.text, fixed) + body.substring(art.end))
    }

    private fun startsWithVowelSound(w: String): Boolean {
        if (SILENT_H.any { w.startsWith(it) } || VOWEL_UN.any { w.startsWith(it) }) return true
        if (CONSONANT_SOUND.any { w.startsWith(it) }) return false
        return w.first() in "aeiou"
    }

    // ---------------------------------------------------------------- shared

    private fun keepCapital(original: String, replacement: String): String {
        if (original.isEmpty() || replacement.isEmpty() || !original[0].isUpperCase()) return replacement
        return replacement.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    internal companion object {
        private val TOKEN = Regex("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*|,")
        private const val TAIL_PUNCTUATION = ".,!?;:…"
        private const val SENTENCE_BREAKS = ".!?;:\n…()\"“”"
        private const val DET_LOOKBACK = 3
        private const val VOWELS = "aeiouáéíóúâêôãõà"

        fun tokenize(text: String, from: Int = 0): List<Token> =
            TOKEN.findAll(text, from).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

        private val PLURAL_DETERMINERS =
            setOf(
                "os", "as", "uns", "umas", "dos", "das", "aos", "às", "pelos", "pelas", "nas",
                "meus", "minhas", "teus", "tuas", "seus", "suas", "nossos", "nossas", "vossos", "vossas",
                "esses", "essas", "estes", "estas", "aqueles", "aquelas", "desses", "dessas", "destes", "destas",
                "daqueles", "daquelas", "nesses", "nessas", "nestes", "nestas", "naqueles", "naquelas",
                "muitos", "muitas", "vários", "várias", "alguns", "algumas", "poucos", "poucas", "outros",
                "outras", "quantos", "quantas", "tantos", "tantas",
            )

        private val CLITICS = setOf("os", "as")
        private val CLITIC_BEFORE =
            setOf(
                "eu", "tu", "ele", "ela", "nós", "você", "vc", "vocês", "eles", "elas", "gente", "não", "nao",
                "se", "já", "nunca", "também", "me", "te", "lhe", "sempre", "ainda",
            )

        private val NOT_NOUNS =
            setOf(
                "que", "quem", "de", "e", "a", "o", "um", "uma", "não", "se", "bem", "mal", "muito", "pouco",
                "tanto", "todo", "toda", "nada", "tudo", "algo", "mais", "menos", "mesmo", "outro", "outra",
                "dele", "dela", "deles", "delas", "meu", "minha", "seu", "sua", "sim", "lá", "aqui", "ali",
                "já", "só", "também", "quando", "onde", "como", "porque", "pra", "pro", "com", "em", "no", "na",
            )

        private val SILENT_H = listOf("hour", "honest", "honor", "honour", "heir")
        private val CONSONANT_SOUND =
            listOf(
                "uni", "use", "usu", "usa", "uti", "ute", "uto", "ure", "uri", "euro", "eu", "one", "once",
                "ewe", "ufo", "uk", "ura",
            )

        /** "un-" + i: "an unimportant", "an uninstalled", "an unidentified". */
        private val VOWEL_UN = listOf("unin", "unim", "unid")
    }
}
