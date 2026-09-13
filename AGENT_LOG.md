# AGENT_LOG.md — 2026-08-06 opencode 接管执行记录

## 时间线
1. **审计**：定位项目（expense-tracker）、v3.6 工作区未提交状态（116 文件）、GitHub origin/dev 停在 2026-07-04
2. **问题定位**：
   - 解析慢：ChatLlmCoordinator MAX_RECORD_CONTEXT=200 + LlmClient 10×4000 历史 + 打字机 22ms/字
   - 确认框：LlmMutationPlanner.kt requiresConfirmation = 有动作或>1 笔新增
   - 服务器：腾讯云实测 /health 200；GitHub 未推送是"时间停住"主因
   - 时间停 8.1：模型在无日期输入时带出历史旧日期（occurred_at），planner 直接采信
3. **修复**：LlmMutationPlanner 时间兜底 + 仅删除确认；性能三处；云函数 /health serverTime + 会员页服务器状态卡片
4. **测试**：修复 InvalidTestClassError（containsExactly 返回 Ordered）、runTest 死循环（healthPollEnabled 开关）、批量新增测试改语义；最终 164/164 通过
5. **Git**：快照提交 09d7374 → merge 远程 4 个 commit（12 冲突以本地为主）f4431ff → 清理远程遗留 10b19ee → 推送成功
6. **构建签名**：assembleRelease + zipalign + apksigner（debug 证书，与上版一致）→ 交付 APK
7. **网站**：page.tsx/build 脚本更新到 8.6 APK，cloudbase-dist 构建成功；云函数 /health 加 serverTime
8. **踩坑**：opencode claude-hooks.js 拦截 git commit（prettier 缺失）；runTest 虚拟时钟死循环；Kotlin @Test 返回类型；PowerShell 编码（沿用已知经验）

## 遇到的坑（详见 大局复利踩坑日记.md 2026-08-06 五条）
- claude-hooks.js 插件导致 git commit 永远失败（$ is not a function）
- runTest + while(true)+delay 轮询挂死测试
- Truth containsExactly 使 @Test 返回 Ordered → InvalidTestClassError
- 模型日期污染（用户时间"停住"根因）
- Gradle 残留 daemon 进程导致 UP-TO-DATE 假象

## 下一步
- 大统领 tcb login 后部署云函数 + 下载站
- 实体手机覆盖安装复测

---

# AGENT_LOG.md — 2026-09-10 v3.9.1 Reliability Hardening（opencode）

## 范围（owner 下发的 6 条验收，全部达成）
1. 结构化 Qwen 请求 thinking=false（MUTATION/QUERY/Escalation/智核分析/账单导入，仅 Qwen；CHAT 保持供应商默认）
2. QUERY 代码层只读（ChatTurnContext.allowMutations=false，含工具结果注入攻击回归）
3. analyze_expenses 两处计算修复（预测排除投资；趋势取本期∪上期显示"已清零"）
4. Escalation 后重解析 period/category/budget（不再复用空 CHAT 决策）
5. README / 软件说明书与真实行为一致（新增/修改直接执行、删除确认；云端 LLM 上下文披露；API Key = Android Keystore）
6. 全量 JVM tests green：257 → 268，0 failed

## 记录
- 变更、决策与技术选型全文：`docs/v3.9.1-reliability-hardening.md`
- 新增 11 个单测：LlmThinkingPolicyTest / LlmClientThinkingTest / ChatLlmCoordinatorTest / ExpenseAgentTest / AgentToolsAnalyzeTest

---

# AGENT_LOG.md — 2026-09-10 ExpenseBench v2 / AgentBehaviorBench（opencode）

## 交付
- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，110 条 × 4 桶（negative_false_positive 30 / multi_temporal 30 / router_ambiguous 30 / multi_turn 20）
- 评测器：`AgentBehaviorEvaluator` — Router / Query Recall / Mutation Precision / **False Mutation Rate** / Tool Selection / Expense Count / Date Binding / E2E
- 离线层：`LocalAgentBehaviorBenchTest` — 数据集完整性 + 前置路由与工具基线 + 多日期逐笔绑定回归 → `docs/expensebench-v2-local-report.md`
- LLM 端到端层：`LlmAgentBehaviorBenchTest` — 真实模型 + 真实 Router/Escalation/Tools/Planner/Applier + 内存数据库（env-gated）
- 观测钩子（生产 no-op）：`AgentToolContext.onToolCall`、`ExpenseAgent.onRouteResolved`
- 协议：`docs/expensebench-v2.md`；README 增补 v2 段
- 仓库卫生：`AGENT_PLAN.md` 重写为当前执行线；`KNOWN_ISSUES.md` 复核；`docs/expensebench.md` §7 指向 v2

