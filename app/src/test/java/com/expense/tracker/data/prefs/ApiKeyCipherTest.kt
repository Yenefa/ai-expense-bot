package com.expense.tracker.data.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator

class ApiKeyCipherTest {
    private fun cipher(): ApiKeyCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return ApiKeyCipher { key }
    }

    @Test fun encryptDecryptRoundTripDoesNotExposePlaintext() {
        val cipher = cipher()
        val plaintext = "sk-private-example-123"

        val encrypted = cipher.encrypt(plaintext)

        assertThat(encrypted.iv).isNotEmpty()
        assertThat(encrypted.ciphertext).isNotEmpty()
        assertThat(encrypted.ciphertext.toString(Charsets.ISO_8859_1)).doesNotContain(plaintext)
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext)
    }

    @Test fun repeatedEncryptionUsesDifferentRandomIvAndCiphertext() {
        val cipher = cipher()

        val first = cipher.encrypt("same-secret")
        val second = cipher.encrypt("same-secret")

        assertThat(first.iv.toList()).isNotEqualTo(second.iv.toList())
        assertThat(first.ciphertext.toList()).isNotEqualTo(second.ciphertext.toList())
    }

    @Test fun tamperedCiphertextIsRejected() {
        val cipher = cipher()
        val encrypted = cipher.encrypt("sk-secret")
        val tampered = encrypted.copy(ciphertext = encrypted.ciphertext.clone().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        })

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(tampered)
        }
    }
}
