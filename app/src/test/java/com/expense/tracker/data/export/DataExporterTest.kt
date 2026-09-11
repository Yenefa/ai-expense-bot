package com.expense.tracker.data.export

import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.db.RecurringPeriodType
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryType
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DataExporterTest {

    @Test fun completeBackupRoundTripsDeletedExpensesChatsAndPreferences() {
        val expenses = listOf(
            ExpenseEntity(3_500L, "food", "午饭", 1L, 2L, id = 10),
            ExpenseEntity(1_850L, "drink", "咖啡", 3L, 4L, deletedAt = 5L, id = 11),
        )
        val chats = listOf(
            ChatMessageEntity("user", "午饭35", 5L, relatedExpenseId = 10, id = 100),
            ChatMessageEntity(
                "assistant",
                "已记2笔",
                6L,
                relatedExpenseId = 10,
                relatedExpenseIdsCsv = "10,11",
                id = 101,
            ),
        )
        val preferences = BackupPreferences(
            llmEnabled = true,
            baseUrl = "https://example.com/v1",
            model = "model-a",
            themeMode = ThemeMode.DARK,
        )

        val json = DataExporter.toBackupJson(
            expenses = expenses,
            chatMessages = chats,
            preferences = preferences,
            sourceAppVersion = "3.6",
            exportedAtIso = "2026-07-31T10:00:00Z",
        )
        val decoded = DataExporter.parseBackup(json)

        assertThat(decoded.formatVersion).isEqualTo(DataExporter.CURRENT_FORMAT_VERSION)
        assertThat(decoded.sourceAppVersion).isEqualTo("3.6")
        assertThat(decoded.expenses).containsExactlyElementsIn(expenses).inOrder()
        assertThat(decoded.chatMessages).containsExactlyElementsIn(chats).inOrder()
        assertThat(json).contains("\"relatedExpenseIds\"")
        assertThat(decoded.preferences).isEqualTo(preferences)
        assertThat(json).doesNotContain("apiKey")
    }

    @Test fun legacyExporterJsonCanBeRestored() {
        val legacy = """
            {
              "version": "3.6",
              "exportedAt": "2026-07-31T10:00:00Z",
              "expenses": [{
                "id": 7,
                "amount": 12.345,
                "categoryId": "food",
                "note": "午饭",
                "occurredAt": 100,
                "createdAt": 101
              }],
              "chatMessages": [{
                "id": 8,
                "role": "assistant",
                "content": "已记录",
                "createdAt": 102,
                "relatedExpenseId": 7
              }]
            }
        """.trimIndent()

        val decoded = DataExporter.parseBackup(legacy)

        assertThat(decoded.expenses.single().amountCents).isEqualTo(1_235L)
        assertThat(decoded.expenses.single().deletedAt).isNull()
        assertThat(decoded.preferences.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun malformedOrDuplicateBackupIsRejectedBeforeRestore() {
        assertThrows(BackupFormatException::class.java) {
            DataExporter.parseBackup("not json")
        }

        val duplicateIds = """
            {
              "formatVersion": 2,
              "sourceAppVersion": "3.6",
              "exportedAt": "2026-07-31T10:00:00Z",
              "expenses": [
                {"id":1,"amountCents":100,"categoryId":"food","note":"a","occurredAt":1,"createdAt":1,"deletedAt":null},
                {"id":1,"amountCents":200,"categoryId":"food","note":"b","occurredAt":2,"createdAt":2,"deletedAt":null}
              ],
              "chatMessages": [],
              "preferences": {"llmEnabled":false,"baseUrl":"https://api.openai.com/v1","model":"gpt-4o-mini","themeMode":"SYSTEM"}
            }
        """.trimIndent()
        assertThrows(BackupFormatException::class.java) {
            DataExporter.parseBackup(duplicateIds)
        }

        val invalidBatchId = """
            {
              "formatVersion":2,
              "sourceAppVersion":"3.6",
              "exportedAt":"2026-07-31T10:00:00Z",
              "expenses":[],
              "chatMessages":[{"id":1,"role":"assistant","content":"x","createdAt":1,"relatedExpenseId":7,"relatedExpenseIds":[7,-8]}],
              "preferences":{"llmEnabled":false,"baseUrl":"","model":"","themeMode":"SYSTEM"}
            }
        """.trimIndent()
        assertThrows(BackupFormatException::class.java) {
            DataExporter.parseBackup(invalidBatchId)
        }
    }

    @Test fun blankDisabledLlmConfigurationDoesNotBlockFinancialBackup() {
        val preferences = BackupPreferences(
            llmEnabled = false,
            baseUrl = "",
            model = "",
            themeMode = ThemeMode.SYSTEM,
        )

        val json = DataExporter.toBackupJson(
            expenses = listOf(ExpenseEntity(100L, "food", "", 1L, 1L, id = 1L)),
            chatMessages = emptyList(),
            preferences = preferences,
            sourceAppVersion = "3.6",
            exportedAtIso = "2026-07-31T10:00:00Z",
        )

        assertThat(DataExporter.parseBackup(json).preferences).isEqualTo(preferences)
    }

    @Test fun unsupportedBackupVersionIsRejected() {
        val unsupported = """
            {"formatVersion":99,"sourceAppVersion":"3.6","exportedAt":"2026-07-31T10:00:00Z","expenses":[],"chatMessages":[],"preferences":{"llmEnabled":false,"baseUrl":"","model":"","themeMode":"SYSTEM"}}
        """.trimIndent()

        val error = assertThrows(BackupFormatException::class.java) {
            DataExporter.parseBackup(unsupported)
        }
        assertThat(error).hasMessageThat().contains("99")
    }

    @Test fun memoryFactsRoundTripInBackup() {
        val facts = listOf(
            MemoryFact(
                type = MemoryType.MONTHLY_INCOME,
                amountCents = 800_000L,
                rawText = "我月收入8000",
                createdAt = 1L,
                id = "mem-1",
            ),
            MemoryFact(
                type = MemoryType.MERCHANT_ALIAS,
                merchant = "瑞幸",
                categoryId = "drink",
                rawText = "以后瑞幸都算饮品",
                createdAt = 2L,
                id = "mem-2",
            ),
        )

        val json = DataExporter.toBackupJson(
            expenses = emptyList(),
            chatMessages = emptyList(),
            preferences = BackupPreferences(false, "", "", ThemeMode.SYSTEM),
            sourceAppVersion = "3.10",
            exportedAtIso = "2026-09-11T10:00:00Z",
            memoryFacts = facts,
        )

        assertThat(DataExporter.parseBackup(json).memoryFacts)
            .containsExactlyElementsIn(facts).inOrder()
    }

    @Test fun v2BackupWithoutMemoryStillParses() {
        val v2 = """
            {"formatVersion":2,"sourceAppVersion":"3.9","exportedAt":"2026-09-10T10:00:00Z","expenses":[],"chatMessages":[],"preferences":{"llmEnabled":false,"baseUrl":"","model":"","themeMode":"SYSTEM"}}
        """.trimIndent()

        val decoded = DataExporter.parseBackup(v2)

        assertThat(decoded.formatVersion).isEqualTo(2)
        assertThat(decoded.memoryFacts).isEmpty()
    }

    @Test fun invalidMemoryFactRejectsBackup() {
        val invalid = """
            {"formatVersion":3,"sourceAppVersion":"3.10","exportedAt":"2026-09-11T10:00:00Z","expenses":[],"chatMessages":[],"preferences":{"llmEnabled":false,"baseUrl":"","model":"","themeMode":"SYSTEM"},"memoryFacts":[{"type":"monthly_income","amount_cents":null,"raw_text":"我月收入","created_at":1,"id":"mem-x"}]}
        """.trimIndent()

        assertThrows(BackupFormatException::class.java) {
            DataExporter.parseBackup(invalid)
        }
    }

    @Test fun csvHeaderAndEscapingStayCompatible() {
        assertThat(DataExporter.toCsv(emptyList()))
            .isEqualTo("id,amount,categoryId,note,occurredAt,createdAt")
        val expenses = listOf(
            ExpenseEntity(900L, "drink", "菠萝,百香果", 1L, 2L, id = 1),
            ExpenseEntity(800L, "food", "他说\"好吃\"", 3L, 4L, id = 2),
        )

        val csv = DataExporter.toCsv(expenses)

        assertThat(csv).contains("\"菠萝,百香果\"")
        assertThat(csv).contains("\"他说\"\"好吃\"\"\"")
        assertThat(csv.lines()[1].split(',')[1]).isEqualTo("9.00")
        assertThat(csv.lines()[2].split(',')[1]).isEqualTo("8.00")
    }

    @Test fun recurringRuleRoundTripsInBackup() {
        val rule = recurringRule()

        val json = DataExporter.toBackupJson(
            expenses = emptyList(),
            chatMessages = emptyList(),
            preferences = BackupPreferences(false, "", "", ThemeMode.SYSTEM),
            sourceAppVersion = "3.11",
            exportedAtIso = "2026-09-12T10:00:00Z",
            recurringRules = listOf(rule),
        )
        val decoded = DataExporter.parseBackup(json)

        assertThat(decoded.recurringRules).containsExactly(rule)
    }

    @Test fun invalidRecurringRulesRejectRestoreAndLeaveExistingRulesUnchanged() {
        val existing = recurringRule(id = 1L)
        val invalidRules = listOf(
            ruleJson(amountCents = 0L) to "周期账单金额必须大于零",
            ruleJson(categoryId = "nope") to "未知周期账单分类",
            ruleJson(periodType = "weird") to "周期账单周期类型无效",
            ruleJson(dayOfMonth = 0) to "周期账单日期无效",
            ruleJson(dayOfWeek = 0) to "周期账单星期无效",
            ruleJson(monthOfYear = 13) to "周期账单月份无效",
            ruleJson(nextDueAt = 0L) to "周期账单下次到期时间无效",
            ruleJson(createdAt = -1L) to "周期账单创建时间无效",
        )

        invalidRules.forEach { (rule, expectedMessage) ->
            val db = mutableListOf(existing)

            // 模拟 restore：解析失败时抛异常，后续清空/写入不会执行，db 保持不变
            val error = assertThrows(BackupFormatException::class.java) {
                val parsed = DataExporter.parseBackup(backupWithRules(rule))
                db.clear()
                db.addAll(parsed.recurringRules)
            }

            assertThat(error).hasMessageThat().contains(expectedMessage)
            assertThat(db).containsExactly(existing)
        }
    }

    private fun recurringRule(
        amountCents: Long = 350_000L,
        categoryId: String = "housing",
        periodType: String = RecurringPeriodType.MONTHLY.name,
        dayOfMonth: Int = 15,
        dayOfWeek: Int = 1,
        monthOfYear: Int = 1,
        nextDueAt: Long = 1_780_000_000_000L,
        createdAt: Long = 1_770_000_000_000L,
        id: Long = 7L,
    ) = RecurringRuleEntity(
        amountCents = amountCents,
        categoryId = categoryId,
        note = "房租",
        periodType = periodType,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek,
        monthOfYear = monthOfYear,
        nextDueAt = nextDueAt,
        createdAt = createdAt,
        id = id,
    )

    private fun ruleJson(
        amountCents: Long = 350_000L,
        categoryId: String = "housing",
        periodType: String = "MONTHLY",
        dayOfMonth: Int = 15,
        dayOfWeek: Int = 1,
        monthOfYear: Int = 1,
        nextDueAt: Long = 1_780_000_000_000L,
        createdAt: Long = 1_770_000_000_000L,
    ): String =
        """{"id":7,"amountCents":$amountCents,"categoryId":"$categoryId","note":"房租","periodType":"$periodType","dayOfMonth":$dayOfMonth,"dayOfWeek":$dayOfWeek,"monthOfYear":$monthOfYear,"nextDueAt":$nextDueAt,"enabled":true,"createdAt":$createdAt}"""

    private fun backupWithRules(vararg rulesJson: String): String =
        """{"formatVersion":3,"sourceAppVersion":"3.11","exportedAt":"2026-09-12T10:00:00Z","expenses":[],"chatMessages":[],"preferences":{"llmEnabled":false,"baseUrl":"","model":"","themeMode":"SYSTEM"},"recurringRules":[${rulesJson.joinToString(",")}]}"""
}
