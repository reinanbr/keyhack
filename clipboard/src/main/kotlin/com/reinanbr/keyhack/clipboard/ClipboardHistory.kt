package com.reinanbr.keyhack.clipboard

import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.array
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.number
import com.reinanbr.keyhack.core.string

/** One copied text. Pinned items never expire and survive "clear". */
data class ClipItem(val id: Long, val text: String, val time: Long, val pinned: Boolean = false)

/**
 * The clipboard history as plain state: newest first, pinned items kept
 * forever, the rest dropped after [expiryMs] or beyond [MAX_UNPINNED].
 * Copying a text that is already there moves it to the top.
 *
 * Pure and single-threaded (the IME's main thread); persistence is the
 * caller's job through [encode] and [decode].
 */
class ClipboardHistory(var expiryMs: Long = DEFAULT_EXPIRY_MS) {
    private val items = ArrayList<ClipItem>()
    private var nextId = 1L

    /** Adds [text] copied at [now]. False when it was ignored (blank or too long). */
    fun add(text: String, now: Long): Boolean {
        if (text.isBlank() || text.length > MAX_CHARS) return false
        val old = items.indexOfFirst { it.text == text }
        val pinned = old >= 0 && items[old].pinned
        if (old >= 0) items.removeAt(old)
        items.add(0, ClipItem(nextId++, text, now, pinned))
        prune(now)
        return true
    }

    fun setPinned(id: Long, pinned: Boolean) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        if (pinned && items.count { it.pinned } >= MAX_PINNED) return
        items[i] = items[i].copy(pinned = pinned)
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
    }

    /** Clears everything except pinned items. */
    fun clearUnpinned() {
        items.removeAll { !it.pinned }
    }

    fun clearAll() {
        items.clear()
    }

    /** Pinned items first, then the rest; newest first within each. */
    fun items(now: Long): List<ClipItem> {
        prune(now)
        return items.filter { it.pinned } + items.filter { !it.pinned }
    }

    /** The newest item if it was copied within [withinMs] of [now]; for the quick-paste chip. */
    fun latest(now: Long, withinMs: Long): ClipItem? = items.firstOrNull()?.takeIf { now - it.time in 0..withinMs }

    val isEmpty: Boolean get() = items.isEmpty()

    private fun prune(now: Long) {
        // A clock set backwards (time < now - expiry is false) keeps items rather than losing them.
        items.removeAll { !it.pinned && now - it.time > expiryMs }
        var unpinned = 0
        items.removeAll { !it.pinned && ++unpinned > MAX_UNPINNED }
    }

    /** UTF-8 JSON, encrypted by the caller before it touches the disk. */
    fun encode(): ByteArray {
        val list = items.map { mapOf("t" to it.text, "at" to it.time, "pin" to it.pinned) }
        return Json.write(mapOf("v" to FORMAT, "items" to list)).toByteArray(Charsets.UTF_8)
    }

    /** Replaces the state with [bytes] from [encode]; malformed input leaves it empty. */
    fun decode(bytes: ByteArray) {
        items.clear()
        val root = runCatching { Json.parse(bytes.toString(Charsets.UTF_8)).jsonObject() }.getOrNull() ?: return
        val parsed = root.array("items").orEmpty().mapNotNull { raw ->
            val o = raw.jsonObject()
            val text = o?.string("t")
            val time = o?.number("at")?.toLong()
            if (text == null || time == null || text.isBlank() || text.length > MAX_CHARS) {
                null
            } else {
                Triple(text, time, o["pin"] == true)
            }
        }
        for ((text, time, pinned) in parsed.distinctBy { it.first }.take(MAX_UNPINNED + MAX_PINNED)) {
            items += ClipItem(nextId++, text, time, pinned)
        }
        items.sortByDescending { it.time }
    }

    companion object {
        const val MAX_UNPINNED = 50
        const val MAX_PINNED = 100

        /** Bigger copies (whole documents) are not kept. */
        const val MAX_CHARS = 50_000
        const val HOUR_MS = 60L * 60 * 1000
        const val DEFAULT_EXPIRY_MS = HOUR_MS
        private const val FORMAT = 1
    }
}
