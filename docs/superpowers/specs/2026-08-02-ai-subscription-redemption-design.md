# AI 订阅与兑换码设计

## 目标

为直接分发的 Y.E cost APK 增加可长期演进的 AI 订阅体系。测试阶段不接真实支付，用户通过一次性兑换码获得 30 天 AI 权益；未来接入正式支付时复用同一套服务端权益模型。

## 安全边界

- CloudBase AI API Key 只能保存在服务端密钥或环境变量中，禁止写入 Android 源码、资源、BuildConfig、APK、测试夹具、日志或文档。
- 先前在聊天中公开的 API Key 必须在 CloudBase 控制台撤销并重新生成，不能用于发布版本。
- App 只持有可撤销、有到期时间的订阅访问令牌。令牌使用 Android Keystore 加密存储，并从系统备份与设备迁移中排除。
- 服务端是订阅状态的唯一权威来源；客户端显示的到期时间不能用于绕过服务端校验。
- AI 请求、响应、账目、备注、兑换码、访问令牌和异常详情不得写入 Release 日志。

## 总体结构

### 当前 APK 测试交付

本机没有 CloudBase 部署项目或 CLI，且用户要求本轮直接交付 APK，因此测试版采用“凭证兑换”模式：用户在 App 内输入由管理员单独发放的 CloudBase API Key，App 在线验证后将它作为 30 天兑换凭证用 Android Keystore 加密保存。固定 Base URL 使用用户提供的 CloudBase AI Gateway 地址，模型固定为 `hy3`。

这个测试方案不会把共享 Key 写入 APK，但持有兑换凭证的测试者仍能取得并滥用它，因此仅适用于当前可信朋友测试。管理员应为测试单独创建可撤销、限额的 Key，并在测试结束后轮换。短兑换码、一次性使用、防共享、服务端统一限流与真实付费必须由下述代理阶段提供。

### 正式代理阶段

```text
Y.E cost Android App
  ├─ POST /v1/subscriptions/redeem
  ├─ GET  /v1/subscriptions/status
  └─ POST /v1/chat/completions
                 │
                 ▼
CloudBase 云函数 ai-subscription-proxy
  ├─ 兑换码与权益数据库
  ├─ 访问令牌、限流和全局预算保护
  └─ CloudBase AI Gateway /v1/ai/cloudbase（固定模型 hy3）
```

当前仓库没有 CloudBase 项目文件或已安装的 CloudBase CLI，因此正式代理的源代码与部署只能作为后续独立交付。当前 APK 只对“直接发放 API Key 作为测试兑换凭证”的流程声明可用，不能宣称已具备服务端防共享能力。

## 订阅规则

- 当前测试 APK 将管理员发放的 CloudBase API Key 视为兑换凭证；同一安装实例内，每个凭证只能首次兑换一次。
- 首次兑换从服务端确认时间起增加 30 天。
- 有效期内兑换新码时，从当前到期时间继续增加 30 天；已过期时从当前服务端时间重新计算。
- 测试 APK 不改变 API Key 大小写，只去除首尾空白；DataStore 仅保存凭证 SHA-256，用于阻止本机重复兑换，凭证明文只存在 Keystore 加密密文。
- 测试阶段权益来源记录为 `REDEMPTION_CODE`；未来可增加 `PLAY_SUBSCRIPTION`，不改变 Android 端的权益状态结构。
- 没有账号系统。权益绑定当前 App 安装实例；清除数据或重装后需要新兑换码，由测试管理员补发。

## 服务端数据

### redemption_codes

- `codeHash`：兑换码 SHA-256，唯一索引。
- `durationDays`：固定为 30。
- `redeemedAt`、`redeemedBy`：为空表示可用；兑换时在同一事务中写入。
- `createdAt`、`disabledAt`：用于管理和作废。

### entitlements

- `installationId`：App 首次运行生成的随机 UUID，唯一索引。
- `expiresAt`：服务端时间计算的权益到期时间。
- `source`：当前为 `REDEMPTION_CODE`。
- `updatedAt`：最后一次权益变更时间。

