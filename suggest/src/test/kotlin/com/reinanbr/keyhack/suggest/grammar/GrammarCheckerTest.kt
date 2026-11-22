package com.reinanbr.keyhack.suggest.grammar

import com.reinanbr.keyhack.suggest.SuggestionEngineTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrammarCheckerTest {
    private val checker = cached

    private fun fix(text: String): String? = checker.check(text)?.let { it.original + " -> " + it.replacement }

    @Test
    fun rulesFilesParse() {
        // grammar() parses strictly: a bad line fails here, not silently on the phone.
        assertEquals(2, listOf(grammar("pt"), grammar("en")).count { it.rules.isNotEmpty() })
    }

    @Test
    fun portugueseVerbAgreement() {
        assertEquals("nós vai -> nós vamos", fix("amanhã nós vai "))
        assertEquals("eles fala -> eles falam", fix("eles fala "))
        assertEquals("a gente vamos -> a gente vai", fix("a gente vamos "))
        assertEquals("eles não sabe -> eles não sabem", fix("eles não sabe "))
        assertEquals("Eu vai -> Eu vou", fix("Eu vai "))
        assertEquals("elas foi -> elas foram", fix("elas foi."))
        assertEquals("você estavam -> você estava", fix("você estavam "))
        assertEquals("nós ficou -> nós ficamos", fix("nós ficou "))
        assertEquals("eles tem -> eles têm", fix("eles tem "))
        assertEquals("eles vem -> eles vêm", fix("eles vem "))
    }

    @Test
    fun portugueseSubjunctiveAgreement() {
        assertEquals("eles não me envie -> eles não me enviem", fix("pedi que eles não me envie "))
        assertEquals("eles quiser -> eles quiserem", fix("se eles quiser "))
        assertEquals("nós soubesse -> nós soubéssemos", fix("se nós soubesse "))
        assertEquals("vocês faça -> vocês façam", fix("tomara que vocês faça "))
        assertEquals("eles for -> eles forem", fix("quando eles for "))
        for (ok in listOf(
            "que eles enviem ", "se eles quiserem ", "se nós soubéssemos ", "que eu fale ", "se eu for ",
            "quando eles saírem ", "que nós passeemos ", "eles falar ", "que você seja ", "que eles deem ",
            "se vocês virem ", "ele sente ", "eu sento ", "ele vende ", "eu leve ", "eu vim ", "ele fala ",
        )) {
            assertNull(fix(ok), ok)
        }
    }

    @Test
    fun correctAgreementIsLeftAlone() {
        for (ok in listOf(
            "nós vamos ", "eles falam ", "a gente vai ", "eu te amo ", "tu vai ", "tu vais ", "ele disse ",
            "eu é que sei ", "eu e ele vamos ", "falei com eles ", "para ele ", "nós saímos ", "eles incluem ",
            "eu acho ", "eles mesmos ", "vocês sabem ", "eu como ", "eu passeio ", "ele passeia ",
            "eles conseguem ", "eu fiquei ", "eu conheço ", "eles se ", "você me ", "eles me ", "eles te ",
        )) {
            assertNull(fix(ok), ok)
        }
    }

    @Test
    fun portuguesePlural() {
        assertEquals("os menino -> os meninos", fix("vi os menino "))
        assertEquals("as coisa -> as coisas", fix("as coisa "))
        assertEquals("dois real -> dois reais", fix("custou dois real "))
        assertEquals("minhas amiga -> minhas amigas", fix("minhas amiga "))
        assertEquals("os animal -> os animais", fix("os animal "))
        assertEquals("três mês -> três meses", fix("três mês "))
        assertEquals("os homem -> os homens", fix("os homem "))
        assertEquals("as mulher -> as mulheres", fix("as mulher "))
        assertEquals("os papel -> os papéis", fix("os papel "))
    }

    @Test
    fun pluralLeavesPronounsAndVerbsAlone() {
        for (ok in listOf("eu os vi ", "não as quero ", "os lápis ", "as pessoas ", "uns tem ", "se os fazer ")) {
            assertNull(fix(ok), ok)
        }
    }

    @Test
    fun portugueseRules() {
        assertEquals("a muito tempo -> há muito tempo", fix("eu não te vejo a muito tempo "))
        assertEquals("A anos -> Há anos", fix("A anos "))
        assertNull(fix("daqui a dois anos "))
        assertEquals("fazem dois anos -> faz dois anos", fix("fazem dois anos "))
        assertEquals("houveram muitos -> houve muitos", fix("houveram muitos "))
        assertEquals("pra mim fazer -> pra eu fazer", fix("é pra mim fazer "))
        assertEquals("se eu ver -> se eu vir", fix("se eu ver "))
        assertEquals("quando eu ter -> quando eu tiver", fix("quando eu ter "))
        assertEquals("se nós poder -> se nós pudermos", fix("se nós poder "))
        assertEquals("à pé -> a pé", fix("fui à pé "))
        assertEquals("à partir -> a partir", fix("à partir "))
        assertEquals("As vezes -> Às vezes", fix("As vezes "))
        assertNull(fix("todas as vezes "))
        assertEquals("agente vai -> a gente vai", fix("agente vai "))
        assertEquals("ao meu ver -> a meu ver", fix("ao meu ver "))
        assertEquals("mal humor -> mau humor", fix("que mal humor "))
        assertEquals("estou afim -> estou a fim", fix("estou afim "))
        assertEquals(", mais não -> , mas não", fix("eu queria, mais não "))
        assertEquals("vende-se casas -> vendem-se casas", fix("vende-se casas "))
    }

    @Test
    fun porqueInQuestions() {
        assertEquals(
            "Porque você não veio -> Por que você não veio",
            fix("Porque você não veio?"),
        )
        assertEquals("porque -> por quê", fix("você não veio, porque?"))
        assertNull(fix("não fui porque choveu."))
    }

    @Test
    fun englishRules() {
        assertEquals("he don't -> he doesn't", fix("he don't "))
        assertEquals("could of -> could have", fix("I could of "))
        assertEquals("they was -> they were", fix("they was "))
        assertEquals("your welcome -> you're welcome", fix("your welcome "))
        assertEquals("its a -> it's a", fix("its a "))
        assertEquals("better then -> better than", fix("better then "))
        assertEquals("she have -> she has", fix("she have "))
        assertNull(fix("did she have "))
    }

    @Test
    fun englishArticles() {
        assertEquals("a apple -> an apple", fix("I want a apple "))
        assertEquals("an book -> a book", fix("an book "))
        assertEquals("a hour -> an hour", fix("in a hour "))
        assertNull(fix("a university "))
        assertNull(fix("an hour "))
        assertNull(fix("a European "))
        assertNull(fix("an FBI "))
    }

    @Test
    fun portugueseTextGetsNoEnglishArticleFix() {
        assertNull(fix("a ilha "))
        assertNull(fix("vou a escola "))
        assertNull(fix("a uma "))
    }

    @Test
    fun onlyAfterAWordIsFinished() {
        assertNull(fix("nós vai"))
        assertNull(fix(""))
        assertEquals(" ", checker.check("nós vai ")?.tail)
    }

    @Test
    fun onlyTheCurrentSentence() {
        assertNull(fix("Eles vieram. Vai "))
    }

    private companion object {
        private fun grammar(lang: String): LanguageGrammar {
            // The dictionary as the app loads it (with chat words, extra words and misspellings).
            val dict = SuggestionEngineTest.shipped.getValue(lang)
            val rules = File("src/main/assets/grammar/$lang.rules").bufferedReader().use {
                GrammarRules.parse(it, strict = true)
            }
            return LanguageGrammar(lang, { w -> dict.lookup(w).let { if (it < 0) -1 else dict.freqs[it] } }, rules)
        }

        val cached: GrammarChecker by lazy { GrammarChecker(listOf(grammar("pt"), grammar("en"))) }
    }
}
