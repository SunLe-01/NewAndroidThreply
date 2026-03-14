# Threply Android

Threply Android 是一款集成 AI 功能的中文/英文输入法项目，包含自定义软键盘、拼音候选、Rime 引擎（可选）以及基于网络的 AI 建议与文本处理能力。

## 功能概览

- **自定义软键盘**：支持英文与符号输入、Shift 大小写、退格、回车等操作。
- **中英文切换**：内置拼音输入与候选展示。
- **Rime 引擎集成（可选）**：可使用原生 Rime 引擎，失败时自动回退到内置词库。
- **AI 建议面板**：支持生成候选回复、改写/润色等模式。
- **聊天内容扫描（辅助）**：配合辅助功能服务获取聊天内容并生成建议。

## 快速开始

### 1. 构建项目

在项目根目录执行：

```bash
gradlew clean
gradlew assembleDebug
```

### 2. 安装与运行

1. 使用 Android Studio 运行或通过 `adb install` 安装 APK。
2. 打开系统设置 → 语言和输入法 → 虚拟键盘。
3. 启用 **Threply** 键盘。
4. 在输入框中切换到 **Threply** 键盘进行测试。

### 3. 常见调试

请参考 [KEYBOARD_DEBUG_GUIDE.md](KEYBOARD_DEBUG_GUIDE.md)。

## 项目结构

- `app/src/main/java/com/arche/threply/ime/`：输入法核心逻辑
- `app/src/main/java/com/arche/threply/ime/compose/`：Compose AI 面板
- `app/src/main/java/com/arche/threply/ime/rime/`：Rime 引擎与资源管理
- `app/src/main/java/com/arche/threply/screenshot/`：聊天扫描与 OCR 相关功能

## 未来计划（Todo）

- [ ] 完整的 Rime 资源打包与自动更新机制
- [ ] AI 能力配置化（模型选择/开关/权限控制）
- [ ] 更完善的候选词 UI 与滚动体验
- [ ] 用户词库与学习能力
- [ ] 端到端自动化测试与稳定性优化
- [ ] 发布流程与版本管理（CI/CD）

## 许可与说明

当前仓库为开发版本，部分功能依赖外部服务与设备权限，建议在测试设备上使用。