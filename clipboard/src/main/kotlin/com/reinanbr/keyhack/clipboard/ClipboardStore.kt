package com.reinanbr.keyhack.clipboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.reinanbr.keyhack.security.Crypter
import com.reinanbr.keyhack.security.KeystoreCrypter
import java.io.File
import java.util.concurrent.Executors

/**
 * The process-wide clipboard history: the keyboard adds to it, the settings
 * screen clears it. State lives on the main thread; the file is written on a
 * background thread, encrypted with a Keystore key ([KeystoreCrypter]), in
 * noBackupFilesDir (never backed up or transferred).
 */
class ClipboardStore private constructor(context: Context, private val crypter: Crypter) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "keyhack-clipboard") }
    private val main = Handler(Looper.getMainLooper())
    private val history = ClipboardHistory()
    private val listeners = LinkedHashSet<() -> Unit>()
    private var loaded = false
    private var wiped = false

    init {
        io.execute {
            val bytes = runCatching { crypter.decrypt(file.readBytes()) }.getOrNull()
            main.post {
                // Anything copied before the file was read stays on top; a wipe meanwhile wins.
                val early = history.items(now())
                if (!wiped) bytes?.let(history::decode)
                for (item in early.reversed()) history.add(item.text, item.time)
                loaded = true
                changed(save = early.isNotEmpty())
            }
        }
    }

    var expiryMs: Long
        get() = history.expiryMs
        set(value) {
            history.expiryMs = value
        }

    fun items(): List<ClipItem> = history.items(now())

    fun latest(withinMs: Long): ClipItem? = history.latest(now(), withinMs)

    fun add(text: String) {
        if (history.add(text, now())) changed()
    }

    fun setPinned(id: Long, pinned: Boolean) {
        history.setPinned(id, pinned)
        changed()
    }

    fun remove(id: Long) {
        history.remove(id)
        changed()
    }

    fun clearUnpinned() {
        history.clearUnpinned()
        changed()
    }

    /** Forgets everything, pinned items included, and deletes the file (history turned off). */
    fun wipe() {
        wiped = !loaded
        history.clearAll()
        listeners.forEach { it() }
        io.execute { file.delete() }
    }

    /** Called on the main thread whenever the list changes. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun changed(save: Boolean = true) {
        listeners.forEach { it() }
        if (!save || !loaded) return
        val bytes = if (history.isEmpty) null else history.encode()
        io.execute { write(bytes) }
    }

    private fun write(bytes: ByteArray?) {
        if (bytes == null) {
            file.delete()
            return
        }
        val tmp = File(file.path + ".tmp")
        runCatching {
            tmp.writeBytes(crypter.encrypt(bytes))
            if (!tmp.renameTo(file)) tmp.delete()
        }.onFailure { tmp.delete() }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val FILE = "clipboard.bin"
        private const val KEY_ALIAS = "keyhack_clipboard"

        @Volatile private var instance: ClipboardStore? = null

        /** Main thread only. */
        fun get(context: Context): ClipboardStore =
            instance ?: ClipboardStore(context, KeystoreCrypter(KEY_ALIAS)).also { instance = it }
    }
}
