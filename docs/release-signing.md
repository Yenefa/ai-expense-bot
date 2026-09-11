# 发布签名（Release Signing）

> 目标：在**不把密钥与口令放进仓库**的前提下，让 `assembleRelease` 产出可公开分发的签名 APK。
> 本文只描述流程，不提供任何真实口令；仓库不生成、不提交任何 `.jks` / `.keystore` 文件。

## 为什么公开发布需要真正的 keystore

- 目前公开渠道的 APK 使用 **Android debug keystore** 签名。debug 证书口令公开、仅供本地测试：应用商店（如 Google Play）不接受 debug 签名，公开分发也存在被冒名顶替的风险。
- Android 用**签名证书**识别应用身份：只有同一把密钥签名的 APK 才能覆盖安装升级。debug 证书与 release 证书不同，**换签名 = 老用户必须先卸载再重装**（本地记账数据会一并清除，需先按 README「数据迁移」导出）。
- 因此正式发布必须使用**自己生成并妥善保管**的 release keystore；正式包与现有 debug 签名包的升级路径不兼容，切换前请先公告用户。

## 1. 生成 keystore（仅一次）

用 JDK 自带的 `keytool`（本步骤不需要 Gradle）：

```bash
keytool -genkeypair -v \
  -keystore <your-path>/ye-cost-release.jks \
  -alias <your-alias> \
  -keyalg RSA -keysize 2048 -validity 10000
```

- 交互提示中自行设置 **keystore 口令**与 **key 口令**（可相同）；请使用强口令，不要复用、不要写入仓库。
- `.jks` 放在仓库之外（建议密码管理器 + 离线加密备份各一份）。
- 查看证书指纹：`keytool -list -v -keystore <your-path>/ye-cost-release.jks -alias <your-alias>`

## 2. 配置凭据（四个，缺一不可）

构建脚本按 **Gradle 属性优先、环境变量其次** 读取；四项必须全部非空才会启用 release 签名：

| 名称 | 含义 |
|---|---|
| `YE_COST_KEYSTORE_PATH` | keystore 文件路径（建议绝对路径） |
| `YE_COST_KEYSTORE_PASSWORD` | keystore 口令 |
| `YE_COST_KEY_ALIAS` | 密钥别名（即上面的 `<your-alias>`） |
| `YE_COST_KEY_PASSWORD` | 密钥口令 |

方式一：环境变量（仅当前终端会话有效）

```bash
export YE_COST_KEYSTORE_PATH="<your-path>/ye-cost-release.jks"
export YE_COST_KEYSTORE_PASSWORD="<your-keystore-password>"
export YE_COST_KEY_ALIAS="<your-alias>"
export YE_COST_KEY_PASSWORD="<your-key-password>"
```

方式二：Gradle 属性（写入**用户级** `~/.gradle/gradle.properties`，**切勿**写进仓库内的 `gradle.properties`）

```properties
YE_COST_KEYSTORE_PATH=<your-path>/ye-cost-release.jks
YE_COST_KEYSTORE_PASSWORD=<your-keystore-password>
YE_COST_KEY_ALIAS=<your-alias>
YE_COST_KEY_PASSWORD=<your-key-password>
```

> ⚠️ 上述任何真实值都不得提交到 git，也不得出现在 CI 日志、Issue 或文档中。

## 3. 构建签名 APK

```bash
./gradlew :app:assembleRelease -PYE_COST_SUBSCRIPTION_API_BASE_URL=https://<your-api-host>/
```

（Windows 使用 `.\gradlew.bat ...`；订阅地址必须是 HTTPS，构建脚本已有校验。）

- 四个签名凭据齐全 → 输出 `app/build/outputs/apk/release/app-release.apk`（已签名）。
- 任意一个缺失 → 输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，构建**不会**失败，也**不会**回退 debug 签名。

## 4. 验证签名（apksigner）

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

期望结果：输出 `Verifies`，且 signer 证书的 SHA-256 指纹与第 1 步 `keytool -list -v` 中你的 release 证书一致。
若出现 `DOES NOT VERIFY` 或证书显示 `CN=Android Debug`，说明签名未生效或仍是 debug 证书，请回到第 2 步检查四个凭据是否齐全。

## ⚠️ 备份（最重要）

- **务必备份 keystore 文件与两个口令**（不少于两份异地/离线副本，口令存密码管理器）。keystore 一旦丢失且无法找回，就**再也无法签发可覆盖安装的升级包**：老用户只能先卸载旧版（本地数据清除）再安装新版。
- 启用 release 签名后，此前安装 debug 签名版本的设备无法直接升级，需要一次卸载/重装；此后每个版本都必须用同一把 key 签名。
- 本仓库不会生成 keystore，也不会代为保管任何密钥或口令。
