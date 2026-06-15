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

    suspend fun delete(id: Long) = dao.deleteById(id)

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
