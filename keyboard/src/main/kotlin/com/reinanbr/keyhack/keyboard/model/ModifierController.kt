package com.reinanbr.keyhack.keyboard.model

/** State of a sticky key: off, armed for the next key, or locked. */
enum class StickyState { OFF, ONCE, LOCKED }

/**
 * Sticky Ctrl/Alt/Meta, as in Hacker's Keyboard: tap = one-shot (applies to
 * the next key, then releases), double tap = lock, tap while locked = off.
 */
class ModifierController(private val doubleTapMillis: Long = ShiftController.DEFAULT_DOUBLE_TAP_MILLIS) {
    private val states = HashMap<Modifier, StickyState>()
    private val lastTap = HashMap<Modifier, Long>()

    fun state(modifier: Modifier): StickyState = states[modifier] ?: StickyState.OFF

    fun isActive(modifier: Modifier): Boolean = state(modifier) != StickyState.OFF

    val anyActive: Boolean get() = states.values.any { it != StickyState.OFF }

    fun onTap(modifier: Modifier, nowMillis: Long): StickyState {
        val last = lastTap[modifier]
        val quick = last != null && nowMillis - last in 0..doubleTapMillis
        val next =
            when (state(modifier)) {
                StickyState.OFF -> StickyState.ONCE
                StickyState.ONCE -> if (quick) StickyState.LOCKED else StickyState.OFF
                StickyState.LOCKED -> StickyState.OFF
            }
        states[modifier] = next
        lastTap[modifier] = nowMillis
        return next
    }

    /** Call after a non-modifier key was sent; releases one-shot modifiers. */
    fun onKeySent() {
        for ((m, s) in states) if (s == StickyState.ONCE) states[m] = StickyState.OFF
    }

    fun reset() {
        states.clear()
        lastTap.clear()
    }

    fun snapshot(): Map<Modifier, StickyState> = Modifier.entries.associateWith { state(it) }
}
