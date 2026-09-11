package com.expense.tracker.memory

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Memory v1 检测与类型校验：四类正例 + 关键反例。 */
class MemoryProposalDetectorTest {

    private fun detect(text: String): MemoryDraft? =
        MemoryProposalDetector.detect(text)?.takeIf(MemoryTypeValidator::validate)

    @Test
    fun `月收入表达识别并解析金额`() {
        assertThat(detect("我月收入8000")?.let { it.type to it.amountCents })
            .isEqualTo(MemoryType.MONTHLY_INCOME to 800_000L)
        assertThat(detect("我月薪1万")?.amountCents).isEqualTo(1_000_000L)
        assertThat(detect("每月工资到手9500")?.amountCents).isEqualTo(950_000L)
        assertThat(detect("工资是8000块")?.amountCents).isEqualTo(800_000L)
        assertThat(detect("月薪8000元")?.type).isEqualTo(MemoryType.MONTHLY_INCOME)
    }

    @Test
    fun `储蓄目标表达识别并解析金额`() {
        assertThat(detect("我每月想存2000")?.let { it.type to it.amountCents })
            .isEqualTo(MemoryType.SAVINGS_GOAL to 200_000L)
        assertThat(detect("储蓄目标是5万")?.amountCents).isEqualTo(5_000_000L)
        assertThat(detect("每次发工资先攒3000")?.amountCents).isEqualTo(300_000L)
        assertThat(detect("每月存500块")?.amountCents).isEqualTo(50_000L)
    }

    @Test
    fun `商户别名表达解析出商户与分类`() {
        assertThat(detect("以后瑞幸都算饮品")).isEqualTo(
            MemoryDraft(MemoryType.MERCHANT_ALIAS, merchant = "瑞幸", categoryId = "drink", rawText = "以后瑞幸都算饮品"),
        )
        assertThat(detect("把星巴克记成饮品")?.merchant).isEqualTo("星巴克")
        assertThat(detect("肯德基都归餐饮")?.categoryId).isEqualTo("food")
        assertThat(detect("麦当劳算餐饮")?.merchant).isEqualTo("麦当劳")
    }

    @Test
    fun `常用分类表达解析出分类`() {
        assertThat(detect("我主要的花销都在吃饭")?.categoryId).isEqualTo("food")
        assertThat(detect("平时消费最多的是交通")?.categoryId).isEqualTo("transport")
        assertThat(detect("我的大部分支出都是房租")?.categoryId).isEqualTo("housing")
        assertThat(detect("平时花钱最多的是娱乐")?.categoryId).isEqualTo("entertainment")
    }

    @Test
    fun `第三方转述不提案`() {
        assertThat(detect("同事月薪两万")).isNull()
        assertThat(detect("朋友说他每月存5000")).isNull()
        assertThat(detect("网上说月收入能到2万")).isNull()
    }

    @Test
    fun `无金额或无效分类不通过类型校验`() {
        assertThat(detect("我想存钱")).isNull()
        assertThat(detect("我月收入还没确定")).isNull()
        assertThat(detect("把瑞幸记成20")).isNull()
        assertThat(detect("以后瑞幸都算好喝的")).isNull()
    }

    @Test
    fun `金额必须紧邻关键词避免误读支出`() {
        // "发了工资，打车花了23" 里的 23 不是月收入
        assertThat(detect("今天发工资了，打车花了23")).isNull()
        assertThat(detect("发工资后买了双鞋花了800")).isNull()
    }
}
