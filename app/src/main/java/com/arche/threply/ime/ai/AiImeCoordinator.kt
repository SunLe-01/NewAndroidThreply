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

    /** Set loading state (used when waiting for screenshot+OCR context read). */
    fun setLoading() {
        _state.value = _state.value.copy(
            isLoading = true,
            streamingPreview = "",
            errorMessage = null
        )
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
                errorMessage = null,
                replyStack = emptyList(),
                replyPathTrail = emptyList(),
                treeCache = emptyMap()
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

        cancel()
        activeJob = scope.launch {
            val mode = _state.value.mode
            // Reset reply tree on new root request; save rootContext for expansion
            _state.value = _state.value.copy(
                isLoading = true,
                streamingPreview = "",
                errorMessage = null,
                replyStack = emptyList(),
                replyPathTrail = emptyList(),
                treeCache = emptyMap(),
                rootContext = input
            )

            try {
                // Both B and C modes use the 2D style pad settings from PrefsManager
                val style = when (mode) {
                    ImeAiMode.B, ImeAiMode.C -> ReplyStyle(
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
                    val tone = if (mode == ImeAiMode.TRANSLATE) 0 else 1
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

    /**
     * Long-press a reply card at [index] in the current layer to expand child replies.
     * Matches iOS suggestionStack push behavior.
     */
    fun expandReplyAtIndex(index: Int, parentText: String) {
        val s = _state.value
        val pathKey = s.childPathKey(index)

        // Check cache first
        val cached = s.treeCache[pathKey]
        if (cached != null) {
            _state.value = s.copy(
                replyStack = s.replyStack + listOf(cached),
                replyPathTrail = s.replyPathTrail + index,
                streamingPreview = "",
                isLoading = false,
                errorMessage = null
            )
            Log.d(TAG, "Reply tree: loaded cached children for path=$pathKey")
            return
        }

        // Push placeholder layer and start generation
        val placeholders = listOf(
            ImeSuggestion(text = "", source = SuggestionSource.AI),
            ImeSuggestion(text = "", source = SuggestionSource.AI),
            ImeSuggestion(text = "", source = SuggestionSource.AI)
        )
        cancel()
        _state.value = s.copy(
            replyStack = s.replyStack + listOf(placeholders),
            replyPathTrail = s.replyPathTrail + index,
            isLoading = true,
            streamingPreview = "",
            errorMessage = null
        )

        activeJob = scope.launch {
            try {
                val style = ReplyStyle(
                    length = PrefsManager.getImeStyleLength(context).toDouble(),
                    temperature = PrefsManager.getImeStyleTemperature(context).toDouble()
                )
                val styleDescriptor = style.promptDescriptor

                val onDelta: suspend (String) -> Unit = { deltaText ->
                    val current = _state.value.streamingPreview
                    _state.value = _state.value.copy(
                        streamingPreview = current + deltaText
                    )
                }

                val useDeepSeek = PrefsManager.getDeepSeekApiKey(context).isNotBlank()
                val rootCtx = _state.value.rootContext
                val childReplies = if (useDeepSeek) {
                    DeepSeekDirectApi.generateExpansions(
                        context = context,
                        parentText = parentText,
                        rootContext = rootCtx,
                        styleDescriptor = styleDescriptor,
                        onDelta = onDelta
                    )
                } else {
                    // Backend path: build explicit rewrite instruction as inputContext
                    val rewriteContext = buildString {
                        if (rootCtx.isNotBlank()) {
                            append("聊天上下文（对方发来的消息）：\n$rootCtx\n\n")
                        }
                        append("以下是我准备发送给对方的一条回复候选，请改写成3条不同措辞的版本，")
                        append("保持说话人方向和语义不变，不要把它当成对方的消息来回复：\n$parentText")
                    }
                    BackendAiApi.generateBaseRepliesStream(
                        context = context,
                        inputContext = rewriteContext,
                        tone = 1,
                        styleDescriptor = styleDescriptor,
                        styleTemperature = style.temperature,
                        onDelta = onDelta
                    )
                }
                Log.d(TAG, "Reply tree expand: rootCtx=${rootCtx.take(50)}, parent=${parentText.take(50)}")

                val childSuggestions = childReplies
                    .filter { it.isNotBlank() }
                    .take(3)
                    .map { ImeSuggestion(text = it, source = SuggestionSource.AI) }

                // Update the top of the stack with real results and cache them
                val cur = _state.value
                val updatedStack = cur.replyStack.toMutableList()
                if (updatedStack.isNotEmpty()) {
                    updatedStack[updatedStack.lastIndex] = childSuggestions
                }
                _state.value = cur.copy(
                    replyStack = updatedStack,
                    treeCache = cur.treeCache + (pathKey to childSuggestions),
                    streamingPreview = if (childSuggestions.isNotEmpty()) childSuggestions.first().text else "",
                    isLoading = false,
                    errorMessage = null
                )
                Log.d(TAG, "Reply tree: generated ${childSuggestions.size} children for path=$pathKey")

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

    /** Pop one layer from the reply tree stack. */
    fun navigateBackInReplyTree() {
        val s = _state.value
        if (s.replyStack.isNotEmpty()) {
            cancel()
            _state.value = s.copy(
                replyStack = s.replyStack.dropLast(1),
                replyPathTrail = s.replyPathTrail.dropLast(1),
                streamingPreview = "",
                isLoading = false,
                expandedReplyId = null
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
