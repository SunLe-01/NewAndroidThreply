package com.arche.threply.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage using EncryptedSharedPreferences.
 * Equivalent to iOS KeychainManager.
 */
object SecureStorage {
    private const val FILE_NAME = "com.arche.threply.secure"
    private const val KEY_API_KEY = "deepseek_api_key"
    private const val TAG = "SecureStorage"

    private fun getEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            FILE_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun loadApiKey(context: Context): String? {
        return try {
            getEncryptedPrefs(context).getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load API key", e)
            null
        }
    }

    fun saveApiKey(context: Context, key: String): Boolean {
        return try {
            getEncryptedPrefs(context).edit().putString(KEY_API_KEY, key).apply()
            // Also sync to shared prefs for keyboard/intents access
            PrefsManager.prefs(context).edit()
                .putString("API_KEY", key)
                .apply()
            Log.d(TAG, "🔐 API Key saved and synced")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key", e)
            false
        }
    }

    fun deleteApiKey(context: Context) {
        try {
            getEncryptedPrefs(context).edit().remove(KEY_API_KEY).apply()
            PrefsManager.prefs(context).edit().remove("API_KEY").apply()
            Log.d(TAG, "🧹 API Key deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete API key", e)
        }
    }
}
