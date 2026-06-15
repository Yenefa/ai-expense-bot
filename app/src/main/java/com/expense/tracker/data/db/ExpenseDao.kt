package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt ASC")
    fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 按 LLM 给的 match 条件查支出。所有参数都可空，null = 不限制（"(:x IS NULL OR ... )" 模式）。
     * 用 LIMIT 50 兜底防御 LLM 给出全空 match 时拉全表的后果。
     *
     * 注意 amount 用 ABS(... - :amount) < 0.01 做 epsilon 比较，避免浮点直接 = 的精度问题。
     */
    @Query("""
      SELECT * FROM expenses
      WHERE (:category IS NULL OR categoryId = :category)
        AND (:amount   IS NULL OR ABS(amount - :amount) < 0.01)
        AND (:from     IS NULL OR occurredAt >= :from)
        AND (:to       IS NULL OR occurredAt <  :to)
        AND (:noteSub  IS NULL OR note LIKE '%' || :noteSub || '%')
      ORDER BY occurredAt DESC LIMIT 50
    """)
    suspend fun findByMatch(
        category: String?,
        amount: Double?,
        from: Long?,
        to: Long?,
        noteSub: String?,
    ): List<ExpenseEntity>

    /**
     * 按 id 局部更新支出字段。null 参数表示"不改该字段"，由 SQL COALESCE 守住。
     * 用于 update action：LLM 只说改金额，不影响 category/note 原值。
     */
    @Query("""
      UPDATE expenses SET
        amount     = COALESCE(:amount, amount),
        categoryId = COALESCE(:cat, categoryId),
        note       = COALESCE(:note, note)
      WHERE id = :id
    """)
    suspend fun patchById(id: Long, amount: Double?, cat: String?, note: String?)
}
