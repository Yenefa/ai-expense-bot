package com.expense.tracker.proactive

import com.expense.tracker.data.finance.SavingsPaceCalculator
import com.expense.tracker.data.model.Category

/** 主动提醒类型（v1 只做三类）。 */
enum class ProactiveAlertType(val wire: String, val typeLabel: String) {
    BUDGET_THRESHOLD("budget_threshold", "预算临界"),
    ANOMALOUS_SPENDING("anomalous_spending", "异常消费"),
    SAVINGS_GOAL_DEVIATION("savings_goal_deviation", "储蓄目标偏离"),
    ;

    companion object {
        fun fromWire(value: String): ProactiveAlertType? = entries.firstOrNull { it.wire == value }
    }
}

enum class ProactiveSeverity(val wire: String) {
    WARN("warn"),
    OVER("over"),
    ;

    companion object {
        fun fromWire(value: String): ProactiveSeverity? = entries.firstOrNull { it.wire == value }
    }
}

/** 分类级周消费（旧→新 4 个完整周 + 本周），用于分类级异常基线。 */
data class CategoryWeeklySpending(
    val categoryId: String,
    /** 本周（进行中）该分类消费合计。 */
    val currentWeekCents: Long,
    /** 最近 4 个完整周该分类消费合计（旧→新）；0 = 该周无数据。 */
    val completedWeeksCents: List<Long>,
)

/** 商户/备注级周消费（旧→新 4 个完整周 + 本周），用于商户级异常基线。 */
data class MerchantWeeklySpending(
    val note: String,
    /** 本周（进行中）该商户（note）消费合计。 */
    val currentWeekCents: Long,
    /** 最近 4 个完整周该商户消费合计（旧→新）；0 = 该周无数据。 */
    val completedWeeksCents: List<Long>,
)

/** 规则输入（全部为端侧确定性事实；memory 字段由调用方按 FINANCIAL_ANALYSIS 授权读取后传入）。 */
data class ProactiveInputs(
    val nowMillis: Long,
    val zone: java.time.ZoneId,
    /** 本月预算（0 = 未设置）。 */
    val monthlyLimitCents: Long = 0L,
    /** 本月非投资消费合计。 */
    val monthSpentCents: Long = 0L,
    /** 本月非投资消费记录数（样本门槛用）。 */
    val monthRecordCount: Int = 0,
    val elapsedMonthDays: Int = 0,
    val daysInMonth: Int = 0,
    /** 本周（进行中）非投资消费合计。 */
    val currentWeekSpentCents: Long = 0L,
    /** 最近 4 个完整周的非投资消费合计（旧→新）；0 = 该周无数据。 */
    val completedWeekSpendsCents: List<Long> = emptyList(),
    /** 分类级周消费（可选）：总消费被其他分类抵消时，仍能发现单一分类异常。 */
    val categoryWeekSpends: List<CategoryWeeklySpending> = emptyList(),
    /** 商户/备注级周消费（可选）：总量与分类都被摊平时，仍能发现单一商户异常。 */
    val merchantWeekSpends: List<MerchantWeeklySpending> = emptyList(),
    /** 已授权读取的财务记忆。 */
    val monthlyIncomeCents: Long? = null,
    val savingsGoalCents: Long? = null,
)

/** 规则决策结果：结构化事实 + 确定性文案；文案可被 LLM 改写，但决策不可。 */
data class ProactiveAlert(
    val type: ProactiveAlertType,
    val severity: ProactiveSeverity,
    val facts: Map<String, Long>,
    val deterministicCopy: String,
    /** 最终展示文案：默认等于确定性文案；LLM 只可覆盖这一字段。 */
    val copy: String = deterministicCopy,
)

/** 提醒中心/历史记录：每次真正产出的提醒（治理已放行）记一条。 */
data class ProactiveAlertRecord(
    val type: ProactiveAlertType,
    val severity: ProactiveSeverity,
    val copy: String,
    val createdAtMillis: Long,
)

/** 规则引擎只做"该不该提醒"；LLM 无权参与该判断。 */
object ProactiveRules {

    /** 硬约束：至少 4 个可比历史样本；冷启动一律不提醒。 */
    const val MIN_COMPARABLE_SAMPLES = 4

    /** 异常判定阈值：本周 >= 基线均值的 1.5 倍且至少高出 ¥100。 */
    const val ANOMALY_RATIO = 1.5
    const val ANOMALY_MIN_DELTA_CENTS = 10_000L

