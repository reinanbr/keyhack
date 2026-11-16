package com.reinanbr.keyhack.ime

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Sends real key events, Hacker's Keyboard style: modifier DOWN events, then
 * the key DOWN/UP with the full meta state, then modifier UP events. Termux,
 * SSH clients and code editors see Ctrl+C, Alt+F, Shift+Arrow exactly as from
 * a hardware keyboard.
 */
object KeySender {
    data class Mods(
        val shift: Boolean = false,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val meta: Boolean = false,
    ) {
        val any: Boolean get() = shift || ctrl || alt || meta
    }

    private const val FLAGS = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE

    fun metaState(m: Mods): Int {
        var s = 0
        if (m.shift) s = s or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (m.ctrl) s = s or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (m.alt) s = s or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (m.meta) s = s or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        return s
    }

    fun send(ic: InputConnection, keyCode: Int, mods: Mods) {
        val now = SystemClock.uptimeMillis()
        val modKeys =
            buildList {
                if (mods.ctrl) add(KeyEvent.KEYCODE_CTRL_LEFT to Mods(ctrl = true))
                if (mods.alt) add(KeyEvent.KEYCODE_ALT_LEFT to Mods(alt = true))
                if (mods.meta) add(KeyEvent.KEYCODE_META_LEFT to Mods(meta = true))
                if (mods.shift) add(KeyEvent.KEYCODE_SHIFT_LEFT to Mods(shift = true))
            }
        var state = 0
        for ((code, m) in modKeys) {
            state = state or metaState(m)
            ic.sendKeyEvent(event(now, KeyEvent.ACTION_DOWN, code, state))
        }
        val meta = metaState(mods)
        ic.sendKeyEvent(event(now, KeyEvent.ACTION_DOWN, keyCode, meta))
        ic.sendKeyEvent(event(now, KeyEvent.ACTION_UP, keyCode, meta))
        for ((code, m) in modKeys.reversed()) {
            state = state and metaState(m).inv()
            ic.sendKeyEvent(event(now, KeyEvent.ACTION_UP, code, state))
        }
    }

    private fun event(time: Long, action: Int, code: Int, meta: Int) =
        KeyEvent(time, time, action, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS)

    private val charMap: KeyCharacterMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    /**
     * Sends [text] (one character) as a key press with [mods] added, e.g. Ctrl+c.
     * Returns false if the character has no key on a standard keyboard.
     */
    fun sendChar(ic: InputConnection, text: String, mods: Mods): Boolean {
        if (text.length != 1) return false
        val events = charMap.getEvents(text.toCharArray()) ?: return false
        val down =
            events.firstOrNull {
                it.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(
                    it.keyCode,
                )
            } ?: return false
        val needsShift = down.isShiftPressed
        send(ic, down.keyCode, mods.copy(shift = mods.shift || needsShift))
        return true
    }
}