## 离线基线发现（2026-09-10）
- 前置路由 58/80（Escalation 依赖项不在离线口径）；工具选择 9/9；多日期绑定回归通过
- 首要发现：无元/块的裸金额（如「午饭35」）不进入 MUTATION 结构化路径 → 连带 Qwen 思考开关未显式关闭（v3.9.2 候选 #1）
- 其他缺口：更正触发词（记错了/不对/说错了）、查询回承（那上个月呢/那这周呢/再看下饮品）

## 测试
- 全量 JVM：**278 passed / 0 failed**（v3.9.1 时 268；v2 新增 10）

## owner 判断（本阶段方向，由 Yenefa 定义）
- 阶段定性：从"模型提取准确率"升级到"Agent 行为可靠性评测"
- 首批只做 4 桶 110 条，不直接冲 500；失败类型比数量重要
- 首要指标 False Mutation Rate，目标 0%（"漏回答可以接受，凭空记一笔非常恶心"）
- v2 不能只测 LLM：必须新增 AgentBehaviorBench，测完整链路的数据库终态
- Bench v2 必须先于 Memory Governance（"我月收入8000"不能被识别成 ¥8000 支出）
- 顺手处理仓库卫生：AGENT_PLAN 过时、KNOWN_ISSUES 待复核

## 首轮真实评测（2026-09-11，qwen3.7-flash @ DashScope，3 轮）
- 报告：`docs/expensebench-v2-llm-report-qwen3.7-flash.md`（含逐条失败明细）
- 归因：`docs/expensebench-v2-triage-2026-09-11.md`
- 关键数字：**False Mutation Rate 1.9% / 5.6% / 5.6%**（目标 0%，未达标，且 temperature=0 下仍有波动）；
  E2E 75.5% / 72.7% / 72.7%；Query Recall 68.8%；Date Binding 92.0% / 87.4% / 86.2%
- 假变更样本：nfp-03 年终奖3万到账 / nfp-21 房租还是2500没变 / nfp-23 信用卡欠12000 / nfp-29 标价899（均裸金额、CHAT 路径）
- 数据丢失样本：mtp-15、mt-12（模型返回纯日期 `occurred_at` → parser 拒整批）；mtp-27（hints 误杀正确提取）
- bench 工具升级：报告新增"失败明细"（`AgentBehaviorEvaluator.describeFailures`），每条失败可归因

## 待 owner
- 依据归因记录定义 v3.9.2 范围（候选：假变更治理 P0 → 纯日期容错 → hints 误杀 → 上下文触发词 → 查询回承）；修完重跑 3 轮对比

---

# AGENT_LOG.md — 2026-09-11 v3.9.2 本地安全加固（opencode）

## 范围（owner：纯本地，不跑 LLM Bench）
- **CHAT write firewall**：非 MUTATION 路径（查询/闲聊）在协调器层代码拒绝 expenses/actions；幻觉"已记录"替换为安全话术；至此只有 MUTATION 能写库
- **Router 收敛**：裸金额支出（午饭35/咖啡18/再记一笔/花了23）→ MUTATION；非支出护栏（月薪/工资/年终奖/预算/欠/余额/标价/太贵/没变/第三方转述…）→ CHAT；查询召回补充（花哪了/哪个花得多/那…呢/再看下）
- **纯日期 `occurred_at` 容错**：LocalDate → 本地当天 00:00，不再整批拒绝
- **partial hints**：标注金额精确出现（防漏记/重复）+ 唯一标注强绑定日期 + 未标注额外提取放行

## 验收（本地）
- JVM 单测 278 → **288，0 failed**（新增 10：Router 3 / 纯日期 1 / partial hints 3 / CHAT 防火墙 3）
- 离线行为基线：前置路由 58/80 → **74/80**；Query 前置召回 9/16 → **14/16**；工具选择 14/14
- 防火墙证明：闲聊幻觉与查询注入两类用例下 `applyPlan` 零调用、数据库零修改

## 决定
- 真实 LLM Bench 暂停，不再自动三轮全量；待免费额度/替代模型/明确指令先跑 smoke
- 未做（后续候选）：分类灰区 gold 复核；Memory Governance 继续后压

---

# AGENT_LOG.md — 2026-09-11 v3.9.3 会话上下文路由（opencode）

## 范围（owner：纯本地，不新增裸关键词 MUTATION 规则）
- **ConversationActionContext**（轻量会话状态，非长期画像）：previousRoute / recentExpenseIds / previousMutationBatch / previousQueryPeriod / previousCategories，每轮结束推进
- **条件更正**：上一轮记账 + 最近账目非空 + 更正词（记错了/说错了/不对/补充一下）→ MUTATION；"你这个分析不对"（查询语境）保持非记账
- **条件续记**：「今天也是35」仅在上一轮 MUTATION 语境后记账
- **Query 回承**：「那上个月呢 / 那这周呢 / 再看下饮品」仅当上一轮 QUERY 时生效，继承 intent，当前句 period/category 覆盖
- 修复：查询工具失败回退到可写上下文的问题（现在回退也保持只读）

