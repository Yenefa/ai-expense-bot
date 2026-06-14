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
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
)
