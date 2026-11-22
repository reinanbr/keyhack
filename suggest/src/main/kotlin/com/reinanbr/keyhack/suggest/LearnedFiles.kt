package com.reinanbr.keyhack.suggest

import android.content.Context
import java.io.File

/**
 * Where learned words live (noBackupFilesDir, never cloud-backed up), and how
 * a restored backup reaches the keyboard.
 *
 * The keyboard owns [LEARNED] and rewrites it from memory, so a restore never
 * writes it directly: it writes [RESTORED], and [Suggester] swaps that in on
 * its own thread, at start or when told to reload ([adopt]).
 */
object LearnedFiles {
    const val LEARNED = "learned.tsv"
    const val RESTORED = "learned.restore.tsv"

    fun learned(context: Context) = File(context.applicationContext.noBackupFilesDir, LEARNED)

    fun restored(context: Context) = File(context.applicationContext.noBackupFilesDir, RESTORED)

    /** The newest learned data: a restore waiting to be picked up, else the keyboard's file. */
    fun read(context: Context): String {
        val f = restored(context).takeIf { it.isFile } ?: learned(context)
        return if (f.isFile) f.readText() else ""
    }

    /**
     * Checks [text] as untrusted (see [UserHistory.read]) and leaves it for the
     * keyboard to load. Returns how many words it holds.
     */
    fun stage(context: Context, text: String): Int {
        val history = UserHistory().apply { read(text.reader().buffered()) }
        val target = restored(context)
        val tmp = File(target.path + ".tmp")
        try {
            tmp.bufferedWriter().use(history::write)
            check(tmp.renameTo(target)) { "rename failed" }
        } finally {
            tmp.delete()
        }
        return history.wordCount
    }

    /** Deletes learned words, including a restore not yet picked up. */
    fun delete(context: Context) {
        restored(context).delete()
        learned(context).delete()
    }

    /** Moves a waiting restore over the learned file; true if there was one. */
    internal fun adopt(learned: File, restored: File): Boolean = restored.isFile && restored.renameTo(learned)
}
