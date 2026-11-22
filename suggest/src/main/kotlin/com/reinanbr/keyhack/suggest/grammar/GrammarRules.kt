package com.reinanbr.keyhack.suggest.grammar

import java.io.BufferedReader
import java.util.Locale

/** Word frequency lookup (0..255), -1 for unknown words. Keys are lowercase. */
fun interface Lexicon {
    fun freq(word: String): Int
}

/** One word of a sentence; [key] is lowercase. Offsets are into the checked text. */
internal data class Token(val text: String, val start: Int, val end: Int) {
    val key: String = text.lowercase(Locale.ROOT)
}

/** One token of a rule pattern. */
internal sealed interface Matcher {
    fun matches(token: Token, lexicon: Lexicon): Boolean

    data class Words(val words: Set<String>) : Matcher {
        override fun matches(token: Token, lexicon: Lexicon) = token.key in words
    }

    data object AnyWord : Matcher {
        override fun matches(token: Token, lexicon: Lexicon) = token.key != ","
    }

    /** An infinitive the dictionary knows. */
    data object Infinitive : Matcher {
        override fun matches(token: Token, lexicon: Lexicon): Boolean {
            val k = token.key
            return k.length >= MIN_INFINITIVE && INFINITIVE_ENDINGS.any { k.endsWith(it) } && lexicon.freq(k) > 0
        }
    }

    data object Number : Matcher {
        override fun matches(token: Token, lexicon: Lexicon) = isPluralNumber(token.key)
    }

    /** Any of several matchers (alternatives mixing words and classes: "<num>|daqui"). */
    data class AnyOf(val options: List<Matcher>) : Matcher {
        override fun matches(token: Token, lexicon: Lexicon) = options.any { it.matches(token, lexicon) }
    }

    companion object {
        private const val MIN_INFINITIVE = 2
        private val INFINITIVE_ENDINGS = listOf("ar", "er", "ir", "or", "ôr")
    }
}

/** A replacement word: literal text, or a copy of the [index]th matched word (0-based). */
internal sealed interface Part {
    data class Literal(val text: String) : Part

    data class Ref(val index: Int) : Part
}

/** A rule from a grammar/<lang>.rules file. See that file for the syntax. */
internal class GrammarRule(
    val pattern: List<Matcher>,
    val replacement: List<Part>,
    val atStart: Boolean = false,
    val notBefore: Matcher? = null,
) {
    /** Index of the first matched token when the rule matches the end of [tokens], else -1. */
    fun matchEnd(tokens: List<Token>, lexicon: Lexicon): Int {
        val start = tokens.size - pattern.size
        if (start < 0 || (atStart && start != 0)) return -1
        for (i in pattern.indices) if (!pattern[i].matches(tokens[start + i], lexicon)) return -1
        if (notBefore != null && start > 0 && notBefore.matches(tokens[start - 1], lexicon)) return -1
        return start
    }

    fun render(matched: List<Token>): String {
        val sb = StringBuilder()
        for (part in replacement) {
            val word =
                when (part) {
                    is Part.Literal -> part.text
                    is Part.Ref -> matched.getOrNull(part.index)?.text ?: continue
                }
            if (sb.isNotEmpty() && word != ",") sb.append(' ')
            sb.append(word)
        }
        return sb.toString()
    }
}

internal object GrammarRules {
    class SyntaxException(message: String) : IllegalArgumentException(message)

    /** Parses a rules file; [strict] throws on a bad line (tests), otherwise bad lines are skipped. */
    fun parse(reader: BufferedReader, strict: Boolean = false): List<GrammarRule> {
        val rules = ArrayList<GrammarRule>()
        reader.useLines { lines ->
            for ((n, raw) in lines.withIndex()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val rule = runCatching { parseLine(line) }
                rule.onSuccess { rules += it }
                if (strict) rule.onFailure { throw SyntaxException("line ${n + 1}: ${it.message}") }
            }
        }
        return rules
    }

    fun parseLine(line: String): GrammarRule {
        val arrow = line.indexOf("=>")
        if (arrow < 0) throw SyntaxException("missing =>")
        var left = line.substring(0, arrow).trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val right = line.substring(arrow + 2).trim().split(WHITESPACE).filter { it.isNotEmpty() }
        var atStart = false
        var notBefore: Matcher? = null
        when {
            left.firstOrNull() == "^" -> {
                atStart = true
                left = left.drop(1)
            }

            left.firstOrNull()?.startsWith("!") == true -> {
                notBefore = matcher(left.first().substring(1))
                left = left.drop(1)
            }
        }
        if (left.isEmpty() || right.isEmpty()) throw SyntaxException("empty side")
        val pattern = left.map(::matcher)
        val replacement =
            right.map { w ->
                if (w.startsWith("$")) {
                    val i = w.substring(1).toIntOrNull() ?: throw SyntaxException("bad reference $w")
                    if (i !in 1..pattern.size) throw SyntaxException("reference $w out of range")
                    Part.Ref(i - 1)
                } else {
                    Part.Literal(w)
                }
            }
        return GrammarRule(pattern, replacement, atStart, notBefore)
    }

    private fun matcher(token: String): Matcher {
        if (token == "_") return Matcher.AnyWord
        val options = token.split('|').filter { it.isNotEmpty() }
        if (options.isEmpty()) throw SyntaxException("empty alternative in $token")
        val words = HashSet<String>()
        val classes = ArrayList<Matcher>()
        for (o in options) {
            when (o) {
                "<inf>" -> classes += Matcher.Infinitive

                "<num>" -> classes += Matcher.Number

                else -> {
                    if (o.startsWith("<")) throw SyntaxException("unknown class $o")
                    words += o.lowercase(Locale.ROOT)
                }
            }
        }
        if (classes.isEmpty()) return Matcher.Words(words)
        if (words.isEmpty() && classes.size == 1) return classes.single()
        return Matcher.AnyOf(classes + listOfNotNull(words.takeIf { it.isNotEmpty() }?.let { Matcher.Words(it) }))
    }

    private val WHITESPACE = Regex("\\s+")
}

private val NUMBER_WORDS =
    setOf(
        "dois", "duas", "três", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze",
        "doze", "treze", "quinze", "vinte", "trinta", "quarenta", "cinquenta", "cem", "cento", "mil",
        "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "twenty", "hundred",
    )

/** A number above one: "2", "15", "dois", "três". */
internal fun isPluralNumber(key: String): Boolean {
    if (key in NUMBER_WORDS) return true
    if (key.isEmpty() || !key.all { it in '0'..'9' }) return false
    return key.trimStart('0').let { it.length > 1 || (it.isNotEmpty() && it[0] >= '2') }
}
