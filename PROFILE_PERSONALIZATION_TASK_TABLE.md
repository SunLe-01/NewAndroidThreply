# Android 用户形象定制回复任务表

更新时间：2026-03-16

## 目标

在 Android 端新增“用户形象驱动的定制化 AI 回复”能力，让 B/C 模式、截图智能回复、聊天扫描回复在生成候选时，能够参考用户的兴趣偏好、常用口头禅、语气习惯等信息。

目标画像不只是“用户喜欢什么”，还要覆盖“用户怎么说话”：

- 兴趣偏好：如计算机、汽车、游戏、动漫、数码、运动等
- 语言习惯：常用口头禅、偏好敬语/口语、是否爱用 emoji、是否简洁
- 表达边界：不喜欢的表达方式、少用/禁用词
- 置信度：哪些结论可靠，哪些只是弱推断

## 参考 iOS 后的结论

iOS 现有链路已经具备这些基础能力：

- OCR 文本归档
- 回复历史归档
- 画像推断开关和阈值
- `ai/profile/infer` 后端调用
- 画像 JSON 的本地存储
- 主 App 内的开发者调试查看

但从当前源码看，iOS 更像是完成了“采集 + 推断 + 存储 + 调试展示”，并没有明确看到“画像已经实质注入到回复生成 prompt”这一段。因此 Android 不能只照搬 iOS，要在参考其基础设施的同时，把“个性化 prompt 注入”补成完整闭环。

## 为什么不能只靠 OCR 做画像

如果只采集 OCR / 聊天上下文，最多比较适合推断用户最近在聊什么主题，但不够适合推断“用户自己的口头禅”。

要把“兴趣偏好”和“说话方式”分开建模：

- 兴趣偏好：
  - 可以主要从 OCR / 聊天扫描内容推断
  - 也可以从用户选择过的回复主题反推
- 口头禅 / 语气风格：
  - 必须优先看“用户自己发送过什么”
  - Android 端最容易拿到的代理数据，是“用户最终选中的 AI 回复”以及“用户主动输入/改写后的内容”

结论：Android 端至少要同时做两类采集：

- OCR / 聊天上下文归档
- AI 回复选中历史归档

## Android 建议架构

### 1. 数据采集层

- OCR 文本归档
  - 来自截图监听、聊天扫描、B 模式上下文读取
- 用户表达归档
  - 记录用户最终选中的 AI 回复
  - 后续可选记录“用户原始输入 + 最终发送文本”的差异

### 2. 画像推断层

- 本地只负责收集样本和调度
- 真正的画像推断仍走后端 `ai/profile/infer`
- 推断输入建议拆成三桶：
  - `observedContextTexts`：OCR / 聊天上下文
  - `userAuthoredTexts`：用户自己发过/选中过的文本
  - `manualSeeds`：用户手动设置的兴趣标签、口头禅、禁用词

### 3. 画像存储层

Android 侧建议新增专门的本地 store，而不是把逻辑散落在 `PrefsManager` 中：

- `ProfileInferenceSettings`
- `OcrTextArchiveStore`
- `ReplyHistoryStore`
- `UserProfileStore`

### 4. 回复生成层

在真正调用 AI 生成回复之前，先把用户画像压缩成一段短 prompt：

- 只保留最稳定、最有用的画像信息
- 只做“轻量偏置”，不能压过当前聊天上下文
- 当画像与当前聊天无关时，不要强行把兴趣点写进回复

### 5. 主 App 管理层

在 Android 主 App 增加一个“个性化回复 / 用户形象”页面，至少支持：

- 开关：是否启用画像驱动回复
- 查看：兴趣标签、常用口头禅、最近更新时间
- 操作：立即重新生成、清空画像、清空采集数据
- 编辑：手动补充兴趣和口头禅

### 6. 隐私与安全层

- 明确让用户知道：会使用 OCR / 回复历史来推断个人风格
- 提供随时关闭、清空、重建能力
- 所有采集都应该有开关和数据保留上限

## 建议画像数据结构

建议后端最终返回结构化 JSON，而不是一大段自然语言，方便 Android 做 UI 展示和 prompt 压缩：

```json
{
  "summary": "用户偏好数码、汽车、游戏话题，常用简洁口语，偶尔会用‘我先看下’、‘回头给你消息’这类表达。",
  "interests": [
    { "name": "计算机", "confidence": 0.86 },
    { "name": "汽车", "confidence": 0.72 },
    { "name": "游戏", "confidence": 0.69 }
  ],
  "catchphrases": [
    { "text": "我先看下", "confidence": 0.78 },
    { "text": "回头给你消息", "confidence": 0.74 }
  ],
  "tonePreferences": {
    "warmth": 0.58,
    "formality": 0.34,
    "emojiUsage": 0.12,
    "verbosity": 0.41
  },
  "avoidRules": [
    "少用过度夸张语气",
    "少用多个感叹号"
  ],
  "manualSeeds": {
    "interests": ["计算机", "汽车"],
    "catchphrases": ["我先看下"],
    "bannedPhrases": []
  },
  "updatedAt": 1760000000
}
```

