package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyGeometryTest {
    private val qwerty = TestLayouts.load("qwerty")
    private val g = KeyGeometry(qwerty, width = 1000f, height = 400f, gap = 10f)

    private fun keyAt(x: Float, y: Float) = g.keys[g.keyIndexAt(x, y)].key

    @Test
    fun placesEveryKey() {
        assertEquals(qwerty.rows.sumOf { it.keys.size }, g.keys.size)
    }

    @Test
    fun hitsKeyCenters() {
        for (placed in g.keys) {
            assertEquals(placed.key, keyAt(placed.bounds.centerX, placed.bounds.centerY))
        }
    }

    @Test
    fun gapsResolveToAKey() {
        // x = 100 is the boundary between q and w in the top row.
        assertEquals("w", keyAt(100f, 50f).label)
        assertEquals("q", keyAt(99.9f, 50f).label)
    }

    @Test
    fun centeredRowEdgesStillHit() {
        // Row 2 (asdfghjkl) is 9 units wide and centered; its edges stretch.
        assertEquals("a", keyAt(1f, 150f).label)
        assertEquals("l", keyAt(999f, 150f).label)
    }

    @Test
    fun outOfBoundsTouchesClamp() {
        assertEquals("q", keyAt(-50f, -50f).label)
        assertEquals("⏎", keyAt(5000f, 5000f).label)
    }

    @Test
    fun shorterRowsGetLessHeight() {
        val short = KeyboardLayout("t", listOf(KeyRow(listOf(Key("1", KeyAction.Character("1"))), 0.5f)) + qwerty.rows)
        val sg = KeyGeometry(short, width = 1000f, height = 450f, gap = 0f)
        assertEquals(50f, sg.keys.first().bounds.bottom)
        assertEquals("1", sg.keys[sg.keyIndexAt(500f, 49f)].key.label)
        assertEquals("q", sg.keys[sg.keyIndexAt(5f, 51f)].key.label)
    }

    @Test
    fun visibleBoundsAreInsetByHalfGap() {
        val q = g.keys.first()
        assertEquals(5f, q.bounds.left)
        assertEquals(95f, q.bounds.right)
    }
}
