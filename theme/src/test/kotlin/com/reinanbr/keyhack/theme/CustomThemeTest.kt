package com.reinanbr.keyhack.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomThemeTest {
    private val picks = listOf(0xFF101010, 0xFF303030, 0xFF202020, 0xFFF0F0F0, 0xFFFFC107).map { it.toInt() }

    @Test
    fun roundTrip() {
        val s = CustomTheme.encode(picks)
        assertEquals("#101010,#303030,#202020,#F0F0F0,#FFC107", s)
        assertEquals(picks, CustomTheme.decode(s))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(CustomTheme.decode(""))
        assertNull(CustomTheme.decode("#101010,#303030"))
        assertNull(CustomTheme.decode("#10101G,#303030,#202020,#F0F0F0,#FFC107"))
        assertNull(CustomTheme.decode("#1010,#303030,#202020,#F0F0F0,#FFC107"))
    }

    @Test
    fun derivedColors() {
        val c = CustomTheme.colors(picks)
        assertEquals(picks[0], c.background)
        // Pressed keys lean toward the label; secondary labels toward the key.
        assertTrue(CustomTheme.luminance(c.pressedKey) > CustomTheme.luminance(c.key))
        assertTrue(CustomTheme.luminance(c.secondaryLabel) < CustomTheme.luminance(c.label))
        assertEquals(0xFF000000.toInt(), c.onAccent, "black reads better on amber")
        assertEquals(0xFFFFFFFF.toInt(), CustomTheme.readableOn(0xFF1A73E8.toInt()))
    }

    @Test
    fun mixAndContrast() {
        assertEquals(0xFF808080.toInt(), CustomTheme.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        assertEquals(21.0, CustomTheme.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, CustomTheme.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
    }

    @Test
    fun builtInThemesStartTheEditor() {
        assertEquals(KeyboardThemes.DARK.label, CustomTheme.picks(KeyboardThemes.DARK)[CustomTheme.Slot.LABEL.ordinal])
    }
}
