package com.arche.threply.ime

import android.content.BroadcastReceiver
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
    private lateinit var suggestionContainer: LinearLayout
    private lateinit var streamingPreviewView: TextView
    private lateinit var aiPanelView: View

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
        val fallbackPanel = createFallbackPanel()
        keyboardRoot.addView(fallbackPanel)
        aiPanelView = createAiPanel(fallbackPanel)
        if (aiPanelView !== fallbackPanel) {
            keyboardRoot.addView(aiPanelView)
        }
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
        rimeController.resetPinyinPage()

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
        keyboardRoot.addView(createFallbackPanel())
        rebuildKeyboard()
        return keyboardRoot
    }

    private fun createAiPanel(fallbackPanel: View): View {
        return runCatching {
            // Don't initialize lifecycle here - will be done when view is attached

            val composeView = ComposeView(this).apply {
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
                        onScan = { triggerChatScan() }
                    )
                }
            }

            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                addView(composeView)
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

        // Initialize suggestionContainer as hidden (pinyin candidates still use it)
        suggestionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(streamingPreviewView)
            addView(suggestionContainer)
        }
    }

    private fun exitAiPanel() {
        // Hide AI panel and just show keyboard
        if (::aiPanelView.isInitialized) {
            aiPanelView.visibility = View.GONE
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
                streamingPreviewView.text = "拼音：$pinyin（第${rimeController.currentPinyinPage() + 1}页）"

                val candidates = currentPinyinCandidates(limit = 5)
                candidates.map {
                    ImeSuggestion(text = it, source = SuggestionSource.RIME, isStreaming = false)
                }
            }
            latestAiSuggestions.isNotEmpty() -> latestAiSuggestions
            else -> {
                val fallback = rimeController.suggest(PrefsManager.getImeLastInputContext(this))
                fallback.take(3).map { ImeSuggestion(it, SuggestionSource.RIME, false) }
            }
        }

        if (merged.isEmpty()) {
            suggestionContainer.addView(createMutedHint("暂无建议"))
            return
        }

        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            suggestionContainer.addView(createPagerChip("‹") {
                rimeController.previousPinyinPage()
                refreshSuggestionTray()
            })
        }

        merged.forEach { suggestion ->
            suggestionContainer.addView(createSuggestionChip(suggestion))
        }

        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            suggestionContainer.addView(createPagerChip("›") {
                rimeController.nextPinyinPage()
                refreshSuggestionTray()
            })
        }
    }

    private fun currentPinyinCandidates(limit: Int = 5): List<String> {
        val pinyin = pinyinComposer.currentRaw().trim().lowercase()
        if (pinyin.isBlank()) return emptyList()

        val safeLimit = limit.coerceIn(1, 20)

        val rimeCandidates = rimeController.suggestFromPinyin(pinyin)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && containsHanOrPunctuation(it) }
            .distinct()
            .toList()
        if (rimeCandidates.isNotEmpty()) return rimeCandidates.take(safeLimit)

        val fallbackCandidates = RimeFallbackLexicon.lookup(
            raw = pinyin,
            limit = safeLimit,
            page = rimeController.currentPinyinPage()
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

    private fun createPagerChip(label: String, onTap: () -> Unit): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(0xFFE2E5EA.toInt())
            setStroke(dp(1), 0xFFB9C0CB.toInt())
        }

        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFF3A475A.toInt())
            gravity = Gravity.CENTER
            background = bg
            setPadding(dp(10), dp(7), dp(10), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { onTap() }
        }
    }

    private fun createMutedHint(text: String): View {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF69788F.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
    }

    private fun createSuggestionChip(suggestion: ImeSuggestion): View {
        val bgColor = if (suggestion.source == SuggestionSource.AI) 0xFFEAF2FF.toInt() else 0xFFEDEFEF.toInt()
        val strokeColor = if (suggestion.source == SuggestionSource.AI) 0xFFAFBDD3.toInt() else 0xFFC2C7CB.toInt()

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(bgColor)
            setStroke(dp(1), strokeColor)
        }

        return TextView(this).apply {
            text = suggestion.text
            textSize = 13f
            setTextColor(0xFF2D3644.toInt())
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
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            pinyinComposer.clear()
            streamingPreviewView.text = ""
        }
        currentInputConnection?.commitText(text, 1)
        PrefsManager.setImeLastInputContext(this, text)
        refreshSuggestionTray()
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
        // Show AI panel if it was hidden
        if (::aiPanelView.isInitialized) {
            aiPanelView.visibility = View.VISIBLE
        }
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

        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        val fallback = pinyinComposer.currentRaw()
        val input = (selected.ifBlank { before }).ifBlank { fallback }.trim()
        if (input.isBlank()) {
            streamingPreviewView.text = "请先输入一些内容"
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

        while (keyboardRoot.childCount > 1) {
            keyboardRoot.removeViewAt(1)
        }

        if (isSymbolMode) {
            addStandardRow(
                keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                hints = listOf("", "", "", "", "", "", "", "", "", "")
            )
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
        row.addView(createKeyView(KeySpec(label = ",", output = ",", weight = 0.80f), bottomRow = true))
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
        row.addView(createKeyView(KeySpec(label = "。", output = ".", weight = 0.80f), bottomRow = true))
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

    private fun handlePinyinLetter(rawValue: String) {
        val ch = rawValue.lowercase().firstOrNull() ?: return
        pinyinComposer.push(ch)
        rimeController.resetPinyinPage()
        PrefsManager.setImeLastInputContext(this, pinyinComposer.currentRaw())
        refreshSuggestionTray()
    }

    private fun handleSpaceTap() {
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            val candidate = currentPinyinCandidates(limit = 1).firstOrNull()
                ?: pinyinComposer.currentRaw()
            currentInputConnection?.commitText(candidate, 1)
            PrefsManager.setImeLastInputContext(this, candidate)
            pinyinComposer.clear()
            rimeController.resetPinyinPage()
            streamingPreviewView.text = ""
            refreshSuggestionTray()
            return
        }
        commitText(" ")
    }

    private fun toggleInputLanguage() {
        inputLanguage = if (inputLanguage == InputLanguage.EN) InputLanguage.ZH_PINYIN else InputLanguage.EN
        PrefsManager.setImeInputLanguage(this, if (inputLanguage == InputLanguage.EN) "en" else "zh_pinyin")

        if (inputLanguage == InputLanguage.EN) {
            pinyinComposer.clear()
            rimeController.resetPinyinPage()
            streamingPreviewView.text = ""
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
            rawValue
        } else if (rawValue.length == 1 && rawValue[0].isLetter()) {
            if (isUppercase) rawValue.uppercase() else rawValue.lowercase()
        } else {
            rawValue
        }
        currentInputConnection?.commitText(value, 1)
        PrefsManager.setImeLastInputContext(this, value)
    }

    private fun handleBackspace() {
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            pinyinComposer.pop()
            if (pinyinComposer.currentRaw().isBlank()) {
                rimeController.resetPinyinPage()
            }
            refreshSuggestionTray()
            if (pinyinComposer.currentRaw().isBlank()) {
                streamingPreviewView.text = ""
            }
            return
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun handleEnter() {
        if (inputLanguage == InputLanguage.ZH_PINYIN && pinyinComposer.currentRaw().isNotBlank()) {
            val candidate = currentPinyinCandidates(limit = 1).firstOrNull()
                ?: pinyinComposer.currentRaw()
            currentInputConnection?.commitText(candidate, 1)
            pinyinComposer.clear()
            rimeController.resetPinyinPage()
            streamingPreviewView.text = ""
            refreshSuggestionTray()
            return
        }

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
}
