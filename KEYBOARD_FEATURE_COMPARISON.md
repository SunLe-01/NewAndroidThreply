# iOS / Android 键盘功能对比表

更新时间：2026-03-16

范围说明：
- 只比较当前代码库里的键盘能力，不比较主 App 页面。
- Android 侧主要依据：`app/src/main/java/com/arche/threply/ime/ThreplyInputMethodService.kt`、`ImeAiOverlay.kt`、`AiImeCoordinator.kt`、`RimeEngineController.kt`、`RimeNativeBridge.kt`。
- iOS 侧主要依据：`../threply/threply/ThreplyKeyboard/ThreplyKeyboardView.swift`、`ThreplyCandidateBar.swift`、`ThreplyBModeView.swift`、`../threply/threply/ThreplyIntents/GenerateRepliesIntent.swift`。

状态说明：
- `完成`：当前代码里已具备完整能力。
- `部分完成`：主能力存在，但成熟度、入口或细节弱于另一端。
- `缺失`：当前代码里未见对应能力闭环。

## 总表

| 功能项 | iOS | Android | 结论 |
| --- | --- | --- | --- |
| 基础英文/中文键盘输入 | 完成 | 完成 | 双端基础输入能力已对齐。 |
| 中文标点优先布局 / 全角标点映射 | 完成 | 完成 | 双端都支持中文标点优先与全角标点映射。 |
| Rime 中文输入主链路 | 完成 | 部分完成 | Android 已接入 Native Rime 和完整 `RimeResources`，但当前仍带 async warm-up、fallback 兜底、仅 `arm64-v8a`，成熟度弱于 iOS。 |
| Rime schema 切换 | 完成 | 完成 | 双端都已支持切换方案；Android 在主 App 里暴露 schema 设置。 |
| 候选栏基础候选展示 | 完成 | 完成 | 双端都有候选栏。 |
| 候选栏元数据（label/comment/highlightedIndex） | 完成 | 缺失 | iOS 候选项保留 `label/comment/highlightedIndex`；Android 当前基本只展示文本 chip，候选信息密度较低。 |
| 候选展开面板 | 完成 | 完成 | Android 已补齐展开候选面板，不再是旧文档里的缺口。 |
| 候选展开面板交互精细度 | 完成 | 部分完成 | iOS 有 `fitCount`、高亮索引和更完整的候选栏状态；Android 当前是固定 4 列网格，交互较粗。 |
| Skills 工具栏 | 完成 | 部分完成 | iOS 有 `Translate / Replace / Polish / Kaomoji / Memes`；Android 当前只有 `Translate / Replace / Polish`。 |
| Replace / Polish 后撤销 | 完成 | 完成 | Android 已有 undo bar，不再落后。 |
| 键盘内翻译模式 | 完成 | 完成 | 双端都支持键盘内翻译。 |
| B 模式基础三回复 | 完成 | 完成 | 双端都有基础三卡片回复。 |
| B 模式长按展开回复树 | 完成 | 完成 | Android 已补齐长按展开，不再缺失。 |
| B 模式返回上一层 | 完成 | 完成 | Android 已补齐 reply tree back。 |
| C 模式风格面板 | 完成 | 完成 | 双端都支持风格坐标面板。 |
| 键盘内 AI 面板（B/C/翻译切换） | 完成 | 完成 | 双端都有独立 AI 面板。 |
| 键盘内扫描 / OCR 触发入口 | 部分完成 | 完成 | iOS 键盘主要消费 Shortcut/AppIntent 回灌结果；Android 键盘内有显式“扫描”入口，并接了截图/Accessibility OCR 闭环。 |
| 系统级 Shortcut / AppIntent 联动键盘 | 完成 | 缺失 | iOS 有 `SharedTrigger + GenerateRepliesIntent` 闭环；Android 当前未见同级别系统快捷指令链路。 |
| 键盘内 Pro / 订阅门控 | 完成 | 缺失 | iOS 键盘内有 B 模式锁定和订阅跳转；Android 当前 `isAiAccessAllowed()` 直接返回 `true`，仍是测试态。 |
| 键盘内订阅跳转反馈 | 完成 | 缺失 | iOS 已接 paywall jump；Android 键盘内未见同级闭环。 |
| 用户画像驱动 AI 回复 | 部分完成 | 完成 | Android 键盘回复 prompt 已注入 `UserProfileStore.toPromptSnippet()`；iOS 当前代码里能看到画像采集/存储，但未见键盘回复链路显式注入。 |
| 触感反馈丰富度 | 完成 | 部分完成 | iOS 有较完整的 `tap / select / confirm / longPress / togglePulse` 等模式；Android 当前主要是基础振动工具。 |
| 键盘 callout / 长按视觉反馈 | 完成 | 缺失 | iOS 键盘已有 callout 风格体系；Android 当前未见同级别按键 callout 体验。 |
| 大小写 / Shift 状态细节 | 完成 | 部分完成 | Android 目前主要是简单 `isUppercase` toggle；iOS 键盘整体状态机更成熟。 |

## 结论

### 1. 安卓已经追平或基本追平的项

- 候选展开面板
- Replace / Polish 撤销
- B 模式回复树展开
- B 模式返回上一层
- C 模式
- 键盘内翻译模式
- Native Rime + 完整 `RimeResources` 的接入链路

这意味着旧版本认知里“Android 键盘只有基础能力、很多核心功能没做完”已经不成立。

### 2. 安卓当前仍落后 iOS 的重点

- 候选栏信息密度与精细度仍弱，缺少 `label/comment/highlightedIndex` 这一层表现。
- Skills 工具栏仍少 `Kaomoji` 和 `Memes`。
- 键盘内 Pro / 订阅 / paywall 闭环还没恢复，当前仍是放开测试态。
- 系统级 Shortcut / AppIntent 联动能力明显弱于 iOS。
- 触感、callout、大小写状态机这类键盘交互细节仍弱于 iOS。
- Android 的 Native Rime 现状虽然已接通，但稳定性和成熟度仍低于 iOS。

### 3. 安卓当前领先 iOS 的项

- 键盘内直接“扫描”入口与 Accessibility OCR 闭环更完整。
- 键盘 AI 回复已接入用户画像 prompt 注入，个性化能力走得比 iOS 键盘更前。

## 最终判断

如果只看“有没有这项功能”，Android 键盘已经不是大幅落后状态，很多历史缺口都已经补上。

如果看“产品完成度和键盘细节”，Android 仍然落后 iOS，主要差在：
- 候选栏精细化
- 付费门控闭环
- 技能栏丰富度
- 快捷指令联动
- 触感与按键交互细节
- Rime 运行成熟度

如果下一轮要优先补齐 iOS 差距，建议按这个顺序：
1. 恢复键盘内 Pro / paywall 闭环
2. 补齐候选栏元数据与交互细节
3. 补 `Kaomoji / Memes`
4. 补系统级快捷指令触发链路
5. 继续打磨 Native Rime 运行稳定性与候选体验
