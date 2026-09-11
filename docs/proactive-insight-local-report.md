# ProactiveInsightBench — 主动提醒报告

- 数据集：`app/src/test/resources/expensebench/proactive-cases.jsonl`，共 38 条
- 被测对象：确定性规则引擎 + 治理约束（stub 文案层；LLM 不参与决策，本地零网络）
- 数据来源：本地规则引擎 + 治理约束（stub 文案层，零网络）
- dataset_sha256 = 57f7133ead4e1b339a2d5b86707cd92e8d122e7881f66daf0da51b8d0994b03e
- ran_at = 2026-09-12T00:29:24.756289+08:00
- 硬约束：≥4 可比样本 / 冷启动不提醒 / 每日 1 条 / 同类冷却 / 可关闭

## 五个指标（目标全为 0）

- False Alert Rate = 0.0%（0/24）
- Missed Alert Rate = 0.0%（0/14）
- Duplicate Alert Rate = 0.0%（0/5）
- Cold-start Violation Rate = 0.0%（0/6）
- Notification Budget Violation Rate = 0.0%（0/5）
- 文案覆盖率（应提醒用例）= 100.0%

| 桶 | 条数 | 实际提醒 |
| --- | --- | --- |
| should_alert | 14 | 14 |
| no_trigger | 5 | 0 |
| suppressed_cold_start | 6 | 0 |
| suppressed_cooldown | 5 | 0 |
| suppressed_daily_budget | 5 | 0 |
| suppressed_disabled | 3 | 0 |

口径：规则先决策，治理层再套硬约束（每日 1 条、同类冷却、可关闭）；文案层不参与是否提醒。
