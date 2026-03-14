package com.arche.threply.screenshot

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenshotMonitorService : Service() {

    private val TAG = "ScreenshotMonitor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: ScreenshotContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        ScreenshotNotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = ScreenshotNotificationHelper.buildMonitorNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ScreenshotNotificationHelper.MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                ScreenshotNotificationHelper.MONITOR_NOTIFICATION_ID,
                notification
            )
        }

        registerObserver()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterObserver()
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    private fun registerObserver() {
        if (observer != null) return
        val handler = Handler(Looper.getMainLooper())
        observer = ScreenshotContentObserver(handler, this) { uri ->
            scope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Processing screenshot: $uri")
                    val text = OcrEngine.recognizeText(this@ScreenshotMonitorService, uri)
                    if (text.isNotBlank()) {
                        ScreenshotAiCoordinator.generateAndDeliver(
                            this@ScreenshotMonitorService, text
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Screenshot processing failed: ${e.message}")
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
        Log.d(TAG, "ContentObserver registered")
    }

    private fun unregisterObserver() {
        observer?.let {
            contentResolver.unregisterContentObserver(it)
            observer = null
            Log.d(TAG, "ContentObserver unregistered")
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ScreenshotMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotMonitorService::class.java))
        }
    }
}
