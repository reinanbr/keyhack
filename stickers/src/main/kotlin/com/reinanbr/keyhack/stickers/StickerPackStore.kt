package com.reinanbr.keyhack.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * The process-wide sticker pack list, shared by the settings screen (which
 * edits it), the keyboard (whose deletions it follows) and
 * [WhatsAppStickerProvider] (which serves it to WhatsApp on binder threads).
 * Every method is synchronized and may touch the disk: call off the main
 * thread where it says so.
 */
class StickerPackStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, FILE)

    /** Imported stickers (StickerStore's directory) and generated tray icons. */
    val stickerDir = File(app.filesDir, StickerStore.DIR)
    val trayDir = File(app.filesDir, TRAY_DIR)

    private val list = StickerPackList()
    private var loaded = false

    @Synchronized
    fun all(): List<StickerPack> = load().all()

    @Synchronized
    operator fun get(id: String): StickerPack? = load()[id]

    /**
     * A pack the provider may hand to WhatsApp: within the rules, and every
     * file still there. Null otherwise.
     */
    @Synchronized
    fun servable(id: String): StickerPack? = load()[id]?.takeIf(::isServable)

    @Synchronized
    fun servable(): List<StickerPack> = load().all().filter(::isServable)

    /** Creates a pack. Blocking (draws the tray icon). Null when it breaks a rule or a file is gone. */
    @Synchronized
    fun create(name: String, stickers: List<String>, emoji: String): StickerPack? {
        val l = load()
        if (!allExist(stickers)) return null
        var id = System.currentTimeMillis().toString(ID_RADIX)
        while (l[id] != null) id += "0"
        return l.create(id, name, stickers, emoji)?.also(::saved)
    }

    /** Changes a pack. Blocking (draws the tray icon). */
    @Synchronized
    fun update(id: String, name: String, stickers: List<String>, emoji: String): StickerPack? {
        if (!allExist(stickers)) return null
        return load().update(id, name, stickers, emoji)?.also(::saved)
    }

    @Synchronized
    fun remove(id: String) {
        if (load().remove(id)) {
            dropTrays(id)
            save()
        }
    }

    /** [name] was deleted from the library. Blocking when a pack's first sticker changes. */
    @Synchronized
    fun removeSticker(name: String) {
        val l = load()
        val before = l.all().map { it.id }.toSet()
        val changed = l.removeSticker(name)
        if (changed.isEmpty() && l.all().size == before.size) return
        (before - l.all().map { it.id }.toSet()).forEach(::dropTrays)
        changed.forEach { runCatching { writeTray(it) } }
        save()
    }

    private fun isServable(p: StickerPack): Boolean =
        StickerPackList.problems(p.name, p.stickers, p.emoji.joinToString(" ")).isEmpty() &&
            allExist(p.stickers) &&
            File(trayDir, p.trayFile).isFile

    private fun allExist(stickers: List<String>) = stickers.all { File(stickerDir, it).isFile }

    private fun saved(pack: StickerPack) {
        // Without its tray icon the pack is kept but not served (isServable).
        runCatching { writeTray(pack) }
        save()
    }

    private fun load(): StickerPackList {
        if (!loaded) {
            runCatching { list.decode(file.readText()) }
            loaded = true
        }
        return list
    }

    private fun save() {
        val tmp = File(file.path + ".tmp")
        runCatching {
            tmp.writeText(list.encode())
            if (!tmp.renameTo(file)) tmp.delete()
        }.onFailure { tmp.delete() }
    }

    /** WhatsApp's tray icon: the pack's first sticker at 96x96 PNG (WhatsApp: at most 50 KB). */
    private fun writeTray(pack: StickerPack) {
        dropTrays(pack.id)
        val first = File(stickerDir, pack.stickers.first())
        // BitmapFactory decodes the first frame of an animated WebP.
        val src = BitmapFactory.decodeFile(first.path) ?: throw IOException("cannot read sticker")
        val tray = Bitmap.createScaledBitmap(src, TRAY_SIZE, TRAY_SIZE, true)
        val out = ByteArrayOutputStream()
        tray.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        if (tray !== src) tray.recycle()
        src.recycle()
        trayDir.mkdirs()
        val dest = File(trayDir, pack.trayFile)
        val tmp = File(dest.path + ".tmp")
        tmp.writeBytes(out.toByteArray())
        if (!tmp.renameTo(dest)) throw IOException("cannot save tray icon")
    }

    private fun dropTrays(id: String) {
        trayDir.listFiles { f -> f.name.startsWith("tray_${id}_") }?.forEach { it.delete() }
    }

    companion object {
        private const val FILE = "sticker_packs.json"
        private const val TRAY_DIR = "sticker_trays"
        const val TRAY_SIZE = 96
        private const val PNG_QUALITY = 100
        private const val ID_RADIX = 36

        @Volatile private var instance: StickerPackStore? = null

        fun get(context: Context): StickerPackStore = instance ?: synchronized(this) {
            instance ?: StickerPackStore(context).also { instance = it }
        }
    }
}
