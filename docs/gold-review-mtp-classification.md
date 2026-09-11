# Gold 复核：muti_temporal 分类灰区（2026-09-11）

- 背景：加固后真实模型复测（qwen3.7-flash ×3）剩余 5 条分类不一致；owner 逐条给出裁定
- 原则：只改"定义"（提示词/别名/数据集 gold），**不改评分器**；本轮按 owner 指示不重跑 LLM Bench
- 影响：`cases-v2.jsonl` 内容变更 ⇒ `dataset_sha256` 变化；之前 3 轮报告对应旧 sha，属历史证据，新 sha 的首轮 LLM 报告待下次运行时生成

## 裁定与落地

| 用例 | 原话 | 原 gold | 模型输出 | Owner 裁定 | 落地动作 |
| --- | --- | --- | --- | --- | --- |
| mtp-13 | 8月25日买书45块，前天买笔10块 | education | other | **笔=学习** | 提示词补「文具、笔 → education」；gold 不变 |
| mtp-09 | 昨天网购衣服200块，今天买日用品50块 | shopping | other | 维持（日用品=购物） | 提示词补「日用品、超市 → shopping」；gold 不变 |
| mtp-23 | 8月20日交房租2500块，8月25日交水电200块 | housing | other | 维持（水电=住房） | 提示词补「水电、物业、燃气、宽带 → housing」；gold 不变 |
| mtp-29 | 昨天理发40块，今天买日用品80块 | other | shopping | **理发=生活开支** | 现有分类无「生活开支」，映射到最接近的 shopping；提示词补「理发/剪发 → shopping」；**gold 改为 shopping** |
| mtp-30 | 前天买门票120块，昨天订酒店600块 | entertainment | housing | **酒店=住宿** | 提示词补「酒店/住宿 → housing」；**gold 改为 housing** |

## 同步变更

- `LlmPrompt.systemPrompt`：新增 3 条分类映射（文具/笔、日用/理发、水电酒店）
- `LlmPrompt.billImportSystemPrompt`：同步同类映射，避免两条链路不一致
- `AgentCategories`（查询分类过滤器）：education += 文具；shopping += 理发/剪发；housing += 酒店/住宿
- `cases-v2.jsonl`：mtp-29 → shopping，mtp-30 → housing，附裁定 note

## 待办

- 新数据集 sha 下重跑 3 轮 LLM Bench（owner 说不用重跑；待下次运行时顺带验证预期：分类错 5 → 0-1）
- 若"生活开支"实际指「其他」而非「购物」，只需把 mtp-29 gold 与提示词映射各改一个词
