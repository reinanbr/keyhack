package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifierControllerTest {
    @Test
    fun tapIsOneShot() {
        val m = ModifierController()
        assertEquals(StickyState.ONCE, m.onTap(Modifier.CTRL, 0))
        assertTrue(m.anyActive)
        m.onKeySent()
        assertEquals(StickyState.OFF, m.state(Modifier.CTRL))
    }

    @Test
    fun doubleTapLocksUntilTappedAgain() {
        val m = ModifierController()
        m.onTap(Modifier.ALT, 0)
        assertEquals(StickyState.LOCKED, m.onTap(Modifier.ALT, 100))
        m.onKeySent()
        assertEquals(StickyState.LOCKED, m.state(Modifier.ALT))
        assertEquals(StickyState.OFF, m.onTap(Modifier.ALT, 5000))
    }

    @Test
    fun slowSecondTapTurnsOff() {
        val m = ModifierController()
        m.onTap(Modifier.META, 0)
        assertEquals(StickyState.OFF, m.onTap(Modifier.META, 1000))
        assertFalse(m.anyActive)
    }

    @Test
    fun modifiersAreIndependent() {
        val m = ModifierController()
        m.onTap(Modifier.CTRL, 0)
        m.onTap(Modifier.ALT, 10)
        assertEquals(StickyState.ONCE, m.state(Modifier.CTRL))
        assertEquals(StickyState.ONCE, m.state(Modifier.ALT))
    }
}
