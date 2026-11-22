package com.reinanbr.keyhack.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipboardHistoryTest {
    private val hour = ClipboardHistory.HOUR_MS

    @Test
    fun newestFirstAndDeduplicated() {
        val h = ClipboardHistory()
        h.add("a", 1)
        h.add("b", 2)
        h.add("a", 3)
        assertEquals(listOf("a", "b"), h.items(4).map { it.text })
    }

    @Test
    fun ignoresBlankAndHugeText() {
        val h = ClipboardHistory()
        assertFalse(h.add("   ", 1))
        assertFalse(h.add("x".repeat(ClipboardHistory.MAX_CHARS + 1), 1))
        assertTrue(h.items(2).isEmpty())
    }

    @Test
    fun unpinnedItemsExpirePinnedStay() {
        val h = ClipboardHistory(expiryMs = hour)
        h.add("old", 0)
        h.add("keep", 0)
        h.setPinned(h.items(0).first { it.text == "keep" }.id, true)
        h.add("new", hour)
        assertEquals(listOf("keep", "new"), h.items(hour + 1).map { it.text })
    }

    @Test
    fun pinnedComeFirstAndSurviveClear() {
        val h = ClipboardHistory()
        h.add("p", 1)
        h.add("q", 2)
        h.setPinned(h.items(3).last().id, true)
        assertEquals(listOf("p", "q"), h.items(3).map { it.text })
        h.clearUnpinned()
        assertEquals(listOf("p"), h.items(4).map { it.text })
    }

    @Test
    fun copyingAPinnedTextAgainKeepsItPinned() {
        val h = ClipboardHistory()
        h.add("p", 1)
        h.setPinned(h.items(1).single().id, true)
        h.add("p", 2)
        assertTrue(h.items(3).single().pinned)
    }

    @Test
    fun keepsAtMostMaxUnpinned() {
        val h = ClipboardHistory()
        repeat(ClipboardHistory.MAX_UNPINNED + 5) { h.add("t$it", it.toLong()) }
        val items = h.items(100)
        assertEquals(ClipboardHistory.MAX_UNPINNED, items.size)
        assertEquals("t${ClipboardHistory.MAX_UNPINNED + 4}", items.first().text)
    }

    @Test
    fun latestOnlyWhenFresh() {
        val h = ClipboardHistory()
        h.add("x", 1000)
        assertEquals("x", h.latest(1500, 1000)?.text)
        assertNull(h.latest(5000, 1000))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val h = ClipboardHistory()
        h.add("línea \"1\"\nnext", 10)
        h.add("pin", 20)
        h.setPinned(h.items(20).first { it.text == "pin" }.id, true)
        val copy = ClipboardHistory()
        copy.decode(h.encode())
        assertEquals(h.items(30).map { it.text to it.pinned }, copy.items(30).map { it.text to it.pinned })
    }

    @Test
    fun decodeToleratesGarbage() {
        val h = ClipboardHistory()
        h.decode("not json".toByteArray())
        assertTrue(h.isEmpty)
        h.decode("""{"items":[{"t":5},{"t":"ok","at":1},{"at":2}]}""".toByteArray())
        assertEquals(listOf("ok"), h.items(2).map { it.text })
    }

    @Test
    fun removeDeletesOne() {
        val h = ClipboardHistory()
        h.add("a", 1)
        h.add("b", 2)
        h.remove(h.items(2).first().id)
        assertEquals(listOf("a"), h.items(3).map { it.text })
    }
}
