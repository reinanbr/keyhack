package com.reinanbr.keyhack.suggest

import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserHistoryTest {
    private fun read(text: String, words: Int = UserHistory.MAX_WORDS, pairs: Int = UserHistory.MAX_PAIRS) =
        UserHistory(words, pairs).apply { read(text.reader().buffered()) }

    @Test
    fun writesAndReadsBack() {
        val h = UserHistory()
        repeat(3) { h.learn("Olá", null) }
        h.learn("mundo", "olá")
        h.accept("vlw")
        val out = StringWriter().also(h::write).toString()
        val back = read(out)
        assertEquals(3, back.count("olá"))
        assertEquals(1, back.pairCount("olá", "mundo"))
        assertTrue(back.isValid("vlw"))
    }

    @Test
    fun skipsBadLinesFromAFile() {
        val long = "x".repeat(Dictionary.MAX_WORD + 1)
        val h =
            read(
                listOf(
                    "w\tok\t5",
                    "w\t$long\t5",
                    "w\tzero\t0",
                    "w\tneg\t-3",
                    "w\tnan\tabc",
                    "w\t \t4",
                    "w\tbig\t2147483647",
                    "garbage",
                    "b\tok\t$long\t2",
                    "b\tok\tbem\t2",
                ).joinToString("\n"),
            )
        assertEquals(setOf("ok", "big"), h.entries().map { it.key }.toSet())
        assertEquals(UserHistory.MAX_COUNT, h.count("big"))
        assertEquals(listOf("bem"), h.predict("ok", 5))
    }

    @Test
    fun capsWhatItKeeps() {
        val text = (1..50).joinToString("\n") { "w\tw$it\t1" } + "\n" + (1..50).joinToString("\n") { "b\ta\tw$it\t1" }
        val h = read(text, words = 10, pairs = 20)
        assertEquals(10, h.wordCount)
        assertEquals(20, h.followers("a", 100).size)
    }
}
