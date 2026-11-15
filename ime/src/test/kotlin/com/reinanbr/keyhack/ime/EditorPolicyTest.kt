package com.reinanbr.keyhack.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPolicyTest {
    @Test
    fun unspecifiedAndNoneInsertNewline() {
        assertEquals(EnterBehavior.NewLine, EditorPolicy.enterBehavior(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertEquals(EnterBehavior.NewLine, EditorPolicy.enterBehavior(EditorInfo.IME_ACTION_NONE))
    }

    @Test
    fun explicitActionsRunTheAction() {
        for (a in listOf(
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS,
        )) {
            assertEquals(EnterBehavior.Action(a), EditorPolicy.enterBehavior(a or EditorInfo.IME_FLAG_NO_FULLSCREEN))
        }
    }

    @Test
    fun noEnterActionFlagWins() {
        val opts = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(EnterBehavior.NewLine, EditorPolicy.enterBehavior(opts))
    }

    @Test
    fun terminalsGetKeyEvents() {
        assertTrue(EditorPolicy.prefersKeyEvents(InputType.TYPE_NULL))
        assertFalse(EditorPolicy.prefersKeyEvents(InputType.TYPE_CLASS_TEXT))
        assertFalse(
            EditorPolicy.prefersKeyEvents(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
        )
    }
}