## Prompt 注入原则

画像不能变成“硬模板”，否则回复会很假。建议统一使用如下原则：

- 当前聊天上下文优先，画像只能轻微影响措辞
- 只在相关时体现兴趣偏好，不要强行提及“计算机/汽车/游戏”
- 口头禅最多轻量出现 0 到 1 次，不要每条都塞
- 如果画像置信度低，只使用 `summary`
- 如果用户手动设置了兴趣/口头禅，手动项优先级高于推断项

建议压缩成类似这种 prompt 片段，再拼到原有回复 prompt 里：

```text
用户画像（仅作轻量表达偏置，不能偏离当前聊天语境）：
- 兴趣偏好：计算机、汽车、游戏
- 常用表达：我先看下、回头给你消息
- 语气倾向：简洁、自然、偏口语、少 emoji
- 避免：少用夸张语气

要求：
- 先保证回复符合当前聊天内容
- 只有在自然相关时才体现兴趣/口头禅
- 不要为了贴画像而改变原本要表达的意思
```

## 任务表

| ID | 优先级 | 模块 | 任务 | 实现要点 | 依赖 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| T01 | P0 | 数据模型 | 定义 Android 画像 JSON schema | 对齐 iOS 的 `profileJson` 存储方式，但把兴趣、口头禅、tone、规则拆成结构化字段 | 无 | Android 能稳定解析、展示、持久化同一份 profile JSON |
| T02 | P0 | 本地存储 | 新增 `ProfileInferenceSettings` / `UserProfileStore` | 仿照 iOS 命名：开关、阈值、运行状态、更新时间、原始 JSON、最后处理计数 | T01 | 本地可读写启用状态、阈值、画像 JSON、更新时间 |
| T03 | P0 | 数据采集 | 新增 `OcrTextArchiveStore` | 归档最近 N 条 OCR / 聊天上下文，支持开关、清空、上限裁剪 | T02 | 能持续存 100-200 条上下文，并支持清空 |
| T04 | P0 | 数据采集 | 新增 `ReplyHistoryStore` | 记录用户最终选中的 AI 回复、来源模式、时间戳、原上下文、是否为扩展回复 | T02 | 选中 AI 回复后，本地能看到对应历史记录 |
| T05 | P0 | 采集接入 | 把 OCR 归档接到截图监听和聊天扫描流程 | 在截图监听、无障碍扫描、B 模式上下文读取成功后统一 append OCR 文本 | T03 | 触发截图/扫描后，本地 OCR 档案数量递增 |
| T06 | P0 | 采集接入 | 把回复历史接到键盘 commit 流程 | 在 `commitSuggestion(...)` 或更早的 AI 选中事件里记录被采用的回复文本 | T04 | 选中一条 B/C 模式回复后，本地会留下一条 reply history |
| T07 | P0 | 后端接口 | Android 侧新增画像推断 API wrapper | 参考 iOS `ai/profile/infer`，但请求体升级为 `observedContextTexts` + `userAuthoredTexts` + `manualSeeds` | T01 | Android 能成功请求画像接口并拿到结构化 JSON |
| T08 | P0 | 调度 | 新增 `ProfileInferenceCoordinator` | 参考 iOS：阈值触发、运行互斥、失败后下次重试、手动重建入口 | T02, T03, T04, T07 | 达到阈值后自动生成画像，且不会并发重复跑 |
| T09 | P0 | 主 App 启动 | 在主 App 启动时尝试触发画像推断 | 参考 iOS `ContentView` 的 `maybeRunFromApp()`；Android 可放在 `MainActivity` 或 `HomeScreen` 初次进入时 | T08 | 打开主 App 时，若满足阈值，能后台触发一次画像推断 |
| T10 | P0 | Prompt 层 | 实现 `UserProfilePromptBuilder` | 把原始 JSON 压缩成短 prompt：兴趣 topN、口头禅 topN、语气倾向、避免规则 | T01, T02 | 能稳定产出短 prompt，长度受控且为空时不注入 |
| T11 | P0 | 回复生成 | 将画像注入 `AiImeCoordinator.requestSuggestions(...)` | 对 B/C 模式根回复生成统一注入 `profileDescriptor`；DeepSeek 与 backend 两条链路都要接 | T10 | 开启画像后，回复文风能轻量体现用户习惯；关闭画像后行为恢复旧逻辑 |
| T12 | P0 | 回复生成 | 将画像注入 B 模式回复树扩展 | 父卡片长按生成子卡片时也带上画像 prompt，但画像权重低于 parentText 语义 | T10 | 子卡片既保持父卡片原意，又能体现用户个人风格 |
| T13 | P0 | 回复生成 | 将画像注入截图智能回复 / 聊天扫描回复 | `ScreenshotAiCoordinator` 和扫描触发链路走同一套 profile prompt 逻辑 | T10 | 截图回复与键盘内回复的个性化风格一致 |
| T14 | P1 | 主 App UI | 新增“用户形象 / 个性化回复”管理页 | 展示 summary、兴趣标签、口头禅、更新时间、启用开关 | T02, T08 | 用户能在主 App 中看到当前画像摘要 |
| T15 | P1 | 主 App UI | 支持手动补充兴趣标签和口头禅 | 手动项单独存储，推断时传给后端，prompt 注入时优先级高于自动推断 | T14 | 用户手动添加“计算机/汽车/我先看下”后，后续回复会轻量体现 |
| T16 | P1 | 主 App UI | 支持“立即重建 / 清空画像 / 清空采集数据” | 便于测试和迭代，不需要每次卸载重装 | T14 | 页面上可一键重建和清空，操作后状态正确刷新 |
| T17 | P1 | 调试能力 | 增加调试视图或隐藏开发页 | 至少能查看 raw JSON、OCR archive 数量、reply history 数量、最近更新时间 | T03, T04, T08 | 开发和测试可定位画像来源与结果 |
| T18 | P1 | 隐私控制 | 增加采集说明和独立开关 | OCR 归档、回复历史归档、画像驱动回复三者应可独立关闭 | T03, T04, T14 | 用户关闭任一开关后，相关数据不再增长 |
| T19 | P2 | 质量优化 | 引入字段级置信度和衰减机制 | 兴趣和口头禅要能随时间更新，降低旧偏好的影响 | T01, T08 | 长期使用后画像能渐进更新，而不是永久固化 |
| T20 | P2 | 评估 | 建立个性化回复回归样例集 | 至少覆盖“计算机/汽车/游戏兴趣”和“口头禅/简洁/礼貌”等样例 | T11, T12, T13 | 能人工回归判断个性化是否有效且不过拟合 |

