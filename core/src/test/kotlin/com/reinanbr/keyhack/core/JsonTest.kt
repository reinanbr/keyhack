package com.reinanbr.keyhack.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JsonTest {
    @Test
    fun parsesNestedDocument() {
        val v = Json.parse("""{"a": [1, 2.5, "x\né"], "b": {"c": true, "d": null}}""").jsonObject()!!
        assertEquals(listOf(1.0, 2.5, "x\né"), v.array("a"))
        assertEquals(true, v.obj("b")!!["c"])
        assertNull(v.obj("b")!!["d"])
    }

    @Test
    fun roundTrips() {
        val doc = mapOf("k" to listOf("a\"b", 3, false), "n" to null)
        assertEquals(doc.toString(), (Json.parse(Json.write(doc)) as Map<*, *>).toString().replace("3.0", "3"))
    }

    @Test
    fun rejectsMalformedInput() {
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "\"\u0001\"", "{} x", "[01e]")) {
            assertFailsWith<Json.ParseException>(bad) { Json.parse(bad) }
        }
    }

    @Test
    fun rejectsDeepNesting() {
        assertFailsWith<Json.ParseException> { Json.parse("[".repeat(100) + "]".repeat(100), maxDepth = 64) }
    }
}
