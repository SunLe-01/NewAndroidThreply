把下面这段直接发给 Claude Code：

```text
请在当前 Android 仓库继续完成“引入 libRime.so 后的验证与诊断脚本”，不要把任务偷换成纯文档整理。

背景：
- 现在 `app/src/main/cpp/include/rime_api.h` 已就位
- `app/src/main/cpp/CMakeLists.txt` 已会检测 `app/src/main/jniLibs/<ABI>/libRime.so`
- CMake 构建时会打印 ABI、libRime.so、rime_api.h、最终 HAS_LIBRIME
- `RimeNativeBridge.kt` 已有 `DiagnosticStatus`
- `RimeEngineController.kt` 已有 `getDiagnosticStatus()`
- `RimeResourceManager.kt` 已有 `getDiagnosticInfo()`
- 当前项目 `abiFilters` 只有 `arm64-v8a`
- `minSdk = 26`
- Rime assets 由 `syncRimeAssets` 打包到 APK 的 `rime/shared`

请先阅读并分析这些文件：
- app/build.gradle.kts
- app/src/main/cpp/CMakeLists.txt
- app/src/main/cpp/rime_jni.cpp
- app/src/main/java/com/arche/threply/ThreplyApp.kt
- app/src/main/java/com/arche/threply/ime/ThreplyInputMethodService.kt
- app/src/main/java/com/arche/threply/ime/rime/RimeNativeBridge.kt
- app/src/main/java/com/arche/threply/ime/rime/RimeEngineController.kt
- app/src/main/java/com/arche/threply/ime/rime/RimeResourceManager.kt
- KEYBOARD_DEBUG_GUIDE.md
- README.md

目标：
1. 新增一套可执行的验证与诊断脚本，不要只给建议。
2. 以 Windows / PowerShell 为第一优先，因为当前开发环境就是 Windows。
3. 允许附带 bash 版本，但 PowerShell 版必须是主入口。
4. 脚本要覆盖“构建前检查 + 构建期确认 + 运行期诊断”三段。
5. 脚本要尽量复用项目里已经有的 `DiagnosticStatus` / `DiagnosticInfo` / 现有日志 tag。

我希望你直接落这些东西：

一、至少新增一个主入口脚本，例如：
- `scripts/verify-rime-native.ps1`

二、这个主入口脚本至少要完成这些检查：
1. 检查 `app/src/main/jniLibs/arm64-v8a/libRime.so` 是否存在
2. 打印文件大小、修改时间、SHA256
3. 如果本机有 `llvm-readelf` / `readelf` / `dumpbin` 之类工具，尽量输出：
   - ELF Header / 架构信息
   - Dynamic section 里的 `NEEDED` 依赖
   - Program headers / LOAD segment
   - 与 16 KB page size 相关的可见信息
4. 调用 `.\gradlew.bat :app:assembleDebug`
5. 明确解析构建输出，告诉我当前是否真的是：
   - `libRime.so FOUND`
   - `rime_api.h FOUND`
   - `HAS_LIBRIME=1`
6. 如果构建失败，要把失败原因单独高亮出来

三、如果本机安装了 `adb` 且连接了设备，再继续做运行期检查：
1. 安装或更新 debug APK
2. 清空并抓取和 Rime 相关的 logcat
3. 过滤这些 tag：
   - `RimeNativeBridge`
   - `RimeEngineController`
   - `RimeResourceManager`
   - `rime_jni`
   - `ThreplyIME`
4. 尽量自动拉起 app 主 Activity
5. 如果可行，使用 `adb shell run-as com.arche.threply` 检查：
   - `files/rime/shared`
   - `files/rime/user`
   - 共享目录文件数
6. 输出一个本地诊断摘要文件，例如：
   - `build/rime-diagnostics/latest-report.txt`

四、如果当前 app 侧日志还不够，请做最小必要补强，但不要大改架构：
1. 在合适位置输出一条紧凑的 diagnostic snapshot，至少包含：
   - native library loaded
   - native ready
   - current schema
   - sharedDataDir
   - userDataDir
   - shared assets deployed / file count
   - 当前是 native 还是 fallback
2. 只加必要日志，不要把 IME 日志刷爆

五、脚本的容错要求：
1. 没有 `libRime.so` 时要清楚报错，但不要异常堆栈满屏
2. 没有 `adb` 时要跳过运行期诊断并说明原因
3. 没有设备时要跳过运行期诊断并说明原因
4. 没有 `readelf` 类工具时也要继续执行
5. 不要依赖网络

六、文档补充：
1. 更新 `KEYBOARD_DEBUG_GUIDE.md` 或 `README.md`
2. 加一段最短使用说明，告诉我：
   - 脚本怎么运行
   - 成功时我应该看到什么
   - 失败时先看哪几个字段

验收标准：
1. 在 Windows PowerShell 下，一条命令就能跑完整套本地验证
2. 结果里能明确区分：
   - stub-only
   - native library found but init failed
   - native library loaded and initialized
3. 结果里能明确看到：
   - 当前 schema
   - shared/user 目录状态
   - 当前是否 fallback
4. 不要只输出原始 logcat；要给一个收敛过的摘要结果
5. 不要破坏现有 Rime 接入链路

请直接改代码和脚本，不要只给建议。完成后告诉我：
- 改了哪些文件
- 新增的脚本入口是什么
- 运行命令是什么
- 输出结果怎么看
- 还有哪些诊断盲区
```
