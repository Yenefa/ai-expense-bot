# ExpenseBench v2 首轮真实评测归因（qwen3.7-flash）

- 日期：2026-09-11
- 被测：`qwen3.7-flash @ dashscope.aliyuncs.com/compatible-mode/v1`，temperature=0，Qwen 结构化路径 `enable_thinking=false`
- 数据：`cases-v2.jsonl`（`dataset_sha256 = 72d27f59…`），110 条 × 4 桶
- 报告（自动生成，含逐条失败明细）：`docs/expensebench-v2-llm-report-qwen3.7-flash.md`
- 本文件是**归因记录**：只给证据和候选修复方向，不包含任何代码改动；v3.9.2 范围由 owner 决定。

## 1. 三轮结果（同协议、同数据，仅时间不同）

| 指标 | Run 1 | Run 2 | Run 3（报告留档） | 目标 |
| --- | ---: | ---: | ---: | ---: |
| **False Mutation Rate** | 1.9% (1/54) | 5.6% (3/54) | 5.6% (3/54) | **0%** |
| E2E Success | 75.5% (83/110) | 72.7% (80/110) | 72.7% (80/110) | — |
| Query Recall | 68.8% (11/16) | 68.8% (11/16) | 68.8% (11/16) | 高 |
| Date Binding | 92.0% (80/87) | 87.4% (76/87) | 86.2% (75/87) | 高 |
| Router | 75.0% (60/80) | 75.0% (60/80) | 75.0% (60/80) | — |

- 请求失败/管线报错：Run 1 4 条、Run 2 8 条、Run 3 8 条（含偶发 timeout）。
- **结论：FMR 不是 0，且 temperature=0 下仍有 1.9%→5.6% 波动**（供应商非确定性）。按 owner 规则：Memory Governance / Proactive Insight 继续后压，v3.9.2 以安全性与数据可靠性优先。

## 2. 失败分类（按证据）

### A. 假变更（P0，FMR 分子）

| 用例 | 文本 | 现象 |
| --- | --- | --- |
| nfp-03 | 年终奖3万到账了 | Run 2/3 均被记为 ¥30,000（other）|
| nfp-21 | 房租还是2500，没变 | Run 2/3 均被记为 ¥2,500（housing）|
| nfp-23 | 信用卡还欠着12000 | Run 3 被记为 ¥12,000（other）|
| nfp-29 | 昨天看到一双鞋，标价899 | Run 2 被记为 ¥899（shopping）|
| nfp-02 | 今天发工资了，到手12000 | Run 3 请求失败（未计入），若成功存在同类风险 |

- 共同点：**无元/块后缀的裸金额** → 走 CHAT 路径；该路径既没有 QUERY 的代码层只读，也没有 MUTATION 的 `structuredRequest=true`（Qwen 思考开关未关）。
- 已有对照：nfp-01「我月薪8000」三轮均未被记账——说明不是全盘失守，是模型在这些句式上不稳定。

### B. 整批数据丢失（P1，可靠性）

| 用例 | 文本 | 现象 |
| --- | --- | --- |
| mtp-15 | 昨天午饭35，今天咖啡18 | 模型返回 `occurred_at = "2026-09-05"`（纯日期）→ `无法解析交易时间` → 整批拒绝 |
| mt-12 | 今天也是35 | 同上，`"2026-09-06"` |

- 根因：`LlmResponseParser.parseOccurredAt` 只接受 Instant / OffsetDateTime / LocalDateTime，纯日期直接抛错并中止整批。
- 影响：正确提取的账目因时间格式丢失；Run 2/3 均复现。

### C. 金额提示 guard 误杀（P1）

| 用例 | 文本 | 现象 |
| --- | --- | --- |
| mtp-27 | 昨天买咖啡18，今天买奶茶16，前天买水3块 | 模型正确提取 3 笔；因只有「3块」有元/块 → hints=1 → planner 以「笔数与原文金额数不一致」拒绝整批 |

