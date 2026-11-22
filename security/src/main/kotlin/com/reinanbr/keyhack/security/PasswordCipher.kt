package com.reinanbr.keyhack.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts data with a password, for files that leave the app (backups).
 *
 * The key is PBKDF2-HMAC-SHA256 of the password and a random salt; the data is
 * sealed with AES-256-GCM. Layout:
 *
 *     "KHBK" | version (1 byte) | iterations (4 bytes, big endian) | salt (16) | IV (12) | ciphertext + tag
 *
 * Everything before the IV is authenticated as associated data, so a changed
 * header fails like a wrong password. Nothing is parsed from the plaintext
 * until the tag checks out.
 */
object PasswordCipher {
    const val ITERATIONS = 600_000

    /** Files asking for fewer rounds are refused (weak), and for more (a way to hang the app). */
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 5_000_000
    const val MIN_PASSWORD = 8

    private val MAGIC = byteArrayOf('K'.code.toByte(), 'H'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val HEADER = 4 + 1 + 4 + SALT_BYTES

    /** The file is not one of ours, or not a version this build reads. */
    class NotEncrypted : GeneralSecurityException("not a KeyHack encrypted file")

    /** Wrong password, or the file was changed or cut short. */
    class WrongPassword : GeneralSecurityException("wrong password or damaged file")

    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations out of range" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header =
            ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(iterations).put(salt).array()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + iv + cipher.doFinal(plain)
    }

    /** Throws [NotEncrypted] or [WrongPassword]. */
    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(blob) || blob.size < HEADER + IV_BYTES + TAG_BITS / Byte.SIZE_BITS) throw NotEncrypted()
        val buf = ByteBuffer.wrap(blob)
        buf.position(MAGIC.size)
        if (buf.get() != VERSION) throw NotEncrypted()
        val iterations = buf.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw NotEncrypted()
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(password, salt, iterations),
            GCMParameterSpec(TAG_BITS, blob, HEADER, IV_BYTES),
        )
        cipher.updateAAD(blob, 0, HEADER)
        return try {
            cipher.doFinal(blob, HEADER + IV_BYTES, blob.size - HEADER - IV_BYTES)
        } catch (_: AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
}
