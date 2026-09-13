# CHANGELOG

> 版本管理规范（2026-08-08 起强制执行）：
>
> - 每次交付：versionCode +1（永不回退），versionName 语义化（修复=修订+1，新功能=次版本+1）
> - 每次版本变更必须在此追加记录，并在 APK 文件名中携带版本号与日期

## 3.15.0 (versionCode 47) — 2026-09-13

**商户级异常基线（主动提醒「异常消费」第三级兜底）**

- **新增商户级异常基线**：总量与分类都被摊平时（总消费被其他分类/商户抵消），若同一商户（`note`）本周消费 ≥ 近 4 个完整周基线均值 ×1.5 且高出 ≥ ¥100，仍会提醒
- **层级顺序固定**：总量 > 分类 > 商户；同一级多个异常只报偏离金额最大的一个（不叠加、不改动既有分类级判定结果）
- **硬约束沿用**：≥4 个可比样本、冷启动不提醒、每日 1 条、异常类 7d 冷却、可逐类关闭均照旧
- **边界**：商户按 `note` 文本精确匹配（「瑞幸」与「瑞幸咖啡」不合并；空备注不参与商户级）；文案中商户名超过 12 字截断展示，不影响判定
- 规则阈值（1.5x / ¥100 / 4 样本）、冷却与评测数据集均未改动；`docs/proactive-insight.md` 同步

测试：398 → **402 个 JVM 单测全部通过**（+4：规则 3 / 引擎 1）。ProactiveInsightBench 38 条五项指标仍全 0%、文案覆盖率 100%（dataset sha 不变），`lintDebug` 通过。

## 3.14.2 (versionCode 46) — 2026-09-11

**第二轮深度审计修复（12 项，含 2 条 High）**

- **截图导入不再崩溃**：未配置 AI 服务（无订阅/无 Key）时返回明确错误提示，而非未捕获异常（`AppContainer` + `BillImportViewModel` 双层防护）
- **真实平台账单可导入**：官方导出的微信/支付宝 CSV 带前置元信息行，现在会定位真正的表头行再解析（`PlatformCsvImporter`）
- **续记日期不再漂移**：历史消息中的「昨天/前天」按该消息自身时间解析，而非按当前时间重算（`ChineseDateResolver` + `ChatLlmCoordinator` 传递消息时间戳）
- **指代词纯查询不再误入写路径**：查询规则先于代词型 MUTATION 判定（「这些一共花了多少」→ QUERY；「把刚才那笔删了」仍 MUTATION）
- **LlmClient 加固**：协程取消即断（enqueue + suspendCancellableCoroutine）、connect/read/call 超时、瞬时故障单次重试（400ms）、响应体 2MB 上限
- **OCR 内存**：派生位图懒创建 + finally 回收，峰值从最多 4 张大图降为 2 张
- **DataStore 抗损坏**：全部 delegate 增加 corruptionHandler；长期记忆 JSON 解码失败时保留原始数据、拒绝覆写
- **订阅页防崩**：Keystore 条目失效/读取异常不再导致页面崩溃，自动清理失效凭证
- **口语金额**：「23块5」「3块半」正确解析；「今天下午3.5元咖啡」不再被改写为 5 元
- **数据库索引**：新增 migration 5→6（expenses.occurredAt/deletedAt、chat_messages.createdAt）
- **CSV 去重修正**：软删除后可重新导入（去重基线改为活跃记录）
- **文档**：补充 force-stop（强制停止）后 WorkManager 调度语义说明

测试：372 → **398 个 JVM 单测全部通过**（+26，覆盖导入崩溃、preamble CSV、日期漂移、路由重排、HTTP 取消/重试/限长、金额口语、DataStore/Keystore 异常、软删重导等）。

## 3.14.1 (versionCode 45) — 2026-09-11

**深度审计修复（11 项报告 + 工程加固）**

崩溃 / 数据正确性：

