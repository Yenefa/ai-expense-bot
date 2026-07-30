# 品牌深紫系统启动页设计

## 背景

当前应用只配置了继承自 `android:Theme.Material.Light.NoActionBar` 的
`Theme.ExpenseTracker`，没有显式配置 Android SplashScreen。Android 12 及以上因此使用
启动图标和浅色主题推导出的白色窗口背景，形成“白底中央图标”的启动画面。

现有自适应图标背景色为 `#2B1A3B`，与 70% 安全区版本图标的外圈颜色一致。

## 已确认目标

- 系统启动页背景使用品牌深紫 `#2B1A3B`。
- 中央图标继续使用当前 70% 安全区启动图标，不重新裁切、不更换图稿。
- 浅色和深色应用模式都使用同一品牌深紫启动背景。
- 不增加类似飞书 slogan 的第二层品牌页面。
- 不改变应用进入首页后的主题、页面动画、CSV 导入及其他业务功能。
- 保持版本号 `3.6`、`versionCode 27`。

## 方案对比与决定

1. 保留系统默认白底：改动最少，但与图标品牌色不统一。
2. 配置 Android 系统 SplashScreen：由系统继续负责布局与退出时机，应用只提供品牌背景和现有图标。改动小、启动快、跨版本行为稳定。
3. 系统启动页后追加自定义 Compose 品牌页：可以自由放置名称和 slogan，但会形成双层启动画面并增加等待感。

采用方案 2。

## 技术设计

### 启动主题

新增 `Theme.ExpenseTracker.Starting`，父主题使用 `Theme.SplashScreen.IconBackground`，配置：

- `windowSplashScreenBackground`：`@color/splash_background`
- `windowSplashScreenAnimatedIcon`：`@drawable/ic_launcher_foreground`
- `windowSplashScreenIconBackgroundColor`：`@color/splash_background`
- `postSplashScreenTheme`：`@style/Theme.ExpenseTracker`
- 启动阶段状态栏和导航栏背景：`@color/splash_background`
- 启动阶段系统栏图标：使用适合深紫背景的浅色图标

颜色资源 `splash_background` 固定为 `#2B1A3B`。不添加 `values-night` 覆盖，确保两种应用主题下品牌色一致。
前景与背景分离可避免 AndroidX 在 API 26–30 对自适应图标的裁切和缩放，同时继续复用现有资源。

### Activity 接入

- `MainActivity` 在 `super.onCreate()` 之前调用 `installSplashScreen()`。
- Manifest 只把 `MainActivity` 的主题指向 `Theme.ExpenseTracker.Starting`。
- Application 继续使用 `Theme.ExpenseTracker`，避免扩大启动主题影响范围。
- 引入 AndroidX `core-splashscreen` 兼容库，使当前最低 API 26 到 Android 12 以上使用统一接入方式。

### 图标和过渡

- 不生成新的位图；系统启动页复用现有图标前景和品牌背景资源。
- 不人为延长启动页时间。
- 不添加独立 Splash Activity。
- SplashScreen 在首帧就绪后按系统时机退出，随后切换到现有 Compose 内容。

## 验证策略

先增加静态验证脚本并确认它在实现前失败，再完成最小配置使其通过。脚本验证：

- 品牌色为 `#2B1A3B`。
- Starting 主题包含背景、图标、图标底色和 post theme。
- Manifest 只给 `MainActivity` 应用 Starting 主题。
- `installSplashScreen()` 位于 `super.onCreate()` 之前。
- Gradle 包含 `core-splashscreen` 依赖。

完成后执行：

- 启动页静态验证脚本。
- 现有 70% 图标安全区验证脚本。
- `testDebugUnitTest` 全量单元测试，并使用 128 行真实 CSV 样本。
- `assembleDebug` 构建。
- APK 包名、`versionName`、`versionCode` 和 v2 签名验证。

由于当前没有连接 Android 设备，最终的系统启动页颜色、厂商状态栏细节和退出动画仍需安装到用户手机后进行真机确认。
