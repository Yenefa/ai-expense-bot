package com.expense.tracker.llm

import com.expense.tracker.data.model.Category
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LlmPromptContractTest {

    @Test
    fun analyticsUsesDedicatedInsightsSchema() {
        val prompt = LlmPrompt.analyticsSystemPrompt()

        assertThat(prompt).contains("\"insights\"")
        assertThat(prompt).doesNotContain("\"expenses\"")
        assertThat(prompt).doesNotContain("\"actions\"")
    }

    @Test
    fun billImportPromptDefinesEverySupportedCategory() {
        val prompt = LlmPrompt.billImportSystemPrompt()

        Category.ALL.forEach { category -> assertThat(prompt).contains(category.id) }
        assertThat(prompt).contains("OCR")
        assertThat(prompt).contains("occurred_at")
    }

    @Test
    fun promptsExplainLearningAndCreationClassification() {
        val chatPrompt = LlmPrompt.systemPrompt()
        val billPrompt = LlmPrompt.billImportSystemPrompt()

        assertThat(chatPrompt).contains("书籍、课程、文具、笔、电子元器件、API、模型调用、云算力")
        assertThat(chatPrompt).contains("category=education")
        assertThat(chatPrompt).contains("理发/剪发")
        assertThat(chatPrompt).contains("酒店/住宿")
        assertThat(billPrompt).contains("category=education")
        assertThat(billPrompt).contains("酒店住宿归 housing")
    }

    @Test fun chatPromptDefinesBatchAndExecutionSafetyContract() {
        val prompt = LlmPrompt.systemPrompt(lastBatchIds = listOf(7L, 8L, 9L))

        assertThat(prompt).contains("最近一次明确批次 ID：7,8,9")
        assertThat(prompt).contains("它们/这些/刚才那批")
        assertThat(prompt).contains("无法确定具体记录时")
        assertThat(prompt).contains("不得声称已经执行")
        assertThat(prompt).contains("用户明确给出的日期优先")
    }

    @Test fun chatPromptPreservesSourceOrderAndNeverDuplicatesExpenses() {
        val prompt = LlmPrompt.systemPrompt()

        assertThat(prompt).contains("按原文顺序")
        assertThat(prompt).contains("每个金额只能对应一笔")
        assertThat(prompt).contains("禁止重复")
        assertThat(prompt).contains("最近一个明确日期")
    }

    @Test fun chatPromptForbidsReExtractingAlreadyRecordedHistoryOnFollowUps() {
        val prompt = LlmPrompt.systemPrompt()

        assertThat(prompt).contains("历史对话里已经确认记录过的账目")
        assertThat(prompt).contains("不得再次提取")
        assertThat(prompt).contains("只提取本轮新出现的消费")
        assertThat(prompt).contains("还有/也买/又买/再记")
        assertThat(prompt).contains("不是把历史里已记录的那笔重新记一遍")
    }

    @Test fun analyticsPromptUsesDotDecimalsRegardlessOfSystemLocale() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val prompt = LlmPrompt.analyticsPrompt(
                periodName = "本月",
                totalAmount = 1234.5,
                count = 3,
                topCategories = listOf("餐饮" to 999.99),
            )

            // 发给模型前必须是 "1234.50"/"999.99"，不能出现德语逗号小数
            assertThat(prompt).contains("1234.50")
            assertThat(prompt).contains("999.99")
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test fun chatPromptRequiresUpdateActionWhenOnlyTheDateChanges() {
        val prompt = LlmPrompt.systemPrompt()

        assertThat(prompt).contains("\"occurred_at\":<新ISO时间|null>")
        assertThat(prompt).contains("必须输出 update 动作")
        assertThat(prompt).contains("禁止用新增一笔代替修改")
    }
}