- 根因：`hasCompleteExpenseHints` 的口径是"所有被识别的元/块金额都有提示"，不等于"原文所有笔数都有提示"；模型多提取时 guard 误杀。
- 影响：正确结果被丢弃（3 笔全丢）。

### D. 上下文/更正触发词缺口（P1，已离线预判、真实复现）

| 用例 | 文本 | 现象 |
| --- | --- | --- |
| mt-04 | 补充一下，午饭其实是40 | 模型输出 update；上下文未加载 → "不在本次范围内" |
| mt-05 | 记错了，是53 | 同上（Run 2 亦复现）|
| mt-13 | 上一条说错了，那杯瑞幸是16不是18 | 同上 |
| mt-18 | 不对，是32 | 同上 |
| mt-07 | 把刚才那笔挪到昨天 | 偶发未产生变更（模型侧） |
| mt-19 | 把刚才那两笔都改成前天 | Run 3 未产生变更（模型侧） |

- 代码侧：`ChatLlmCoordinator.EXISTING_RECORD_INTENT` 不含 补充/记错了/说错了/不对；模型拿不到账目 id。
- 注意：owner 已警告不要直接把这些词加进 mutation trigger（"你这个分析不对"会误伤）——需要与具体上下文条件绑定。

### E. 查询回承与升级缺口（P1）

| 用例 | 文本 | 现象 |
| --- | --- | --- |
| ra-16 | 上个月和这个月比，哪个花得多 | 前置 CHAT，无 escalation 触发词 → 未走工具 |
| ra-19 | 我的钱都花哪了 | 同上（"花哪了" ≠ "花在哪"）|
| mt-10 | 那上个月呢 | 未继承上一轮 QUERY 语境 |
| mt-14 | 那这周呢 | 同上 |
| mt-20 | 再看下饮品 | 同上 + 分类切换 |
| mtp-14 | 昨天房租2500，今天水电150 | 裸金额 → CHAT；但记录本身成功（仅 route/count 指标扣分）|

- 客观影响：Query Recall 68.8%；但裸金额类（ra-01/05/06/09/10、mt-01/02/16）记录结果本身正确，route 缺口的主要代价是 **Qwen 思考开关未关** 与指标扣分。

### F. Gold 需要复核的灰区（数据质量，非代码 bug）

- mtp-09 买日用品 → 模型 other / gold shopping；mtp-13 买笔 → other / gold education；mtp-23 交水电 → other / gold housing；mtp-29 理发 → shopping / gold other；mtp-30 订酒店 → housing / gold entertainment；mt-03 买笔 → other / gold education。
- 建议：这些条目要么收紧 gold 定义（写进协议），要么在 E2E 里把分类从强校验降级为记录项，避免污染 E2E 观感。

## 3. v3.9.2 候选（按证据排序，未实施）

1. **假变更治理（P0）**：先查"裸金额 CHAT 路径 + 思考开关"两条线；最小改动可能是把裸金额纳入结构化路径 + 系统 prompt 增加收入/负债/不变事实的负例。验收 = FMR 回 0（连续 3 轮）。
2. **纯日期 occurred_at 容错（P1）**：parser 接受 `yyyy-MM-dd`（与当前时间组合）或客户端归一化；验收 = mtp-15/mt-12 不再整批失败。
3. **hints 误杀修复（P1）**：仅当 hints 数与模型提取笔数一致时才作为硬绑定；不一致时降级为"不用 hints"而不是拒绝整批（需安全评审）。
4. **上下文触发词的最小规则（P1）**：只在"存在最近批次/活跃账目 + 更正语义"时加载上下文；避免无差别加 trigger 造成 False Positive。
5. **查询回承（P2）**：升级层或会话状态记住上一轮 QUERY 语境（要防误伤，measure 先行）。

> 重跑协议：任何一条修完，`EXPENSEBENCH_MODEL=qwen3.7-flash` 跑全量 3 轮，对比 FMR / E2E / Query Recall / Date Binding，再决定下一条。