## 验收（本地）
- JVM 单测 288 → **295，0 failed**（新增路由正反例 4 组 + Agent 级会话用例 3 个）
- 离线行为基线：前置路由 74/80 → **80/80**；Query 前置召回 14/16 → **16/16**；工具选择 16/16
- 正反例：查询后「不对」不触发改账；无最近账目「记错了」不升 MUTATION；无上一轮 QUERY「那上个月呢」不继承
- 数据集：multi_turn 用例新增 `previous_route` gold 用于上下文模拟

## 决定
- 真实 LLM Bench 仍暂停；待免费额度/替代模型/明确指令
- 未做（后续候选）：分类灰区 gold 复核

---

# AGENT_LOG.md — 2026-09-11 v3.10.0 Memory Governance v1（opencode）

## 范围（owner：长期记忆与会话状态分离；LLM 不得从闲聊写记忆）
- 四类长期记忆：monthly_income / savings_goal / merchant_alias / category_preference
- 路径：用户表达 → Memory Proposal（本地确定性检测）→ 类型校验 → 预览 → Human Confirm → UserProfile
- 唯一写入口 `MemoryGovernor.confirm`；提案阶段零写入、零 LLM 调用、零账目写入
- UI：聊天页确认弹窗（记住/取消）；取消/过期 token 零写入
- 存储：`user_profile_prefs` DataStore，排除系统备份与设备迁移（backup_rules/data_extraction_rules）

## Bench（MemoryGovernanceBench v1，36 条）
- 四类各 6 + 拒绝集 12；报告 `docs/memory-governance-local-report.md`
- **Silent Memory Write Rate = 0.0%（0/36）**；提案准确率 36/36；路由 12/12；确认后持久化 24/24；提案轮 LLM 调用 0
- 拒绝集包含：午饭35（正常记账）、预算、负债、估值、第三方收入、年终奖、无效别名分类等

## 验收（本地）
- JVM 单测 295 → **311，0 failed**（新增 16：检测 7 / 治理 5 / Agent 拦截 2 / Bench 2）

## 决定 / 边界
- v1 只保存不消费（分析/建议不注入画像）；无画像管理页
- 后续候选：画像管理页、消费端注入、预算等扩展类型，再进 Proactive Insight

---

# AGENT_LOG.md — 2026-09-11 v3.11.0 Memory Consumption & User Control（opencode）

## 范围（owner：读也要有权限边界；用户必须能管理记忆）
- **MemoryReadScope**：NONE / CLASSIFICATION / FINANCIAL_ANALYSIS；按轮次授权注入，整份画像不无差别进 prompt
  - MUTATION 轮只读商户别名，并确定性应用（note 命中商户 → 覆盖分类；「瑞幸15」→ 饮品）
  - QUERY 轮才读月收入/储蓄目标/常用分类；CHAT 轮不读
  - 「已知商户 + 裸金额」进入记账轮（分类读权限的路由联动）
- **我的记忆**（设置入口）：查看（类型/摘要/来源/创建时间）、修改单条（重新过类型校验）、删除单条、清空全部
- **JSON 备份 v3**：`memoryFacts` 入包；v2 旧备份兼容（记忆为空）；恢复字段校验
- 系统备份/设备迁移继续排除 `user_profile_prefs`

## Bench（MemoryConsumptionBench v1，38 条）
- 六桶：alias_application 8 / analysis_reads 8 / preference_analysis 4 / unauthorized 8 / deleted_reuse 6 / no_memory 4
- 报告 `docs/memory-consumption-local-report.md`
- **Unauthorized Memory Read Rate = 0%（0/38）**；**Deleted Memory Reuse Rate = 0%（0/6）**；Correct Memory Application = 100%（25/25）；路由 38/38

## 验收（本地）
- JVM 单测 311 → **322，0 failed**（新增 11：读权限 4 / 管理 2 / 备份 3 / 消费 Bench 2）

## 决定
- 安全写 + 安全读 + 可管理 三项闭环完成，**下一步才允许进入 Proactive Insight**
- 边界：分析类记忆只作上下文不参与计算；无冲突合并

---

# AGENT_LOG.md — 2026-09-11 v3.12.0 Proactive Insight v1（opencode）

## 范围（owner：规则决定"该不该提醒"，LLM 只负责"怎么说"）
- 三类：预算临界（90%/超支）、异常消费（本周 ≥ 近 4 周基线 ×1.5 且高出 ≥¥100）、储蓄目标偏离（收入+储蓄+pace 外推）
- 硬约束：≥4 可比样本 / 冷启动不提醒 / 每日最多 1 条 / 同类冷却（预算 24h、异常/储蓄 7d）/ WARN→OVER 升级可突破冷却 / 用户可逐类关闭
- LLM 边界：copywriter 只在规则触发后调用；超长/换行/失败回退确定性文案；不参与决策
- 开关 UI：设置 → 🔮 主动提醒（3 类，默认开）
- 投递：记录成功后聊天内追加 "🔔 …"（系统通知栏为后续版本）

