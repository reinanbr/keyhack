package com.reinanbr.keyhack.keyboard.model

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * Shift state machine. One tap = shift the next character; a second tap within
 * [doubleTapMillis] = caps lock; a tap while locked = off.
 *
 * Shift can also be held like a hardware key: everything typed while it is
 * down comes out shifted, and releasing it after typing turns it off.
 */
class ShiftController(private val doubleTapMillis: Long = DEFAULT_DOUBLE_TAP_MILLIS) {
    var state: ShiftState = ShiftState.OFF
        private set

    private var lastTapAt = Long.MIN_VALUE

    /** The Shift key is physically down. */
    var isHeld: Boolean = false
        private set

    /** Something was typed while Shift was held. */
    private var chorded = false

    /** True while the current ONCE state was set by auto-capitalization, not by the user. */
    var isAuto: Boolean = false
        private set

    fun onShiftTap(nowMillis: Long): ShiftState {
        isAuto = false
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

    /** Shift pressed: acts like a tap right away, and stays shifted while held. */
    fun onShiftDown(nowMillis: Long): ShiftState {
        onShiftTap(nowMillis)
        isHeld = true
        chorded = false
        return state
    }

    /** Shift released: after typing while held it turns off; a plain tap keeps its state. */
    fun onShiftUp(): ShiftState {
        if (isHeld && chorded && state != ShiftState.LOCKED) state = ShiftState.OFF
        isHeld = false
        chorded = false
        return state
    }

    /** Call after a character is committed; releases a one-shot shift (not while Shift is held). */
    fun onCharacterCommitted(): ShiftState {
        if (isHeld) {
            chorded = true
            isAuto = false
            return state
        }
        if (state == ShiftState.ONCE) state = ShiftState.OFF
        isAuto = false
        return state
    }

    /**
     * Auto-capitalization: arms a one-shot shift when [capsWanted], and drops a
     * shift it armed itself when not. Never touches a user's shift or caps lock.
     */
    fun applyAutoCaps(capsWanted: Boolean): ShiftState {
        if (capsWanted && state == ShiftState.OFF) {
            state = ShiftState.ONCE
            isAuto = true
        } else if (!capsWanted && isAuto && state == ShiftState.ONCE) {
            state = ShiftState.OFF
            isAuto = false
        }
        return state
    }

    fun reset() {
        isAuto = false
        state = ShiftState.OFF
        lastTapAt = Long.MIN_VALUE
        isHeld = false
        chorded = false
    }

    val isShifted: Boolean get() = state != ShiftState.OFF || isHeld

    /** What the key shows: held counts as shifted. */
    val displayState: ShiftState get() = if (isHeld && state == ShiftState.OFF) ShiftState.ONCE else state

    /**
     * Shift as a modifier for non-text keys (arrows, Tab, Ctrl+key, cursor
     * swipe): only when the user asked for it. Auto-capitalization must not
     * turn an arrow into a selection or Tab into Shift+Tab.
     */
    val isUserShift: Boolean get() = isShifted && !isAuto

    companion object {
        const val DEFAULT_DOUBLE_TAP_MILLIS = 350L
    }
}
