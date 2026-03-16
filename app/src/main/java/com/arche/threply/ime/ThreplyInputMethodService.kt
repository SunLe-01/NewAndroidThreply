package com.arche.threply.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.arche.threply.data.BackendAiApi
import com.arche.threply.data.BackendSessionStore
import com.arche.threply.data.DeepSeekDirectApi
import com.arche.threply.data.PrefsManager
import com.arche.threply.ime.ai.AiImeCoordinator
import com.arche.threply.ime.model.ImeAiMode
import com.arche.threply.ime.model.ImeSuggestion
import com.arche.threply.ime.model.SuggestionSource
import com.arche.threply.ime.pinyin.PinyinComposer
import com.arche.threply.ime.rime.RimeEngineController
import com.arche.threply.ime.rime.RimeFallbackLexicon
import com.arche.threply.ime.rime.RimeResourceManager
import com.arche.threply.ime.trigger.SharedTriggerStore
import com.arche.threply.ime.compose.ImeAiOverlay
import com.arche.threply.ime.compose.ImeLifecycleOwner
import com.arche.threply.ime.model.SuggestionState
import com.arche.threply.screenshot.ChatScanAccessibilityService
import com.arche.threply.ime.context.ChatContextHolder
import com.arche.threply.util.HapticsUtil
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ThreplyInputMethodService : InputMethodService() {
    companion object {
        private const val TAG = "ThreplyIME"
    }

    private enum class KeyStyle {
        Normal,
        Action,
        Accent
    }

    private enum class TextSkill { TRANSLATE, REPLACE, POLISH }

    private enum class InputLanguage {
        EN,
        ZH_PINYIN
    }

    private data class KeySpec(
        val label: String,
        val output: String? = null,
        val hint: String? = null,
        val weight: Float = 1f,
        val style: KeyStyle = KeyStyle.Normal,
        val onTap: (() -> Unit)? = null,
        val repeatOnLongPress: Boolean = false
    )

    private var isUppercase = false
    private var isSymbolMode = false
    private var inputLanguage: InputLanguage = InputLanguage.EN

    private lateinit var keyboardRoot: LinearLayout
    private lateinit var fallbackPanel: View
    private lateinit var suggestionContainer: LinearLayout
    private lateinit var streamingPreviewView: TextView
    private lateinit var composingBarView: TextView
    private lateinit var skillBarView: View
    private lateinit var expandedCandidatePanel: LinearLayout
    private lateinit var aiPanelView: View
    private lateinit var aiPanelWrapper: LinearLayout

    private val englishBuffer = StringBuilder()
    private var isSkillBarVisible = false
    private var selectionTriggeredSkillBar = false
    private var isExpandedPanelVisible = false

    private var lastSelStart = -1
    private var lastSelEnd = -1
    private var isSettingComposingText = false

    /** Stores undo context after a Replace/Polish operation is committed. */
    private data class UndoContext(val originalText: String, val replacedText: String, val skill: TextSkill)
    private var pendingUndo: UndoContext? = null

    /** Timestamp of last space commit for double-space → period detection. */
    private var lastSpaceTimeMs: Long = 0L

    /** Handler for context read timeout. */
    private val contextReadTimeoutHandler = Handler(Looper.getMainLooper())

    /** ASCII → Chinese full-width punctuation mapping for ZH_PINYIN mode. */
    private val zhPunctuationMap = mapOf(
        "," to "\uFF0C",   // ，
        "." to "\u3002",   // 。
        "?" to "\uFF1F",   // ？
        "!" to "\uFF01",   // ！
        ":" to "\uFF1A",   // ：
        ";" to "\uFF1B",   // ；
        "(" to "\uFF08",   // （
        ")" to "\uFF09",   // ）
        "\"" to "\u201C",  // " (left double quotation)
        "'" to "\u2018",   // ' (left single quotation)
        "[" to "\u3010",   // 【
        "]" to "\u3011",   // 】
        "<" to "\u300A",   // 《
        ">" to "\u300B",   // 》
        "\\" to "\u3001",  // 、(enumeration comma)
        "/" to "\u3001",   // 、
        "~" to "\uFF5E",   // ～
        "@" to "\uFF20",   // ＠
        "#" to "\uFF03",   // ＃
        "$" to "\uFFE5",   // ￥
        "^" to "\u2026",   // … (ellipsis)
        "&" to "\uFF06",   // ＆
        "-" to "\u2014",   // — (em dash)
        "_" to "\u2014"    // — (em dash)
    )

    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val aiCoordinator by lazy(LazyThreadSafetyMode.NONE) { AiImeCoordinator(this) }
    private val rimeController = RimeEngineController()
    private val pinyinComposer = PinyinComposer()

    private var stateJob: Job? = null
    private var aiMode: ImeAiMode = ImeAiMode.B
    private var latestAiSuggestions: List<ImeSuggestion> = emptyList()
    private val imeLifecycleOwner = ImeLifecycleOwner()

    override fun onCreateInputView(): View {
        return runCatching { createInputViewContent() }
            .getOrElse { throwable ->
                Log.e(TAG, "Failed to create input view, using emergency keyboard", throwable)
                createEmergencyInputView()
            }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        // Some emulators and devices suppress soft IME when they think a hardware keyboard exists.
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Games and immersive apps behave better with an inline keyboard than Android's extract UI.
        return false
    }

    private fun createInputViewContent(): View {
        aiMode = ImeAiMode.fromRaw(PrefsManager.getImeAiMode(this))
        inputLanguage = readInputLanguage()

        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFDCDDDF.toInt())
            setPadding(dp(5), dp(6), dp(5), dp(6))
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    val root = v.rootView
                    root.setViewTreeLifecycleOwner(imeLifecycleOwner)
                    root.setViewTreeSavedStateRegistryOwner(imeLifecycleOwner)
                    imeLifecycleOwner.onCreate()
                    imeLifecycleOwner.onResume()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    imeLifecycleOwner.onPause()
                    imeLifecycleOwner.onStop()
                }
            })
        }
        fallbackPanel = createFallbackPanel()
        keyboardRoot.addView(fallbackPanel)
        aiPanelView = createAiPanel(fallbackPanel)
        aiPanelWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(aiPanelView)
        }
        keyboardRoot.addView(aiPanelWrapper)
        rebuildKeyboard()
        runCatching { observeAiState() }
            .onFailure { Log.e(TAG, "Failed to observe AI state", it) }
        return keyboardRoot
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setExtractViewShown(false)
        isUppercase = false
        isSymbolMode = false
        aiMode = ImeAiMode.fromRaw(PrefsManager.getImeAiMode(this))
        inputLanguage = readInputLanguage()
        pinyinComposer.clear()
        englishBuffer.clear()
        rimeController.resetPinyinPage()
        lastSelStart = -1
        lastSelEnd = -1

        runCatching {
            if (PrefsManager.isImeRimeEnabled(this)) {
                val schema = PrefsManager.getImeRimeSchema(this)
                val wantsNativeRime = PrefsManager.isImeRimeNativeEnabled(this)
                val dirs = if (wantsNativeRime) {
                    RimeResourceManager.getPreparedDirectories(this)
                } else {
                    null
                }
                if (wantsNativeRime && dirs == null) {
                    RimeResourceManager.warmUpAsync(this)
                }

                rimeController.initialize(
                    schema = schema,
                    nativeEnabled = wantsNativeRime && dirs != null,
                    sharedDataDir = dirs?.sharedDataDir,
                    userDataDir = dirs?.userDataDir
                )
            } else {
                rimeController.initialize(
                    schema = PrefsManager.getImeRimeSchema(this),
                    nativeEnabled = false
                )
            }
            rimeController.onStartInput()
        }.onFailure {
            Log.e(TAG, "Failed during input startup, falling back to non-native mode", it)
            rimeController.initialize(
                schema = PrefsManager.getImeRimeSchema(this),
                nativeEnabled = false
            )
            rimeController.onStartInput()
        }

        runCatching { consumePendingTriggerIfNeeded() }
            .onFailure { Log.e(TAG, "Failed to consume pending trigger", it) }
        rebuildKeyboard()
        runCatching { refreshSuggestionTray() }
            .onFailure { Log.e(TAG, "Failed to refresh suggestion tray", it) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        rimeController.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        // Skip processing if we're the ones setting composing text
        if (isSettingComposingText) {
            isSettingComposingText = false
            lastSelStart = newSelStart
            lastSelEnd = newSelEnd
            return
        }

        // Detect external text changes: if cursor position changed and we have pinyin buffer, clear it
        val cursorMoved = (newSelStart != lastSelStart || newSelEnd != lastSelEnd)
        if (cursorMoved && inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            // External change detected — reset pinyin state
            pinyinComposer.clear()
            rimeController.resetPinyinPage()
            currentInputConnection?.setComposingText("", 0)
            streamingPreviewView.text = ""
            updateComposingBar()
            refreshSuggestionTray()
        }
        lastSelStart = newSelStart
        lastSelEnd = newSelEnd

        // Handle text selection for auto-showing skill bar
        val hasSelection = newSelEnd > newSelStart
        if (hasSelection) {
            // Text is selected — auto-show skill bar if not already visible
            if (!isSkillBarVisible) {
                isSkillBarVisible = true
                selectionTriggeredSkillBar = true
                if (::skillBarView.isInitialized) {
                    skillBarView.visibility = View.VISIBLE
                }
                refreshSuggestionTray()
            }
        } else {
            // Selection cleared — auto-hide skill bar only if we were the ones who opened it
            if (selectionTriggeredSkillBar && isSkillBarVisible) {
                isSkillBarVisible = false
                selectionTriggeredSkillBar = false
                if (::skillBarView.isInitialized) {
                    skillBarView.visibility = View.GONE
                }
                refreshSuggestionTray()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stateJob?.cancel()
        aiCoordinator.cancel()
        imeScope.cancel()
        rimeController.release()
        imeLifecycleOwner.onDestroy()
        unregisterScanResultReceiver()
    }

    private fun createEmergencyInputView(): View {
        aiMode = ImeAiMode.fromRaw(PrefsManager.getImeAiMode(this))
        inputLanguage = readInputLanguage()
        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFDCDDDF.toInt())
            setPadding(dp(5), dp(6), dp(5), dp(6))
        }
        fallbackPanel = createFallbackPanel()
        keyboardRoot.addView(fallbackPanel)
        // Add hidden placeholder to keep same child index structure as normal view
        aiPanelWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        keyboardRoot.addView(aiPanelWrapper)
        rebuildKeyboard()
        return keyboardRoot
    }

    private fun createAiPanel(fallbackPanel: View): View {
        return runCatching {
            // Don't initialize lifecycle here - will be done when view is attached

            ComposeView(this).apply {
                // Use DisposeOnDetachedFromWindow instead of DisposeOnViewTreeLifecycleDestroyed
                // because InputMethodService doesn't have a proper ViewTree lifecycle
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

                setContent {
                    val state by aiCoordinator.state.collectAsState()
                    ImeAiOverlay(
                        state = state,
                        onModeChange = { mode -> setAiMode(mode) },
                        onRefresh = { triggerAiFromCurrentInput() },
                        onExit = { exitAiPanel() },
                        onSelectReply = { text -> commitSuggestion(text) },
                        onStyleConfirm = { length, temp ->
                            PrefsManager.setImeStyle(this@ThreplyInputMethodService, length, temp)
                            setAiMode(ImeAiMode.B)
                            triggerAiFromCurrentInput()
                        },
                        onScan = { triggerChatScan() },
                        onSourceLanguageChange = { lang -> 
                            aiCoordinator.setTranslateSourceLanguage(lang)
                        },
                        onTargetLanguageChange = { lang -> 
                            aiCoordinator.setTranslateTargetLanguage(lang)
                        },
                        onTranslate = { source, target ->
                            val textToTranslate = getSelectedTextOrCurrentInput()
                            if (textToTranslate.isNotEmpty()) {
                                aiCoordinator.requestTranslation(textToTranslate, source, target)
                            }
                        },
                        onExpandReplyAtIndex = { index, replyText ->
                            aiCoordinator.expandReplyAtIndex(index, replyText)
                        },
                        onNavigateBack = {
                            aiCoordinator.navigateBackInReplyTree()
                        },
                        selectedText = getSelectedTextOrCurrentInput()
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "Failed to create AI panel, using plain suggestion panel", it)
            // Cleanup lifecycle on failure
            try {
                imeLifecycleOwner.onDestroy()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cleanup lifecycle after AI panel creation failure", e)
            }
            fallbackPanel
        }
    }

    private fun createFallbackPanel(): View {
        // Initialize streamingPreviewView as a hidden placeholder (still needed for skill results)
        streamingPreviewView = TextView(this).apply {
            text = ""
            textSize = 0f
            setPadding(0, 0, 0, 0)
            visibility = View.GONE
        }

        // Composing bar: shows real-time pinyin/english input buffer above candidate tray
        composingBarView = TextView(this).apply {
            text = ""
            textSize = 15f
            setTextColor(0xFF2D3644.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setBackgroundColor(0xFFF0F1F3.toInt())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Candidate tray: horizontal LinearLayout inside a HorizontalScrollView for unlimited scrolling
        suggestionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }

        val scrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(suggestionContainer)
        }

        skillBarView = createSkillBar()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(streamingPreviewView)
            addView(composingBarView)
            addView(skillBarView)
            addView(scrollView)
        }
    }

    private fun exitAiPanel() {
        // Hide AI panel and restore fallback panel (skill bar + candidates)
        if (::aiPanelWrapper.isInitialized) {
            aiPanelWrapper.visibility = View.GONE
        }
        if (::fallbackPanel.isInitialized) {
            fallbackPanel.visibility = View.VISIBLE
        }
        rebuildKeyboard()
    }

    /** Toggles the AI panel (B/C/Translate overlay) on or off. */
    private fun toggleAiPanel() {
        if (!::aiPanelWrapper.isInitialized) return
        HapticsUtil.impactMedium(this)
        if (aiPanelWrapper.visibility == View.VISIBLE) {
            exitAiPanel()
        } else {
            if (aiMode == ImeAiMode.B && ChatContextHolder.isAccessibilityServiceEnabled(this)) {
                // B mode: always clear stale cache and force fresh screenshot+OCR
                ChatContextHolder.clear()
                requestHideSelf(0)
                Handler(Looper.getMainLooper()).postDelayed({
                    requestChatContextAndTrigger()
                }, 400)
            } else {
                setAiMode(aiMode)
                triggerAiFromCurrentInput()
                rebuildKeyboard()
            }
        }
    }

    private fun observeAiState() {
        stateJob?.cancel()
        stateJob = imeScope.launch {
            aiCoordinator.state.collectLatest { state ->
                aiMode = state.mode
                latestAiSuggestions = state.suggestions
                // Compose UI observes state directly; only update pinyin tray here
                refreshSuggestionTray()
            }
        }
    }

    private fun refreshSuggestionTray() {
        if (!::suggestionContainer.isInitialized) return
        suggestionContainer.removeAllViews()

        val merged = when {
            inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank() -> {
                val pinyin = pinyinComposer.currentRaw()
                streamingPreviewView.text = "拼音：$pinyin"
                // Fetch up to 20 candidates — all displayed via horizontal scroll, no paging needed
                val candidates = currentPinyinCandidates(limit = 20)
                candidates.map {
                    ImeSuggestion(text = it, source = SuggestionSource.RIME, isStreaming = false)
                }
            }
            latestAiSuggestions.isNotEmpty() -> latestAiSuggestions
            else -> {
                val fallback = rimeController.suggest(PrefsManager.getImeLastInputContext(this))
                fallback.take(8).map { ImeSuggestion(it, SuggestionSource.RIME, false) }
            }
        }

        if (merged.isEmpty()) {
            suggestionContainer.addView(createMutedHint("暂无建议"))
            return
        }

        // All chips are added in sequence; the parent HorizontalScrollView handles overflow
        // Leading "/" button to toggle the skill bar
        suggestionContainer.addView(createSlashButton())
        merged.forEachIndexed { index, suggestion ->
            suggestionContainer.addView(createSuggestionChip(suggestion, isHighlighted = index == 0))
        }
        // Trailing expand button — only shown when there are candidates to expand
        if (merged.isNotEmpty()) {
            suggestionContainer.addView(createExpandButton())
        }
        // Keep expanded panel in sync with current candidates
        updateExpandedPanel(merged)
    }

    private fun currentPinyinCandidates(limit: Int = 20): List<String> {
        val pinyin = pinyinComposer.currentRaw().trim().lowercase()
        if (pinyin.isBlank()) return emptyList()

        val safeLimit = limit.coerceIn(1, 30)

        val rimeCandidates = rimeController.suggestFromPinyin(pinyin)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && containsHanOrPunctuation(it) }
            .distinct()
            .toList()
        if (rimeCandidates.isNotEmpty()) return rimeCandidates.take(safeLimit)

        // Fetch all candidates from fallback lexicon (page 0 only — scroll replaces pagination)
        val fallbackCandidates = RimeFallbackLexicon.lookup(
            pinyin = pinyin,
            limit = safeLimit,
            page = 0
        ).filter { containsHanOrPunctuation(it) }
        if (fallbackCandidates.isNotEmpty()) return fallbackCandidates

        val localCandidates = pinyinComposer.candidates()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && containsHanOrPunctuation(it) }
            .distinct()
            .toList()
        if (localCandidates.isNotEmpty()) return localCandidates.take(safeLimit)

        return emptyList()
    }

    private fun containsHanOrPunctuation(text: String): Boolean {
        return text.any { ch ->
            Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN ||
                    ch in "，。！？；：（）“”‘’、《》【】—…·"
        }
    }

    /** The expand/collapse button at the right end of the candidate tray. */
    private fun createExpandButton(): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(if (isExpandedPanelVisible) 0xFF3D5A80.toInt() else 0xFFE5EAF3.toInt())
            setStroke(dp(1), if (isExpandedPanelVisible) 0xFF2D4A6E.toInt() else 0xFFB5BECF.toInt())
        }
        return TextView(this).apply {
            text = if (isExpandedPanelVisible) "⊟" else "⊞"
            textSize = 15f
            setTextColor(if (isExpandedPanelVisible) 0xFFFFFFFF.toInt() else 0xFF3A475A.toInt())
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
            setOnClickListener { toggleExpandedPanel() }
        }
    }

    /** Toggles the expanded candidate grid panel. */
    private fun toggleExpandedPanel() {
        isExpandedPanelVisible = !isExpandedPanelVisible
        if (::expandedCandidatePanel.isInitialized) {
            expandedCandidatePanel.visibility =
                if (isExpandedPanelVisible) View.VISIBLE else View.GONE
        }
        // Hide/show keyboard rows (index 1 onwards, except the last which is expandedCandidatePanel)
        if (::keyboardRoot.isInitialized) {
            val lastIndex = keyboardRoot.childCount - 1
            for (i in 1 until lastIndex) {
                keyboardRoot.getChildAt(i)?.visibility =
                    if (isExpandedPanelVisible) View.GONE else View.VISIBLE
            }
        }
        refreshSuggestionTray()
    }

    /**
     * Rebuilds the expanded candidate grid with the current merged suggestions.
     * Full-screen layout: left side = 4-column candidate grid, right side = action buttons.
     */
    private fun updateExpandedPanel(suggestions: List<ImeSuggestion>) {
        if (!::expandedCandidatePanel.isInitialized) return
        expandedCandidatePanel.removeAllViews()
        if (suggestions.isEmpty() || !isExpandedPanelVisible) return

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Left: candidate grid (4 columns)
        val gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val columnsPerRow = 4
        var currentRow: LinearLayout? = null

        suggestions.forEachIndexed { index, suggestion ->
            if (index % columnsPerRow == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    ).apply { bottomMargin = dp(4) }
                }
                gridContainer.addView(currentRow)
            }

            val chipBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(if (index == 0) 0xFF3D5A80.toInt() else 0xFFFFFFFF.toInt())
                setStroke(dp(1), if (index == 0) 0xFF2D4A6E.toInt() else 0xFFD0D3D8.toInt())
            }
            val chip = TextView(this).apply {
                text = suggestion.text
                textSize = 18f
                setTextColor(if (index == 0) 0xFFFFFFFF.toInt() else 0xFF2D3644.toInt())
                gravity = Gravity.CENTER
                background = chipBg
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dp(4)
                }
                setOnClickListener {
                    isExpandedPanelVisible = false
                    expandedCandidatePanel.visibility = View.GONE
                    // Restore keyboard rows
                    if (::keyboardRoot.isInitialized) {
                        val lastIndex = keyboardRoot.childCount - 1
                        for (i in 1 until lastIndex) {
                            keyboardRoot.getChildAt(i)?.visibility = View.VISIBLE
                        }
                    }
                    commitSuggestion(suggestion.text)
                }
            }
            currentRow?.addView(chip)
        }

        // Fill last row with empty spacers if needed
        val remainder = suggestions.size % columnsPerRow
        if (remainder != 0) {
            repeat(columnsPerRow - remainder) {
                currentRow?.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
                })
            }
        }

        // Right: action button column (返回/删除/收起)
        val actionColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(4), dp(6), dp(6), dp(6))
        }

        val actionButtons = listOf(
            Triple("返回", "↵") { handleEnter() },
            Triple("删除", "⌫") { handleBackspace() },
            Triple("收起", "⊟") { toggleExpandedPanel() }
        )

        actionButtons.forEach { (label, icon, action) ->
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(0xFFE5EAF3.toInt())
                setStroke(dp(1), 0xFFB5BECF.toInt())
            }
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply { bottomMargin = dp(6) }
                setOnClickListener { action() }
            }
            val iconView = TextView(this).apply {
                text = icon
                textSize = 20f
                setTextColor(0xFF3A475A.toInt())
                gravity = Gravity.CENTER
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(0xFF556072.toInt())
                gravity = Gravity.CENTER
            }
            btn.addView(iconView)
            btn.addView(labelView)
            actionColumn.addView(btn)
        }

        mainRow.addView(gridContainer)
        mainRow.addView(actionColumn)
        expandedCandidatePanel.addView(mainRow)
    }

    /** The "/" button in the candidate tray that toggles the skill bar. */
    private fun createSlashButton(): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(if (isSkillBarVisible) 0xFF3D5A80.toInt() else 0xFFE5EAF3.toInt())
            setStroke(dp(1), if (isSkillBarVisible) 0xFF2D4A6E.toInt() else 0xFFB5BECF.toInt())
        }
        return TextView(this).apply {
            text = "/"
            textSize = 15f
            setTextColor(if (isSkillBarVisible) 0xFFFFFFFF.toInt() else 0xFF3A475A.toInt())
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            setOnClickListener { toggleSkillBar() }
        }
    }

    /** Builds the skill bar: Translate / Replace / Polish cards. Hidden by default. */
    private fun createSkillBar(): View {
        val skills = listOf(
            Triple("翻译", "Translate", TextSkill.TRANSLATE),
            Triple("替换", "Replace",   TextSkill.REPLACE),
            Triple("润色", "Polish",    TextSkill.POLISH)
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFFF7F8FA.toInt())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        skills.forEach { (label, sublabel, skill) ->
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(0xFFEAF2FF.toInt())
                setStroke(dp(1), 0xFFAFC8E8.toInt())
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cardBg
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    toggleSkillBar()
                    triggerSkill(skill)
                }
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(0xFF1E3A5F.toInt())
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val subView = TextView(this).apply {
                text = sublabel
                textSize = 10f
                setTextColor(0xFF6E8DAA.toInt())
                gravity = Gravity.CENTER
            }
            card.addView(labelView)
            card.addView(subView)
            row.addView(card)
        }

        return row
    }

    /**
     * Replaces the skill bar content with an undo prompt after Replace/Polish commit.
     * Shows the original text preview and an "撤销" button.
     */
    private fun showUndoBar(undo: UndoContext) {
        if (!::skillBarView.isInitialized) return

        // Rebuild skillBarView content as undo bar
        val container = skillBarView as? LinearLayout ?: return
        container.removeAllViews()
        container.visibility = View.VISIBLE
        isSkillBarVisible = true

        val skillLabel = when (undo.skill) {
            TextSkill.REPLACE -> "替换"
            TextSkill.POLISH -> "润色"
            else -> "操作"
        }

        // Preview label
        val preview = TextView(this).apply {
            text = "$skillLabel 完成：\"${undo.originalText.take(20)}${if (undo.originalText.length > 20) "…" else "\""}"
            textSize = 12f
            setTextColor(0xFF556072.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        // Undo button
        val undoBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(0xFFFFEBEB.toInt())
            setStroke(dp(1), 0xFFE0A0A0.toInt())
        }
        val undoBtn = TextView(this).apply {
            text = "↩ 撤销"
            textSize = 13f
            setTextColor(0xFFB03030.toInt())
            gravity = Gravity.CENTER
            background = undoBg
            setPadding(dp(16), dp(8), dp(16), dp(8))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { performUndo(undo) }
        }

        container.addView(preview)
        container.addView(undoBtn)
        refreshSuggestionTray()
    }

    /**
     * Performs one-step undo: deletes the replaced text and re-inserts the original.
     */
    private fun performUndo(undo: UndoContext) {
        val ic = currentInputConnection ?: return
        // Delete the replaced text that was just inserted
        val deleteLen = undo.replacedText.length
        ic.deleteSurroundingText(deleteLen, 0)
        // Re-insert original text
        ic.commitText(undo.originalText, 1)
        PrefsManager.setImeLastInputContext(this, undo.originalText)
        // Reset skill bar back to normal
        rebuildSkillBar()
        isSkillBarVisible = false
        skillBarView.visibility = View.GONE
        refreshSuggestionTray()
    }

    /**
     * Rebuilds the skill bar back to its original Translate/Replace/Polish state
     * (used after undo clears the undo-bar content).
     */
    private fun rebuildSkillBar() {
        if (!::skillBarView.isInitialized) return
        val container = skillBarView as? LinearLayout ?: return
        container.removeAllViews()

        val skills = listOf(
            Triple("翻译", "Translate", TextSkill.TRANSLATE),
            Triple("替换", "Replace",   TextSkill.REPLACE),
            Triple("润色", "Polish",    TextSkill.POLISH)
        )
        skills.forEach { (label, sublabel, skill) ->
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(0xFFEAF2FF.toInt())
                setStroke(dp(1), 0xFFAFC8E8.toInt())
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cardBg
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    toggleSkillBar()
                    triggerSkill(skill)
                }
            }
            val labelView = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(0xFF1E3A5F.toInt())
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val subView = TextView(this).apply {
                text = sublabel
                textSize = 10f
                setTextColor(0xFF6E8DAA.toInt())
                gravity = Gravity.CENTER
            }
            card.addView(labelView)
            card.addView(subView)
            container.addView(card)
        }
    }

    /** Toggles the skill bar visibility and refreshes the "/" button state. */
    private fun toggleSkillBar() {
        isSkillBarVisible = !isSkillBarVisible
        // Manual toggle — reset the auto-selection flag so onUpdateSelection won't close it
        selectionTriggeredSkillBar = false
        if (::skillBarView.isInitialized) {
            skillBarView.visibility = if (isSkillBarVisible) View.VISIBLE else View.GONE
        }
        // Rebuild candidate tray so the "/" button colour updates
        refreshSuggestionTray()
    }

    private fun createMutedHint(text: String): View {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF69788F.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
    }

    private fun createSuggestionChip(suggestion: ImeSuggestion, isHighlighted: Boolean = false): View {
        val bgColor = when {
            isHighlighted -> 0xFF3D5A80.toInt()
            suggestion.source == SuggestionSource.AI -> 0xFFEAF2FF.toInt()
            else -> 0xFFEDEFEF.toInt()
        }
        val strokeColor = when {
            isHighlighted -> 0xFF2D4A6E.toInt()
            suggestion.source == SuggestionSource.AI -> 0xFFAFBDD3.toInt()
            else -> 0xFFC2C7CB.toInt()
        }
        val textColor = if (isHighlighted) 0xFFFFFFFF.toInt() else 0xFF2D3644.toInt()

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(bgColor)
            setStroke(dp(1), strokeColor)
        }

        return TextView(this).apply {
            text = suggestion.text
            textSize = 13f
            setTextColor(textColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = bg
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { commitSuggestion(suggestion.text) }
        }
    }

    private fun commitSuggestion(text: String) {
        HapticsUtil.impactMedium(this)
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            pinyinComposer.clear()
            streamingPreviewView.text = ""
            // Clear composing text before committing the candidate
            isSettingComposingText = true
            currentInputConnection?.setComposingText("", 0)
        }
        englishBuffer.clear()

        // In B/C mode, replace the original input text instead of appending
        val ic = currentInputConnection
        if (ic != null && (aiMode == ImeAiMode.B || aiMode == ImeAiMode.C)
            && ::aiPanelWrapper.isInitialized && aiPanelWrapper.visibility == View.VISIBLE
        ) {
            val originalInput = PrefsManager.getImeLastInputContext(this)
            if (originalInput.isNotBlank()) {
                val before = ic.getTextBeforeCursor(originalInput.length, 0)?.toString().orEmpty()
                if (before == originalInput) {
                    // Delete the original text, then insert the AI reply
                    ic.deleteSurroundingText(originalInput.length, 0)
                }
            }
        }

        currentInputConnection?.commitText(text, 1)
        PrefsManager.setImeLastInputContext(this, text)
        updateComposingBar()

        // If this commit matches a pending undo context, activate undo mode
        val undo = pendingUndo
        if (undo != null && undo.replacedText == text) {
            pendingUndo = null
            showUndoBar(undo)
        } else {
            pendingUndo = null
            refreshSuggestionTray()
        }
    }

    private fun createModeChip(label: String, onTap: () -> Unit): View {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { onTap() }
        }
    }

    private fun createActionChip(label: String, onTap: () -> Unit): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(0xCC4A5568.toInt())  // dark slate with slight transparency
            setStroke(dp(1), 0x55FFFFFF.toInt())
        }
        return TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener { onTap() }
        }
    }

    private fun createSkillSpacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        }
    }

    private fun refreshModeChips(topRow: LinearLayout) {
        for (index in 0 until topRow.childCount) {
            val child = topRow.getChildAt(index)
            if (child !is TextView) continue
            if (child.text != "B" && child.text != "C") continue

            val selected = child.text.toString().equals(aiMode.name, ignoreCase = true)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                if (selected) {
                    setColor(0xFFE3EBF8.toInt())
                    setStroke(dp(1), 0xFF8FA3C2.toInt())
                } else {
                    setColor(0xFFF2F3F5.toInt())
                    setStroke(dp(1), 0xFFC9CDD2.toInt())
                }
            }
            child.background = bg
            child.setTextColor(if (selected) 0xFF2E3D54.toInt() else 0xFF556072.toInt())
        }
    }

    private fun setAiMode(mode: ImeAiMode) {
        if (!isAiAccessAllowed()) return
        aiMode = mode
        aiCoordinator.setMode(mode)
        // Show AI panel and hide fallback panel (skill bar)
        if (::aiPanelWrapper.isInitialized) {
            aiPanelWrapper.visibility = View.VISIBLE
        }
        if (::fallbackPanel.isInitialized) {
            fallbackPanel.visibility = View.GONE
        }
        rebuildKeyboard()
    }

    /**
     * Check login + Pro entitlement before allowing AI features.
     * Currently bypassed for testing — always returns true.
     */
    private fun isAiAccessAllowed(): Boolean {
        // TODO: restore login + Pro gate before release
        return true
    }

    private fun triggerAiFromCurrentInput() {
        if (!isAiAccessAllowed()) return

        // In B mode, ALWAYS force a fresh screenshot+OCR scan (never use stale cache)
        if (aiMode == ImeAiMode.B) {
            if (ChatContextHolder.isAccessibilityServiceEnabled(this)) {
                ChatContextHolder.clear()
                requestHideSelf(0)
                Handler(Looper.getMainLooper()).postDelayed({
                    requestChatContextAndTrigger()
                }, 400)
                return
            }
        }

        // Fallback: use input field text
        triggerAiFromInputField()
    }

    /** Send broadcast to AccessibilityService to read context now, then trigger AI. */
    private fun requestChatContextAndTrigger() {
        Log.d(TAG, "Requesting on-demand chat context read (screenshot+OCR)")

        // Register a one-shot receiver for the result
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try { unregisterReceiver(this) } catch (_: Exception) {}
                contextReadTimeoutHandler.removeCallbacksAndMessages(null)

                val contextText = intent?.getStringExtra(
                    ChatScanAccessibilityService.EXTRA_CONTEXT_TEXT
                ).orEmpty()

                // Now show the AI panel and trigger suggestions
                setAiMode(aiMode)
                rebuildKeyboard()
                requestShowSelf(0)

                if (contextText.isNotBlank()) {
                    Log.d(TAG, "On-demand context received (${contextText.length} chars)")
                    PrefsManager.setImeLastInputContext(this@ThreplyInputMethodService, contextText)
                    aiCoordinator.requestSuggestions(contextText)
                } else {
                    Log.d(TAG, "On-demand context empty, falling back to input field")
                    triggerAiFromInputField()
                }
            }
        }

        val filter = IntentFilter(ChatScanAccessibilityService.ACTION_CONTEXT_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Timeout: if no response in 5 seconds, show panel anyway with fallback
        contextReadTimeoutHandler.postDelayed({
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            Log.d(TAG, "Context read timed out, showing panel with fallback")
            setAiMode(aiMode)
            rebuildKeyboard()
            requestShowSelf(0)
            triggerAiFromInputField()
        }, 5000)

        // Send the request
        val intent = Intent(ChatScanAccessibilityService.ACTION_READ_CONTEXT)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    /** Fallback: read text from input field and trigger AI. */
    private fun triggerAiFromInputField() {
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        val fallback = pinyinComposer.currentRaw()
        val input = (selected.ifBlank { before }).ifBlank { fallback }.trim()
        if (input.isBlank()) {
            streamingPreviewView.text = if (aiMode == ImeAiMode.B) {
                "未读取到聊天上下文，请开启无障碍服务或输入内容"
            } else {
                "请先输入一些内容"
            }
            return
        }
        PrefsManager.setImeLastInputContext(this, input)
        aiCoordinator.requestSuggestions(input)
    }

    private fun triggerSkill(skill: TextSkill) {
        if (!isAiAccessAllowed()) return

        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        val before = currentInputConnection?.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        val input = selected.ifBlank { before }.trim()
        if (input.isBlank()) {
            streamingPreviewView.text = "请先输入或选中文本"
            return
        }

        streamingPreviewView.text = when (skill) {
            TextSkill.TRANSLATE -> "翻译中…"
            TextSkill.REPLACE -> "替换中…"
            TextSkill.POLISH -> "润色中…"
        }

        imeScope.launch {
            try {
                val ctx = this@ThreplyInputMethodService
                val useDeepSeek = PrefsManager.getDeepSeekApiKey(ctx).isNotBlank()
                val result = when (skill) {
                    TextSkill.TRANSLATE -> {
                        val target = if (isLikelyChinese(input)) "English" else "中文"
                        if (useDeepSeek) DeepSeekDirectApi.translateText(ctx, input, target)
                        else BackendAiApi.translateText(ctx, text = input, targetLanguage = target)
                    }
                    TextSkill.REPLACE -> {
                        if (useDeepSeek) DeepSeekDirectApi.replaceText(ctx, input)
                        else BackendAiApi.replaceText(ctx, text = input)
                    }
                    TextSkill.POLISH -> {
                        if (useDeepSeek) DeepSeekDirectApi.polishText(ctx, input)
                        else BackendAiApi.polishText(ctx, text = input)
                    }
                }
                streamingPreviewView.text = result
                // For Replace/Polish: stage an undo context (activated when user taps the result chip)
                if (skill == TextSkill.REPLACE || skill == TextSkill.POLISH) {
                    pendingUndo = UndoContext(originalText = input, replacedText = result, skill = skill)
                }
                // Show result as a tappable suggestion
                latestAiSuggestions = listOf(
                    ImeSuggestion(text = result, source = SuggestionSource.AI)
                )
                refreshSuggestionTray()
            } catch (e: Exception) {
                streamingPreviewView.text = e.message ?: "操作失败，请重试"
            }
        }
    }

    private fun isLikelyChinese(text: String): Boolean {
        val cjk = text.count { it.code in 0x4E00..0x9FFF }
        return cjk > text.length / 3
    }

    // ─── Chat Scan (Accessibility Service) ───

    private var scanResultReceiver: BroadcastReceiver? = null

    private fun triggerChatScan() {
        streamingPreviewView.text = "正在扫描聊天内容…"
        registerScanResultReceiver()
        val intent = Intent(ChatScanAccessibilityService.ACTION_SCAN).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun registerScanResultReceiver() {
        if (scanResultReceiver != null) return
        scanResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                val status = intent?.getStringExtra(ChatScanAccessibilityService.EXTRA_STATUS) ?: return
                val text = intent.getStringExtra(ChatScanAccessibilityService.EXTRA_RESULT) ?: return
                streamingPreviewView.text = text
                if (status == "success") {
                    latestAiSuggestions = text.lines()
                        .filter { it.isNotBlank() }
                        .map { ImeSuggestion(text = it.replaceFirst(Regex("^\\d+\\.\\s*"), ""), source = SuggestionSource.AI) }
                    refreshSuggestionTray()
                    unregisterScanResultReceiver()
                } else if (status == "error") {
                    unregisterScanResultReceiver()
                }
            }
        }
        val filter = IntentFilter(ChatScanAccessibilityService.ACTION_SCAN_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanResultReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scanResultReceiver, filter)
        }
    }

    private fun unregisterScanResultReceiver() {
        scanResultReceiver?.let {
            unregisterReceiver(it)
            scanResultReceiver = null
        }
    }

    private fun consumePendingTriggerIfNeeded() {
        val payload = SharedTriggerStore.consumePending(this) ?: return
        setAiMode(payload.mode)
        PrefsManager.setImeLastInputContext(this, payload.draft)
        aiCoordinator.requestSuggestions(payload.draft)
    }

    private fun rebuildKeyboard() {
        if (!::keyboardRoot.isInitialized) return

        // Keep fallbackPanel (index 0) and aiPanelWrapper (index 1), remove keyboard rows
        while (keyboardRoot.childCount > 2) {
            keyboardRoot.removeViewAt(2)
        }

        if (isSymbolMode) {
            val isZh = inputLanguage == InputLanguage.ZH_PINYIN
            addStandardRow(
                keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                hints = listOf("", "", "", "", "", "", "", "", "", "")
            )
            if (isZh) {
                // Chinese punctuation priority layout
                addStandardRow(
                    keys = listOf("。", "，", "、", "？", "！", "：", "；", "（", "）", "…"),
                    hints = listOf(".", ",", "/", "?", "!", ":", ";", "(", ")", "^"),
                    leadingInsetWeight = 0.30f,
                    trailingInsetWeight = 0.30f
                )
                addActionRow(
                    leftKey = KeySpec(
                        label = "ABC",
                        weight = 1.25f,
                        style = KeyStyle.Action,
                        onTap = {
                            isSymbolMode = false
                            rebuildKeyboard()
                        }
                    ),
                    centerKeys = listOf(
                        KeySpec(label = "《", output = "《"),
                        KeySpec(label = "》", output = "》"),
                        KeySpec(label = "【", output = "【"),
                        KeySpec(label = "】", output = "】"),
                        KeySpec(label = "\u201C", output = "\u201C"),
                        KeySpec(label = "\u201D", output = "\u201D"),
                        KeySpec(label = "\u2014", output = "\u2014")
                    ),
                    rightKey = KeySpec(
                        label = "⌫",
                        weight = 1.25f,
                        style = KeyStyle.Action,
                        onTap = { handleBackspace() },
                        repeatOnLongPress = true
                    )
                )
            } else {
                addStandardRow(
                    keys = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
                    hints = listOf("~", "!", "*", "^", "=", "_", "\\", "[", "]", "?"),
                    leadingInsetWeight = 0.30f,
                    trailingInsetWeight = 0.30f
                )
                addActionRow(
                    leftKey = KeySpec(
                        label = "ABC",
                        weight = 1.25f,
                        style = KeyStyle.Action,
                        onTap = {
                            isSymbolMode = false
                            rebuildKeyboard()
                        }
                    ),
                    centerKeys = listOf(
                        KeySpec(label = ".", output = "."),
                        KeySpec(label = ",", output = ","),
                        KeySpec(label = "?", output = "?"),
                        KeySpec(label = "!", output = "!"),
                        KeySpec(label = "'", output = "'"),
                        KeySpec(label = "\"", output = "\""),
                        KeySpec(label = ":", output = ":")
                    ),
                    rightKey = KeySpec(
                        label = "⌫",
                        weight = 1.25f,
                        style = KeyStyle.Action,
                        onTap = { handleBackspace() },
                        repeatOnLongPress = true
                    )
                )
            }
        } else {
            addStandardRow(
                keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                hints = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            )
            addStandardRow(
                keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                hints = listOf("~", "!", "@", "#", "%", "\"", "'", "*", "?"),
                leadingInsetWeight = 0.35f,
                trailingInsetWeight = 0.35f
            )
            addActionRow(
                leftKey = KeySpec(
                    label = "↑",
                    weight = 1.25f,
                    style = KeyStyle.Action,
                    onTap = {
                        isUppercase = !isUppercase
                        rebuildKeyboard()
                    }
                ),
                centerKeys = listOf(
                    KeySpec(label = displayKey("z"), output = "z", hint = "("),
                    KeySpec(label = displayKey("x"), output = "x", hint = ")"),
                    KeySpec(label = displayKey("c"), output = "c", hint = "-"),
                    KeySpec(label = displayKey("v"), output = "v", hint = "_"),
                    KeySpec(label = displayKey("b"), output = "b", hint = ":"),
                    KeySpec(label = displayKey("n"), output = "n", hint = ";"),
                    KeySpec(label = displayKey("m"), output = "m", hint = "/")
                ),
                rightKey = KeySpec(
                    label = "⌫",
                    weight = 1.25f,
                    style = KeyStyle.Action,
                    onTap = { handleBackspace() },
                    repeatOnLongPress = true
                )
            )
        }

        addBottomRow()

        // Add expanded candidate panel at the end (overlays keyboard when visible)
        if (!::expandedCandidatePanel.isInitialized) {
            expandedCandidatePanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setBackgroundColor(0xFFF5F6F8.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
        }
        // Remove if already added, then re-add at the end
        if (expandedCandidatePanel.parent == keyboardRoot) {
            keyboardRoot.removeView(expandedCandidatePanel)
        }
        keyboardRoot.addView(expandedCandidatePanel)
    }

    private fun addStandardRow(
        keys: List<String>,
        hints: List<String>,
        leadingInsetWeight: Float = 0f,
        trailingInsetWeight: Float = 0f
    ) {
        val row = createRow()
        if (leadingInsetWeight > 0f) {
            row.addView(createSpacer(leadingInsetWeight))
        }
        keys.forEachIndexed { index, key ->
            row.addView(
                createKeyView(
                    KeySpec(
                        label = if (isSymbolMode) key else displayKey(key),
                        output = key,
                        hint = hints.getOrNull(index)?.takeIf { it.isNotEmpty() }
                    )
                )
            )
        }
        if (trailingInsetWeight > 0f) {
            row.addView(createSpacer(trailingInsetWeight))
        }
        keyboardRoot.addView(row)
    }

    private fun addActionRow(
        leftKey: KeySpec,
        centerKeys: List<KeySpec>,
        rightKey: KeySpec
    ) {
        val row = createRow()
        row.addView(createKeyView(leftKey))
        centerKeys.forEach { key ->
            row.addView(createKeyView(key))
        }
        row.addView(createKeyView(rightKey))
        keyboardRoot.addView(row)
    }

    private fun addBottomRow() {
        val row = createRow(bottomRow = true)
        val leftModeLabel = if (isSymbolMode) "ABC" else "符"

        // AI panel toggle button
        val aiPanelVisible = ::aiPanelWrapper.isInitialized && aiPanelWrapper.visibility == View.VISIBLE
        row.addView(
            createKeyView(
                KeySpec(
                    label = "AI",
                    weight = 1.10f,
                    style = if (aiPanelVisible) KeyStyle.Accent else KeyStyle.Action,
                    onTap = { toggleAiPanel() }
                ),
                bottomRow = true
            )
        )

        row.addView(
            createKeyView(
                KeySpec(
                    label = leftModeLabel,
                    weight = 1.10f,
                    style = KeyStyle.Action,
                    onTap = {
                        isSymbolMode = !isSymbolMode
                        rebuildKeyboard()
                    }
                ),
                bottomRow = true
            )
        )
        row.addView(
            createKeyView(
                KeySpec(
                    label = "123",
                    weight = 1.10f,
                    style = KeyStyle.Action,
                    onTap = {
                        if (!isSymbolMode) {
                            isSymbolMode = true
                            rebuildKeyboard()
                        }
                    }
                ),
                bottomRow = true
            )
        )
        val isZh = inputLanguage == InputLanguage.ZH_PINYIN
        val commaLabel = if (isZh) "\uFF0C" else ","
        val commaOutput = ","
        val periodLabel = if (isZh) "\u3002" else "."
        val periodOutput = "."
        row.addView(createKeyView(KeySpec(label = commaLabel, output = commaOutput, weight = 0.80f), bottomRow = true))
        row.addView(
            createKeyView(
                KeySpec(
                    label = "空格",
                    weight = 3.20f,
                    onTap = { handleSpaceTap() }
                ),
                bottomRow = true
            )
        )
        row.addView(createKeyView(KeySpec(label = periodLabel, output = periodOutput, weight = 0.80f), bottomRow = true))
        row.addView(
            createKeyView(
                KeySpec(
                    label = languageToggleLabel(),
                    weight = 1.10f,
                    style = KeyStyle.Action,
                    onTap = { toggleInputLanguage() }
                ),
                bottomRow = true
            )
        )
        row.addView(
            createKeyView(
                KeySpec(
                    label = enterKeyLabel(),
                    weight = 1.30f,
                    style = KeyStyle.Accent,
                    onTap = { handleEnter() }
                ),
                bottomRow = true
            )
        )
        keyboardRoot.addView(row)
    }

    private fun createRow(bottomRow: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (bottomRow) dp(0) else dp(6)
            }
        }
    }

    private fun createKeyView(spec: KeySpec, bottomRow: Boolean = false): LinearLayout {
        val keyBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (bottomRow) 10 else 8).toFloat()
            when (spec.style) {
                KeyStyle.Normal -> {
                    setColor(0xFFF7F7F8.toInt())
                    setStroke(dp(1), 0xFFC1C3C8.toInt())
                }
                KeyStyle.Action -> {
                    setColor(0xFFE5EAF3.toInt())
                    setStroke(dp(1), 0xFFB5BECF.toInt())
                }
                KeyStyle.Accent -> {
                    setColor(0xFF51617D.toInt())
                    setStroke(dp(1), 0xFF4A5972.toInt())
                }
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = keyBackground
            minimumHeight = dp(if (bottomRow) 44 else 48)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(if (bottomRow) 44 else 48),
                spec.weight
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            val tapAction: () -> Unit = {
                HapticsUtil.tap(this@ThreplyInputMethodService)
                when {
                    spec.onTap != null -> spec.onTap.invoke()
                    shouldHandleAsPinyin(spec.output ?: spec.label) -> handlePinyinLetter(spec.output ?: spec.label)
                    else -> commitText(spec.output ?: spec.label)
                }
            }

            if (spec.repeatOnLongPress) {
                val handler = Handler(Looper.getMainLooper())
                var repeating = false
                val repeatRunnable = object : Runnable {
                    override fun run() {
                        tapAction()
                        handler.postDelayed(this, 50)
                    }
                }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            repeating = false
                            tapAction()
                            handler.postDelayed({
                                repeating = true
                                repeatRunnable.run()
                            }, 700)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            handler.removeCallbacksAndMessages(null)
                            if (!repeating) v.performClick()
                            true
                        }
                        else -> false
                    }
                }
            } else {
                setOnClickListener { tapAction() }
            }
        }

        val hintView = TextView(this).apply {
            text = spec.hint ?: ""
            setTextColor(0xFFAAB7D4.toInt())
            textSize = if (bottomRow) 0f else 8f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val labelView = TextView(this).apply {
            text = spec.label
            setTextColor(if (spec.style == KeyStyle.Accent) 0xFFFFFFFF.toInt() else 0xFF2F3136.toInt())
            textSize = when {
                bottomRow && spec.style == KeyStyle.Accent -> 14f
                bottomRow && spec.label.length > 2 -> 11f
                bottomRow -> 15f
                spec.style == KeyStyle.Normal -> 19f
                else -> 14f
            }
            gravity = Gravity.CENTER
            typeface = if (spec.style == KeyStyle.Normal) Typeface.SANS_SERIF else Typeface.DEFAULT_BOLD
            maxLines = if (bottomRow && spec.label.contains("\n")) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
        }

        if (!bottomRow) {
            container.addView(
                hintView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0.24f
                )
            )
        }

        container.addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                if (bottomRow) 1f else 0.96f
            )
        )

        return container
    }

    private fun createSpacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), weight)
        }
    }

    private fun shouldHandleAsPinyin(rawValue: String): Boolean {
        return inputLanguage == InputLanguage.ZH_PINYIN &&
                !isSymbolMode &&
                rawValue.length == 1 &&
                rawValue[0].isLetter()
    }

    /**
     * Updates the composing bar above the candidate tray with the current input buffer.
     * - Pinyin mode: shows the raw pinyin string (e.g. "nihao")
     * - English mode: shows the current word being typed (e.g. "hello")
     * The bar is hidden when there is nothing to show.
     */
    private fun updateComposingBar() {
        if (!::composingBarView.isInitialized) return
        val content = when (inputLanguage) {
            InputLanguage.ZH_PINYIN -> pinyinComposer.currentRaw()
            InputLanguage.EN -> englishBuffer.toString()
        }
        if (content.isBlank()) {
            composingBarView.text = ""
            composingBarView.visibility = View.GONE
        } else {
            composingBarView.text = "$content|"
            composingBarView.visibility = View.VISIBLE
        }
    }

    private fun handlePinyinLetter(rawValue: String) {
        val ch = rawValue.lowercase().firstOrNull() ?: return
        pinyinComposer.push(ch)
        rimeController.resetPinyinPage()
        PrefsManager.setImeLastInputContext(this, pinyinComposer.currentRaw())
        // Show composing text in the host input field
        val pinyin = pinyinComposer.currentRaw()
        isSettingComposingText = true
        currentInputConnection?.setComposingText(pinyin, 1)
        updateComposingBar()
        refreshSuggestionTray()
    }

    private fun handleSpaceTap() {
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            val candidate = currentPinyinCandidates(limit = 1).firstOrNull()
                ?: pinyinComposer.currentRaw()
            // Clear composing text before committing the candidate
            isSettingComposingText = true
            currentInputConnection?.setComposingText("", 0)
            currentInputConnection?.commitText(candidate, 1)
            PrefsManager.setImeLastInputContext(this, candidate)
            pinyinComposer.clear()
            rimeController.resetPinyinPage()
            streamingPreviewView.text = ""
            updateComposingBar()
            refreshSuggestionTray()
            lastSpaceTimeMs = 0L
            return
        }

        // Double-space → period in Chinese mode
        val now = System.currentTimeMillis()
        if (inputLanguage == InputLanguage.ZH_PINYIN
            && lastSpaceTimeMs > 0L
            && (now - lastSpaceTimeMs) < 500L
        ) {
            // Delete the previous space, insert "。"
            currentInputConnection?.deleteSurroundingText(1, 0)
            currentInputConnection?.commitText("\u3002", 1)
            PrefsManager.setImeLastInputContext(this, "\u3002")
            lastSpaceTimeMs = 0L
            englishBuffer.clear()
            updateComposingBar()
            return
        }

        lastSpaceTimeMs = if (inputLanguage == InputLanguage.ZH_PINYIN) now else 0L
        englishBuffer.clear()
        updateComposingBar()
        commitText(" ")
    }

    private fun toggleInputLanguage() {
        HapticsUtil.impactMedium(this)
        inputLanguage = if (inputLanguage == InputLanguage.EN) InputLanguage.ZH_PINYIN else InputLanguage.EN
        PrefsManager.setImeInputLanguage(this, if (inputLanguage == InputLanguage.EN) "en" else "zh_pinyin")

        if (inputLanguage == InputLanguage.EN) {
            pinyinComposer.clear()
            englishBuffer.clear()
            rimeController.resetPinyinPage()
            streamingPreviewView.text = ""
            isSettingComposingText = true
            currentInputConnection?.setComposingText("", 0)
        }

        rebuildKeyboard()
        refreshSuggestionTray()
    }

    private fun languageToggleLabel(): String {
        return if (inputLanguage == InputLanguage.EN) "中" else "英"
    }

    private fun readInputLanguage(): InputLanguage {
        return if (PrefsManager.getImeInputLanguage(this) == "zh_pinyin") {
            InputLanguage.ZH_PINYIN
        } else {
            InputLanguage.EN
        }
    }

    private fun commitText(rawValue: String) {
        val value = if (isSymbolMode) {
            // In symbol mode with Chinese input, map to full-width punctuation
            if (inputLanguage == InputLanguage.ZH_PINYIN) {
                zhPunctuationMap[rawValue] ?: rawValue
            } else {
                rawValue
            }
        } else if (rawValue.length == 1 && rawValue[0].isLetter()) {
            if (isUppercase) rawValue.uppercase() else rawValue.lowercase()
        } else if (inputLanguage == InputLanguage.ZH_PINYIN && rawValue.length == 1) {
            // Map inline punctuation (comma, period on bottom row) to full-width
            zhPunctuationMap[rawValue] ?: rawValue
        } else {
            rawValue
        }
        currentInputConnection?.commitText(value, 1)
        PrefsManager.setImeLastInputContext(this, value)
        // Track english buffer for composing bar display
        if (inputLanguage == InputLanguage.EN && !isSymbolMode && value.length == 1 && value[0].isLetter()) {
            englishBuffer.append(value)
        } else {
            // Non-letter commit (space, symbol, etc.) — flush the buffer
            englishBuffer.clear()
        }
        updateComposingBar()
    }

    private fun handleBackspace() {
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            pinyinComposer.pop()
            if (pinyinComposer.currentRaw().isBlank()) {
                rimeController.resetPinyinPage()
                isSettingComposingText = true
                currentInputConnection?.setComposingText("", 0)
            } else {
                isSettingComposingText = true
                currentInputConnection?.setComposingText(pinyinComposer.currentRaw(), 1)
            }
            updateComposingBar()
            refreshSuggestionTray()
            if (pinyinComposer.currentRaw().isBlank()) {
                streamingPreviewView.text = ""
            }
            return
        }
        // English mode: pop one char from buffer if any
        if (inputLanguage == InputLanguage.EN && englishBuffer.isNotEmpty()) {
            englishBuffer.deleteCharAt(englishBuffer.lastIndex)
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        updateComposingBar()
    }

    private fun handleEnter() {
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            val candidate = currentPinyinCandidates(limit = 1).firstOrNull()
                ?: pinyinComposer.currentRaw()
            // Clear composing text before committing the candidate
            isSettingComposingText = true
            currentInputConnection?.setComposingText("", 0)
            currentInputConnection?.commitText(candidate, 1)
            pinyinComposer.clear()
            rimeController.resetPinyinPage()
            streamingPreviewView.text = ""
            updateComposingBar()
            refreshSuggestionTray()
            return
        }
        englishBuffer.clear()
        updateComposingBar()

        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    private fun enterKeyLabel(): String {
        return when (currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_GO -> "前往"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_DONE -> "完成"
            EditorInfo.IME_ACTION_NEXT -> "下一项"
            else -> "发送"
        }
    }

    private fun displayKey(key: String): String {
        return key.uppercase()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getSelectedTextOrCurrentInput(): String {
        val inputConnection = currentInputConnection ?: return ""
        val selectedText = inputConnection.getSelectedText(0)
        return selectedText?.toString() ?: PrefsManager.getImeLastInputContext(this)
    }
}
