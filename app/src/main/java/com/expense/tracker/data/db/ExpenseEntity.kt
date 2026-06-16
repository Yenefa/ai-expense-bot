package com.expense.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    val amount: Double,
    val categoryId: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    /**
     * 软删除时间戳（epoch millis）。null = 活动记录；非 null = 在"最近删除"里。
     * 30 天后由 [ExpenseDao.hardDeleteExpired] 真正物理清除。
     *
     * v2 新增。Migration：ALTER TABLE expenses ADD COLUMN deletedAt INTEGER DEFAULT NULL。
     */
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
)