## Bench（ProactiveInsightBench v1，38 条六桶）
- 报告 `docs/proactive-insight-local-report.md`
- **False Alert 0%（0/24）/ Missed 0%（0/14）/ Duplicate 0%（0/5）/ Cold-start 0%（0/6）/ Budget Violation 0%（0/5）**；文案覆盖率 100%

## 验收（本地）
- JVM 单测 322 → **334，0 failed**（新增 12：规则 5 / 治理 5 / Bench 2）

## 结果
- 完整链路成型：Deterministic Routing → Short-term Context → Governed Memory → Scoped Memory Read → Deterministic Finance Tools → Controlled Mutation → Rule-governed Proactive Insight
- 后续候选：系统通知投递与提醒中心、分类级异常基线、LLM 文案真实复测

---

# AGENT_LOG.md — 2026-09-11 真实模型复测（owner 指令，opencode）

## 范围
- `LlmAgentBehaviorBenchTest`，qwen3.7-flash @ DashScope，110 条 × 3 轮全量（temperature=0，enable_thinking=false）

## 结果（加固前 → 加固后）
- **False Mutation Rate：1.9%/5.6%/5.6% → 0.0%/0.0%/0.0%（0/54）**
- Router 75% → **100%（80/80）**；Query Recall 68.8% → **100%（16/16）**；Tool 79.2% → **100%（24/24）**
- Date Binding 86–92% → **98.9%（86/87）**；E2E 72.7–75.5% → **90.9–91.8%**；请求失败 4–8 条 → **0 条**
- 剩余 9/110：分类灰区 gold 5 + 多轮重复记录 3（mt-02/03/16）+ 更新未产出 1（mt-15）
- 报告：`docs/expensebench-v2-llm-report-qwen3.7-flash.md`；总结：`docs/expensebench-v2-verification-post-hardening.md`

## 决定
- 安全验收线（FMR 0% 连续 3 轮）达成；剩余问题与写安全无关
- 后续候选（优先级）：分类灰区 gold 复核 → 多轮历史去重/最近批次约束 → 更新动作可靠性 → 系统通知投递

---

# AGENT_LOG.md — 2026-09-11 v3.12.1 分类口径修正（owner 裁定）

- Owner 裁定：笔=学习、酒店=住宿、理发=生活开支（映射到现有分类 shopping）
- 落地：提示词补 3 组映射（主链路 + 账单导入同步）；AgentCategories 别名补文具/理发/酒店住宿；数据集 mtp-29 → shopping、mtp-30 → housing
- 记录：`docs/gold-review-mtp-classification.md`；未改评分器
- 按 owner 指示未重跑 LLM Bench；新数据集 sha 下复测待下次运行（预期分类不一致 5 → 0-1）
- 测试：JVM 334/334（含更新后的 prompt contract）

---

# AGENT_LOG.md — 2026-09-11 v3.12.2 多轮防重记 + 更新动作可靠性（opencode，owner P0/P1）

## 范围（按 owner 下发的方案，不扩权）
- **P0 多轮防重记（mt-02/03/16）**：提示词硬规则"历史中已确认记录的账目不得重复提取，只提取本轮新出现的消费"；`ChatLlmCoordinator.EXISTING_RECORD_INTENT` 扩展 `还有/也买/又买/再买/再记/接着记`（追加表达加载已有账目上下文）；新增"追加式表达"示例
- **P1 更新动作可靠性（mt-15）**：update 动作 schema 补全 `note/occurred_at`；规则"把已有账目改到某日期必须输出 update、occurred_at 为新 ISO 时间、禁止新增代替"；新增"修改日期"示例
- 客户端日期绑定保持确定性：模型只给 `update + expense_id` 时仍由 `ChineseDateResolver` + Planner 落到目标日期（回归覆盖）
- **未加确定性去重兜底**（owner 计划：先 prompt/上下文，真实模型仍不稳再评估）

## 攻击/回归用例（新增 5）
- `LlmPromptContractTest`：防重记规则契约 + 日期修改 update 契约
- `ChatLlmCoordinatorTest`：
  - mt-02 场景（seed 打车50 + 历史"已记录" + 提示词含已有账目行）→ 只新增奶茶，5000 不重复
  - mt-02/03/16 + 又买：全部触发词都加载已有账目上下文（防止触发词被静默移除）
  - mt-15 场景（模型仅输出 update ID，无 occurred_at）→ 日期确定性落 2026-09-04 且保持 15:00

