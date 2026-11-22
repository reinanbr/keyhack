package com.reinanbr.keyhack.backup

import android.content.Context
import com.reinanbr.keyhack.data.KeyHackSettings
import com.reinanbr.keyhack.emoji.EmojiStore
import com.reinanbr.keyhack.suggest.LearnedFiles
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Manual backup and restore (ADR 0007): settings, learned words, snippets and
 * emoji recents, in one file encrypted with the user's password.
 *
 * Snippets live in a main-thread store, so the caller reads and writes them
 * (SnippetStore.encode / replaceAll); everything here runs off the main
 * thread, since key derivation takes a second or two.
 */
object Backups {
    /** What a restore changed. */
    class Restored(val settings: Int, val words: Int)

    /** Writes the encrypted backup to [out]. [snippets] is SnippetStore.encode(). */
    fun export(context: Context, snippets: ByteArray, password: CharArray, appVersion: String, out: OutputStream) {
        val settings = KeyHackSettings(context)
        val backup =
            Backup(
                settings = SettingsSchema.filter(settings.prefs.all),
                emoji = EmojiStore(context).snapshot(),
                learned = LearnedFiles.read(context),
                snippets = snippets,
                created = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            )
        out.write(BackupFormat.seal(backup, password, appVersion))
        out.flush()
    }

    /**
     * Reads and decrypts a file the user picked: untrusted, so size-capped and
     * checked. Throws [BackupFormat.NotABackup] or
     * [com.reinanbr.keyhack.security.PasswordCipher.WrongPassword].
     */
    fun read(input: InputStream, password: CharArray): Backup {
        val blob = BackupFormat.readCapped(input)
        return BackupFormat.open(blob, password)
    }

    /**
     * Applies everything but the snippets. Learned words are staged for the
     * keyboard, which swaps them in on its own thread when the
     * [KeyHackSettings.LEARNING_RESTORED] signal arrives (or at its next start).
     */
    fun restore(context: Context, backup: Backup): Restored {
        val words = backup.learned?.let { LearnedFiles.stage(context, it) } ?: 0
        backup.emoji?.let { EmojiStore(context).restore(it) }
        val settings = backup.settings
        val prefs = KeyHackSettings(context).prefs
        prefs.edit().apply {
            if (settings != null) {
                // Keys absent from the file were at their defaults when it was made.
                SettingsSchema.rules.keys.forEach(::remove)
                for ((key, value) in settings) {
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
            }
            if (backup.learned != null) putLong(KeyHackSettings.LEARNING_RESTORED, System.currentTimeMillis())
        }.apply()
        return Restored(settings?.size ?: 0, words)
    }
}
