# ExpenseBench v1 — 评测协议

> Y.E cost 的提取质量基准。目标：把"AI 能记账"变成可量化的"AI 记账有多准"。

## 1. 数据集

- 位置：`app/src/test/resources/expensebench/cases.jsonl`
- 规模：120 条用户语录（中文），共 137 笔预期
- 基准时刻：`2026-09-06T12:00:00+08:00`（周日）——所有相对时间（昨天 / 上周三 / 3 天前）都相对该时刻解析，标注稳定可复现
- 六个桶：

| 桶 | 条数 | 考察点 |
| --- | --- | --- |
| basic | 30 | 无日期口述，「午饭35」→ 金额+分类，日期必须为 null |
| date_relative | 25 | 昨天/前天/今天/上周X/3天前/9月1日 等相对与明确日期 |
| multi | 15 | 一句多笔（2-3 笔），跨日期归属与原文顺序 |
| merchant | 25 | 瑞幸/麦当劳/滴滴/盒马… 商户识别进 note |
| colloquial | 15 | 口语（「打了个车回家21.5」「充了50块话费」）|
| explicit_date | 10 | 明确日期+时分（「8月15日12:30…」）|

- 每条标注：`amount_cents`（分，精确）、`category`（必须取自 `Category.ALL` 的 id）、`date`（yyyy-MM-dd 或 null）、`note_contains`（商户子串，可空）

## 2. 两层评测

### 2.1 端侧确定性管道（离线，每次 CI 都跑）

`LocalPipelineBenchTest` —— 测 `ExpenseTextInterpreter` + `ChineseDateResolver` 能锁住多少「客户端可证明的事实」：

- **日期提示覆盖率**：带日期用例中，端侧提示能完整配对的比例。覆盖率低是设计使然：端侧只锁「金额带元/块 且 日期词明确」的组合，其余交给 LLM
- **日期/金额提示准确率**：配对成功时提示与标注的一致率

当前结果：`docs/expensebench-local-report.md`（数据集就绪时为：覆盖率 6.6%，提示命中后日期/金额准确率 100%）

### 2.2 LLM 提取评测（按需运行）

`LlmExpenseBenchTest` —— 用真实 LLM 按生产 system prompt 逐条解析，再评 expected vs parsed：

```bash
EXPENSEBENCH_API_KEY=sk-xxx \
EXPENSEBENCH_BASE_URL=https://api.deepseek.com \
EXPENSEBENCH_MODEL=deepseek-chat \
# EXPENSEBENCH_LIMIT=10 可先跑前 10 条冒烟
./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LlmExpenseBenchTest"
```

- 未设置 API Key 时自动跳过（`Assume`），不影响常规 CI
- 全量 120 条约 5-10 分钟（串行请求）
- 报告写入 `docs/expensebench-llm-report.md`

## 3. 指标口径

| 指标 | 定义 |
| --- | --- |
| 金额准确率 | 预测金额（分）与标注精确相等 |
| 分类准确率 | 金额配对成功的预测里，分类 id 精确相等 |
| 日期准确率 | 金额配对成功的预测里，本地日历日相等；标注 null 时预测也必须 null（测幻觉） |
| 商户准确率 | 金额配对成功且标注了 `note_contains` 的条目里，预测 note（小写去空白）包含标注子串 |
| 整笔全对 | 该笔所有已标注字段全部正确 |
| 笔数全对 | 预测笔数 == 标注笔数的用例占比 |

配对规则：预测与预期按**金额最近邻贪心配对**（金额相同的按输出顺序），配对后逐字段计分；漏记直接拉低金额准确率，多记计入 `extraRecords`。

## 4. 可复现协议

LLM 评测报告必须携带完整环境指纹，否则数字不可信：

- **temperature = 0.0 固定**（`ChatCompletionRequest.temperature` 为可空字段：null 不发送该字段，生产路径沿用供应商默认；仅评测路径显式传 0.0）
- 报告记录 `dataset_sha256`（cases.jsonl 内容哈希）、`prompt_sha256`（system prompt 哈希）、`ran_at` 时间戳
- 模型建议使用带日期的快照别名（如 `deepseek-chat-2026-08-xx`），避免供应商 silent update 污染对比
- 同哈希数据集 + 同哈希 prompt + temperature=0 + 同模型快照 ⇒ 结果应一致（±供应商残留非确定性）

## 5. 换代流程

1. 改模型 / 改 prompt 后，先跑 `EXPENSEBENCH_LIMIT=10` 冒烟
2. 全量跑一遍，把 `docs/expensebench-llm-report.md` 的结果与上一版对比
3. 回归明显（金额或日期准确率下降 >2%）时先修再发
4. 新增能力（如退款项、时间戳精度）→ 新增桶并升版本号（ExpenseBench v2），旧桶标注保持不变

## 6. 已知边界

- v1 只测**提取**（新增记账路径），不测删改意图（需要预置数据库状态，v2 考虑）
- 「2块5」类口语金额、退款/免单语义未纳入 v1
- LLM 评测结果依赖所接模型，不同供应商数据不可混读

## 7. v2（已实现）

ExpenseBench v2 / AgentBehaviorBench 已落地：110 条 × 4 桶（negative_false_positive / multi_temporal / router_ambiguous / multi_turn），评测从"提取准确率"升级为"Agent 行为可靠性"——路由、工具选择、变更笔数、删除确认门、日期绑定、**False Mutation Rate**、端到端成功率。协议与指标见 [expensebench-v2.md](expensebench-v2.md)；离线基线见 `docs/expensebench-v2-local-report.md`。
