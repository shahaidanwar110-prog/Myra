package com.myra.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

open class SecureStorage(context: Context) {

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

    open fun getGeminiModel(): String {
        return sharedPreferences.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)?.ifBlank { DEFAULT_GEMINI_MODEL } ?: DEFAULT_GEMINI_MODEL
    }

    fun saveActiveProvider(provider: String) {
        sharedPreferences.edit().putString(KEY_ACTIVE_PROVIDER, provider).apply()
    }

    open fun getActiveProvider(): String {
        return sharedPreferences.getString(KEY_ACTIVE_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
    }

    fun saveLanguage(languageCode: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, "en-US") ?: "en-US"
    }

    fun saveDarkTheme(isDark: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
    }

    fun isDarkTheme(): Boolean {
        return sharedPreferences.getBoolean(KEY_DARK_THEME, true)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getString(key: String): String {
        return sharedPreferences.getString(key, "") ?: ""
    }

    fun savePitch(pitch: Float) {
        sharedPreferences.edit().putFloat("tts_pitch", pitch).apply()
    }

    fun getPitch(): Float {
        return sharedPreferences.getFloat("tts_pitch", 1.0f)
    }

    fun saveSpeechRate(rate: Float) {
        sharedPreferences.edit().putFloat("tts_speech_rate", rate).apply()
    }

    fun getSpeechRate(): Float {
        return sharedPreferences.getFloat("tts_speech_rate", 1.0f)
    }

    fun saveSelectedVoice(voiceName: String) {
        saveString("selected_tts_voice", voiceName)
    }

    fun getSelectedVoice(): String {
        return getString("selected_tts_voice")
    }

    fun saveFavoriteVoicesJson(json: String) {
        saveString("favorite_voices_json", json)
    }

    fun getFavoriteVoicesJson(): String {
        return getString("favorite_voices_json")
    }

    fun saveUserName(name: String) {
        saveString(KEY_USER_NAME, name)
    }

    open fun getUserName(): String {
        val stored = getString(KEY_USER_NAME)
        return if (stored.isBlank()) "Friend" else stored
    }

    fun savePersonalityStyle(style: String) {
        saveString(KEY_PERSONALITY_STYLE, style)
    }

    open fun getPersonalityStyle(): String {
        val stored = getString(KEY_PERSONALITY_STYLE)
        return if (stored.isBlank()) "Caring friend" else stored
    }

    fun saveLanguageMix(mix: String) {
        saveString(KEY_LANGUAGE_MIX, mix)
    }

    open fun getLanguageMix(): String {
        val stored = getString(KEY_LANGUAGE_MIX)
        return if (stored.isBlank()) "Urdu/Hindi/English Mix" else stored
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PERSONALITY_STYLE = "personality_style"
        private const val KEY_LANGUAGE_MIX = "language_mix"
        private const val PREFS_FILENAME = "myra_secure_prefs"
        private const val FALLBACK_PREFS_FILENAME = "myra_fallback_prefs"
        private const val KEY_PREFIX_API = "api_key_"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_ACTIVE_PROVIDER = "active_ai_provider"
        private const val KEY_LANGUAGE = "selected_language"
        private const val KEY_DARK_THEME = "is_dark_theme"
        private const val KEY_ONBOARDING_COMPLETED = "is_onboarding_completed"

        const val PROVIDER_GEMINI = "Google Gemini"
        const val PROVIDER_OPENAI = "OpenAI"
        const val PROVIDER_ANTHROPIC = "Anthropic"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}
