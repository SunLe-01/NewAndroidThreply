package com.arche.threply.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Backend AI API — Android equivalent of iOS DeepSeekKeyboardAPI.
 * Supports both regular and SSE streaming endpoints with auto token refresh.
 */
object BackendAiApi {

    private const val TAG = "BackendAiApi"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Response models ──────────────────────────────────────────────

    data class RepliesResponse(
        @SerializedName("replies") val replies: List<String>
    )

    data class TextResponse(
        @SerializedName("text") val text: String
    )

    private data class StreamDelta(
        @SerializedName("text") val text: String?
    )

    private data class StreamDoneReplies(
        @SerializedName("response") val response: RepliesResponse?,
        @SerializedName("fullText") val fullText: String?
    )

    // ── Public: generate base replies (non-stream) ───────────────────

    suspend fun generateBaseReplies(
        context: Context,
        inputContext: String,
        tone: Int,
        styleDescriptor: String,
        styleTemperature: Double
    ): List<String> {
        val body = mapOf(
            "context" to inputContext,
            "tone" to tone,
            "styleDescriptor" to styleDescriptor,
            "styleTemperature" to styleTemperature
        )
        val data = performAuthorizedRequest(context, "ai/replies/base", body)
        val decoded = gson.fromJson(data, RepliesResponse::class.java)
        return decoded.replies.take(3)
    }

    // ── Public: generate base replies (SSE stream) ───────────────────

    /**
     * Streaming variant. Calls [onDelta] with incremental text chunks.
     * Falls back to non-stream on 409 or non-SSE response.
     * Returns final list of replies.
     */
    suspend fun generateBaseRepliesStream(
        context: Context,
        inputContext: String,
        tone: Int,
        styleDescriptor: String,
        styleTemperature: Double,
        onDelta: suspend (String) -> Unit
    ): List<String> {
        val body = mapOf(
            "context" to inputContext,
            "tone" to tone,
            "styleDescriptor" to styleDescriptor,
            "styleTemperature" to styleTemperature
        )
        val result = performAuthorizedStreamRequest(context, "ai/replies/base/stream", body, onDelta)
        if (result != null) {
            return result.take(3)
        }
        // Downgrade to non-stream
        Log.d(TAG, "Stream downgrade, falling back to non-stream for replies/base")
        return generateBaseReplies(context, inputContext, tone, styleDescriptor, styleTemperature)
    }

    // ── Public: text skills (translate / replace / polish) ───────────

    suspend fun translateText(
        context: Context,
        text: String,
        sourceLanguage: String = "auto-detect",
        targetLanguage: String
    ): String {
        val body = mapOf(
            "text" to text,
            "sourceLanguage" to sourceLanguage,
            "targetLanguage" to targetLanguage
        )
        val data = performAuthorizedRequest(context, "ai/text/translate", body)
        return gson.fromJson(data, TextResponse::class.java).text
    }

    suspend fun replaceText(
        context: Context,
        text: String
    ): String {
        val body = mapOf("text" to text)
        val data = performAuthorizedRequest(context, "ai/text/replace", body)
        return gson.fromJson(data, TextResponse::class.java).text
    }

    suspend fun polishText(
        context: Context,
        text: String
    ): String {
        val body = mapOf("text" to text)
        val data = performAuthorizedRequest(context, "ai/text/polish", body)
        return gson.fromJson(data, TextResponse::class.java).text
    }

    // ── Internal: authorized request with auto 401 refresh ───────────

    private suspend fun performAuthorizedRequest(
        context: Context,
        path: String,
        body: Map<String, Any>
    ): String = withContext(Dispatchers.IO) {
        // TODO: restore token requirement before release
        val token = BackendSessionStore.accessToken(context) ?: ""

        val first = sendRequest(context, path, body, token)
        if (first.code == 401) {
            val refreshed = BackendSessionStore.tryRefreshToken(context)
            if (!refreshed) throw Exception("登录已过期，请重新登录。")
            val newToken = BackendSessionStore.accessToken(context)
                ?: throw Exception("登录已过期，请重新登录。")
            val retry = sendRequest(context, path, body, newToken)
            if (retry.code !in 200..299) {
                throw Exception(extractErrorMessage(retry.body) ?: "请求失败（${retry.code}）")
            }
            retry.body
        } else if (first.code !in 200..299) {
            throw Exception(extractErrorMessage(first.body) ?: "请求失败（${first.code}）")
        } else {
            first.body
        }
    }

    /**
     * Returns parsed replies list, or null if server indicates streaming unavailable (downgrade).
     */
    private suspend fun performAuthorizedStreamRequest(
        context: Context,
        path: String,
        body: Map<String, Any>,
        onDelta: suspend (String) -> Unit
    ): List<String>? = withContext(Dispatchers.IO) {
        // TODO: restore token requirement before release
        val token = BackendSessionStore.accessToken(context) ?: ""

        try {
            sendStreamRequest(context, path, body, token, onDelta)
        } catch (e: StreamUnauthorizedException) {
            val refreshed = BackendSessionStore.tryRefreshToken(context)
            if (!refreshed) throw Exception("登录已过期，请重新登录。")
            val newToken = BackendSessionStore.accessToken(context)
                ?: throw Exception("登录已过期，请重新登录。")
            try {
                sendStreamRequest(context, path, body, newToken, onDelta)
            } catch (_: StreamDowngradeException) {
                null
            }
        } catch (_: StreamDowngradeException) {
            null
        }
    }

