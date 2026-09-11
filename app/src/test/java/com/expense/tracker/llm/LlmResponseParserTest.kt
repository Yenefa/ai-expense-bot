package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class LlmResponseParserTest {

    @Test fun parseSinglePayload() {
        val raw = """{"reply":"收到","expenses":[{"amount":35.0,"category":"food","note":"午饭","occurred_at":null}]}"""
        val result = LlmResponseParser.parse(raw)
        assertThat(result.reply).isEqualTo("收到")
        assertThat(result.expenses).hasSize(1)
        assertThat(result.expenses[0].categoryId).isEqualTo("food")
        assertThat(result.expenses[0].amountCents).isEqualTo(3_500L)
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

    @Test fun parseDateOnlyOccurredAt() {
        // v3.9.2：模型偶尔只回日期，必须落到当天而不是拒绝整批。
        val raw = """{"reply":"ok","expenses":[{"amount":10,"category":"food","note":"","occurred_at":"2026-09-05"}]}"""

        val result = LlmResponseParser.parse(raw)

        val date = Instant.ofEpochMilli(result.expenses.single().occurredAtMillis!!)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        assertThat(date).isEqualTo(java.time.LocalDate.of(2026, 9, 5))
    }

    @Test fun parseUtcOccurredAt() {
        val raw = """{"reply":"ok","expenses":[{"amount":10,"category":"food","note":"","occurred_at":"2025-06-12T12:00:00Z"}]}"""

        val result = LlmResponseParser.parse(raw)

        assertThat(result.expenses.single().occurredAtMillis)
            .isEqualTo(Instant.parse("2025-06-12T12:00:00Z").toEpochMilli())
    }

    @Test fun parseOffsetOccurredAt() {
        val raw = """{"reply":"ok","expenses":[{"amount":10,"category":"food","note":"","occurred_at":"2025-06-12T20:00:00+08:00"}]}"""

        val result = LlmResponseParser.parse(raw)

        assertThat(result.expenses.single().occurredAtMillis)
            .isEqualTo(Instant.parse("2025-06-12T12:00:00Z").toEpochMilli())
    }

    @Test fun unknownCategoryFallsBackToOther() {
        val raw = """{"reply":"","expenses":[{"amount":1,"category":"weird","note":"","occurred_at":null}]}"""
        val result = LlmResponseParser.parse(raw)
        assertThat(result.expenses[0].categoryId).isEqualTo("other")
    }

    @Test fun educationCategoryIsPreserved() {
        val raw = """{"reply":"已记","expenses":[{"amount":88,"category":"education","note":"API 费用","occurred_at":null}]}"""

        val result = LlmResponseParser.parse(raw)

        assertThat(result.expenses.single().categoryId).isEqualTo("education")
    }

    @Test fun nonPositiveAmountRejectsWholeJsonPayload() {
        val raw = """{"reply":"这些金额无效","expenses":[{"amount":0,"category":"food","note":"","occurred_at":null},
          {"amount":-5,"category":"food","note":"","occurred_at":null}]}"""
        assertThrows(LlmParseException::class.java) { LlmResponseParser.parse(raw) }
    }

    @Test fun garbageReturnsAsPlainTextReply() {
        // v2.4 起：不再抛异常，纯文本当作闲聊回复
        val result = LlmResponseParser.parse("你好呀！今天过得怎么样？")
        assertThat(result.reply).isEqualTo("你好呀！今天过得怎么样？")
        assertThat(result.expenses).isEmpty()
    }

    @Test fun extractsJsonFromMixedText() {
        // 模型偶尔会在 JSON 前后加废话，应该能被抽取
        val raw = """好的，这是回复：{"reply":"已记 ¥35","expenses":[]} 希望能帮到你"""
        val result = LlmResponseParser.parse(raw)
        assertThat(result.reply).isEqualTo("已记 ¥35")
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

    @Test fun parseAmountDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val result = LlmResponseParser.parse(
                """{"reply":"ok","expenses":[{"amount":12.345,"category":"food","note":"","occurred_at":null}]}"""
            )

            assertThat(result.expenses.single().amountCents).isEqualTo(1_235L)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun updateAmountParsesDirectlyToCents() {
        val result = LlmResponseParser.parse(
            """{"reply":"ok","expenses":[],"actions":[{"action":"update","expense_id":7,"amount":6.789}]}"""
        )

        val action = result.actions.single() as ParsedAction.Update
        assertThat(action.amountCents).isEqualTo(679L)
    }

    @Test fun oneInvalidActionRejectsWholePayloadInsteadOfApplyingValidSubset() {
        val raw = """{"reply":"已修改2笔","expenses":[],"actions":[
          {"action":"update","expense_id":7,"note":"有效项"},
          {"action":"update","expense_id":0,"note":"非法项"}
        ]}"""

        assertThrows(LlmParseException::class.java) { LlmResponseParser.parse(raw) }
    }

    @Test fun unknownActionRejectsWholePayload() {
        val raw = """{"reply":"已处理","expenses":[],"actions":[
          {"action":"move_everything","expense_id":7}
        ]}"""

        assertThrows(LlmParseException::class.java) { LlmResponseParser.parse(raw) }
    }
}
