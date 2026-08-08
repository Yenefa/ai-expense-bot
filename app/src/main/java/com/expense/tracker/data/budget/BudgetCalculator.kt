package com.expense.tracker.data.budget

/** 预算状态：正常 / 接近上限（≥90%）/ 超支（>100%） */
enum class BudgetStatus(val colorKey: String) {
    OK("ok"),
    WARN("warn"),
    OVER("over"),
}

data class BudgetEntry(
    val label: String,
    val amountCents: Long,
    val limitCents: Long,
    /** 0..∞，1.0 = 100% */
    val percent: Double,
    val status: BudgetStatus,
)

data class BudgetOverview(
    val monthly: BudgetEntry?,
    val categories: List<BudgetEntry>,
) {
    /** 整体取最严重状态；未设置任何预算返回 null。 */
    val worstStatus: BudgetStatus? = run {
        val candidates = buildList {
            monthly?.let { add(it) }
            addAll(categories.filter { it.limitCents > 0L })
        }
        candidates.maxByOrNull { it.status.ordinal }?.status
    }
}

/**
 * 预算计算（纯函数）：本月支出按分类聚合后与预算对比。
 * 规则：≥100% 超支红警；≥90% 接近上限黄警。
 */
object BudgetCalculator {

    fun overview(
        monthlyLimitCents: Long,
        categoryLimitsCents: Map<String, Long>,
        monthlySpentByCategory: Map<String, Long>,
    ): BudgetOverview {
        val monthlySpent = monthlySpentByCategory.values.sum()
        val monthly = if (monthlyLimitCents > 0L) {
            BudgetEntry(
                label = "月度总预算",
                amountCents = monthlySpent,
                limitCents = monthlyLimitCents,
                percent = ratio(monthlySpent, monthlyLimitCents),
                status = statusOf(monthlySpent, monthlyLimitCents),
            )
        } else {
            null
        }

        val categories = categoryLimitsCents
            .filterValues { it > 0L }
            .map { (categoryId, limit) ->
                val spent = monthlySpentByCategory[categoryId] ?: 0L
                BudgetEntry(
                    label = categoryId,
                    amountCents = spent,
                    limitCents = limit,
                    percent = ratio(spent, limit),
                    status = statusOf(spent, limit),
                )
            }

        return BudgetOverview(monthly, categories)
    }

    fun ratio(spentCents: Long, limitCents: Long): Double =
        if (limitCents <= 0L) 0.0 else spentCents.toDouble() / limitCents

    fun statusOf(spentCents: Long, limitCents: Long): BudgetStatus {
        if (limitCents <= 0L) return BudgetStatus.OK
        val percent = spentCents.toDouble() / limitCents
        return when {
            percent >= 1.0 -> BudgetStatus.OVER
            percent >= 0.9 -> BudgetStatus.WARN
            else -> BudgetStatus.OK
        }
    }
}
