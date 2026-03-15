# Threply 键盘功能对照表

> iOS 端（ThreplyKeyboard / KeyboardKit）vs Android 端（ThreplyInputMethodService）
>
> 生成日期：2026-03-14

---

## 1. 基础输入层


| 功能                | iOS 端                                 | Android 端                                    | 差异说明                         |
| ----------------- | ------------------------------------- | -------------------------------------------- | ---------------------------- |
| 英文字母输入            | ✅ KeyboardKit 标准键盘                    | ✅ 自定义 View 实现                                | Android 为纯手写布局，无 KeyboardKit |
| 大小写切换（Shift）      | ✅ KeyboardKit + 手动注册防自动大写             | ✅ `isUppercase` toggle + `rebuildKeyboard()` | iOS 有专项防自动大写逻辑               |
| Caps Lock         | ✅ KeyboardKit 原生支持                    | ❌ 未实现                                        | Android 无独立 CapsLock 键       |
| 数字键盘              | ✅ KeyboardKit `.numeric` 类型           | ✅ `isSymbolMode` 切换                          | iOS 使用多键盘类型，Android 仅单层切换    |
| 符号键盘              | ✅ KeyboardKit `.symbolic` + 全宽字符映射    | ✅ 符号行内切换                                     | iOS 自动映射全角符号；Android 无全角映射   |
| 退格键（长按连删）         | ✅ KeyboardKit `.repeat` 手势            | ✅ Handler + postDelayed(50ms)                | 实现方式不同，功能等效                  |
| 回车/确认键（多态 label）  | ✅ KeyboardKit `.primary` + IME Action | ✅ `EditorInfo.IME_MASK_ACTION` 动态 label      | 功能等效                         |
| 空格键               | ✅ KeyboardKit `.space`                | ✅ 拼音时自动上屏首候选                                 | 功能等效                         |
| 长按 Callout 次选字符   | ✅ KeyboardKit Action Callout（仅激活时显示）  | ❌ 未实现                                        | Android 无长按 callout          |
| 按键 Hint 字符（右上角提示） | ❌ 无                                   | ✅ 每键右上角显示 hint 字符                            | Android 有，iOS 无              |
| 按键按下视觉反馈          | ✅ KeyboardKit 内置                      | ⚠️ 无按下高亮动画                                   | Android 缺按键按下态               |


---

## 2. 中文输入（拼音 + Rime 引擎）


| 功能                   | iOS 端                                         | Android 端                                        | 差异说明                            |
| -------------------- | --------------------------------------------- | ------------------------------------------------ | ------------------------------- |
| 拼音输入模式               | ✅ Rime 引擎驱动（RimeKit / librime）                | ✅ `PinyinComposer` + `RimeEngineController`      |                                 |
| Rime Native 引擎       | ✅ 完整 RimeKit，稳定可用                             | ⚠️ JNI 桥接，可选，加载失败自动 fallback                     | Android native Rime 不稳定，为可选状态   |
| 候选词条显示               | ✅ `ThreplyCandidateBar` 横向滚动列表                | ✅ `suggestionContainer` 水平 LinearLayout          | iOS 实现更完整（滚动、高亮索引）              |
| 候选词展开全屏面板            | ✅ `ThreplyCandidateExpandedPanel` 网格展开        | ❌ 未实现                                            | Android 无展开面板                   |
| 候选词真正滚动              | ✅ `CandidateCarouselView`（UICollectionView）   | ⚠️ 固定显示最多 5 个，无真正滚动                              | Android 候选词 UI 简陋               |
| 候选词高亮当前项             | ✅ `highlightedIndex` 高亮                       | ❌ 无高亮                                            |                                 |
| 候选词 label/comment 显示 | ✅ `CandidateItem.label` + `comment`           | ❌ 无 label/comment                                |                                 |
| 分页翻页                 | ✅ Rime 引擎原生分页                                 | ✅ 「‹」/「›」按钮 + `pinyinPage`                       | 机制不同，功能等效                       |
| 内联合成文本（Marked Text）  | ✅ `proxy.setMarkedText` 真正 marked text        | ⚠️ 仅 `streamingPreviewView` 文字显示                 | Android 无真正 marked text，体验较差    |
| 拼音 Backspace 删字母     | ✅ `RimeActionHandler` 处理                      | ✅ `PinyinComposer.pop()`                         | 功能等效                            |
| Rime Schema 可配置      | ✅ `RimeBootstrapper` + App Group UserDefaults | ✅ `PrefsManager.getImeRimeSchema()`              | 功能等效                            |
| Fallback 词库          | ✅ Rime 内置完整词库                                 | ✅ `RimeFallbackLexicon` + `PinyinComposer` 静态小词典 | Android fallback 词库极小（仅约 20 词组） |
| 自动大写禁用（中文模式）         | ✅ `syncAutocapitalizationOverride` 专项逻辑       | ❌ 未实现                                            | Android 中文模式仍可能触发系统自动大写         |
| Emoji 候选词            | ✅ Rime Schema 可返回 Emoji                       | ❌ 未实现                                            |                                 |
| 外部文本变化重置输入状态         | ✅ `syncRimeCompositionWithHostContext`        | ❌ 未实现                                            | Android 外部文本变化不清除拼音输入缓冲         |
| 用户词库/个性化学习           | ❌ 未实现                                         | ❌ 未实现                                            | 双端均缺失                           |


