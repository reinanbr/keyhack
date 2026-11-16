package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ShiftControllerTest {
    @Test
    fun singleTapIsOneShot() {
        val s = ShiftController()
        assertEquals(ShiftState.ONCE, s.onShiftTap(0))
        assertEquals(ShiftState.OFF, s.onCharacterCommitted())
    }

    @Test
    fun doubleTapLocks() {
        val s = ShiftController(doubleTapMillis = 300)
        s.onShiftTap(1_000)
        assertEquals(ShiftState.LOCKED, s.onShiftTap(1_200))
        assertEquals(ShiftState.LOCKED, s.onCharacterCommitted())
        assertEquals(ShiftState.OFF, s.onShiftTap(5_000))
    }

    @Test
    fun slowSecondTapTurnsShiftOff() {
        val s = ShiftController(doubleTapMillis = 300)
        s.onShiftTap(1_000)
        assertEquals(ShiftState.OFF, s.onShiftTap(2_000))
    }

    @Test
    fun resetClearsState() {
        val s = ShiftController()
        s.onShiftTap(0)
        s.reset()
        assertEquals(ShiftState.OFF, s.state)
        assertEquals(ShiftState.ONCE, s.onShiftTap(10))
    }

    @Test
    fun autoCapsArmsAndDropsOnlyItsOwnShift() {
        val s = ShiftController()
        assertEquals(ShiftState.ONCE, s.applyAutoCaps(true))
        assertEquals(ShiftState.OFF, s.applyAutoCaps(false))
        s.onShiftTap(0)
        assertEquals(ShiftState.ONCE, s.applyAutoCaps(false))
        s.onShiftTap(100)
        assertEquals(ShiftState.LOCKED, s.applyAutoCaps(false))
    }
}