## 推荐迭代顺序

### 第一阶段：先把“画像基础设施”补起来

- T01 ~ T09

产出：

- Android 能采集 OCR 和 reply history
- Android 能自动 / 手动触发画像推断
- Android 能把画像 JSON 存下来

### 第二阶段：把画像真正接进回复生成

- T10 ~ T13

产出：

- B/C 模式、截图回复、长按扩展都能使用用户画像
- 回复风格能轻量体现用户偏好和口头禅

### 第三阶段：把能力暴露给用户和测试

- T14 ~ T18

产出：

- 主 App 内有可用的“用户形象”页面
- 用户可查看、编辑、清空、重建画像
- 测试能定位画像是否生效

### 第四阶段：长期质量优化

- T19 ~ T20

产出：

- 画像不会越用越脏
- 有稳定回归方法，避免个性化过拟合

## Android 侧建议落点文件

建议优先在这些文件和模块附近扩展：

- AI 生成入口：
  - `app/src/main/java/com/arche/threply/ime/ai/AiImeCoordinator.kt`
  - `app/src/main/java/com/arche/threply/screenshot/ScreenshotAiCoordinator.kt`
- AI 数据层：
  - `app/src/main/java/com/arche/threply/data/BackendAiApi.kt`
  - `app/src/main/java/com/arche/threply/data/DeepSeekDirectApi.kt`
- 本地存储：
  - `app/src/main/java/com/arche/threply/data/PrefsManager.kt`
  - 建议新增 `profile/` 或 `data/profile/` 子模块承载 store
- 主 App UI：
  - `app/src/main/java/com/arche/threply/ui/profile/ProfileScreen.kt`
  - 后续如果补开发者页，可新增 `ui/profile/ProfilePersonaScreen.kt`
- 键盘选中记录：
  - `app/src/main/java/com/arche/threply/ime/ThreplyInputMethodService.kt`

## 验收口径

功能完成后，至少要满足以下验收：

1. 开启画像功能后，AI 回复能轻量体现用户兴趣和口头禅，但不会偏离当前聊天语境。
2. 关闭画像功能后，回复结果应回退到当前未个性化逻辑。
3. 同一用户在 B 模式、截图回复、聊天扫描回复中的风格应一致。
4. 用户可以在主 App 中看到当前画像、更新时间，并能手动清空或重建。
5. 口头禅推断主要来自“用户采用过的回复”，而不是错误地从对方消息里学习。

## 一句话总结

参考 iOS，Android 最应该先复用的是“采集 + 推断 + 存储 + 调试”的框架；但为了实现真正的“定制化 AI 回复”，Android 还必须额外补上“回复历史采集”和“画像 prompt 注入”两段关键闭环。
