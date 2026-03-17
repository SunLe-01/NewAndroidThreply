# 如何自己编译 Android `libRime.so`

## 目标

这份文档是给当前 Android 仓库用的，不是泛泛的 Rime 教程。当前项目已经具备这些前提：

- `app/src/main/cpp/include/rime_api.h` 已经在仓库里。
- `app/src/main/cpp/CMakeLists.txt` 会自动检测 `app/src/main/jniLibs/<ABI>/libRime.so`。
- `app/build.gradle.kts` 目前只打开了 `arm64-v8a`，`minSdk = 26`。
- `syncRimeAssets` 会把 `../threply/threply/ThreplyKeyboard/RimeResources` 打进 APK。

所以你现在缺的不是接入链路，而是一个可用的 `arm64-v8a/libRime.so`。

## 先说结论

对这个项目，最稳妥的自编译路径是：

1. 本地编译一个 Android Rime 前端工程，优先参考 `Trime`。
2. 从它的构建产物里取出 `arm64-v8a/libRime.so`。
3. 放到当前仓库的 `app/src/main/jniLibs/arm64-v8a/libRime.so`。
4. 回到当前仓库重新构建并验证 `HAS_LIBRIME=1`。

不建议只编“纯 upstream base librime”后直接塞进来。你当前这套 `RimeResources` 明确依赖：

- `librime-lua`
- `librime-predict`
- OpenCC 相关资源

原因很直接：

- [rime_ice.schema.yaml](/c:/Users/12686/Desktop/threply-android/app/build/generated/rimeAssets/rime/shared/rime_ice.schema.yaml) 启用了 `predictor` / `predict_translator`，并引用了 `predict.db`。
- [double_pinyin.schema.yaml](/c:/Users/12686/Desktop/threply-android/app/build/generated/rimeAssets/rime/shared/double_pinyin.schema.yaml) 和多套 schema 启用了 `lua_processor` / `lua_translator` / `lua_filter`。

如果 `.so` 里没有这些插件，最常见结果不是“完全不能启动”，而是：

- schema deploy 失败
- 某些 schema 可切但功能残缺
- `rime_ice` 或双拼相关能力异常
- 候选行为和 iOS 明显不一致

## 方案选择

### 方案 A：用 Trime 本地编出 `libRime.so`

这是推荐方案。原因是它本身就是 Android Rime 前端，已经处理过 Android NDK、ABI、插件和资源这一层的坑。

### 方案 B：从 upstream `librime` 和各插件手工交叉编译

这条路能做，但工程量明显更大。只有在你需要完全自己掌控版本矩阵时才建议走。

下面优先写方案 A，最后补方案 B 的手工编译骨架。

## 方案 A：基于 Trime 自己编

### 1. 准备环境

建议环境：

- Windows 11 + PowerShell
- Android Studio 稳定版
- Android SDK
- Android NDK r28 或更高
- CMake
- JDK 17
- Python 3
- Git

如果你在 Windows 上编译 Android native，优先建议：

- 把源码放在一个短路径里，比如 `C:\src\trime`
- 不要放进 OneDrive
- 如果走 Git checkout，先开 symlink 支持

PowerShell：

```powershell
git config --global core.symlinks true
```

如果你已经装了 Android Studio，先确认 SDK/NDK 路径。当前机器常见路径类似：

```text
C:\Users\12686\AppData\Local\Android\Sdk
```

你至少要确认这些目录存在：

```text
C:\Users\12686\AppData\Local\Android\Sdk\ndk\<version>
C:\Users\12686\AppData\Local\Android\Sdk\platform-tools
C:\Users\12686\AppData\Local\Android\Sdk\cmake
```

如果你更习惯 Linux 工具链，也可以在 WSL2 里编。对复杂 native 依赖来说，WSL2 往往比纯 Windows 更稳定。

### 2. 拉取 Trime 源码

PowerShell：

```powershell
cd C:\
mkdir src -ErrorAction SilentlyContinue
cd C:\src
git clone https://github.com/osfans/trime.git
cd .\trime
git submodule update --init --recursive
```

如果 submodule 或 symlink 有异常，优先处理这个问题，不要继续往下构建。

### 3. 检查 Android 构建前提

你要先确认：

- Android Studio 里已经装好了 NDK
- `local.properties` 指向正确的 `sdk.dir`
- Trime 仓库没有因为 symlink 丢失文件

如果 Trime 仓库没有自动生成 `local.properties`，可以手动补一个：

```properties
sdk.dir=C:\\Users\\12686\\AppData\\Local\\Android\\Sdk
```

