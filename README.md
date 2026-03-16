# Threply Android

本仓库是 `Threply` 的 Android 版本，包含主 App、Android 自定义输入法、AI 面板、聊天扫描链路和截图智能回复能力。

本 README 基于 `main` 分支在 2026-03-16 的源码状态整理，描述以当前 Android 实现为准，不默认对齐 iOS。

## 当前状态

### 已实现的键盘能力

- 自定义输入法服务 `ThreplyInputMethodService`
- 英文输入、拼音输入、符号模式三条基础输入路径
- Shift 大小写切换、退格长按连删、动态回车键标签
- 中英切换，切换到英文时会清空拼音 composing 状态
- 拼音输入通过 `setComposingText()` 写入宿主输入框，并在键盘上方显示 composing bar
- 中文模式下内联标点和符号页会映射为全角中文标点
- 中文模式支持双击空格自动插入 `。`
- 候选栏横向滚动、首项高亮、`/` 技能入口、`⊞` 展开候选面板
- 展开候选面板为 4 列网格，并带“返回 / 删除 / 收起”侧边操作
- 选中文本时自动展开技能栏，取消选择后自动收起
- 技能栏支持 `翻译 / 替换 / 润色`
- `替换 / 润色` 提交后支持一步撤销

### 已实现的 AI 键盘能力

- 底栏 `AI` 按钮可切换 AI Overlay
- Overlay 提供 `B / C / 翻译` 三种模式
- B 模式会生成 3 条回复建议，并显示流式预览
- B 模式支持长按回复卡片展开子回复树，并支持返回上一层
- C 模式提供 2D 风格面板，调节回复长度与语气温度
- 翻译模式支持源语言 / 目标语言选择，并将结果作为候选建议返回
- AI 请求支持两条链路：
  - 配置 DeepSeek API Key 后直连 DeepSeek
  - 未配置时走后端 API，并带 token 刷新逻辑
- 主 App 可通过 `SharedTriggerStore` 向键盘推送草稿，切到键盘后直接触发 AI 建议

### 已实现的聊天上下文与截图能力

- 键盘 B 模式触发 AI 时，会优先尝试读取聊天上下文，再回退到输入框文本
- `ChatScanAccessibilityService` 会先扫描无障碍节点树，再在必要时降级到截图 + OCR
- 聊天上下文会按左右位置区分 `对方` / `我`，再交给 AI 生成回复
- 已内置常见聊天 App 包名识别，覆盖微信、QQ、WhatsApp、Telegram、Discord、Slack 等
- AI 面板中的 `扫描` 按钮可主动发起聊天扫描
- `ScreenshotMonitorService` 支持监听截图，在主 App 开启后自动 OCR 并生成回复建议通知
- 截图 OCR 使用 ML Kit 中文文本识别

### 主 App 已实现能力

- 单 Activity Compose App，包含 onboarding、主页、登录、个人中心、Paywall
- 键盘启用状态检测与输入法切换入口
- Rime 方案切换：`rime_ice`、`luna_pinyin`、`double_pinyin`
- DeepSeek API Key 配置页
- 后端 Base URL、触感反馈强度、语言偏好、惯用手设置
- Google Play Billing 会员页
- `threply://paywall` Deep Link 可直接拉起会员页

## 当前限制

- 键盘侧 AI 权限校验仍处于测试绕过状态：`isAiAccessAllowed()` 当前直接返回 `true`
- 主 App 已有登录、会员和 Paywall，但 IME 内尚未真正拦截未登录 / 非 Pro 用户
- Native Rime 仍依赖外部资源：
  - 需要 `app/src/main/jniLibs/<ABI>/libRime.so`
  - 需要 `rime_api.h`
  - 需要构建时从 `../threply/threply/ThreplyKeyboard/RimeResources` 同步 `rime/shared`
- 若上述 Native Rime 资源缺失，项目仍可编译，但 JNI 会退回 stub，中文输入主要依赖 fallback 词库
- `Profile` 里有“连按空格自动句号”开关，但当前 IME 路径仍会在中文模式直接处理双空格句号
- Caps Lock、字母长按候选字符、键盘日志查看、回复历史记录等能力还未补齐
- 触感反馈已接到按键、AI 面板点击和 B 模式卡片，但 C 模式拖拽等高级触感尚未完成
- 聊天扫描的截图路径需要 Android 11 及以上；截图监听在 Android 13 及以上还需要通知权限

## 构建环境

- Android Studio 稳定版
- JDK 17
- Android SDK 34
- `minSdk = 26`
- Kotlin 1.9.24
- Android Gradle Plugin 8.4.2

## 构建与运行

### 1. 构建 APK

在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

### 2. Rime 资源同步

构建前会执行 `syncRimeAssets`，默认从以下目录复制资源：

```text
../threply/threply/ThreplyKeyboard/RimeResources
```

如果该目录不存在，APK 仍能编译，但 Native Rime 不会处于完整可用状态。

### 3. 启用键盘

1. 安装 APK
2. 打开主 App 完成 onboarding
3. 进入系统设置，启用 `Threply`
4. 在输入法选择器中切换到 `Threply`

### 4. 启用 AI 与扫描能力

- 若要直连 DeepSeek，在主 App 中填写 API Key
- 若要走后端 AI，需要先完成登录并配置可用后端地址
- 若要让键盘读取聊天上下文，需要开启无障碍服务
- 若要开启截图智能回复，需要授予媒体读取权限；Android 13+ 还需要通知权限

### 5. Google 登录配置

如果需要使用 Google 登录，需要先在 [app/build.gradle.kts](/c:/Users/12686/Desktop/threply-android/app/build.gradle.kts) 中配置 `GOOGLE_WEB_CLIENT_ID`。

## 目录结构

- `app/src/main/java/com/arche/threply/ime/`
  输入法核心逻辑、候选栏、技能栏、键盘布局
- `app/src/main/java/com/arche/threply/ime/compose/`
  AI Overlay、B 模式、C 模式、翻译面板
- `app/src/main/java/com/arche/threply/ime/ai/`
  AI 状态管理、流式请求、回复树逻辑
- `app/src/main/java/com/arche/threply/ime/context/`
  聊天上下文桥接
- `app/src/main/java/com/arche/threply/ime/rime/`
  Rime 资源准备、Native Bridge、fallback 词库
- `app/src/main/java/com/arche/threply/screenshot/`
  无障碍聊天扫描、截图监听、OCR、通知投递
- `app/src/main/java/com/arche/threply/ui/`
  主 App 页面、登录、个人中心、Paywall、onboarding

## 相关文档

- [KEYBOARD_TODO_PRIORITY.md](KEYBOARD_TODO_PRIORITY.md)
- [KEYBOARD_DEBUG_GUIDE.md](KEYBOARD_DEBUG_GUIDE.md)
- [KEYBOARD_FEATURE_COMPARISON.md](KEYBOARD_FEATURE_COMPARISON.md)
- [B_MODE_USAGE_GUIDE.md](B_MODE_USAGE_GUIDE.md)
- [B_MODE_QUICK_START.md](B_MODE_QUICK_START.md)
- [TRANSLATE_FEATURE_GUIDE.md](TRANSLATE_FEATURE_GUIDE.md)
- [TRANSLATE_QUICK_START.md](TRANSLATE_QUICK_START.md)
- [T14_REPLY_TREE_IMPLEMENTATION.md](T14_REPLY_TREE_IMPLEMENTATION.md)
