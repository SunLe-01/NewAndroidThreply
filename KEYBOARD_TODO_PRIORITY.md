# Android 键盘功能差距任务表（按优先级更新）

> 基于 2026-03-15 源码核对生成
>
> Android 基线：`ThreplyInputMethodService`、`ImeAiOverlay`、`AiImeCoordinator`、`RimeEngineController`
>
> iOS 基线：`ThreplyKeyboardView`、`ThreplyCandidateBar`、`ThreplyBModeView`、`KeyboardViewController`

---

## 本次核对结论

- 已完成并从待办移除：候选词横向滚动、候选词展开面板、候选词首项高亮、宿主输入框 `setComposingText()`、外部选区变化清空拼音缓冲、选中文本自动弹技能栏、`Replace/Polish` 一步撤销、翻译语言面板基础实现。
- 已修正旧判断：Android 当前的 Native Rime 主要问题不是“16KB 对齐未修”，而是 `app/src/main/cpp/CMakeLists.txt` 在缺少 `libRime.so` 时只会编译 stub 版 `rime_jni`，同时项目里也没有 `app/src/main/assets/rime/shared` 资源目录，导致真实 Rime 路径事实上不可用。
- 已修正旧差异：iOS 数据层虽然保留了 `CandidateItem.label/comment`，但当前 `ThreplyCandidateBar` 的实际 UI 只显示 `text`，所以“候选词 label/comment 显示”不再作为安卓追平 iOS 的高优先级任务。

---

## 优先级说明

- **P0 — 核心阻塞**：不解决就无法接近 iOS 核心体验
- **P1 — 高优先级**：用户高频路径差距，近期应完成
- **P2 — 中优先级**：体验补齐，提升一致性
- **P3 — 低优先级**：细节增强或工程化补全

---

## P0 — 核心阻塞

### T01. 接入真实 Native Rime 与完整资源打包链路

- **当前状态**：`app/src/main/cpp/CMakeLists.txt` 在缺少 `app/src/main/jniLibs/<ABI>/libRime.so` 时会编译 stub-only `rime_jni`；`app/src/main/assets` 下也没有 `rime/shared` 资源，`RimeResourceManager` 无法准备 iOS 同级别词库与 schema。
- **差距表现**：Android 中文输入当前实质依赖 fallback 词库；iOS 已通过 `RimeBootstrapper` 打包完整资源并稳定运行真实 Rime。
- **目标**：补齐 `libRime.so`、`rime_api.h`、Rime assets、schema/OpenCC/emoji 资源，打通初始化、预热、回退和版本更新链路。
- **为什么是 P0**：这是当前 Android 与 iOS 最大的底层能力差距，直接决定中文输入质量上限。
- **涉及文件**：`app/src/main/cpp/CMakeLists.txt`、`app/src/main/cpp/rime_jni.cpp`、`RimeNativeBridge.kt`、`RimeResourceManager.kt`、`app/src/main/assets/rime/`

---

## P1 — 高优先级

### T02. 打通 IME 内 AI 权限门控与主 App Paywall 跳转

- **当前状态**：`ThreplyInputMethodService.isAiAccessAllowed()` 直接返回 `true`；`BillingManager`、`PrefsManager.isProEntitled()`、`MainActivity` 的 `threply://paywall` 路由都已存在，但 IME 没接入。
- **差距表现**：iOS B 模式和订阅入口已经闭环，Android 仍是“全量放开”状态。
- **目标**：在 IME 内统一校验登录态与 Pro 权限，未满足时展示轻量拦截 UI，并拉起主 App Paywall。
- **涉及文件**：`ThreplyInputMethodService.kt`、`BillingManager.kt`、`MainActivity.kt`

### T03. 完成 B 模式回复树闭环

- **当前状态**：Android 已有 `BModePanel.onLongPress` 和 `AiImeCoordinator.expandReplyForChildren()`，但当前 UI 仍只渲染 `state.suggestions.map { it.text }`；`currentReplyTreePath` 没有真正入栈，返回按钮也不会出现。
- **差距表现**：iOS 已支持长按展开子回复、多层返回、流式子树生成；Android 仍是单层 3 条建议。
- **目标**：补齐“长按展开子回复 -> 子层渲染 -> 返回上一层 -> 继续选择/再展开”的完整状态机。
- **涉及文件**：`ImeAiOverlay.kt`、`BModePanel.kt`、`AiImeCoordinator.kt`、`SuggestionModels.kt`

