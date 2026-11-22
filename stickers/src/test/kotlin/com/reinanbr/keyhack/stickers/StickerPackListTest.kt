package com.reinanbr.keyhack.stickers

import com.reinanbr.keyhack.stickers.StickerPackList.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickerPackListTest {
    private fun still(n: Int) = (1..n).map { "s_${1000 + it}_$it.webp" }

    private fun animated(n: Int) = (1..n).map { "a_${2000 + it}_$it.webp" }

    /** Every sticker with [emoji]. */
    private fun all(stickers: List<String>, vararg emoji: String) = stickers.associateWith { emoji.toList() }

    @Test
    fun whatsAppRules() {
        fun problems(name: String, stickers: List<String>) =
            StickerPackList.problems(name, stickers, all(stickers, "😂"))
        assertEquals(emptySet(), problems("Memes", still(3)))
        assertEquals(setOf(Problem.TOO_FEW), problems("Memes", still(2)))
        assertEquals(setOf(Problem.TOO_MANY), problems("Memes", still(31)))
        assertEquals(setOf(Problem.MIXED), problems("Memes", still(2) + animated(1)))
        assertEquals(setOf(Problem.NO_NAME), problems("  ", still(3)))
        assertEquals(setOf(Problem.LONG_NAME), problems("x".repeat(129), still(3)))
        assertEquals(emptySet(), problems("Memes", animated(30)))
    }

    @Test
    fun everyStickerNeedsItsOwnEmoji() {
        val files = still(3)
        val emoji = all(files, "😂").toMutableMap()
        emoji[files[1]] = listOf("❤️", "👍🏽", "🎉")
        assertEquals(emptySet(), StickerPackList.problems("Memes", files, emoji))
        for (bad in listOf(emptyList(), listOf("😂", "😂"), listOf("a"), listOf("😂😂"), listOf("1", "2", "3", "4"))) {
            emoji[files[2]] = bad
            assertEquals(setOf(Problem.BAD_EMOJI), StickerPackList.problems("Memes", files, emoji), bad.toString())
        }
        emoji.remove(files[2])
        assertEquals(setOf(Problem.BAD_EMOJI), StickerPackList.problems("Memes", files, emoji), "missing")
    }

    @Test
    fun splitsTypedEmoji() {
        assertEquals(listOf("😂"), StickerPackList.splitEmoji("😂"))
        assertEquals(listOf("😂", "❤️", "👍🏽"), StickerPackList.splitEmoji(" 😂, ❤️ 👍🏽"))
        assertEquals(listOf("👨‍👩‍👧", "🇧🇷"), StickerPackList.splitEmoji("👨‍👩‍👧🇧🇷"))
        assertNull(StickerPackList.splitEmoji("😂😂😂😂"))
        assertNull(StickerPackList.splitEmoji("haha"))
        assertNull(StickerPackList.splitEmoji("😂 a"))
        assertNull(StickerPackList.splitEmoji("©"))
        assertNull(StickerPackList.splitEmoji(" , "))
        assertTrue(StickerPackList.isEmojiList(listOf("👨‍👩‍👧", "🇧🇷")))
        assertFalse(StickerPackList.isEmojiList(listOf("👨‍👩‍👧🇧🇷")), "two emoji in one entry")
    }

    @Test
    fun createUpdateRemove() {
        val list = StickerPackList()
        val extra = "other.webp" to listOf("🎉")
        val p = assertNotNull(list.create("abc", " Memes ", still(3), all(still(3), "😂") + extra))
        assertEquals("Memes", p.name)
        assertEquals(1, p.version)
        assertEquals("tray_abc_1.png", p.trayFile)
        assertEquals(still(3).toSet(), p.emoji.keys, "emoji only for the pack's stickers")
        assertNull(list.create("abc", "Again", still(3), all(still(3), "😂")), "ids are unique")
        assertNull(list.create("bad id", "Bad", still(3), all(still(3), "😂")), "ids are plain")
        assertNull(list.create("def", "Too few", still(2), all(still(2), "😂")))

        val q = assertNotNull(list.update("abc", "Memes 2", animated(4), all(animated(4), "🎉", "🥳")))
        assertEquals(2, q.version)
        assertTrue(q.animated)
        assertEquals(listOf("🎉", "🥳"), q.emojiOf(animated(4)[0]))
        assertNull(list.update("abc", "Mixed", still(2) + animated(2), all(still(2) + animated(2), "🎉")))
        assertEquals(q, list["abc"], "a refused update changes nothing")

        assertTrue(list.remove("abc"))
        assertEquals(emptyList(), list.all())
    }

    @Test
    fun allEmojiPutsTheMostUsedFirst() {
        val files = still(3)
        val emoji = mapOf(files[0] to listOf("😂"), files[1] to listOf("❤️", "😂"), files[2] to listOf("❤️", "😂"))
        val p = StickerPack("a", "A", files, emoji, 1)
        assertEquals(listOf("😂", "❤️"), p.allEmoji())
    }

    @Test
    fun atMostTenPacks() {
        val list = StickerPackList()
        repeat(
            StickerPackList.MAX_PACKS,
        ) { assertNotNull(list.create("p$it", "Pack $it", still(3), all(still(3), "😂"))) }
        assertNull(list.create("extra", "Extra", still(3), all(still(3), "😂")))
    }

    @Test
    fun deletedStickersLeaveTheirPacks() {
        val list = StickerPackList()
        val files = still(4)
        list.create("a", "A", files, all(files, "😂"))
        list.create("b", "B", files.take(3), all(files, "😂"))
        list.create("c", "C", files.drop(1), all(files, "😂"))

        val changed = list.removeSticker(files[0])
        assertEquals(listOf("a", "b"), changed.map { it.id })
        assertEquals(files.drop(1), list["a"]?.stickers)
        assertFalse(files[0] in assertNotNull(list["a"]).emoji, "its emoji go too")
        assertEquals(2, list["a"]?.version, "WhatsApp must fetch it again")
        assertEquals(1, list["c"]?.version, "untouched")

        // Packs left empty go away; ones below three stay, but are no longer valid.
        list.removeSticker(files[1])
        list.removeSticker(files[2])
        assertNull(list["b"])
        assertEquals(listOf(files[3]), list["a"]?.stickers)
    }

    @Test
    fun survivesEncodeAndDecode() {
        val list = StickerPackList()
        val files = animated(5)
        list.create("abc", "Família 👪", files.take(3), all(files.take(3), "👍🏽", "❤️"))
        list.update("abc", "Família 👪", files, all(files, "👍🏽") + (files[4] to listOf("👨‍👩‍👧", "🇧🇷")))
        val copy = StickerPackList().apply { decode(list.encode()) }
        assertEquals(list.all(), copy.all())
    }

    @Test
    fun readsPacksWithOneEmojiListForAll() {
        val list = StickerPackList()
        list.decode(
            """{"packs": [{"id": "old", "name": "Old", "stickers": ["s_1_1.webp", "s_2_2.webp"], "emoji": ["😂"]}]}""",
        )
        val p = assertNotNull(list["old"])
        assertEquals(listOf("😂"), p.emojiOf("s_2_2.webp"))
    }

    @Test
    fun decodeSkipsWhatItCannotTrust() {
        val list = StickerPackList()
        list.decode(
            """
            {"packs": [
              {"id": "ok", "name": "Ok", "version": 3, "stickers": [
                {"file": "s_1_1.webp", "emoji": ["😂"]},
                {"file": "../../shared_prefs/x.xml", "emoji": ["😂"]},
                {"file": "s_2_2.webp", "emoji": ["not emoji"]},
                {"file": "s_1_1.webp", "emoji": ["❤️"]}
              ]},
              {"id": "../x", "name": "Bad id", "stickers": [{"file": "s_1_1.webp", "emoji": ["😂"]}]},
              {"id": "ok", "name": "Duplicate", "stickers": [{"file": "s_1_1.webp", "emoji": ["😂"]}]},
              {"id": "empty", "name": "Empty", "stickers": []},
              "junk"
            ]}
            """.trimIndent(),
        )
        val ok = assertNotNull(list["ok"])
        assertEquals(listOf("s_1_1.webp", "s_2_2.webp"), ok.stickers)
        assertEquals(listOf("😂"), ok.emojiOf("s_1_1.webp"), "the first entry for a file wins")
        assertEquals(listOf(StickerPackList.DEFAULT_EMOJI), ok.emojiOf("s_2_2.webp"), "bad emoji fall back")
        assertEquals(3, ok.version)
        assertEquals(listOf("ok"), list.all().map { it.id })
        list.decode("not json")
        assertEquals(emptyList(), list.all())
    }
}
