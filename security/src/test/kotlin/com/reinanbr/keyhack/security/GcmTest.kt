package com.reinanbr.keyhack.security

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GcmTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun roundTrips() {
        val plain = "olá clipboard".toByteArray()
        val blob = Gcm.encrypt(key, plain)
        assertFalse(blob.copyOfRange(Gcm.IV_BYTES, blob.size).contentEquals(plain))
        assertContentEquals(plain, Gcm.decrypt(key, blob))
    }

    @Test
    fun sameTextEncryptsDifferently() {
        val plain = "same".toByteArray()
        assertFalse(Gcm.encrypt(key, plain).contentEquals(Gcm.encrypt(key, plain)))
    }

    @Test
    fun tamperingIsDetected() {
        val blob = Gcm.encrypt(key, "secret".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertFailsWith<GeneralSecurityException> { Gcm.decrypt(key, blob) }
    }

    @Test
    fun shortBlobIsRejected() {
        assertFailsWith<GeneralSecurityException> { Gcm.decrypt(key, ByteArray(5)) }
    }
}
