package com.arche.threply.ime.model

enum class ImeAiMode {
    B,
    C;

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
    val isStreaming: Boolean = false
)

data class SuggestionState(
    val mode: ImeAiMode,
    val suggestions: List<ImeSuggestion>,
    val streamingPreview: String,
    val isLoading: Boolean,
    val errorMessage: String?
) {
    companion object {
        fun idle(mode: ImeAiMode = ImeAiMode.B): SuggestionState =
            SuggestionState(
                mode = mode,
                suggestions = emptyList(),
                streamingPreview = "",
                isLoading = false,
                errorMessage = null
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