### T04. 补齐中文标点行为：全角符号、中文标点行、双空格句号

- **当前状态**：Android `commitText()` 仍直接输出 ASCII；符号键盘未做全角映射；`PrefsManager.isAutoSentencePunctuationEnabled()` 已有设置项，但 IME 路径没有使用。
- **差距表现**：iOS 已实现 `layoutWithFullwidthCharacters()`、`layoutWithPunctuationRow()`、`shouldEndCurrentSentence()`。
- **目标**：在中文输入模式下输出全角标点，符号页优先展示中文标点，并接通“双空格自动句号”行为。
- **涉及文件**：`ThreplyInputMethodService.kt`、`PrefsManager.kt`

### T05. 接入基础触觉反馈到键盘主路径

- **当前状态**：Android 有 `HapticsUtil.kt`，但键盘、候选栏、AI 面板当前没有任何调用，实际输入是“全静默”。
- **差距表现**：iOS 在按键、候选词、确认、模式切换上都有一致的触觉反馈。
- **目标**：先补齐基础触觉：按键点击、退格、回车、候选选择、展开/收起、AI/B/C 切换。
- **为什么是 P1**：这是高频触点，体感差距非常明显，而且实现成本低于原生 Rime。
- **涉及文件**：`ThreplyInputMethodService.kt`、`BModePanel.kt`、`CModePanel.kt`、`HapticsUtil.kt`

### T06. 在 Native Rime 落地前继续增强 fallback 中文输入兜底

- **当前状态**：fallback 词库已从旧文档里的“约 20 条”提升到约 `483` 个拼音条目，但仍明显弱于 iOS 完整 Rime 资源，且没有 emoji / 用户词典级能力。
- **差距表现**：一旦 native 路径不可用，Android 中文输入质量立刻退化。
- **目标**：继续补全高频会话词、常用短句、中文标点/emoji 触发词，优化 prefix 命中与评分策略，至少把 fallback 做到“可用”而不是“演示级”。
- **涉及文件**：`RimeFallbackLexicon.kt`、`RimeFallbackLexiconExt1.kt`、`RimeFallbackLexiconExt2.kt`

---

## P2 — 中优先级

### T07. 把 TranslatePanel 打通到 slash 技能栏工作流

- **当前状态**：Android 已有 `TranslatePanel.kt` 和 `AiImeCoordinator.requestTranslation()`，但它们只存在于 AI Overlay；slash 技能栏里的“翻译”仍然直接走 `triggerSkill(TextSkill.TRANSLATE)` 默认翻译路径。
- **差距表现**：iOS 的翻译工作流在候选栏内完成，选中文本后可以直接选择源/目标语言并确认；Android 现在是“两套入口，两套行为”。
- **目标**：统一为“选中文本 -> `/` 技能栏 -> 翻译 -> 选语言 -> 确认替换”的单一路径。
- **涉及文件**：`ThreplyInputMethodService.kt`、`TranslatePanel.kt`、`AiImeCoordinator.kt`

### T08. 支持 Caps Lock

- **当前状态**：Android 只有 `isUppercase` 单次切换，没有双击 Shift 锁定大写。
- **差距表现**：iOS 依赖 KeyboardKit 原生 `.capsLock`。
- **目标**：实现双击 Shift 进入/退出 Caps Lock，并给出明确视觉状态。
- **涉及文件**：`ThreplyInputMethodService.kt`

### T09. 支持长按次选字符 Callout

- **当前状态**：Android 没有字母长按气泡，也没有变音字符面板。
- **差距表现**：iOS 已使用 KeyboardKit 的 action callout 体系。
- **目标**：先覆盖英文常用次选字符，再视情况扩展到更多键位。
- **涉及文件**：`ThreplyInputMethodService.kt`

### T10. 补齐 B/C 模式高级触觉

