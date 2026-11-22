package com.reinanbr.keyhack.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordCipherTest {
    // The minimum keeps the tests fast; the real default is ITERATIONS.
    private val rounds = PasswordCipher.MIN_ITERATIONS
    private val plain = "configurações e palavras".toByteArray()

    @Test
    fun roundTrips() {
        val blob = PasswordCipher.encrypt(plain, "senha forte".toCharArray(), rounds)
        assertTrue(PasswordCipher.isEncrypted(blob))
        assertContentEquals(plain, PasswordCipher.decrypt(blob, "senha forte".toCharArray()))
    }

    @Test
    fun saltAndIvAreFresh() {
        val a = PasswordCipher.encrypt(plain, "senha forte".toCharArray(), rounds)
        val b = PasswordCipher.encrypt(plain, "senha forte".toCharArray(), rounds)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun wrongPasswordFails() {
        val blob = PasswordCipher.encrypt(plain, "senha forte".toCharArray(), rounds)
        assertFailsWith<PasswordCipher.WrongPassword> { PasswordCipher.decrypt(blob, "senha fraca".toCharArray()) }
    }

    @Test
    fun anyChangedByteFails() {
        val blob = PasswordCipher.encrypt(plain, "senha forte".toCharArray(), rounds)
        // Salt, IV, ciphertext and tag: each change is caught by the tag.
        for (i in listOf(10, 25, 30, blob.size - 1)) {
            val bad = blob.copyOf().also { it[i] = (it[i] + 1).toByte() }
            assertFailsWith<PasswordCipher.WrongPassword>("byte $i") {
                PasswordCipher.decrypt(bad, "senha forte".toCharArray())
            }
        }
        assertFailsWith<PasswordCipher.WrongPassword> {
            PasswordCipher.decrypt(blob.copyOf(blob.size - 1), "senha forte".toCharArray())
        }
    }

    @Test
    fun refusesForeignFilesAndHostileHeaders() {
        assertFalse(PasswordCipher.isEncrypted("{\"a\":1}".toByteArray()))
        assertFailsWith<PasswordCipher.NotEncrypted> { PasswordCipher.decrypt(ByteArray(64), "x".toCharArray()) }
        val blob = PasswordCipher.encrypt(plain, "senha forte".toCharArray(), rounds)
        // Iteration counts outside the range are refused before any key is derived.
        val slow = blob.copyOf().also { it[5] = 0x7f }
        assertFailsWith<PasswordCipher.NotEncrypted> { PasswordCipher.decrypt(slow, "senha forte".toCharArray()) }
        val newer = blob.copyOf().also { it[4] = 2 }
        assertFailsWith<PasswordCipher.NotEncrypted> { PasswordCipher.decrypt(newer, "senha forte".toCharArray()) }
    }
}
