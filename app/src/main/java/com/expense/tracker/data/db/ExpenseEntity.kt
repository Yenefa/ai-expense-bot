package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    val amount: Double,
    val categoryId: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    /** 软删除标记：非 null = 已删除（Unix 毫秒时间戳）；null = 活跃记录。v2.9+ */
    val deletedAt: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
)
