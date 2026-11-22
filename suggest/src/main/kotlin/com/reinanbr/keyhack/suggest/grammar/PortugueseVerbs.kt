package com.reinanbr.keyhack.suggest.grammar

/**
 * Portuguese verb forms for the tenses people write in chats, enough to tell
 * which person a verb is in and to produce the form a subject needs:
 * "nós vai" -> "vamos", "eles fala" -> "falam", "a gente fomos" -> "foi".
 *
 * Persons are indexed [S1] eu, [S2] tu, [S3] ele/você/a gente, [P1] nós,
 * [P3] eles/vocês. Regular -ar/-er/-ir verbs are generated (with the usual
 * spelling changes: fiquei, conheço, protejo); common irregular verbs and
 * their compounds come from tables.
 */
internal object PortugueseVerbs {
    enum class Tense { PRESENT, PRETERITE, IMPERFECT, FUTURE, CONDITIONAL }

    const val S1 = 0
    const val S2 = 1
    const val S3 = 2
    const val P1 = 3
    const val P3 = 4

    data class Parse(val lemma: String, val tense: Tense, val person: Int)

    private val REGULAR: Map<String, Map<Tense, List<String>>> =
        mapOf(
            "ar" to mapOf(
                Tense.PRESENT to listOf("o", "as", "a", "amos", "am"),
                Tense.PRETERITE to listOf("ei", "aste", "ou", "amos", "aram"),
                Tense.IMPERFECT to listOf("ava", "avas", "ava", "ávamos", "avam"),
            ),
            "er" to mapOf(
                Tense.PRESENT to listOf("o", "es", "e", "emos", "em"),
                Tense.PRETERITE to listOf("i", "este", "eu", "emos", "eram"),
                Tense.IMPERFECT to listOf("ia", "ias", "ia", "íamos", "iam"),
            ),
            "ir" to mapOf(
                Tense.PRESENT to listOf("o", "es", "e", "imos", "em"),
                Tense.PRETERITE to listOf("i", "iste", "iu", "imos", "iram"),
                Tense.IMPERFECT to listOf("ia", "ias", "ia", "íamos", "iam"),
            ),
        )

    /** Appended to the infinitive. */
    private val FUTURE = listOf("ei", "ás", "á", "emos", "ão")
    private val CONDITIONAL = listOf("ia", "ias", "ia", "íamos", "iam")

    private fun t(vararg forms: String) = forms.toList()