---

## 3. 语言切换


| 功能            | iOS 端                                    | Android 端                              | 差异说明              |
| ------------- | ---------------------------------------- | -------------------------------------- | ----------------- |
| 中英文切换         | ✅ Rime ASCII Mode（`isASCIIMode`）切换       | ✅ `toggleInputLanguage()`              | 实现机制不同，功能等效       |
| 切换按钮 label    | ✅ AI sparkle 按钮旁（底行）                     | ✅ 底行右侧「中/英」按钮                          | 位置不同，功能等效         |
| 语言偏好持久化       | ✅ App Group UserDefaults                 | ✅ `PrefsManager.setImeInputLanguage()` | 功能等效              |
| 符号/数字键盘全宽字符映射 | ✅ `layoutWithFullwidthCharacters()` 自动处理 | ❌ 未实现                                  | Android 符号模式无全角字符 |
| 中文标点自动替换      | ✅ `layoutWithPunctuationRow()`（。，、？！）    | ❌ 未实现                                  | Android 无中文标点自动替换 |
| 双语切换时清除拼音缓冲   | ✅ Rime ASCII 模式切换时自动清除                   | ✅ `pinyinComposer.clear()`             | 功能等效              |


---

## 4. AI 功能 — B 模式（回复建议）


| 功能                 | iOS 端                                               | Android 端                                  | 差异说明                           |
| ------------------ | --------------------------------------------------- | ------------------------------------------ | ------------------------------ |
| B 模式入口             | ✅ 底行 sparkle（✦）按钮 toggle                            | ✅ AI 面板 B Chip 按钮                          |                                |
| 生成 3 条回复建议         | ✅ 卡片式，支持流式逐字动画                                      | ✅ `BModePanel` 卡片 + shimmer + 逐字动画         | 功能等效                           |
| SSE 流式推流           | ✅ 完整 SSE 解析 + 自动降级非流式                               | ✅ 支持 streaming delta 回调                    | 功能等效                           |
| 逐字打字动画             | ✅ 18ms/字 + 随机触觉反馈                                   | ✅ 18ms/字（无触觉）                              | Android 逐字动画缺触觉配合              |
| 回复树（长按展开子回复）       | ✅ `handleLongPress` 生成子树，支持多层                       | ❌ 未实现                                      | Android 无回复树，无子回复展开            |
| 树层级返回              | ✅ `handleBack` 返回上一层                                | ❌ 未实现                                      |                                |
| 刷新建议               | ✅ 刷新按钮 `onRefresh`                                  | ✅ `triggerAiFromCurrentInput()`            | 功能等效                           |
| 点击建议直接插入           | ✅ `insertSuggestion`                                | ✅ `commitSuggestion`                       | 功能等效                           |
| Pro 订阅门控           | ✅ `isBModeLocked` + Paywall 界面                      | ⚠️ 代码预留（`isAiAccessAllowed` 恒 true）        | Android 目前无权限控制                |
| Paywall 跳转主 App    | ✅ `extensionContext.open(paywallURL)`               | ❌ 未实现                                      |                                |
| 建议选择历史记录           | ✅ `recordSuggestionSelection` + `ReplyHistoryStore` | ❌ 未实现                                      |                                |
| 展开建议历史记录           | ✅ `recordExpandedReplies`                           | ❌ 未实现                                      |                                |
| DeepSeek 直连 API    | ✅ `DeepSeekKeyboardAPI`                             | ✅ `DeepSeekDirectApi`                      | 功能等效                           |
| 后端 API（需登录）        | ✅ Bearer Token + 自动刷新                               | ✅ `BackendAiApi` + Session Store           | 功能等效                           |
| 上下文草稿同步（跨 App）     | ✅ `SharedSuggestions.saveUserDraft()`               | ⚠️ `PrefsManager.setImeLastInputContext()` | iOS 实现更完整（含 host bundle ID 记录） |
| Shortcut/Intent 触发 | ✅ `SharedTrigger` + Darwin 通知机制                     | ✅ `SharedTriggerStore` + SharedPreferences | 功能等效，机制不同                      |


