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

## 待 owner
- 用真实模型跑 `LlmAgentBehaviorBenchTest` 全量，产出 `docs/expensebench-v2-llm-report-<model>.md`；按失败分类排 v3.9.2 修复清单
