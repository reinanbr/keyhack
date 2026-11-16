package com.reinanbr.keyhack.suggest

import com.reinanbr.keyhack.suggest.SuggestionEngineTest.Companion.shipped
import com.reinanbr.keyhack.suggest.SuggestionEngineTest.Companion.shippedNgrams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two-word context, conversation models and chat words. */
class ContextTest {
    private fun engine(vararg langs: String, history: UserHistory = UserHistory()) = SuggestionEngine(
        langs.map { shipped.getValue(it) },
        history,
        ngrams = langs.map { shippedNgrams.getValue(it) },
    )

    private fun predicted(e: SuggestionEngine, previous: String, before: String? = null, limit: Int = 3) =
        e.predict(previous, before, limit).map { it.word }

    @Test
    fun parsesRows() {
        val m = NgramModel.read("# header\neu\tnão:190 sei:120\neu te\tamo:184\n".reader().buffered())
        assertEquals(listOf("não" to 190, "sei" to 120), m.after("Eu"))
        assertEquals(listOf("amo" to 184), m.afterPair("eu", "te"))
        assertEquals(184, m.score("eu", "te", "amo"))
        assertEquals(120, m.score(null, "eu", "sei"))
        assertEquals(0, m.score(null, "eu", "gato"))
    }

    @Test
    fun predictsConversationContinuations() {
        val e = engine("pt", "en")
        assertEquals("amo", predicted(e, "te", before = "eu").first())
        assertTrue("sei" in predicted(e, "não", before = "eu"))
        assertTrue("noite" in predicted(e, "boa"), predicted(e, "boa").toString())
        assertTrue("you" in predicted(e, "are", before = "how"))
        // Pronoun "I" keeps its capital.
        assertTrue(
            "I" in predicted(engine("en"), "and", limit = 6),
            predicted(engine("en"), "and", limit = 6).toString(),
        )
    }

    @Test
    fun twoWordContextPicksTheLanguage() {
        // "eu não" is Portuguese: English followers of "não"-less contexts must not lead.
        val e = engine("en", "pt")
        val words = predicted(e, "não", before = "eu")
        assertTrue(words.all { shipped.getValue("pt").lookup(it.lowercase()) >= 0 }, words.toString())
    }

    @Test
    fun oneWordContextPrefersItsLanguage() {
        // "boa" is common Portuguese and rare English ("boa constrictor").
        for (e in listOf(engine("en", "pt"), engine("pt", "en"))) {
            val words = predicted(e, "boa")
            assertFalse("constrictor" in words, words.toString())
            assertTrue("ideia" in words || "noite" in words, words.toString())
        }
        // Words common in both languages keep both models.
        assertTrue("you" in predicted(engine("pt", "en"), "are"))
    }

    @Test
    fun userHabitsComeFirst() {
        val h = UserHistory()
        repeat(2) { h.learn("pizza", "boa") }
        assertEquals("pizza", predicted(engine("pt", history = h), "boa").first())
    }

    @Test
    fun contextRanksCandidatesWhileTyping() {
        val e = engine("pt")
        val words = e.suggest("am", "te", before = "eu").candidates.drop(1).map { it.word }
        assertEquals("amo", words.first(), words.toString())
    }

    @Test
    fun chatWordsAreKept() {
        val pt = engine("pt")
        for (w in listOf("vc", "kkk", "blz", "vlw", "hj", "obg")) {
            assertNull(pt.suggest(w, null).autoCorrection, w)
            assertTrue(pt.suggest(w, null).typedIsWord, w)
        }
        val en = engine("en")
        for (w in listOf("lol", "idk", "btw", "gonna")) assertNull(en.suggest(w, null).autoCorrection, w)
    }

    @Test
    fun modelsHoldNoTatoebaNames() {
        for (m in shippedNgrams.values) {
            for (name in listOf("tom", "mary", "boston")) {
                assertTrue(m.after(name).isEmpty(), name)
                assertFalse(m.after("with").any { it.first == name }, name)
            }
        }
    }
}
