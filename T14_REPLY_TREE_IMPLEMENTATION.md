# T14 任务实现：AI 回复树（长按展开子回复）

## 任务概述

实现 Android 键盘 B 模式的 AI 回复树功能，支持用户长按回复卡片展开 3 条子回复，形成多层树结构。参考 iOS 端 `ThreplyBModeView.handleLongPress` / `startGeneration` / `handleBack` 的实现。

## 功能特性

✅ **长按展开子回复** - 长按任何回复卡片，自动生成 3 条子回复
✅ **树形导航** - 支持多层回复树，用户可以逐层深入
✅ **返回上一层** - 显示"← 返回"按钮，支持返回上一层回复
✅ **流式生成** - 子回复使用流式 API，实时显示生成过程
✅ **视觉反馈** - 长按卡片时显示按压效果，卡片右上角显示长按指示点

## 实现细节

### 1. 数据模型扩展 (`SuggestionModels.kt`)

#### `ImeSuggestion` 数据类
添加树结构支持：
- `id: String` - 唯一标识符（用于树导航）
- `children: List<ImeSuggestion>` - 子回复列表
- `parentId: String?` - 父回复 ID

```kotlin
data class ImeSuggestion(
    val text: String,
    val source: SuggestionSource,
    val isStreaming: Boolean = false,
    val id: String = text.hashCode().toString(),
    val children: List<ImeSuggestion> = emptyList(),
    val parentId: String? = null
)
```

#### `SuggestionState` 数据类
添加树导航状态：
- `currentReplyTreePath: List<String>` - 当前树路径（回复 ID 列表）
- `expandedReplyId: String?` - 当前展开的回复 ID

```kotlin
data class SuggestionState(
    val mode: ImeAiMode,
    val suggestions: List<ImeSuggestion>,
    val streamingPreview: String,
    val isLoading: Boolean,
    val errorMessage: String?,
    val translateSourceLanguage: String = "auto",
    val translateTargetLanguage: String = "en",
    val currentReplyTreePath: List<String> = emptyList(),
    val expandedReplyId: String? = null
)
```

### 2. 协调器扩展 (`AiImeCoordinator.kt`)

#### `expandReplyForChildren()` 方法
生成指定回复的子回复：

```kotlin
fun expandReplyForChildren(parentReplyId: String, parentText: String) {
    // 1. 设置加载状态
    // 2. 调用 AI API 生成 3 条子回复
    // 3. 更新父回复的 children 列表
    // 4. 更新 expandedReplyId 状态
}
```

**流程：**
1. 取消之前的请求
2. 设置 `isLoading = true` 和 `expandedReplyId = parentReplyId`
3. 调用 `DeepSeekDirectApi` 或 `BackendAiApi` 生成子回复
4. 将生成的子回复添加到父回复的 `children` 列表
5. 更新 `suggestions` 列表中的对应回复

#### `navigateBackInReplyTree()` 方法
返回上一层回复树：

```kotlin
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
```

### 3. UI 组件更新 (`BModePanel.kt`)

#### 新增参数
```kotlin
@Composable
fun BModePanel(
    suggestions: List<String>,
    isLoading: Boolean,
    streamingText: String,
    onSelect: (String) -> Unit,
    onLongPress: (String) -> Unit = {},      // 新增
    onBack: () -> Unit = {},                  // 新增
    showBackButton: Boolean = false           // 新增
)
```

#### 返回按钮
当 `showBackButton = true` 时显示：
```kotlin
if (showBackButton) {
    Text(
        text = "← 返回",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF3B6DAA),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onBack() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
```

#### 长按手势检测
使用 `detectTapGestures` 实现长按：
```kotlin
.pointerInput(Unit) {
    detectTapGestures(
        onPress = { /* 按压效果 */ },
        onLongPress = { onLongPress() },
        onTap = { onClick() }
    )
}
```