- **历史页跨年崩溃**：LazyColumn key 从「M月d日」改为含年份的 `dateIso`，跨年同日不再抛 `Key was already used`（`HistoryViewModel`/`HistoryScreen`）
- **周期账单排期错误**：新增规则不再跳整月/整年；「31 号」在 2 月钳位后不会永久漂移到 28 号（改为完全由规则字段推导下一个周期点）
- **周期账单并发重复生成**：`runOnce` 加进程级互斥；单条坏规则失败不再中断整批（跳过并继续）
- **未来日期账目不再计入"本月/今天"**：预算总览、桌面组件、每日提醒文案、预算预警四处查询补上窗口上界（此前用 `Long.MAX_VALUE`）
- **提醒页配置被覆盖**：进入页面不再以默认值初始化（快照到达后回填、用户编辑优先、未加载时禁用保存）
- **备份恢复校验补全**：`recurringRules` 纳入校验（金额/分类/周期/日期/到期时间），坏备份整包拒绝
- **LLM JSON 解析**：多候选时跳过无关块并继续尝试，不再因第一个坏块丢单
- **会员页耗电**：健康轮询改为页面生命周期内运行（离开页面即停，不再 30s 后台轮询）
- **Locale 独立**：金额/百分比/时间格式化固定 `Locale.US`（含喂给 LLM 的 prompt），避免德语等区域出现逗号小数
- **记忆编辑反馈**：非法金额不再静默丢弃（弹窗内提示并保持打开）

工程：

- **正式签名基础设施**：`app/build.gradle.kts` 支持从 Gradle 属性/环境变量读取 keystore 凭据（缺省仍产出 unsigned，不回退 debug）；新增 `docs/release-signing.md`
- **CI 加固**：新增 `assembleRelease` 编译检查（占位订阅地址）、失败自动上传测试报告、最小权限；修复 `gradlew` 可执行位

测试：359 → **372 个 JVM 单测全部通过**（新增 13：解析器 2 / 历史 1 / 周期账单 6 / 备份 2 / Locale 2，含跨年、并发、损坏备份回归）。

## 3.14.0 (versionCode 44) — 2026-09-11

**主动提醒增强 + 体验/工程收尾（代码内一次性清账）**

新增：

- **分类级异常基线**：总消费被其他分类"抵消"时，单一分类本周 ≥ 近 4 周均值 ×1.5 且高出 ≥¥100 也会提醒（多分类同时异常只报偏离金额最大的一个；样本门槛与总量口径一致）
- **储蓄 pace 细化**：新增"剩余 N 天，最多还能花 ¥X（日均 ¥Y）"（剩余可支配额度 / 剩余天数），查询【储蓄进度】与主动提醒文案同步
- **提醒中心未读**：设置页「提醒中心」入口显示"· N 条未读"，进入页面自动已读；清空历史同时重置已读水位
- **启动检查**：App 启动时评估一次主动提醒（治理器每日额度/冷却兜底，重复启动不重复提醒）
- **Android 13 主题图标**：补 monochrome 层（笔记本 + 小花剪影）
- **CI**：GitHub Actions 运行单测 + lint（push dev/main、PR、手动触发）

清理：

- 桌面小组件文案全部迁入 `strings.xml`（lint HardcodedText 清零）

测试：356 → **359 个 JVM 单测全部通过**（分类异常 3 条）；`lintDebug` / `assembleDebug` / `assembleRelease` 通过。

## 3.13.2 (versionCode 43) — 2026-09-11

**修复：周期账单页同类截断问题（滚动排查第二轮）**

- `RecurringScreen`：规则多时列表无法滚动、底部"添加周期账单"按钮被挤出屏幕 → 列表区改为可滚动（`weight(1f) + verticalScroll`），按钮固定可见
- 至此全量扫描确认：所有内容型页面均已支持滚动（BrandSplash 无内容除外）
- 测试：JVM 356/356 不变

## 3.13.1 (versionCode 42) — 2026-09-11

**修复：预算 / 设置页内容超出屏幕且无法滚动（owner 真机反馈）**

- `BudgetScreen`：分类预算较多时底部被截断、保存按钮够不到（文件内导入了 `verticalScroll` 但未使用）→ 内容区接入 `verticalScroll(rememberScrollState())`
- `SettingsMenuScreen` / `DataExportScreen`：同类隐患一并修复（无滚动 + `weight(1f)` 底部占位改为固定间距，避免与滚动冲突）
- 测试：JVM 356/356 不变；release APK 重新签名并更新下载站

## 3.13.0 (versionCode 41) — 2026-09-11

**P2 主动提醒通知投递 + 提醒中心 / P3 储蓄节奏接入画像计算（纯本地验收）**

P2（主动提醒真正 proactive）：