    // ── Low-level: regular HTTP POST ─────────────────────────────────

    private data class HttpResult(val code: Int, val body: String)

    private fun sendRequest(
        context: Context,
        path: String,
        body: Map<String, Any>,
        accessToken: String
    ): HttpResult {
        val baseUrl = BackendSessionStore.configuredBaseURL(context).trimEnd('/')
        val url = "$baseUrl/$path"
        val jsonBody = gson.toJson(body)

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("x-device-id", PrefsManager.getDeviceId(context))
            .addHeader("x-request-id", UUID.randomUUID().toString())
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        return HttpResult(response.code, responseBody)
    }

    // ── Low-level: SSE streaming POST ────────────────────────────────

    private class StreamUnauthorizedException : Exception()
    private class StreamDowngradeException : Exception()

    /**
     * Sends a streaming POST, parses SSE events (delta / done),
     * and returns the final replies list.
     */
    private fun sendStreamRequest(
        context: Context,
        path: String,
        body: Map<String, Any>,
        accessToken: String,
        onDelta: suspend (String) -> Unit
    ): List<String> {
        val baseUrl = BackendSessionStore.configuredBaseURL(context).trimEnd('/')
        val url = "$baseUrl/$path"
        val jsonBody = gson.toJson(body)
        val requestId = UUID.randomUUID().toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("x-device-id", PrefsManager.getDeviceId(context))
            .addHeader("x-request-id", requestId)
            .build()

        Log.d(TAG, "SSE start path=$path requestId=$requestId")
        val response = client.newCall(request).execute()
        val code = response.code
        val contentType = response.header("Content-Type") ?: ""

        Log.d(TAG, "SSE response path=$path status=$code ct=$contentType")

        if (code == 401) {
            response.close()
            throw StreamUnauthorizedException()
        }
        if (code == 409) {
            response.close()
            throw StreamDowngradeException()
        }
        if (code !in 200..299) {
            val errBody = response.body?.string() ?: ""
            response.close()
            throw Exception(extractErrorMessage(errBody) ?: "请求失败（$code）")
        }
        if (!contentType.lowercase().contains("text/event-stream")) {
            Log.d(TAG, "SSE downgrade non-sse ct=$contentType")
            response.close()
            throw StreamDowngradeException()
        }

        // Parse SSE events
        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("响应体为空")
        return parseSseEvents(reader, onDelta)
    }

    // ── SSE event parser ─────────────────────────────────────────────

    /**
     * Reads SSE lines from [reader]. Recognises `event:` and `data:` fields.
     * Dispatches delta text via [onDelta] and returns final replies from the `done` event.
     */
    private fun parseSseEvents(
        reader: BufferedReader,
        onDelta: suspend (String) -> Unit
    ): List<String> {
        var currentEvent: String? = null
        var currentData = StringBuilder()
        var finalReplies: List<String>? = null

        reader.use { r ->
            r.forEachLine { line ->
                when {
                    line.startsWith("event:") -> {
                        currentEvent = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        if (currentData.isNotEmpty()) currentData.append("\n")
                        currentData.append(line.removePrefix("data:").trim())
                    }
                    line.isBlank() -> {
                        // End of SSE message block
                        val event = currentEvent
                        val data = currentData.toString()
                        currentEvent = null
                        currentData = StringBuilder()

                        if (event != null && data.isNotEmpty()) {
                            val result = handleSseMessage(event, data, onDelta)
                            if (result != null) {
                                finalReplies = result
                            }
                        }
                    }
                }
            }
        }

        return finalReplies ?: throw Exception("服务端未返回完整结果")
    }

    private fun handleSseMessage(
        event: String,
        dataText: String,
        onDelta: suspend (String) -> Unit
    ): List<String>? {
        return when (event) {
            "delta" -> {
                try {
                    val delta = gson.fromJson(dataText, StreamDelta::class.java)
                    if (!delta.text.isNullOrEmpty()) {
                        kotlinx.coroutines.runBlocking { onDelta(delta.text) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SSE delta parse error: ${e.message}")
                }
                null
            }
            "done" -> {
                try {
                    val done = gson.fromJson(dataText, StreamDoneReplies::class.java)
                    done.response?.replies?.take(3)
                } catch (e: Exception) {
                    Log.w(TAG, "SSE done parse error: ${e.message}")
                    // Try to parse replies from fullText as fallback
                    try {
                        val done = gson.fromJson(dataText, StreamDoneReplies::class.java)
                        done.fullText?.let { parseRepliesFromFullText(it) }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            "error" -> {
                Log.w(TAG, "SSE error event: $dataText")
                null
            }
            else -> null
        }
    }

    /**
     * Fallback: parse numbered replies from streaming fullText.
     * Matches iOS DeepSeekKeyboardAPI.previewReplies(fromFullText:).
     */
    private fun parseRepliesFromFullText(fullText: String): List<String> {
        return fullText.lines()
            .map { it.replace(Regex("^\\d+[.、)）]\\s*"), "").trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    private fun extractErrorMessage(body: String): String? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(body, Map::class.java) as? Map<String, Any>
            val message = map?.get("message")
            when (message) {
                is String -> message.takeIf { it.isNotEmpty() }
                is List<*> -> (message.firstOrNull() as? String)?.takeIf { it.isNotEmpty() }
                else -> (map?.get("error") as? String)?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }
}