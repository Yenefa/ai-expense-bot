# ExpenseBench v2 — Agent 行为可靠性评测协议

> v1 回答"模型提取有多准"；v2 回答"完整 Agent 会不会做错事"。
> 评测链路：用户文本 → Router（含 Intent Escalation）→ 工具调用 → 变更计划 → 确认门 → 最终数据库状态。

## 1. 数据集

- 位置：`app/src/test/resources/expensebench/cases-v2.jsonl`
- 规模：**110 条**，首批只做四个高价值桶（不冲 500）：

| 桶 | 条数 | 核心风险 |
| --- | ---: | --- |
| `negative_false_positive` | 30 | 不该记账却记账（收入/预算/估值/假设/否定/第三方） |
| `multi_temporal` | 30 | 多笔跨日期绑定错误（逐笔日期不能串） |
| `router_ambiguous` | 30 | QUERY / CHAT / MUTATION 路由错误 |
| `multi_turn` | 20 | 指代、上一轮上下文与更正错误 |

- 基准时刻：`2026-09-06T12:00:00+08:00`（与 v1 相同，相对时间统一相对该时刻解析）
- 每条 gold 字段：

| 字段 | 含义 |
| --- | --- |
| `text` | 本轮用户输入 |
| `history` | 可选，之前轮次（`link_seed=true` 的 assistant 消息挂上 seed 账目 id，模拟"刚才那批"） |
| `seed_expenses` | 可选，预置数据库账目（多轮指代/更正用） |
| `expect` | 本轮期望的最终变更（insert/update 的最终状态；复用 v1 的金额/分类/日期/商户口径） |
| `expected_route` | `MUTATION`/`QUERY`/`CHAT`；`null` = 本条不评路由（首看最终数据库） |
| `expected_tools` | QUERY 轮期望的工具集合（精确匹配）；`null` = 不评 |
| `expected_mutation_count` | 拟变更总数（已落库 + 待确认）；`null` = 不评 |
| `expected_pending` | 期望进入删除确认门（拟变更但不落库） |
| `expected_active_count` | 轮末活跃账目总数；用于抓"该 update 却 insert"的重复记账 |
| `note` | 人工标注理由 / 已知缺口 |

## 2. 两层运行

### 2.1 离线确定性层（无 API Key，进常规 CI）

`LocalAgentBehaviorBenchTest`：

- 数据集完整性（桶规模、id 唯一、分类/日期合法、gold 自洽）
- 前置路由基线与工具选择（确定性；Escalation 结果不计入离线口径）
- 多日期逐笔绑定回归：对端侧提示完整的用例，用 gold 提取作为模型输出，验证 `ExpenseTextInterpreter` 提示把每笔金额绑到正确日期
- 报告：`docs/expensebench-v2-local-report.md`

### 2.2 LLM 端到端层（按需运行）

`LlmAgentBehaviorBenchTest` —— 真实模型 + 真实 Router/Escalation/Tools/Planner/Applier + 内存数据库：

```bash
EXPENSEBENCH_API_KEY=sk-xxx \
EXPENSEBENCH_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
EXPENSEBENCH_MODEL=qwen3.7-flash \
EXPENSEBENCH_CONCURRENCY=4 \
# EXPENSEBENCH_LIMIT=10 可先冒烟
./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LlmAgentBehaviorBenchTest"
```

- 删除确认门不会被 bench 自动确认（保持人类治理语义：拟删除只记 pending，不落库）
- 报告：`docs/expensebench-v2-llm-report-<model>.md`

## 3. 指标口径

| 指标 | 定义 |
| --- | --- |
| **False Mutation Rate** | `expected_mutation_count=0` 的用例中，实际产生拟变更（已落库 + 待确认）的比例。**首要指标，目标 0%** |
| Router Accuracy | 生效路由（含 Escalation 结果）与 `expected_route` 一致的比例（仅对声明 gold 的用例） |
| Query Recall | `expected_route=QUERY` 中实际走到 QUERY 的比例 |
| Mutation Precision | `expected>0 且产生变更` /（`expected>0 且产生变更` + `expected=0 却产生变更`） |
| Tool Selection Accuracy | `expected_tools` 集合与实际调用工具集合精确相等的比例 |
| Expense Count Accuracy | 拟变更总数与 `expected_mutation_count` 相等的比例 |
| Date Binding Accuracy | `expect` 中带日期的条目，最终变更日期与 gold 相等的比例（漏记按错计） |
| End-to-End Success Rate | 该条所有声明的检查（路由/工具/笔数/确认门/活跃账目数/最终状态/无假变更）全部通过的比例 |

配对规则：与 v1 一致，按金额最近邻贪心配对后逐字段计分。

## 4. 可复现协议

- `temperature = 0.0` 固定；Qwen 结构化路径 `enable_thinking=false`（走生产同款 `LlmThinkingPolicy`）
- 报告记录 `dataset_sha256`、`system_prompt_sha256`、`escalation_prompt_sha256`、`ran_at`、并发数
- 模型建议使用带日期的快照别名；不同供应商数据不可混读

## 5. 观测钩子（生产 no-op）

两个仅用于 bench/监控的最小 seam，生产默认不改变行为：

- `AgentToolContext.onToolCall: (String) -> Unit` —— 记录 `query_expenses` / `analyze_expenses` / `get_budget_status`
- `ExpenseAgent.onRouteResolved: (AgentRoute) -> Unit` —— 记录每轮最终生效路由（含升级结果）

## 6. 已知边界

- 首批 110 条是行为基线，不追求覆盖率；失败模式比数量重要
- 多轮桶包含"已知路由缺口"用例（dataset `note` 标注）：这些是待 v3.9.2 修复的输入，报告会如实扣分
- Bench 不自动确认删除，`expected_pending` 用例验证的是"拟删除且未落库"
- 费用口径：`expected_mutation_count` 统计拟变更（insert/update/delete）笔数，不按金额

## 7. 首轮真实评测与归因（qwen3.7-flash，2026-09-11）

- 自动报告（含逐条失败明细）：`docs/expensebench-v2-llm-report-qwen3.7-flash.md`
- 归因记录（证据 + 候选修复，不含代码改动）：`docs/expensebench-v2-triage-2026-09-11.md`
- 三轮关键数字：**FMR 1.9% / 5.6% / 5.6%**（目标 0%，未达标且 temperature=0 下仍有波动）；
  E2E 75.5% / 72.7% / 72.7%；Query Recall 68.8%；Date Binding 92.0% / 87.4% / 86.2%
- 结论：按 owner 规则，Memory Governance / Proactive Insight 后压，v3.9.2 安全性与可靠性优先
- v3.9.2 候选（未实施，按证据排序）：假变更治理（P0）→ 纯日期 `occurred_at` 容错 → `hints` 误杀 → 上下文触发词最小规则 → 查询回承

修完任意一条：同协议重跑全量 3 轮，对比 FMR / E2E / Query Recall / Date Binding。
