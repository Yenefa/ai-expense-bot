package com.expense.tracker.bench

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MemoryGovernanceBench v1 数据集：检验 Memory 提案、类型校验、不误记支出、
 * 以及"没有确认就绝不写长期记忆"。
 */
@Serializable
data class MemoryBenchCase(
    val id: String,
    val bucket: String,
    val text: String,
    /** monthly_income | savings_goal | merchant_alias | category_preference；null = 不应提案。 */
    @SerialName("expect_type") val expectType: String? = null,
    @SerialName("expect_amount_cents") val expectAmountCents: Long? = null,
    @SerialName("expect_merchant") val expectMerchant: String? = null,
    @SerialName("expect_category") val expectCategory: String? = null,
    /** MUTATION | QUERY | CHAT；null = 不评路由。 */
    @SerialName("expect_route") val expectRoute: String? = null,
    @SerialName("expect_mutation_count") val expectMutationCount: Int? = null,
    val note: String? = null,
)

object MemoryBenchDataset {
    const val PROPOSE_INCOME = "propose_income"
    const val PROPOSE_SAVINGS = "propose_savings"
    const val PROPOSE_ALIAS = "propose_alias"
    const val PROPOSE_PREFERENCE = "propose_preference"
    const val REJECT = "reject"

    val BUCKETS = listOf(PROPOSE_INCOME, PROPOSE_SAVINGS, PROPOSE_ALIAS, PROPOSE_PREFERENCE, REJECT)

    fun load(): List<MemoryBenchCase> {
        val stream = MemoryBenchDataset::class.java.getResourceAsStream("/expensebench/memory-cases.jsonl")
            ?: error("找不到测试资源 expensebench/memory-cases.jsonl")
        val json = Json { ignoreUnknownKeys = true }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .mapIndexed { index, line ->
                runCatching { json.decodeFromString(MemoryBenchCase.serializer(), line) }
                    .getOrElse { error("memory 数据集第 ${index + 1} 行解析失败：${it.message}") }
            }
    }

    fun datasetSha256(): String {
        val stream = MemoryBenchDataset::class.java.getResourceAsStream("/expensebench/memory-cases.jsonl")
            ?: error("找不到测试资源 expensebench/memory-cases.jsonl")
        return ExpenseBenchDataset.sha256Hex(stream.use { it.readBytes() })
    }
}
