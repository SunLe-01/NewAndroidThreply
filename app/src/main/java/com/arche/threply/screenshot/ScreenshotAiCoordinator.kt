package com.arche.threply.screenshot

import android.content.Context
import android.util.Log
import com.arche.threply.data.BackendAiApi
import com.arche.threply.data.DeepSeekDirectApi
import com.arche.threply.data.PrefsManager
import com.arche.threply.ime.model.ImeAiMode
import com.arche.threply.ime.trigger.SharedTriggerStore

object ScreenshotAiCoordinator {

    private const val TAG = "ScreenshotAiCoord"

    suspend fun generateAndDeliver(context: Context, ocrText: String) {
        if (ocrText.isBlank()) {
            Log.d(TAG, "OCR text empty, skipping")
            return
        }

        Log.d(TAG, "Generating replies for OCR text (${ocrText.length} chars)")

        val useDeepSeek = PrefsManager.getDeepSeekApiKey(context).isNotBlank()
        val replies = if (useDeepSeek) {
            DeepSeekDirectApi.generateReplies(
                context = context,
                inputContext = ocrText,
                styleDescriptor = "",
                onDelta = {}
            )
        } else {
            BackendAiApi.generateBaseRepliesStream(
                context = context,
                inputContext = ocrText,
                tone = 0,
                styleDescriptor = "",
                styleTemperature = 0.0,
                onDelta = {}
            )
        }

        val filtered = replies.filter { it.isNotBlank() }.take(3)
        if (filtered.isEmpty()) {
            Log.d(TAG, "No replies generated")
            return
        }

        Log.d(TAG, "Got ${filtered.size} replies, delivering")

        // Post notification
        ScreenshotNotificationHelper.postReplyNotification(context, filtered)

        // Push to SharedTriggerStore so keyboard picks it up
        SharedTriggerStore.pushTrigger(
            context = context,
            draft = ocrText,
            source = "screenshot",
            mode = ImeAiMode.B
        )
    }
}
