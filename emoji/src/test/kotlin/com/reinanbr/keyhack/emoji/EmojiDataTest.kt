package com.reinanbr.keyhack.emoji

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiDataTest {
    private val data = File("src/main/assets/emoji.tsv").bufferedReader().use(EmojiData::read)

    @Test
    fun loadsAllGroups() {
        assertTrue(data.all.size > 1800)
        assertEquals(
            listOf("smileys", "people", "nature", "food", "travel", "activities", "objects", "symbols", "flags"),
            data.groups,
        )
    }

    @Test
    fun searchesInEnglishAndPortuguese() {
        assertTrue("🐱" in data.search("cat").map { it.value })
        assertTrue("🐱" in data.search("gato").map { it.value })
        assertTrue("👍" in data.search("joia").map { it.value })
        assertEquals("❤️", data.search("red heart").first().value)
        assertTrue(data.search("coracao").isNotEmpty())
        assertTrue(data.search("   ").isEmpty())
    }

    @Test
    fun appliesSkinTones() {
        val thumbs = data.find("👍")!!
        assertTrue(thumbs.toneable)
        assertEquals("👍🏽", thumbs.withTone(3))
        assertEquals("👍", thumbs.withTone(0))
        assertEquals("🐱", data.find("🐱")!!.withTone(3))
        val raised = data.find("☝️")!!
        assertEquals("☝🏻", raised.withTone(1))
    }

    @Test
    fun offersEmojiForTypedWords() {
        fun first(w: String) = data.forWord(w).firstOrNull()?.value
        assertTrue("❤️" in data.forWord("amor").map { it.value })
        assertEquals("😂", first("kkk"))
        assertEquals("😂", first("kkkk"))
        assertEquals("🙏", first("obrigado"))
        assertTrue("🍕" in data.forWord("pizza").map { it.value })
        assertTrue(data.forWord("a").isEmpty(), "too short")
        assertTrue(data.forWord("xyzzy").isEmpty())
    }

    @Test
    fun chatWordsComeFirstWithThreeEmoji() {
        fun all(w: String) = data.forWord(w).map { it.value }
        assertEquals(listOf("❤️", "😍", "🥰"), all("amor"))
        assertEquals(listOf("😂", "🤣", "😆"), all("kkkkkkk"))
        assertEquals(listOf("😂", "🤣", "😆"), all("KKKK"))
        assertEquals(listOf("😂", "🤣", "😆"), all("hahahah"))
        assertEquals("😅", all("rsrsrs").first())
        assertEquals("🎉", all("Parabéns").first())
        assertEquals("😊", all("bom").first(), "not a superhero")
        assertEquals("😋", all("fome").first(), "not a pineapple")
        assertTrue("🍕" in data.forWord("pizza", 6).map { it.value }, "Unicode keywords still count")
        assertTrue(all("aaaa").isEmpty(), "a scream is not a laugh")
        assertTrue(all("ah").isEmpty())
    }

    @Test
    fun functionWordsGetNoEmoji() {
        for (w in listOf("de", "isso", "no", "na", "para", "mais", "voce", "você", "this", "that", "aqui")) {
            assertTrue(data.forWord(w).isEmpty(), w)
        }
    }

    @Test
    fun everyChatEmojiIsInTheTable() {
        for ((word, emoji) in ChatEmoji.all) {
            assertEquals(word, EmojiData.normalize(word), "keys are normalized")
            for (e in emoji.split(' ')) {
                val hex = e.codePoints().toArray().joinToString(" ") { Integer.toHexString(it) }
                assertTrue(data.find(e) != null, "$word: $e ($hex)")
            }
        }
    }
}
