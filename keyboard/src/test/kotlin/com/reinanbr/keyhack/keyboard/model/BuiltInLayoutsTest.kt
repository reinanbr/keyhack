package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInLayoutsTest {
    private val qwerty = BuiltInLayouts.QWERTY

    @Test
    fun qwertyHasAllLettersOnce() {
        val letters =
            qwerty.rows
                .flatMap { it.keys }
                .mapNotNull { (it.action as? KeyAction.Character)?.text }
                .filter { it.single().isLetter() }
        assertEquals(('a'..'z').map { it.toString() }.sorted(), letters.sorted())
    }

    @Test
    fun qwertyHasEssentialFunctionKeys() {
        val actions = qwerty.rows.flatMap { it.keys }.map { it.action }
        listOf(KeyAction.Shift, KeyAction.Delete, KeyAction.Enter, KeyAction.Space).forEach {
            assertTrue(it in actions, "missing $it")
        }
    }

    @Test
    fun rowsAreAtMostTenUnitsWide() {
        assertEquals(10f, qwerty.unitWidth)
        assertTrue(qwerty.rows.all { it.width <= 10f })
    }

    @Test
    fun characterKeyLabelsMatchTheirOutput() {
        // Anti-spoofing: the visible label is derived from the output.
        for (key in qwerty.rows.flatMap { it.keys }.filter { it.action is KeyAction.Character }) {
            assertEquals(key.outputText(shifted = false), key.displayLabel(shifted = false))
            assertEquals(key.outputText(shifted = true), key.displayLabel(shifted = true))
        }
    }

    @Test
    fun shiftedLettersAreUppercase() {
        val q = qwerty.rows[0].keys[0]
        assertEquals("q", q.outputText(shifted = false))
        assertEquals("Q", q.outputText(shifted = true))
        assertEquals(null, qwerty.rows[2].keys[0].outputText(shifted = true))
    }
}
