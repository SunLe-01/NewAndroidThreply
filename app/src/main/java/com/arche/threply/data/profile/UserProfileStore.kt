package com.arche.threply.data.profile

import android.content.Context
import android.util.Log
import com.arche.threply.data.DeepSeekDirectApi
import com.arche.threply.data.PrefsManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class UserProfile(
    @SerializedName("interests") val interests: List<String> = emptyList(),
    @SerializedName("catchphrases") val catchphrases: List<String> = emptyList(),
    @SerializedName("toneDescription") val toneDescription: String = "",
    @SerializedName("avoidRules") val avoidRules: List<String> = emptyList(),
    @SerializedName("manualSeeds") val manualSeeds: List<String> = emptyList(),
    @SerializedName("personalityTags") val personalityTags: List<String> = emptyList(),
    @SerializedName("favoriteThings") val favoriteThings: List<String> = emptyList(),
    @SerializedName("lastInferredAt") val lastInferredAt: Long = 0L
) {
    /** Build a compact prompt snippet (≤150 chars target). */
    fun toPromptSnippet(): String {
        val parts = mutableListOf<String>()
        if (personalityTags.isNotEmpty()) parts += "性格风格：${personalityTags.joinToString("、")}"
        val allInterests = (manualSeeds.filter { it.startsWith("#") }.map { it.removePrefix("#") } + interests).distinct()
        if (allInterests.isNotEmpty()) parts += "兴趣偏好：${allInterests.joinToString("、")}"
        if (catchphrases.isNotEmpty()) parts += "常用表达：${catchphrases.joinToString("、")}"
        if (toneDescription.isNotBlank()) parts += "语气倾向：$toneDescription"
        if (favoriteThings.isNotEmpty()) parts += "喜欢：${favoriteThings.joinToString("、")}"
        if (avoidRules.isNotEmpty()) parts += "避免：${avoidRules.joinToString("、")}"
        val seeds = manualSeeds.filter { !it.startsWith("#") }
        if (seeds.isNotEmpty()) parts += seeds.joinToString("；")
        if (parts.isEmpty()) return ""
        val body = parts.joinToString("；")
        val trimmed = if (body.length > 140) body.take(137) + "..." else body
        return "用户画像（仅作轻量表达偏置，不能偏离当前聊天语境）：\n- $trimmed\n要求：先保证回复符合当前聊天内容，只有在自然相关时才体现偏好。"
    }

    val isEmpty: Boolean get() = interests.isEmpty() && catchphrases.isEmpty() && toneDescription.isBlank() && manualSeeds.isEmpty() && personalityTags.isEmpty() && favoriteThings.isEmpty()
}

object UserProfileStore {
    private const val TAG = "UserProfileStore"
    private val gson = Gson()

    fun get(context: Context): UserProfile {
        val json = PrefsManager.getProfileJson(context)
        if (json.isBlank()) return UserProfile()
        return try {
            gson.fromJson(json, UserProfile::class.java) ?: UserProfile()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse profile: ${e.message}")
            UserProfile()
        }
    }

    fun save(context: Context, profile: UserProfile) {
        PrefsManager.setProfileJson(context, gson.toJson(profile))
    }

    fun clear(context: Context) {
        PrefsManager.setProfileJson(context, "")
    }

    /**
     * Infer user profile from recent reply history using DeepSeek.
     * Returns the inferred profile, or null on failure.
     */
    suspend fun inferFromHistory(context: Context): UserProfile? {
        val entries = ReplyHistoryStore.getRecent(context, 50)
        if (entries.size < 3) {
            Log.d(TAG, "Not enough history for inference (${entries.size} entries)")
            return null
        }

        val sampleText = entries.joinToString("\n") { "- ${it.selectedReply}" }
        val systemPrompt = buildString {
            append("分析以下用户选择过的聊天回复样本，提取用户的表达风格画像。")
            append("返回严格的 JSON，格式如下，不要输出任何其他内容：\n")
            append("""{"interests":["兴趣1","兴趣2"],"catchphrases":["口头禅1"],"toneDescription":"简洁描述语气风格","avoidRules":["避免项"]}""")
        }

        return try {
            val result = DeepSeekDirectApi.chatForProfile(context, systemPrompt, sampleText)
            val inferred = gson.fromJson(result, UserProfile::class.java)
                ?.copy(lastInferredAt = System.currentTimeMillis())
                ?: return null
            // Merge with existing explicit persona (manual seeds, personality tags, favorite things)
            val existing = get(context)
            val merged = inferred.copy(
                manualSeeds = existing.manualSeeds,
                personalityTags = existing.personalityTags,
                favoriteThings = existing.favoriteThings
            )
            save(context, merged)
            Log.d(TAG, "Profile inferred: interests=${merged.interests}, catchphrases=${merged.catchphrases}")
            merged
        } catch (e: Exception) {
            Log.w(TAG, "Profile inference failed: ${e.message}")
            null
        }
    }
}
