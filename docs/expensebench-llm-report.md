# ExpenseBench v1 — LLM 提取精度对比（qwen3.7 家族）

- 数据集：基准时刻 `2026-09-06T12:00:00+08:00`，120 条 / 138 笔预期
- 协议：temperature = 0.0、enable_thinking = false、并发 4、生产同款 system prompt
- dataset_sha256 = `444711bf391e1ebf457223b327a973574ddc4cfee8935035bfa05a79ee70cc30`
- prompt_sha256 = `985b77e28dcf82059a6c7d99965de679a818503ed7500b9dc937e428c3ca58ca`
- ran_at = 2026-09-06 18:56~19:00 (+08:00)，三模型均 **0 次失败请求**
- 说明：`qwen3.7-plus` 免费额度已耗尽未测；其余模型均在免费额度内完成

## 总对比

| 模型 | 金额 | 分类 | 日期 | 商户 | 整笔全对 | 笔数全对 |
| --- | --- | --- | --- | --- | --- | --- |
| qwen3.7-max-2026-06-08 | 100.0% | 95.7% | **97.8%** | 98.0% | **92.8%** | 100.0% |
| qwen3.7-flash | 100.0% | 95.7% | 93.5% | 98.0% | 89.1% | 100.0% |
| qwen3.7-flash-2026-07-15 | 100.0% | 95.7% | 92.8% | 100.0% | 89.1% | 100.0% |

对照：qwen3.7-flash-2026-07-15 **开启思考模式**（enable_thinking 默认开）→ 金额 90.6% / 分类 92.0% / 日期 90.4% / 整笔全对 74.6% / 笔数全对 92.5%（详见 `expensebench-llm-report-qwen3.7-flash-2026-07-15-thinking-on.md`）

## 关键发现

1. **金额与笔数是强项**：三个模型金额准确率全部 100%、笔数全对 100% —— 漏记/多记（记账 App 最致命的错误类型）在当前提示词协议下是低风险事件。
2. **多笔跨日期是最弱桶**：`multi` 桶日期准确率 78.8%~100%（flash 快照 78.8%），「昨天X 今天Y」的逐笔日期归属仍是难点。现有端侧 `ExpenseTextInterpreter` 日期提示恰好覆盖这类形态，两层互补的设计得到数据支持。
3. **思考模式是负优化，不是精度提升**：同一模型快照打开 `enable_thinking`，整笔全对 89.1% → 74.6%，金额 100% → 90.6%，笔数全对 100% → 92.5%。机理：长思维链挤占输出预算导致 JSON 被截断 → 漏笔。结论：**结构化记账任务必须关闭思考**——对 BYOK 接 Qwen 的用户这是重要配置建议（顺带延迟约 10 倍差距：120 条 7 分钟 vs 1 分钟）。
4. **flash 别名 vs 日期快照**：overall 几乎一致（日期 93.5% vs 92.8%），当前漂移很小；但按可复现协议仍建议报告绑定快照别名。
5. **分类错误集中在口径边界**：merchant 桶分类 92%（话费/会员/商户名与分类的边界争议），v2 扩数据时需要先澄清标注规则而不是加量。

## 建议

- 生产推荐：精度优先 `qwen3.7-max-2026-06-08`（唯一在 multi 桶拿到日期 100% 的模型），成本敏感用 `qwen3.7-flash-2026-07-15`
- BYOK 接 Qwen 思考模型时，客户端应显式发送 `enable_thinking=false`（`ChatCompletionRequest.enableThinking` 已支持，null 时不发送、其他供应商不受影响）
- Bench v2 扩充方向（对准本次失败模式）：multi 跨日期桶加量、分类边界标注规则澄清、口语金额（「2块5」类）——不做无差别加量

## 分桶明细（链接）

- [qwen3.7-max-2026-06-08](expensebench-llm-report-qwen3.7-max-2026-06-08.md)
- [qwen3.7-flash](expensebench-llm-report-qwen3.7-flash.md)
- [qwen3.7-flash-2026-07-15](expensebench-llm-report-qwen3.7-flash-2026-07-15.md)
- [qwen3.7-flash-2026-07-15（思考开启对照）](expensebench-llm-report-qwen3.7-flash-2026-07-15-thinking-on.md)
