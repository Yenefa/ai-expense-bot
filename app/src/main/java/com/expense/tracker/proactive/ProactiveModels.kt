package com.expense.tracker.proactive

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
        if (income <= 0L || goal <= 0L) return null
        if (inputs.monthRecordCount < MIN_COMPARABLE_SAMPLES) return null
        if (inputs.elapsedMonthDays < MIN_COMPARABLE_SAMPLES || inputs.daysInMonth <= 0) return null
        val projectedSpend = inputs.monthSpentCents.toDouble() / inputs.elapsedMonthDays * inputs.daysInMonth
        val projectedLeftover = income - projectedSpend
        if (projectedLeftover >= goal) return null
        val over = projectedLeftover <= 0.0
        val copy = if (over) {
            "按当前节奏，本月预计入不敷出 ¥${fmt((-projectedLeftover).toLong())}，储蓄目标 ¥${fmt(goal)} 可能落空。"
        } else {
            "按当前节奏，本月预计可存 ¥${fmt(projectedLeftover.toLong())}，低于储蓄目标 ¥${fmt(goal)}。"
        }
        return ProactiveAlert(
            type = ProactiveAlertType.SAVINGS_GOAL_DEVIATION,
            severity = if (over) ProactiveSeverity.OVER else ProactiveSeverity.WARN,
            facts = mapOf(
                "income_cents" to income,
                "goal_cents" to goal,
                "projected_leftover_cents" to projectedLeftover.toLong(),
            ),
            deterministicCopy = copy,
        )
    }

    private fun anomalyAlert(inputs: ProactiveInputs): ProactiveAlert? {
        val weeks = inputs.completedWeekSpendsCents
        val samples = weeks.count { it > 0L }
        if (samples < MIN_COMPARABLE_SAMPLES) return null
        val baseline = weeks.sum().toDouble() / weeks.size
        if (baseline <= 0.0) return null
        val current = inputs.currentWeekSpentCents
        val delta = current - baseline.toLong()
        if (current < baseline * ANOMALY_RATIO || delta < ANOMALY_MIN_DELTA_CENTS) return null
        return ProactiveAlert(
            type = ProactiveAlertType.ANOMALOUS_SPENDING,
            severity = ProactiveSeverity.WARN,
            facts = mapOf(
                "current_week_cents" to current,
                "baseline_cents" to baseline.toLong(),
                "delta_cents" to delta,
            ),
            deterministicCopy = "本周消费 ¥${fmt(current)}，明显高于近 4 周平均 ¥${fmt(baseline.toLong())}，建议看看是哪几笔。",
        )
    }

    private fun fmt(cents: Long): String = com.expense.tracker.data.model.Money.formatYuan(cents)
}
