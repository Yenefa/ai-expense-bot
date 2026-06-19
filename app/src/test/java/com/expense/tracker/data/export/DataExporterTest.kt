package com.expense.tracker.data.export

import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DataExporterTest {

    @Test fun jsonContainsAllExpensesAndChats() {
        val expenses = listOf(
            ExpenseEntity(amount = 35.0, categoryId = "food", note = "午饭", occurredAt = 1L, createdAt = 2L, id = 10),
            ExpenseEntity(amount = 18.5, categoryId = "drink", note = "咖啡", occurredAt = 3L, createdAt = 4L, id = 11),
        )
        val chats = listOf(
            ChatMessageEntity(role = "user", content = "午饭35", createdAt = 5L, relatedExpenseId = 10, id = 100),
            ChatMessageEntity(role = "assistant", content = "已记 ¥35", createdAt = 6L, relatedExpenseId = 10, id = 101),
        )

        val json = DataExporter.toJson(expenses, chats, version = "2.5", exportedAtIso = "2026-06-19T10:00:00Z")

        // 不做精确匹配 — JSON 序列化可能有空格差异；只检查关键字段都在
        assertThat(json).contains("\"version\": \"2.5\"")
        assertThat(json).contains("\"exportedAt\": \"2026-06-19T10:00:00Z\"")
        assertThat(json).contains("\"amount\": 35.0")
        assertThat(json).contains("\"categoryId\": \"drink\"")
        assertThat(json).contains("\"role\": \"user\"")
        assertThat(json).contains("已记 ¥35")
        assertThat(json).contains("\"relatedExpenseId\": 10")
    }

    @Test fun csvHeaderIsCorrect() {
        val csv = DataExporter.toCsv(emptyList())
        assertThat(csv).isEqualTo("id,amount,categoryId,note,occurredAt,createdAt")
    }

    @Test fun csvEscapesCommasAndQuotesInNote() {
        val expenses = listOf(
            ExpenseEntity(amount = 9.0, categoryId = "drink", note = "菠萝,百香果", occurredAt = 1L, createdAt = 2L, id = 1),
            ExpenseEntity(amount = 8.0, categoryId = "food", note = "他说\"好吃\"", occurredAt = 3L, createdAt = 4L, id = 2),
        )

        val csv = DataExporter.toCsv(expenses)
        // 第 2 行：包含逗号 → 双引号包裹
        assertThat(csv).contains("\"菠萝,百香果\"")
        // 第 3 行：包含双引号 → 包裹+内部双双引号转义
        assertThat(csv).contains("\"他说\"\"好吃\"\"\"")
    }

    @Test fun csvIncludesAllRows() {
        val expenses = (1..5).map { i ->
            ExpenseEntity(
                amount = i.toDouble(),
                categoryId = "food",
                note = "记录$i",
                occurredAt = i.toLong(),
                createdAt = i.toLong(),
                id = i.toLong(),
            )
        }
        val csv = DataExporter.toCsv(expenses)
        // 1 行头 + 5 行数据 = 6 行
        assertThat(csv.lines()).hasSize(6)
    }
}
