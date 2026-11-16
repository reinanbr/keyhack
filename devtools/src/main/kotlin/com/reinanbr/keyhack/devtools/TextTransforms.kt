package com.reinanbr.keyhack.devtools

import com.reinanbr.keyhack.core.Json
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID

/** A developer action on the selection (or last word), or a generator that inserts text. */
enum class DevTool(val label: String, val needsInput: Boolean = true) {
    CAMEL("camelCase"),
    PASCAL("PascalCase"),
    SNAKE("snake_case"),
    KEBAB("kebab-case"),
    CONSTANT("CONSTANT_CASE"),
    UPPER("UPPER"),
    LOWER("lower"),
    TITLE("Title Case"),
    BASE64_ENCODE("Base64 ↑"),
    BASE64_DECODE("Base64 ↓"),
    URL_ENCODE("URL ↑"),
    URL_DECODE("URL ↓"),
    JSON_PRETTY("JSON pretty"),
    JSON_MINIFY("JSON minify"),
    JSON_ESCAPE("JSON escape"),
    JSON_UNESCAPE("JSON unescape"),
    HTML_ESCAPE("HTML escape"),
    HTML_UNESCAPE("HTML unescape"),
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256"),
    UUID4("UUID v4", needsInput = false),
    UUID7("UUID v7", needsInput = false),
    UNIX_TIME("Unix time", needsInput = false),
    ISO_TIME("ISO-8601", needsInput = false),
    TIME_CONVERT("ts ⇄ date"),
    COUNT("Count"),
}

/**
 * Pure text transforms. Each returns the replacement text, or throws
 * [IllegalArgumentException] with a short reason when the input does not fit
 * (invalid Base64, bad JSON...). [COUNT] returns a description instead.
 */
object TextTransforms {
    private val random = SecureRandom()

    fun apply(tool: DevTool, input: String, now: Instant = Instant.now()): String = when (tool) {
        DevTool.CAMEL -> words(input).mapIndexed { i, w -> if (i == 0) w.lowercase() else cap(w) }.joinToString("")
        DevTool.PASCAL -> words(input).joinToString("") { cap(it) }
        DevTool.SNAKE -> words(input).joinToString("_") { it.lowercase() }
        DevTool.KEBAB -> words(input).joinToString("-") { it.lowercase() }
        DevTool.CONSTANT -> words(input).joinToString("_") { it.uppercase() }
        DevTool.UPPER -> input.uppercase()
        DevTool.LOWER -> input.lowercase()
        DevTool.TITLE -> input.split(' ').joinToString(" ") { cap(it) }
        DevTool.BASE64_ENCODE -> Base64.getEncoder().encodeToString(input.toByteArray())
        DevTool.BASE64_DECODE -> base64Decode(input)
        DevTool.URL_ENCODE -> URLEncoder.encode(input, "UTF-8")
        DevTool.URL_DECODE -> URLDecoder.decode(input, "UTF-8")
        DevTool.JSON_PRETTY -> pretty(Json.parse(input), 0)
        DevTool.JSON_MINIFY -> Json.write(Json.parse(input))
        DevTool.JSON_ESCAPE -> StringBuilder().also { Json.quote(it, input) }.let { it.substring(1, it.length - 1) }
        DevTool.JSON_UNESCAPE -> Json.parse("\"" + input + "\"") as String
        DevTool.HTML_ESCAPE -> htmlEscape(input)
        DevTool.HTML_UNESCAPE -> htmlUnescape(input)
        DevTool.MD5 -> hash("MD5", input)
        DevTool.SHA1 -> hash("SHA-1", input)
        DevTool.SHA256 -> hash("SHA-256", input)
        DevTool.UUID4 -> UUID.randomUUID().toString()
        DevTool.UUID7 -> uuid7(now.toEpochMilli())
        DevTool.UNIX_TIME -> now.epochSecond.toString()
        DevTool.ISO_TIME -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atOffset(ZoneOffset.UTC).withNano(0))
        DevTool.TIME_CONVERT -> convertTime(input.trim())
        DevTool.COUNT -> count(input)
    }

    /** Splits identifiers and phrases into words: "myHTTPServer_v2 x" -> my, HTTP, Server, v2, x. */
    fun words(input: String): List<String> = input
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }

    private fun cap(w: String): String = w.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun base64Decode(input: String): String {
        val clean = input.trim().replace("\n", "")
        val bytes =
            runCatching { Base64.getDecoder().decode(clean) }
                .recoverCatching { Base64.getUrlDecoder().decode(clean) }
                .getOrElse { throw IllegalArgumentException("not Base64") }
        val text = String(bytes, Charsets.UTF_8)
        require(!text.contains('�')) { "Base64 is not UTF-8 text" }
        return text
    }

    private fun hash(algorithm: String, input: String): String =
        MessageDigest.getInstance(algorithm).digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    /** RFC 9562 UUIDv7: 48-bit Unix ms timestamp, version 7, variant 10, random rest. */
    @Suppress("MagicNumber") // Bit layout from RFC 9562, section 5.7.
    fun uuid7(epochMillis: Long): String {
        val bytes = ByteArray(UUID_BYTES)
        random.nextBytes(bytes)
        for (i in 0 until TS_BYTES) bytes[i] = (epochMillis ushr (8 * (TS_BYTES - 1 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val bb = ByteBuffer.wrap(bytes)
        return UUID(bb.long, bb.long).toString()
    }

    private fun convertTime(input: String): String {
        val n = input.toLongOrNull()
        if (n != null) {
            val instant = if (input.length > SECONDS_DIGITS) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC))
        }
        val parsed =
            runCatching { Instant.parse(input) }
                .recoverCatching { java.time.OffsetDateTime.parse(input).toInstant() }
                .getOrElse { throw IllegalArgumentException("not a timestamp or ISO-8601 date") }
        return parsed.epochSecond.toString()
    }

    private fun count(input: String): String {
        val chars = input.codePointCount(0, input.length)
        val words = input.split(Regex("\\s+")).count { it.isNotEmpty() }
        val lines = if (input.isEmpty()) 0 else input.count { it == '\n' } + 1
        return "$chars chars · $words words · $lines lines"
    }

    private fun htmlEscape(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private fun htmlUnescape(s: String): String = Regex("&(#x?[0-9a-fA-F]+|[a-z]+);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith(
                "#x",
                ignoreCase = true,
            ) -> e.substring(2).toIntOrNull(HEX)?.let { String(Character.toChars(it)) }

            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }

            else -> NAMED[e]
        } ?: m.value
    }

    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

    private fun pretty(v: Any?, indent: Int): String {
        val pad = "  ".repeat(indent + 1)
        val end = "  ".repeat(indent)
        return when (v) {
            is Map<*, *> ->
                if (v.isEmpty()) {
                    "{}"
                } else {
                    v.entries.joinToString(
                        ",\n",
                        "{\n",
                        "\n$end}",
                    ) { (k, x) -> pad + Json.write(k.toString()) + ": " + pretty(x, indent + 1) }
                }

            is List<*> -> if (v.isEmpty()) {
                "[]"
            } else {
                v.joinToString(
                    ",\n",
                    "[\n",
                    "\n$end]",
                ) { pad + pretty(it, indent + 1) }
            }

            else -> Json.write(v)
        }
    }

    private const val UUID_BYTES = 16
    private const val HEX = 16
    private const val TS_BYTES = 6
    private const val SECONDS_DIGITS = 11
}
