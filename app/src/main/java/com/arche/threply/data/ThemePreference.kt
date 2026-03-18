package com.arche.threply.data

import androidx.appcompat.app.AppCompatDelegate

enum class ThemePreference(
    val storageValue: String,
    val nightMode: Int,
    val label: String,
) {
    System(
        storageValue = "system",
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        label = "跟随系统",
    ),
    Light(
        storageValue = "light",
        nightMode = AppCompatDelegate.MODE_NIGHT_NO,
        label = "浅色",
    ),
    Dark(
        storageValue = "dark",
        nightMode = AppCompatDelegate.MODE_NIGHT_YES,
        label = "深色",
    );

    companion object {
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}
