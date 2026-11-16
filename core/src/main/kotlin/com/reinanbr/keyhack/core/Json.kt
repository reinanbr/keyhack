package com.reinanbr.keyhack.core

/**
 * Minimal, strict JSON reader and writer. KeyHack parses small, mostly local
 * documents (layouts, GIF API responses); a dependency-free parser keeps them
 * testable on the JVM and avoids pulling a library into the IME process.
 *
 * Values map to: Map<String, Any?>, List<Any?>, String, Double, Boolean, null.
 */
object Json {
    private const val HEX = 16
    private const val HEX_DIGITS = 4

    /** Doubles below this print as integers without losing precision. */
    private const val MAX_EXACT_INTEGRAL = 1e15

    class ParseException(message: String) : IllegalArgumentException(message)

    /** Parses [text]. Nesting deeper than [maxDepth] is rejected (untrusted input). */
    fun parse(text: String, maxDepth: Int = 64): Any? {
        val p = Parser(text, maxDepth)
        p.skipWs()
        val value = p.value(0)
        p.skipWs()
        if (p.pos != text.length) throw ParseException("trailing data at ${p.pos}")
        return value
    }

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    private fun writeTo(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")

            is String -> quote(sb, value)

            is Boolean -> sb.append(value)

            is Int, is Long -> sb.append(value)

            is Number -> {
                val d = value.toDouble()
                val integral = d == Math.rint(d) && kotlin.math.abs(d) < MAX_EXACT_INTEGRAL
                if (integral) sb.append(d.toLong()) else sb.append(d)
            }

            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    writeTo(sb, v)
                }
                sb.append('}')
            }

            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeTo(sb, v)
                }
                sb.append(']')
            }

            else -> quote(sb, value.toString())
        }
    }

    fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String, val maxDepth: Int) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        private fun fail(msg: String): Nothing = throw ParseException("$msg at $pos")

        fun value(depth: Int): Any? {
            if (depth > maxDepth) fail("nesting too deep")
            if (pos >= s.length) fail("unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("unexpected '$c'")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) fail("bad literal")
            pos += word.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            pos++
            val m = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return m
            }
            while (true) {
                skipWs()
                if (peek() != '"') fail("expected key")
                val k = str()
                skipWs()
                if (peek() != ':') fail("expected ':'")
                pos++
                skipWs()
                m[k] = value(depth + 1)
                skipWs()
                when (peek()) {
                    ',' -> pos++

                    '}' -> {
                        pos++
                        return m
                    }

                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            pos++
            val l = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return l
            }
            while (true) {
                skipWs()
                l += value(depth + 1)
                skipWs()
                when (peek()) {
                    ',' -> pos++

                    ']' -> {
                        pos++
                        return l
                    }

                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun peek(): Char = if (pos < s.length) s[pos] else fail("unexpected end")

        private fun str(): String {
            pos++
            val sb = StringBuilder()
            while (true) {
                val c = peek()
                pos++
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> escape(sb)
                    c < ' ' -> fail("control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun escape(sb: StringBuilder) {
            when (val e = peek()) {
                '"', '\\', '/' -> sb.append(e)

                'n' -> sb.append('\n')

                'r' -> sb.append('\r')

                't' -> sb.append('\t')

                'b' -> sb.append('\b')

                'f' -> sb.append('\u000c')

                'u' -> {
                    if (pos + 1 + HEX_DIGITS > s.length) fail("bad unicode escape")
                    val hex = s.substring(pos + 1, pos + 1 + HEX_DIGITS)
                    sb.append(hex.toIntOrNull(HEX)?.toChar() ?: fail("bad unicode escape"))
                    pos += HEX_DIGITS
                }

                else -> fail("bad escape")
            }
            pos++
        }

        private fun num(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: fail("bad number")
        }
    }
}

/** Typed accessors for parsed JSON. */
@Suppress("UNCHECKED_CAST")
fun Any?.jsonObject(): Map<String, Any?>? = this as? Map<String, Any?>

fun Any?.jsonArray(): List<Any?>? = this as? List<Any?>

fun Map<String, Any?>.string(key: String): String? = this[key] as? String

fun Map<String, Any?>.number(key: String): Double? = (this[key] as? Number)?.toDouble()

fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key].jsonObject()

fun Map<String, Any?>.array(key: String): List<Any?>? = this[key].jsonArray()
