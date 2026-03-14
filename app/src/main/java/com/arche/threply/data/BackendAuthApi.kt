package com.arche.threply.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Backend authentication API.
 * Equivalent to iOS BackendAuthAPI.
 */
object BackendAuthApi {

    data class User(
        @SerializedName("displayName") val displayName: String?,
        @SerializedName("plan") val plan: String?
    )

    data class LoginResponse(
        @SerializedName("accessToken") val accessToken: String,
        @SerializedName("refreshToken") val refreshToken: String,
        @SerializedName("user") val user: User
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Login with Google ID token.
     * Equivalent to iOS loginWithApple().
     */
    suspend fun loginWithGoogle(
        baseUrl: String,
        idToken: String,
        googleUserId: String,
        displayName: String
    ): LoginResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/auth/google/login"
        val body = gson.toJson(
            mapOf(
                "idToken" to idToken,
                "googleUserId" to googleUserId,
                "displayName" to displayName
            )
        )

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val message = extractServerMessage(responseBody)
                ?: "登录失败（${response.code}）"
            throw Exception(message)
        }

        gson.fromJson(responseBody, LoginResponse::class.java)
    }

    /**
     * Request phone verification code from backend.
     */
    suspend fun sendPhoneCode(
        baseUrl: String,
        phoneNumber: String
    ) = withContext(Dispatchers.IO) {
        val normalized = phoneNumber.trim()
        if (normalized.length < 6) {
            throw Exception("手机号格式不正确")
        }

        val url = "${baseUrl.trimEnd('/')}/auth/phone/send-code"
        val body = gson.toJson(mapOf("phoneNumber" to normalized))

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val message = extractServerMessage(responseBody)
                ?: "发送验证码失败（${response.code}）"
            throw Exception(message)
        }
    }

    /**
     * Verify phone code and login.
     */
    suspend fun verifyPhoneCode(
        baseUrl: String,
        phoneNumber: String,
        code: String
    ): LoginResponse = withContext(Dispatchers.IO) {
        val normalizedPhone = phoneNumber.trim()
        val normalizedCode = code.trim()
        if (normalizedPhone.length < 6) {
            throw Exception("手机号格式不正确")
        }
        if (normalizedCode.length != 6) {
            throw Exception("验证码应为 6 位")
        }

        val url = "${baseUrl.trimEnd('/')}/auth/phone/verify"
        val body = gson.toJson(
            mapOf(
                "phoneNumber" to normalizedPhone,
                "code" to normalizedCode
            )
        )

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val message = extractServerMessage(responseBody)
                ?: "验证失败（${response.code}）"
            throw Exception(message)
        }

        gson.fromJson(responseBody, LoginResponse::class.java)
    }

    /**
     * Refresh access token using refresh token.
     */
    suspend fun refreshAccessToken(
        baseUrl: String,
        refreshToken: String
    ): LoginResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/auth/refresh"
        val body = gson.toJson(mapOf("refreshToken" to refreshToken))

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val message = extractServerMessage(responseBody)
                ?: "刷新登录状态失败（${response.code}）"
            throw Exception(message)
        }

        gson.fromJson(responseBody, LoginResponse::class.java)
    }

    private fun extractServerMessage(body: String): String? {
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
