# ExpenseBench v2 — Agent 行为可靠性报告

- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，基准时刻 2026-09-06T12:00:00+08:00
- 被测对象：qwen3.7-flash @ dashscope.aliyuncs.com/compatible-mode/v1
- 数据来源：LLM 端到端实测（真实 Router/Escalation/Tools/Planner/Applier + 内存数据库；运行失败 0 条按无路由/无变更计）
- temperature = 0.0（固定）
- enable_thinking = false（Qwen 结构化路径；其他模型缺省）
- concurrency = 4
- dataset_sha256 = 962bdd4a74d9a48fcdac5e46d964f3afe1b9c9068b6d947b9293609d105cbf77
- system_prompt_sha256 = 985b77e28dcf82059a6c7d99965de679a818503ed7500b9dc937e428c3ca58ca
- escalation_prompt_sha256 = cbef1d86b5eab67282d8cd218c828cc38e2d3b612fa3b287d00f7f84cdfce056
- ran_at = 2026-09-11T20:43:45.171024900+08:00
- 复现：同哈希数据 + 同 prompt + temperature=0 + 同模型快照 ⇒ 结果应一致（±供应商非确定性）

## 首要指标：False Mutation Rate

- **False Mutation Rate = 0.0%**（0/54，目标 0%）
- 定义：期望零变更的用例中，实际产生拟变更（已落库 + 待确认）的比例

## 分桶指标

| 桶 | 条数 | Router | Query Recall | Mutation Precision | False Mutation | Tool Sel | Mut Count | Date Binding | E2E |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| multi_temporal | 30 | 100.0% (30/30) | N/A (0/0) | 100.0% | N/A (0/0) | N/A (0/0) | 100.0% (30/30) | 100.0% (62/62) | 83.3% (25/30) |
| multi_turn | 20 | 100.0% (20/20) | 100.0% (4/4) | 100.0% | 0.0% (0/4) | 100.0% (4/4) | 80.0% (16/20) | 93.8% (15/16) | 80.0% (16/20) |
| negative_false_positive | 30 | N/A (0/0) | N/A (0/0) | N/A | 0.0% (0/30) | N/A (0/0) | 100.0% (30/30) | N/A (0/0) | 100.0% (30/30) |
| router_ambiguous | 30 | 100.0% (30/30) | 100.0% (12/12) | 100.0% | 0.0% (0/20) | 100.0% (20/20) | 100.0% (30/30) | 100.0% (9/9) | 100.0% (30/30) |
| overall | 110 | 100.0% (80/80) | 100.0% (16/16) | 100.0% | 0.0% (0/54) | 100.0% (24/24) | 96.4% (106/110) | 98.9% (86/87) | 91.8% (101/110) |

口径：
- Router = 生效路由（含 Escalation）与 expected_route 一致；只对声明了 gold 的用例计分
- Query Recall = expected_route=QUERY 中实际走到 QUERY 的比例
- Mutation Precision = 有正例期望且确实产生变更 /（有正例期望且产生变更 + 无期望却产生变更）
- False Mutation = expected_mutation_count=0 的用例中 proposedCount>0（含待确认）
- Tool Sel = expected_tools 集合精确匹配；Mut Count = proposedCount 与 gold 相等
- Date Binding = expect 中带日期的条目，最终变更日期与 gold 相等（漏记按错计）
- E2E = 该条所有声明的检查（路由/工具/笔数/确认门/活跃账目数/最终状态/无假变更）全部通过

请求失败/管线报错：0 条

## 失败明细

- mtp-09 [multi_temporal] 「昨天网购衣服200块，今天买日用品50块」：final expect=[20000/shopping/2026-09-05, 5000/shopping/2026-09-06] actual=[20000/shopping/2026-09-05, 5000/other/2026-09-06]
- mtp-13 [multi_temporal] 「8月25日买书45块，前天买笔10块」：final expect=[4500/education/2026-08-25, 1000/education/2026-09-04] actual=[4500/education/2026-08-25, 1000/other/2026-09-04]
- mtp-23 [multi_temporal] 「8月20日交房租2500块，8月25日交水电20」：final expect=[250000/housing/2026-08-20, 20000/housing/2026-08-25] actual=[250000/housing/2026-08-20, 20000/other/2026-08-25]
- mtp-29 [multi_temporal] 「昨天理发40块，今天买日用品80块」：final expect=[4000/other/2026-09-05, 8000/shopping/2026-09-06] actual=[4000/shopping/2026-09-05, 8000/shopping/2026-09-06]
- mtp-30 [multi_temporal] 「前天买门票120块，昨天订酒店600块」：final expect=[12000/entertainment/2026-09-04, 60000/entertainment/2026-09-05] actual=[12000/entertainment/2026-09-04, 60000/housing/2026-09-05]
- mt-02 [multi_turn] 「还有一杯奶茶16」：count expect=1 actual=2(applied=2,pending=0)；active expect=1 actual=2；final expect=[1600/drink/2026-09-06] actual=[5000/transport/2026-09-06, 1600/drink/2026-09-06]
- mt-03 [multi_turn] 「昨天也买了支笔10块」：count expect=1 actual=2(applied=2,pending=0)；active expect=1 actual=2；final expect=[1000/education/2026-09-05] actual=[4500/education/2026-09-05, 1000/education/2026-09-05]
- mt-15 [multi_turn] 「刚才那杯咖啡记到前天」：count expect=1 actual=0(applied=0,pending=0)；final expect=[1800/drink/2026-09-04] actual=[]；dates ok=0/1
- mt-16 [multi_turn] 「再记一笔晚饭30」：count expect=1 actual=2(applied=2,pending=0)；active expect=1 actual=2；final expect=[3000/food/2026-09-06] actual=[3500/food/2026-09-06, 3000/food/2026-09-06]