- **系统通知投递**：独立渠道 `proactive_insight`；规则放行后聊天 🔔 + 通知栏双投递，点击通知直达提醒中心（`MainActivity` singleTop + intent extra）
- **提醒中心**（设置 → 📣）：历史最近 50 条（类型 / 严重度 / 最终文案 / 时间），可清空；存于 `ProactivePrefs`（独立 DataStore，排除系统备份与设备迁移）
- **后台检查**：WorkManager `PeriodicWorkRequest`（周期 1 天，首次延迟到 20:00，KEEP 不重置周期，重启恢复）；后台只用确定性文案、零网络；无通知权限时跳过评估且不消耗当日额度
- **`ProactiveEngine` 抽取**：前台展示与后台检查共用同一输入构建（账目 / 预算 / 已授权记忆）与治理器
- 通知权限：进入主动提醒页时若类型已开启但权限缺失，显示横幅"开启"入口；开关类型时也会请求（与每日提醒同款交互）

P3（消费端记忆进入计算）：

- 新增 `data/finance/SavingsPaceCalculator`（确定性）：预计消费 / 结余 / 目标缺口 / 剩余天数 / 达标日均上限
- 主动提醒储蓄规则改为共用该计算器（facts / copy 增加缺口与节奏信息），不再各自手算
- QUERY 轮：授权画像同时含月收入 + 储蓄目标时注入【储蓄进度】可信块（`savings_pace` 工具 + ExpenseAgent 注入），查询提示词明确不得自行推算

测试：339 → **354 个 JVM 单测全部通过**（新增 15：SavingsPace 5 / ProactiveEngine 4 / 规则 2 / 工具 2 / governor 历史 1 / ChatViewModel 投递 1）；ProactiveInsightBench 新增"放行必落历史、抑制零历史"校验，五项指标仍全 0%。

Owner 审阅修复（2026-09-11 二审）：

- **M2 月度窗口**：`ProactiveEngine` 上界 `Long.MAX_VALUE` → 下月初，未来日期账目不再计入本月（与 `AgentTools.savingsPace` 口径统一）
- **M3 并发 TOCTOU**：`ProactiveGovernor` 检查 + 落库移入进程级 `COMMIT_LOCK`，前后台并发不会重复提醒；新增并发回归用例
- **L4 权限获取路径**：主动提醒页在无权限且类型开启时显示横幅"开启"按钮（不再只有"关→开"才能触发请求）
- **L5 锁屏隐私**：通知 `VISIBILITY_PRIVATE` + 锁屏泛化文案（不再直接暴露金额）
- **L6 调度健壮性**：一次性任务自续 → `PeriodicWorkRequest`（KEEP），不再自我取消
- **I7 文档/清理**：README 主动提醒节标题更新；AGENT_PLAN 版本/测试数更新；移除未用 `ProactiveScheduler.cancel()` 与 `ProactiveGovernor.zone` 参数；记忆授权块标记改为 `MemoryReadPolicy.BLOCK_MARKER` 常量（消除评测器字面量耦合）

> 真实模型 3 轮复测与 LLM 文案真实复测均待 owner 指令。

## 3.12.2 (versionCode 40) — 2026-09-11

> **发布说明**：versionCode 40 未单独发布（工作区未提交期间直接进入 v3.13.0/v41），本条目随 v3.13.0 一并提交留档。

**多轮防重记 + 更新动作可靠性（P0/P1，纯本地验收；真实模型复测待 owner 指令）**

修复：

- **P0 多轮防重记（mt-02/03/16）**：历史对话里已确认记录的账目不得被再次提取
  - 提示词新增硬规则：历史中已记录（助手已回复"已记录"）及【你的记录】中已存在的账目禁止重复提取，只提取本轮新出现的消费；「还有/也买/又买/再记」表示追加新账目
  - 协调器上下文触发词补充 `还有/也买/又买/再买/再记/接着记`：追加表达同样加载已有账目上下文，模型可见"历史已记录"的账目
  - 新增示例：追加式表达只记本轮新账目
- **P1 更新动作可靠性（mt-15）**：把已有账目改到某个日期时模型必须产出 update
  - 提示词补全 update 动作字段（amount/category/note/occurred_at）并加规则：日期修改必须输出 update、occurred_at 填新 ISO 时间、禁止用新增一笔代替
  - 新增示例：修改已有账目的日期
  - 客户端维持确定性日期绑定：模型只给 `update + expense_id` 也能落到目标日期（回归用例覆盖）
