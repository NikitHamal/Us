package com.us.copilot.data.local.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encrypted preferences backed by the Android Keystore.
 * The only place API keys and the database passphrase are ever written.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun remove(vararg keys: String) {
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        private const val FILE_NAME = "us_secure_settings"

        const val KEY_DB_PASSPHRASE = "db_passphrase"
        const val KEY_BASE_URL = "cloud_base_url"
        const val KEY_API_KEY = "cloud_api_key"
        const val KEY_MODEL_NAME = "cloud_model_name"
        const val KEY_EMBEDDING_MODEL = "cloud_embedding_model"
        const val KEY_NEBIANS_API_KEY = "nebians_api_key"
        const val KEY_NEBIANS_BASE_URL = "nebians_base_url"
    }
}
