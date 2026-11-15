package com.reinanbr.keyhack.keyboard.model

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * Shift state machine. One tap = shift the next character; a second tap within
 * [doubleTapMillis] = caps lock; a tap while locked = off.
 */
class ShiftController(private val doubleTapMillis: Long = DEFAULT_DOUBLE_TAP_MILLIS) {
    var state: ShiftState = ShiftState.OFF
        private set

    private var lastTapAt = Long.MIN_VALUE

    fun onShiftTap(nowMillis: Long): ShiftState {
        val quick = lastTapAt != Long.MIN_VALUE && nowMillis - lastTapAt in 0..doubleTapMillis
        state =
            when (state) {
                ShiftState.OFF -> ShiftState.ONCE
                ShiftState.ONCE -> if (quick) ShiftState.LOCKED else ShiftState.OFF
                ShiftState.LOCKED -> ShiftState.OFF
            }
        lastTapAt = nowMillis
        return state
    }

    /** Call after a character is committed; releases a one-shot shift. */
    fun onCharacterCommitted(): ShiftState {
        if (state == ShiftState.ONCE) state = ShiftState.OFF
        return state
    }

    fun reset() {
        state = ShiftState.OFF
        lastTapAt = Long.MIN_VALUE
    }

    val isShifted: Boolean get() = state != ShiftState.OFF

    companion object {
        const val DEFAULT_DOUBLE_TAP_MILLIS = 350L
    }
}