### 4. 编译 Trime

先走最直接的 Gradle 路径：

```powershell
.\gradlew.bat assembleDebug
```

如果仓库内还有 `make debug` 这类封装命令，也可以使用，但对 Windows 来说优先尝试 Gradle 原生命令。

构建完成后，不要先急着复制文件，先搜索真正产物：

```powershell
Get-ChildItem -Recurse -File . | Where-Object { $_.Name -eq 'libRime.so' } | Select-Object FullName, Length
```

你要的是：

- 文件名精确叫 `libRime.so`
- `ABI` 是 `arm64-v8a`

注意不要拿错这几个东西：

- `librime_jni.so`
- `x86` 或 `x86_64` 版本
- 中间产物里的 stub 库

### 5. 检查 `libRime.so` 本体

如果你已经装了 NDK，可以直接用 NDK 自带的 ELF 工具检查。

PowerShell 示例：

```powershell
$Sdk = "C:\Users\12686\AppData\Local\Android\Sdk"
$Ndk = Get-ChildItem "$Sdk\ndk" | Sort-Object Name -Descending | Select-Object -First 1
$ToolRoot = Join-Path $Ndk.FullName "toolchains\llvm\prebuilt\windows-x86_64\bin"
$ReadElf = Join-Path $ToolRoot "llvm-readelf.exe"

& $ReadElf -h path\to\libRime.so
& $ReadElf -d path\to\libRime.so
& $ReadElf -l path\to\libRime.so
```

重点看三件事：

1. 它确实是 `AArch64` / `arm64-v8a`
2. `Dynamic section` 里有没有额外 `NEEDED` 依赖
3. `LOAD` 段对齐不要太旧，尽量使用 NDK r28+ 产物

如果 `NEEDED` 里还有其他运行时依赖，比如额外的 `.so`，那你不能只复制 `libRime.so`，还要把这些依赖一起带到当前项目的同一 ABI 目录。

### 6. 把产物放回当前仓库

目标目录是：

```text
C:\Users\12686\Desktop\threply-android\app\src\main\jniLibs\arm64-v8a\
```

PowerShell：

```powershell
New-Item -ItemType Directory -Force "C:\Users\12686\Desktop\threply-android\app\src\main\jniLibs\arm64-v8a"
Copy-Item path\to\libRime.so "C:\Users\12686\Desktop\threply-android\app\src\main\jniLibs\arm64-v8a\libRime.so"
```

如果 `llvm-readelf -d` 显示它还依赖其他 `.so`，一并复制到这个目录。

### 7. 回到当前仓库重新构建

PowerShell：

```powershell
cd C:\Users\12686\Desktop\threply-android
.\gradlew.bat :app:assembleDebug
```

你要在 CMake 日志里明确看到类似结果：

- `ABI: arm64-v8a`
- `libRime.so: FOUND`
- `rime_api.h: FOUND`
- `Result: HAS_LIBRIME=1 (full native Rime)`

如果仍然是 `HAS_LIBRIME=0`，优先排查：

- 文件是不是放错目录
- 文件名是不是不是 `libRime.so`
- 你是不是拿了错误 ABI
- 是否只复制了 `librime_jni.so`

### 8. 安装并验证运行时

构建成功后，先安装 APK，再看日志。

建议过滤这些 tag：

- `RimeNativeBridge`
- `RimeEngineController`
- `RimeResourceManager`
- `rime_jni`
- `ThreplyIME`

你想看到的关键信号是：

- `lib rime_jni.so` 正常加载
- `Native initialize: schema=..., result=true`
- `suggestFromPinyin: native path returned ...`
- 不再是 stub-only warning

然后至少手测这些项：

- `nihao`
- `women`
- `jisuanji`
- `qiche`
- `youxi`

以及 schema 切换：

- `rime_ice`
- `luna_pinyin`
- `double_pinyin`

### 9. 成功标准

满足下面这些，基本就算接通了：

1. 构建日志显示 `HAS_LIBRIME=1`
2. 运行日志显示 native initialize 成功
3. 常用字词候选量明显高于当前 fallback
4. `rime_ice`、`luna_pinyin`、`double_pinyin` 至少能切换并产出合理候选
5. 没有 silent fallback 到 stub-only 状态

## 方案 B：手工交叉编译 upstream `librime`

这条路适合你想完全自己控制版本和插件时使用。

### 1. 你至少要准备这些源码

核心库：

- `rime/librime`

当前项目建议同时准备的插件：

