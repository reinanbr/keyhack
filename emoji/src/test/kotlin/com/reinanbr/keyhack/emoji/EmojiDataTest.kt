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
}