## 验收（本地）
- JVM 单测 334 → **339，0 failed**
- 未跑 LLM Bench（owner：真实模型复测议题暂且搁置）；恢复时用新数据集 sha 跑 3 轮，预期分类错 5 → 0-1、mt-02/03/16 → 0

---

# AGENT_LOG.md — 2026-09-11 v3.13.0 主动提醒投递 + 提醒中心 + 储蓄节奏入计算（opencode，owner P2/P3）

## 范围（owner 下发 P2/P3，纯本地；不动规则阈值与数据集）
- **P2 系统通知投递**：`ProactiveNotifier`（独立渠道 `proactive_insight`、点击直达提醒中心）；前台规则触发时聊天 🔔 + 通知栏双投递
- **P2 提醒中心**：治理器放行即写历史（`ProactiveStateStore.record` 增 copy 参数，ProactivePrefs 同一事务落 JSON，最近 50 条）；设置 → 📣 提醒中心查看/清空；存于独立 DataStore 并排除系统备份/设备迁移
- **P2 后台检查**：`ProactiveInsightWorker` + `ProactiveScheduler`（WorkManager 每日 20:00 自续、重启恢复）；后台 `copywriter=null`（确定性文案、零网络）；无通知权限时跳过评估、不消耗当日额度
- **P2 输入构建抽取**：`ProactiveEngine`（账目窗口 / 预算 / FINANCIAL_ANALYSIS 授权记忆），前台 AppContainer 与后台 Worker 共用
- **P3 储蓄节奏**：新增 `data/finance/SavingsPaceCalculator`；主动储蓄规则改共用；QUERY 轮注入【储蓄进度】可信块（`AgentTools.savingsPace` + `ExpenseAgent`），提示词禁止自行推算
- 边界：规则阈值 / 冷却 / 数据集均未改；通知权限缺失降级为聊天 + 提醒中心

## 验收（本地）
- JVM 单测 339 → **354，0 failed**（新增 15：SavingsPace 5 / ProactiveEngine 4 / 规则 2 / 工具 2 / governor 历史 1 / ChatViewModel 投递 1）
- ProactiveInsightBench 38 条新增"放行必落历史、抑制零历史"校验；五项指标仍全 0%
- MemoryConsumptionBench 删除复用仍 0%（修正：查询指令文案避免出现记忆标记词，防误判）
- 离线行为基线保持 80/80、工具 16/16；`assembleDebug` 成功；`lintDebug` 通过（顺带修复 v3.12.0 遗留 `LocalDate.ofInstant` NewApi 错误）

## 决定 / 后续
- 真实模型 3 轮复测（新数据集 sha、分类修正、mt-02/03/16、mt-15）与 LLM 文案真实复测待 owner 指令

---

# AGENT_LOG.md — 2026-09-11 v3.13.0 二审修复 + 补提交（opencode，owner 审阅 findings）

## H1 治理处置
- 问题：HEAD=5903d1a 仍为 v3.12.1（v39），工作区 36 改 + 9 新直接到 v41；CHANGELOG 的 v3.12.2/v40 无可追溯提交
- 处置：P0–P3 合并为一个 v3.13.0（v41）提交，CHANGELOG 注明 v40 未单独发布；推送 origin/dev

## 二审修复（逐条对应 owner findings）
- **M2（正确性）**：`ProactiveEngine` 月度窗口上界 `Long.MAX_VALUE` → 下月初，与 `AgentTools.savingsPace` 统一；新增"未来月份账目不计入"回归
- **M3（并发）**：治理"检查 + 落库"移入进程级 `COMMIT_LOCK`（前台/后台单进程共享）；新增并发回归（用挂起的 copywriter 复现原 TOCTOU 窗口，断言只提交一次）
- **L4（体验）**：主动提醒页在"类型已开启但无通知权限"时显示横幅 + "开启"入口（原先只有关→开才触发请求）
- **L5（隐私）**：通知 `VISIBILITY_PRIVATE` + 锁屏泛化文案；渠道 `lockscreenVisibility=PRIVATE`
- **L6（健壮性）**：一次性任务自续（REPLACE 自我取消）→ `PeriodicWorkRequest`（1 天，KEEP 不重置）；移除未调用 `ProactiveScheduler.cancel()`
- **I7（文档/清理）**：README 主动提醒节标题（v3.12→v3.13）；AGENT_PLAN 版本/测试数（v3.9.1/278 → v3.13.0/354+）；移除 `ProactiveGovernor.zone` 未用参数；`MemoryReadPolicy.BLOCK_MARKER` 常量供评测器复用（消除字面量耦合）

## 验收（本地）
- JVM **356，0 failed**（354 + M2/M3 回归 2）；`lintDebug` / `assembleDebug` 通过

---

# AGENT_LOG.md — 2026-09-11 v3.13.0 下载站重建与部署（opencode，owner 指令）

