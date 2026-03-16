package com.arche.threply.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.arche.threply.ime.context.ChatContextHolder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatScanAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_SCAN = "com.arche.threply.ACTION_CHAT_SCAN"
        const val ACTION_SCAN_RESULT = "com.arche.threply.ACTION_CHAT_SCAN_RESULT"
        const val ACTION_READ_CONTEXT = "com.arche.threply.ACTION_READ_CHAT_CONTEXT"
        const val ACTION_CONTEXT_READY = "com.arche.threply.ACTION_CHAT_CONTEXT_READY"
        const val EXTRA_RESULT = "scan_result"
        const val EXTRA_STATUS = "scan_status"
        const val EXTRA_CONTEXT_TEXT = "context_text"
        private const val TAG = "ChatScanA11y"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanReceiver: BroadcastReceiver? = null
    private var lastNodeScanTime = 0L
    /** Track the last known chat app package from events. */
    private var lastChatPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        registerScanReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Track last known chat app package
        if (ChatContextHolder.isChatApp(pkg)) {
            lastChatPackage = pkg
        }

        // Only process chat apps
        if (!ChatContextHolder.isChatApp(pkg)) return

        // Only react to meaningful UI changes
        val dominated = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!dominated) return

        // Throttle: at most once per 500ms
        val now = System.currentTimeMillis()
        if (now - lastNodeScanTime < 500) return
        lastNodeScanTime = now

        scanChatWindows()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        unregisterScanReceiver()
        scope.cancel()
    }

    private fun registerScanReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_SCAN -> performScan()
                    ACTION_READ_CONTEXT -> {
                        // IME requests an on-demand context read
                        Log.d(TAG, "On-demand context read requested by IME")
                        performContextRead()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_SCAN)
            addAction(ACTION_READ_CONTEXT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scanReceiver, filter)
        }
    }

    private fun unregisterScanReceiver() {
        scanReceiver?.let {
            unregisterReceiver(it)
            scanReceiver = null
        }
    }

    private fun performScan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            broadcastResult("此功能需要 Android 11 及以上版本", "error")
            return
        }

        scope.launch(Dispatchers.Main) {
            try {
                broadcastResult("正在截屏识别…", "loading")
                val bitmap = captureScreen()
                if (bitmap == null) {
                    broadcastResult("截屏失败，请确认无障碍权限已开启", "error")
                    return@launch
                }

                val chatContext = recognizeAndClassify(bitmap)
                bitmap.recycle()

                if (chatContext.isBlank()) {
                    broadcastResult("未识别到聊天内容", "error")
                    return@launch
                }

                broadcastResult("识别成功，正在生成回复…", "loading")

                // Build a context-aware prompt with role explanation
                val aiPrompt = buildString {
                    append("以下是从聊天截图中识别出的对话内容，格式为\"角色: 消息内容\"。\n")
                    append("\"对方\"是聊天对象发送的消息（靠左），\"我\"是用户自己发送的消息（靠右）。\n")
                    append("请仔细理解完整的对话语境和情感，站在\"我\"的角度，")
                    append("针对对方最新的消息生成3条自然、得体、符合语境的回复。\n")
                    append("每条回复独占一行，不要编号，不要加引号，不要解释。\n\n")
                    append("对话记录：\n")
                    append(chatContext)
                }

                // Generate AI replies
                val useDeepSeek = com.arche.threply.data.PrefsManager
                    .getDeepSeekApiKey(this@ChatScanAccessibilityService).isNotBlank()
                val replies = if (useDeepSeek) {
                    com.arche.threply.data.DeepSeekDirectApi.generateReplies(
                        context = this@ChatScanAccessibilityService,
                        inputContext = aiPrompt,
                        styleDescriptor = "",
                        onDelta = {}
                    )
                } else {
                    com.arche.threply.data.BackendAiApi.generateBaseRepliesStream(
                        context = this@ChatScanAccessibilityService,
                        inputContext = aiPrompt,
                        tone = 0, styleDescriptor = "",
                        styleTemperature = 0.0, onDelta = {}
                    )
                }

                val filtered = replies.filter { it.isNotBlank() }.take(3)
                if (filtered.isEmpty()) {
                    broadcastResult("未能生成回复建议", "error")
                    return@launch
                }

                val resultText = filtered.mapIndexed { i, r -> "${i + 1}. $r" }.joinToString("\n")
                broadcastResult(resultText, "success")

                // Also push to SharedTriggerStore for keyboard
                com.arche.threply.ime.trigger.SharedTriggerStore.pushTrigger(
                    context = this@ChatScanAccessibilityService,
                    draft = chatContext,
                    source = "chat_scan",
                    mode = com.arche.threply.ime.model.ImeAiMode.B
                )
            } catch (e: Exception) {
                Log.w(TAG, "Scan failed: ${e.message}")
                broadcastResult("扫描失败: ${e.message}", "error")
            }
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private suspend fun captureScreen(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer, result.colorSpace
                        )
                        result.hardwareBuffer.close()
                        val sw = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        bitmap?.recycle()
                        cont.resume(sw)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed: $errorCode")
                        cont.resume(null)
                    }
                }
            )
        }

    private suspend fun recognizeAndClassify(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        data class ChatLine(val y: Int, val role: String, val text: String)

        val lines = mutableListOf<ChatLine>()
        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            val text = block.text.replace("\n", " ").trim()

            // Filter noise: too short, or in top status bar / bottom nav area
            if (text.length < 2) continue
            if (box.top < screenHeight * 0.08) continue  // status bar
            if (box.bottom > screenHeight * 0.92) continue  // nav bar / keyboard

            // Use 40% / 60% thresholds for left/right classification
            // Chat apps: other's messages left-aligned, mine right-aligned
            val centerX = box.centerX()
            val role = when {
                centerX < screenWidth * 0.4 -> "对方"
                centerX > screenWidth * 0.6 -> "我"
                else -> continue  // middle zone: likely timestamps, names, system messages
            }

            lines.add(ChatLine(box.centerY(), role, text))
        }

        lines.sortBy { it.y }
        val recent = lines.takeLast(20)
        return recent.joinToString("\n") { "${it.role}: ${it.text}" }
    }

    /**
     * Extract chat messages from the accessibility node tree.
     * Uses screen position (left/right) to classify messages as "对方" or "我".
     * Works across major chat apps (WeChat, QQ, WhatsApp, Telegram, etc.).
     */
    private fun extractChatFromNodeTree(root: AccessibilityNodeInfo, pkg: String): String {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        data class ChatNode(val y: Int, val role: String, val text: String)

        val nodes = mutableListOf<ChatNode>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            // Collect text from TextView-like nodes
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length >= 2) {
                val rect = Rect()
                node.getBoundsInScreen(rect)

                // Filter: skip status bar area and bottom nav/keyboard area
                if (rect.top > screenHeight * 0.08 && rect.bottom < screenHeight * 0.85) {
                    val centerX = rect.centerX()
                    // Skip very narrow elements (icons, badges, timestamps in center)
                    val width = rect.width()
                    if (width > screenWidth * 0.1) {
                        val role = when {
                            centerX < screenWidth * 0.45 -> "对方"
                            centerX > screenWidth * 0.55 -> "我"
                            else -> null // center zone: timestamps, system messages
                        }
                        if (role != null && text.length <= 500) {
                            nodes.add(ChatNode(rect.centerY(), role, text))
                        }
                    }
                }
            }

            // Traverse children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }

        // Deduplicate: parent nodes may contain same text as children
        val deduped = nodes
            .distinctBy { "${it.y / 10}_${it.text}" }
            .sortedBy { it.y }
            .takeLast(30)

        return deduped.joinToString("\n") { "${it.role}: ${it.text}" }
    }

    /**
     * On-demand context read: try node tree first, fallback to screenshot+OCR.
     * Broadcasts ACTION_CONTEXT_READY when done.
     */
    private fun performContextRead() {
        // Step 1: try node tree (fast, synchronous)
        scanChatWindows()
        val nodeResult = ChatContextHolder.getFreshContext()
        if (nodeResult != null && nodeResult.length > 10) {
            Log.d(TAG, "Context read via node tree succeeded (${nodeResult.length} chars)")
            broadcastContextReady(nodeResult)
            return
        }

        // Step 2: fallback to screenshot + OCR (async)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Screenshot OCR requires Android 11+")
            broadcastContextReady("")
            return
        }

        Log.d(TAG, "Node tree insufficient, falling back to screenshot+OCR")
        scope.launch(Dispatchers.Main) {
            try {
                val bitmap = captureScreen()
                if (bitmap == null) {
                    Log.w(TAG, "Screenshot capture failed")
                    broadcastContextReady("")
                    return@launch
                }

                val chatContext = recognizeAndClassify(bitmap)
                bitmap.recycle()

                if (chatContext.isNotBlank()) {
                    val pkg = lastChatPackage.ifBlank { "unknown" }
                    ChatContextHolder.update(chatContext, pkg)
                    Log.d(TAG, "Context read via screenshot+OCR succeeded (${chatContext.length} chars)")
                    broadcastContextReady(chatContext)
                } else {
                    Log.d(TAG, "Screenshot+OCR found no chat content")
                    broadcastContextReady("")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Screenshot+OCR failed: ${e.message}")
                broadcastContextReady("")
            }
        }
    }

    private fun broadcastContextReady(contextText: String) {
        val intent = Intent(ACTION_CONTEXT_READY).apply {
            putExtra(EXTRA_CONTEXT_TEXT, contextText)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Scan all windows to find chat app content.
     * Uses getWindows() to find the chat app window even when IME is in foreground.
     */
    private fun scanChatWindows() {
        try {
            // Method 1: search all windows for a chat app
            val chatRoot = findChatAppWindow()
            if (chatRoot != null) {
                val pkg = lastChatPackage.ifBlank { "unknown" }
                val chatText = extractChatFromNodeTree(chatRoot, pkg)
                chatRoot.recycle()
                if (chatText.isNotBlank()) {
                    ChatContextHolder.update(chatText, pkg)
                    Log.d(TAG, "Chat context extracted via window scan ($pkg, ${chatText.length} chars)")
                    return
                }
            }

            // Method 2: fallback to rootInActiveWindow
            val root = rootInActiveWindow
            if (root != null) {
                val rootPkg = root.packageName?.toString() ?: ""
                if (ChatContextHolder.isChatApp(rootPkg)) {
                    val chatText = extractChatFromNodeTree(root, rootPkg)
                    root.recycle()
                    if (chatText.isNotBlank()) {
                        ChatContextHolder.update(chatText, rootPkg)
                        Log.d(TAG, "Chat context extracted via rootInActiveWindow ($rootPkg)")
                        return
                    }
                } else {
                    root.recycle()
                }
            }

            Log.d(TAG, "No chat app window found in scan")
        } catch (e: Exception) {
            Log.w(TAG, "scanChatWindows failed: ${e.message}")
        }
    }

    /**
     * Find the chat app's window root node by iterating all windows.
     * This works even when the IME window is in foreground.
     */
    private fun findChatAppWindow(): AccessibilityNodeInfo? {
        try {
            val windowList = windows
            for (window in windowList) {
                // Look for APPLICATION type windows from chat apps
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (ChatContextHolder.isChatApp(pkg)) {
                    lastChatPackage = pkg
                    return root
                }
                root.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "findChatAppWindow failed: ${e.message}")
        }
        return null
    }

    private fun broadcastResult(text: String, status: String) {
        val intent = Intent(ACTION_SCAN_RESULT).apply {
            putExtra(EXTRA_RESULT, text)
            putExtra(EXTRA_STATUS, status)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}