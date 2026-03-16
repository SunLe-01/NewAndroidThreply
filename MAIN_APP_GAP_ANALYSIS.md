# iOS 主 App vs Android 主 App 差异分析

更新时间：2026-03-16

## 对比范围

本次只对比主 App，不包含键盘扩展本体的功能追平情况。

- Android 侧基线：
  - `app/src/main/java/com/arche/threply/MainActivity.kt`
  - `app/src/main/java/com/arche/threply/ui/home/HomeScreen.kt`
  - `app/src/main/java/com/arche/threply/ui/home/CModeCard.kt`
  - `app/src/main/java/com/arche/threply/ui/onboarding/OnboardingScreen.kt`
  - `app/src/main/java/com/arche/threply/ui/login/LoginScreen.kt`
  - `app/src/main/java/com/arche/threply/ui/login/PhoneLoginContent.kt`
  - `app/src/main/java/com/arche/threply/ui/profile/ProfileScreen.kt`
  - `app/src/main/java/com/arche/threply/ui/paywall/PaywallScreen.kt`
  - `app/src/main/java/com/arche/threply/billing/BillingManager.kt`

- iOS 侧基线：
  - `../threply/threply/threply/ContentView.swift`
  - `../threply/threply/threply/DeveloperMenuView.swift`
  - `../threply/threply/threply/StoreKitManager.swift`

## 结论

Android 主 App 在首页信息量上其实比 iOS 更多，已经额外提供了 DeepSeek API、截图监听、聊天扫描、Rime 方案切换、AI 触发桥接等卡片；但如果以 iOS 主 App 为基线，Android 目前还缺一组“主 App 闭环能力”，主要集中在开发者菜单、启动期自动任务、Apple 登录链路、以及若干交互细节。

## Android 相对 iOS 缺失的功能

### 1. 缺少主 App 启动时的画像推断触发

- iOS 在 `ContentView` 首次进入时会执行 `ProfileInferenceCoordinator.maybeRunFromApp()`，主动触发画像推断流程。
  - 参考：`../threply/threply/threply/ContentView.swift:255-259`
- Android `MainActivity` 只有 onboarding / home 路由和 paywall deep link 处理，没有对应的启动任务。
  - 参考：`app/src/main/java/com/arche/threply/MainActivity.kt:31-102`

结论：Android 主 App 还没有 iOS 这条“主 App 启动即尝试跑画像推断”的链路。

### 2. 缺少主 App 内的初始 Rime 资源部署

- iOS 在 onboarding 未完成时，会执行 `maybeStartInitialRimeDeployIfNeeded()`，并通过 `RimeAppDeployer.forceCopyResourcesAndDeploy` 做初始资源拷贝和 deploy。
  - 参考：`../threply/threply/threply/ContentView.swift:260-291`
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:80-294`
- Android 主 App 没有对应的初始化部署逻辑。
  - 参考：`app/src/main/java/com/arche/threply/MainActivity.kt:31-102`

结论：Android 侧 Rime 相关准备更多还停留在手动配置和键盘侧逻辑，主 App 没有承担 iOS 那种首启部署职责。

### 3. 缺少待处理 Paywall 请求桥接消费逻辑

- iOS 主 App 会从 App Group 里读取 `pendingPaywallRequestAt`，把键盘侧写入的待处理付费请求消费掉，再自动拉起 Paywall。
  - 参考：`../threply/threply/threply/ContentView.swift:71-86`
  - 参考：`../threply/threply/threply/ContentView.swift:263-299`
- Android 主 App 目前只支持外部 deep link `threply://paywall`，没有看到对应的“共享待处理请求”消费逻辑。
  - 参考：`app/src/main/java/com/arche/threply/MainActivity.kt:31-55`
  - 参考：`app/src/main/AndroidManifest.xml:24-38`

结论：Android 有 paywall deep link 入口，但还没有 iOS 那种由键盘/共享存储驱动的主 App 付费唤起桥。

### 4. 缺少 Apple 登录链路和授权状态校验

- iOS 登录页提供 `SignInWithAppleButton`，并且会在登录时校验 Apple 凭证，在首页首次进入时也会刷新 Apple 授权状态。
  - 参考：`../threply/threply/threply/ContentView.swift:1483-1715`
  - 参考：`../threply/threply/threply/ContentView.swift:1254-1279`
- Android 登录页目前是 Google 登录 + 手机验证码登录，没有对应的 Apple 登录路径，也没有类似的 Apple 凭证状态刷新逻辑。
  - 参考：`app/src/main/java/com/arche/threply/ui/login/LoginScreen.kt:35-317`
  - 参考：`app/src/main/java/com/arche/threply/ui/login/PhoneLoginContent.kt:24-238`

结论：如果以 iOS 功能集合为准，Android 主 App 仍缺 Apple 登录这一条账号入口。

