# Proactive Insight v1 — 协议与边界

> 主动智能建立在已闭环的治理层之上：**规则决定"该不该提醒"，LLM 只负责"怎么说"。**
> 模型无权判断异常、无权决定发送、无权绕过每日额度。

## 1. 三类提醒（v1）

| 类型 | 判定（全部端侧确定性） | 关键参数 |
| --- | --- | --- |
| `budget_threshold` | 本月非投资消费 ≥ 预算 90% → WARN；超过预算 → OVER | 预算需已设置 |
| `anomalous_spending` | 本周消费（或某一分类）≥ 近 4 个完整周基线均值 × 1.5，且高出 ≥ ¥100；总量正常时按分类级基线兜底（多分类同时异常只报偏离最大的一个） | 4 个可比样本 |
| `savings_goal_deviation` | 按本月 pace 外推预计结余 < 储蓄目标 → WARN；≤0 → OVER | 需已确认月收入 + 储蓄目标 |

同时触发时按固定优先级只出一条：**BUDGET（OVER > WARN）> SAVINGS > ANOMALY**。

## 2. 硬约束（治理层）

- **至少 4 个可比样本**：异常需 4 个有数据的完整周；预算/储蓄需本月 ≥4 笔记录；储蓄还需 ≥4 个已过天数
- **冷启动不提醒**：样本不足、未设预算、缺少收入/储蓄记忆 → 一律不提醒
- **每日最多 1 条**
- **同类冷却**：预算 24h、异常/储蓄 7d；预算 WARN→OVER **升级可突破冷却**（hysteresis），OVER→OVER 不可
- **用户可关闭**：设置 → 🔮 主动提醒，逐类开关（默认全开）；关闭的类型不参与候选、也不阻断其他类型
- **LLM 边界**：规则触发后才调用 copywriter（可选）；输出超长/含换行/失败 → 回退确定性文案；文案层无法阻止或触发提醒
- **检查与落库原子**：前台聊天轮与后台 Worker 共用进程级互斥（`ProactiveGovernor.COMMIT_LOCK`），并发时只有一个能通过"每日 1 条 / 冷却"并落库，不会重复投递

## 3. ProactiveInsightBench

- 数据集：`app/src/test/resources/expensebench/proactive-cases.jsonl`（38 条六桶：should_alert 14 / no_trigger 5 / suppressed_cold_start 6 / suppressed_cooldown 5 / suppressed_daily_budget 5 / suppressed_disabled 3）
- 运行：`./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.bench.LocalProactiveInsightBenchTest"`（纯本地、零网络）
- 报告：`docs/proactive-insight-local-report.md`

| 指标 | 口径 | 目标 |
| --- | --- | --- |
| False Alert Rate | 不应提醒却触发 / 全部抑制用例 | **0%** |
| Missed Alert Rate | 应提醒却未触发 / 应提醒用例 | **0%** |
| Duplicate Alert Rate | 冷却期内重复触发 / 冷却用例 | **0%** |
| Cold-start Violation Rate | 样本不足仍触发 / 冷启动用例 | **0%** |
| Notification Budget Violation Rate | 每日额度已满仍触发 / 额度用例 | **0%** |

## 4. 交付形态（v3.13 / v3.14）

- **提醒中心历史**：规则放行即写入 `ProactivePrefs`（最近 50 条，含最终文案，可清空）；设置 → 📣 提醒中心可查看，入口显示未读数（进页已读）
- **聊天内消息**：前台记账成功且规则触发时追加一条"🔔 …"
- **系统通知**：独立渠道 `proactive_insight`，点击直达提醒中心；`VISIBILITY_PRIVATE` + 锁屏泛化文案（不暴露金额）；Android 13+ 需要通知权限，权限缺失时该次不投递（打开主动提醒页有横幅"开启"入口）
- **启动检查**：App 启动时评估一次（与记账后、后台检查共用同一治理器，额度/冷却兜底，不重复）
- **后台检查**：WorkManager `PeriodicWorkRequest`（周期 1 天，首次延迟到下一个 20:00，`KEEP` 不重置周期，重启自动恢复）；**后台只用确定性文案（copywriter=null，零网络）**，避免后台产生 API 成本；通知权限缺失时跳过评估，不消耗当日额度
- 无论何种形态，决策仍只来自本协议
- 储蓄规则自 v3.13 起与查询分析共用 `SavingsPaceCalculator`（`data/finance`），不再各自手算

## 5. 已知边界

- 异常检测：v3.14 已支持分类级基线；**商户级**基线未做
- 储蓄外推用线性 pace（含"剩余 N 天最多还能花"细化），不区分工作日/节假日
- 前台文案可接 LLM 改写；后台通知固定确定性文案；真实模型复测待 owner 指令

## 6. 复现

```bash
./gradlew :app:testDebugUnitTest --tests "com.expense.tracker.proactive.*" \
  --tests "com.expense.tracker.bench.LocalProactiveInsightBenchTest"
```

报告携带 `dataset_sha256`；同代码 + 同数据集 ⇒ 结果一致（文案层为 stub，无模型参与）。
