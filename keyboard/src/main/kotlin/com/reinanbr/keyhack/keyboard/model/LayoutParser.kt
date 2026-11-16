package com.reinanbr.keyhack.keyboard.model

import android.view.KeyEvent
import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.array
import com.reinanbr.keyhack.core.jsonArray
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.number
import com.reinanbr.keyhack.core.obj
import com.reinanbr.keyhack.core.string

/**
 * Shared definitions used by every layout file: named rows ("@alpha-bottom")
 * and the long-press table for letters.
 */
class LayoutCommon(
    val rows: Map<String, KeyRow> = emptyMap(),
    val popups: Map<String, List<String>> = emptyMap(),
    /** Per letter row, top to bottom: the symbol each key gets (see [KeyboardLayout.withSymbolHints]). */
    val symbolHints: List<List<String>> = emptyList(),
)

/**
 * Parses KeyHack's declarative layout JSON (see layouts/README.md).
 *
 * A row is an array of keys, an object {"keys": [...], "h": 0.8}, or a
 * reference "@name" to a row in common.json. A key is either a string (a
 * character key) or an object:
 *   t     text committed            s    shifted text (default: uppercase)
 *   code  function key (see [KeyCodes]), e.g. "shift", "ctrl", "f5", "layer:symbols"
 *   l     label override            w    width in key units (default 1)
 *   pop   long-press alternatives (string = one per code point, or array)
 *   hint  small corner label        lp   long-press action (a code) or text
 */
object LayoutParser {
    class LayoutException(message: String) : IllegalArgumentException(message)

    fun parseCommon(json: String): LayoutCommon {
        val root = Json.parse(json).jsonObject() ?: invalid("common: not an object")
        val popups =
            root.obj("popups").orEmpty().mapValues { (k, v) -> popupList(v) ?: invalid("popup $k") }
        val partial = LayoutCommon(popups = popups)
        val rows = root.obj("rows").orEmpty().mapValues { (name, v) -> parseRow(v, partial, "common.$name") }
        val symbolHints =
            root.array("symbolHints").orEmpty().map { popupList(it) ?: invalid("common.symbolHints") }
        return LayoutCommon(rows, popups, symbolHints)
    }

    fun parse(json: String, common: LayoutCommon = LayoutCommon()): KeyboardLayout {
        val root = Json.parse(json).jsonObject() ?: invalid("layout: not an object")
        val id = root.string("id") ?: invalid("layout: missing id")
        val rows = root.array("rows") ?: invalid("$id: missing rows")
        if (rows.isEmpty()) invalid("$id: no rows")
        return try {
            KeyboardLayout(id, rows.mapIndexed { i, r -> parseRow(r, common, "$id.row$i") })
        } catch (e: IllegalArgumentException) {
            throw e as? LayoutException ?: LayoutException("$id: ${e.message}")
        }
    }

    private fun parseRow(spec: Any?, common: LayoutCommon, where: String): KeyRow {
        if (spec is String) {
            if (!spec.startsWith("@")) invalid("$where: bad row reference '$spec'")
            return common.rows[spec.substring(1)] ?: invalid("$where: unknown row $spec")
        }
        val obj = spec.jsonObject()
        val keys = (obj?.array("keys") ?: spec.jsonArray()) ?: invalid("$where: bad row")
        if (keys.isEmpty()) invalid("$where: empty row")
        val height = obj?.number("h")?.toFloat() ?: 1f
        if (height <= 0f) invalid("$where: bad row height")
        return KeyRow(keys.mapIndexed { i, k -> parseKey(k, common, "$where.key$i") }, height)
    }

    fun parseKey(spec: Any?, common: LayoutCommon, where: String = "key"): Key {
        if (spec is String) {
            if (spec.isEmpty()) invalid("$where: empty key")
            return Key(spec, KeyAction.Character(spec), popup = common.popups[spec].orEmpty())
        }
        val o = spec.jsonObject() ?: invalid("$where: bad key")
        val width = o.number("w")?.toFloat() ?: 1f
        if (width <= 0f) invalid("$where: bad width")
        val hint = o.string("hint")
        val longPress = o.string("lp")?.let { KeyCodes.action(it) ?: KeyAction.Character(it) }
        val code = o.string("code")
        if (code != null) {
            val action = KeyCodes.action(code) ?: invalid("$where: unknown code '$code'")
            val label = o.string("l") ?: KeyCodes.label(code) ?: invalid("$where: '$code' needs a label")
            return Key(label, action, width, popupList(o["pop"]).orEmpty(), hint, longPress)
        }
        val text = o.string("t") ?: invalid("$where: key needs 't' or 'code'")
        if (text.isEmpty()) invalid("$where: empty text")
        val popup = popupList(o["pop"]) ?: common.popups[text].orEmpty()
        val withHint = if (hint != null && hint !in popup) listOf(hint) + popup else popup
        return Key(text, KeyAction.Character(text, o.string("s")), width, withHint, hint, longPress)
    }