### 5. 缺少可用的“开发者菜单”入口

- iOS 个人中心里的“开发者模式”可以实际跳到 `DeveloperMenuView`。
  - 参考：`../threply/threply/threply/ContentView.swift:1820-1826`
- Android 个人中心里“开发者模式”卡片仍是 TODO，没有跳转实现。
  - 参考：`app/src/main/java/com/arche/threply/ui/profile/ProfileScreen.kt:217-237`

结论：Android 目前没有把开发者能力暴露到主 App 可用入口。

### 6. 缺少整套开发者菜单能力

iOS `DeveloperMenuView` 已经是一整套完整主 App 调试面板，Android 主 App 目前没有对应页面。缺失项包括：

- API Key 保存/清除、后端登录态提示
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:997-1026`
- 订阅调试：手动撤销 / 恢复 Pro 权限
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:1028-1046`
- Rime 状态、Predict 状态、Deploy 记录、强制部署、Rime 设置
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:1048-1181`
- 融球效果调试
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:1183-1239`
- 键盘扩展日志查看、复制、清空
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:1241-1281`
- OCR 文本 / 回复历史采集开关和存储查看
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:1283-1439`
- 用户画像推断、画像 JSON、词云查看
  - 参考：`../threply/threply/threply/DeveloperMenuView.swift:1301-1461`

结论：这是 Android 主 App 相对 iOS 最大的页面级功能缺口。

### 7. 缺少触感强度的即时预览

- iOS 在个人中心拖动“触感强度”滑杆时，会调用 `Haptics.previewStrength` 做即时预览。
  - 参考：`../threply/threply/threply/ContentView.swift:1772-1789`
- Android 只保存强度值，没有即时预览反馈。
  - 参考：`app/src/main/java/com/arche/threply/ui/profile/ProfileScreen.kt:109-133`

结论：Android 个人中心少了 iOS 这条很直接的交互反馈。

### 8. 缺少 Paywall 的恢复中 / 处理中状态反馈

- iOS Paywall 顶部 `Restore` 按钮会根据 `isRestoring` 显示 `Restoring...` 并禁用；购买按钮也会在处理中显示 `处理中...` 并禁用。
  - 参考：`../threply/threply/threply/ContentView.swift:2143-2153`
  - 参考：`../threply/threply/threply/ContentView.swift:2183-2188`
  - 参考：`../threply/threply/threply/ContentView.swift:2079-2110`
- Android 虽然底层 `BillingManager` 有 `isRestoring` / `isProcessingPurchase`，但页面只消费了 `isProcessingPurchase` 的一部分场景；顶部 `Restore` 没有恢复中状态，Checkout 页的“确认支付”按钮也没有处理中态文案或禁用。
  - 参考：`app/src/main/java/com/arche/threply/billing/BillingManager.kt:81-97`
  - 参考：`app/src/main/java/com/arche/threply/billing/BillingManager.kt:239-247`
  - 参考：`app/src/main/java/com/arche/threply/ui/paywall/PaywallScreen.kt:90-95`
  - 参考：`app/src/main/java/com/arche/threply/ui/paywall/PaywallScreen.kt:461-483`

结论：Android Paywall 底层计费已接上，但主 App 的状态反馈还没补齐到 iOS 水平。

### 9. 缺少 onboarding 权限页的自动推进与刷新动作

- iOS 权限页会监听回到前台后的权限状态，满足条件后自动进入下一步；未满足时也有刷新按钮。
  - 参考：`../threply/threply/threply/ContentView.swift:435-453`
  - 参考：`../threply/threply/threply/ContentView.swift:513-556`
- Android 权限页是手动判断 `isEnabled` / `isSelected` 后点“继续”，没有自动推进，也没有独立刷新操作。
  - 参考：`app/src/main/java/com/arche/threply/ui/onboarding/OnboardingScreen.kt:68-76`
  - 参考：`app/src/main/java/com/arche/threply/ui/onboarding/OnboardingScreen.kt:153-198`

结论：Android onboarding 可用，但流程感和引导完成度比 iOS 弱一档。

### 10. 缺少 C 模式卡片里的格点触感反馈

- iOS `CModeCard` 在拖动过程中会按格点变化触发 `Haptics.selection()`。
  - 参考：`../threply/threply/threply/ContentView.swift:1449-1454`
- Android `CModeCard` 目前只有拖动和保存，没有对应的格点触感反馈。
  - 参考：`app/src/main/java/com/arche/threply/ui/home/CModeCard.kt:115-134`

结论：这是首页交互细节差异，不影响功能可用，但体验不如 iOS 完整。

## 内容与排版差异

以下属于“差异”，不一定算 Android 缺功能。

### 1. 首页内容结构不同