## 背景
- 旧下载站源码不在本仓库/本机（线上 `/ye-cost` 停留在 v3.7.2 / 2026-08-13）
- owner 指令：重建单页下载站，沿用品牌样式，部署到同一 CloudBase

## 交付
- `website/`（纯静态单页，无构建链、无外链依赖）：沿用品牌图标与深紫 `#2B1A3B`、白底；
  更新 v3.13.0 发布说明、APK 规格与 SHA-256 复制、隐私边界；`website/downloads/*.apk` 不入库
- 部署：CloudBase env `ilove-d5g0gzrpp375112b9` → 路径 `/ye-cost/`
- APK：`Y.E-cost-v3.13.0-2026-09-11.apk`（55,353,801 bytes，SHA-256 `ecf7c1d0…`，与本地签名包一致）

## 线上验证
- `https://ilove-d5g0gzrpp375112b9-1413557923.tcloudbaseapp.com/ye-cost/`：index / icon / apk 均 200；APK Content-Length 与哈希一致
- 旧版 APK 保留在 `/ye-cost/downloads/`（不破坏旧链接）；误传到 `A:/Git/Git/ye-cost` 的副本已删除

## 坑
- Git Bash（MSYS）会把 `/ye-cost` 转成 `A:/Git/Git/ye-cost`；`tcb hosting deploy/delete/list` 必须加 `MSYS_NO_PATHCONV=1`（已写入本记录）

---

# AGENT_LOG.md — 2026-09-11 v3.13.1 滚动显示修复（opencode，owner 真机反馈）

## 问题
- owner 截图：预算管理页分类列表超出屏幕、底部被截断且**无法滚动**（保存按钮够不到）
- 根因：`BudgetScreen` 内容区缺少 `verticalScroll`（文件内已有该 import 却未使用）；同类问题潜伏于 `SettingsMenuScreen` / `DataExportScreen`（无滚动 + `weight(1f)` 底部占位）

## 修复
- 三页统一：内容 Column 接入 `verticalScroll(rememberScrollState())`；移除滚动列中无效的 `weight(1f)` 占位（固定间距替代）
- 版本 v3.13.1（versionCode 42）；release APK 重新签名，下载站同步更新

## 验收
- JVM 356/356；`assembleRelease` 成功；新 APK SHA-256 与下载站一致

---

# AGENT_LOG.md — 2026-09-11 v3.13.2 滚动排查第二轮（opencode，owner 追问"还有可修的吗"）

## 排查
- 用"内容型页面 × 滚动能力"全量扫描：所有 Screen 已逐个确认（含弹窗内滚动与主内容滚动区分）；本轮唯一遗漏为 `RecurringScreen`
- `RecurringScreen`：规则列表无滚动，条目多时被截断且底部"添加周期账单"被挤出屏幕

## 修复
- 列表区改为 `weight(1f) + verticalScroll`，按钮固定可见（不是整页滚动，主操作始终可达）
- 版本 v3.13.2（versionCode 43）；APK/下载站同步更新

## 验收
- JVM 356/356；`assembleRelease` 成功；线上 index/APK 与新 APK 哈希一致（`ebab6fdd…`）

---

# AGENT_LOG.md — 2026-09-11 v3.14.0 主动提醒增强 + 代码内收尾（opencode，owner：一次性做完，可用 subagent）

## 并行分工（遵循 dispatching-parallel-agents，文件集互不重叠）
- Subagent A（CI）：新增 `.github/workflows/ci.yml`（test + lint，push dev/main + PR + 手动）
- Subagent B（资源）：`ic_launcher_monochrome.xml` 主题图标层 + 小组件文案入 `strings.xml`
- Subagent C（UI）：提醒中心未读水位（`ProactivePrefs.unreadCount/markHistoryRead`）+ 设置页角标 + 进页已读
- 主线程（高风险共享模型）：启动检查、分类级异常基线、储蓄 pace 细化、集成/验证/发布

## 交付（v3.14.0 / versionCode 44）
- 分类级异常：`CategoryWeeklySpending` 输入 + 规则（总量优先，其次选中偏离最大分类，文案带分类名）
- 储蓄 pace：`SavingsPace.remainingDailyBudgetCents`（剩余可支配/剩余天数，不为负），提醒与查询块同步
- 启动检查：`AppContainer.proactiveStartupCheck` + `ExpenseApp` 启动协程（聊天 🔔 + 通知 + 历史；额度/冷却兜底）
- 未读角标：进入提醒中心写已读水位；清空历史重置水位
- 主题图标 monochrome + 小组件文案资源化 + CI

## 验收（本地）
- JVM 356 → **359，0 failed**（分类异常规则 2 + 引擎端到端 1）；`lintDebug` / `assembleDebug` / `assembleRelease` 通过

---

# AGENT_LOG.md — 2026-09-11 v3.14.1 深度审计修复 + 工程加固（opencode，多 subagent 并行）

