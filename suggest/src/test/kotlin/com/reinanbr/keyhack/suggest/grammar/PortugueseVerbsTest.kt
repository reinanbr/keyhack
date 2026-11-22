package com.reinanbr.keyhack.suggest.grammar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.reinanbr.keyhack.suggest.grammar.PortugueseVerbs as Verbs
import com.reinanbr.keyhack.suggest.grammar.PortugueseVerbs.Tense as T

class PortugueseVerbsTest {
    private fun forms(lemma: String, tense: T) = Verbs.forms(lemma).getValue(tense).joinToString(" ")

    @Test
    fun presentSubjunctive() {
        assertEquals("fale fales fale falemos falem", forms("falar", T.PRESENT_SUBJUNCTIVE))
        assertEquals("coma comas coma comamos comam", forms("comer", T.PRESENT_SUBJUNCTIVE))
        assertEquals("envie envies envie enviemos enviem", forms("enviar", T.PRESENT_SUBJUNCTIVE))
        // Spelling kept from the preterite (fiquei, paguei, comecei) or the present (conheço, protejo).
        assertEquals("fique fiques fique fiquemos fiquem", forms("ficar", T.PRESENT_SUBJUNCTIVE))
        assertEquals("pague pagues pague paguemos paguem", forms("pagar", T.PRESENT_SUBJUNCTIVE))
        assertEquals("comece comeces comece comecemos comecem", forms("começar", T.PRESENT_SUBJUNCTIVE))
        assertEquals("conheça conheças conheça conheçamos conheçam", forms("conhecer", T.PRESENT_SUBJUNCTIVE))
        assertEquals("distinga distingas distinga distingamos distingam", forms("distinguir", T.PRESENT_SUBJUNCTIVE))
        assertEquals("passeie passeies passeie passeemos passeiem", forms("passear", T.PRESENT_SUBJUNCTIVE))
        assertEquals("odeie odeies odeie odiemos odeiem", forms("odiar", T.PRESENT_SUBJUNCTIVE))
        // Irregular "eu" forms carry over to every person.
        assertEquals("faça faças faça façamos façam", forms("fazer", T.PRESENT_SUBJUNCTIVE))
        assertEquals("durma durmas durma durmamos durmam", forms("dormir", T.PRESENT_SUBJUNCTIVE))
        assertEquals("saia saias saia saiamos saiam", forms("sair", T.PRESENT_SUBJUNCTIVE))
        assertEquals("mantenha mantenhas mantenha mantenhamos mantenham", forms("manter", T.PRESENT_SUBJUNCTIVE))
        assertEquals("proponha proponhas proponha proponhamos proponham", forms("propor", T.PRESENT_SUBJUNCTIVE))
        // And the few that follow no rule.
        assertEquals("seja sejas seja sejamos sejam", forms("ser", T.PRESENT_SUBJUNCTIVE))
        assertEquals("vá vás vá vamos vão", forms("ir", T.PRESENT_SUBJUNCTIVE))
        assertEquals("dê dês dê demos deem", forms("dar", T.PRESENT_SUBJUNCTIVE))
    }

    @Test
    fun imperfectSubjunctive() {
        assertEquals("falasse falasses falasse falássemos falassem", forms("falar", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("comesse comesses comesse comêssemos comessem", forms("comer", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("partisse partisses partisse partíssemos partissem", forms("partir", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("fizesse fizesses fizesse fizéssemos fizessem", forms("fazer", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("fosse fosses fosse fôssemos fossem", forms("ser", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("desse desses desse déssemos dessem", forms("dar", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("lesse lesses lesse lêssemos lessem", forms("ler", T.IMPERFECT_SUBJUNCTIVE))
        assertEquals("saísse saísses saísse saíssemos saíssem", forms("sair", T.IMPERFECT_SUBJUNCTIVE))
    }

    @Test
    fun futureSubjunctive() {
        assertEquals("falar falares falar falarmos falarem", forms("falar", T.FUTURE_SUBJUNCTIVE))
        assertEquals("quiser quiseres quiser quisermos quiserem", forms("querer", T.FUTURE_SUBJUNCTIVE))
        assertEquals("for fores for formos forem", forms("ir", T.FUTURE_SUBJUNCTIVE))
        assertEquals("vier vieres vier viermos vierem", forms("vir", T.FUTURE_SUBJUNCTIVE))
        assertEquals("vir vires vir virmos virem", forms("ver", T.FUTURE_SUBJUNCTIVE))
        assertEquals("mantiver mantiveres mantiver mantivermos mantiverem", forms("manter", T.FUTURE_SUBJUNCTIVE))
        assertEquals("sair saíres sair sairmos saírem", forms("sair", T.FUTURE_SUBJUNCTIVE))
        assertEquals("incluir incluíres incluir incluirmos incluírem", forms("incluir", T.FUTURE_SUBJUNCTIVE))
    }

    @Test
    fun parsesSubjunctiveForms() {
        val words = setOf("enviar", "falar", "querer", "saber", "sair")
        val isWord: (String) -> Boolean = { it in words }
        assertEquals(
            setOf(
                Verbs.Parse("enviar", T.PRESENT_SUBJUNCTIVE, Verbs.S1),
                Verbs.Parse("enviar", T.PRESENT_SUBJUNCTIVE, Verbs.S3),
            ),
            Verbs.parse("envie", isWord).toSet(),
        )
        assertEquals(listOf(Verbs.Parse("falar", T.IMPERFECT_SUBJUNCTIVE, Verbs.P1)), Verbs.parse("falássemos", isWord))
        assertEquals(listOf(Verbs.Parse("sair", T.FUTURE_SUBJUNCTIVE, Verbs.P3)), Verbs.parse("saírem", isWord))
        assertTrue(Verbs.Parse("querer", T.FUTURE_SUBJUNCTIVE, Verbs.S3) in Verbs.parse("quiser", isWord))
    }

    @Test
    fun infinitivesStayInfinitives() {
        val isWord: (String) -> Boolean = { it == "falar" }
        assertEquals(emptyList(), Verbs.parse("falar", isWord))
        // "vir" is ver's future subjunctive, but read as the infinitive of vir.
        assertTrue(Verbs.parse("vir") { false }.none { it.tense == T.FUTURE_SUBJUNCTIVE })
        // Subjunctive forms that double as nouns do not count as irregular forms for plural fixes.
        assertFalse(Verbs.isIrregularForm("peça"))
        assertTrue(Verbs.isIrregularForm("fiz"))
    }
}
