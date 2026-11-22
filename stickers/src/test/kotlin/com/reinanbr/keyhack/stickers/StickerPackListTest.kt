package com.reinanbr.keyhack.stickers

import com.reinanbr.keyhack.stickers.StickerPackList.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickerPackListTest {
    private fun still(n: Int) = (1..n).map { "s_${1000 + it}_$it.webp" }

    private fun animated(n: Int) = (1..n).map { "a_${2000 + it}_$it.webp" }

    @Test
    fun whatsAppRules() {
        assertEquals(emptySet(), StickerPackList.problems("Memes", still(3), "😂"))
        assertEquals(setOf(Problem.TOO_FEW), StickerPackList.problems("Memes", still(2), "😂"))
        assertEquals(setOf(Problem.TOO_MANY), StickerPackList.problems("Memes", still(31), "😂"))
        assertEquals(setOf(Problem.MIXED), StickerPackList.problems("Memes", still(2) + animated(1), "😂"))
        assertEquals(setOf(Problem.NO_NAME), StickerPackList.problems("  ", still(3), "😂"))
        assertEquals(setOf(Problem.LONG_NAME), StickerPackList.problems("x".repeat(129), still(3), "😂"))
        assertEquals(setOf(Problem.BAD_EMOJI), StickerPackList.problems("Memes", still(3), ""))
        assertEquals(emptySet(), StickerPackList.problems("Memes", animated(30), "😂"))
    }

    @Test
    fun emojiAreOneToThreeWholeEmoji() {
        assertEquals(listOf("😂"), StickerPackList.emojiOf("😂"))
        assertEquals(listOf("😂", "❤️", "👍🏽"), StickerPackList.emojiOf(" 😂, ❤️ 👍🏽"))
        assertEquals(listOf("👨‍👩‍👧", "🇧🇷"), StickerPackList.emojiOf("👨‍👩‍👧🇧🇷"))
        assertNull(StickerPackList.emojiOf("😂😂😂😂"))
        assertNull(StickerPackList.emojiOf("haha"))
        assertNull(StickerPackList.emojiOf("😂 a"))
        assertNull(StickerPackList.emojiOf("©"))
        assertNull(StickerPackList.emojiOf(" , "))
    }

    @Test
    fun createUpdateRemove() {
        val list = StickerPackList()
        val p = assertNotNull(list.create("abc", " Memes ", still(3), "😂"))
        assertEquals("Memes", p.name)
        assertEquals(1, p.version)
        assertEquals("tray_abc_1.png", p.trayFile)
        assertNull(list.create("abc", "Again", still(3), "😂"), "ids are unique")
        assertNull(list.create("bad id", "Bad", still(3), "😂"), "ids are plain")
        assertNull(list.create("def", "Too few", still(2), "😂"))

        val q = assertNotNull(list.update("abc", "Memes 2", animated(4), "🎉 🥳"))
        assertEquals(2, q.version)
        assertTrue(q.animated)
        assertEquals(listOf("🎉", "🥳"), q.emoji)
        assertNull(list.update("abc", "Mixed", still(2) + animated(2), "🎉"))
        assertEquals(q, list["abc"], "a refused update changes nothing")

        assertTrue(list.remove("abc"))
        assertEquals(emptyList(), list.all())
    }

    @Test
    fun atMostTenPacks() {
        val list = StickerPackList()
        repeat(StickerPackList.MAX_PACKS) { assertNotNull(list.create("p$it", "Pack $it", still(3), "😂")) }
        assertNull(list.create("extra", "Extra", still(3), "😂"))
    }

    @Test
    fun deletedStickersLeaveTheirPacks() {
        val list = StickerPackList()
        val files = still(4)
        list.create("a", "A", files, "😂")
        list.create("b", "B", files.take(3), "😂")
        list.create("c", "C", files.drop(1), "😂")

        val changed = list.removeSticker(files[0])
        assertEquals(listOf("a", "b"), changed.map { it.id })
        assertEquals(files.drop(1), list["a"]?.stickers)
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
        list.create("abc", "Família 👪", animated(3), "👍🏽 ❤️")
        list.update("abc", "Família 👪", animated(5), "👍🏽 ❤️")
        val copy = StickerPackList().apply { decode(list.encode()) }
        assertEquals(list.all(), copy.all())
    }

    @Test
    fun decodeSkipsWhatItCannotTrust() {
        val list = StickerPackList()
        list.decode(
            """
            {"packs": [
              {"id": "ok", "name": "Ok", "stickers": ["s_1_1.webp", "../../shared_prefs/x.xml", "s_2_2.webp"],
               "emoji": ["😂"], "version": 3},
              {"id": "../x", "name": "Bad id", "stickers": ["s_1_1.webp"], "emoji": ["😂"]},
              {"id": "ok", "name": "Duplicate", "stickers": ["s_1_1.webp"], "emoji": ["😂"]},
              {"id": "noemoji", "name": "No emoji", "stickers": ["s_1_1.webp"], "emoji": []},
              "junk"
            ]}
            """.trimIndent(),
        )
        val ok = assertNotNull(list["ok"])
        assertEquals(listOf("s_1_1.webp", "s_2_2.webp"), ok.stickers)
        assertEquals(3, ok.version)
        assertEquals(listOf("ok"), list.all().map { it.id })
        list.decode("not json")
        assertEquals(emptyList(), list.all())
    }
}
