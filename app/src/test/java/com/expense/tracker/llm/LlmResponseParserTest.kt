package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LlmResponseParserTest {

    @Test fun parseSinglePayload() {
        val raw = """{"reply":"收到","expenses":[{"amount":35.0,"category":"food","note":"午饭","occurred_at":null}]}"""
        val result = LlmResponseParser.parse(raw)
        assertThat(result.reply).isEqualTo("收到")
        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses[0].categoryId).isEqualTo("food")
        assertThat(result.expenses[0].amount).isEqualTo(35.0)
        assertThat(result.expenses[0].occurredAtMillis).isNull()
    }

    @Test fun parseMultipleExpenses() {
        val raw = """{"reply":"已记2笔","expenses":[
          {"amount":35,"category":"food","note":"午饭","occurred_at":null},
          {"amount":18.5,"category":"drink","note":"咖啡","occurred_at":null}
        ]}"""
        val result = LlmResponseParser.parse(raw)
        assertThat(result.reply).isEqualTo("已记2笔")
        assertThat(result.expenses).hasSize(2)
        assertThat(result.expenses.map { it.categoryId }).containsExactly("food", "drink").inOrder()
    }

    @Test fun parseIso8601OccurredAt() {
        val raw = """{"reply":"ok","expenses":[{"amount":10,"category":"food","note":"","occurred_at":"2025-06-12T12:00:00"}]}"""
        val result = LlmResponseParser.parse(raw)
        val expected = LocalDateTime.of(2025, 6, 12, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(result.expenses[0].occurredAtMillis).isEqualTo(expected)
    }

    @Test fun unknownCategoryFallsBackToOther() {
        val raw = """{"reply":"","expenses":[{"amount":1,"category":"weird","note":"","occurred_at":null}]}"""
        val result = LlmResponseParser.parse(raw)
        assertThat(result.expenses[0].categoryId).isEqualTo("other")
    }

    @Test fun nonPositiveAmountFiltered() {
        val raw = """{"reply":"这些金额无效","expenses":[{"amount":0,"category":"food","note":"","occurred_at":null},
          {"amount":-5,"category":"food","note":"","occurred_at":null}]}"""
        val result = LlmResponseParser.parse(raw)
        // 金额过滤掉，但 reply 还在
        assertThat(result.reply).isEqualTo("这些金额无效")
        assertThat(result.expenses).isEmpty()
    }

    @Test fun garbageThrowsParseException() {
        try {
            LlmResponseParser.parse("not json")
            assert(false) { "应抛 LlmParseException" }
        } catch (_: LlmParseException) { /* ok */ }
    }

    @Test fun emptyExpensesReturnsReply() {
        val result = LlmResponseParser.parse("""{"reply":"今天记了什么？","expenses":[]}""")
        assertThat(result.reply).isEqualTo("今天记了什么？")
        assertThat(result.expenses).isEmpty()
    }

    @Test fun blankReplyDefaults() {
        val result = LlmResponseParser.parse("""{"reply":"","expenses":[{"amount":5,"category":"food","note":"","occurred_at":null}]}""")
        assertThat(result.reply).isEqualTo("已记录")
        assertThat(result.expenses).hasSize(1)
    }
}