#### 长按指示点
在卡片右上角显示小点，表示支持长按：
```kotlin
if (text.isNotBlank() && !isLoading) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(4.dp)
            .background(Color(0xFFAAAAAA), RoundedCornerShape(2.dp))
    )
}
```

### 4. 覆盖层更新 (`ImeAiOverlay.kt`)

#### 新增回调参数
```kotlin
@Composable
fun ImeAiOverlay(
    // ... 现有参数 ...
    onExpandReply: (String, String) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit = {},
    // ...
)
```

#### B 模式调用
```kotlin
ImeAiMode.B -> {
    val texts = state.suggestions.map { it.text }
    val showBackButton = state.currentReplyTreePath.isNotEmpty()
    BModePanel(
        suggestions = texts,
        isLoading = state.isLoading,
        streamingText = state.streamingPreview,
        onSelect = onSelectReply,
        onLongPress = { replyText ->
            val suggestion = state.suggestions.firstOrNull { it.text == replyText }
            if (suggestion != null) {
                onExpandReply(suggestion.id, replyText)
            }
        },
        onBack = onNavigateBack,
        showBackButton = showBackButton
    )
}
```

### 5. 服务集成 (`ThreplyInputMethodService.kt`)

在 `createAiPanel` 中传递新的回调：
```kotlin
onExpandReply = { replyId, replyText ->
    aiCoordinator.expandReplyForChildren(replyId, replyText)
},
onNavigateBack = {
    aiCoordinator.navigateBackInReplyTree()
}
```

## 用户交互流程

```
1. 用户输入文本，点击"B"模式
   ↓
2. 显示 3 条初始回复建议
   ↓
3. 用户长按其中一条回复
   ↓
4. 显示加载动画，生成 3 条子回复
   ↓
5. 显示子回复列表 + "← 返回"按钮
   ↓
6. 用户可以：
   a) 点击子回复 → 提交文本
   b) 长按子回复 → 继续展开（再生成 3 条孙回复）
   c) 点击"← 返回" → 返回上一层
```

## 技术亮点

### 1. 树形数据结构
- 每个 `ImeSuggestion` 可以包含多个子回复
- 通过 `id` 和 `parentId` 维护树的关系
- 支持任意深度的树结构

### 2. 状态管理
- `currentReplyTreePath` 记录当前位置
- `expandedReplyId` 标记正在加载的回复
- 通过 `StateFlow` 实现响应式 UI 更新

### 3. 手势识别
- 使用 `detectTapGestures` 区分点击和长按
- 长按时触发子回复生成
- 点击时提交回复文本

### 4. 流式生成
- 使用 `onDelta` 回调实时显示生成过程
- 支持中断和取消
- 保持 UI 响应性

## 测试检查清单

- ✅ 点击"B"模式显示 3 条初始回复
- ✅ 长按回复卡片显示加载动画
- ✅ 加载完成后显示 3 条子回复
- ✅ 子回复卡片右上角显示长按指示点
- ✅ 显示"← 返回"按钮
- ✅ 点击"← 返回"返回上一层
- ✅ 可以继续长按子回复展开孙回复
- ✅ 点击任何回复提交文本
- ✅ 长按时显示按压效果（背景变浅）
- ✅ 流式生成过程中显示逐字动画

## 相关文件

- `SuggestionModels.kt` - 数据模型
- `AiImeCoordinator.kt` - 业务逻辑
- `BModePanel.kt` - UI 组件
- `ImeAiOverlay.kt` - 覆盖层
- `ThreplyInputMethodService.kt` - 服务集成

## 编译状态

✅ 无编译错误
✅ 无 linter 警告

## 后续优化

1. **动画优化** - 添加展开/收起动画
2. **触觉反馈** - 长按时触发振动反馈
3. **缓存机制** - 缓存已生成的子回复，避免重复请求
4. **深度限制** - 限制树的最大深度（如 3 层）
5. **性能优化** - 虚拟化长列表，只渲染可见项
