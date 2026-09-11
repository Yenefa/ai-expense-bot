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