- **当前状态**：Android 还没有 iOS 那种逐字打字触觉、C 模式格点触觉、toggle 双脉冲、long-press 持续振动。
- **目标**：在基础触觉接入后，再补齐 B 模式逐字反馈、C 模式格点反馈和模式切换特效。
- **涉及文件**：`BModePanel.kt`、`CModePanel.kt`、`HapticsUtil.kt`

### T11. 实现键盘日志持久化并在主 App 查看

- **当前状态**：Android 仍主要依赖 Logcat；iOS 已有 `KeyboardLogStore` + `DeveloperMenuView`。
- **目标**：把 IME 关键日志写入共享存储，并提供主 App 侧开发者查看入口。
- **涉及文件**：`ThreplyInputMethodService.kt`、主 App 开发者页相关文件

### T12. 实现回复建议/展开历史记录

- **当前状态**：iOS 已记录 `recordSuggestionSelection`、`recordExpandedReplies`；Android 没有对应本地留痕。
- **目标**：记录 B 模式选择、展开、风格变化等行为，为调优和回放提供数据。
- **涉及文件**：`AiImeCoordinator.kt`、新增本地 history store、主 App 开发者页

---

## P3 — 低优先级

### T13. 增加按键按下态与细节动效

- **当前状态**：Android 键盘键帽是静态背景，没有明显的按下态；iOS 依赖 KeyboardKit/callout 体系，交互反馈更完整。
- **目标**：为普通键、功能键、候选按钮增加按下态和轻量过渡。
- **涉及文件**：`ThreplyInputMethodService.kt`

### T14. 补充键盘相关自动化测试

- **当前状态**：当前对拼音、fallback、AI 协调器、回复树状态的回归保护较弱。
- **目标**：至少补齐 `PinyinComposer`、`RimeFallbackLexicon`、`AiImeCoordinator`、回复树状态机的单测。
- **涉及文件**：`app/src/test/`

---

## 已完成或暂不再列入的旧任务

- 已完成：候选词横向滚动
- 已完成：候选词展开面板
- 已完成：候选词高亮当前项
- 已完成：宿主输入框 composing text / marked text 基础能力
- 已完成：外部文本变化时重置拼音输入状态
- 已完成：选中文本自动弹出技能栏
- 已完成：`Replace / Polish` 一步 Undo
- 已完成：翻译语言选择基础面板与状态持久化
- 暂不列为 iOS 对齐主差距：候选词 `label/comment` UI 显示

---

## 任务汇总

| 编号 | 任务 | 优先级 | 说明 |
| --- | --- | --- | --- |
| T01 | 接入真实 Native Rime 与完整资源打包链路 | P0 | 当前中文输入能力上限的核心阻塞 |
| T02 | 打通 IME 内 AI 权限门控与主 App Paywall 跳转 | P1 | 商业化与 iOS 权限体验未闭环 |
| T03 | 完成 B 模式回复树闭环 | P1 | Android 仍是单层建议，iOS 已多层展开 |
| T04 | 补齐中文标点行为：全角符号、中文标点行、双空格句号 | P1 | 高频中文输入路径差距 |
| T05 | 接入基础触觉反馈到键盘主路径 | P1 | Android 当前实际没有键盘触觉 |
| T06 | 在 Native Rime 落地前继续增强 fallback 中文输入兜底 | P1 | 现阶段 native 不可用时仍需止血 |
| T07 | 把 TranslatePanel 打通到 slash 技能栏工作流 | P2 | 现有翻译入口分裂，和 iOS 不一致 |
| T08 | 支持 Caps Lock | P2 | 基础输入能力差距 |
| T09 | 支持长按次选字符 Callout | P2 | 英文输入细节差距 |
| T10 | 补齐 B/C 模式高级触觉 | P2 | 体验细节，不阻塞主流程 |
| T11 | 实现键盘日志持久化并在主 App 查看 | P2 | 调试能力差距 |
| T12 | 实现回复建议/展开历史记录 | P2 | 数据留痕与调优能力差距 |
| T13 | 增加按键按下态与细节动效 | P3 | 视觉细节增强 |
| T14 | 补充键盘相关自动化测试 | P3 | 工程化补全 |

