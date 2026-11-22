package com.reinanbr.keyhack.suggest

import java.text.Normalizer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlideTest {
    private val qwerty =
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").flatMapIndexed { r, row ->
            row.mapIndexed { i, c -> c to ((i + 0.5f + r * 0.5f) to (r + 0.5f)) }
        }.toMap()

    private fun engine(vararg langs: String, history: UserHistory = UserHistory()) =
        SuggestionEngine(langs.map { SuggestionEngineTest.shipped.getValue(it) }, history, EditCosts(qwerty)).also {
            it.glideDecoder.centers = qwerty
        }

    /**
     * A finger path for [word]: through its keys, landing up to [noise] key
     * widths off each center, corners rounded (Chaikin) the way a moving
     * finger cuts them, sampled densely like touch events.
     */
    private fun path(word: String, noise: Float = 0.2f, seed: Int = word.hashCode()): GlidePath {
        val rnd = Random(seed)
        val letters = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD).filter { it in qwerty }
        var pts = letters.map { c ->
            val (x, y) = qwerty.getValue(c)
            (x + (rnd.nextFloat() * 2 - 1) * noise) to (y + (rnd.nextFloat() * 2 - 1) * noise / ASPECT)
        }
        pts = roundCorners(pts)
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (i in 0 until pts.size - 1) {
            val (ax, ay) = pts[i]
            val (bx, by) = pts[i + 1]
            val steps = 6
            for (s in 0 until steps) {
                xs += ax + (bx - ax) * s / steps
                ys += ay + (by - ay) * s / steps
            }
        }
        xs += pts.last().first
        ys += pts.last().second
        return GlidePath(xs.toFloatArray(), ys.toFloatArray(), ASPECT)
    }

    /** Each turn becomes a curve that starts and ends [CORNER] key widths from the key: fingers cut corners. */
    private fun roundCorners(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (pts.size < 3) return pts
        val out = ArrayList<Pair<Float, Float>>()
        out += pts.first()
        for (i in 1 until pts.size - 1) {
            val (px, py) = pts[i - 1]
            val (cx, cy) = pts[i]
            val (nx, ny) = pts[i + 1]
            val a = toward(cx, cy, px, py)
            val b = toward(cx, cy, nx, ny)
            for (s in 0..4) {
                val u = s / 4f
                // Quadratic Bézier a -> b with the key center as control point.
                val x = (1 - u) * (1 - u) * a.first + 2 * u * (1 - u) * cx + u * u * b.first
                val y = (1 - u) * (1 - u) * a.second + 2 * u * (1 - u) * cy + u * u * b.second
                out += x to y
            }
        }
        out += pts.last()
        return out
    }

    private fun toward(x: Float, y: Float, tx: Float, ty: Float): Pair<Float, Float> {
        val d = kotlin.math.hypot(tx - x, ty - y)
        if (d == 0f) return x to y
        val r = minOf(CORNER, d / 3)
        return (x + (tx - x) / d * r) to (y + (ty - y) / d * r)
    }

    private fun top(e: SuggestionEngine, word: String, noise: Float = 0.2f, previous: String? = null) =
        e.glide(path(word, noise), previous).map { it.word }

    private fun accuracy(e: SuggestionEngine, words: List<String>, noise: Float): Pair<Float, List<String>> {
        val misses = ArrayList<String>()
        var hits = 0
        for (w in words) {
            val got = top(e, w, noise)
            if (got.firstOrNull()?.lowercase() == w.lowercase()) hits++ else misses += "$w -> ${got.take(3)}"
        }
        return hits.toFloat() / words.size to misses
    }

    @Test
    fun commonEnglishWords() {
        val e = engine("en")
        val (acc, misses) = accuracy(e, EN, 0.2f)
        println("en top-1 ${acc * 100}%: $misses")
        assertTrue(acc >= MIN_ACCURACY, "en top-1 $acc: $misses")
    }

    @Test
    fun commonPortugueseWords() {
        val e = engine("pt", "en")
        val (acc, misses) = accuracy(e, PT, 0.2f)
        println("pt top-1 ${acc * 100}%: $misses")
        assertTrue(acc >= MIN_ACCURACY, "pt top-1 $acc: $misses")
    }

    @Test
    fun sloppyGlidesStillOfferTheWord() {
        val e = engine("pt", "en")
        val words = EN + PT
        val misses = words.filter { w -> top(e, w, SLOPPY).take(3).none { it.lowercase() == w.lowercase() } }
        val top1 = words.count { w -> top(e, w, SLOPPY).firstOrNull()?.lowercase() == w.lowercase() }
        println("sloppy: top-1 $top1/${words.size}, not in top 3: $misses")
        assertTrue(misses.size <= words.size / 10, misses.toString())
    }

    @Test
    fun accentsAndApostrophesComeFromTheDictionary() {
        val e = engine("pt", "en")
        assertEquals("você", top(e, "voce").first())
        assertEquals("não", top(e, "nao").first())
        assertEquals("don't", engine("en").glide(path("dont"), null).first().word)
    }

    @Test
    fun alternativesAreOffered() {
        val got = top(engine("en"), "good")
        assertTrue(got.size >= 2, got.toString())
    }

    @Test
    fun learnedWordsAreGlidable() {
        val h = UserHistory()
        repeat(UserHistory.VALID_COUNT + 2) { h.learn("kkkkk", null) }
        repeat(UserHistory.VALID_COUNT + 2) { h.learn("rafinha", null) }
        assertEquals("rafinha", engine("pt", history = h).glide(path("rafinha", 0.1f), null).first().word)
    }

    @Test
    fun tapIsNotAGlide() {
        val tap = GlidePath(floatArrayOf(3.5f, 3.55f), floatArrayOf(0.5f, 0.52f), ASPECT)
        assertEquals(emptyList(), engine("en").glide(tap, null))
    }

    @Test
    fun fastEnough() {
        val e = engine("pt", "en")
        e.glide(path("trabalho"), null)
        val start = System.nanoTime()
        for (w in PT.take(20)) e.glide(path(w), null)
        val ms = (System.nanoTime() - start) / 1_000_000 / 20
        println("glide decode: $ms ms per word")
        assertTrue(ms < 150, "$ms ms per word")
    }

    private companion object {
        const val ASPECT = 1.4f
        const val CORNER = 0.5f
        const val SLOPPY = 0.35f
        const val MIN_ACCURACY = 0.85f
        val EN = listOf(
            "hello", "the", "world", "keyboard", "thanks", "you", "good", "morning", "people", "because",
            "about", "would", "there", "their", "what", "know", "time", "should", "really", "something",
            "tomorrow", "great", "love", "work", "home", "please", "sorry", "night", "today", "think",
        )
        val PT = listOf(
            "olá", "você", "obrigado", "amanhã", "trabalho", "casa", "não", "também", "coisa", "pessoa",
            "quando", "porque", "tudo", "bem", "agora", "depois", "hoje", "gente", "muito", "ainda",
            "falar", "fazer", "semana", "dinheiro", "amigo", "verdade", "sempre", "nada", "obrigada", "beijo",
        )
    }
}
