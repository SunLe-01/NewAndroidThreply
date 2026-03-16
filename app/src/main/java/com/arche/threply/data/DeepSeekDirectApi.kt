package com.arche.threply.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Direct DeepSeek API client (OpenAI-compatible format).
 * Used when user provides their own API key, bypassing the self-hosted backend.
 */
object DeepSeekDirectApi {

    private const val TAG = "DeepSeekDirectApi"
    private const val BASE_URL = "https://api.deepseek.com"
    private const val MODEL = "deepseek-chat"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Public: generate reply suggestions (streaming) ───────────────

    suspend fun generateReplies(
        context: Context,
        inputContext: String,
        styleDescriptor: String,
        onDelta: suspend (String) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val systemPrompt = buildString {
            append("你是一个智能回复助手。根据用户提供的聊天上下文，生成恰好3条简短自然的回复建议。")
            append("每条回复独占一行，不要编号，不要加引号，不要解释。")
            if (styleDescriptor.isNotBlank()) {
                append("风格要求：$styleDescriptor")
            }
        }

        val fullText = streamChat(context, systemPrompt, inputContext, 0.8, onDelta)
        parseReplies(fullText)
    }

    // ── Public: expand a parent suggestion into 3 variations ────────

    suspend fun generateExpansions(
        context: Context,
        parentText: String,
        rootContext: String,
        styleDescriptor: String,
        onDelta: suspend (String) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val systemPrompt = buildString {
            append("你是一个聊天回复改写助手。")
            append("用户正在和别人聊天，已经选中了一条准备发送给对方的回复候选。")
            append("请你基于这条回复候选的核心语义，改写出恰好3条不同版本。")
            append("要求：")
            append("1. 保持说话人方向不变——原文是[我]说给[对方]的，改写后仍然是[我]说给[对方]的。")
            append("2. 保留原文的承诺、意图和语义目的，只改变措辞、角度或语气。")
            append("3. 绝对不要把这条回复当成对方发来的消息去回答它。")
            append("4. 每条改写独占一行，不要编号，不要加引号，不要解释。")
            if (styleDescriptor.isNotBlank()) {
                append("风格要求：$styleDescriptor")
            }
        }

        val userMessage = buildString {
            if (rootContext.isNotBlank()) {
                append("聊天上下文（对方发来的消息）：\n$rootContext\n\n")
            }
            append("我选中的回复候选（请改写这条）：\n$parentText")
        }

        val fullText = streamChat(context, systemPrompt, userMessage, 0.8, onDelta)
        parseReplies(fullText)
    }

    // ── Public: text skills ──────────────────────────────────────────

    suspend fun translateText(
        context: Context,
        text: String,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        val system = "将以下文本翻译为${targetLanguage}，只输出翻译结果，不要解释。"
        chatOnce(context, system, text, 0.3)
    }

    suspend fun replaceText(
        context: Context,
        text: String
    ): String = withContext(Dispatchers.IO) {
        val system = "改写以下文本，保持原意但使用不同的表达方式，只输出改写结果，不要解释。"
        chatOnce(context, system, text, 0.8)
    }

    suspend fun polishText(
        context: Context,
        text: String
    ): String = withContext(Dispatchers.IO) {
        val system = "润色以下文本，改善语法和表达，保持原意，只输出润色结果，不要解释。"
        chatOnce(context, system, text, 0.5)
    }

    // ── Internal: non-streaming single chat ──────────────────────────

    private fun chatOnce(
        context: Context,
        systemPrompt: String,
        userMessage: String,
        temperature: Double
    ): String {
        val apiKey = PrefsManager.getDeepSeekApiKey(context)
        if (apiKey.isBlank()) throw Exception("请先在设置中填入 DeepSeek API Key")

        val body = buildRequestBody(systemPrompt, userMessage, temperature, stream = false)
        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw Exception(extractError(responseBody) ?: "DeepSeek 请求失败（${response.code}）")
        }

        val json = gson.fromJson(responseBody, JsonObject::class.java)
        return json.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString?.trim()
            ?: throw Exception("DeepSeek 返回格式异常")
    }

    // ── Internal: streaming chat ─────────────────────────────────────

    private fun streamChat(
        context: Context,
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        onDelta: suspend (String) -> Unit
    ): String {
        val apiKey = PrefsManager.getDeepSeekApiKey(context)
        if (apiKey.isBlank()) throw Exception("请先在设置中填入 DeepSeek API Key")

        val body = buildRequestBody(systemPrompt, userMessage, temperature, stream = true)
        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        Log.d(TAG, "SSE stream start")
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            response.close()
            throw Exception(extractError(errBody) ?: "DeepSeek 请求失败（${response.code}）")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("响应体为空")

        return parseOpenAiSse(reader, onDelta)
    }

    /**
     * Parse OpenAI-compatible SSE stream.
     * Format: `data: {"choices":[{"delta":{"content":"text"}}]}`
     * End:    `data: [DONE]`
     */
    private fun parseOpenAiSse(
        reader: BufferedReader,
        onDelta: suspend (String) -> Unit
    ): String {
        val accumulated = StringBuilder()

        reader.use { r ->
            r.forEachLine { line ->
                if (!line.startsWith("data: ")) return@forEachLine
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") return@forEachLine

                try {
                    val json = gson.fromJson(payload, JsonObject::class.java)
                    val content = json.getAsJsonArray("choices")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("delta")
                        ?.get("content")?.asString
                    if (!content.isNullOrEmpty()) {
                        accumulated.append(content)
                        kotlinx.coroutines.runBlocking { onDelta(content) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SSE parse error: ${e.message}")
                }
            }
        }

        return accumulated.toString()
    }

    // ── Utilities ────────────────────────────────────────────────────

    private fun buildRequestBody(
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        stream: Boolean
    ): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userMessage)
        )
        val body = mapOf(
            "model" to MODEL,
            "messages" to messages,
            "temperature" to temperature,
            "stream" to stream
        )
        return gson.toJson(body)
    }

    private fun parseReplies(fullText: String): List<String> {
        return fullText.lines()
            .map { it.replace(Regex("^\\d+[.、)）]\\s*"), "").trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    private fun extractError(body: String): String? {
        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.getAsJsonObject("error")?.get("message")?.asString
        } catch (_: Exception) {
            null
        }
    }
}