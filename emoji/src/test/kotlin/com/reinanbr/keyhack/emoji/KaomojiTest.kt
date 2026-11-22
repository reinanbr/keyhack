package com.reinanbr.keyhack.emoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaomojiTest {
    @Test
    fun everyMoodHasItsOwnFaces() {
        val faces = Kaomoji.faces
        assertTrue(faces.size >= 200, "${faces.size} faces")
        val twice = faces.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(emptySet(), twice, "a face is in one mood only")
        for (c in Kaomoji.categories) {
            assertTrue(c.faces.size >= 15, c.id)
            assertTrue(c.faces.none { it.isBlank() || '\n' in it }, c.id)
        }
        assertEquals(Kaomoji.categories.size, Kaomoji.categories.map { it.id }.toSet().size)
        assertEquals(Kaomoji.categories.size, Kaomoji.categories.map { it.label }.toSet().size)
    }
}
