# ExpenseBench v2 — Agent 行为可靠性报告

- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，基准时刻 2026-09-06T12:00:00+08:00
- 被测对象：qwen3.7-flash @ dashscope.aliyuncs.com/compatible-mode/v1
- 数据来源：LLM 端到端实测（真实 Router/Escalation/Tools/Planner/Applier + 内存数据库；运行失败 0 条按无路由/无变更计）
- temperature = 0.0（固定）
- enable_thinking = false（Qwen 结构化路径；其他模型缺省）
- concurrency = 4
- dataset_sha256 = 72d27f59c158da76eb1194abf5adb3b0aa85f1b0cdb66583b169f60dd58287ee
- system_prompt_sha256 = 985b77e28dcf82059a6c7d99965de679a818503ed7500b9dc937e428c3ca58ca
- escalation_prompt_sha256 = cbef1d86b5eab67282d8cd218c828cc38e2d3b612fa3b287d00f7f84cdfce056
- ran_at = 2026-09-11T00:15:16.726809600+08:00
- 复现：同哈希数据 + 同 prompt + temperature=0 + 同模型快照 ⇒ 结果应一致（±供应商非确定性）

## 首要指标：False Mutation Rate

- **False Mutation Rate = 5.6%**（3/54，目标 0%）
- 定义：期望零变更的用例中，实际产生拟变更（已落库 + 待确认）的比例

## 分桶指标

| 桶 | 条数 | Router | Query Recall | Mutation Precision | False Mutation | Tool Sel | Mut Count | Date Binding | E2E |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| multi_temporal | 30 | 93.3% (28/30) | N/A (0/0) | 100.0% | N/A (0/0) | N/A (0/0) | 93.3% (28/30) | 91.9% (57/62) | 76.7% (23/30) |
| multi_turn | 20 | 45.0% (9/20) | 25.0% (1/4) | 100.0% | 0.0% (0/4) | 25.0% (1/4) | 70.0% (14/20) | 56.3% (9/16) | 35.0% (7/20) |
| negative_false_positive | 30 | N/A (0/0) | N/A (0/0) | 0.0% | 10.0% (3/30) | N/A (0/0) | 90.0% (27/30) | N/A (0/0) | 90.0% (27/30) |
| router_ambiguous | 30 | 76.7% (23/30) | 83.3% (10/12) | 100.0% | 0.0% (0/20) | 90.0% (18/20) | 100.0% (30/30) | 100.0% (9/9) | 76.7% (23/30) |
| overall | 110 | 75.0% (60/80) | 68.8% (11/16) | 94.1% | 5.6% (3/54) | 79.2% (19/24) | 90.0% (99/110) | 86.2% (75/87) | 72.7% (80/110) |

口径：
- Router = 生效路由（含 Escalation）与 expected_route 一致；只对声明了 gold 的用例计分
- Query Recall = expected_route=QUERY 中实际走到 QUERY 的比例
- Mutation Precision = 有正例期望且确实产生变更 /（有正例期望且产生变更 + 无期望却产生变更）
- False Mutation = expected_mutation_count=0 的用例中 proposedCount>0（含待确认）
- Tool Sel = expected_tools 集合精确匹配；Mut Count = proposedCount 与 gold 相等
- Date Binding = expect 中带日期的条目，最终变更日期与 gold 相等（漏记按错计）
- E2E = 该条所有声明的检查（路由/工具/笔数/确认门/活跃账目数/最终状态/无假变更）全部通过

请求失败/管线报错：8 条（mt-12、mt-18、mt-13、mtp-15、mtp-27、nfp-02、mt-05、mt-04）

## 失败明细

