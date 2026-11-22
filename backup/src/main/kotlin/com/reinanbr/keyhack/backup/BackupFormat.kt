package com.reinanbr.keyhack.backup

import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.array
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.number
import com.reinanbr.keyhack.core.obj
import com.reinanbr.keyhack.core.string
import com.reinanbr.keyhack.emoji.EmojiStore
import com.reinanbr.keyhack.security.PasswordCipher
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * What a backup holds. A part that is null was not in the file and is left
 * alone on restore; a part that is present replaces what the app has.
 */
class Backup(
    /** Allowed settings only ([SettingsSchema]). */
    val settings: Map<String, Any>?,
    val emoji: EmojiStore.State?,
    /** Learned words in UserHistory's text form; checked again when staged. */
    val learned: String?,
    /** Snippets as SnippetList encodes them; checked again when decoded. */
    val snippets: ByteArray?,
    val created: String? = null,
)

/**
 * The backup file: a JSON document, gzipped, sealed with [PasswordCipher].
 * Reading treats the file as untrusted even after the password checks out:
 * sizes are capped, unknown fields are ignored and every value is checked.
 */
object BackupFormat {
    const val FORMAT = "keyhack-backup"
    const val VERSION = 1

    /** Far above any real backup (learned words are capped at about 2 MB). */
    const val MAX_FILE = 16 * 1024 * 1024
    const val MAX_JSON = 32 * 1024 * 1024
    private const val MAX_DEPTH = 8

    /** Not a KeyHack backup, a newer format, or damaged inside. */
    class NotABackup(message: String) : IOException(message)

    fun seal(backup: Backup, password: CharArray, appVersion: String, iterations: Int = PasswordCipher.ITERATIONS) =
        PasswordCipher.encrypt(gzip(encode(backup, appVersion)), password, iterations)

    /** Throws [NotABackup], [PasswordCipher.WrongPassword], or IOException for a file over [MAX_FILE]. */
    fun open(blob: ByteArray, password: CharArray): Backup {
        if (!PasswordCipher.isEncrypted(blob)) throw NotABackup("not a KeyHack backup")
        val plain =
            try {
                PasswordCipher.decrypt(blob, password)
            } catch (_: PasswordCipher.NotEncrypted) {
                throw NotABackup("unsupported backup")
            }
        return decode(gunzip(plain))
    }

    /** Reads at most [MAX_FILE] bytes from [input]; a bigger file is refused, not cut. */
    fun readCapped(input: InputStream, max: Int = MAX_FILE): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(BUFFER)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw NotABackup("file too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun encode(b: Backup, appVersion: String): String = Json.write(
        buildMap {
            put("format", FORMAT)
            put("version", VERSION)
            put("app", appVersion)
            b.created?.let { put("created", it) }
            b.settings?.let { put("settings", SettingsSchema.filter(it)) }
            b.emoji?.sanitized()?.let { put("emoji", mapOf("recents" to it.recents, "tones" to it.tones)) }
            b.learned?.let { put("learned", it) }
            b.snippets?.let { put("snippets", Json.parse(it.toString(Charsets.UTF_8), MAX_DEPTH)) }
        },
    )

    internal fun decode(text: String): Backup {
        val root =
            try {
                Json.parse(text, MAX_DEPTH).jsonObject()
            } catch (e: Json.ParseException) {
                throw NotABackup("bad JSON: ${e.message}")
            } ?: throw NotABackup("not an object")
        if (root.string("format") != FORMAT) throw NotABackup("not a KeyHack backup")
        val version = root.number("version") ?: throw NotABackup("no version")
        if (version != VERSION.toDouble()) throw NotABackup("backup version $version is not supported")
        return Backup(
            settings = root.obj("settings")?.let(SettingsSchema::filter),
            emoji = root.obj("emoji")?.let(::emoji),
            learned = root.string("learned"),
            snippets = root.obj("snippets")?.let { Json.write(it).toByteArray(Charsets.UTF_8) },
            created = root.string("created")?.take(MAX_DATE),
        )
    }

    private fun emoji(o: Map<String, Any?>) = EmojiStore.State(
        o.array("recents").orEmpty().filterIsInstance<String>(),
        o.obj("tones").orEmpty().mapNotNull { (k, v) -> (v as? Number)?.toDouble()?.toInt()?.let { k to it } }.toMap(),
    ).sanitized()

    internal fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    /** Inflates at most [MAX_JSON] bytes: a small file cannot expand into a huge one. */
    internal fun gunzip(bytes: ByteArray): String = try {
        GZIPInputStream(bytes.inputStream()).use { readCapped(it, MAX_JSON) }.toString(Charsets.UTF_8)
    } catch (e: IOException) {
        throw e as? NotABackup ?: NotABackup("damaged: ${e.message}")
    }

    private const val BUFFER = 64 * 1024
    private const val MAX_DATE = 40
}
