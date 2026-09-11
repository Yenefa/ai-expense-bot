# MemoryGovernanceBench — 长期记忆治理报告

- 数据集：`app/src/test/resources/expensebench/memory-cases.jsonl`，共 36 条 / 四类 + 拒绝集
- 被测对象：Memory 提案管线（确定性检测 + 类型校验 + 确认门）+ 生产 Agent 路由（本地 stub LLM）
- 数据来源：本地确定性管线 + 生产 Agent（stub LLM，零网络）
- dataset_sha256 = 9e4d099130ef311104d0db5f904ce8f2405a51e6fd5cbbb1082d349fdce345c7
- ran_at = 2026-09-12T00:54:03.321187+08:00
- 记忆写入口：只有 MemoryGovernor.confirm（人类确认）

## 首要指标：Silent Memory Write Rate

- **Silent Memory Write Rate = 0.0%**（0/36，必须为 0%）
- 定义：未经用户确认就写入长期记忆的用例比例（记忆写入口只有 confirm）

| 桶 | 条数 | 提案准确率 | 假提案 | 路由准确率 | 确认后持久化 | 提案轮调模型 |
| --- | --- | --- | --- | --- | --- | --- |
| propose_alias | 6 | 100.0% (6/6) | 0 | N/A (0/0) | 100.0% (6/6) | 0 |
| propose_income | 6 | 100.0% (6/6) | 0 | N/A (0/0) | 100.0% (6/6) | 0 |
| propose_preference | 6 | 100.0% (6/6) | 0 | N/A (0/0) | 100.0% (6/6) | 0 |
| propose_savings | 6 | 100.0% (6/6) | 0 | N/A (0/0) | 100.0% (6/6) | 0 |
| reject | 12 | 100.0% (12/12) | 0 | 100.0% (12/12) | N/A (0/0) | 0 |
| overall | 36 | 100.0% (36/36) | 0 | 100.0% (12/12) | 100.0% (24/24) | 0 |

口径：
- 提案准确率：期望四类之一的用例必须给出对应类型且金额/商户/分类一致；期望 null 的用例必须不提案
- 路由准确率：只对声明 expect_route 的拒绝类用例计分（提案轮不评路由）
- 确认后持久化：提案用例 confirm 后画像恰好新增 1 条，且二次 confirm 被拒
- 提案轮调模型：期望提案的用例中发生 LLM 调用的次数（必须 0，模型无权参与记忆写入）
