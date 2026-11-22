package com.reinanbr.keyhack.suggest.grammar

import com.reinanbr.keyhack.suggest.SuggestionEngineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.reinanbr.keyhack.suggest.grammar.PortugueseVerbs as Verbs

class AgreementTest {
    private val agreement = cached

    private fun person(text: String) = agreement.personAfter(text.split(' '))

    @Test
    fun findsTheSubjectBeforeTheVerb() {
        assertEquals(Verbs.P1, person("nós"))
        assertEquals(Verbs.P3, person("eles não me"))
        assertEquals(Verbs.S3, person("a gente não se"))
        assertEquals(Verbs.S1, person("amanhã eu também não"))
        assertEquals(Verbs.S2, person("tu"))
    }

    @Test
    fun noSubjectNoPerson() {
        assertNull(person("falei com eles"))
        assertNull(person("para ele"))
        assertNull(person("nos"))
        assertNull(person("boa noite"))
        // Too many words in between: the pronoun is probably not this verb's subject.
        assertNull(person("eles não me se te"))
    }

    @Test
    fun fitsVerbsToThePerson() {
        assertEquals(Agreement.Fit.Becomes("vamos"), agreement.fit("vai", Verbs.P1))
        assertEquals(Agreement.Fit.Becomes("têm"), agreement.fit("tem", Verbs.P3))
        assertEquals(Agreement.Fit.Becomes("estou"), agreement.fit("está", Verbs.S1))
        assertEquals(Agreement.Fit.Becomes("falam"), agreement.fit("fala", Verbs.P3))
        assertEquals(Agreement.Fit.Agrees, agreement.fit("estamos", Verbs.P1))
        // "tu vai" is how most of Brazil speaks.
        assertEquals(Agreement.Fit.Agrees, agreement.fit("vai", Verbs.S2))
    }

    @Test
    fun fitsSubjunctives() {
        fun fit(w: String, person: Int) = agreement.fit(w, person, subjunctive = true)
        assertEquals(Agreement.Fit.Becomes("enviem"), fit("envie", Verbs.P3))
        assertEquals(Agreement.Fit.Becomes("quisermos"), fit("quiser", Verbs.P1))
        assertEquals(Agreement.Fit.Becomes("soubessem"), fit("soubesse", Verbs.P3))
        assertEquals(Agreement.Fit.Becomes("façam"), fit("faça", Verbs.P3))
        assertEquals(Agreement.Fit.Agrees, fit("fale", Verbs.S1))
        assertEquals(Agreement.Fit.Agrees, fit("soubéssemos", Verbs.P1))
        // "fala" is falar's, not the subjunctive of falir; infinitives are not read as subjunctives.
        assertEquals(Agreement.Fit.Becomes("falo"), fit("fala", Verbs.S1))
        assertEquals(Agreement.Fit.NotVerb, fit("falar", Verbs.P3))
        // Suggestions leave the subjunctive out: "for" after "nós" stays, "fomos" is not pushed down.
        assertEquals(Agreement.Fit.NotVerb, agreement.fit("for", Verbs.P1))
    }

    @Test
    fun cliticsAndShortWordsAreNotVerbs() {
        for (w in listOf("se", "me", "te", "to", "não", "muito", "para", "como", "that")) {
            assertEquals(Agreement.Fit.NotVerb, agreement.fit(w, Verbs.P3), w)
        }
    }

    @Test
    fun strictModeAlsoFixesÉ() {
        assertEquals(Agreement.Fit.Becomes("sou"), agreement.fit("é", Verbs.S1))
        assertEquals(Agreement.Fit.NotVerb, agreement.fit("é", Verbs.S1, strict = false))
        assertEquals(Agreement.Fit.Becomes("somos"), agreement.fit("é", Verbs.P1, strict = false))
    }

    private companion object {
        val cached: Agreement by lazy {
            val dict = SuggestionEngineTest.shipped.getValue("pt")
            Agreement { w -> dict.lookup(w).let { if (it < 0) -1 else dict.freqs[it] } }
        }
    }
}
