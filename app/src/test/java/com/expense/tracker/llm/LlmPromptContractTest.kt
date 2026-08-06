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

        assertThat(chatPrompt).contains("书籍、课程、电子元器件、API、模型调用、云算力")
        assertThat(chatPrompt).contains("category=education")
        assertThat(billPrompt).contains("category=education")
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
}
