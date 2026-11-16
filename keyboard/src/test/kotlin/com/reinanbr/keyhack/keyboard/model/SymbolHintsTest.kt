package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SymbolHintsTest {
    private val common = TestLayouts.common

    private fun KeyboardLayout.key(text: String): Key =
        rows.flatMap { it.keys }.first { (it.action as? KeyAction.Character)?.text == text }

    private fun qwerty(numberRow: Boolean) =
        TestLayouts.load("qwerty").withSymbolHints(common.symbolHints, common.popups, replaceDigits = numberRow)

    @Test
    fun codeSymbolsAreOnLetterKeysWithNumberRow() {
        val l = qwerty(numberRow = true)
        val expected = mapOf(
            "q" to "`", "e" to "\\", "t" to "{", "y" to "}", "u" to "[", "i" to "]",
            "f" to "_", "h" to "-", "x" to "\"", "c" to "'", "a" to "@",
        )
        for ((letter, symbol) in expected) {
            val key = l.key(letter)
            assertEquals(symbol, key.hint, letter)
            assertEquals(symbol, key.popup.first(), "$letter long-press starts with its symbol")
        }
        // Accents survive after the symbol; the digit is gone (the number row has it).
        assertTrue("á" in l.key("a").popup)
        assertTrue("8" !in l.key("i").popup)
    }

    @Test
    fun withoutNumberRowTopRowKeepsDigitsAndOffersSymbolSecond() {
        val l = qwerty(numberRow = false)
        assertEquals("1", l.key("q").hint)
        assertEquals(listOf("1", "`"), l.key("q").popup.take(2))
        assertEquals("@", l.key("a").hint)
    }

    @Test
    fun periodKeepsItsSlash() {
        val l = qwerty(numberRow = true)
        assertEquals("/", l.key(".").hint)
        assertEquals("/", l.key(".").popup.first())
    }

    @Test
    fun functionKeysAreSkipped() {
        val l = qwerty(numberRow = true)
        val shift = l.rows[2].keys.first()
        assertNull(shift.hint)
        // The 7 letters z..m take the 7 symbols of row 3, in order.
        assertEquals(listOf("*", "\"", "'", ":", ";", "!", "?"), "zxcvbnm".map { l.key(it.toString()).hint })
    }

    @Test
    fun abnt2CedillaGetsSlash() {
        val l = TestLayouts.load("abnt2").withSymbolHints(common.symbolHints, common.popups, true)
        assertEquals("/", l.key("ç").hint)
    }

    @Test
    fun symbolsLayerHasEveryCodeSymbol() {
        val texts = TestLayouts.load("symbols").rows.flatMap { it.keys }
            .mapNotNull { (it.action as? KeyAction.Character)?.text }
        for (s in listOf("/", "\\", "{", "}", "[", "]", "_", "\"", "`", "'", "|", "~", "<", ">")) {
            assertTrue(s in texts, s)
        }
    }
}
