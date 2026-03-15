# 翻译语言选择 UI 显示问题修复

## 问题描述

实现了 T13 任务（翻译语言选择 UI）后，点击"翻译"按钮时，AI 面板显示但**语言选择 UI 没有出现**。

## 根本原因

在 `ThreplyInputMethodService.kt` 的 `createAiPanel` 方法中，AI 面板被包装在一个初始状态为 `View.GONE` 的 `LinearLayout` 中：

```kotlin
LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    visibility = View.GONE  // ← 问题所在
    addView(composeView)
}
```

这导致：
1. AI 面板在初始化时被隐藏
2. 当调用 `setAiMode(ImeAiMode.TRANSLATE)` 时，虽然面板被设置为 `VISIBLE`，但 Compose 内容可能没有正确重新组合
3. `TranslatePanel` 组件的语言选择 UI 无法正确渲染

## 解决方案

### 修改 1：添加包装容器变量
在 `ThreplyInputMethodService.kt` 中添加 `aiPanelWrapper` 变量：

```kotlin
private lateinit var aiPanelWrapper: LinearLayout
```

### 修改 2：创建独立的包装容器
在 `createInputViewContent` 方法中，为 AI 面板创建一个独立的包装容器：

```kotlin
val fallbackPanel = createFallbackPanel()
keyboardRoot.addView(fallbackPanel)
aiPanelView = createAiPanel(fallbackPanel)
aiPanelWrapper = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    visibility = View.GONE
    addView(aiPanelView)
}
keyboardRoot.addView(aiPanelWrapper)
```

### 修改 3：简化 `createAiPanel` 方法
移除 `createAiPanel` 中的外层 `LinearLayout` 包装，直接返回 `ComposeView`：

```kotlin
private fun createAiPanel(fallbackPanel: View): View {
    return runCatching {
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by aiCoordinator.state.collectAsState()
                ImeAiOverlay(
                    state = state,
                    // ... 其他参数
                )
            }
        }
    }.getOrElse {
        // 错误处理
        fallbackPanel
    }
}
```

### 修改 4：更新 `setAiMode` 方法
使用 `aiPanelWrapper` 而不是 `aiPanelView` 来控制可见性：

```kotlin
private fun setAiMode(mode: ImeAiMode) {
    if (!isAiAccessAllowed()) return
    aiMode = mode
    aiCoordinator.setMode(mode)
    // Show AI panel if it was hidden
    if (::aiPanelWrapper.isInitialized) {
        aiPanelWrapper.visibility = View.VISIBLE
    }
}
```

### 修改 5：更新 `exitAiPanel` 方法
使用 `aiPanelWrapper` 来隐藏面板：

```kotlin
private fun exitAiPanel() {
    // Hide AI panel and just show keyboard
    if (::aiPanelWrapper.isInitialized) {
        aiPanelWrapper.visibility = View.GONE
    }
}
```

## 为什么这样修复有效

1. **分离关注点**：将 AI 面板的可见性控制与 Compose 内容分离
2. **确保 Compose 重新组合**：`ComposeView` 始终处于活跃状态，当 `aiPanelWrapper` 变为 `VISIBLE` 时，Compose 内容能正确重新组合
3. **保持状态一致性**：`TranslatePanel` 中的语言选择状态通过 `AiImeCoordinator` 的 `StateFlow` 管理，确保 UI 与状态同步

## 测试步骤

1. 打开键盘
2. 点击"翻译"按钮（或在 ActionBar 中选择翻译模式）
3. 验证以下内容显示：
   - ✅ 源语言选择器（显示"自动检测"）
   - ✅ 目标语言选择器（显示"英文"）
   - ✅ 交换按钮（⇄）
   - ✅ 翻译按钮
4. 点击源语言选择器，验证语言列表下拉菜单显示
5. 选择不同的语言，验证选择生效
6. 输入文本并点击翻译按钮，验证翻译功能正常工作

## 相关文件

- `ThreplyInputMethodService.kt` - 主键盘服务类
- `ImeAiOverlay.kt` - AI 面板 Compose 组件
- `TranslatePanel.kt` - 翻译面板 Compose 组件
- `AiImeCoordinator.kt` - AI 协调器，管理状态

## 编译状态

✅ 无编译错误
✅ 无 linter 警告