    fun evaluate(
        inputs: ProactiveInputs,
        allowed: Set<ProactiveAlertType> = ProactiveAlertType.entries.toSet(),
    ): ProactiveAlert? {
        if (allowed.isEmpty()) return null
        val candidates = buildList {
            budgetAlert(inputs)?.let(::add)
            savingsAlert(inputs)?.let(::add)
            anomalyAlert(inputs)?.let(::add)
        }
        // 每天只发一条：固定优先级 BUDGET > SAVINGS > ANOMALY（同类型内 OVER > WARN）；
        // 被用户关闭的类型不参与候选，也不阻断其他已开启规则。
        return candidates.filter { it.type in allowed }.minByOrNull { priority(it) }
    }

    private fun priority(alert: ProactiveAlert): Int = when (alert.type) {
        ProactiveAlertType.BUDGET_THRESHOLD -> if (alert.severity == ProactiveSeverity.OVER) 0 else 1
        ProactiveAlertType.SAVINGS_GOAL_DEVIATION -> if (alert.severity == ProactiveSeverity.OVER) 2 else 3
        ProactiveAlertType.ANOMALOUS_SPENDING -> 4
    }

    private fun budgetAlert(inputs: ProactiveInputs): ProactiveAlert? {
        if (inputs.monthlyLimitCents <= 0L) return null
        if (inputs.monthRecordCount < MIN_COMPARABLE_SAMPLES) return null
        val spent = inputs.monthSpentCents
        val percent = spent.toDouble() / inputs.monthlyLimitCents
        if (percent < 0.9) return null
        val over = spent > inputs.monthlyLimitCents
        val copy = if (over) {
            "本月已超支 ¥${fmt(spent - inputs.monthlyLimitCents)}（预算 ¥${fmt(inputs.monthlyLimitCents)}），建议查看明细。"
        } else {
            "本月预算已用 ${(percent * 100).toInt()}%（¥${fmt(spent)} / ¥${fmt(inputs.monthlyLimitCents)}），注意控制。"
        }
        return ProactiveAlert(
            type = ProactiveAlertType.BUDGET_THRESHOLD,
            severity = if (over) ProactiveSeverity.OVER else ProactiveSeverity.WARN,
            facts = mapOf(
                "limit_cents" to inputs.monthlyLimitCents,
                "spent_cents" to spent,
                "percent" to (percent * 100).toLong(),
            ),
            deterministicCopy = copy,
        )
    }

    private fun savingsAlert(inputs: ProactiveInputs): ProactiveAlert? {
        val income = inputs.monthlyIncomeCents ?: return null
        val goal = inputs.savingsGoalCents ?: return null
        if (inputs.monthRecordCount < MIN_COMPARABLE_SAMPLES) return null
        if (inputs.elapsedMonthDays < MIN_COMPARABLE_SAMPLES) return null
        // P3：储蓄规则与查询分析共用同一确定性计算（SavingsPaceCalculator），不再各自手算。
        val pace = SavingsPaceCalculator.compute(
            incomeCents = income,
            goalCents = goal,
            spentCents = inputs.monthSpentCents,
            elapsedDays = inputs.elapsedMonthDays,
            daysInMonth = inputs.daysInMonth,
        ) ?: return null
        if (pace.onTrack) return null
        val over = pace.projectedLeftoverCents <= 0L
        val copy = if (over) {
            "按当前节奏，本月预计入不敷出 ¥${fmt(-pace.projectedLeftoverCents)}（储蓄目标 ¥${fmt(goal)} 可能落空）；剩余 ${pace.remainingDays} 天，建议压降支出。"
        } else {
            "按当前节奏，本月预计可存 ¥${fmt(pace.projectedLeftoverCents)}，低于储蓄目标 ¥${fmt(goal)}（还差 ¥${fmt(-pace.goalGapCents)}）；" +
                "剩余 ${pace.remainingDays} 天，最多还能花 ¥${fmt(pace.remainingSpendableCents.coerceAtLeast(0L))}" +
                "（日均 ¥${fmt(pace.remainingDailyBudgetCents)}）。"
        }
        return ProactiveAlert(
            type = ProactiveAlertType.SAVINGS_GOAL_DEVIATION,
            severity = if (over) ProactiveSeverity.OVER else ProactiveSeverity.WARN,
            facts = mapOf(
                "income_cents" to income,
                "goal_cents" to goal,
                "projected_spend_cents" to pace.projectedSpendCents,
                "projected_leftover_cents" to pace.projectedLeftoverCents,
                "goal_gap_cents" to pace.goalGapCents,
                "remaining_days" to pace.remainingDays.toLong(),
                "remaining_spendable_cents" to pace.remainingSpendableCents,
                "remaining_daily_cents" to pace.remainingDailyBudgetCents,
            ),
            deterministicCopy = copy,
        )
    }