---

## 5. AI 功能 — C 模式（风格控制）


| 功能             | iOS 端                             | Android 端                      | 差异说明          |
| -------------- | --------------------------------- | ------------------------------ | ------------- |
| 2D 拖拽风格板       | ✅ `CModeKeyboardCard`（210dp 高）    | ✅ `CModePanel`（180dp 高）        | 功能等效，高度略有差异   |
| 长度轴（更长/更短）     | ✅ X 轴，-1 ~ +1                     | ✅ X 轴，-1 ~ +1                  | 等效            |
| 温度轴（温暖/克制）     | ✅ Y 轴，-1 ~ +1                     | ✅ Y 轴，-1 ~ +1                  | 等效            |
| 动态混色 Accent 颜色 | ✅ 四角混色（橙/绿/蓝/紫）                   | ✅ 四角混色（同算法）                    | 等效            |
| 网格点场动画         | ✅ `DotFieldView`（SwiftUI Canvas）  | ✅ Android Canvas 逐点绘制          | 等效            |
| 拖拽格点触觉反馈       | ✅ 每过一格触发 `Haptics.select()`       | ❌ 无触觉反馈                        | Android 缺拖拽触觉 |
| 风格状态描述文字       | ✅ 英文描述（"long and warm"）           | ✅ 中文描述（"更长，语气更温暖"）             | 语言不同，功能等效     |
| 风格持久化          | ✅ `SharedSuggestions.saveStyle()` | ✅ `PrefsManager.setImeStyle()` | 功能等效          |
| 确认后重新生成回复      | ✅ `applyStyleAndRegenerate`       | ✅ `onStyleConfirm` 回调          | 功能等效          |
| 退出 C 模式        | ✅ ✕ 按钮返回 B 模式                     | ✅ 切换 B/C Chip                  |               |


---

## 6. AI 技能栏（Toolbar Skills）


