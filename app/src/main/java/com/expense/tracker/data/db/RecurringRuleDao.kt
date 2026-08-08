package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Insert
    suspend fun insertAll(rules: List<RecurringRuleEntity>)

    @Query("DELETE FROM recurring_rules")
    suspend fun clearAll()

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("SELECT * FROM recurring_rules ORDER BY nextDueAt ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE enabled = 1 AND nextDueAt <= :nowMillis ORDER BY nextDueAt ASC")
    suspend fun getEnabledDue(nowMillis: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
