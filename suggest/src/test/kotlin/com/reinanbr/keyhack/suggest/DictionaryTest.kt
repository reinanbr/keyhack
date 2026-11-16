package com.reinanbr.keyhack.suggest

import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictionaryTest {
    private val dict =
        Dictionary.build(
            "t",
            listOf("cat" to 200, "car" to 150, "cart" to 100, "Cat" to 50, "Brasil" to 120, "dog" to 0),
        )

    @Test
    fun looksUpCaseInsensitively() {
        assertEquals("cat", dict.words[dict.lookup("cat")])
        assertEquals("Brasil", dict.words[dict.lookup("brasil")])
        assertEquals(-1, dict.lookup("ca"))
        assertEquals(-1, dict.lookup("cats"))
    }

    @Test
    fun completesBestFirst() {
        val got = ArrayList<String>()
        dict.search("ca", EditCosts(), 0f, 0f, 2) { w, _, completion -> if (completion) got += dict.words[w] }
        assertEquals(listOf("cat", "car"), got)
    }

    @Test
    fun findsFuzzyMatches() {
        val got = HashMap<String, Float>()
        dict.search("cst", EditCosts(), 1.0f, 0f, 0) { w, cost, _ -> got[dict.words[w]] = cost }
        assertEquals(1.0f, got["cat"])
        assertTrue("dog" !in got)
    }

    @Test
    fun foldsAccents() {
        assertEquals('a', EditCosts.fold('ã'))
        assertEquals('c', EditCosts.fold('ç'))
        assertEquals("nao", EditCosts.fold("não"))
    }

    @Test
    fun historyRoundTrips() {
        val h = UserHistory()
        h.learn("hello", null)
        h.learn("world", "hello")
        h.accept("keyhack")
        val out = StringWriter()
        h.write(out)
        val back = UserHistory()
        back.read(out.toString().reader().buffered())
        assertEquals(1, back.pairCount("hello", "world"))
        assertTrue(back.isValid("keyhack"))
    }

    @Test
    fun historyPrunesWhenFull() {
        val h = UserHistory(maxWords = 10, maxPairs = 10)
        repeat(20) { h.learn("w$it", null) }
        assertTrue(h.wordCount <= 10)
    }
}