- 两者均为提示词/上下文层修复，**未新增确定性去重兜底**（若真实模型复测仍不稳，再评估）

测试：334 → **339 个 JVM 单测全部通过**（新增 5：提示词契约 2 / 协调器攻击回归 3）。

> 真实模型 3 轮复测（新数据集 sha + 分类修正 + mt-02/03/16/mt-15）待 owner 指令执行。

## 3.12.1 (versionCode 39) — 2026-09-11

**分类口径修正（gold review 落地）**

- 记账提示词明确三类映射：文具/笔 → education；日用品/超市、理发/剪发 → shopping；水电/物业/燃气/宽带、酒店/住宿 → housing（账单导入提示词同步）
- 查询分类别名补充：文具（education）、理发/剪发（shopping）、酒店/住宿（housing）
- 测试数据集按 owner 裁定修正：mtp-29 理发 → shopping、mtp-30 酒店 → housing（裁定记录 `docs/gold-review-mtp-classification.md`）
- 按 owner 指示**未重跑 LLM Bench**；新数据集 sha 下的复测待下次运行验证（预期分类不一致 5 → 0–1）
- JVM 全量测试通过

## 3.12.0 (versionCode 38) — 2026-09-11

**Proactive Insight v1：规则决定该不该提醒，LLM 只负责怎么说（纯本地验收）**

新增：

- **三类主动提醒**：预算临界（90% / 超支）、异常消费（本周明显高于近 4 周个人基线）、储蓄目标偏离（月收入 + 储蓄目标 + 本月 pace 外推）
- **硬约束（治理层，LLM 无权参与）**：至少 4 个可比样本 / 冷启动不提醒 / 每日最多 1 条 / 同类冷却（预算 24h、异常与储蓄 7d）/ 预算 WARN→OVER 可升级突破冷却（hysteresis）/ 用户可逐类关闭
- **LLM 只允许改文案**：规则先产出结构化事实与确定性文案，copywriter 仅在规则触发后调用；失败/超长自动回退，不参与"是否提醒"
- **设置 → 🔮 主动提醒**：三类开关（默认开启）
- **ProactiveInsightBench**：38 条六桶；**False Alert / Missed Alert / Duplicate / Cold-start Violation / Notification Budget Violation 五项指标全部 0%**，文案覆盖率 100%

测试：322 → **334 个 JVM 单测全部通过**（新增 12：规则 5 / 治理 5 / Bench 2）。

> 真实 LLM Bench 仍暂停（owner 决定，纯本地验收）。

## 3.11.0 (versionCode 37) — 2026-09-11

**Memory Consumption & User Control：读有授权边界，用户可完整管理（纯本地验收）**

新增：

- **MemoryReadScope 读权限**：`NONE`（闲聊）/ `CLASSIFICATION`（记账只读商户别名）/ `FINANCIAL_ANALYSIS`（查询分析读月收入/储蓄目标/常用分类）；按轮次显式授权，整份画像永不无差别注入
- **别名确定性应用**：记账轮命中已授权别名时覆盖模型分类（「瑞幸15」→ 饮品）；"已知商户 + 裸金额"由此进入记账轮
- **设置 → 我的记忆**：查看（类型/摘要/来源/创建时间）、修改单条、删除单条、清空全部（二次确认）；删除后所有 Agent 路径不再读取
- **完整 JSON 备份 v3**：新增长期记忆 `memoryFacts`，v2 旧备份兼容解析；恢复字段校验不通过整包拒绝
- **MemoryConsumptionBench**：38 条六桶；三项核心指标 **Unauthorized Memory Read Rate = 0% / Deleted Memory Reuse Rate = 0% / Correct Memory Application = 100%**

测试：311 → **322 个 JVM 单测全部通过**（新增 11：读权限 4 / 管理 2 / 备份 3 / 消费 Bench 2）。

> 真实 LLM Bench 仍暂停（owner 决定，纯本地验收）。

## 3.10.0 (versionCode 36) — 2026-09-11

**Memory Governance v1：长期记忆只能经人类确认写入（纯本地验收）**

新增：