    /** Irregular tenses; tenses not listed are regular. */
    @Suppress("LongMethod") // A table.
    private val IRREGULAR: Map<String, Map<Tense, List<String>>> =
        mapOf(
            "ser" to mapOf(
                Tense.PRESENT to t("sou", "és", "é", "somos", "são"),
                Tense.PRETERITE to t("fui", "foste", "foi", "fomos", "foram"),
                Tense.IMPERFECT to t("era", "eras", "era", "éramos", "eram"),
            ),
            "estar" to mapOf(
                Tense.PRESENT to t("estou", "estás", "está", "estamos", "estão"),
                Tense.PRETERITE to t("estive", "estiveste", "esteve", "estivemos", "estiveram"),
            ),
            "ir" to mapOf(
                Tense.PRESENT to t("vou", "vais", "vai", "vamos", "vão"),
                Tense.PRETERITE to t("fui", "foste", "foi", "fomos", "foram"),
                Tense.IMPERFECT to t("ia", "ias", "ia", "íamos", "iam"),
            ),
            "ter" to mapOf(
                Tense.PRESENT to t("tenho", "tens", "tem", "temos", "têm"),
                Tense.PRETERITE to t("tive", "tiveste", "teve", "tivemos", "tiveram"),
                Tense.IMPERFECT to t("tinha", "tinhas", "tinha", "tínhamos", "tinham"),
            ),
            "haver" to mapOf(
                Tense.PRESENT to t("hei", "hás", "há", "havemos", "hão"),
                Tense.PRETERITE to t("houve", "houveste", "houve", "houvemos", "houveram"),
            ),
            "fazer" to mapOf(
                Tense.PRESENT to t("faço", "fazes", "faz", "fazemos", "fazem"),
                Tense.PRETERITE to t("fiz", "fizeste", "fez", "fizemos", "fizeram"),
                Tense.FUTURE to t("farei", "farás", "fará", "faremos", "farão"),
                Tense.CONDITIONAL to t("faria", "farias", "faria", "faríamos", "fariam"),
            ),
            "poder" to mapOf(
                Tense.PRESENT to t("posso", "podes", "pode", "podemos", "podem"),
                Tense.PRETERITE to t("pude", "pudeste", "pôde", "pudemos", "puderam"),
            ),
            "querer" to mapOf(
                Tense.PRESENT to t("quero", "queres", "quer", "queremos", "querem"),
                Tense.PRETERITE to t("quis", "quiseste", "quis", "quisemos", "quiseram"),
            ),
            "saber" to mapOf(
                Tense.PRESENT to t("sei", "sabes", "sabe", "sabemos", "sabem"),
                Tense.PRETERITE to t("soube", "soubeste", "soube", "soubemos", "souberam"),
            ),
            "dizer" to mapOf(
                Tense.PRESENT to t("digo", "dizes", "diz", "dizemos", "dizem"),
                Tense.PRETERITE to t("disse", "disseste", "disse", "dissemos", "disseram"),
                Tense.FUTURE to t("direi", "dirás", "dirá", "diremos", "dirão"),
                Tense.CONDITIONAL to t("diria", "dirias", "diria", "diríamos", "diriam"),
            ),
            "trazer" to mapOf(
                Tense.PRESENT to t("trago", "trazes", "traz", "trazemos", "trazem"),
                Tense.PRETERITE to t("trouxe", "trouxeste", "trouxe", "trouxemos", "trouxeram"),
                Tense.FUTURE to t("trarei", "trarás", "trará", "traremos", "trarão"),
                Tense.CONDITIONAL to t("traria", "trarias", "traria", "traríamos", "trariam"),
            ),
            "ver" to mapOf(
                Tense.PRESENT to t("vejo", "vês", "vê", "vemos", "veem"),
                Tense.PRETERITE to t("vi", "viste", "viu", "vimos", "viram"),
            ),
            "vir" to mapOf(
                Tense.PRESENT to t("venho", "vens", "vem", "vimos", "vêm"),
                Tense.PRETERITE to t("vim", "vieste", "veio", "viemos", "vieram"),
                Tense.IMPERFECT to t("vinha", "vinhas", "vinha", "vínhamos", "vinham"),
            ),
            "dar" to mapOf(
                Tense.PRESENT to t("dou", "dás", "dá", "damos", "dão"),
                Tense.PRETERITE to t("dei", "deste", "deu", "demos", "deram"),
            ),
            "pôr" to mapOf(
                Tense.PRESENT to t("ponho", "pões", "põe", "pomos", "põem"),
                Tense.PRETERITE to t("pus", "puseste", "pôs", "pusemos", "puseram"),
                Tense.IMPERFECT to t("punha", "punhas", "punha", "púnhamos", "punham"),
                Tense.FUTURE to t("porei", "porás", "porá", "poremos", "porão"),
                Tense.CONDITIONAL to t("poria", "porias", "poria", "poríamos", "poriam"),
            ),
            "ler" to mapOf(Tense.PRESENT to t("leio", "lês", "lê", "lemos", "leem")),
            "crer" to mapOf(Tense.PRESENT to t("creio", "crês", "crê", "cremos", "creem")),
            "ouvir" to mapOf(Tense.PRESENT to t("ouço", "ouves", "ouve", "ouvimos", "ouvem")),
            "pedir" to mapOf(Tense.PRESENT to t("peço", "pedes", "pede", "pedimos", "pedem")),
            "medir" to mapOf(Tense.PRESENT to t("meço", "medes", "mede", "medimos", "medem")),
            "perder" to mapOf(Tense.PRESENT to t("perco", "perdes", "perde", "perdemos", "perdem")),
            "valer" to mapOf(Tense.PRESENT to t("valho", "vales", "vale", "valemos", "valem")),
            "caber" to mapOf(
                Tense.PRESENT to t("caibo", "cabes", "cabe", "cabemos", "cabem"),
                Tense.PRETERITE to t("coube", "coubeste", "coube", "coubemos", "couberam"),
            ),
            "rir" to mapOf(
                Tense.PRESENT to t("rio", "ris", "ri", "rimos", "riem"),
                Tense.PRETERITE to t("ri", "riste", "riu", "rimos", "riram"),
            ),
            "sorrir" to mapOf(
                Tense.PRESENT to t("sorrio", "sorris", "sorri", "sorrimos", "sorriem"),
                Tense.PRETERITE to t("sorri", "sorriste", "sorriu", "sorrimos", "sorriram"),
            ),
            "dormir" to mapOf(Tense.PRESENT to t("durmo", "dormes", "dorme", "dormimos", "dormem")),
            "cobrir" to mapOf(Tense.PRESENT to t("cubro", "cobres", "cobre", "cobrimos", "cobrem")),
            "descobrir" to mapOf(Tense.PRESENT to t("descubro", "descobres", "descobre", "descobrimos", "descobrem")),
            "sentir" to mapOf(Tense.PRESENT to t("sinto", "sentes", "sente", "sentimos", "sentem")),
            "mentir" to mapOf(Tense.PRESENT to t("minto", "mentes", "mente", "mentimos", "mentem")),
            "seguir" to mapOf(Tense.PRESENT to t("sigo", "segues", "segue", "seguimos", "seguem")),
            "conseguir" to mapOf(Tense.PRESENT to t("consigo", "consegues", "consegue", "conseguimos", "conseguem")),
            "preferir" to mapOf(Tense.PRESENT to t("prefiro", "preferes", "prefere", "preferimos", "preferem")),
            "sugerir" to mapOf(Tense.PRESENT to t("sugiro", "sugeres", "sugere", "sugerimos", "sugerem")),
            "repetir" to mapOf(Tense.PRESENT to t("repito", "repetes", "repete", "repetimos", "repetem")),
            "servir" to mapOf(Tense.PRESENT to t("sirvo", "serves", "serve", "servimos", "servem")),
            "vestir" to mapOf(Tense.PRESENT to t("visto", "vestes", "veste", "vestimos", "vestem")),
            "divertir" to mapOf(Tense.PRESENT to t("divirto", "divertes", "diverte", "divertimos", "divertem")),
            "subir" to mapOf(Tense.PRESENT to t("subo", "sobes", "sobe", "subimos", "sobem")),
            "fugir" to mapOf(Tense.PRESENT to t("fujo", "foges", "foge", "fugimos", "fogem")),
            "construir" to mapOf(Tense.PRESENT to t("construo", "constróis", "constrói", "construímos", "constroem")),
            "destruir" to mapOf(Tense.PRESENT to t("destruo", "destróis", "destrói", "destruímos", "destroem")),
            "odiar" to mapOf(Tense.PRESENT to t("odeio", "odeias", "odeia", "odiamos", "odeiam")),
        )

