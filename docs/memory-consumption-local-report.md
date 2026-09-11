# MemoryConsumptionBench — 长期记忆消费报告

- 数据集：`app/src/test/resources/expensebench/memory-consumption-cases.jsonl`，共 38 条
- 被测对象：真实 Agent + MemoryReadScope 授权注入 + 商户别名确定性应用（stub LLM，零网络）
- 数据来源：本地确定性管线 + 生产 Agent（stub LLM，零网络）
- dataset_sha256 = be55d982274a5705b0059376470c093ba7d07712c827d5a85ca2c2cec6665ffe
- ran_at = 2026-09-11T21:12:13.839756700+08:00
- 授权范围：MUTATION→商户别名；QUERY→月收入/储蓄目标/常用分类；CHAT→无

## 核心指标

- **Unauthorized Memory Read Rate = 0.0%**（0/38，必须 0%）
- **Deleted Memory Reuse Rate = 0.0%**（0/6，必须 0%）
- Correct Memory Application = 100.0%（25/25）

| 桶 | 条数 | 越权读取 | 正确应用 | 删除后复用 | 路由 |
| --- | --- | --- | --- | --- | --- |
| alias_application | 8 | 0/8 | 100.0% (8/8) | 0/0 | 100.0% (8/8) |
| analysis_reads | 8 | 0/8 | 100.0% (8/8) | 0/0 | 100.0% (8/8) |
| deleted_reuse | 6 | 0/6 | 100.0% (2/2) | 0/6 | 100.0% (6/6) |
| no_memory | 4 | 0/4 | N/A (0/0) | 0/0 | 100.0% (4/4) |
| preference_analysis | 4 | 0/4 | 100.0% (4/4) | 0/0 | 100.0% (4/4) |
| unauthorized | 8 | 0/8 | 100.0% (3/3) | 0/0 | 100.0% (8/8) |
| overall | 38 | 0/38 | 100.0% (25/25) | 0/6 | 100.0% (38/38) |

口径：
- 授权范围：MUTATION→商户别名；QUERY→月收入/储蓄目标/常用分类；CHAT→无；未授权类型的标记不得出现在 prompt
- 正确应用：预期读取类型的标记出现；别名用例最终账目分类等于 gold
- 删除后复用：被删除类型的标记禁止出现；被删除别名不得再覆盖分类
