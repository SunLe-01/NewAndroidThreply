package com.arche.threply.ime

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

object ImeSetupHelper {
    data class Status(
        val isEnabled: Boolean,
        val isSelected: Boolean
    )

    private fun imeId(context: Context): String {
        return ComponentName(context, ThreplyInputMethodService::class.java).flattenToShortString()
    }

    fun status(context: Context): Status {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val currentImeId = imeId(context)
        val enabled = imm?.enabledInputMethodList.orEmpty().any { it.id == currentImeId }
        val selected = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) == currentImeId
        return Status(isEnabled = enabled, isSelected = selected)
    }

    fun showInputMethodPicker(context: Context) {
        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}
