package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LlmResponseParserTest {

    @Test fun parseSinglePayload() {
        val raw = """{"expenses":[{"amount":35.0,"category":"food","note":"午饭","occurred_at":null}]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items).hasSize(1)
        assertThat(items[0].categoryId).isEqualTo("food")
        assertThat(items[0].amount).isEqualTo(35.0)
        assertThat(items[0].occurredAtMillis).isNull()
    }

    @Test fun parseMultipleExpenses() {
        val raw = """{"expenses":[
          {"amount":35,"category":"food","note":"午饭","occurred_at":null},
          {"amount":18.5,"category":"drink","note":"咖啡","occurred_at":null}
        ]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items).hasSize(2)
        assertThat(items.map { it.categoryId }).containsExactly("food", "drink").inOrder()
    }

    @Test fun parseIso8601OccurredAt() {
        val raw = """{"expenses":[{"amount":10,"category":"food","note":"","occurred_at":"2025-06-12T12:00:00"}]}"""
        val items = LlmResponseParser.parse(raw)
        val expected = LocalDateTime.of(2025, 6, 12, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(items[0].occurredAtMillis).isEqualTo(expected)
    }

    @Test fun unknownCategoryFallsBackToOther() {
        val raw = """{"expenses":[{"amount":1,"category":"weird","note":"","occurred_at":null}]}"""
        val items = LlmResponseParser.parse(raw)
        assertThat(items[0].categoryId).isEqualTo("other")
    }

    @Test fun nonPositiveAmountFiltered() {
        val raw = """{"expenses":[{"amount":0,"category":"food","note":"","occurred_at":null},
          {"amount":-5,"category":"food","note":"","occurred_at":null}]}"""
        try {
            LlmResponseParser.parse(raw)
            assert(false) { "应抛 LlmParseException — 全部金额非正" }
        } catch (e: LlmParseException) {
            assertThat(e.message).isNotNull()
        }
    }

    @Test fun garbageThrowsParseException() {
        try {
            LlmResponseParser.parse("not json")
            assert(false) { "应抛 LlmParseException" }
        } catch (_: LlmParseException) { /* ok */ }
    }

    @Test fun emptyExpensesArrayThrowsNoExpense() {
        try {
            LlmResponseParser.parse("""{"expenses":[]}""")
            assert(false)
        } catch (e: LlmParseException) {
            assertThat(e.message).contains("未识别到支出")
        }
    }
}
