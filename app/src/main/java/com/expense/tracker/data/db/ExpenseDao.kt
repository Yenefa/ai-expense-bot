package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    /** 活动记录（不含已软删的）— 默认 UI/聚合都用这个。 */
    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("""
      SELECT * FROM expenses
      WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to
      ORDER BY occurredAt ASC
    """)
    fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>>

    /**
     * 软删除：标记 deletedAt，记录留在 trash 30 天可恢复。
     * 历史命名沿用 deleteById 是为了不改所有调用点 — 但语义已经变成软删。
     */
    @Query("UPDATE expenses SET deletedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteById(id: Long, at: Long = System.currentTimeMillis())

    /** 兼容旧调用名 — 走软删。 */
    suspend fun deleteById(id: Long) = softDeleteById(id)

    /** 从 trash 恢复（清空 deletedAt）。 */
    @Query("UPDATE expenses SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreById(id: Long)

    /** 真删：从 trash 里彻底移除（"立即清空 / 30 天到期清理"用）。 */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun hardDeleteById(id: Long)

    /** 清理 30 天前的软删记录。App 启动时调一次即可。 */
    @Query("DELETE FROM expenses WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun hardDeleteExpired(before: Long): Int

    /** 一键清空 trash（用户在最近删除页点"全部清空"）。 */
    @Query("DELETE FROM expenses WHERE deletedAt IS NOT NULL")
    suspend fun hardDeleteAllTrashed(): Int

    /** 最近删除列表 — 按删除时间倒序。 */
    @Query("SELECT * FROM expenses WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<ExpenseEntity>>

    /**
     * 按 LLM 给的 match 条件查支出。所有参数都可空，null = 不限制（"(:x IS NULL OR ... )" 模式）。
     * 用 LIMIT 50 兜底防御 LLM 给出全空 match 时拉全表的后果。
     *
     * 注意 amount 用 ABS(... - :amount) < 0.01 做 epsilon 比较，避免浮点直接 = 的精度问题。
     * 注意只查活动记录（不含 trash） — LLM 不应该操作已删除的记录。
     */
    @Query("""
      SELECT * FROM expenses
      WHERE deletedAt IS NULL
        AND (:category IS NULL OR categoryId = :category)
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
