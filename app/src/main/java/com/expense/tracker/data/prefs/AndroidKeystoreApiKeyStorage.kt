package com.expense.tracker.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidKeystoreApiKeyStorage(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    preferencesName: String = DEFAULT_PREFERENCES_NAME,
) : ApiKeyStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val cipher = ApiKeyCipher(::getOrCreateKey)

    @Synchronized
    override fun read(): String {
        val ivBase64 = preferences.getString(KEY_IV, null)
        val ciphertextBase64 = preferences.getString(KEY_CIPHERTEXT, null)
        if (ivBase64 == null && ciphertextBase64 == null) return ""
        if (ivBase64 == null || ciphertextBase64 == null) {
            throw GeneralSecurityException("API Key 密文不完整")
        }
        val encrypted = try {
            EncryptedApiKey(
                iv = Base64.decode(ivBase64, Base64.NO_WRAP),
                ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP),
            )
        } catch (error: IllegalArgumentException) {
            throw GeneralSecurityException("API Key 密文格式无效", error)
        }
        return cipher.decrypt(encrypted)
    }

    @Synchronized
    override fun write(value: String) {
        val saved = if (value.isEmpty()) {
            preferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).commit()
        } else {
            val encrypted = cipher.encrypt(value)
            preferences.edit()
                .putString(KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP))
                .commit()
        }
        if (!saved) throw IllegalStateException("无法保存 API Key")
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "y_e_cost_api_key_aes_v1"
        const val DEFAULT_PREFERENCES_NAME = "secure_api_key"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
    }
}
