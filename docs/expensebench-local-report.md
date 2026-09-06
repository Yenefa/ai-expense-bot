# ExpenseBench v1 — 端侧确定性管道报告（离线）

- 数据集基准时刻：2026-09-06T12:00:00+08:00
- 带日期用例：48 条，预期笔数 61
- 日期提示覆盖率（完整用例口径）：4 / 61 = 6.6%
  - 覆盖率低是设计使然：端侧提示只锁定「客户端可证明的事实」（金额带元/块且日期词明确），其余交给 LLM
- 日期提示准确率（配对成功时）：3 / 3 = 100.0%
- 金额提示准确率（配对成功时）：3 / 3 = 100.0%

说明：端侧提示锁日期与金额笔数，分类与商户由 LLM 决策；两者互补，口径见 docs/expensebench.md。

未配对/未命中样例（前 20 条）：
- d001: hints=0 expect=1
- d002: hints=0 expect=1
- d003: hints=0 expect=1
- d004: hints=0 expect=1
- d005: hints=0 expect=1
- d006: hints=0 expect=1
- d007: hints=0 expect=1
- d008: hints=0 expect=1
- d009: hints=0 expect=1
- d010: hints=0 expect=1
- d012: hints=0 expect=1
- d013: hints=0 expect=1
- d014: hints=0 expect=1
- d015: hints=0 expect=1
- d016: hints=0 expect=1
- d017: hints=0 expect=1
- d018: hints=0 expect=1
- d019: hints=0 expect=1
- d020: hints=0 expect=1
- d021: hints=0 expect=1
