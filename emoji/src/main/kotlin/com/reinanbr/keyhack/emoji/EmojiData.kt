package com.reinanbr.keyhack.emoji

import java.io.BufferedReader

/** One emoji from the Unicode table (see tools/gen_emoji.py). */
data class Emoji(
    val value: String,
    val group: String,
    /** Accepts Fitzpatrick skin-tone modifiers. */
    val toneable: Boolean,
    /** Search keywords, all languages, lowercase. */
    val keywords: List<String>,
) {
    /** This emoji with [tone] (0 = none, 1..5 = light..dark). */
    fun withTone(tone: Int): String {
        if (!toneable || tone !in 1..TONES) return value
        val cps = value.codePoints().toArray().toMutableList()
        if (cps.size > 1 && cps[1] == VS16) cps.removeAt(1)
        cps.add(1, TONE_BASE + tone - 1)
        return String(cps.toIntArray(), 0, cps.size)
    }

    companion object {
        const val TONES = 5
        private const val VS16 = 0xFE0F
        private const val TONE_BASE = 0x1F3FB
    }
}

/** Emoji table loaded from emoji.tsv, with keyword search. */
class EmojiData(val all: List<Emoji>) {
    val groups: List<String> = all.map { it.group }.distinct()
    private val byGroup = all.groupBy { it.group }
    private val byValue = all.associateBy { it.value }

    fun group(name: String): List<Emoji> = byGroup[name].orEmpty()

    fun find(value: String): Emoji? = byValue[value]

    /**
     * Emoji whose keywords start with every word of [query] (any language),
     * names first. "gato" and "cat" both find 🐱.
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<Emoji> {
        val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Emoji, Int>>()
        for (e in all) {
            var score = 0
            var ok = true
            for (t in terms) {
                var best = 0
                for ((i, k) in e.keywords.withIndex()) {
                    val nk = normalize(k)
                    val s =
                        when {
                            nk == t -> EXACT
                            nk.startsWith(t) -> PREFIX
                            nk.split(' ').any { it.startsWith(t) } -> WORD
                            else -> 0
                        }
                    // Earlier keywords are the emoji's names: rank them higher.
                    val v = if (s > 0 && i < NAME_SLOTS) s + 1 else s
                    if (v > best) best = v
                }
                if (best == 0) {
                    ok = false
                    break
                }
                score += best
            }
            if (ok) scored += e to score
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    companion object {
        private const val SEARCH_LIMIT = 40
        private const val EXACT = 6
        private const val PREFIX = 4
        private const val WORD = 2
        private const val NAME_SLOTS = 1

        fun read(reader: BufferedReader): EmojiData {
            val list = ArrayList<Emoji>(INITIAL)
            reader.forEachLine { line ->
                val f = line.split('\t')
                if (f.size < KEYWORD_COLUMN) return@forEachLine
                val keywords = f.drop(KEYWORD_COLUMN).flatMap { it.split('|') }.filter { it.isNotEmpty() }
                list += Emoji(f[1], f[0], f[2] == "1", keywords)
            }
            return EmojiData(list)
        }

        private const val INITIAL = 2048
        private const val KEYWORD_COLUMN = 4

        private fun normalize(s: String): String = java.text.Normalizer
            .normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()
    }
}

/** Text faces and symbols for the extra tabs. */
object Kaomoji {
    val faces =
        listOf(
            "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "¯\\_(ツ)_/¯", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ", "(ᵔᴥᵔ)", "ʕ•ᴥ•ʔ",
            "(っ◔◡◔)っ", "(づ｡◕‿‿◕｡)づ", "(•_•) ( •_•)>⌐■-■ (⌐■_■)", "( ˘▽˘)っ♨", "(ง'̀-'́)ง", "(¬‿¬)",
            "(＾▽＾)", "(≧◡≦)", "(｡♥‿♥｡)", "(T_T)", "(ಥ﹏ಥ)", "(>_<)", "(°ロ°)", "(⊙_⊙)", "(-_-) zzZ",
            "ヽ(°〇°)ﾉ", "\\(^o^)/", "(☞ﾟヮﾟ)☞", "☜(ﾟヮﾟ☜)", "(ノಠ益ಠ)ノ彡┻━┻", "(•‿•)", "(✿◠‿◠)",
            "(^_−)☆", "(*^‿^*)", "(◕‿◕✿)", "(❁´◡`❁)", "<(￣︶￣)>", "(￣ー￣)", "(¬_¬ )", "(~˘▾˘)~",
            "♪~ ᕕ(ᐛ)ᕗ", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "( •̀ ω •́ )✧", "(∩^o^)⊃━☆", "щ(゜ロ゜щ)", "(ʘ‿ʘ)", "(°ー°〃)",
        )

    val symbols =
        listOf(
            "→", "←", "↑", "↓", "↔", "⇒", "⇐", "⇔", "↩", "↪", "✓", "✔", "✗", "✘", "★", "☆", "♥", "♡",
            "•", "◦", "·", "…", "‰", "°", "±", "×", "÷", "≈", "≠", "≤", "≥", "∞", "√", "∑", "∏", "∫",
            "∂", "∆", "∇", "∈", "∉", "∅", "∧", "∨", "¬", "⊕", "⊗", "λ", "π", "µ", "Ω", "α", "β", "γ",
            "θ", "σ", "©", "®", "™", "§", "¶", "†", "‡", "№", "€", "£", "¥", "¢", "₿", "₹", "⌘", "⌥",
            "⇧", "⌃", "⎋", "⏎", "⌫", "⇥", "␣", "▲", "▼", "◀", "▶", "■", "□", "●", "○", "◆", "◇",
        )
}
