package com.reinanbr.keyhack.keyboard.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LayoutParserTest {
    @Test
    fun parsesKeysCodesAndRows() {
        val l =
            LayoutParser.parse(
                """{"id": "t", "rows": [{"h": 0.5, "keys": ["a", {"code": "ctrl", "w": 2}]},
                   [{"t": "1", "s": "!", "lp": "settings"}, {"code": "layer:fn", "l": "Fn"}]]}""",
            )
        assertEquals(0.5f, l.rows[0].height)
        assertEquals(KeyAction.Mod(Modifier.CTRL), l.rows[0].keys[1].action)
        assertEquals(2f, l.rows[0].keys[1].width)
        assertEquals(KeyAction.Character("1", "!"), l.rows[1].keys[0].action)
        assertEquals(KeyAction.Cmd(Command.SETTINGS), l.rows[1].keys[0].longPress)
        assertEquals(KeyAction.Layer("fn"), l.rows[1].keys[1].action)
    }

    @Test
    fun resolvesSharedRows() {
        val common = LayoutParser.parseCommon("""{"rows": {"b": [{"code": "space", "w": 5}]}, "popups": {"e": "éè"}}""")
        val l = LayoutParser.parse("""{"id": "t", "rows": [["e"], "@b"]}""", common)
        assertEquals(listOf("é", "è"), l.rows[0].keys[0].popup)
        assertEquals(KeyAction.Space, l.rows[1].keys[0].action)
    }

    @Test
    fun parsesUserKeyLists() {
        val keys = LayoutParser.parseKeyList("esc tab ctrl left ~")
        assertEquals(listOf("Esc", "Tab", "Ctrl", "←", "~"), keys.map { it.label })
        assertIs<KeyAction.Code>(keys[0].action)
    }

    @Test
    fun rejectsBadLayouts() {
        val bad =
            listOf(
                """{"rows": [["a"]]}""",
                """{"id": "x", "rows": []}""",
                """{"id": "x", "rows": [[]]}""",
                """{"id": "x", "rows": [[{"code": "nope"}]]}""",
                """{"id": "x", "rows": [[{"code": "layer:fn"}]]}""",
                """{"id": "x", "rows": [[{"t": "a", "w": 0}]]}""",
                """{"id": "x", "rows": ["@missing"]}""",
                """{"id": "x", "rows": [[""]]}""",
            )
        for (json in bad) assertFailsWith<LayoutParser.LayoutException>(json) { LayoutParser.parse(json) }
    }
}
