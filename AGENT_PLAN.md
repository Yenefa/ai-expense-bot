# AGENT_PLAN.md — 当前执行计划（2026-09-10 更新）

> 旧版（2026-08-06 v3.6 通宵接管计划）已过时：仓库地址、版本号、测试数量均已更新。
> 历史执行记录见 `AGENT_LOG.md`，踩坑见工作区 `全局复利与踩坑日记.md`。

## 执行线（owner 定义）

**v3.9.1 ✅ → ExpenseBench v2 ✅ → v3.9.2/v3.9.3 ✅ → Memory Governance v1 ✅ → Memory Consumption & User Control ✅ → Proactive Insight v1 ✅（完整链路成型）**

## 项目现状

- Android 记账 App「Y.E cost」（`com.expense.tracker`）：Kotlin 1.9.22 + Jetpack Compose + Room + ML Kit OCR + LLM Agent
- 仓库：https://github.com/Yenefa/ai-expense-bot（`dev` 分支）
- 版本：v3.9.1（versionCode 33）
- 测试：**278 个 JVM 单测**（`./gradlew :app:testDebugUnitTest`，其中 1 个 LLM bench 在无 Key 时 skip）
- 服务器：腾讯云 CloudBase 云函数 `ye-cost-api`（兑换码核销 + AI 代理 `hy3`），/health 在线

## ExpenseBench v2（当前阶段）

- 定位：从"模型提取准确率"升级到"Agent 行为可靠性"
- 数据集：110 条 × 4 桶（negative_false_positive 30 / multi_temporal 30 / router_ambiguous 30 / multi_turn 20）
- 首要指标：**False Mutation Rate（目标 0%）**；另有 Router / Query Recall / Mutation Precision / Tool / Count / Date Binding / E2E
- 协议：`docs/expensebench-v2.md`；离线基线：`docs/expensebench-v2-local-report.md`
- 首轮真实评测已完成（qwen3.7-flash，2026-09-11，3 轮）：
  - 报告 `docs/expensebench-v2-llm-report-qwen3.7-flash.md`；归因 `docs/expensebench-v2-triage-2026-09-11.md`
  - **FMR 1.9% / 5.6% / 5.6%（目标 0%，未达标）**；E2E ~73%；Query Recall 68.8%；Date Binding ~87%
- **v3.9.2 本地加固已完成**（2026-09-11，纯本地验收）：
  - CHAT write firewall（非 MUTATION 路径代码层禁写）、Router 收敛（裸金额→MUTATION / 非支出护栏→CHAT / 查询追问）、纯日期 `occurred_at` 容错、partial hints
  - 离线基线：前置路由 58/80 → **74/80**；Query 前置召回 9/16 → **14/16**；工具 14/14；JVM **288/288**
- **v3.9.3 会话上下文路由已完成**（2026-09-11，纯本地验收）：
  - `ConversationActionContext`：条件更正（上一轮记账 + 最近账目 + 更正词）、条件续记（也是35）、Query 回承（那X呢/再看下X）
  - 离线基线：前置路由 74/80 → **80/80**；Query 前置召回 14/16 → **16/16**；工具 16/16；JVM **295/295**
- **真实模型复测已完成（2026-09-11，owner 指令）**：qwen3.7-flash ×3 轮，
  **False Mutation Rate 0.0%（0/54）**；Router 100% / Query Recall 100% / Tool 100%；Date Binding 98.9%；E2E 90.9–91.8%；
  详见 `docs/expensebench-v2-verification-post-hardening.md`
- LLM Bench 继续按需（不再自动跑）；剩余：多轮重复记录、更新未产出（分类灰区已按 owner 裁定落地，见 docs/gold-review-mtp-classification.md）
- 后续候选：分类灰区 gold 复核

## Memory Governance（v3.10 写 / v3.11 读+管理，已完成，2026-09-11）

- 写：用户表达 → Memory Proposal → 类型校验 → 预览 → **Human Confirm** → UserProfile；唯一写入口 `MemoryGovernor.confirm`，LLM 无写接口
- 读：`MemoryReadScope`（NONE / CLASSIFICATION / FINANCIAL_ANALYSIS）按轮次授权；别名确定性应用（瑞幸15→饮品）
- 管理：设置 → 我的记忆（查看/修改/删除/清空，含来源与创建时间）；JSON 备份 v3 含记忆
- Bench：写侧 Silent Write = 0%（36 条）；读侧 Unauthorized Read = 0% / Deleted Reuse = 0% / Application 100%（38 条）
- 下一步：**Proactive Insight**（才允许开始）；候选：分析/建议消费画像、预算等扩展类型

## Proactive Insight（v3.12.0 v1 已完成，2026-09-11）

- 规则决定是否提醒（预算临界 / 异常消费 / 储蓄偏离），LLM 只改文案
- 硬约束：≥4 可比样本 / 冷启动不提醒 / 每日 1 条 / 同类冷却 / 可逐类关闭
- Bench 38 条：False/Missed/Duplicate/Cold-start/Budget 五项指标全 0%
- 完整链路：Deterministic Routing → Short-term Context → Governed Memory → Scoped Memory Read → Deterministic Finance Tools → Controlled Mutation → Rule-governed Proactive Insight
- 下一步候选：系统通知投递与提醒中心、分类级异常基线、LLM 文案真实复测

## 历史里程碑（简）

v3.6 AI 安全（日期/确认/解析提速）→ v3.7 预算/提醒/周期账单/桌面组件 → v3.8 Agent v1 + ExpenseBench v1 →
v3.9 Intent Escalation + analyze_expenses → v3.9.1 Reliability Hardening（thinking 门控 / QUERY 只读 / analyze 修复 / Escalation 参数 / 文案对齐）→
v3.9.2 本地安全加固（CHAT write firewall / Router 收敛 / 纯日期容错 / partial hints）→
v3.9.3 会话上下文路由（条件更正 / 条件续记 / Query 回承）→
v3.10.0 Memory Governance v1（四类提案 / 确认门 / Silent Memory Write Rate = 0）→
v3.11.0 Memory Consumption & Control（读权限 / 别名应用 / 我的记忆 / 备份 v3 / Unauthorized & Deleted 双 0）→
v3.12.0 Proactive Insight v1（规则决策 / LLM 文案 / 五指标全 0）