## 背景
- owner 指令："一直往下推进，可以派 subagent 加速"
- 只读审计 subagent 产出 11 条高置信缺陷报告（含 4 条 High）；本文记录修复与验证

## 并行分工（文件集互不重叠）
- 修复 A：历史页跨年 key 崩溃（`dateIso`）+ LLM JSON 多候选解析
- 修复 B：周期账单排期（跳月/31 号漂移）+ 进程级互斥 + 单条失败不中断
- 修复 C：提醒页快照回填 + 未来日期窗口（预算总览/桌面组件/每日提醒/预算预警）
- 修复 D：备份 recurringRules 校验 + 记忆编辑无效金额反馈
- 主线程：Locale 固定（18 处，含 LLM prompt）+ 会员页轮询生命周期 + 集成/发布
- 工程 subagent：CI 加固（release 编译检查/失败报告/最小权限）、正式签名基础设施（env/properties + `docs/release-signing.md`）

## 二审修复（CI 首跑暴露）
- `gradlew` 补可执行位；CSV 导入器补 `zone` 参数、3 条提示词断言改为按运行环境动态计算（Linux/UTC 可复现）

## 验收（本地 + CI）
- JVM 359 → **372，0 failed**（+13：解析器 2 / 历史 1 / 周期 6 / 备份 2 / Locale 2）
- GitHub Actions：timezone 修复后 run 34622309914 **success**；本版推送后跑新工作流（含 release 编译检查）
- 未修/待 owner 决定：云备份是否排除 `expense.db`（与"仅本地存储"文案相关，属产品决策）

---

# AGENT_LOG.md — 2026-09-11 v3.14.2 第二轮深度审计修复（opencode，多 subagent 并行 + 分支 PR 审核流）

## 背景
- owner 指令：递归再审一轮、能修就修（技术选型用最推荐方案），成果走 GitHub 审核；审核通过后再部署网站；软著材料同步初步准备

## 并行分工（7 路，文件集互不重叠）
- 修复 1：截图导入未配置 AI 崩溃（双层防护）+ 口语金额（23块5/3块半/3.5元歧义）
- 修复 2：DataStore corruptionHandler（6 处）+ 长期记忆解码失败不覆写 + Keystore 失效防崩
- 修复 3：历史相对日期按消息时间戳解析（新 API + coordinator 传参）
- 修复 4：路由代词误判（查询优先，显式写动词保留 MUTATION）
- 修复 5：LlmClient 取消/超时/单次重试/响应限长
- 修复 6：OCR 派生位图懒创建 + finally 回收
- 修复 7：Room 索引 migration 5→6 + CSV 去重基线改活跃记录
- 主线程：真实平台账单 preamble 表头定位 + force-stop 文档 + 集成/验证/发布

## 验收（本地）
- JVM 372 → **398，0 failed**（+26）
- 行为基线：路由 80/80、Query 16/16、工具 16/16 保持；Proactive/Memory Bench 不变
- `lintDebug` / `assembleRelease` 通过；分支 PR 待 owner 审核（APPROVED 后自动合并 + 部署下载站）

## 软著材料（初步）
- `project_023_ai-expense-bot/软著材料/`：源程序文档.pdf（60 页：前 30 + 后 30，共 322 页；127 文件 / 15971 行）、软件说明书（md+pdf）、软著申请信息表、提交清单
- 待 owner 确认：软件全称/版本号写法、开发完成日期与首次发表日期（信息表已标出逻辑矛盾）、著作权人身份材料、界面截图补拍（清单 28 项）

---

# AGENT_LOG.md — 2026-09-13 v3.15.0 商户级异常基线（AI 执行线，owner 指定 D）

## 背景 / owner 决策
- 本轮无 `EXPENSEBENCH_API_KEY` → 待办 A（真实模型 3 轮复测）暂缓
- owner 指定做 B + D：B 软著材料**沿用 V3.14.1、不重生成**；D 选**商户级异常基线**
- B 日期口径：著作权人确认**发表状态＝未发表**，首次发表日期不再填写 → 消除「发表日期早于开发完成日期」的逻辑矛盾

## 交付（D：代码，走分支 PR，未合并 dev）
- 输入：新增 `MerchantWeeklySpending(note, currentWeekCents, completedWeeksCents)`；`ProactiveInputs` 增 `merchantWeekSpends`
- 构建：`ProactiveEngine` 按 `note`（trim 后非空）聚合「本周 + 近 4 个完整周」的商户级周消费
- 判定：`ProactiveRules.anomalyAlert` 改为三级兜底 **总量 → 分类 → 商户**；抽出共用 `weeklyAnomaly(...)`（≥4 样本 / 1.5x / ¥100 口径不变）
- 文案：`本周在「<商户>」消费 ¥X，明显高于近 4 周平均 ¥Y，建议看看是哪几笔。`；商户名超 12 字截断展示（`MAX_MERCHANT_LABEL_CHARS`）

