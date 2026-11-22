package com.reinanbr.keyhack.ime

import kotlin.test.Test
import kotlin.test.assertEquals

class CommandPaletteTest {
    private val entries =
        listOf(
            PaletteEntry("clip", "📋 Área de transferência", "clipboard copy paste"),
            PaletteEntry("b64e", "Base64 ↑", "encode"),
            PaletteEntry("b64d", "Base64 ↓", "decode"),
            PaletteEntry("snake", "snake_case"),
            PaletteEntry("all", "Selecionar tudo", "select all"),
        )

    private fun ids(q: String) = CommandPalette.rank(q, entries, 3).map { it.id }

    @Test
    fun prefixFirstThenWordsThenKeywords() {
        assertEquals(listOf("b64e", "b64d"), ids("base"))
        assertEquals(listOf("clip"), ids("transf"))
        assertEquals(listOf("clip"), ids("clipboard"))
        assertEquals(listOf("all"), ids("tudo"))
    }

    @Test
    fun ignoresCaseAccentsAndLeadingSymbols() {
        assertEquals(listOf("clip"), ids("AREA"))
        assertEquals(listOf("snake"), ids("case"))
    }

    @Test
    fun emptyQueryListsTheFirstOnes() {
        assertEquals(listOf("clip", "b64e", "b64d"), ids(" "))
    }

    @Test
    fun noMatch() {
        assertEquals(emptyList(), ids("zzz"))
    }
}
