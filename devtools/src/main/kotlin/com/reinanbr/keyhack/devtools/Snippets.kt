package com.reinanbr.keyhack.devtools

import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.array
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.string
import java.text.Normalizer
import java.util.Locale

/**
 * A saved text. Typing its [trigger] ("end", "pix") offers [text] in the
 * suggestion bar; it is also one tap away in the snippets panel.
 */
data class Snippet(val id: Long, val trigger: String, val text: String)

/** The user's snippets as plain state; persistence is the caller's job ([encode], [decode]). */
class SnippetList {
    private val items = ArrayList<Snippet>()
    private var nextId = 1L

    fun all(): List<Snippet> = items.toList()

    /** Adds a snippet; false when [text] is blank or too long, or there are too many. */
    fun add(trigger: String, text: String): Boolean {
        if (text.isBlank() || text.length > MAX_CHARS || items.size >= MAX_SNIPPETS) return false
        items += Snippet(nextId++, normalizeTrigger(trigger), text)
        return true
    }

    fun update(id: Long, trigger: String, text: String): Boolean {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || text.isBlank() || text.length > MAX_CHARS) return false
        items[i] = Snippet(id, normalizeTrigger(trigger), text)
        return true
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
    }

    /** The snippet whose trigger is [word] (any case, accents ignored). */
    fun forTrigger(word: String): Snippet? {
        val k = normalizeTrigger(word)
        return if (k.isEmpty()) null else items.firstOrNull { it.trigger == k }
    }

    fun encode(): ByteArray =
        Json.write(mapOf("v" to 1, "items" to items.map { mapOf("k" to it.trigger, "t" to it.text) }))
            .toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray) {
        items.clear()
        val root = runCatching { Json.parse(bytes.toString(Charsets.UTF_8)).jsonObject() }.getOrNull() ?: return
        for (raw in root.array("items").orEmpty().take(MAX_SNIPPETS)) {
            val o = raw.jsonObject() ?: continue
            add(o.string("k").orEmpty(), o.string("t").orEmpty())
        }
    }

    companion object {
        const val MAX_SNIPPETS = 500
        const val MAX_CHARS = 10_000
        private const val MAX_TRIGGER = 24

        /** Triggers are typed as one word: lowercase letters and digits, accents dropped. */
        fun normalizeTrigger(s: String): String {
            val folded = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            return folded.filter { it.isLetterOrDigit() && it.code < COMBINING_START }.take(MAX_TRIGGER)
        }

        private const val COMBINING_START = 0x300
    }
}
