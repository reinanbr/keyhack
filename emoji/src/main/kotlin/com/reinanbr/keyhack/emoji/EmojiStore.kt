package com.reinanbr.keyhack.emoji

import android.content.Context

/** Recently used emoji and per-emoji skin tones, kept in private preferences. */
class EmojiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun recents(): List<String> = prefs.getString(
        RECENTS,
        null,
    )?.split('\n')?.filter { it.isNotEmpty() } ?: DEFAULT_RECENTS

    fun addRecent(text: String) {
        val list = (listOf(text) + recents().filter { it != text }).take(MAX_RECENTS)
        prefs.edit().putString(RECENTS, list.joinToString("\n")).apply()
    }

    fun tone(emoji: String): Int = prefs.getInt(TONE_PREFIX + emoji, 0)

    fun setTone(emoji: String, tone: Int) {
        prefs.edit().putInt(TONE_PREFIX + emoji, tone).apply()
    }

    /** Recents and tones, for a backup. */
    fun snapshot(): State = State(
        recents(),
        prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith(TONE_PREFIX) && v is Int) k.removePrefix(TONE_PREFIX) to v else null
        }.toMap(),
    ).sanitized()

    /** Replaces recents and tones with [state] (from a backup; checked here). */
    fun restore(state: State) {
        val clean = state.sanitized()
        prefs.edit().apply {
            clear()
            putString(RECENTS, clean.recents.joinToString("\n"))
            for ((emoji, tone) in clean.tones) putInt(TONE_PREFIX + emoji, tone)
        }.apply()
    }

    data class State(val recents: List<String>, val tones: Map<String, Int>) {
        /** Drops what this store would never have written: long or multi-line entries, unknown tones. */
        fun sanitized(): State = State(
            recents.filter(::isEmojiText).distinct().take(MAX_RECENTS),
            tones.filter { (e, t) -> isEmojiText(e) && t in 1..Emoji.TONES }.entries.take(MAX_TONES)
                .associate { it.key to it.value },
        )
    }

    companion object {
        const val FILE = "keyhack_emoji"
        const val MAX_RECENTS = 40
        const val MAX_TONES = 500
        private const val RECENTS = "recents"
        private const val TONE_PREFIX = "tone:"
        private const val MAX_EMOJI_CHARS = 32
        private val DEFAULT_RECENTS = listOf("😂", "❤️", "👍", "😊", "🙏", "😭", "🔥", "😅", "🎉", "🤔", "👀", "✅")

        /** Recents hold emoji, kaomoji and symbols: one short line each. */
        internal fun isEmojiText(s: String) = s.isNotBlank() && s.length <= MAX_EMOJI_CHARS && '\n' !in s
    }
}
