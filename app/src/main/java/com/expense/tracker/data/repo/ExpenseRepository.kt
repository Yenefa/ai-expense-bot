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
}
