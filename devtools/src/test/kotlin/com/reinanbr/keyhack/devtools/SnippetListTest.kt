package com.reinanbr.keyhack.devtools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SnippetListTest {
    @Test
    fun triggersAreOneLowercaseWord() {
        assertEquals("endereco2", SnippetList.normalizeTrigger(" Endereço 2! "))
        assertEquals("", SnippetList.normalizeTrigger("!!"))
    }

    @Test
    fun findsByTriggerIgnoringCaseAndAccents() {
        val l = SnippetList()
        l.add("end", "Rua das Flores, 123")
        assertEquals("Rua das Flores, 123", l.forTrigger("END")?.text)
        assertEquals("Rua das Flores, 123", l.forTrigger("énd")?.text)
        assertNull(l.forTrigger("en"))
        assertNull(l.forTrigger(""))
    }

    @Test
    fun snippetsWithoutTriggerAreNeverMatched() {
        val l = SnippetList()
        l.add("", "text")
        assertNull(l.forTrigger("text"))
        assertEquals(1, l.all().size)
    }

    @Test
    fun rejectsBlankText() {
        val l = SnippetList()
        assertFalse(l.add("x", "  "))
        assertFalse(l.update(99, "x", "y"))
    }

    @Test
    fun updateAndRemove() {
        val l = SnippetList()
        l.add("a", "1")
        val id = l.all().single().id
        l.update(id, "b", "2")
        assertEquals("2", l.forTrigger("b")?.text)
        l.remove(id)
        assertEquals(0, l.all().size)
    }

    @Test
    fun roundTrip() {
        val l = SnippetList()
        l.add("pix", "chave \"x\"\nlinha")
        l.add("", "sem atalho")
        val copy = SnippetList()
        copy.decode(l.encode())
        assertEquals(l.all().map { it.trigger to it.text }, copy.all().map { it.trigger to it.text })
        copy.decode("garbage".toByteArray())
        assertEquals(0, copy.all().size)
    }
}
