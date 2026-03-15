# 翻译功能快速开始

## 5 分钟快速上手

### 1. 编译和安装
```bash
# 确保项目已同步
./gradlew build

# 安装到设备
./gradlew installDebug
```

### 2. 打开翻译功能
1. 在任何输入框中输入文本
2. 点击候选词栏左侧的 **"/"** 按钮
3. 在 AI 面板顶部点击 **"翻译"** 按钮

### 3. 选择语言
- **源语言：** 点击下拉菜单选择（支持自动检测）
- **目标语言：** 点击下拉菜单选择
- **交换：** 点击 **"⇄"** 快速交换

### 4. 执行翻译
1. 点击 **"翻译"** 按钮
2. 等待翻译完成
3. 结果显示在下方预览区

### 5. 提交结果
- 点击翻译结果将其插入到输入框
- 或继续修改语言后重新翻译

## 支持的语言

| 代码 | 语言 |
|-----|------|
| auto | 自动检测 |
| zh | 中文 |
| en | 英文 |
| ja | 日文 |
| ko | 韩文 |
| es | 西班牙文 |
| fr | 法文 |
| de | 德文 |

## 配置 DeepSeek API（可选）

为了获得更好的翻译质量，建议配置 DeepSeek API：

1. 获取 API Key：https://platform.deepseek.com
2. 打开 Threply 主应用
3. 进入设置 → 开发者选项
4. 粘贴 API Key
5. 保存

## 常见问题

**Q: 翻译失败？**
- 检查网络连接
- 确保已登录或配置 API Key
- 查看错误信息提示

**Q: 看不到翻译按钮？**
- 确保点击了 "/" 按钮打开 AI 面板
- 检查键盘是否已更新

**Q: 翻译结果不准确？**
- 尝试明确指定源语言（而不是自动检测）
- 检查输入文本是否正确

## 文件位置

- **UI 组件：** `TranslatePanel.kt`
- **状态管理：** `AiImeCoordinator.kt`
- **集成点：** `ThreplyInputMethodService.kt`
- **数据模型：** `SuggestionModels.kt`

## 调试

查看日志：
```bash
adb logcat | grep "AiImeCoordinator\|TranslatePanel"
```

## 下一步

- 查看 `TRANSLATE_FEATURE_GUIDE.md` 了解完整功能
- 查看 `TRANSLATE_UI_INTEGRATION.md` 了解技术细节
- 查看 `TRANSLATE_UI_IMPLEMENTATION_SUMMARY.md` 了解实现总结
