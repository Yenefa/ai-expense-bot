# Memory Governance v1 — 协议与边界

> 会话状态（v3.9.3 `ConversationActionContext`）与长期记忆（`UserProfile`）是两回事：
> 前者管"刚才那笔 / 那上个月呢 / 说错了"，后者才管月收入、储蓄目标、商户别名、常用分类。
> **LLM 永远不持有长期记忆写接口。**

## 1. 治理路径

```
用户表达
   ↓  （本地确定性检测，不调用模型）
Memory Proposal（草稿 + 挂起，token 一次性）
   ↓  （独立类型校验：金额范围 / 分类合法性 / 字段完整性）
预览（聊天页确认弹窗：类型 + 摘要 + "确认后才保存"）
   ↓  Human Confirm
UserProfile Persist（MemoryGovernor.confirm —— 唯一写入口）
```

- 取消、过期（10 分钟）、重复确认：全部零写入
- 提案轮不发生任何 LLM 调用，也不产生账目写操作
- 拒绝第三方转述（同事/朋友/网上/听说…）与无金额表达

## 2. Memory v1 四类

| 类型 | wire | 需要的字段 | 示例 |
| --- | --- | --- | --- |
| 月收入 | `monthly_income` | amountCents | 「我月收入8000」 |
| 储蓄目标 | `savings_goal` | amountCents | 「每月存2000」「储蓄目标5万」 |
| 商户别名 | `merchant_alias` | merchant + categoryId | 「以后瑞幸都算饮品」 |
| 常用分类 | `category_preference` | categoryId | 「我主要的花销都在吃饭」 |

金额解析支持 元/块/万/千/w/k；金额必须紧邻关键词（防「发工资后打车花了23」被读成月收入 23）。

## 3. MemoryGovernanceBench

- 数据集：`app/src/test/resources/expensebench/memory-cases.jsonl`（36 条：四类各 6 + 拒绝集 12）
- 运行：`./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LocalMemoryGovernanceBenchTest"`（纯本地、零网络）
- 报告：`docs/memory-governance-local-report.md`
- 被测管线：真实 `ExpenseAgent` + 真实 `MemoryGovernor` + stub LLM（证明提案轮 llmCalls = 0）

| 指标 | 口径 |
| --- | --- |
| **Silent Memory Write Rate** | 未经确认写入画像的用例比例。**必须为 0%** |
| 提案准确率 | 期望四类：类型 + 金额/商户/分类一致；期望 null：必须不提案 |
| 路由准确率 | 拒绝集声明 `expect_route` 的用例（提案轮不评路由） |
| 确认后持久化 | confirm 后画像恰好 +1，二次 confirm 被拒 |
| 提案轮调模型 | 期望提案用例中的 LLM 调用次数（必须 0） |

## 4. 已知边界（v1）

- **只保存不消费**：分析/建议/记账默认值暂不读取画像
- 无画像管理页（查看/删除/编辑）——后续版本
- 不处理预算记忆、工资日、定期收入；`年终奖` 等非 monthly_income 表达不提案
- 同句同时出现明确记账与记忆表达时，记忆提案优先（本轮不记账）；建议分开输入
- 存储排除系统备份/设备迁移；App 内 JSON 导出暂不含画像

## 5. 复现

```bash
./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.memory.*" \
  --tests "com.expense.tracker.bench.LocalMemoryGovernanceBenchTest"
```

报告携带 `dataset_sha256` 与运行时间；同哈希数据集 + 同代码 ⇒ 结果一致（无模型参与）。
