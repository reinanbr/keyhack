package com.reinanbr.keyhack.ime

import android.content.res.AssetManager
import com.reinanbr.keyhack.keyboard.model.KeyRow
import com.reinanbr.keyhack.keyboard.model.KeyboardLayout
import com.reinanbr.keyhack.keyboard.model.LayoutCommon
import com.reinanbr.keyhack.keyboard.model.LayoutParser

/** Loads and caches the JSON layouts shipped in :layouts (assets/layouts). */
class LayoutRepository(private val assets: AssetManager) {
    private val cache = HashMap<String, KeyboardLayout>()

    val common: LayoutCommon by lazy {
        runCatching { LayoutParser.parseCommon(read("common")) }.getOrDefault(LayoutCommon())
    }

    private fun read(id: String): String = assets.open("layouts/$id.json").bufferedReader().use { it.readText() }

    /** Layout [id], falling back to QWERTY if it is missing or invalid. */
    fun get(id: String): KeyboardLayout = cache.getOrPut(id) {
        runCatching { LayoutParser.parse(read(id), common) }.getOrNull()
            ?: if (id != FALLBACK) get(FALLBACK) else error("built-in layout $FALLBACK is broken")
    }

    fun row(name: String): KeyRow? = common.rows[name]

    /** The user's dev row ("esc tab ctrl ..."), or null if it does not parse. */
    fun devRow(spec: String): KeyRow? = runCatching {
        KeyRow(LayoutParser.parseKeyList(spec, common), DEV_ROW_HEIGHT)
    }.getOrNull()?.takeIf { it.keys.isNotEmpty() }

    private companion object {
        const val FALLBACK = "qwerty"
        const val DEV_ROW_HEIGHT = 0.8f
    }
}
