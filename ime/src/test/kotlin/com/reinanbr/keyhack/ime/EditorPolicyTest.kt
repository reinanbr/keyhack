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

    private val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    @Test
    fun normalTextGetsEverything() {
        val p = EditorPolicy.policy(text, 0)
        assertEquals(FieldKind.TEXT, p.kind)
        assertTrue(p.suggestions && p.autocorrect && p.learning && p.autoCaps)
        assertEquals(null, p.initialLayer)
    }

    @Test
    fun passwordFieldsNeverSuggestOrLearn() {
        for (v in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) {
            val p = EditorPolicy.policy(InputType.TYPE_CLASS_TEXT or v, 0)
            assertEquals(FieldKind.PASSWORD, p.kind)
            assertFalse(p.suggestions || p.autocorrect || p.learning || p.autoCaps, "variation $v")
        }
        val pin = EditorPolicy.policy(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0)
        assertFalse(pin.learning)
        assertEquals("numpad", pin.initialLayer)
    }

    @Test
    fun noPersonalizedLearningFlagDisablesLearning() {
        val p = EditorPolicy.policy(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertFalse(p.learning)
        assertFalse(p.suggestions)
        assertTrue(EditorPolicy.isSensitive(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
    }

    @Test
    fun incognitoAndPcModeDisableLearningAndAutocorrect() {
        assertFalse(EditorPolicy.policy(text, 0, incognito = true).learning)
        assertTrue(EditorPolicy.policy(text, 0, incognito = true).suggestions)
        val pc = EditorPolicy.policy(text, 0, pcMode = true)
        assertFalse(pc.autocorrect || pc.learning || pc.autoCaps)
    }

    @Test
    fun urlAndEmailSuggestButNeverAutocorrect() {
        for (v in listOf(InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)) {
            val p = EditorPolicy.policy(InputType.TYPE_CLASS_TEXT or v, 0)
            assertTrue(p.suggestions)
            assertFalse(p.autocorrect || p.learning)
        }
        assertFalse(EditorPolicy.policy(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0).autocorrect)
    }

    @Test
    fun numberAndPhoneFieldsOpenTheirPads() {
        assertEquals("numpad", EditorPolicy.policy(InputType.TYPE_CLASS_NUMBER, 0).initialLayer)
        assertEquals("phone", EditorPolicy.policy(InputType.TYPE_CLASS_PHONE, 0).initialLayer)
        assertFalse(EditorPolicy.policy(InputType.TYPE_CLASS_NUMBER, 0).suggestions)
    }

    @Test
    fun terminalsGetRawKeys() {
        val p = EditorPolicy.policy(InputType.TYPE_NULL, 0)
        assertTrue(p.rawKeys)
        assertFalse(p.suggestions)
    }
}
