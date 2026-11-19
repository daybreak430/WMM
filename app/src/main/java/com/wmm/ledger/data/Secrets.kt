package com.wmm.ledger.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Secrets(context: Context) {
    private val preferences = context.getSharedPreferences("model_settings", Context.MODE_PRIVATE)

    var provider: Provider
        get() = Provider.entries.firstOrNull { it.name == preferences.getString("provider", null) }
            ?: Provider.DEEPSEEK
        set(value) { preferences.edit().putString("provider", value.name).apply() }

    @Synchronized
    fun databaseKey(allowCreate: Boolean): ByteArray {
        read("ledger_key")?.let { return it }
        // 已有数据库时不补发密钥，避免把无法解密误判成空账本。
        check(allowCreate)
        return ByteArray(32).also {
            SecureRandom().nextBytes(it)
            write("ledger_key", it)
        }
    }

    fun hasKey(provider: Provider): Boolean = preferences.contains("key_${provider.name}")
    fun modelKey(provider: Provider): String? = read("key_${provider.name}")?.toString(Charsets.UTF_8)
    fun saveModelKey(provider: Provider, value: String) = write("key_${provider.name}", value.toByteArray())
    fun removeModelKey(provider: Provider) {
        check(preferences.edit().remove("key_${provider.name}").commit())
    }

    fun hasReportConsent(): Boolean = preferences.getBoolean("report_consent_v2", false)
    fun setReportConsent() { preferences.edit().putBoolean("report_consent_v2", true).apply() }

    internal fun hasQuestionConsent(provider: Provider): Boolean = preferences.getBoolean("question_consent_v1_${provider.name}", false)
    internal fun setQuestionConsent(provider: Provider) { preferences.edit().putBoolean("question_consent_v1_${provider.name}", true).apply() }

    @Synchronized
    private fun write(slot: String, data: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(allowCreate = true))
        cipher.updateAAD(slot.toByteArray())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(data), Base64.NO_WRAP)
        check(preferences.edit().putString(slot, encoded).commit())
    }

    @Synchronized
    private fun read(slot: String): ByteArray? {
        val encoded = preferences.getString(slot, null) ?: return null
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(allowCreate = false), GCMParameterSpec(128, raw, 0, 12))
        cipher.updateAAD(slot.toByteArray())
        return cipher.doFinal(raw, 12, raw.size - 12)
    }

    private fun key(allowCreate: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(allowCreate)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private companion object { const val KEY_ALIAS = "wmm_secrets_v1" }
}
