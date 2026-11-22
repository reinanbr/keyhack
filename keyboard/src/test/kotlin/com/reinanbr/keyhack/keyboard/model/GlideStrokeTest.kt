package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlideStrokeTest {
    private val w = 100f

    @Test
    fun staysATapWhileOnTheKey() {
        val s = GlideStroke(1, 50f, 50f, w)
        assertFalse(s.add(70f, 60f, 1, true))
        assertFalse(s.glideOnRelease(null))
    }

    @Test
    fun twoOtherLettersMakeAGlide() {
        val s = GlideStroke(1, 50f, 50f, w)
        assertFalse(s.add(120f, 50f, 2, true))
        assertTrue(s.add(220f, 50f, 3, true))
        assertTrue(s.gliding)
    }

    @Test
    fun sidewaysToTheNextLetterIsAGlide() {
        val s = GlideStroke(1, 50f, 50f, w)
        assertFalse(s.add(100f, 50f, 2, true))
        assertTrue(s.add(140f, 50f, 2, true))
    }

    @Test
    fun shortSwipeUpStaysASymbol() {
        val s = GlideStroke(5, 150f, 150f, w)
        s.add(155f, 110f, 5, true)
        s.add(160f, 80f, 1, true)
        assertFalse(s.gliding)
        // Released near the edge of the key above: the corner symbol.
        assertFalse(s.glideOnRelease(55f))
    }

    @Test
    fun swipeUpEndingOnTheKeyAboveIsAGlide() {
        val s = GlideStroke(5, 150f, 150f, w)
        s.add(170f, 60f, 1, true)
        assertTrue(s.glideOnRelease(20f))
    }

    @Test
    fun otherKeysDoNotCount() {
        val s = GlideStroke(1, 50f, 50f, w)
        s.add(150f, 50f, 9, false)
        s.add(250f, 50f, 10, false)
        assertFalse(s.gliding)
    }

    @Test
    fun keepsThePath() {
        val s = GlideStroke(1, 0f, 0f, w)
        repeat(200) { s.add(it * 3f, 0f, 1, true) }
        assertTrue(s.size > 100)
    }
}