### access_tokens

- `tokenHash`：随机访问令牌的 SHA-256，数据库不保存明文。
- `installationId`、`expiresAt`、`revokedAt`。
- 兑换成功后签发新令牌；客户端只在响应当次收到明文。

## 服务端接口

### POST /v1/subscriptions/redeem

请求包含 `installationId` 和 `redemptionCode`。服务端在事务中验证兑换码、标记已使用、创建或延长权益，并签发访问令牌。重复码、禁用码和无效码统一返回不泄露内部状态的错误。

### GET /v1/subscriptions/status

使用 `Authorization: Bearer <subscription token>`。返回 `ACTIVE` 或 `EXPIRED` 以及服务端到期时间；无效、撤销或过期令牌返回 401。

### POST /v1/chat/completions

保持 OpenAI Chat Completions 请求和响应结构，使现有 `LlmClient` 仅需切换 Base URL 和凭证来源。服务端必须验证订阅有效、限制请求体大小、覆盖客户端传入模型为 `hy3`，再使用服务端 CloudBase API Key 调用 AI Gateway。

测试期默认保护参数：每个有效安装每分钟最多 5 次、每天最多 50 次；单请求消息总字符数最多 20,000；上游超时 60 秒。所有阈值由服务端环境变量配置。达到限制时返回 429，Android 显示简短可恢复提示。

## Android 体验

- 设置页新增「AI 会员」入口。
- 未开通时显示「兑换 30 天测试会员」、兑换码输入框和兑换按钮。
- 开通后显示「AI 会员已生效」及服务端到期日期；允许提前输入新码续期。
- 到期或服务器判定无效时停止使用订阅代理，并提示续期。
- 保留现有「自定义 LLM 设置」作为 BYOK 高级模式。有效订阅默认优先使用订阅代理；没有有效订阅时，已配置的用户自有 API Key 仍可使用。
- 聊天记账、智核分析和 OCR 账单结构化都通过同一个 AI 访问解析器选择订阅代理或 BYOK，OCR 识别模型、图片选择和本地文字识别流程保持不变。

## 本地存储

- `installationId` 可存入 DataStore，不属于凭证。
- 当前测试兑换凭证及未来订阅访问令牌使用独立 Keystore 别名和独立私有 SharedPreferences 密文文件。
- DataStore 只保存用于离线展示的最后一次服务端到期时间；联网调用仍由服务端重新判断。
- 订阅令牌密文文件加入 Android 8-11 与 Android 12+ 备份排除规则。

## 兑换码管理

服务端项目提供管理员脚本生成高熵兑换码并将哈希写入数据库。脚本只在管理员本机运行，明文兑换码只输出一次，不进入 Git。测试阶段不增加 App 内管理员入口。

## 测试与验收

- 当前 APK 单元测试覆盖凭证在线验证、兑换 30 天、有效期叠加、本机重复凭证拒绝、过期状态、凭证哈希和订阅模式强制使用 CloudBase Base URL 与 `hy3`。
- Android 单元测试覆盖兑换状态、Keystore 存储接口、订阅优先/BYOK 回退和错误文案。
- UI 契约覆盖设置入口、兑换输入、成功到期日期和续期入口。
- 静态安全契约扫描 APK 配置与仓库，禁止出现 CloudBase API Key 或管理员密钥。
- Release 构建继续通过单元测试、Android 测试编译、Lint、签名与 OCR 四 ABI/模型检查。
- 端到端验收必须使用已轮换的新服务端 Key、实际部署的云函数和一次性测试兑换码；没有这些条件时只报告本地实现与编译结果。

## 独立交付阶段

1. 先完成并验证「学习与创作」类别，独立于订阅后端。
2. 本轮接入 Android 测试订阅、凭证在线验证、30 天本地权益和 AI 路由，生成同签名 APK；APK 不含任何 CloudBase API Key。
3. 后续创建并部署 CloudBase 代理、数据模型和短兑换码管理，用服务端令牌替换当前测试凭证，Android 权益 UI 与 30 天状态模型保持不变。
