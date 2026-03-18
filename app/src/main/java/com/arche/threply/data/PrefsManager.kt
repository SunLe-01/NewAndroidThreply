package com.arche.threply.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Central SharedPreferences manager for all app settings.
 * Equivalent to iOS App Group UserDefaults.
 */
object PrefsManager {
    private const val PREFS_NAME = "com.arche.threply.shared"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Onboarding ───
    fun hasCompletedOnboarding(context: Context): Boolean =
        prefs(context).getBoolean("hasCompletedOnboarding", false)

    fun setCompletedOnboarding(context: Context, completed: Boolean) =
        prefs(context).edit().putBoolean("hasCompletedOnboarding", completed).apply()

    // ─── Login ───
    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean("threply.isLoggedIn", false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) =
        prefs(context).edit().putBoolean("threply.isLoggedIn", loggedIn).apply()

    fun getUserDisplayName(context: Context): String =
        prefs(context).getString("threply.userDisplayName", "访客") ?: "访客"

    fun setUserDisplayName(context: Context, name: String) =
        prefs(context).edit().putString("threply.userDisplayName", name).apply()

    fun getGoogleUserId(context: Context): String =
        prefs(context).getString("threply.googleUserIdentifier", "") ?: ""

    fun setGoogleUserId(context: Context, id: String) =
        prefs(context).edit().putString("threply.googleUserIdentifier", id).apply()

    fun getGoogleUserDisplayName(context: Context): String =
        prefs(context).getString("threply.googleUserDisplayName", "") ?: ""

    fun setGoogleUserDisplayName(context: Context, name: String) =
        prefs(context).edit().putString("threply.googleUserDisplayName", name).apply()

