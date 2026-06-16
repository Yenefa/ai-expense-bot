package com.expense.tracker.data.action

import com.expense.tracker.data.db.ExpenseEntity

/**
 * UI 上待用户确认的"操作卡片"状态。
 *
 * - Delete / Update：必须用户在卡片上勾选 + 点确认才会真正写库。
 * - QueryResult：纯只读统计卡片，数据由 App 本地数据库生成，不让 LLM 编排。
 */
sealed interface PendingAction {
    val id: String  // 卡片唯一 key（UUID），用于 UI 列表 + dismiss

    data class Delete(
        override val id: String,
        val candidates: List<ExpenseEntity>,
    ) : PendingAction

    data class Update(
        override val id: String,
        val candidates: List<ExpenseEntity>,
        val patchAmount: Double?,
        val patchCategoryId: String?,
        val patchNote: String?,
    ) : PendingAction

    data class QueryResult(
        override val id: String,
        val title: String,
        val totalAmount: Double,
        val count: Int,
        val rows: List<QueryCategoryRow>,
    ) : PendingAction

    data class QueryCategoryRow(
        val categoryId: String,
        val emoji: String,
        val name: String,
        val amount: Double,
        val count: Int,
        val percent: Double,
    )

    /** 候选为 0 时统一退化成这个，让用户知道"没找到"，但不弹删除按钮 */
    data class Empty(
        override val id: String,
        val message: String,
    ) : PendingAction
}
