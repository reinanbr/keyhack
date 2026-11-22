package com.reinanbr.keyhack.ime

import kotlin.test.Test
import kotlin.test.assertEquals

class WordSpansTest {
    @Test
    fun wordsWithTheirTrailingSpaces() {
        assertEquals(listOf(7, 11), WordSpans.deleteLengths("olá mundo  ", 5))
        assertEquals(listOf(5, 9), WordSpans.deleteLengths("olá mundo", 5))
    }

    @Test
    fun stopsAtMax() {
        assertEquals(listOf(1, 3), WordSpans.deleteLengths("a b c d", 2))
    }

    @Test
    fun punctuationRunIsOneStep() {
        assertEquals(listOf(2, 6), WordSpans.deleteLengths("tudo?!", 2))
    }

    @Test
    fun contractionsAndHyphensStayWhole() {
        assertEquals(listOf(5), WordSpans.deleteLengths("don't", 3))
        assertEquals(listOf(9), WordSpans.deleteLengths("bem-vindo", 3))
    }

    @Test
    fun emojiSurrogatePairIsNotSplit() {
        assertEquals(listOf(2, 5), WordSpans.deleteLengths("oi 😀", 3))
    }

    @Test
    fun emptyAndBlank() {
        assertEquals(emptyList(), WordSpans.deleteLengths("", 3))
        assertEquals(listOf(3), WordSpans.deleteLengths("   ", 3))
    }
}