    // ─── Haptics ───
    fun isHapticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.hapticsEnabled", true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.hapticsEnabled", enabled).apply()

    fun getHapticStrength(context: Context): Float =
        prefs(context).getFloat("com.arche.threply.hapticStrength", 1.0f)

    fun setHapticStrength(context: Context, strength: Float) =
        prefs(context).edit().putFloat("com.arche.threply.hapticStrength", strength).apply()

    // ─── Keyboard Behavior ───
    fun isAutoSentencePunctuationEnabled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.autoSentencePunctuationEnabled", false)

    fun setAutoSentencePunctuationEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.autoSentencePunctuationEnabled", enabled).apply()

    // ─── Language & Handedness ───
    fun getLanguagePreference(context: Context): String =
        prefs(context).getString("threply.languagePreference", "system") ?: "system"

    fun setLanguagePreference(context: Context, value: String) =
        prefs(context).edit().putString("threply.languagePreference", value).apply()

    fun getHandedness(context: Context): String =
        prefs(context).getString("threply.handedness", "right") ?: "right"

    fun setHandedness(context: Context, value: String) =
        prefs(context).edit().putString("threply.handedness", value).apply()

    fun getThemePreference(context: Context): ThemePreference =
        ThemePreference.fromStorage(
            prefs(context).getString("threply.themePreference", ThemePreference.System.storageValue)
        )

    fun setThemePreference(context: Context, value: ThemePreference) =
        prefs(context).edit().putString("threply.themePreference", value.storageValue).apply()

    // ─── Backend ───
    fun getBackendBaseURL(context: Context): String =
        prefs(context).getString("com.arche.threply.backendBaseURL", BackendSessionStore.DEFAULT_BASE_URL)
            ?: BackendSessionStore.DEFAULT_BASE_URL

    fun setBackendBaseURL(context: Context, url: String) =
        prefs(context).edit().putString("com.arche.threply.backendBaseURL", url).apply()

    // ─── DeepSeek Direct API ───
    fun getDeepSeekApiKey(context: Context): String {
        return prefs(context).getString("com.arche.threply.deepseek.apiKey", "")?.trim().orEmpty()
    }

    fun setDeepSeekApiKey(context: Context, key: String) =
        prefs(context).edit().putString("com.arche.threply.deepseek.apiKey", key.trim()).apply()

    fun getDeviceId(context: Context): String {
        val key = "com.arche.threply.deviceId"
        val existing = prefs(context).getString(key, null)
        if (!existing.isNullOrEmpty()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        prefs(context).edit().putString(key, generated).apply()
        return generated
    }

    // ─── Pro Entitlement ───
    fun isProEntitled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.pro.entitled", false)

    fun setProEntitled(context: Context, entitled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.pro.entitled", entitled).apply()

    // ─── IME AI Core ───
    fun isImeAiEnabled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.ime.ai.enabled", true)

    fun setImeAiEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.ime.ai.enabled", enabled).apply()

    fun getImeAiMode(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.ai.mode", "B") ?: "B"

    fun setImeAiMode(context: Context, mode: String) =
        prefs(context).edit().putString("com.arche.threply.ime.ai.mode", mode).apply()

    // ─── IME AI Style (C-Mode 2D pad) ───
    fun getImeStyleLength(context: Context): Float =
        prefs(context).getFloat("com.arche.threply.ime.ai.style.length", 0f)

    fun getImeStyleTemperature(context: Context): Float =
        prefs(context).getFloat("com.arche.threply.ime.ai.style.temperature", 0f)

    fun setImeStyle(context: Context, length: Float, temperature: Float) =
        prefs(context).edit()
            .putFloat("com.arche.threply.ime.ai.style.length", length)
            .putFloat("com.arche.threply.ime.ai.style.temperature", temperature)
            .apply()

    fun getImePendingTriggerPayload(context: Context): String? =
        prefs(context).getString("com.arche.threply.ime.trigger.payload", null)

    fun setImePendingTriggerPayload(context: Context, payload: String?) =
        prefs(context).edit().putString("com.arche.threply.ime.trigger.payload", payload).apply()

    fun getImeSuggestionCache(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.suggestions.cache", "") ?: ""

    fun setImeSuggestionCache(context: Context, cache: String) =
        prefs(context).edit().putString("com.arche.threply.ime.suggestions.cache", cache).apply()

    fun getImeSuggestionVersion(context: Context): Long =
        prefs(context).getLong("com.arche.threply.ime.suggestions.version", 0L)

    fun setImeSuggestionVersion(context: Context, version: Long) =
        prefs(context).edit().putLong("com.arche.threply.ime.suggestions.version", version).apply()

    fun getImeLastInputContext(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.lastInputContext", "") ?: ""

    fun setImeLastInputContext(context: Context, value: String) =
        prefs(context).edit().putString("com.arche.threply.ime.lastInputContext", value).apply()

    fun isImeRimeEnabled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.ime.rime.enabled", true)

    fun setImeRimeEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.ime.rime.enabled", enabled).apply()

    fun isImeRimeNativeEnabled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.ime.rime.nativeEnabled", true)

    fun setImeRimeNativeEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.ime.rime.nativeEnabled", enabled).apply()

    fun getImeRimeSchema(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.rime.schema", "luna_pinyin") ?: "luna_pinyin"

    fun setImeRimeSchema(context: Context, schema: String) =
        prefs(context).edit().putString("com.arche.threply.ime.rime.schema", schema).apply()

    fun getImeRimeResourceVersion(context: Context): Int =
        prefs(context).getInt("com.arche.threply.ime.rime.resourceVersion", 0)

    fun setImeRimeResourceVersion(context: Context, version: Int) =
        prefs(context).edit().putInt("com.arche.threply.ime.rime.resourceVersion", version).apply()

    fun getImeInputLanguage(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.inputLanguage", "en") ?: "en"

    fun setImeInputLanguage(context: Context, value: String) =
        prefs(context).edit().putString("com.arche.threply.ime.inputLanguage", value).apply()

    // ─── IME Translate Mode ───
    fun getTranslateSourceLanguage(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.translate.sourceLanguage", "auto") ?: "auto"

    fun setTranslateSourceLanguage(context: Context, languageCode: String) =
        prefs(context).edit().putString("com.arche.threply.ime.translate.sourceLanguage", languageCode).apply()

    fun getTranslateTargetLanguage(context: Context): String =
        prefs(context).getString("com.arche.threply.ime.translate.targetLanguage", "en") ?: "en"

    fun setTranslateTargetLanguage(context: Context, languageCode: String) =
        prefs(context).edit().putString("com.arche.threply.ime.translate.targetLanguage", languageCode).apply()

    // ─── Screenshot Monitor ───
    fun isScreenshotMonitorEnabled(context: Context): Boolean =
        prefs(context).getBoolean("com.arche.threply.screenshot.enabled", false)

    fun setScreenshotMonitorEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("com.arche.threply.screenshot.enabled", enabled).apply()

    fun getLastScreenshotUri(context: Context): String =
        prefs(context).getString("com.arche.threply.screenshot.lastUri", "") ?: ""

    fun setLastScreenshotUri(context: Context, uri: String) =
        prefs(context).edit().putString("com.arche.threply.screenshot.lastUri", uri).apply()

    // ─── User Profile / Persona ───
    fun isProfileEnabled(context: Context): Boolean =
        prefs(context).getBoolean("threply.profile.enabled", false)

    fun setProfileEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("threply.profile.enabled", enabled).apply()

    fun getProfileJson(context: Context): String =
        prefs(context).getString("threply.userProfile.json", "") ?: ""

    fun setProfileJson(context: Context, json: String) =
        prefs(context).edit().putString("threply.userProfile.json", json).apply()
}