| 功能                      | iOS 端                                             | Android 端                                | 差异说明                 |
| ----------------------- | ------------------------------------------------- | ---------------------------------------- | -------------------- |
| 技能栏入口（/ 按钮）             | ✅ 候选栏左侧 / 按钮                                      | ❌ 未实现                                    | Android 无 Skills 技能栏 |
| 翻译（Translate）           | ✅ 选中文本 → 选语言对 → 确认替换                              | ⚠️ `triggerSkill(TRANSLATE)` 有实现，无 UI 入口 | Android 有逻辑，无界面入口    |
| 替换（Replace）             | ✅ 选中文本 → AI 重写 → 可 Undo                           | ⚠️ `triggerSkill(REPLACE)` 有实现，无 UI 入口   | 同上                   |
| 润色（Polish）              | ✅ 选中文本 → AI 润色 → 可 Undo                           | ⚠️ `triggerSkill(POLISH)` 有实现，无 UI 入口    | 同上                   |
| 操作 Undo（Replace/Polish） | ✅ `ReplaceUndoContext` / `PolishUndoContext` 一步撤销 | ❌ 未实现                                    |                      |
| 颜文字（Kaomoji）            | ✅ 技能栏占位（后端未实现）                                    | ❌ 未实现                                    | 双端均为占位               |
| Meme 表情                 | ✅ 技能栏占位（后端未实现）                                    | ❌ 未实现                                    | 双端均为占位               |
| 文本选中自动弹出技能栏             | ✅ `updateSelectionStateIfNeeded` 自动切换 skills 模式   | ❌ 未实现                                    | Android 无选中文本自动联动    |
| 翻译语言选择 UI               | ✅ 候选栏内 Menu 选择器（8 种语言）                            | ❌ 未实现                                    |                      |


---

## 7. 聊天扫描（Chat Scan / OCR）


| 功能          | iOS 端                                     | Android 端                                                  | 差异说明       |
| ----------- | ----------------------------------------- | ---------------------------------------------------------- | ---------- |
| 聊天内容扫描入口    | ✅ B 模式面板「扫描」按钮                            | ✅ ImeAiOverlay「扫描」按钮                                       | 功能等效       |
| 扫描实现方式      | ✅ App Intents / Shortcut（`SharedTrigger`） | ✅ `ChatScanAccessibilityService` 辅助功能服务                    | 机制不同       |
| OCR 引擎      | ❌ 不在键盘内（由主 App 处理）                        | ✅ `OcrEngine` + `ScreenshotMonitorService`                 | Android 独有 |
| 截图自动监听      | ❌ 不在键盘内                                   | ✅ `ScreenshotContentObserver` + `ScreenshotMonitorService` | Android 独有 |
| 扫描结果回传键盘    | ✅ Darwin 通知 + `SharedSuggestions`         | ✅ `BroadcastReceiver`（`ACTION_SCAN_RESULT`）                | 机制不同，功能等效  |
| 扫描结果解析为建议列表 | ✅ 由 Intents/主 App 处理                      | ✅ 按行解析为 `ImeSuggestion` 列表                                 |            |


---

## 8. 触觉反馈（Haptics）


| 功能            | iOS 端                                | Android 端                                       | 差异说明     |
| ------------- | ------------------------------------ | ----------------------------------------------- | -------- |
| 触觉引擎          | ✅ CoreHaptics `CHHapticEngine` 常驻实例  | ✅ `VibrationEffect` / `HapticFeedbackConstants` | iOS 精度更高 |
| 按键轻触（tap）     | ✅ `Haptics.tap()` intensity 0.35     | ✅ `KEYBOARD_TAP`                                | 功能等效     |
| 选择反馈（select）  | ✅ `Haptics.select()` intensity 0.45  | ✅ `GESTURE_START` / `CLOCK_TICK`                | 功能等效     |
| 确认反馈（confirm） | ✅ `Haptics.confirm()` intensity 0.8  | ✅ `EFFECT_HEAVY_CLICK`                          | 功能等效     |
| Toggle 双脉冲    | ✅ `Haptics.togglePulse()`（双脉冲 0.08s） | ❌ 未实现                                           |          |
| 长按持续振动        | ✅ `Haptics.longPress()`（0.18s 连续）    | ❌ 未实现                                           |          |
| 强度可调节         | ✅ App Group `hapticStrength` 0~1 浮点  | ✅ `PrefsManager` 开关 + 强度配置                      | 功能等效     |
| 逐字动画配合触觉      | ✅ 每 2~4 字触发一次 tap                    | ❌ 无触觉配合                                         |          |
| C 模式拖拽格点触觉    | ✅ 每过一格触发                             | ❌ 未实现                                           |          |


