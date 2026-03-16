package com.arche.threply.ime.model

enum class ImeAiMode {
    B,
    C,
    TRANSLATE;

    companion object {
        fun fromRaw(value: String?): ImeAiMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: B
    }
}

enum class SuggestionSource {
    AI,
    RIME
}

data class ImeSuggestion(
    val text: String,
    val source: SuggestionSource,
    val isStreaming: Boolean = false,
    val id: String = text.hashCode().toString(),
    val children: List<ImeSuggestion> = emptyList(),
    val parentId: String? = null
)

data class SuggestionState(
    val mode: ImeAiMode,
    val suggestions: List<ImeSuggestion>,
    val streamingPreview: String,
    val isLoading: Boolean,
    val errorMessage: String?,
    val translateSourceLanguage: String = "auto",
    val translateTargetLanguage: String = "en",
    /** Stack of reply layers: each layer is 3 suggestions. Root is index 0. */
    val replyStack: List<List<ImeSuggestion>> = emptyList(),
    /** Parallel path trail tracking which parent index was expanded at each depth. */
    val replyPathTrail: List<Int> = emptyList(),
    /** Cache: key = path (e.g. "0,2,1"), value = children for that node. */
    val treeCache: Map<String, List<ImeSuggestion>> = emptyMap(),
    val expandedReplyId: String? = null,
    // Keep for backward compat but deprecated
    val currentReplyTreePath: List<String> = emptyList()
) {
    /** Current layer's suggestions (top of stack, or root suggestions). */
    val currentLayerSuggestions: List<ImeSuggestion>
        get() = replyStack.lastOrNull() ?: suggestions

    /** Tree depth (0 = root). */
    val treeDepth: Int get() = replyStack.size

    /** Whether back button should show. */
    val showBackButton: Boolean get() = replyStack.isNotEmpty()

    /** Cache key for current path. */
    val currentPathKey: String
        get() = replyPathTrail.joinToString(",")

    fun childPathKey(index: Int): String {
        val parts = replyPathTrail.toMutableList()
        parts.add(index)
        return parts.joinToString(",")
    }

    companion object {
        fun idle(mode: ImeAiMode = ImeAiMode.B): SuggestionState =
            SuggestionState(
                mode = mode,
                suggestions = emptyList(),
                streamingPreview = "",
                isLoading = false,
                errorMessage = null,
                translateSourceLanguage = "auto",
                translateTargetLanguage = "en",
                replyStack = emptyList(),
                replyPathTrail = emptyList(),
                treeCache = emptyMap(),
                expandedReplyId = null,
                currentReplyTreePath = emptyList()
            )
    }
}

data class ImeTriggerPayload(
    val draft: String,
    val source: String,
    val mode: ImeAiMode,
    val createdAt: Long,
    val version: Long
)

/**
 * 2D style control matching iOS ReplyStyle.
 * length: -1 (shorter) to 1 (longer)
 * temperature: -1 (colder/formal) to 1 (warmer/casual)
 */
data class ReplyStyle(
    val length: Double = 0.0,
    val temperature: Double = 0.0
) {
    val promptDescriptor: String
        get() {
            val lengthText = descriptor(length, "回复更长", "回复更短", "长度适中")
            val temperatureText = descriptor(temperature, "语气更温暖", "语气更克制", "语气自然")
            return "$lengthText，$temperatureText"
        }

    companion object {
        val NEUTRAL = ReplyStyle(0.0, 0.0)

        private fun descriptor(value: Double, positive: String, negative: String, neutral: String): String {
            return when {
                value > 0.15 -> positive
                value < -0.15 -> negative
                else -> neutral
            }
        }
    }
}
