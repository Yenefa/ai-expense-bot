package com.expense.tracker.data.prefs

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyStorage {
    fun read(): String
    fun write(value: String)
}

internal data class EncryptedApiKey(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

internal class ApiKeyCipher(
    private val keyProvider: () -> SecretKey,
) {
    fun encrypt(plaintext: String): EncryptedApiKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        return EncryptedApiKey(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
        )
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(value: EncryptedApiKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, value.iv),
        )
        return cipher.doFinal(value.ciphertext).toString(Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
