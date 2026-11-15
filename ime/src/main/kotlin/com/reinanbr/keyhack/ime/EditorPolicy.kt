package com.reinanbr.keyhack.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** What the Enter key does in the current editor. */
sealed interface EnterBehavior {
    /** Insert a newline (as a key event for raw-key editors such as terminals). */
    data object NewLine : EnterBehavior

    /** Run the editor action (Go, Search, Send, ...). */
    data class Action(val actionId: Int) : EnterBehavior
}

/**
 * Pure decisions derived from an editor's EditorInfo fields. Takes raw ints so
 * it is testable on the JVM without an EditorInfo instance.
 */
object EditorPolicy {
    fun enterBehavior(imeOptions: Int): EnterBehavior {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterBehavior.NewLine
        return when (val action = imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> EnterBehavior.NewLine
            else -> EnterBehavior.Action(action)
        }
    }

    /**
     * TYPE_NULL editors (Termux and most terminal emulators) want raw key
     * events rather than text edits for Enter and Delete.
     */
    fun prefersKeyEvents(inputType: Int): Boolean = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
}
