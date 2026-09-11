package com.expense.tracker.data.finance

/**
 * 储蓄节奏（savings pace）：由**已确认画像**（月收入 / 储蓄目标）与本月已发生的
 * 消费确定性计算，供查询分析（信任数据块）与主动提醒（储蓄规则）共用。
 *
 * 计算永远在端侧完成；LLM 只允许转述，不允许自行推算。
 */
data class SavingsPace(
    val incomeCents: Long,
    val goalCents: Long,
    val spentCents: Long,
    val elapsedDays: Int,
    val daysInMonth: Int,
    /** 按当前日均外推的月末消费。 */
    val projectedSpendCents: Long,
    /** 预计月末结余 = 月收入 - 预计消费。 */
    val projectedLeftoverCents: Long,
    /** 预计结余 - 储蓄目标；<0 表示预计达不到目标。 */
    val goalGapCents: Long,
    val remainingDays: Int,
    /** 静态口径：在仍要达成目标的前提下，本月还可支配的金额（可为负 = 已超）。 */
    val remainingSpendableCents: Long,
    /** 是否达标：预计结余 >= 目标。 */
    val onTrack: Boolean,
)

object SavingsPaceCalculator {

    /**
     * 输入不完整或非法时返回 null（收入/目标必须为正、已过天数必须在 1..当月天数 内）。
     * 样本门槛由调用方决定：主动提醒要求 ≥4 天且 ≥4 笔；查询侧只要求时间合法。
     */
    fun compute(
        incomeCents: Long,
        goalCents: Long,
        spentCents: Long,
        elapsedDays: Int,
        daysInMonth: Int,
    ): SavingsPace? {
        if (incomeCents <= 0L || goalCents <= 0L) return null
        if (elapsedDays <= 0 || daysInMonth <= 0 || elapsedDays > daysInMonth) return null
        if (spentCents < 0L) return null
        val projectedSpend = spentCents.toDouble() / elapsedDays * daysInMonth
        val projectedLeftover = incomeCents - projectedSpend
        return SavingsPace(
            incomeCents = incomeCents,
            goalCents = goalCents,
            spentCents = spentCents,
            elapsedDays = elapsedDays,
            daysInMonth = daysInMonth,
            projectedSpendCents = projectedSpend.toLong(),
            projectedLeftoverCents = projectedLeftover.toLong(),
            goalGapCents = (projectedLeftover - goalCents).toLong(),
            remainingDays = daysInMonth - elapsedDays,
            remainingSpendableCents = incomeCents - goalCents - spentCents,
            onTrack = projectedLeftover >= goalCents,
        )
    }

    /** 达标所需日均支出上限 = (收入 - 目标) / 当月天数；目标高于收入时为 0。 */
    fun requiredDailySpendCents(incomeCents: Long, goalCents: Long, daysInMonth: Int): Long {
        if (daysInMonth <= 0) return 0L
        return ((incomeCents - goalCents).coerceAtLeast(0L)) / daysInMonth
    }
}