- `hchunhui/librime-lua`
- `rime/librime-predict`

`librime` 官方 README 列出的核心依赖包括：

- Boost
- LevelDB
- Marisa
- OpenCC
- yaml-cpp
- glog 可选

### 2. 统一交叉编译参数

下面是一套可复用的 Android CMake 参数骨架。你需要对所有依赖和主库保持一致：

```bash
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export NDK=$ANDROID_SDK_ROOT/ndk/28.0.13004108
export TOOLCHAIN=$NDK/build/cmake/android.toolchain.cmake
export ABI=arm64-v8a
export API=26
export PREFIX=$HOME/rime-android/prefix/$ABI

export COMMON_CMAKE_ARGS="
  -G Ninja
  -DCMAKE_TOOLCHAIN_FILE=$TOOLCHAIN
  -DANDROID_ABI=$ABI
  -DANDROID_PLATFORM=android-$API
  -DCMAKE_BUILD_TYPE=Release
  -DBUILD_SHARED_LIBS=ON
  -DCMAKE_INSTALL_PREFIX=$PREFIX
  -DCMAKE_PREFIX_PATH=$PREFIX
  -DCMAKE_FIND_ROOT_PATH=$PREFIX
"
```

### 3. 推荐编译顺序

先编依赖，再编主库，再编插件：

1. `yaml-cpp`
2. `marisa-trie`
3. `leveldb`
4. `OpenCC`
5. Boost
6. `librime`
7. `librime-lua`
8. `librime-predict`

核心原则只有一个：

- 所有依赖都要安装到同一个 `$PREFIX`
- `librime` 和插件要用同一套 toolchain / ABI / API level

### 4. 单个依赖的通用编译模板

对基于 CMake 的依赖，大致都是这个套路：

```bash
git clone <repo>
cd <repo>
cmake -S . -B build-android $COMMON_CMAKE_ARGS
cmake --build build-android -j
cmake --install build-android
```

对非 CMake 依赖，需要按它自己的构建系统适配 Android toolchain，但原则不变：

- 最终头文件和库都进 `$PREFIX`
- 最终由 `librime` 的 CMake 能从 `$PREFIX` 找到它们

### 5. 编 `librime`

当 `$PREFIX` 里已经有前置依赖后，再编 `librime`：

```bash
git clone https://github.com/rime/librime.git
cd librime
cmake -S . -B build-android $COMMON_CMAKE_ARGS
cmake --build build-android -j
cmake --install build-android
```

### 6. 编 `librime-lua` 和 `librime-predict`

思路和上面一致，但要确保它们能找到已经安装好的 `librime` 和前置依赖。

如果插件自己的 CMake 支持显式传前缀，继续沿用：

```bash
-DCMAKE_PREFIX_PATH=$PREFIX
-DCMAKE_FIND_ROOT_PATH=$PREFIX
```

编完后，你需要确认最终得到的是 Android `arm64-v8a` 的 `libRime.so`，并且相关插件已编译进你期望的部署形态。

### 7. 手工路线什么时候容易出问题

最常见的是这几类：

- Boost 版本或 ABI 不匹配
- 插件和主库用的不是同一套 toolchain
- `predict.db` 在资源里有，但 `.so` 没有 `predict` 插件
- schema 用到了 Lua，但 `.so` 没有 `librime-lua`
- `.so` 还依赖别的共享库，你却只复制了一个 `libRime.so`

## 当前仓库接入后的最终落点

编好以后，当前仓库唯一必须存在的目标文件是：

```text
app/src/main/jniLibs/arm64-v8a/libRime.so
```

然后重新执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

## 额外建议

- 首次接通时先只保证 `arm64-v8a`，不要一开始就追多 ABI。
- 优先选 NDK r28 或更高，减少 16 KB page-size 兼容问题。
- 第一次跑通后，先做“构建日志 + 运行日志 + 输入验证”三重确认，再考虑清理和优化。
- 如果你只是想尽快验链路，方案 A 明显比方案 B 更适合当前项目。

## 参考资料

- `librime` 官方 README：<https://github.com/rime/librime/blob/master/README.md>
- `librime-predict` 官方 README：<https://github.com/rime/librime-predict>
- `librime-lua` 官方仓库：<https://github.com/hchunhui/librime-lua>
- `Trime` 官方 README：<https://github.com/osfans/trime/blob/develop/README.md>
- Android Studio 安装 NDK：<https://developer.android.com/studio/projects/install-ndk>
- Android 16 KB page size 兼容说明：<https://developer.android.com/guide/practices/page-sizes>
