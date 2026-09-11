package com.expense.tracker.memory

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 读权限边界：整份画像不允许无差别注入。 */
class MemoryReadPolicyTest {

    private val facts = listOf(
        MemoryFact(MemoryType.MONTHLY_INCOME, amountCents = 800_000L, id = "1", createdAt = 1L),
        MemoryFact(MemoryType.SAVINGS_GOAL, amountCents = 200_000L, id = "2", createdAt = 1L),
        MemoryFact(MemoryType.MERCHANT_ALIAS, merchant = "瑞幸", categoryId = "drink", id = "3", createdAt = 1L),
        MemoryFact(MemoryType.CATEGORY_PREFERENCE, categoryId = "food", id = "4", createdAt = 1L),
    )

    @Test
    fun `分类任务只读商户别名`() {
        val scoped = MemoryReadPolicy.filter(MemoryReadScope.CLASSIFICATION, facts)
        assertThat(scoped.map { it.type }).containsExactly(MemoryType.MERCHANT_ALIAS)

        val block = MemoryReadPolicy.promptBlock(MemoryReadScope.CLASSIFICATION, facts)!!
        assertThat(block).contains("商户别名：瑞幸→drink")
        assertThat(block).doesNotContain("月收入")
        assertThat(block).doesNotContain("储蓄目标")
        assertThat(block).doesNotContain("常用分类")
    }

    @Test
    fun `财务分析读收入储蓄与常用分类但不读别名`() {
        val block = MemoryReadPolicy.promptBlock(MemoryReadScope.FINANCIAL_ANALYSIS, facts)!!
        assertThat(block).contains("月收入：¥8000.00")
        assertThat(block).contains("储蓄目标：¥2000.00")
        assertThat(block).contains("常用分类：food")
        assertThat(block).doesNotContain("瑞幸")
    }

    @Test
    fun `NONE 不读任何记忆`() {
        assertThat(MemoryReadPolicy.filter(MemoryReadScope.NONE, facts)).isEmpty()
        assertThat(MemoryReadPolicy.promptBlock(MemoryReadScope.NONE, facts)).isNull()
    }

    @Test
    fun `无授权事实时不产生任何标记块`() {
        val withoutAlias = facts.filter { it.type != MemoryType.MERCHANT_ALIAS }
        assertThat(MemoryReadPolicy.promptBlock(MemoryReadScope.CLASSIFICATION, withoutAlias)).isNull()
        val aliasOnly = listOf(facts.first { it.type == MemoryType.MERCHANT_ALIAS })
        assertThat(MemoryReadPolicy.promptBlock(MemoryReadScope.FINANCIAL_ANALYSIS, aliasOnly)).isNull()
    }
}
