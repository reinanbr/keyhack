package com.reinanbr.keyhack.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.reinanbr.keyhack.keyboard.model.BuiltInLayouts
import com.reinanbr.keyhack.keyboard.model.KeyAction
import com.reinanbr.keyhack.keyboard.model.ShiftController
import com.reinanbr.keyhack.keyboard.model.outputText
import com.reinanbr.keyhack.keyboard.view.KeyboardView

/**
 * KeyHack's input method service.
 *
 * Privacy invariants (see README "Threat model"):
 *  - Typed text goes only to the focused editor's InputConnection. It is never
 *    logged, stored or sent anywhere; the app has no INTERNET permission.
 *  - The service keeps no copy of text beyond the current key event.
 */
class KeyHackImeService : InputMethodService() {
    private var keyboardView: KeyboardView? = null
    private val shift = ShiftController()

    private var enterBehavior: EnterBehavior = EnterBehavior.NewLine
    private var rawKeyEditor = false
    private var hasSelection = false

    override fun onCreateInputView(): View = KeyboardView(this).also { view ->
        view.layout = BuiltInLayouts.QWERTY
        view.listener = KeyboardView.Listener(::onKey)
        keyboardView = view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        enterBehavior = EditorPolicy.enterBehavior(info.imeOptions)
        rawKeyEditor = EditorPolicy.prefersKeyEvents(info.inputType)
        hasSelection = info.initialSelStart != info.initialSelEnd
        if (!restarting) shift.reset()
        keyboardView?.apply {
            enterLabel = enterLabelFor(enterBehavior)
            shiftState = shift.state
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        hasSelection = newSelStart != newSelEnd
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        shift.reset()
    }

    override fun onDestroy() {
        keyboardView = null
        super.onDestroy()
    }

    private fun onKey(action: KeyAction) {
        val ic = currentInputConnection ?: return
        when (action) {
            is KeyAction.Character -> {
                val key = keyFor(action)
                val text = key?.outputText(shift.isShifted) ?: action.text
                ic.commitText(text, 1)
                shift.onCharacterCommitted()
            }

            KeyAction.Space -> ic.commitText(" ", 1)

            KeyAction.Shift -> shift.onShiftTap(SystemClock.uptimeMillis())

            KeyAction.Delete ->
                when {
                    rawKeyEditor -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    hasSelection -> ic.commitText("", 1)
                    else -> ic.deleteSurroundingTextInCodePoints(1, 0)
                }

            KeyAction.Enter ->
                when (val enter = enterBehavior) {
                    is EnterBehavior.Action -> ic.performEditorAction(enter.actionId)

                    EnterBehavior.NewLine ->
                        if (rawKeyEditor) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.commitText("\n", 1)
                }
        }
        keyboardView?.shiftState = shift.state
    }

    private fun keyFor(action: KeyAction.Character) = keyboardView
        ?.layout
        ?.rows
        ?.firstNotNullOfOrNull { row -> row.keys.firstOrNull { it.action == action } }

    private fun enterLabelFor(behavior: EnterBehavior): String {
        val res =
            when ((behavior as? EnterBehavior.Action)?.actionId) {
                EditorInfo.IME_ACTION_GO -> R.string.keyhack_enter_go
                EditorInfo.IME_ACTION_SEARCH -> R.string.keyhack_enter_search
                EditorInfo.IME_ACTION_SEND -> R.string.keyhack_enter_send
                EditorInfo.IME_ACTION_NEXT -> R.string.keyhack_enter_next
                EditorInfo.IME_ACTION_PREVIOUS -> R.string.keyhack_enter_previous
                EditorInfo.IME_ACTION_DONE -> R.string.keyhack_enter_done
                else -> return "⏎"
            }
        return getString(res)
    }
}
