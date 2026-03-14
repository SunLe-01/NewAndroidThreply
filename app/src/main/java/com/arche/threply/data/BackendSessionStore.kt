package com.arche.threply.data

import android.content.Context
import android.util.Log

/**
 * Backend session management.
 * Equivalent to iOS BackendSessionStore.
 */
object BackendSessionStore {
    const val DEFAULT_BASE_URL = "https://api.arche.pw/v1"
    private const val TAG = "BackendSessionStore"
    private const val KEY_ACCESS_TOKEN = "com.arche.threply.backend.accessToken"
    private const val KEY_REFRESH_TOKEN = "com.arche.threply.backend.refreshToken"
    private const val KEY_IME_LAST_AI_REQUEST_AT = "com.arche.threply.ime.ai.lastRequestAt"
    private const val KEY_IME_AI_REQUEST_COUNT = "com.arche.threply.ime.ai.requestCount"

    fun saveSession(context: Context, accessToken: String, refreshToken: String, plan: String?) {
        val prefs = PrefsManager.prefs(context)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putBoolean("threply.isLoggedIn", true)
            .apply()
        if (plan?.lowercase() == "pro") {
            PrefsManager.setProEntitled(context, true)
        }
    }

    fun clearSession(context: Context) {
        val prefs = PrefsManager.prefs(context)
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .putBoolean("threply.isLoggedIn", false)
            .apply()
        PrefsManager.setProEntitled(context, false)
    }

    fun accessToken(context: Context): String? =
        PrefsManager.prefs(context).getString(KEY_ACCESS_TOKEN, null)
            ?.takeIf { it.isNotEmpty() }

    fun refreshToken(context: Context): String? =
        PrefsManager.prefs(context).getString(KEY_REFRESH_TOKEN, null)
            ?.takeIf { it.isNotEmpty() }

    fun configuredBaseURL(context: Context): String {
        val url = PrefsManager.getBackendBaseURL(context).trim()
        return url.ifEmpty { DEFAULT_BASE_URL }
    }

    fun isReadyForImeAi(context: Context): Boolean =
        PrefsManager.isLoggedIn(context) && !accessToken(context).isNullOrEmpty()

    /**
     * Attempt to refresh the access token using the stored refresh token.
     * Returns true if refresh succeeded and new tokens are saved.
     * Returns false and clears session if refresh fails (e.g. revoked).
     */
    suspend fun tryRefreshToken(context: Context): Boolean {
        val currentRefresh = refreshToken(context) ?: return false
        val baseUrl = configuredBaseURL(context)
        return try {
            val response = BackendAuthApi.refreshAccessToken(baseUrl, currentRefresh)
            saveSession(context, response.accessToken, response.refreshToken, response.user.plan)
            Log.d(TAG, "Token refresh succeeded")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh failed, clearing session: ${e.message}")
            clearSession(context)
            false
        }
    }

    fun saveImeAiRequestMeta(context: Context, requestAtMillis: Long) {
        val prefs = PrefsManager.prefs(context)
        val current = prefs.getInt(KEY_IME_AI_REQUEST_COUNT, 0)
        prefs.edit()
            .putLong(KEY_IME_LAST_AI_REQUEST_AT, requestAtMillis)
            .putInt(KEY_IME_AI_REQUEST_COUNT, current + 1)
            .apply()
    }

    fun lastImeAiRequestAt(context: Context): Long =
        PrefsManager.prefs(context).getLong(KEY_IME_LAST_AI_REQUEST_AT, 0L)

    fun imeAiRequestCount(context: Context): Int =
        PrefsManager.prefs(context).getInt(KEY_IME_AI_REQUEST_COUNT, 0)
}

