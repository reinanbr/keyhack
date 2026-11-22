package com.reinanbr.keyhack.devtools

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.reinanbr.keyhack.security.Crypter
import com.reinanbr.keyhack.security.KeystoreCrypter
import java.io.File
import java.util.concurrent.Executors

/**
 * The process-wide snippet list, shared by the keyboard and the settings
 * screen. Snippets often hold addresses, documents or keys, so the file is
 * encrypted like the clipboard history and kept out of backups.
 */
class SnippetStore private constructor(context: Context, private val crypter: Crypter) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "keyhack-snippets") }
    private val main = Handler(Looper.getMainLooper())
    private val list = SnippetList()
    private val listeners = LinkedHashSet<() -> Unit>()
    private var loaded = false

    init {
        io.execute {
            val bytes = runCatching { crypter.decrypt(file.readBytes()) }.getOrNull()
            main.post {
                val early = list.all()
                bytes?.let(list::decode)
                for (s in early) list.add(s.trigger, s.text)
                loaded = true
                changed(save = early.isNotEmpty())
            }
        }
    }

    fun all(): List<Snippet> = list.all()

    fun forTrigger(word: String): Snippet? = list.forTrigger(word)

    fun add(trigger: String, text: String): Boolean = list.add(trigger, text).also { if (it) changed() }

    fun update(id: Long, trigger: String, text: String): Boolean =
        list.update(id, trigger, text).also { if (it) changed() }

    fun remove(id: Long) {
        list.remove(id)
        changed()
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun changed(save: Boolean = true) {
        listeners.toList().forEach { it() }
        if (!save || !loaded) return
        val bytes = list.encode()
        io.execute {
            val tmp = File(file.path + ".tmp")
            runCatching {
                tmp.writeBytes(crypter.encrypt(bytes))
                if (!tmp.renameTo(file)) tmp.delete()
            }.onFailure { tmp.delete() }
        }
    }

    companion object {
        private const val FILE = "snippets.bin"
        private const val KEY_ALIAS = "keyhack_snippets"

        @Volatile private var instance: SnippetStore? = null

        /** Main thread only. */
        fun get(context: Context): SnippetStore =
            instance ?: SnippetStore(context, KeystoreCrypter(KEY_ALIAS)).also { instance = it }
    }
}
