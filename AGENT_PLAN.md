# AGENT_PLAN.md — 当前执行计划（2026-09-10 更新）

> 旧版（2026-08-06 v3.6 通宵接管计划）已过时：仓库地址、版本号、测试数量均已更新。
> 历史执行记录见 `AGENT_LOG.md`，踩坑见工作区 `全局复利与踩坑日记.md`。

## 执行线（owner 定义）

**v3.9.1 Reliability Hardening ✅ → ExpenseBench v2 / AgentBehaviorBench（当前）→ Memory Governance → Proactive Insight**

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
- 下一步：
  1. owner 用真实模型跑 `LlmAgentBehaviorBenchTest`（全量 110 条），产出 `docs/expensebench-v2-llm-report-<model>.md`
  2. 按报告失败分类列出 v3.9.2 修复清单（候选已在 v2 协议 §7：裸金额路由、更正触发词、查询回承）
  3. 修复后重跑对比 False Mutation Rate 与 E2E

## Memory Governance（下一阶段，未开工）

「我月收入8000」→ 不是 Expense → Memory Proposal → 预览 → Confirm → UserProfile。
v2 的 negative_false_positive 桶保证它不会被识别成 ¥8000 支出。

## 历史里程碑（简）

v3.6 AI 安全（日期/确认/解析提速）→ v3.7 预算/提醒/周期账单/桌面组件 → v3.8 Agent v1 + ExpenseBench v1 →
v3.9 Intent Escalation + analyze_expenses → v3.9.1 Reliability Hardening（thinking 门控 / QUERY 只读 / analyze 修复 / Escalation 参数 / 文案对齐）