    private fun anomalyAlert(inputs: ProactiveInputs): ProactiveAlert? {
        totalAnomaly(inputs)?.let { return it }
        // 分类级基线：总消费被其他分类"抵消"时（总量正常但某个分类暴涨）仍能发现异常；
        // 多个分类同时异常时只报偏离金额最大的一个。
        val worst = inputs.categoryWeekSpends.mapNotNull { spending ->
            weeklyAnomaly(spending.currentWeekCents, spending.completedWeeksCents)
                ?.let { (baseline, delta) -> Triple(spending, baseline, delta) }
        }.maxByOrNull { it.third } ?: return merchantAnomaly(inputs)

        val (spending, baseline, delta) = worst
        val name = Category.byIdOrOther(spending.categoryId).displayName
        return ProactiveAlert(
            type = ProactiveAlertType.ANOMALOUS_SPENDING,
            severity = ProactiveSeverity.WARN,
            facts = mapOf(
                "current_week_cents" to spending.currentWeekCents,
                "baseline_cents" to baseline,
                "delta_cents" to delta,
            ),
            deterministicCopy = "本周「$name」消费 ¥${fmt(spending.currentWeekCents)}，明显高于近 4 周平均 ¥${fmt(baseline)}，建议看看是哪几笔。",
        )
    }

    private fun totalAnomaly(inputs: ProactiveInputs): ProactiveAlert? {
        val current = inputs.currentWeekSpentCents
        val (baseline, delta) = weeklyAnomaly(current, inputs.completedWeekSpendsCents) ?: return null
        return ProactiveAlert(
            type = ProactiveAlertType.ANOMALOUS_SPENDING,
            severity = ProactiveSeverity.WARN,
            facts = mapOf(
                "current_week_cents" to current,
                "baseline_cents" to baseline,
                "delta_cents" to delta,
            ),
            deterministicCopy = "本周消费 ¥${fmt(current)}，明显高于近 4 周平均 ¥${fmt(baseline)}，建议看看是哪几笔。",
        )
    }

    /**
     * 周消费异常判定（分类级 / 商户级共用，与总量级同口径）：
     * ≥[MIN_COMPARABLE_SAMPLES] 个非零样本，本周 ≥ 基线均值 ×[ANOMALY_RATIO] 且高出 ≥ ¥100。
     * 返回（基线均值，高出金额）。
     */
    private fun weeklyAnomaly(currentCents: Long, completedWeeksCents: List<Long>): Pair<Long, Long>? {
        if (completedWeeksCents.isEmpty()) return null
        val samples = completedWeeksCents.count { it > 0L }
        if (samples < MIN_COMPARABLE_SAMPLES) return null
        val baseline = completedWeeksCents.sum().toDouble() / completedWeeksCents.size
        if (baseline <= 0.0) return null
        val delta = currentCents - baseline.toLong()
        if (currentCents < baseline * ANOMALY_RATIO || delta < ANOMALY_MIN_DELTA_CENTS) return null
        return baseline.toLong() to delta
    }

    /** 商户级兜底：总量与分类正常时，单个商户（note）暴涨；名称过长只截断展示，不影响判定。 */
    private fun merchantAnomaly(inputs: ProactiveInputs): ProactiveAlert? {
        val worst = inputs.merchantWeekSpends.mapNotNull { spending ->
            weeklyAnomaly(spending.currentWeekCents, spending.completedWeeksCents)
                ?.let { (baseline, delta) -> Triple(spending, baseline, delta) }
        }.maxByOrNull { it.third } ?: return null

        val (spending, baseline, delta) = worst
        val name = spending.note.take(MAX_MERCHANT_LABEL_CHARS)
        return ProactiveAlert(
            type = ProactiveAlertType.ANOMALOUS_SPENDING,
            severity = ProactiveSeverity.WARN,
            facts = mapOf(
                "current_week_cents" to spending.currentWeekCents,
                "baseline_cents" to baseline,
                "delta_cents" to delta,
            ),
            deterministicCopy = "本周在「$name」消费 ¥${fmt(spending.currentWeekCents)}，明显高于近 4 周平均 ¥${fmt(baseline)}，建议看看是哪几笔。",
        )
    }

    /** 商户名在文案中的最大展示长度（字符）；超长截断仅影响展示。 */
    const val MAX_MERCHANT_LABEL_CHARS = 12

    private fun fmt(cents: Long): String = com.expense.tracker.data.model.Money.formatYuan(cents)
}