## 交付（B：软著材料，仓库外 `project_023_ai-expense-bot/软著材料/`）
- `软著申请信息表.md`：发表状态「未发表」、首次发表日期留空（已确认）；「日期逻辑自检」标记为已解决
- `提交清单.md`：一致性检查同步为「未发表、不填发表日期」
- 版本保持 V3.14.1；源程序文档 / 软件说明书**未重生成**（owner 决定）

## 边界 / 未改
- 阈值（1.5x / ¥100 / 4 样本）、冷却、每日额度、评测器与数据集均未改动
- 商户级仅在总量与分类都未命中时评估；既有分类级判定结果不变
- 商户按 `note` 精确匹配（同一商户不同写法不合并；空备注不参与）

## 验收（本地）
- JVM 398 → **402，0 failed**（规则 +3 / 引擎 +1；先写测试见红，再实现见绿）；`lintDebug` 通过
- ProactiveInsightBench 38 条五项指标仍全 0%、文案覆盖率 100%（dataset sha 不变）
- 版本 v3.15.0 / versionCode 47；CHANGELOG / README / AGENT_PLAN / `docs/proactive-insight.md` 已同步
- **Human Review PENDING**：分支 `feat/merchant-anomaly-baseline`，等 owner 审核后合并 dev；是否出包 / 部署下载站待 owner 指令

## 待办（更新）
- A：真实模型 3 轮复测（需 `EXPENSEBENCH_API_KEY`）
- B：界面截图补拍（清单 28 项，需真机）＋ 著作权人身份材料；开发完成日期待著作权人核实
- C：云自动备份是否排除 `expense.db`（当前包含；记忆/偏好类 DataStore 均已排除）
- D：后续候选 主动提醒按分类独立冷却 / 上架正式签名

---

# AGENT_LOG.md — 2026-09-13 v3.15.1 隐私边界收紧（AI 执行线，owner 决策 C）

## 背景 / owner 决策
- owner 就「云自动备份是否排除 expense.db」选择 **C**：云备份排除财务数据、Android 12+ 保留设备迁移；并确认 **budget_prefs 一起排除**
- 现状问题（本轮修正）：账目 + 聊天记录（最大最敏感）进云备份，而长期记忆 / 凭据早已排除 → 口径冲突 + 恢复不自洽（账目回来、记忆不回）+ DB 撑过 25MB 配额会**整包静默失败**

## 交付
- `backup_rules.xml`（Android 8–11：云备份与设备迁移共用）：新增 `expense.db` / `-wal` / `-shm` / `-journal` 与 `datastore/budget_prefs.preferences_pb` 排除
- `data_extraction_rules.xml`：`cloud-backup` 加同样排除；`device-transfer` **不排除**账目与预算（12+ 换机保留）
- `BackupPolicyTest`（新增，JVM，**进 CI**）：云备份必须排除财务数据与凭据、设备迁移必须保留账目与预算、清单必须声明两套规则资源
- `tools/test-api-key-backup-rules.ps1`：补**反向断言**（财务数据不得出现在 device-transfer），并把 user_profile / proactive 纳入必排清单
- 文案三处同步：README「🗂 数据安全」新增系统备份口径段；App 内置说明书「数据安全」与欢迎语；官网隐私卡 LEDGER / REMINDERS
- 版本 v3.15.1 / versionCode 48（修订版）

## 未做 / 边界
- 长期记忆与主动提醒数据**仍按原样**排除 device-transfer（owner 本轮只要求「账目 + 预算一起排除」；换机用 JSON 备份恢复，已在文案写明）
- 未修改评测口径、数据集与阈值
- 官网**未部署**：本 PR 只改网站源码的隐私卡；出包与部署等 owner APPROVED 后一并做

## 验收（本地）
- JVM 402 → **405，0 failed**（+3 备份契约；先写测试见红，再改规则见绿）
- 契约脚本：pwsh 7 与 Windows PowerShell 5.1 均 PASS；**负向对照**（把 `expense.db` 从 cloud-backup 移除）如期报错 —— 证明能拦住回归
- `lintDebug` 通过
- **Human Review PENDING**：分支 `fix/cloud-backup-scope`（堆叠在 `feat/merchant-anomaly-baseline` 之上），PR base 指向 v3.15.0 分支，待其合并后改指 dev

## 待办（更新）
- A：真实模型 3 轮复测（需 `EXPENSEBENCH_API_KEY`）
- B：界面截图补拍（28 项，需真机）＋ 著作权人身份材料；发表状态已按 owner 定为「未发表」（**线上公开下载页仍在，建议 owner 复核该口径**）
- D：后续候选 主动提醒按分类独立冷却 / 上架正式签名