- **长期记忆四类**：`monthly_income` / `savings_goal` / `merchant_alias` / `category_preference`
- **治理路径**：用户表达 → Memory Proposal → 类型校验 → 预览 → Human Confirm → UserProfile；唯一持久化入口是 `MemoryGovernor.confirm`（人类确认），**LLM 不持有任何记忆写接口**
- **确定性提案**：本地检测（含金额邻近关键词校验、第三方转述拒绝）+ 独立类型校验（金额范围/分类合法性）；提案轮不调用模型、不写账目、不写记忆
- **确认 UI**：聊天页记忆确认弹窗（记住 / 取消）；取消与过期 token 均零写入
- **存储**：UserProfile 独立 DataStore（`user_profile_prefs`），排除系统备份与设备迁移
- **MemoryGovernanceBench**：36 条（四类正例 + 12 条拒绝集），首要指标 **Silent Memory Write Rate = 0%**

测试：295 → **311 个 JVM 单测全部通过**（新增 16：检测 7 / 治理 5 / Agent 拦截 2 / Bench 2）。

边界（v1）：只保存不消费（分析/建议暂不注入画像）；无画像管理页；预算等其余记忆类型后续版本。

> 真实 LLM Bench 仍暂停（owner 决定，纯本地验收）。

## 3.9.3 (versionCode 35) — 2026-09-11

**会话上下文路由：条件更正 / 续记 / Query 回承（纯本地验收）**

新增：

- **ConversationActionContext（轻量会话状态，非长期用户画像）**：记录 previousRoute / recentExpenseIds / previousMutationBatch / previousQueryPeriod / previousCategories，每轮结束后推进；ExpenseAgent 持有并驱动路由
- **条件更正语义**：只有「上一轮是记账 + 最近账目非空 + 更正词（记错了/说错了/不对/补充一下）」才升为 MUTATION；查询语境下「你这个分析不对」保持非记账
- **条件续记**：「今天也是35」只在上一轮明确消费语境（MUTATION）后记账，裸数字不再无条件写账
- **Query 回承**：「那上个月呢 / 那这周呢 / 再看下饮品」仅当上一轮是 QUERY 时继承 intent，当前句出现的 period/category 覆盖继承值
- 修复查询工具执行失败时回退到可写上下文的问题（现在回退也保持只读）

测试：288 → **295 个 JVM 单测全部通过**（新增路由正反例与 Agent 级会话用例 7 个）。

离线行为基线：前置路由 74/80 → **80/80**；Query 前置召回 14/16 → **16/16**；工具选择 16/16。

> 真实 LLM Bench 仍暂停（owner 决定，纯本地验收）。

## 3.9.2 (versionCode 34) — 2026-09-11

**本地安全加固（不依赖 LLM Bench）：CHAT 写防火墙 + Router 收敛 + 解析容错**

修复：

- **CHAT write firewall（P0）**：非 MUTATION 路径（查询/闲聊）在协调器层代码拒绝任何 expenses/actions，闲聊轮幻觉"已记录"也会被安全话术替换。至此**只有 MUTATION 能写库**（测试证明 applyPlan 零调用、数据库零修改）
- **Router 收敛**：
  - 裸金额支出（「午饭35」「今天咖啡18」「再记一笔：打车23」「花了23」）进入 MUTATION 结构化路径（此前落 CHAT，连带 Qwen 思考开关未显式关闭）
  - 非支出语义护栏（月薪/工资/年终奖/预算/欠/余额/标价/太贵/没变/第三方转述…）→ CHAT，由防火墙保证不落库
  - 查询召回补充：「花哪了」「哪个花得多」「那上个月呢」「那这周呢」「再看下饮品」
- **纯日期 occurred_at 容错**：模型只回 `"2026-09-05"` 时按本地当天 00:00 解析，不再整批拒绝（真实评测 mtp-15/mt-12 数据丢失根因）
- **partial hints**：原文只有部分金额带「元/块」时，已标注金额必须精确出现（防漏记/防重复），未标注的额外提取允许通过，唯一标注金额仍强绑定日期；修复 mtp-27 正确的 3 笔被整批拒绝
- 离线行为基线：前置路由 58/80 → **74/80**，Query 前置召回 9/16 → **14/16**，工具选择 14/14（`docs/expensebench-v2-local-report.md`）

测试：278 → **288 个 JVM 单测全部通过**（新增 10：Router 3 / 纯日期 1 / partial hints 3 / CHAT 防火墙 3）。

