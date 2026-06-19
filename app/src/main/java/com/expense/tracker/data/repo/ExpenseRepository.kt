package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {

    /** 活跃记录（给聊天列表、分析、历史用） */
    fun observeActive(): Flow<List<ExpenseEntity>> = dao.observeActive()

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

    /** 硬删除（彻底移除），v2.8 及以前的行为。 */
    suspend fun hardDelete(id: Long) = dao.deleteById(id)

    /** 软删除：标记时间戳，保留行。 */
    suspend fun softDelete(id: Long) = dao.softDeleteById(id, System.currentTimeMillis())

    /** 恢复软删除的记录。 */
    suspend fun restore(id: Long) = dao.restoreById(id)

    /** 冷删除记录列表（观察流）。 */
    fun observeDeleted(): Flow<List<ExpenseEntity>> = dao.observeDeleted()

    /** 彻底删除指定 id。 */
    suspend fun purge(id: Long) = dao.deleteById(id)

    /** 清理 N 天前软删除的过期记录。 */
    suspend fun purgeOlderThan(cutoffMillis: Long) = dao.purgeOlderThan(cutoffMillis)

    suspend fun update(expense: ExpenseEntity) = dao.update(expense)

    suspend fun getById(id: Long): ExpenseEntity? = dao.getById(id)

    /** 一次拉取全部活跃记录（给导出用）。 */
    suspend fun getAllActiveOnce(): List<ExpenseEntity> = dao.getAllActiveOnce()
}
