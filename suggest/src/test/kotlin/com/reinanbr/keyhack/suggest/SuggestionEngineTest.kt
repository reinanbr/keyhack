package com.reinanbr.keyhack.suggest

import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionEngineTest {
    private val qwerty =
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").flatMapIndexed { r, row ->
            row.mapIndexed { i, c -> c to (i + 0.5f + r * 0.5f) to (r + 0.5f) }
        }.associate { (cx, y) -> cx.first to (cx.second to y) }

    private fun engine(vararg langs: String, history: UserHistory = UserHistory()) =
        SuggestionEngine(langs.map { shipped.getValue(it) }, history, EditCosts(qwerty))

    @Test
    fun correctsTyposUsingKeyProximity() {
        val e = engine("en")
        assertEquals("the", e.suggest("teh", null).autoCorrection)
        assertEquals("hello", e.suggest("hrllo", null).autoCorrection)
        assertEquals("keyboard", e.suggest("keyboatd", null).autoCorrection)
        assertEquals("because", e.suggest("becuase", null).autoCorrection)
    }

    @Test
    fun keepsValidWords() {
        val e = engine("en")
        for (w in listOf("the", "cat", "hello", "keyboard", "a")) assertNull(e.suggest(w, null).autoCorrection, w)
        assertTrue(e.suggest("cat", null).typedIsWord)
    }

    @Test
    fun restoresPortugueseAccents() {
        val e = engine("pt")
        assertEquals("não", e.suggest("nao", null).autoCorrection)
        assertEquals("você", e.suggest("voce", null).autoCorrection)
        assertEquals("está", e.suggest("esta", null).candidates.map { it.word }.first { it != "esta" })
        assertEquals("música", e.suggest("musica", null).autoCorrection)
        assertEquals("Não", e.suggest("Nao", null).autoCorrection)
    }

    @Test
    fun completesPrefixes() {
        val e = engine("en")
        val words = e.suggest("keyb", null).candidates.map { it.word }
        assertTrue("keyboard" in words, words.toString())
        assertEquals("keyb", words.first())
    }

    @Test
    fun fixesCaseOfCapitalOnlyWords() {
        val e = engine("en")
        assertEquals("I", e.suggest("i", null).autoCorrection)
    }

    @Test
    fun mixesLanguages() {
        val e = engine("pt", "en")
        assertNull(e.suggest("keyboard", null).autoCorrection)
        assertNull(e.suggest("teclado", null).autoCorrection)
        assertEquals("não", e.suggest("nao", null).autoCorrection)
    }

    @Test
    fun learnsWordsAndPairs() {
        val h = UserHistory()
        val e = engine("en", history = h)
        assertTrue(e.suggest("keyhack", null).autoCorrection != null || !e.suggest("keyhack", null).typedIsWord)
        repeat(3) { h.learn("keyhack", "use") }
        assertNull(e.suggest("keyhack", null).autoCorrection)
        assertEquals(listOf("keyhack"), e.predict("use").map { it.word })
        assertTrue("keyhack" in e.suggest("keyh", "use").candidates.map { it.word })
    }

    @Test
    fun usageMovesWordsUpTheSuggestions() {
        val h = UserHistory()
        val e = engine("pt", history = h)
        val before = e.suggest("paralelep", null).candidates.map { it.word }
        repeat(5) { h.learn("paralelepípedo", null) }
        val after = e.suggest("paralelep", null).candidates.map { it.word }
        assertTrue("paralelepípedo" in after, "after=$after before=$before")
        assertTrue(after.indexOf("paralelepípedo") <= before.indexOf("paralelepípedo").let { if (it < 0) 99 else it })
    }

    @Test
    fun newWordTypedOftenStopsBeingCorrected() {
        val h = UserHistory()
        val e = engine("pt", "en", history = h)
        repeat(UserHistory.VALID_COUNT) { h.learn("kubectl", null) }
        assertNull(e.suggest("kubectl", null).autoCorrection)
        assertTrue("kubectl" in e.suggest("kubec", null).candidates.map { it.word })
    }

    @Test
    fun accentsWinOverRareWordsOfTheOtherLanguage() {
        val e = engine("pt", "en")
        assertEquals("você", e.suggest("voce", null).autoCorrection)
        assertEquals("Você", e.suggest("Voce", null).autoCorrection)
        // Common words in either language stay as typed.
        assertNull(e.suggest("ate", null).autoCorrection)
        assertNull(e.suggest("esta", null).autoCorrection)
        assertNull(e.suggest("pais", null).autoCorrection)
    }

    @Test
    fun matchesCase() {
        assertEquals("The", SuggestionEngine.matchCase("Teh", "the"))
        assertEquals("THE", SuggestionEngine.matchCase("TEH", "the"))
        assertEquals("the", SuggestionEngine.matchCase("teh", "the"))
    }

    @Test
    fun suggestsFastEnough() {
        val e = engine("pt", "en")
        e.suggest("warmup", null)
        val start = System.nanoTime()
        for (w in listOf("teh", "keyboatd", "nao", "programaçao", "desenvolvimeto", "a", "xyzzyq")) e.suggest(w, null)
        val perWordMs = (System.nanoTime() - start) / 7 / 1_000_000
        assertTrue(perWordMs < 50, "took $perWordMs ms per word")
    }

    companion object {
        val shipped: Map<String, Dictionary> by lazy {
            listOf("en", "pt").associateWith { lang ->
                GZIPInputStream(File("src/main/assets/dict/$lang.dict.gz").inputStream()).bufferedReader().use {
                    Dictionary.read(lang, it)
                }
            }
        }
    }
}
