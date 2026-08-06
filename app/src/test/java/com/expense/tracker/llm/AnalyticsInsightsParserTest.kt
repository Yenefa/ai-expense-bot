package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AnalyticsInsightsParserTest {

    @Test
    fun parsesInsightsWrappedInMarkdownAndPreamble() {
        val raw = """分析如下：```json
            {"insights":["餐饮占比最高","减少夜宵频率","下周设置预算"]}
            ```
        """.trimIndent()

        assertThat(AnalyticsInsightsParser.parse(raw)).containsExactly(
            "餐饮占比最高",
            "减少夜宵频率",
            "下周设置预算",
        ).inOrder()
    }

    @Test
    fun rejectsExpenseAssistantSchemaInsteadOfShowingEmptyInsights() {
        val raw = """{"reply":"已分析","expenses":[],"actions":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsInsightsParser.parse(raw)
        }
    }

    @Test
    fun skipsUnrelatedJsonBeforeAnalyticsPayload() {
        val raw = """调试信息 {}，正式结果：{"insights":["本月交通支出稳定"]}"""

        assertThat(AnalyticsInsightsParser.parse(raw))
            .containsExactly("本月交通支出稳定")
    }
}
