package com.reinanbr.keyhack.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small blobs at rest (clipboard history, snippets). */
interface Crypter {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws [GeneralSecurityException] when [blob] was not made by this key or was altered. */
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a key that never leaves the Android Keystore (it cannot be
 * exported, and it is deleted with the app's data). Output is the 12-byte IV
 * followed by the ciphertext and its tag, so any change to the file is
 * detected on decrypt.
 */
class KeystoreCrypter(private val alias: String) : Crypter {
    private val key: SecretKey by lazy { loadOrCreate() }

    private fun loadOrCreate(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray = Gcm.encrypt(key, plain)

    override fun decrypt(blob: ByteArray): ByteArray = Gcm.decrypt(key, blob)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_BITS = 256
    }
}

/** The AES-GCM framing shared by [KeystoreCrypter] and tests (which use a plain JCE key). */
object Gcm {
    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore picks a random IV itself (randomized encryption is required).
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        if (iv.size != IV_BYTES) throw GeneralSecurityException("unexpected IV size ${iv.size}")
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw GeneralSecurityException("blob too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }
}
