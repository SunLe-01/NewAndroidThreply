package com.arche.threply.screenshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.arche.threply.R

object ScreenshotNotificationHelper {

    const val CHANNEL_MONITOR = "threply_screenshot_monitor"
    const val CHANNEL_REPLIES = "threply_screenshot_replies"
    const val MONITOR_NOTIFICATION_ID = 9001
    private var replyNotificationId = 9100

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR, "截图监控",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "截图监控服务运行中" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REPLIES, "AI 回复建议",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "截图识别后的 AI 回复建议" }
        )
    }

    fun buildMonitorNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Threply 截图监控")
            .setContentText("正在监听截图，识别后自动生成回复建议")
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun postReplyNotification(context: Context, replies: List<String>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val text = replies.mapIndexed { i, r -> "${i + 1}. $r" }.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AI 回复建议")
            .setContentText(replies.firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(replyNotificationId++, notification)
    }
}