- iOS 首页更克制，主要是：
  - C 模式卡片
  - 键盘与权限
  - 快捷指令安装
  - 重新观看引导
  - Pro 付费入口
  - 参考：`../threply/threply/threply/ContentView.swift:1063-1075`
- Android 首页信息更重，除了上面几类，还额外放了：
  - DeepSeek API
  - 截图智能回复
  - 聊天扫描（无障碍）
  - 拼音词库方案
  - 辅助功能设置
  - AI 触发桥接
  - 参考：`app/src/main/java/com/arche/threply/ui/home/HomeScreen.kt:148-404`

### 2. 首页品牌内容不同

- iOS 首页和 onboarding 完成页使用了真实 logo 资源 `threplylogoH`。
  - 参考：`../threply/threply/threply/ContentView.swift:1100-1107`
  - 参考：`../threply/threply/threply/ContentView.swift:683-687`
- Android 首页头部直接写了注释说明“因为没有 image asset，用 logo text placeholder”。
  - 参考：`app/src/main/java/com/arche/threply/ui/home/HomeScreen.kt:99-112`
- Android onboarding 完成页也是纯文字，没有品牌 logo 资源。
  - 参考：`app/src/main/java/com/arche/threply/ui/onboarding/OnboardingScreen.kt:490-524`

### 3. 登录 / 个人中心 / 付费页的容器形式不同

- iOS 这三类页面主要走 `NavigationStack` + sheet，页面有标准标题和关闭按钮。
  - 参考：`../threply/threply/threply/ContentView.swift:1493-1576`
  - 参考：`../threply/threply/threply/ContentView.swift:1748-1847`
  - 参考：`../threply/threply/threply/ContentView.swift:1993-2172`
- Android 三者都是 `ModalBottomSheet` 承载。
  - 参考：`app/src/main/java/com/arche/threply/ui/home/HomeScreen.kt:407-447`

### 4. 个人中心布局风格不同

- iOS 个人中心是系统 `Form` 风格，结构更像设置页。
  - 参考：`../threply/threply/threply/ContentView.swift:1748-1847`
- Android 个人中心是自定义卡片 + 分组标题，视觉上更像内容页。
  - 参考：`app/src/main/java/com/arche/threply/ui/profile/ProfileScreen.kt:39-261`

### 5. Paywall 流程不同

- iOS Paywall 是单页直接购买。
  - 参考：`../threply/threply/threply/ContentView.swift:1993-2188`
- Android Paywall 是“两段式”：先选方案，再进入 Checkout 页确认支付。
  - 参考：`app/src/main/java/com/arche/threply/ui/paywall/PaywallScreen.kt:121-154`

### 6. Android onboarding 第二步的标题和内容不一致

- step 枚举标题仍然叫“添加快捷指令”。
  - 参考：`app/src/main/java/com/arche/threply/ui/onboarding/OnboardingScreen.kt:43-48`
- 但实际页面内容已经变成“辅助功能设置”，说明是 Android 替代方案。
  - 参考：`app/src/main/java/com/arche/threply/ui/onboarding/OnboardingScreen.kt:426-473`

结论：这不是缺功能，但文案会让用户误以为 Android 也需要装快捷指令。

### 7. Android Paywall 文案仍带有“演示模式”残留

- Android Checkout 页文案写的是“后续再替换成真实 Google Play 购买”“不会真实购买，也不会把你标记为 Pro”。
  - 参考：`app/src/main/java/com/arche/threply/ui/paywall/PaywallScreen.kt:378-455`
- 但 `BillingManager` 实际已经接了 `launchBillingFlow` 和购买回调。
  - 参考：`app/src/main/java/com/arche/threply/billing/BillingManager.kt:215-237`
  - 参考：`app/src/main/java/com/arche/threply/billing/BillingManager.kt:249-260`

结论：这是 Android 主 App 当前最明显的内容层错误，容易误导测试和产品判断。

## 不应算作 Android 缺失的部分

这些点和 iOS 不同，但不能归类为“Android 比 iOS 少了”：

- Android 已有手机验证码登录；iOS 这里仍是“暂未开放”的提示。
  - Android：`app/src/main/java/com/arche/threply/ui/login/PhoneLoginContent.kt:24-238`
  - iOS：`../threply/threply/threply/ContentView.swift:1571-1575`
- Android 首页已经额外提供多个系统能力卡片，信息密度高于 iOS。
- Android 已接入 Google Play Billing；iOS 是 StoreKit 购买，两边都是平台原生实现。

## 建议优先级

如果只从“补齐 Android 主 App 相对 iOS 的缺口”出发，优先级建议如下：

1. 先补“开发者模式入口 + 开发者菜单”。
2. 再补“主 App 启动期自动任务”：Rime 初始部署、画像推断触发、Paywall 请求桥接。
3. 再补“交互细节”：个人中心触感强度预览、onboarding 自动推进、Paywall 状态反馈、C 模式触感。