---

## 9. 键盘生命周期 & 稳定性


| 功能                | iOS 端                                 | Android 端                             | 差异说明                         |
| ----------------- | ------------------------------------- | ------------------------------------- | ---------------------------- |
| 键盘视图创建失败兜底        | ✅ KeyboardKit 框架保障                    | ✅ `createEmergencyInputView()` 紧急键盘   |                              |
| Compose 生命周期管理    | N/A（SwiftUI 原生）                       | ✅ `ImeLifecycleOwner` 自定义生命周期         |                              |
| 协程/异步资源管理         | ✅ Swift Task 结构化并发                    | ✅ `imeScope(SupervisorJob)` 取消管理      | 功能等效                         |
| AI 请求取消（切换/退出）    | ✅ `Task.cancel()`                     | ✅ `aiCoordinator.cancel()`            | 功能等效                         |
| 全屏模式禁用            | ✅ `onEvaluateFullscreenMode` 返回 false | ✅ `onEvaluateFullscreenMode` 返回 false | 功能等效                         |
| 强制显示软键盘           | ✅ `onEvaluateInputViewShown` 返回 true  | ✅ `onEvaluateInputViewShown` 返回 true  | 功能等效                         |
| Full Access 检测与同步 | ✅ `syncFullAccessToAppGroup()`        | ❌ 未实现（Android 无此概念）                   |                              |
| 调试日志系统            | ✅ `KeyboardLogStore` 主 App 内可查看       | ✅ Logcat + Log.TAG                    | iOS 日志可持久查阅；Android 仅 Logcat |


---

## 10. 配置与设置


| 功能                  | iOS 端                                                | Android 端                                           | 差异说明          |
| ------------------- | ---------------------------------------------------- | --------------------------------------------------- | ------------- |
| 自动句末标点（双空格）         | ✅ `ThreplyKeyboardBehavior.shouldEndCurrentSentence` | ❌ 未实现                                               |               |
| 惯用手设置               | ✅ `handedness` UserDefaults                          | ✅ `PrefsManager.getHandedness()`                    | 功能等效          |
| 语言偏好                | ✅ `languagePreference`                               | ✅ `PrefsManager.getLanguagePreference()`            | 功能等效          |
| 后端 Base URL 可配置     | ✅ App Group UserDefaults                             | ✅ `PrefsManager.getBackendBaseURL()`                | 功能等效          |
| DeepSeek API Key 配置 | ✅ KeychainManager（Keychain 安全存储）                     | ✅ `PrefsManager`（SharedPreferences）                 | iOS 存储更安全     |
| Pro 订阅状态            | ✅ StoreKit + `SharedAccountStatus.isProEntitled()`   | ✅ `BillingManager` + `PrefsManager.isProEntitled()` | 功能等效          |
| Rime Native 开关      | ✅ `RimeBootstrapper.isRimeEnabled()`                 | ✅ `PrefsManager.isImeRimeNativeEnabled()`           | 功能等效          |
| AI 功能总开关            | ✅ 隐式（Pro 门控）                                         | ✅ `PrefsManager.isImeAiEnabled()`                   | Android 有独立开关 |


---

## 11. 总结：功能覆盖率估算


| 分类          | iOS 端功能数 | Android 已实现 | 覆盖率      |
| ----------- | -------- | ----------- | -------- |
| 基础输入层       | 11       | 8           | ~73%     |
| 中文输入 / Rime | 16       | 9           | ~56%     |
| 语言切換        | 6        | 4           | ~67%     |
| AI B 模式     | 16       | 10          | ~63%     |
| AI C 模式     | 10       | 8           | ~80%     |
| AI 技能栏      | 9        | 1           | ~11%     |
| 聊天扫描        | 6        | 5           | ~83%     |
| 触觉反馈        | 9        | 5           | ~56%     |
| 生命周期 & 稳定性  | 8        | 6           | ~75%     |
| 配置与设置       | 8        | 7           | ~88%     |
| **合计**      | **99**   | **63**      | **~64%** |


