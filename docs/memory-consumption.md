# Memory Consumption & Control — 协议

> 完成闭环的第三块：写有确认门（v3.10），读有授权边界，用户可完整管理。
> 路线：Memory Governance v1 ✅ → **Memory Consumption & User Control** → Proactive Insight。

## 1. Memory Read Policy（读权限）

不把整个 UserProfile 塞进所有 system prompt；按任务显式授权：

| Scope | 可读类型 | 使用轮次 |
| --- | --- | --- |
| `NONE` | 无 | 闲聊 |
| `CLASSIFICATION` | `merchant_alias` | 记账（MUTATION）轮 |
| `FINANCIAL_ANALYSIS` | `monthly_income` / `savings_goal` / `category_preference` | 查询与分析（QUERY）轮 |

- 注入形式：`【已授权记忆 · <scope>】` 块；无授权事实时**不产生任何标记**
- **确定性应用**：记账轮把已授权别名注入 prompt，同时在计划层用别名覆盖命中 note 的分类（用户确认过的映射优先于模型猜测）
- 路由联动：「瑞幸15」这类"已知商户 + 裸金额"进入记账轮（分类读权限的应用）；未授权类型绝不进入 prompt

## 2. 用户管理（Memory Screen）

设置 → 🧠 我的记忆：

- 查看已保存记忆（类型 / 摘要 / 来源原文 / 创建时间）
- 修改单条（金额 / 商户 / 分类，编辑后重新走类型校验）
- 删除单条；清空全部（二次确认）
- **删除即失效**：被删除的记忆不会出现在任何 prompt，也不会再参与别名覆盖

## 3. 备份

- 完整 JSON 导出格式升级到 **v3**，新增 `memoryFacts`；v2 旧备份仍可解析（记忆为空）
- 恢复时覆盖当前长期记忆；字段校验（类型/金额/分类）不通过则整包拒绝
- 系统备份/设备迁移继续排除 `user_profile_prefs`

## 4. MemoryConsumptionBench

- 数据集：`app/src/test/resources/expensebench/memory-consumption-cases.jsonl`（38 条）
- 运行：`./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LocalMemoryConsumptionBenchTest"`（纯本地、零网络）
- 报告：`docs/memory-consumption-local-report.md`
- 桶：alias_application 8 / analysis_reads 8 / preference_analysis 4 / unauthorized 8 / deleted_reuse 6 / no_memory 4

| 指标 | 口径 | 目标 |
| --- | --- | --- |
| **Unauthorized Memory Read Rate** | 未授权类型的标记出现在本轮 prompt（或空画像出现授权块）的用例比例 | **0%** |
| Correct Memory Application | 应读类型标记出现；别名用例最终账目分类等于 gold | 100% |
| **Deleted Memory Reuse Rate** | 被删除记忆仍出现在 prompt / 仍覆盖分类的用例比例 | **0%** |

## 5. 已知边界（v1.1）

- 消费端目前是"prompt 注入 + 别名确定性应用"；分析类记忆（收入/储蓄/偏好）尚不参与计算，只作上下文
- 画像管理不做冲突合并（同一类型多条并存；别名按录入顺序命中）
- 无导入去重/迁移合并（恢复为覆盖语义）

## 6. 复现

```bash
./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.memory.*" \
  --tests "com.expense.tracker.bench.LocalMemoryConsumptionBenchTest" \
  --tests "com.expense.tracker.data.export.*"
```

报告携带 `dataset_sha256`；同代码 + 同数据集 ⇒ 结果一致（无模型参与）。
