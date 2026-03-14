# 键盘崩溃问题修复指南

## 已修复的问题

### 1. Native Library 加载失败
**问题**: `librime_jni.so` 因为 16 KB 对齐问题无法加载，导致崩溃
**修复**: 
- 在 CMakeLists.txt 中添加了 16 KB 对齐标志
- 在 RimeNativeBridge 中添加了详细的加载日志和异常处理
- 即使 native library 加载失败，键盘也能正常工作（使用 fallback 模式）

### 2. 资源管理器崩溃
**问题**: RimeResourceManager 在应用启动时尝试访问不存在的资源导致崩溃
**修复**:
- 在所有资源操作中添加了异常处理
- 资源不存在时返回 null 而不是抛出异常
- 在后台线程中异步加载资源，不阻塞主线程

### 3. 应用启动崩溃
**问题**: ThreplyApp.onCreate() 中的初始化失败导致整个应用崩溃
**修复**:
- 所有初始化操作都包装在 runCatching 中
- 单个功能失败不会影响应用启动
- 添加了详细的错误日志

### 4. 键盘服务崩溃
**问题**: InputMethodService 生命周期管理不当导致崩溃
**修复**:
- 在所有关键方法中添加了异常处理
- Compose 生命周期管理更加健壮
- 提供了紧急键盘作为 fallback

## 如何调试键盘问题

### 1. 查看 Logcat 日志

在 Android Studio 中打开 Logcat，过滤以下标签：

```
ThreplyApp
ThreplyIME
RimeResourceManager
RimeNativeBridge
RimeEngineController
ImeLifecycleOwner
```

或者使用命令行：

```bash
adb logcat | grep -E "ThreplyApp|ThreplyIME|Rime"
```

### 2. 检查关键日志

**应用启动时应该看到：**
```
I/ThreplyApp: Application started
I/RimeResourceManager: Warming up Rime resources in background
```

**键盘启动时应该看到：**
```
I/ThreplyIME: Creating input view
I/RimeNativeBridge: Successfully loaded librime_jni.so
或
W/RimeNativeBridge: Failed to load librime_jni.so: ...
I/RimeEngineController: Initialized: schema=luna_pinyin, nativeEnabled=true/false
```

**如果看到错误：**
```
E/ThreplyIME: Failed to create input view, using emergency keyboard
```
这表示主键盘创建失败，但紧急键盘应该能工作。

### 3. 测试步骤

#### 步骤 1: 清理并重新构建
```bash
# 在项目根目录执行
gradlew clean
gradlew assembleDebug
```

#### 步骤 2: 安装并启动应用
1. 在 Android Studio 中点击 Run
2. 等待应用安装完成
3. 检查 Logcat 中是否有错误

#### 步骤 3: 启用键盘
1. 打开设置 → 系统 → 语言和输入法 → 虚拟键盘
2. 点击"管理键盘"
3. 启用 "Threply" 键盘
4. 检查 Logcat 是否有错误

#### 步骤 4: 选择键盘
1. 打开任意应用（如备忘录）
2. 点击文本输入框
3. 长按空格键或点击键盘切换按钮
4. 选择 "Threply" 键盘
5. 检查键盘是否显示

#### 步骤 5: 测试输入
1. 尝试输入英文字母
2. 尝试切换到中文输入
3. 尝试输入拼音
4. 检查是否有候选词显示

### 4. 常见问题排查

#### 问题: 应用启动就崩溃
**检查**:
- Logcat 中查找 "ThreplyApp" 相关错误
- 检查是否是权限问题
- 尝试禁用 Rime 功能：在 PrefsManager 中设置 `isImeRimeEnabled` 为 false

#### 问题: 键盘无法显示
**检查**:
- 确认键盘已在设置中启用
- 确认键盘已被选择为当前输入法
- 查看 Logcat 中 "ThreplyIME" 相关日志
- 检查 `onCreateInputView()` 是否被调用

#### 问题: 键盘显示但无法输入
**检查**:
- 点击按键时是否有反应
- Logcat 中是否有异常
- 尝试切换到符号模式测试

#### 问题: Native library 加载失败
**这是正常的！** 如果看到：
```
W/RimeNativeBridge: Failed to load librime_jni.so
```
键盘仍然可以工作，只是使用 fallback 模式（不使用 native Rime 引擎）。

**如果需要 native Rime**:
1. 确认 `librime_jni.so` 已编译
2. 确认 16 KB 对齐标志已添加
3. 检查 `app/build/intermediates/cmake/debug/obj/` 目录下是否有 `.so` 文件
4. 重新构建项目

### 5. 紧急修复

如果键盘完全无法使用，可以临时禁用某些功能：

#### 禁用 Native Rime
在 `PrefsManager.kt` 中修改默认值：
```kotlin
fun isImeRimeNativeEnabled(context: Context): Boolean =
    prefs(context).getBoolean("com.arche.threply.ime.rime.nativeEnabled", false) // 改为 false
```

#### 禁用 AI 功能
在 `PrefsManager.kt` 中修改默认值：
```kotlin
fun isImeAiEnabled(context: Context): Boolean =
    prefs(context).getBoolean("com.arche.threply.ime.ai.enabled", false) // 改为 false
```

#### 使用紧急键盘
如果主键盘创建失败，系统会自动使用紧急键盘（只有基本的英文输入功能）。

## 验证修复

### 成功的标志：
1. ✅ 应用启动不崩溃
2. ✅ 可以在设置中启用键盘
3. ✅ 可以选择键盘作为输入法
4. ✅ 键盘可以显示
5. ✅ 可以输入英文字母
6. ✅ 可以切换中英文
7. ✅ 可以输入拼音并看到候选词

### 预期的警告（不影响使用）：
- ⚠️ Native library 加载失败（如果没有编译 libRime.so）
- ⚠️ Rime 资源不存在（会使用 fallback 词库）
- ⚠️ AI 功能需要网络连接

## 下一步

1. **测试基本功能** - 确保键盘可以正常输入
2. **测试 16 KB 对齐** - 在支持 16 KB 页面的设备上测试
3. **编译完整的 Rime** - 如果需要完整的中文输入功能
4. **测试 AI 功能** - 确保网络请求正常工作
