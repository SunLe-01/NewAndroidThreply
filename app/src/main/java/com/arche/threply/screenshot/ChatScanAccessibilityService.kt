package com.arche.threply.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
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
        const val EXTRA_RESULT = "scan_result"
        const val EXTRA_STATUS = "scan_status"
        private const val TAG = "ChatScanA11y"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        registerScanReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need takeScreenshot
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
                if (intent?.action == ACTION_SCAN) {
                    performScan()
                }
            }
        }
        val filter = IntentFilter(ACTION_SCAN)
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

    private fun broadcastResult(text: String, status: String) {
        val intent = Intent(ACTION_SCAN_RESULT).apply {
            putExtra(EXTRA_RESULT, text)
            putExtra(EXTRA_STATUS, status)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}