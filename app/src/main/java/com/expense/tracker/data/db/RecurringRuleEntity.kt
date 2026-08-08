package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 周期账单周期类型。 */
enum class RecurringPeriodType(val label: String) {
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年"),
}

/**
 * 周期账单规则：到 nextDueAt 自动生成一笔账目，然后推进到下一个周期点。
 * 字段约定：
 * - dayOfMonth：MONTHLY/YEARLY 使用（1..31，超出当月天数时取当月最后一天）
 * - dayOfWeek：WEEKLY 使用（1=周一 .. 7=周日）
 * - monthOfYear：YEARLY 使用（1..12）
 */
@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    val amountCents: Long,
    val categoryId: String,
    val note: String,
    val periodType: String,
    val dayOfMonth: Int = 1,
    val dayOfWeek: Int = 1,
    val monthOfYear: Int = 1,
    val nextDueAt: Long,
    val enabled: Boolean = true,
    val createdAt: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
) {
    val period: RecurringPeriodType
        get() = runCatching { RecurringPeriodType.valueOf(periodType) }
            .getOrDefault(RecurringPeriodType.MONTHLY)
}
