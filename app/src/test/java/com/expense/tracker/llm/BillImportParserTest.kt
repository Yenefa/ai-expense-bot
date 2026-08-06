package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BillImportParserTest {

    @Test
    fun parsesMultipleTransactionsFromFencedJson() {
        val raw = """```json
            {"reply":"识别完成","expenses":[
              {"amount":12.50,"category":"food","note":"早餐","occurred_at":null},
              {"amount":3.20,"category":"transport","note":"公交","occurred_at":null}
            ]}
            ```
        """.trimIndent()

        val expenses = BillImportParser.parse(raw)

        assertThat(expenses).hasSize(2)
        assertThat(expenses.map { it.amountCents }).containsExactly(1_250L, 320L).inOrder()
    }

    @Test
    fun rejectsResponsesWithoutTransactions() {
        assertThrows(IllegalArgumentException::class.java) {
            BillImportParser.parse("没有识别到交易")
        }
    }

    @Test
    fun skipsUnrelatedJsonBeforeBillPayload() {
        val raw = """调试信息 {}，正式结果：{"reply":"识别成功","expenses":[{"amount":26.80,"category":"food","note":"晚餐","occurred_at":null}]}"""

        val expenses = BillImportParser.parse(raw)

        assertThat(expenses).hasSize(1)
        assertThat(expenses.single().amountCents).isEqualTo(2_680L)
        assertThat(expenses.single().note).isEqualTo("晚餐")
    }

    @Test
    fun keepsValidOcrRowsWhenAnotherRowHasInvalidAmount() {
        val raw = """{"reply":"识别完成","expenses":[
          {"amount":0,"category":"food","note":"坏行","occurred_at":null},
          {"amount":18.60,"category":"transport","note":"有效地铁","occurred_at":null}
        ]}"""

        val expenses = BillImportParser.parse(raw)

        assertThat(expenses).hasSize(1)
        assertThat(expenses.single().amountCents).isEqualTo(1_860L)
        assertThat(expenses.single().note).isEqualTo("有效地铁")
    }
}
