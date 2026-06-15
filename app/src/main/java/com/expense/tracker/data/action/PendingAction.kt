package com.expense.tracker.data.action

import com.expense.tracker.data.db.ExpenseEntity

/**
 * UI 上待用户确认的"操作卡片"状态。
 *
 * - Delete / Update：必须用户在卡片上勾选 + 点确认才会真正写库。
 * - QueryResult：纯只读，已经是渲染好的字符串，渲染完即可移除。
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
        val text: String,
    ) : PendingAction

    /** 候选为 0 时统一退化成这个，让用户知道"没找到"，但不弹删除按钮 */
    data class Empty(
        override val id: String,
        val message: String,
    ) : PendingAction
}
