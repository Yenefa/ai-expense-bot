# ExpenseBench v2 — 离线行为基线报告（无 LLM）

- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，共 110 条 / 基准时刻 2026-09-06T12:00:00+08:00
- 覆盖：前置路由（不含 Escalation 的确定性层；多轮用例按 gold `previous_route` 模拟会话上下文）、工具选择、多日期端侧提示覆盖率
- 多日期端侧提示完整覆盖：27/30（其余金额无元/块，交由 LLM 端 Date Binding）

## 前置路由（expected_route 已标注的 80 条）

- 准确率：80/80
- Query 前置召回：16/16（升级路径依赖 LLM，不计入本离线口径）
- 未命中：
  - 无

## 工具选择（前置路由=QUERY 的 16 条，集合精确匹配）

- 准确率：16/16

说明：端到端 False Mutation Rate / 路由升级 / 多轮指代由 `LlmAgentBehaviorBenchTest` 用真实模型评测（按需运行）。