    /** Compounds conjugated like their base: manter like ter, propor like pôr. */
    private val COMPOUNDS: Map<String, List<String>> =
        mapOf(
            "ter" to listOf("man", "ob", "con", "de", "re", "entre"),
            "pôr" to listOf("pro", "com", "dis", "im", "su", "ex", "re", "de", "pres", "trans", "sobre"),
            "fazer" to listOf("des", "re", "satis"),
            "ver" to listOf("pre", "re"),
        )

    private val COMPOUND_LEMMAS: Map<String, Pair<String, String>> =
        COMPOUNDS.flatMap { (base, prefixes) ->
            prefixes.map { p -> (p + base.replace("ô", "o")) to (p to base) }
        }.toMap()

    /** Forms of irregular verbs (all tenses), for parsing without guessing a stem. */
    private val IRREGULAR_INDEX: Map<String, List<Parse>> by lazy {
        val index = HashMap<String, MutableList<Parse>>()
        for (lemma in IRREGULAR.keys + COMPOUND_LEMMAS.keys) {
            for ((tense, forms) in forms(lemma)) {
                forms.forEachIndexed { person, f -> index.getOrPut(f) { ArrayList() } += Parse(lemma, tense, person) }
            }
        }
        index
    }

    fun isIrregularForm(word: String): Boolean = word in IRREGULAR_INDEX

    fun isIrregular(lemma: String): Boolean = lemma in IRREGULAR || lemma in COMPOUND_LEMMAS

    /** Every tense of [lemma] (an infinitive), five persons each; empty if it is not one. */
    fun forms(lemma: String): Map<Tense, List<String>> {
        COMPOUND_LEMMAS[lemma]?.let { (prefix, base) -> return compound(prefix, base) }
        val cls = lemma.takeLast(2)
        val endings = REGULAR[cls]
        val stem = lemma.dropLast(2)
        val out = LinkedHashMap<Tense, List<String>>()
        if (endings != null) {
            if (stem.isNotEmpty()) {
                val special = specialForms(lemma, stem)
                for ((tense, list) in endings) {
                    out[tense] = special[tense] ?: list.mapIndexed { p, e -> spell(stem, cls, tense, p, e) }
                }
            }
            out[Tense.FUTURE] = FUTURE.map { lemma + it }
            out[Tense.CONDITIONAL] = CONDITIONAL.map { lemma + it }
        }
        IRREGULAR[lemma]?.let { out.putAll(it) }
        return out
    }

