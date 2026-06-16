package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {

    fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()

    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<ExpenseEntity>> =
        dao.observeInRange(fromMillis, toMillis)

    suspend fun add(amount: Double, categoryId: String, note: String, occurredAt: Long): Long {
        val now = System.currentTimeMillis()
        return dao.insert(ExpenseEntity(
            amount = amount,
            categoryId = categoryId,
            note = note,
            occurredAt = occurredAt,
            createdAt = now,
        ))
    }

    /**
     * 软删除 — 标记 deletedAt，记录在最近删除里 30 天内可恢复。
     * 命名沿用 delete 是为了不改所有调用点 — 但所有删除路径（滑动删除/LLM action）现在都走软删。
     */
    suspend fun delete(id: Long) = dao.softDeleteById(id)

    /** 从最近删除里恢复。 */
    suspend fun restore(id: Long) = dao.restoreById(id)

    /** 真删（最近删除页面"立即清除"用）。 */
    suspend fun hardDelete(id: Long) = dao.hardDeleteById(id)

    /** App 启动调一次 — 物理清除超过 30 天的软删记录。返回清除条数。 */
    suspend fun purgeExpired(before: Long): Int = dao.hardDeleteExpired(before)

    /** 一键清空最近删除。 */
    suspend fun emptyTrash(): Int = dao.hardDeleteAllTrashed()

    /** 最近删除列表 — 按删除时间倒序。 */
    fun observeTrashed(): Flow<List<ExpenseEntity>> = dao.observeTrashed()

    /** LLM action delete/update/query 用：按 match 条件查候选。详见 [ExpenseDao.findByMatch]。 */
    suspend fun findByMatch(
        category: String?,
        amount: Double?,
        from: Long?,
        to: Long?,
        noteSub: String?,
    ): List<ExpenseEntity> = dao.findByMatch(category, amount, from, to, noteSub)

    /** LLM action update 用：局部 patch 字段。null 表示不改。 */
    suspend fun patch(id: Long, amount: Double?, categoryId: String?, note: String?) =
        dao.patchById(id, amount, categoryId, note)
}
