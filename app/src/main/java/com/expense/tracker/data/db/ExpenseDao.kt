package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    /** 活跃记录（deleteAt IS NULL）：列表/分析/历史均用此查询 */
    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeActive(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY occurredAt ASC")
    suspend fun getAllActiveOnce(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to AND deletedAt IS NULL ORDER BY occurredAt ASC")
    fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>>

    /** 已删除记录（最近删除列表），按删除时间倒序 */
    @Query("SELECT * FROM expenses WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<ExpenseEntity>>

    /** 软删除：标记 deletedAt 为当前时间，不移除行 */
    @Query("UPDATE expenses SET deletedAt = :deletedAtMillis WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAtMillis: Long)

    /** 恢复：把 deletedAt 置为 null */
    @Query("UPDATE expenses SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreById(id: Long)

    /** 彻底删除（从表中移除行） */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清理超过 retainDays 天的软删除项（后台任务 / 手动触发） */
    @Query("DELETE FROM expenses WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long)
}
