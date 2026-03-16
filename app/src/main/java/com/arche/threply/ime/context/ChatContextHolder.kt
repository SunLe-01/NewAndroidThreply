package com.arche.threply.ime.context

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Singleton bridge between AccessibilityService and InputMethodService.
 * AccessibilityService continuously reads chat screen content and stores it here.
 * When user clicks AI button, IME reads the latest context from here.
 */
object ChatContextHolder {

    private const val TAG = "ChatContextHolder"
    private const val A11Y_SERVICE_SHORT = "com.arche.threply/.screenshot.ChatScanAccessibilityService"
    private const val A11Y_SERVICE_FULL = "com.arche.threply/com.arche.threply.screenshot.ChatScanAccessibilityService"

    /** The latest chat context extracted from the accessibility node tree. */
    @Volatile
    var latestChatContext: String = ""
        private set

    /** Timestamp of last update. */
    @Volatile
    var lastUpdatedAt: Long = 0L
        private set

    /** The package name of the foreground chat app. */
    @Volatile
    var foregroundPackage: String = ""
        private set

    /** Known chat app package names. */
    private val CHAT_PACKAGES = setOf(
        "com.tencent.mm",           // WeChat
        "com.tencent.mobileqq",     // QQ
        "com.whatsapp",             // WhatsApp
        "org.telegram.messenger",   // Telegram
        "com.facebook.orca",        // Messenger
        "com.alibaba.android.rimet", // DingTalk
        "com.tencent.wework",       // WeCom
        "com.lark.chatting",        // Feishu (Lark)
        "jp.naver.line.android",    // LINE
        "com.instagram.android",    // Instagram DM
        "com.twitter.android",      // X (Twitter) DM
        "com.snapchat.android",     // Snapchat
        "com.discord",              // Discord
        "com.slack",                // Slack
        "com.xiaomi.smarthome",     // Mi Home (chat)
    )

    fun update(chatContext: String, packageName: String) {
        latestChatContext = chatContext
        foregroundPackage = packageName
        lastUpdatedAt = System.currentTimeMillis()
        Log.d(TAG, "Chat context updated from $packageName (${chatContext.length} chars)")
    }

    fun isChatApp(packageName: String): Boolean {
        return packageName in CHAT_PACKAGES
    }

    /**
     * Returns the chat context if it's fresh (within last 10 seconds) and non-empty.
     * Otherwise returns null, meaning IME should fall back to input field text.
     */
    fun getFreshContext(): String? {
        val age = System.currentTimeMillis() - lastUpdatedAt
        if (age > 60_000) return null  // stale after 60s
        return latestChatContext.takeIf { it.isNotBlank() }
    }

    fun clear() {
        latestChatContext = ""
        foregroundPackage = ""
        lastUpdatedAt = 0L
    }

    /** Check if our AccessibilityService is enabled in system settings. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(A11Y_SERVICE_SHORT, ignoreCase = true) ||
                enabledServices.contains(A11Y_SERVICE_FULL, ignoreCase = true)
    }

    /** Open system accessibility settings page. */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open accessibility settings: ${e.message}")
        }
    }
}
