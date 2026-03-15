# Threply Android

Threply Android 是一个包含主 App 与自定义输入法服务的 Android 项目。当前版本已经具备英文键盘、拼音输入、候选词栏、AI 回复/风格/翻译面板、文本技能栏以及聊天扫描入口，但与 iOS 端相比仍有一部分底层能力和交互细节待补齐。

## 当前已实现

- 自定义输入法服务 `ThreplyInputMethodService`
- 英文 / 拼音 / 符号三类输入路径
- Shift 大小写、退格长按连删、动态回车标签、中英切换
- 拼音合成文本写入宿主输入框 `setComposingText()`
- 横向候选词栏 + 候选展开面板 + 首项高亮
- fallback 拼音词库（当前约 483 个拼音条目）
- 可选 Native Rime 桥接、Schema 配置、Rime 资源同步任务
- 底栏 `AI` 按钮打开 AI Overlay
- B 模式：3 条 AI 回复建议、流式预览、点击直接插入
- C 模式：2D 拖拽风格板，调节回复长度与语气温度
- 翻译模式：源语言 / 目标语言选择与翻译请求
- 候选栏 `/` 技能栏：翻译、替换、润色
- 选中文本时自动弹出技能栏
- Replace / Polish 一步撤销
- 聊天扫描入口，联动辅助功能服务读取聊天内容
- 主 App 内置 Billing / Paywall 页面与 `threply://paywall` Deep Link

## 当前已知限制

- Native Rime 仍未形成完整可交付链路：当前 CMake 会在缺少 `libRime.so` 时退回 stub 版 JNI，稳定可用路径仍主要依赖 fallback 词库。
- B 模式回复树尚未真正闭环：长按扩展相关代码已接入，但 Android 端还没完全达到 iOS 的多层回复树体验。
- IME 内 Pro 门控还没接入：键盘侧 AI 权限目前仍是放开状态。
- 中文标点全角映射、双空格句号、Caps Lock、长按次选字符仍未完成。
- `HapticsUtil` 已存在，但键盘主路径尚未全面接通触觉反馈。

当前优先级任务见 [KEYBOARD_TODO_PRIORITY.md](KEYBOARD_TODO_PRIORITY.md)。

## 主要文档

- [B_MODE_USAGE_GUIDE.md](B_MODE_USAGE_GUIDE.md)
- [B_MODE_QUICK_START.md](B_MODE_QUICK_START.md)
- [KEYBOARD_DEBUG_GUIDE.md](KEYBOARD_DEBUG_GUIDE.md)
- [KEYBOARD_FEATURE_COMPARISON.md](KEYBOARD_FEATURE_COMPARISON.md)
- [KEYBOARD_TODO_PRIORITY.md](KEYBOARD_TODO_PRIORITY.md)
- [TRANSLATE_FEATURE_GUIDE.md](TRANSLATE_FEATURE_GUIDE.md)
- [TRANSLATE_QUICK_START.md](TRANSLATE_QUICK_START.md)
- [T14_REPLY_TREE_IMPLEMENTATION.md](T14_REPLY_TREE_IMPLEMENTATION.md)

## 构建环境

- Android Studio 最新稳定版
- JDK 17
- Android SDK 34
- `minSdk = 26`

## 构建说明

### 1. 构建 APK

在项目根目录执行：

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

### 2. Rime 资源同步说明

构建前会执行 `syncRimeAssets`，默认从相邻目录读取：

```text
../threply/threply/ThreplyKeyboard/RimeResources
```

也就是当前 Android 仓库默认会复用 iOS 项目的 Rime 资源文件。  
如果该目录不存在，项目仍可编译，但 Native Rime 能力不会完整可用，实际输入会主要依赖 fallback 词库。

### 3. 安装与启用输入法

1. 安装 APK
2. 打开主 App 完成基础配置
3. 进入系统设置 -> 语言和输入法 -> 虚拟键盘
4. 启用 `Threply`
5. 在任意输入框中切换到 `Threply` 键盘

## AI 功能启用说明

AI 能力当前依赖以下任一条件：

- 在主 App 中配置 DeepSeek API Key
- 或接通后端登录 / 会话能力

如果未配置，键盘中的 AI 请求通常不会返回有效结果。

## 聊天扫描说明

聊天扫描依赖以下组件：

- `ChatScanAccessibilityService`
- `ScreenshotMonitorService`

如果需要从聊天界面读取上下文并生成建议，还需要在系统设置中手动启用对应辅助功能服务。

## 项目结构

- `app/src/main/java/com/arche/threply/ime/`
  输入法核心逻辑、键盘布局、候选栏、技能栏
- `app/src/main/java/com/arche/threply/ime/compose/`
  AI Overlay、B/C 模式、翻译面板
- `app/src/main/java/com/arche/threply/ime/rime/`
  Rime 桥接、fallback 词库、资源准备逻辑
- `app/src/main/java/com/arche/threply/screenshot/`
  聊天扫描、截图监听、OCR 相关能力
- `app/src/main/java/com/arche/threply/billing/`
  Google Play Billing
- `app/src/main/java/com/arche/threply/ui/`
  主 App 页面、配置页、Paywall

## 调试建议

- 键盘异常优先看 [KEYBOARD_DEBUG_GUIDE.md](KEYBOARD_DEBUG_GUIDE.md)
- AI 面板与 B 模式行为优先看 [B_MODE_USAGE_GUIDE.md](B_MODE_USAGE_GUIDE.md)
- 当前 Android / iOS 能力差距参考 [KEYBOARD_FEATURE_COMPARISON.md](KEYBOARD_FEATURE_COMPARISON.md)

## 说明

当前仓库是开发中版本。README 以当前 Android 源码状态为准，不以 iOS 端功能为默认完成标准。