- nfp-03 [negative_false_positive] 「年终奖3万到账了」：count expect=0 actual=1(applied=1,pending=0)；FALSE_MUTATION applied=[3000000] pending=0；final expect=[] actual=[3000000/other/2026-09-06]
- nfp-21 [negative_false_positive] 「房租还是2500，没变」：count expect=0 actual=1(applied=1,pending=0)；FALSE_MUTATION applied=[250000] pending=0；final expect=[] actual=[250000/housing/2026-09-06]
- nfp-23 [negative_false_positive] 「信用卡还欠着12000」：count expect=0 actual=1(applied=1,pending=0)；FALSE_MUTATION applied=[1200000] pending=0；final expect=[] actual=[1200000/other/2026-09-06]
- mtp-09 [multi_temporal] 「昨天网购衣服200块，今天买日用品50块」：final expect=[20000/shopping/2026-09-05, 5000/shopping/2026-09-06] actual=[20000/shopping/2026-09-05, 5000/other/2026-09-06]
- mtp-13 [multi_temporal] 「8月25日买书45块，前天买笔10块」：final expect=[4500/education/2026-08-25, 1000/education/2026-09-04] actual=[4500/education/2026-08-25, 1000/other/2026-09-04]
- mtp-14 [multi_temporal] 「昨天房租2500，今天水电150」：route expect=MUTATION actual=CHAT
- mtp-15 [multi_temporal] 「昨天午饭35，今天咖啡18」：error=无法解析交易时间：2026-09-05；route expect=MUTATION actual=CHAT；count expect=2 actual=0(applied=0,pending=0)；active expect=2 actual=0；final expect=[3500/food/2026-09-05, 1800/drink/2026-09-06] actual=[]；dates ok=0/2
- mtp-23 [multi_temporal] 「8月20日交房租2500块，8月25日交水电20」：final expect=[250000/housing/2026-08-20, 20000/housing/2026-08-25] actual=[250000/housing/2026-08-20, 20000/other/2026-08-25]
- mtp-27 [multi_temporal] 「昨天买咖啡18，今天买奶茶16，前天买水3块」：error=AI 返回笔数与原文金额数不一致，本次未记录。；count expect=3 actual=0(applied=0,pending=0)；active expect=3 actual=0；final expect=[1800/drink/2026-09-05, 1600/drink/2026-09-06, 300/drink/2026-09-04] actual=[]；dates ok=0/3
- mtp-30 [multi_temporal] 「前天买门票120块，昨天订酒店600块」：final expect=[12000/entertainment/2026-09-04, 60000/entertainment/2026-09-05] actual=[12000/entertainment/2026-09-04, 60000/housing/2026-09-05]
- ra-01 [router_ambiguous] 「午饭35」：route expect=MUTATION actual=CHAT
- ra-05 [router_ambiguous] 「记账：晚饭45」：route expect=MUTATION actual=CHAT
- ra-06 [router_ambiguous] 「刚买了杯奶茶16」：route expect=MUTATION actual=CHAT
- ra-09 [router_ambiguous] 「再记一笔：打车23」：route expect=MUTATION actual=CHAT
- ra-10 [router_ambiguous] 「补记昨天的午饭35」：route expect=MUTATION actual=CHAT
- ra-16 [router_ambiguous] 「上个月和这个月比，哪个花得多」：route expect=QUERY actual=CHAT；tools expect=[query_expenses, analyze_expenses] actual=[]
- ra-19 [router_ambiguous] 「我的钱都花哪了」：route expect=QUERY actual=CHAT；tools expect=[query_expenses, analyze_expenses] actual=[]
- mt-01 [multi_turn] 「今天咖啡18」：route expect=MUTATION actual=CHAT
- mt-02 [multi_turn] 「还有一杯奶茶16」：route expect=MUTATION actual=CHAT
- mt-03 [multi_turn] 「昨天也买了支笔10块」：final expect=[1000/education/2026-09-05] actual=[1000/other/2026-09-05]
- mt-04 [multi_turn] 「补充一下，午饭其实是40」：error=AI 返回了不在本次范围内的账目，本次未修改。；route expect=MUTATION actual=CHAT；count expect=1 actual=0(applied=0,pending=0)；final expect=[4000/food/2026-09-06] actual=[]；dates ok=0/1
- mt-05 [multi_turn] 「记错了，是53」：error=timeout；route expect=MUTATION actual=CHAT；count expect=1 actual=0(applied=0,pending=0)；final expect=[5300/food/2026-09-05] actual=[]；dates ok=0/1
- mt-10 [multi_turn] 「那上个月呢」：route expect=QUERY actual=CHAT；tools expect=[query_expenses, analyze_expenses] actual=[]
- mt-12 [multi_turn] 「今天也是35」：error=无法解析交易时间：2026-09-06；route expect=MUTATION actual=CHAT；count expect=1 actual=0(applied=0,pending=0)；active expect=1 actual=0；final expect=[3500/food/2026-09-06] actual=[]；dates ok=0/1
- mt-13 [multi_turn] 「上一条说错了，那杯瑞幸是16不是18」：error=AI 返回了不在本次范围内的账目，本次未修改。；route expect=MUTATION actual=CHAT；count expect=1 actual=0(applied=0,pending=0)；final expect=[1600/drink/2026-09-06] actual=[]；dates ok=0/1
- mt-14 [multi_turn] 「那这周呢」：route expect=QUERY actual=CHAT；tools expect=[query_expenses, analyze_expenses] actual=[]
- mt-16 [multi_turn] 「再记一笔晚饭30」：route expect=MUTATION actual=CHAT
- mt-18 [multi_turn] 「不对，是32」：error=AI 返回了不在本次范围内的账目，本次未修改。；route expect=MUTATION actual=CHAT；count expect=1 actual=0(applied=0,pending=0)；final expect=[3200/transport/2026-09-06] actual=[]；dates ok=0/1
- mt-19 [multi_turn] 「把刚才那两笔都改成前天」：count expect=2 actual=0(applied=0,pending=0)；final expect=[3500/food/2026-09-04, 1800/drink/2026-09-04] actual=[]；dates ok=0/2
- mt-20 [multi_turn] 「再看下饮品」：route expect=QUERY actual=CHAT；tools expect=[query_expenses, analyze_expenses] actual=[]
