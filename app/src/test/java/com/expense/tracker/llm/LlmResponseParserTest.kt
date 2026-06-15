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

    // === actions 字段（删/改/查） ===

    @Test fun parseDeleteActionWithMatch() {
        val raw = """
          {"reply":"准备删掉昨天那笔咖啡，确认吗？","expenses":[],"actions":[
            {"op":"delete","match":{
              "category":"drink","amount":18,
              "date_from":"2026-06-14T00:00:00","date_to":"2026-06-15T00:00:00",
              "note_contains":null}}
          ]}
        """.trimIndent()
        val r = LlmResponseParser.parse(raw)
        assertThat(r.actions).hasSize(1)
        val a = r.actions[0]
        assertThat(a.op).isEqualTo("delete")
        assertThat(a.matchCategoryId).isEqualTo("drink")
        assertThat(a.matchAmount).isEqualTo(18.0)
        assertThat(a.matchFromMillis).isNotNull()
        assertThat(a.matchToMillis).isNotNull()
        // from < to
        assertThat(a.matchFromMillis!!).isLessThan(a.matchToMillis!!)
        assertThat(a.matchNoteContains).isNull()
    }

    @Test fun parseUpdateActionWithPatch() {
        val raw = """
          {"reply":"改好了","expenses":[],"actions":[
            {"op":"update","match":{"category":"food","amount":35},
             "patch":{"amount":40, "category":null, "note":null}}
          ]}
        """.trimIndent()
        val a = LlmResponseParser.parse(raw).actions.single()
        assertThat(a.op).isEqualTo("update")
        assertThat(a.matchAmount).isEqualTo(35.0)
        assertThat(a.patchAmount).isEqualTo(40.0)
        assertThat(a.patchCategoryId).isNull()
        assertThat(a.patchNote).isNull()
    }

    @Test fun parseQueryAction() {
        val raw = """
          {"reply":"查一下","expenses":[],"actions":[
            {"op":"query","match":{"category":"food",
              "date_from":"2026-06-01T00:00:00","date_to":"2026-07-01T00:00:00"},
             "aggregate":"sum"}
          ]}
        """.trimIndent()
        val a = LlmResponseParser.parse(raw).actions.single()
        assertThat(a.op).isEqualTo("query")
        assertThat(a.aggregate).isEqualTo("sum")
        assertThat(a.matchCategoryId).isEqualTo("food")
    }

    @Test fun unknownActionOpFiltered() {
        val raw = """{"reply":"","expenses":[],"actions":[{"op":"hack","match":{}}]}"""
        assertThat(LlmResponseParser.parse(raw).actions).isEmpty()
    }

    @Test fun unknownMatchCategoryBecomesNull() {
        // 关键：跟 expense 不同，action.match.category 未知时落 null（= SQL 不限制），
        // 而不是 "other" —— 防止误删
        val raw = """{"reply":"","expenses":[],"actions":[
          {"op":"delete","match":{"category":"weird"}}
        ]}"""
        val a = LlmResponseParser.parse(raw).actions.single()
        assertThat(a.matchCategoryId).isNull()
    }

    @Test fun nonPositiveMatchAmountBecomesNull() {
        val raw = """{"reply":"","expenses":[],"actions":[
          {"op":"delete","match":{"amount":0}}
        ]}"""
        assertThat(LlmResponseParser.parse(raw).actions.single().matchAmount).isNull()
    }

    @Test fun blankNoteContainsBecomesNull() {
        val raw = """{"reply":"","expenses":[],"actions":[
          {"op":"delete","match":{"note_contains":"   "}}
        ]}"""
        assertThat(LlmResponseParser.parse(raw).actions.single().matchNoteContains).isNull()
    }

    @Test fun missingActionsFieldDefaultsEmpty() {
        // 老协议兼容：没有 actions 字段
        val raw = """{"reply":"hi","expenses":[]}"""
        assertThat(LlmResponseParser.parse(raw).actions).isEmpty()
    }
}
