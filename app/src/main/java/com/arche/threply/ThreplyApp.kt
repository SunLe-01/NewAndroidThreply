package com.arche.threply

import android.app.Application
import android.util.Log
import com.arche.threply.data.PrefsManager
import com.arche.threply.screenshot.ScreenshotMonitorService
import com.arche.threply.screenshot.ScreenshotNotificationHelper

class ThreplyApp : Application() {
    companion object {
        private const val TAG = "ThreplyApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Ensure notification channels are created
        runCatching {
            ScreenshotNotificationHelper.ensureChannels(this)
        }.onFailure {
            Log.e(TAG, "Failed to create notification channels", it)
        }

        // Warm up Rime resources in background if enabled
        runCatching {
            if (PrefsManager.isImeRimeEnabled(this) && PrefsManager.isImeRimeNativeEnabled(this)) {
                com.arche.threply.ime.rime.RimeResourceManager.warmUpAsync(this)
            }
        }.onFailure {
            Log.e(TAG, "Failed to warm up Rime resources", it)
        }

        // Auto-restart screenshot monitor if it was enabled
        runCatching {
            if (PrefsManager.isScreenshotMonitorEnabled(this)) {
                ScreenshotMonitorService.start(this)
            }
        }.onFailure {
            Log.e(TAG, "Failed to start screenshot monitor", it)
        }
    }
}
