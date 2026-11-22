package com.reinanbr.keyhack.stickers

import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.array
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.number
import com.reinanbr.keyhack.core.string
import java.text.BreakIterator

/**
 * A named group of library stickers, the unit WhatsApp adds to its sticker
 * tray. [stickers] are file names in the library, in order; [emoji] holds
 * each one's emoji (one to three), which WhatsApp files the sticker under.
 * [version] goes up on each change so WhatsApp fetches the pack again.
 */
data class StickerPack(
    val id: String,
    val name: String,
    val stickers: List<String>,
    val emoji: Map<String, List<String>>,
    val version: Int,
) {
    val animated: Boolean get() = stickers.firstOrNull()?.let(StickerPackList::isAnimated) == true

    /** Tray icon file name; it changes with [version], so WhatsApp never keeps a stale one. */
    val trayFile: String get() = "tray_${id}_$version.png"

    fun emojiOf(file: String): List<String> = emoji[file].orEmpty()

    /** The pack's emoji, most used first, without repeats. */
    fun allEmoji(): List<String> =
        stickers.flatMap(::emojiOf).groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}

/**
 * The pack list and WhatsApp's rules for a pack, Android-free so they can be
 * unit tested. Not thread-safe: [StickerPackStore] guards it.
 */
class StickerPackList {
    private val packs = ArrayList<StickerPack>()

    fun all(): List<StickerPack> = packs.toList()

    operator fun get(id: String): StickerPack? = packs.firstOrNull { it.id == id }

    /** Adds a pack under the new [id] (unique, never reused), or returns null when it breaks a rule. */
    fun create(id: String, name: String, stickers: List<String>, emoji: Map<String, List<String>>): StickerPack? {
        if (packs.size >= MAX_PACKS || this[id] != null || !ID.matches(id)) return null
        if (problems(name, stickers, emoji).isNotEmpty()) return null
        return StickerPack(id, name.trim(), stickers.toList(), emoji.filterKeys { it in stickers }, 1)
            .also { packs += it }
    }

    /** Replaces the pack's contents; null when [id] is unknown or the new contents break a rule. */
    fun update(id: String, name: String, stickers: List<String>, emoji: Map<String, List<String>>): StickerPack? {
        val i = packs.indexOfFirst { it.id == id }
        if (i < 0 || problems(name, stickers, emoji).isNotEmpty()) return null
        val old = packs[i]
        val new =
            old.copy(
                name = name.trim(),
                stickers = stickers.toList(),
                emoji = emoji.filterKeys { it in stickers },
                version = old.version + 1,
            )
        packs[i] = new
        return new
    }

    fun remove(id: String): Boolean = packs.removeAll { it.id == id }

    /**
     * Drops [file] from every pack (it was deleted from the library). Packs
     * left empty go away. Returns the packs that changed and survived.
     */
    fun removeSticker(file: String): List<StickerPack> {
        val changed = ArrayList<StickerPack>()
        val it = packs.listIterator()
        while (it.hasNext()) {
            val p = it.next()
            if (file !in p.stickers) continue
            val left = p.stickers - file
            if (left.isEmpty()) {
                it.remove()
            } else {
                val q = p.copy(stickers = left, emoji = p.emoji - file, version = p.version + 1)
                it.set(q)
                changed += q
            }
        }
        return changed
    }

    fun encode(): String = Json.write(
        mapOf(
            "packs" to
                packs.map { p ->
                    mapOf(
                        "id" to p.id,
                        "name" to p.name,
                        "stickers" to p.stickers.map { mapOf("file" to it, "emoji" to p.emojiOf(it)) },
                        "version" to p.version,
                    )
                },
        ),
    )

