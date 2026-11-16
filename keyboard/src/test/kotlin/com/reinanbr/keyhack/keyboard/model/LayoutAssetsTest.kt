package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutAssetsTest {
    private val layouts = TestLayouts.all()
    private fun keys(id: String) = layouts.getValue(id).rows.flatMap { it.keys }
    private fun texts(id: String) = keys(id).mapNotNull { (it.action as? KeyAction.Character)?.text }

    @Test
    fun everyShippedLayoutParses() {
        val expected =
            setOf(
                "qwerty", "abnt2", "azerty", "dvorak", "colemak", "pc",
                "fn", "symbols", "symbols2", "numpad", "phone",
            )
        assertEquals(expected, layouts.keys)
    }

    @Test
    fun letterLayoutsHaveAllLettersOnce() {
        for (id in listOf("qwerty", "abnt2", "azerty", "dvorak", "colemak", "pc")) {
            val letters = texts(id).filter { it.single() in 'a'..'z' }
            assertEquals(('a'..'z').map { it.toString() }, letters.sorted(), id)
        }
        assertTrue("ç" in texts("abnt2"))
    }

    @Test
    fun layerSwitchesPointToExistingLayers() {
        for ((id, layout) in layouts) {
            for (key in layout.rows.flatMap { it.keys }) {
                val target = (key.action as? KeyAction.Layer)?.target ?: continue
                assertTrue(target == "alpha" || target in layouts, "$id -> $target")
            }
        }
    }

    @Test
    fun pcLayoutHasHackerKeys() {
        val pc = keys("pc").map { it.action } + keys("fn").map { it.action }
        for (m in Modifier.entries) assertTrue(KeyAction.Mod(m) in pc, "missing $m")
        val codes = pc.filterIsInstance<KeyAction.Code>().map { it.name }.toSet()
        val wanted = listOf("esc", "tab", "left", "right", "up", "down", "home", "end", "pgup", "pgdn", "ins", "fdel")
        assertTrue(codes.containsAll(wanted + (1..12).map { "f$it" }), "missing ${wanted - codes}")
        val one = keys("pc").first { (it.action as? KeyAction.Character)?.text == "1" }
        assertEquals("!", one.outputText(shifted = true))
    }

    @Test
    fun characterKeyLabelsMatchTheirOutput() {
        // Anti-spoofing: the visible label is derived from the output.
        for (key in layouts.values.flatMap { l -> l.rows.flatMap { it.keys } }) {
            if (key.action !is KeyAction.Character) continue
            assertEquals(key.outputText(shifted = false), key.displayLabel(shifted = false))
            assertEquals(key.outputText(shifted = true), key.displayLabel(shifted = true))
        }
    }

    @Test
    fun rowsFitTheirLayout() {
        for ((id, layout) in layouts) assertTrue(layout.unitWidth <= 14f, "$id is ${layout.unitWidth} wide")
    }

    @Test
    fun accentsAreOnLongPress() {
        val a = keys("qwerty").first { it.label == "a" }
        assertTrue("ã" in a.popup && "á" in a.popup)
        assertEquals(listOf("Ã"), a.popupFor(shifted = true).filter { it == "Ã" })
        val q = keys("qwerty").first { it.label == "q" }
        assertEquals("1", q.hint)
        assertEquals("1", q.popup.first())
    }
}
