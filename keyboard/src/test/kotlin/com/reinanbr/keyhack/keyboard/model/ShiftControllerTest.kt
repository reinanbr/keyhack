package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun holdingShiftShiftsEveryKeyUntilReleased() {
        val s = ShiftController()
        s.onShiftDown(0)
        repeat(3) {
            assertTrue(s.isShifted)
            s.onCharacterCommitted()
        }
        assertEquals(ShiftState.ONCE, s.state, "still shifted while held")
        assertEquals(ShiftState.OFF, s.onShiftUp())
        assertFalse(s.isShifted)
    }

    @Test
    fun pressAndReleaseWithoutTypingIsATap() {
        val s = ShiftController(doubleTapMillis = 300)
        s.onShiftDown(1_000)
        assertEquals(ShiftState.ONCE, s.onShiftUp())
        s.onShiftDown(1_200)
        assertEquals(ShiftState.LOCKED, s.onShiftUp(), "double tap still locks")
    }

    @Test
    fun holdingShiftWhileItWasOnStillShifts() {
        val s = ShiftController(doubleTapMillis = 300)
        s.onShiftTap(0)
        s.onShiftDown(5_000) // slow second press turns the one-shot off...
        assertTrue(s.isShifted, "...but holding it shifts")
        assertEquals(ShiftState.ONCE, s.displayState)
        s.onCharacterCommitted()
        assertEquals(ShiftState.OFF, s.onShiftUp())
    }

    @Test
    fun releaseKeepsCapsLock() {
        val s = ShiftController(doubleTapMillis = 300)
        s.onShiftTap(1_000)
        s.onShiftDown(1_100)
        s.onCharacterCommitted()
        assertEquals(ShiftState.LOCKED, s.onShiftUp())
    }

    @Test
    fun autoCapsIsNotAModifier() {
        val s = ShiftController()
        s.applyAutoCaps(true)
        assertTrue(s.isShifted, "letters are capitalized")
        assertFalse(s.isUserShift, "but arrows and Tab are not shifted")
        s.reset()
        s.onShiftTap(0)
        assertTrue(s.isUserShift)
    }
}