> 真实 LLM Bench 本批暂停（owner 决定，纯本地验收）；后续有免费额度或明确指令时再跑小规模 smoke。

## 3.9.1 (versionCode 33) — 2026-09-10

**Reliability Hardening：生产行为与 Bench 结论、README 声明对齐**

修复：

- **生产 Qwen 结构化请求未关闭思考（P0）**：MUTATION / QUERY / Intent Escalation / 智核分析 / 账单导入现在显式发送 `enable_thinking=false`（仅 Qwen 思考模型；其他供应商保持字段缺省）。此前只有 Bench 显式关闭，生产调用沿用供应商默认；思考模式的长思维链会截断结构化 JSON（Bench：整笔全对 89.1% → 74.6%）
- **QUERY 轮真正只读**：`ChatTurnContext.allowMutations=false`，模型返回的 expenses/actions 在进入变更计划器之前被代码层丢弃；新增攻击回归测试——工具结果 note 注入"删除所有账目"诱导模型返回删除动作，数据库零修改
- **analyze_expenses 两处计算错误**：月底预测排除投资类（与消费合计口径一致）；分类趋势集合改为"本期 ∪ 上期"，上月有、本月 0 的分类可以显示"已清零"；各补 1 个 regression test
- **Intent Escalation 后查询参数丢失**：升级判定为 analysis 后从原句重新解析 period / category / budget（此前复用升级前的空 CHAT 决策，"我最近吃饭是不是花多了"会退化成"本月所有消费"）
- **治理与隐私文案对齐真实行为**：README / 软件说明书明确"AI 新增/修改直接执行、删除必须确认、查询只读"；"永不上云"改为"财务数据本地持久化，启用云端 LLM 时当前请求上下文发送给用户配置的模型供应商"；API Key 描述从 DataStore 改为 Android Keystore（AES-256/GCM）

测试：新增 11 个单测（思考策略 / 请求体序列化 / 只读拦截 / 攻击用例 / 升级参数 / 预测与趋势回归），全套 268 个 JVM 单测通过。

## 3.9.0 (versionCode 32) — 2026-09-06

**两级路由 + analyze_expenses：Agent 从"能查"到"能分析"**

新增：

- `analyze_expenses` 工具：本期 vs 上期环比（日历对齐，整月/整周/单日各自对齐，不丢天）、分类趋势（按 |环比变化| 降序）、按日均 pace 的月底预测；环比分母为零输出"新增支出"而非 +∞%，样本不足 7 天或非整月区间不预测，预测强制携带推算依据（daily_average）
- Intent Escalation 两级路由：规则快路径不命中但句子带分析特征（是不是/要不要/花太多/帮我看看…）时，才发一次轻量 LLM 调用（独立小协议 `{"intent","requires_tools","tool_candidates"}`），判为分析意图才走工具轮；升级失败/判为 chat/record 一律回退原管线，记账协议零污染
- 查询轮默认同时注入 query_expenses + analyze_expenses（+按需 get_budget_status），「比上个月多花了吗」这类问题直接可答
- 评测可复现协议：`ChatCompletionRequest.temperature` 改为可空（null=不发送，生产行为不变；线上此前从未发送该字段），LLM Bench 显式固定 temperature=0.0，报告记录 dataset_sha256 / prompt_sha256 / ran_at
- 新增 15 个测试（analyze 数学保护、升级触发与解析、升级流转、temperature 线上行为），全套 257 个单测通过

## 3.8.0 (versionCode 31) — 2026-09-06

**Expense Agent v1：从 AI 解析器升级为带工具的智能体**

新增：

- Agent 层（`agent/` 包）：`AgentRouter` 本地确定性意图路由（记账/删改/闲聊 → 原管线；查询 → 工具轮），零成本零延迟
- 本地工具：`query_expenses`（区间+分类过滤、合计、分类/商户聚合、明细）、`get_budget_status`（月度+分类预算 vs 实际，端侧确定性计算）
- 查询轮把工具结果注入 system prompt，LLM 只负责组织语言 —— 数字来自本地数据库，不可能被编造；「这个月吃饭花多少」「预算还剩多少」现在能直接在对话里回答
- 查询轮关闭"虚假记账话术"替换，合法回复（含"已记录 X 笔"）不再被误改
- ExpenseBench v1：120 条标注数据集（basic / date_relative / multi / merchant / colloquial / explicit_date 六桶）+ 评测器（金额/分类/日期/商户/整笔全对/笔数全对）+ 端侧离线管道报告（`docs/expensebench-local-report.md`）+ LLM 评测入口（`LlmExpenseBenchTest`，设 `EXPENSEBENCH_API_KEY` 等环境变量按需运行，报告写 `docs/expensebench-llm-report.md`）
- 新增 25 个单元测试（路由/时段/工具/Agent 端到端/评测器），全套 242 个单测通过

