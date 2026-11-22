package com.reinanbr.keyhack.backup

import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.data.KeyHackSettings
import com.reinanbr.keyhack.emoji.EmojiStore
import com.reinanbr.keyhack.security.PasswordCipher
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupFormatTest {
    private val password = "uma senha longa".toCharArray()
    private val rounds = PasswordCipher.MIN_ITERATIONS
    private val k = KeyHackSettings

    private fun seal(b: Backup) = BackupFormat.seal(b, password, "0.3.0", rounds)

    private fun sealJson(json: String) = PasswordCipher.encrypt(BackupFormat.gzip(json), password, rounds)

    @Test
    fun roundTrips() {
        val snippets = """{"v":1,"items":[{"k":"end","t":"Rua das Flores, 12"}]}""".toByteArray()
        val original =
            Backup(
                settings =
                mapOf(
                    k.THEME to "ios",
                    k.NUMBER_ROW to false,
                    k.HEIGHT to 120,
                    k.FLOAT_X to 0.25f,
                    k.LANGUAGES to "pt,en",
                ),
                emoji = EmojiStore.State(listOf("😂", "❤️"), mapOf("👍" to 3)),
                learned = "w\tola\t3\nb\tola\tmundo\t1\n",
                snippets = snippets,
                created = "2026-11-22T15:00:00Z",
            )
        val back = BackupFormat.open(seal(original), password)
        assertEquals(original.settings, back.settings)
        assertEquals(original.emoji, back.emoji)
        assertEquals(original.learned, back.learned)
        assertContentEquals(snippets, back.snippets)
        assertEquals("2026-11-22T15:00:00Z", back.created)
    }

    @Test
    fun missingPartsStayNull() {
        val back = BackupFormat.open(sealJson("""{"format":"keyhack-backup","version":1}"""), password)
        assertNull(back.settings)
        assertNull(back.emoji)
        assertNull(back.learned)
        assertNull(back.snippets)
    }

    @Test
    fun keepsOnlySettingsTheScreenCouldSet() {
        val file =
            mapOf(
                "format" to "keyhack-backup",
                "version" to 1,
                "settings" to
                    mapOf(
                        k.GIF_KEY_PREFIX + "giphy" to "stolen",
                        "learning_clear" to 5,
                        "unknown" to true,
                        k.HEIGHT to 9999,
                        k.LONG_PRESS to 300.5,
                        k.THEME to "../../evil",
                        k.LANGUAGES to "pt,xx",
                        k.NUMBER_ROW to "yes",
                        k.FLOAT_Y to 7,
                        k.PC_APPS to "x".repeat(5000),
                        k.MODE to "advanced",
                        k.VIBRATION_MS to 20,
                    ),
            )
        val back = BackupFormat.open(sealJson(Json.write(file)), password)
        assertEquals(mapOf(k.MODE to "advanced", k.VIBRATION_MS to 20), back.settings)
    }

    @Test
    fun exportNeverCarriesTheGifKey() {
        val prefs = mapOf(k.GIF_KEY_PREFIX + "giphy" to "secret", k.THEME to "dark", "learning_clear" to 1L)
        assertEquals(mapOf<String, Any>(k.THEME to "dark"), SettingsSchema.filter(prefs))
    }

    @Test
    fun cleansEmoji() {
        val file =
            """{"format":"keyhack-backup","version":1,"emoji":{"recents":["😂","a\nb",7,"😂"],
               "tones":{"👍":3,"✋":9,"👋":"2"}}}"""
        val emoji = assertNotNull(BackupFormat.open(sealJson(file), password).emoji)
        assertEquals(listOf("😂"), emoji.recents)
        assertEquals(mapOf("👍" to 3), emoji.tones)
    }

    @Test
    fun refusesOtherFiles() {
        val plain = """{"format":"keyhack-backup"}""".toByteArray()
        assertFailsWith<BackupFormat.NotABackup> { BackupFormat.open(plain, password) }
        val other = sealJson("""{"format":"other","version":1}""")
        assertFailsWith<BackupFormat.NotABackup> { BackupFormat.open(other, password) }
        assertFailsWith<BackupFormat.NotABackup> {
            BackupFormat.open(sealJson("""{"format":"keyhack-backup","version":2}"""), password)
        }
        assertFailsWith<BackupFormat.NotABackup> { BackupFormat.open(sealJson("[1,2]"), password) }
        assertFailsWith<BackupFormat.NotABackup> {
            BackupFormat.open(PasswordCipher.encrypt("not gzip".toByteArray(), password, rounds), password)
        }
        assertFailsWith<PasswordCipher.WrongPassword> {
            BackupFormat.open(seal(Backup(null, null, null, null)), "outra senha".toCharArray())
        }
    }

    @Test
    fun capsSizes() {
        assertFailsWith<BackupFormat.NotABackup> { BackupFormat.readCapped(ByteArray(100).inputStream(), max = 99) }
        assertEquals(100, BackupFormat.readCapped(ByteArray(100).inputStream(), max = 100).size)
        // A few kilobytes that would inflate past the limit.
        val bomb =
            ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { z ->
                    val zeros = ByteArray(1 shl 20)
                    repeat(BackupFormat.MAX_JSON / zeros.size + 1) { z.write(zeros) }
                }
            }.toByteArray()
        assertFailsWith<BackupFormat.NotABackup> {
            BackupFormat.open(PasswordCipher.encrypt(bomb, password, rounds), password)
        }
    }
}
