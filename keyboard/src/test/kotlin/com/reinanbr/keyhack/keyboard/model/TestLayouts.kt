package com.reinanbr.keyhack.keyboard.model

import java.io.File

/** Loads the shipped layout JSON from the :layouts module for JVM tests. */
object TestLayouts {
    val dir = File("../layouts/src/main/assets/layouts")
    val common: LayoutCommon by lazy { LayoutParser.parseCommon(File(dir, "common.json").readText()) }

    fun load(id: String): KeyboardLayout = LayoutParser.parse(File(dir, "$id.json").readText(), common)

    fun all(): Map<String, KeyboardLayout> = dir
        .listFiles { f -> f.extension == "json" && f.name != "common.json" }
        .orEmpty()
        .associate { it.nameWithoutExtension to LayoutParser.parse(it.readText(), common) }
}
