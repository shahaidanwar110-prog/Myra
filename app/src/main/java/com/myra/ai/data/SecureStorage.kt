package com.myra.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            context.getSharedPreferences(FALLBACK_PREFS_FILENAME, Context.MODE_PRIVATE)
        }
    }

    fun saveApiKey(provider: String, key: String) {
        sharedPreferences.edit().putString(KEY_PREFIX_API + provider, key).apply()
    }

    fun getApiKey(provider: String): String {
        return sharedPreferences.getString(KEY_PREFIX_API + provider, "") ?: ""
    }

    fun saveGeminiModel(model: String) {
        sharedPreferences.edit().putString(KEY_GEMINI_MODEL, model).apply()
    }

    fun getGeminiModel(): String {
        return sharedPreferences.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)?.ifBlank { DEFAULT_GEMINI_MODEL } ?: DEFAULT_GEMINI_MODEL
    }

    fun saveActiveProvider(provider: String) {
        sharedPreferences.edit().putString(KEY_ACTIVE_PROVIDER, provider).apply()
    }

    fun getActiveProvider(): String {
        return sharedPreferences.getString(KEY_ACTIVE_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
    }

    fun saveLanguage(languageCode: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, "en-US") ?: "en-US"
    }

    companion object {
        private const val PREFS_FILENAME = "myra_secure_prefs"
        private const val FALLBACK_PREFS_FILENAME = "myra_fallback_prefs"
        private const val KEY_PREFIX_API = "api_key_"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_ACTIVE_PROVIDER = "active_ai_provider"
        private const val KEY_LANGUAGE = "selected_language"

        const val PROVIDER_GEMINI = "Google Gemini"
        const val PROVIDER_OPENAI = "OpenAI"
        const val PROVIDER_ANTHROPIC = "Anthropic"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}
