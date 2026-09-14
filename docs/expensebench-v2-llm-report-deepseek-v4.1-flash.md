# ExpenseBench v2 — Agent 行为可靠性报告（deepseek-v4.1-flash 旁路基线）

> ⚠️ **这不是协议基准，请勿与 qwen3.7-flash 的历史结果直接比较。** 本轮为一次**旁路实测**，运行条件与协议有三处差异（见下）。

## 运行条件

- 数据集：`app/src/test/resources/expensebench/cases-v2.jsonl`，基准时刻 2026-09-06T12:00:00+08:00
- dataset_sha256 = `c820cd32b5979c8393085fa9bd017ff8ab172fb97938df5ea5acbd04b218deb6`
- system_prompt_sha256 = `d4467383cbb7cb9dbe89ee9d2f9e20fcaafff12f4721efa08328444d6b14fb22`
- escalation_prompt_sha256 = `cbef1d86b5eab67282d8cd218c828cc38e2d3b612fa3b287d00f7f84cdfce056`
- 模型：**`deepseek-v4.1-flash`** @ `https://opencode.ai/zen/go/v1`（OpenCode Zen Go）
- `temperature = 0.0`；**`reasoning_effort = high`**（非协议参数，本次 owner 指定额外注入）
- concurrency = 4；三轮，**请求失败 / 管线报错 0 条**
- ran_at（三轮）：2026-09-14T18:56:40 / 18:57:49 / 18:58:57 +08:00

### 与协议的三处差异（重要）

1. **模型不同**：协议基准为 `qwen3.7-flash`，本轮为 `deepseek-v4.1-flash`。
2. **多注入了 `reasoning_effort = high`**：该参数不在 `docs/expensebench-v2.md` 的协议里。
3. **经由本地转发代理**：上游要求客户端发送 `x-opencode-session` 头（缺则 HTTP 400），而 `LlmClient` 不发该头。因此本轮通过**仓库外的一次性本地代理**（`127.0.0.1:8787`）注入该头与 `reasoning_effort`，**仓库代码零改动**。

> 因此自动生成的报告里「被测对象 @ 127.0.0.1:8787/v1」是代理地址，真实上游为上表所列；`enable_thinking = false` 一行对本模型不适用（`LlmThinkingPolicy` 对非 qwen 模型返回 null，该字段未发送）。

## 三轮结果

| 轮次 | False Mutation Rate | Router | Query Recall | Tool Sel | Mut Count | Date Binding | E2E | 失败用例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | **0.0%** (0/54) | 100.0% (80/80) | 100.0% (16/16) | 100.0% (24/24) | 100.0% (110/110) | 100.0% (87/87) | 99.1% (109/110) | ra-08 |
| 2 | **0.0%** (0/54) | 100.0% (80/80) | 100.0% (16/16) | 100.0% (24/24) | 100.0% (110/110) | 100.0% (87/87) | 99.1% (109/110) | mtp-17 |
| 3 | **0.0%** (0/54) | 100.0% (80/80) | 100.0% (16/16) | 100.0% (24/24) | 100.0% (110/110) | 100.0% (87/87) | 99.1% (109/110) | ra-08 |

**对照（不得据此下"改进"结论，模型不同）**：qwen3.7-flash 加固后 3 轮 FMR 0.0%、E2E 90.9–91.8%、Date Binding 98.9%、Query Recall 100%。详见 `docs/expensebench-v2-verification-post-hardening.md`。

## 失败归因

三轮共出现 **2 个不同用例**，且**都是同一个根因**——「水果」的分类口径：

| 用例 | 桶 | 输入 | gold | 实际 | 出现轮次 |
| --- | --- | --- | --- | --- | --- |
| ra-08 | router_ambiguous | 「今天水果30块」 | 3000 / food / 2026-09-06 | 3000 / shopping / 2026-09-06 | 1、3 |
| mtp-17 | multi_temporal | 「昨天买菜80块，今天水果30块」 | 8000·3000 / food | 8000·3000 / shopping | 2 |

- 「买菜」在 1、3 轮判 `food` 正确，仅第 2 轮连带误判为 `shopping` → 属模型抖动；
- 「水果」在 3/3 轮稳定判为 `shopping` → **不是抖动，是口径分歧**。

**这不是 Agent 行为问题**：路由、工具选择、变更笔数、日期绑定、变更精度全部 100%，唯一失分点是分类。

## 下一步（待 owner 裁定）

- **A 改提示词/别名**：把「水果」纳入 `food` 的关键词或别名映射（产品改动，走正常 PR）。
- **B 重裁 gold**：owner 裁定「水果 → shopping」，改数据集。按仓库铁律（评测器 / 数据集 gold / 阈值不顺手改）需**独立 PR + owner 审核 + 重跑全部 baseline**。

## 边界

- 本报告**不替代** `docs/expensebench-v2-llm-report-qwen3.7-flash.md`；
- 未改动评测器、数据集、阈值、冷却与任何生产代码；
- 若要把 deepseek 定为新的协议基准，需 owner 明确并同步 `docs/expensebench-v2.md` 的模型与参数口径。
