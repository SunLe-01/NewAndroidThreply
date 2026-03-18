package com.arche.threply.ime

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

object ImeSetupHelper {
    private const val DEFAULT_DEVICE_INPUT_METHOD = "default_device_input_method"

    data class Status(
        val isEnabled: Boolean,
        val isSelected: Boolean
    )

    private fun imeId(context: Context): String {
        return ComponentName(context, ThreplyInputMethodService::class.java).flattenToShortString()
    }

    private fun normalizeImeId(rawId: String?): String? {
        val value = rawId?.trim().orEmpty()
        if (value.isEmpty()) return null
        return ComponentName.unflattenFromString(value)?.flattenToShortString() ?: value
    }

    private fun readSecureSetting(context: Context, key: String): String? {
        return runCatching {
            Settings.Secure.getString(context.contentResolver, key)
        }.getOrNull()
    }

    fun status(context: Context): Status {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val currentImeId = normalizeImeId(imeId(context))
        val enabled = imm?.enabledInputMethodList.orEmpty().any {
            normalizeImeId(it.id) == currentImeId
        }
        val selectedImeId = normalizeImeId(
            readSecureSetting(context, DEFAULT_DEVICE_INPUT_METHOD)
                ?: readSecureSetting(context, Settings.Secure.DEFAULT_INPUT_METHOD)
        )
        val selected = selectedImeId == currentImeId
        return Status(isEnabled = enabled, isSelected = selected)
    }

    /**
     * Try to show the system IME picker.
     * On Android 12+ (API 31+) this often silently fails when the calling app
     * is not the current IME, so we return false on those versions to let the
     * caller fall back to opening system settings directly.
     */
    fun showInputMethodPicker(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+, showInputMethodPicker() is unreliable from non-IME apps.
            // Return false so the caller opens system settings instead.
            return false
        }
        return runCatching {
            context.getSystemService(InputMethodManager::class.java)?.let {
                it.showInputMethodPicker()
                true
            } ?: false
        }.getOrDefault(false)
    }

    fun openInputMethodSettings(context: Context) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }
}
