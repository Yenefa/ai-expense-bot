# ExpenseBench v2 — 离线行为基线报告（无 LLM）

- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，共 110 条 / 基准时刻 2026-09-06T12:00:00+08:00
- 覆盖：前置路由（不含 Escalation 的确定性层）、工具选择、多日期端侧提示覆盖率
- 多日期端侧提示完整覆盖：27/30（其余金额无元/块，交由 LLM 端 Date Binding）

## 前置路由（expected_route 已标注的 80 条）

- 准确率：58/80
- Query 前置召回：9/16（升级路径依赖 LLM，不计入本离线口径）
- 未命中：
  - mtp-14: expect=MUTATION actual=CHAT — 金额无元/块：端侧提示不覆盖，考 LLM 日期
  - mtp-15: expect=MUTATION actual=CHAT
  - ra-01: expect=MUTATION actual=CHAT — 无时间词→必须落基准当天
  - ra-05: expect=MUTATION actual=CHAT
  - ra-06: expect=MUTATION actual=CHAT
  - ra-09: expect=MUTATION actual=CHAT — 已知路由缺口：'再记一笔'不在 MUTATION_INTENT，前置路由落 CHAT
  - ra-10: expect=MUTATION actual=CHAT — 已知路由缺口：'补记'不触发补账语境
  - ra-13: expect=QUERY actual=CHAT — 需 Intent Escalation 判为 analysis
  - ra-14: expect=QUERY actual=CHAT
  - ra-16: expect=QUERY actual=CHAT — 已知路由缺口：无比对关键词，前置路由落 CHAT
  - ra-19: expect=QUERY actual=CHAT — 已知路由缺口：'花哪了'不匹配 STRONG_QUERY 的'花在哪'
  - mt-01: expect=MUTATION actual=CHAT
  - mt-02: expect=MUTATION actual=CHAT
  - mt-04: expect=MUTATION actual=CHAT — 更正必须是 update，不能新增成 2 条
  - mt-05: expect=MUTATION actual=CHAT — 已知缺口：'记错了'不触发变更上下文加载，模型可能拿不到 id
  - mt-10: expect=QUERY actual=CHAT — 已知路由缺口：'那…呢'不回继承查询语境
  - mt-12: expect=MUTATION actual=CHAT — 已知路由缺口：'也是35'无元/块，前置路由落 CHAT
  - mt-13: expect=MUTATION actual=CHAT — 已知缺口：'说错了'不触发上下文加载
  - mt-14: expect=QUERY actual=CHAT — 已知路由缺口：回承上一轮分析语境
  - mt-16: expect=MUTATION actual=CHAT — 已知路由缺口：'再记'不在 MUTATION_INTENT
  - mt-18: expect=MUTATION actual=CHAT — 已知缺口：'不对'不触发上下文加载
  - mt-20: expect=QUERY actual=CHAT — 已知路由缺口：回承查询语境 + 分类切换

## 工具选择（前置路由=QUERY 的 9 条，集合精确匹配）

- 准确率：9/9

说明：端到端 False Mutation Rate / 路由升级 / 多轮指代由 `LlmAgentBehaviorBenchTest` 用真实模型评测（按需运行）。
