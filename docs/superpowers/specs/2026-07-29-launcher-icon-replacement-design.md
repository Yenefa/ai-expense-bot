# v3.5 启动图标替换设计

## 目标

将 `ChatGPT Image 2026年7月29日 23_13_35.png` 用作“记账助手”v3.5 的手机桌面启动图标。

## 范围

- 仅替换 Android Launcher 图标。
- 同时支持普通图标与圆形图标。
- 不修改应用内界面、业务逻辑、包名、版本号或数据结构。

## 实现

保留现有 Android 自适应图标结构：

- `mipmap-anydpi-v26/ic_launcher.xml`
- `mipmap-anydpi-v26/ic_launcher_round.xml`
- `drawable/ic_launcher_background.xml`
- 五档密度的 `drawable-*/ic_launcher_foreground.png`

输入图片按完整正方形画面等比缩放为 108、162、216、324、432 像素的前景资源。背景色使用图片边缘的深紫色 `#2B1A3B`，避免系统使用圆形或圆角遮罩时出现不协调边缘。

## 构建与验证

1. 从当前 v3.5 源码构建 Debug APK。
2. 检查 APK 包名、版本号、启动图标资源和签名。
3. 验证 APK 完整性；若有可用 Android 设备，再执行安装验证。
4. 输出独立命名的最终 APK 和 SHA-256 校验值。
