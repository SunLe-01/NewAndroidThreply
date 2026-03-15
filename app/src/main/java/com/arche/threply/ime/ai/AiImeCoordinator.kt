package com.arche.threply.ime.ai

import android.content.Context
import android.util.Log
import com.arche.threply.data.BackendAiApi
import com.arche.threply.data.BackendSessionStore
import com.arche.threply.data.DeepSeekDirectApi
import com.arche.threply.data.PrefsManager
import com.arche.threply.ime.model.ImeAiMode
import com.arche.threply.ime.model.ImeSuggestion
import com.arche.threply.ime.model.ReplyStyle
import com.arche.threply.ime.model.SuggestionSource
import com.arche.threply.ime.model.SuggestionState
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiImeCoordinator(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gson = Gson()

    private val _state = MutableStateFlow(
        SuggestionState.idle(
            ImeAiMode.fromRaw(PrefsManager.getImeAiMode(context))
        ).copy(
            translateSourceLanguage = PrefsManager.getTranslateSourceLanguage(context),
            translateTargetLanguage = PrefsManager.getTranslateTargetLanguage(context)
        )
    )
    val state: StateFlow<SuggestionState> = _state.asStateFlow()

    private var activeJob: Job? = null

    companion object {
        private const val TAG = "AiImeCoordinator"
    }

    fun setMode(mode: ImeAiMode) {
        PrefsManager.setImeAiMode(context, mode.name)
        _state.value = _state.value.copy(mode = mode, errorMessage = null)
    }

    fun setTranslateSourceLanguage(languageCode: String) {
        _state.value = _state.value.copy(translateSourceLanguage = languageCode)
        PrefsManager.setTranslateSourceLanguage(context, languageCode)
    }

    fun setTranslateTargetLanguage(languageCode: String) {
        _state.value = _state.value.copy(translateTargetLanguage = languageCode)
        PrefsManager.setTranslateTargetLanguage(context, languageCode)
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = _state.value.copy(isLoading = false, streamingPreview = "")
    }

    fun requestSuggestions(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) {
            _state.value = _state.value.copy(
                suggestions = emptyList(),
                streamingPreview = "",
                isLoading = false,
                errorMessage = null
            )
            return
        }

        if (!PrefsManager.isImeAiEnabled(context)) {
            _state.value = _state.value.copy(
                suggestions = emptyList(),
                streamingPreview = "",
                isLoading = false,
                errorMessage = "AI 模式已关闭"
            )
            return
        }

        // TODO: restore login gate before release
        // if (!BackendSessionStore.isReadyForImeAi(context)) { ... }

        cancel()
        activeJob = scope.launch {
            val mode = _state.value.mode
            _state.value = _state.value.copy(
                isLoading = true,
                streamingPreview = "",
                errorMessage = null
            )

            try {
                // B mode: neutral style; C mode: read from 2D pad stored in PrefsManager
                val style = when (mode) {
                    ImeAiMode.B -> ReplyStyle.NEUTRAL
                    ImeAiMode.C -> ReplyStyle(
                        length = PrefsManager.getImeStyleLength(context).toDouble(),
                        temperature = PrefsManager.getImeStyleTemperature(context).toDouble()
                    )
                    ImeAiMode.TRANSLATE -> ReplyStyle.NEUTRAL
                }
                val styleDescriptor = style.promptDescriptor

                val onDelta: suspend (String) -> Unit = { deltaText ->
                    val current = _state.value.streamingPreview
                    _state.value = _state.value.copy(
                        streamingPreview = current + deltaText
                    )
                }

                val useDeepSeek = PrefsManager.getDeepSeekApiKey(context).isNotBlank()
                val replies = if (useDeepSeek) {
                    DeepSeekDirectApi.generateReplies(
                        context = context,
                        inputContext = input,
                        styleDescriptor = styleDescriptor,
                        onDelta = onDelta
                    )
                } else {
                    val tone = if (mode == ImeAiMode.B) 0 else 1
                    BackendAiApi.generateBaseRepliesStream(
                        context = context,
                        inputContext = input,
                        tone = tone,
                        styleDescriptor = styleDescriptor,
                        styleTemperature = style.temperature,
                        onDelta = onDelta
                    )
                }

                val finalSuggestions = replies
                    .filter { it.isNotBlank() }
                    .take(3)
                    .map { ImeSuggestion(text = it, source = SuggestionSource.AI) }

                _state.value = _state.value.copy(
                    suggestions = finalSuggestions,
                    streamingPreview = if (finalSuggestions.isNotEmpty()) finalSuggestions.first().text else "",
                    isLoading = false,
                    errorMessage = null
                )

                PrefsManager.setImeSuggestionCache(
                    context,
                    gson.toJson(finalSuggestions.map { it.text })
                )
                BackendSessionStore.saveImeAiRequestMeta(context, System.currentTimeMillis())
                Log.d(TAG, "AI suggestions received: ${finalSuggestions.size}")

            } catch (e: CancellationException) {
                throw e // Don't swallow coroutine cancellation
            } catch (e: Exception) {
                Log.w(TAG, "AI suggestion request failed: ${e.message}")
                _state.value = _state.value.copy(
                    suggestions = emptyList(),
                    streamingPreview = "",
                    isLoading = false,
                    errorMessage = e.message ?: "请求失败，请重试"
                )
            }
        }
    }

    fun requestTranslation(text: String, sourceLanguage: String, targetLanguage: String) {
        if (text.isEmpty()) {
            _state.value = _state.value.copy(
                suggestions = emptyList(),
                streamingPreview = "",
                isLoading = false,
                errorMessage = "请输入要翻译的文本"
            )
            return
        }

        cancel()
        activeJob = scope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                streamingPreview = "",
                errorMessage = null
            )

            try {
                val useDeepSeek = PrefsManager.getDeepSeekApiKey(context).isNotBlank()
                val translatedText = if (useDeepSeek) {
                    DeepSeekDirectApi.translateText(
                        context = context,
                        text = text,
                        targetLanguage = getLanguageName(targetLanguage)
                    )
                } else {
                    BackendAiApi.translateText(
                        context = context,
                        text = text,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage
                    )
                }

                val suggestion = ImeSuggestion(text = translatedText, source = SuggestionSource.AI)
                _state.value = _state.value.copy(
                    suggestions = listOf(suggestion),
                    streamingPreview = translatedText,
                    isLoading = false,
                    errorMessage = null
                )

                Log.d(TAG, "Translation completed: $sourceLanguage -> $targetLanguage")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Translation failed: ${e.message}")
                _state.value = _state.value.copy(
                    suggestions = emptyList(),
                    streamingPreview = "",
                    isLoading = false,
                    errorMessage = e.message ?: "翻译失败，请重试"
                )
            }
        }
    }

    fun expandReplyForChildren(parentReplyId: String, parentText: String) {
        cancel()
        activeJob = scope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                streamingPreview = "",
                errorMessage = null,
                expandedReplyId = parentReplyId
            )

            try {
                val style = ReplyStyle.NEUTRAL
                val styleDescriptor = style.promptDescriptor

                val onDelta: suspend (String) -> Unit = { deltaText ->
                    val current = _state.value.streamingPreview
                    _state.value = _state.value.copy(
                        streamingPreview = current + deltaText
                    )
                }

                val useDeepSeek = PrefsManager.getDeepSeekApiKey(context).isNotBlank()
                val childReplies = if (useDeepSeek) {
                    DeepSeekDirectApi.generateReplies(
                        context = context,
                        inputContext = parentText,
                        styleDescriptor = styleDescriptor,
                        onDelta = onDelta
                    )
                } else {
                    BackendAiApi.generateBaseRepliesStream(
                        context = context,
                        inputContext = parentText,
                        tone = 0,
                        styleDescriptor = styleDescriptor,
                        styleTemperature = 0.0,
                        onDelta = onDelta
                    )
                }

                val childSuggestions = childReplies
                    .filter { it.isNotBlank() }
                    .take(3)
                    .map { ImeSuggestion(text = it, source = SuggestionSource.AI, parentId = parentReplyId) }

                // Update the parent suggestion with children
                val updatedSuggestions = _state.value.suggestions.map { suggestion ->
                    if (suggestion.id == parentReplyId) {
                        suggestion.copy(children = childSuggestions)
                    } else {
                        suggestion
                    }
                }

                _state.value = _state.value.copy(
                    suggestions = updatedSuggestions,
                    streamingPreview = if (childSuggestions.isNotEmpty()) childSuggestions.first().text else "",
                    isLoading = false,
                    errorMessage = null
                )

                Log.d(TAG, "Child replies generated: ${childSuggestions.size}")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate child replies: ${e.message}")
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "生成子回复失败，请重试"
                )
            }
        }
    }

    fun navigateBackInReplyTree() {
        val currentPath = _state.value.currentReplyTreePath
        if (currentPath.isNotEmpty()) {
            _state.value = _state.value.copy(
                currentReplyTreePath = currentPath.dropLast(1),
                expandedReplyId = null,
                streamingPreview = ""
            )
        }
    }

    private fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            "auto" -> "自动检测"
            "zh" -> "中文"
            "en" -> "英文"
            "ja" -> "日文"
            "ko" -> "韩文"
            "es" -> "西班牙文"
            "fr" -> "法文"
            "de" -> "德文"
            else -> languageCode
        }
    }
}