## 3.7.2 (versionCode 30) — 2026-08-13

修正产品规则：AI 修改账目直接执行，只有删除账目需要用户确认。保留 v3.7.1 的单日期金额/笔数校验、逐笔日期时分锁定、桌面组件异步刷新及 Android 8 周期账单兼容修复。

## 3.7.1 (versionCode 29) — 2026-08-13

**AI 记账安全与桌面组件稳定性修订**

修复：

- AI 修改账目保持直接执行；只有删除账目必须先确认，取消时不会写入数据库
- 单日期批量口述也启用原文金额与笔数校验，模型漏记、重复或调换金额时拒绝落库
- 明确口述的逐笔时分由客户端确定性解析并覆盖模型猜测，不再跨笔继承时间
- 桌面组件的 Room / DataStore 查询移到 IO 协程，前台刷新改为显式组件广播，避免主线程阻塞

保持：OCR 识别链路、模型与图片处理能力未修改。

## 3.7.0 (versionCode 28) — 2026-08-08

**五功能大版本：预算 / 记账提醒 / 周期账单 / 桌面组件 / 平台 CSV**

新增：

- 预算管理：月度总预算 + 分类预算；本月支出达到预算 90% 黄色预警、超支红色提醒；分析页实时预算卡片；记账后聊天区自动预警
- 记账提醒：每日定时补记提醒（系统通知，含当日已记笔数与金额）；测试提醒按钮；重启自动恢复调度
- 周期账单：房租 / 订阅 / 工资等固定账单到期自动记账，防重复生成；管理页支持增删改与启停
- 桌面组件：本月支出 + 预算进度 + 快捷记账入口，回到前台自动刷新
- 微信 / 支付宝账单 CSV 导入：自动识别平台格式、只导入支出、退款与失败行跳过、商户关键词自动分类、GBK/UTF-8 编码自适应
- 完整备份现包含周期账单规则（旧备份文件兼容）

修复：

- 支付宝 CSV 中「退款成功 / 部分退款 / 交易关闭」不再被误导入为支出
- 无日期记账强制使用当前时间（此前可能被模型旧日期污染）

数据库：v4 → v5（新增 recurring_rules 表，迁移已验证，旧数据无损）

验证：195 单元测试 + 14 仪器测试全过；订阅凭据 / 备份规则 / 隐私日志安全契约 PASS

## 3.6 (versionCode 27) — 2026-08-03 ~ 08-08

> 说明：此版本号下曾交付多个 APK（订阅兑换、AI 日期安全、OCR 通用识别、微信 CSV），
> 版本号未随迭代递增，为历史遗留；自 3.7.0 起恢复规范版本管理。

- 2026-08-03 一次性兑换码会员（服务端核销 + AI 代理）
- 2026-08-03 AI 日期与批量操作安全（有界历史、确定性日期解析、批次边界、仅删除确认）
- 2026-08-06 解析速度优化（记录上下文 200→30、历史消息截断、打字机 3 倍速）
- 2026-08-06 时间修复（无日期记账强制当前时间；修改不改日期）
- 2026-08-06 AI 会员页服务器状态与服务器时间显示
- 2026-08-06 通用 OCR（中英双识别器、深色反色、EXIF 旋转、对比度增强）
- 2026-08-07 微信 / 支付宝账单 CSV 导入

## 3.5 (versionCode 23–26) — 2026-07-29 ~ 08-01

- 深色模式（浅色 / 深色 / 跟随系统）
- 品牌紫色开屏
- 中文 OCR 截图导入（ML Kit）
- 数据安全加固（整数分存储、Keystore 密钥、隐私日志、软删除回收站）
- 智核分析独立页面与图表体系

## 3.4 及更早

- 对话式 AI 记账、分类统计图表、CSV/JSON 导入导出、模板快速记账等（详见 git 历史）