    private fun popupList(v: Any?): List<String>? = when (v) {
        null -> null
        is String -> v.codePoints().toArray().map { String(Character.toChars(it)) }
        is List<*> -> v.map { it as? String ?: invalid("popup entries must be strings") }
        else -> invalid("bad popup")
    }

    /** Parses a space-separated list of codes/characters, e.g. the user's dev row. */
    fun parseKeyList(spec: String, common: LayoutCommon = LayoutCommon()): List<Key> = spec
        .split(' ', ',')
        .filter { it.isNotBlank() }
        .map { token ->
            if (KeyCodes.action(token) != null) parseKey(mapOf("code" to token), common) else parseKey(token, common)
        }
}

/** The function-key vocabulary shared by layout JSON and the configurable dev row. */
object KeyCodes {
    private val codes: Map<String, Pair<String, KeyAction>> =
        buildMap {
            put("shift", "⇧" to KeyAction.Shift)
            put("delete", "⌫" to KeyAction.Delete)
            put("enter", "⏎" to KeyAction.Enter)
            put("space", "" to KeyAction.Space)
            put("ctrl", "Ctrl" to KeyAction.Mod(Modifier.CTRL))
            put("alt", "Alt" to KeyAction.Mod(Modifier.ALT))
            put("meta", "Meta" to KeyAction.Mod(Modifier.META))
            fun code(name: String, label: String, keyCode: Int) = put(name, label to KeyAction.Code(keyCode, name))
            code("esc", "Esc", KeyEvent.KEYCODE_ESCAPE)
            code("tab", "Tab", KeyEvent.KEYCODE_TAB)
            code("left", "←", KeyEvent.KEYCODE_DPAD_LEFT)
            code("right", "→", KeyEvent.KEYCODE_DPAD_RIGHT)
            code("up", "↑", KeyEvent.KEYCODE_DPAD_UP)
            code("down", "↓", KeyEvent.KEYCODE_DPAD_DOWN)
            code("home", "Home", KeyEvent.KEYCODE_MOVE_HOME)
            code("end", "End", KeyEvent.KEYCODE_MOVE_END)
            code("pgup", "PgUp", KeyEvent.KEYCODE_PAGE_UP)
            code("pgdn", "PgDn", KeyEvent.KEYCODE_PAGE_DOWN)
            code("ins", "Ins", KeyEvent.KEYCODE_INSERT)
            code("fdel", "Del", KeyEvent.KEYCODE_FORWARD_DEL)
            code("prtsc", "PrtSc", KeyEvent.KEYCODE_SYSRQ)
            code("pause", "Pause", KeyEvent.KEYCODE_BREAK)
            for (n in 1..F_KEYS) code("f$n", "F$n", KeyEvent.KEYCODE_F1 + n - 1)
            fun cmd(name: String, label: String, c: Command) = put(name, label to KeyAction.Cmd(c))
            cmd("emoji", "☺", Command.EMOJI)
            cmd("settings", "⚙", Command.SETTINGS)
            cmd("pc", "PC", Command.TOGGLE_PC)
            cmd("nextime", "🌐", Command.NEXT_IME)
            cmd("picker", "🌐", Command.IME_PICKER)
            cmd("tools", "</>", Command.TOOLS)
            cmd("gif", "GIF", Command.GIF)
            cmd("clipboard", "📋", Command.CLIPBOARD)
        }

    fun action(code: String): KeyAction? {
        val layer = code.removePrefix(LAYER_PREFIX)
        if (layer != code && layer.isNotEmpty()) return KeyAction.Layer(layer)
        return codes[code]?.second
    }

    /** Default label, or null when the code needs an explicit one (layers). */
    fun label(code: String): String? = codes[code]?.first

    val names: Set<String> get() = codes.keys

    private const val F_KEYS = 12
    private const val LAYER_PREFIX = "layer:"
}

private fun invalid(message: String): Nothing = throw LayoutParser.LayoutException(message)
