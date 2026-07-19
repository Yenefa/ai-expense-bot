package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BillImportTest {

    private val prefs = UserPrefsSnapshot(
        llmEnabled = true, baseUrl = "https://x", apiKey = "k", model = "m",
    )

    @Test fun `billImportSystemPrompt demands expenses schema and extraction, not actions`() {
        val sys = LlmPrompt.billImportSystemPrompt()
        assertThat(sys).contains("expenses")
        assertThat(sys).contains("交易")          // 提取每笔交易的指令
        assertThat(sys).doesNotContain("actions") // 账单导入不做删改
    }

    @Test fun `billImportPrompt embeds OCR text`() {
        val ocr = "2025-07-19 星巴克 ¥38.00\n2025-07-19 地铁 ¥4.00"
        val p = LlmPrompt.billImportPrompt(ocr)
        assertThat(p).contains("星巴克")
        assertThat(p).contains("地铁")
    }

    @Test fun `importFromBillText parses multiple expenses from clean JSON`() = runTest {
        val fake = object : LlmClient() {
            override suspend fun chatJson(
                baseUrl: String, apiKey: String, model: String,
                userText: String, systemPrompt: String,
            ): String =
                """{"reply":"已识别2笔","expenses":[{"amount":38.0,"category":"drink","note":"星巴克","occurred_at":null},{"amount":4.0,"category":"transport","note":"地铁","occurred_at":null}]}"""
        }
        val r = importFromBillText(fake, prefs, "任意OCR文本")
        assertThat(r).isInstanceOf(BillImportResult.Ok::class.java)
        val ok = r as BillImportResult.Ok
        assertThat(ok.expenses).hasSize(2)
        assertThat(ok.expenses.map { it.categoryId }).containsExactly("drink", "transport")
    }

    @Test fun `importFromBillText extracts JSON from prose-wrapped response`() = runTest {
        val fake = object : LlmClient() {
            override suspend fun chatJson(
                baseUrl: String, apiKey: String, model: String,
                userText: String, systemPrompt: String,
            ): String =
                """好的，结果如下：{"reply":"已记","expenses":[{"amount":12,"category":"food","note":"午饭","occurred_at":null}]} 希望帮到你"""
        }
        val r = importFromBillText(fake, prefs, "x")
        val ok = r as BillImportResult.Ok
        assertThat(ok.expenses).hasSize(1)
        assertThat(ok.expenses[0].amount).isEqualTo(12.0)
    }

    @Test fun `importFromBillText returns Error when LLM throws`() = runTest {
        val fake = object : LlmClient() {
            override suspend fun chatJson(
                baseUrl: String, apiKey: String, model: String,
                userText: String, systemPrompt: String,
            ): String = error("LLM HTTP 500: boom")
        }
        val r = importFromBillText(fake, prefs, "x")
        assertThat(r).isInstanceOf(BillImportResult.Error::class.java)
        assertThat((r as BillImportResult.Error).message).contains("500")
    }
}
