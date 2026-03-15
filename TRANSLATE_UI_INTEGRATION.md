/**
 * 翻译语言选择 UI 功能集成指南
 * 
 * 本文档说明如何在 ThreplyInputMethodService 中集成翻译语言选择功能。
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 1. 新增组件概览
 * ───────────────
 * 
 * TranslatePanel.kt
 *   - 翻译面板 UI 组件
 *   - 支持 8 种语言：中文、英文、日文、韩文、西班牙文、法文、德文、自动检测
 *   - 包含源语言/目标语言选择器、交换按钮、翻译按钮
 *   - 显示翻译结果流
 * 
 * ImeAiMode.TRANSLATE
 *   - 新增 AI 模式，用于翻译功能
 *   - 在 SuggestionModels.kt 中定义
 * 
 * SuggestionState 扩展
 *   - 添加 translateSourceLanguage 和 translateTargetLanguage 字段
 *   - 用于保存用户选择的语言对
 * 
 * AiImeCoordinator 扩展
 *   - setTranslateSourceLanguage(languageCode: String)
 *   - setTranslateTargetLanguage(languageCode: String)
 *   - requestTranslation(text: String, sourceLanguage: String, targetLanguage: String)
 * 
 * PrefsManager 扩展
 *   - getTranslateSourceLanguage(context: Context): String
 *   - setTranslateSourceLanguage(context: Context, languageCode: String)
 *   - getTranslateTargetLanguage(context: Context): String
 *   - setTranslateTargetLanguage(context: Context, languageCode: String)
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 2. 在 ThreplyInputMethodService 中的集成步骤
 * ──────────────────────────────────────────────
 * 
 * 步骤 1: 在 ImeAiOverlay Compose 调用中添加回调
 * ─────────────────────────────────────────────
 * 
 * 当前代码（在 setupAiPanel() 或类似方法中）：
 * 
 *   ComposeView(...).setContent {
 *       ImeAiOverlay(
 *           state = aiState,
 *           onModeChange = { mode -> aiCoordinator.setMode(mode) },
 *           onRefresh = { aiCoordinator.requestSuggestions(currentInput) },
 *           onExit = { hideAiPanel() },
 *           onSelectReply = { reply -> commitText(reply) },
 *           onStyleConfirm = { length, temp -> ... },
 *           onScan = { ... }
 *       )
 *   }
 * 
 * 更新为：
 * 
 *   ComposeView(...).setContent {
 *       ImeAiOverlay(
 *           state = aiState,
 *           onModeChange = { mode -> aiCoordinator.setMode(mode) },
 *           onRefresh = { aiCoordinator.requestSuggestions(currentInput) },
 *           onExit = { hideAiPanel() },
 *           onSelectReply = { reply -> commitText(reply) },
 *           onStyleConfirm = { length, temp -> ... },
 *           onScan = { ... },
 *           // 新增翻译相关回调
 *           onSourceLanguageChange = { lang -> 
 *               aiCoordinator.setTranslateSourceLanguage(lang)
 *           },
 *           onTargetLanguageChange = { lang -> 
 *               aiCoordinator.setTranslateTargetLanguage(lang)
 *           },
 *           onTranslate = { source, target ->
 *               aiCoordinator.requestTranslation(
 *                   text = getSelectedTextOrCurrentInput(),
 *                   sourceLanguage = source,
 *                   targetLanguage = target
 *               )
 *           }
 *       )
 *   }
 * 
 * 步骤 2: 添加辅助方法获取待翻译文本
 * ──────────────────────────────────
 * 
 *   private fun getSelectedTextOrCurrentInput(): String {
 *       val inputConnection = currentInputConnection ?: return ""
 *       val selectedText = inputConnection.getSelectedText(0)
 *       return selectedText?.toString() ?: currentInput
 *   }
 * 
 * 步骤 3: 处理翻译结果
 * ──────────────────
 * 
 * 翻译完成后，结果会显示在 TranslatePanel 的流中。
 * 用户可以点击结果来提交翻译文本：
 * 
 *   // 在 TranslatePanel 中添加点击处理（可选增强）
 *   // 或在 onSelectReply 回调中处理
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 3. 语言代码映射
 * ──────────────
 * 
 * 代码    | 显示名称  | 原生名称
 * --------|----------|----------
 * auto    | Auto-detect | 自动检测
 * zh      | Chinese  | 中文
 * en      | English  | 英文
 * ja      | Japanese | 日文
 * ko      | Korean   | 韩文
 * es      | Spanish  | 西班牙文
 * fr      | French   | 法文
 * de      | German   | 德文
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 4. 后端 API 调用
 * ───────────────
 * 
 * 翻译请求会自动选择后端：
 * 
 * - 如果用户配置了 DeepSeek API Key：
 *   使用 DeepSeekDirectApi.translateText()
 *   
 * - 否则：
 *   使用 BackendAiApi.translateText()
 * 
 * 两个 API 都支持以下参数：
 *   - text: 待翻译文本
 *   - sourceLanguage: 源语言代码（可选，"auto" 表示自动检测）
 *   - targetLanguage: 目标语言代码或名称
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 5. 状态持久化
 * ────────────
 * 
 * 用户选择的源语言和目标语言会自动保存到 SharedPreferences：
 * 
 * - com.arche.threply.ime.translate.sourceLanguage
 * - com.arche.threply.ime.translate.targetLanguage
 * 
 * 下次打开翻译模式时会自动恢复用户的选择。
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 6. UI 特性
 * ─────────
 * 
 * TranslatePanel 包含以下 UI 元素：
 * 
 * - 标题："翻译"
 * - 源语言选择器（支持自动检测）
 * - 交换按钮（⇄）：快速交换源/目标语言
 * - 目标语言选择器（不支持自动检测）
 * - 翻译按钮：触发翻译请求
 * - 流式预览：显示翻译结果
 * - 错误提示：显示翻译失败信息
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 7. 示例用法
 * ──────────
 * 
 * // 用户选择翻译模式
 * aiCoordinator.setMode(ImeAiMode.TRANSLATE)
 * 
 * // 用户选择源语言为中文
 * aiCoordinator.setTranslateSourceLanguage("zh")
 * 
 * // 用户选择目标语言为英文
 * aiCoordinator.setTranslateTargetLanguage("en")
 * 
 * // 用户点击翻译按钮
 * aiCoordinator.requestTranslation(
 *     text = "你好世界",
 *     sourceLanguage = "zh",
 *     targetLanguage = "en"
 * )
 * 
 * // 翻译结果会通过 state.streamingPreview 显示
 * // 用户可以点击结果来提交翻译文本
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 8. 测试检查清单
 * ──────────────
 * 
 * ☐ 翻译模式可以从 B/C 模式切换
 * ☐ 源语言选择器显示所有 8 种语言
 * ☐ 目标语言选择器不显示"自动检测"
 * ☐ 交换按钮可以交换源/目标语言
 * ☐ 翻译按钮在加载时禁用
 * ☐ 翻译结果正确显示
 * ☐ 错误信息正确显示
 * ☐ 语言选择在应用重启后保持
 * ☐ 支持 DeepSeek API 和后端 API 两种方式
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