    /** Verbs in -air (sair), -uir (incluir) and -ear (passear) follow their own patterns. */
    private fun specialForms(lemma: String, stem: String): Map<Tense, List<String>> = when {
        (lemma.endsWith("air") || lemma.endsWith("uir")) && !lemma.endsWith("guir") && !lemma.endsWith("quir") -> {
            val i = if (lemma.endsWith("air")) "i" else ""
            mapOf(
                Tense.PRESENT to t(stem + i + "o", stem + "is", stem + "i", stem + "ímos", stem + "em"),
                Tense.PRETERITE to t(stem + "í", stem + "íste", stem + "iu", stem + "ímos", stem + "íram"),
                Tense.IMPERFECT to t(stem + "ía", stem + "ías", stem + "ía", stem + "íamos", stem + "íam"),
            )
        }

        lemma.endsWith("ear") -> mapOf(
            Tense.PRESENT to t(stem + "io", stem + "ias", stem + "ia", stem + "amos", stem + "iam"),
        )

        else -> emptyMap()
    }

    /** Spelling changes that keep the stem's sound: fiquei, paguei, comecei, conheço, protejo, distingo. */
    private fun spell(stem: String, cls: String, tense: Tense, person: Int, ending: String): String {
        val preteriteAr = person == S1 && tense == Tense.PRETERITE && cls == "ar"
        val presentErIr = person == S1 && tense == Tense.PRESENT && cls != "ar"
        val changed =
            when {
                preteriteAr && stem.endsWith("c") -> stem.dropLast(1) + "qu"
                preteriteAr && stem.endsWith("g") -> stem + "u"
                preteriteAr && stem.endsWith("ç") -> stem.dropLast(1) + "c"
                presentErIr && stem.endsWith("c") -> stem.dropLast(1) + "ç"
                presentErIr && stem.endsWith("g") -> stem.dropLast(1) + "j"
                presentErIr && stem.endsWith("gu") && cls == "ir" -> stem.dropLast(1)
                else -> stem
            }
        return changed + ending
    }

    /** Ter's compounds take an acute accent: ele mantém, tu manténs. */
    private val TER_ACCENTS = mapOf("tem" to "tém", "tens" to "téns")

    private fun compound(prefix: String, base: String): Map<Tense, List<String>> = forms(base).mapValues { (_, list) ->
        list.map { f -> prefix + (if (base == "ter") TER_ACCENTS[f] ?: f else f) }
    }

    /**
     * Possible analyses of [word] as a verb form whose infinitive [isWord]
     * knows: "falam" -> (falar, PRESENT, P3). Empty when it is not a verb form.
     */
    fun parse(word: String, isWord: (String) -> Boolean): List<Parse> {
        IRREGULAR_INDEX[word]?.let { return it }
        val found = LinkedHashSet<Parse>()
        val tried = HashSet<String>()
        for (k in 1..minOf(MAX_ENDING, word.length - 1)) {
            val stem = word.dropLast(k)
            for (lemma in candidateLemmas(stem)) {
                if (!tried.add(lemma) || isIrregular(lemma) || !isWord(lemma)) continue
                for ((tense, forms) in forms(lemma)) {
                    forms.forEachIndexed { person, f -> if (f == word) found += Parse(lemma, tense, person) }
                }
            }
        }
        return found.toList()
    }

    private fun candidateLemmas(stem: String): List<String> {
        val stems = LinkedHashSet<String>()
        stems += stem
        if (stem.endsWith("qu")) stems += stem.dropLast(2) + "c"
        if (stem.endsWith("gu")) stems += stem.dropLast(1)
        if (stem.endsWith("ç")) stems += stem.dropLast(1) + "c"
        if (stem.endsWith("c")) stems += stem.dropLast(1) + "ç"
        if (stem.endsWith("j")) stems += stem.dropLast(1) + "g"
        if (stem.endsWith("i")) stems += stem.dropLast(1)
        if (stem.endsWith("g")) stems += stem + "u"
        val out = ArrayList<String>()
        for (s in stems) {
            if (s.length < MIN_STEM) continue
            out += s + "ar"
            out += s + "er"
            out += s + "ir"
        }
        // Future and conditional: the stem is the infinitive itself.
        if (stem.length > MIN_STEM && (stem.endsWith("ar") || stem.endsWith("er") || stem.endsWith("ir"))) out += stem
        return out
    }

    private const val MAX_ENDING = 6
    private const val MIN_STEM = 1
}