    /** Replaces the list with [text]; entries that do not parse are skipped. */
    fun decode(text: String) {
        packs.clear()
        val root = runCatching { Json.parse(text).jsonObject() }.getOrNull() ?: return
        for (item in root.array("packs").orEmpty()) {
            val o = item.jsonObject() ?: continue
            val id = o.string("id")?.takeIf { ID.matches(it) && this[it] == null } ?: continue
            // Before per-sticker emoji, the pack had one "emoji" list for all its stickers.
            val packEmoji = o.array("emoji").orEmpty().filterIsInstance<String>().takeIf(::isEmojiList)
            val emoji = LinkedHashMap<String, List<String>>()
            for (s in o.array("stickers").orEmpty()) {
                val so = s.jsonObject()
                val file = (so?.string("file") ?: s as? String)?.takeIf { FILE.matches(it) && it !in emoji } ?: continue
                val own = so?.array("emoji")?.filterIsInstance<String>()?.takeIf(::isEmojiList)
                emoji[file] = own ?: packEmoji ?: listOf(DEFAULT_EMOJI)
            }
            if (emoji.isEmpty() || packs.size >= MAX_PACKS) continue
            val version = o.number("version")?.toInt()?.coerceAtLeast(1) ?: 1
            packs += StickerPack(id, o.string("name").orEmpty(), emoji.keys.toList(), emoji, version)
        }
    }

    enum class Problem { NO_NAME, LONG_NAME, TOO_FEW, TOO_MANY, MIXED, BAD_EMOJI }

    companion object {
        /** WhatsApp takes 3 to 30 stickers per pack and up to 10 packs per app. */
        const val MIN_STICKERS = 3
        const val MAX_STICKERS = 30
        const val MAX_PACKS = 10
        const val MAX_NAME = 128
        const val MAX_EMOJI = 3
        const val DEFAULT_EMOJI = "🙂"

        /** Library file name prefixes (StickerStore): "a_" is animated, "s_" still. */
        const val ANIMATED_PREFIX = "a_"
        const val STILL_PREFIX = "s_"

        /** Within WhatsApp's identifier rule ([\w-.,'\s]+, no ".."), and safe in file names. */
        private val ID = Regex("[a-z0-9]{1,32}")
        private val FILE = Regex("[as]_[0-9]+_[0-9]+\\.webp")

        fun isAnimated(file: String): Boolean = file.startsWith(ANIMATED_PREFIX)

        /**
         * What keeps [stickers] named [name], with each sticker's [emoji],
         * from being a WhatsApp pack; empty when it can be one.
         */
        fun problems(name: String, stickers: List<String>, emoji: Map<String, List<String>>): Set<Problem> {
            val out = HashSet<Problem>()
            val n = name.trim()
            if (n.isEmpty()) out += Problem.NO_NAME
            if (n.length > MAX_NAME) out += Problem.LONG_NAME
            if (stickers.size < MIN_STICKERS) out += Problem.TOO_FEW
            if (stickers.size > MAX_STICKERS) out += Problem.TOO_MANY
            if (stickers.map(::isAnimated).distinct().size > 1) out += Problem.MIXED
            if (stickers.any { !isEmojiList(emoji[it].orEmpty()) }) out += Problem.BAD_EMOJI
            return out
        }

        /**
         * The one to three emoji in [text] ("😂 ❤️", "👍🏽,🎉"), or null when it
         * holds anything else, none, or more than three.
         */
        fun splitEmoji(text: String): List<String>? {
            val out = ArrayList<String>()
            val it = BreakIterator.getCharacterInstance()
            it.setText(text)
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                val g = text.substring(start, end)
                start = end
                end = it.next()
                if (g.isBlank() || g == ",") continue
                if (!isEmoji(g) || out.size == MAX_EMOJI) return null
                out += g
            }
            return out.ifEmpty { null }
        }

        /** One to three emoji, each a single emoji (as [splitEmoji] splits them), none twice. */
        fun isEmojiList(list: List<String>): Boolean =
            list.size in 1..MAX_EMOJI && list.distinct().size == list.size && list.all { splitEmoji(it) == listOf(it) }

        private fun isEmoji(g: String): Boolean {
            val cp = g.codePointAt(0)
            return Character.getType(cp) == Character.OTHER_SYMBOL.toInt() && cp >= MIN_EMOJI_CODE_POINT
        }

        /** Below this, OTHER_SYMBOL is "©", "°" and the like. */
        private const val MIN_EMOJI_CODE_POINT = 0x2000
    }
}
