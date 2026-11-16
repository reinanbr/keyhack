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

/** What kind of text the focused field holds. */
enum class FieldKind { TEXT, PASSWORD, EMAIL, URI, NUMBER, PHONE, DATETIME, RAW }

/** Everything the IME decides from EditorInfo when a field gains focus. */
data class FieldPolicy(
    val kind: FieldKind,
    val enter: EnterBehavior,
    val rawKeys: Boolean,
    /** Show the suggestion strip candidates. */
    val suggestions: Boolean,
    /** Replace the typed word on space/punctuation. */
    val autocorrect: Boolean,
    /** Feed typed words into on-device learning. */
    val learning: Boolean,
    val autoCaps: Boolean,
    /** Layer to open with: "numpad", "phone" or null for letters. */
    val initialLayer: String?,
)

/**
 * Pure decisions derived from an editor's EditorInfo fields. Takes raw ints so
 * it is testable on the JVM without an EditorInfo instance.
 *
 * Sensitive-field policy: in password fields, and whenever the app sets
 * IME_FLAG_NO_PERSONALIZED_LEARNING (incognito tabs, banking apps), KeyHack
 * neither suggests from nor learns what is typed.
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

    fun kind(inputType: Int): FieldKind {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_NULL -> FieldKind.RAW

            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldKind.PASSWORD else FieldKind.NUMBER

            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE

            InputType.TYPE_CLASS_DATETIME -> FieldKind.DATETIME

            else ->
                when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    -> FieldKind.PASSWORD

                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    -> FieldKind.EMAIL

                    InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URI

                    else -> FieldKind.TEXT
                }
        }
    }

    fun isSensitive(inputType: Int, imeOptions: Int): Boolean =
        kind(inputType) == FieldKind.PASSWORD || imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    /**
     * [pcMode] turns off autocorrect for terminals and code editors;
     * [incognito] is the user's manual toggle.
     */
    fun policy(
        inputType: Int,
        imeOptions: Int,
        suggestionsOn: Boolean = true,
        autocorrectOn: Boolean = true,
        learningOn: Boolean = true,
        autoCapsOn: Boolean = true,
        incognito: Boolean = false,
        pcMode: Boolean = false,
    ): FieldPolicy {
        val kind = kind(inputType)
        val sensitive = isSensitive(inputType, imeOptions)
        val flags = inputType and InputType.TYPE_MASK_FLAGS
        val textClass = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val noSuggestions = textClass && flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val wordsField = kind == FieldKind.TEXT || kind == FieldKind.EMAIL || kind == FieldKind.URI
        val suggestions = suggestionsOn && wordsField && !sensitive && !pcMode
        val capsFlags =
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        return FieldPolicy(
            kind = kind,
            enter = enterBehavior(imeOptions),
            rawKeys = kind == FieldKind.RAW,
            suggestions = suggestions,
            autocorrect = suggestions && autocorrectOn && kind == FieldKind.TEXT && !noSuggestions,
            learning = suggestions && learningOn && !incognito && kind == FieldKind.TEXT,
            autoCaps = autoCapsOn && textClass && flags and capsFlags != 0 && !sensitive && !pcMode,
            initialLayer =
            when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> "numpad"
                InputType.TYPE_CLASS_PHONE -> "phone"
                else -> null
            },
        )
    }
}
