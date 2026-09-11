package com.expense.tracker.bench

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MemoryConsumptionBench v1 数据集：检验长期记忆"被正确、且仅被授权地读取"。
 * 每条用例先种入已确认记忆，再跑一轮真实 Agent（stub LLM），观察 prompt 与最终账目。
 */
@Serializable
data class ConsumptionSeedFact(
    val type: String,
    @SerialName("amount_cents") val amountCents: Long? = null,
    val merchant: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("raw_text") val rawText: String = "",
)

@Serializable
data class ConsumptionScript(
    val amount: Double,
    val category: String,
    val note: String,
)

@Serializable
data class MemoryConsumptionCase(
    val id: String,
    val bucket: String,
    val text: String,
    val memory: List<ConsumptionSeedFact> = emptyList(),
    /** 轮前删除这些类型的记忆（证明删除后不再被任何路径使用）。 */
    @SerialName("delete_types") val deleteTypes: List<String> = emptyList(),
    @SerialName("expect_route") val expectRoute: String? = null,
    /** 这些类型的标记必须出现在本轮 prompt 中。 */
    @SerialName("expect_reads") val expectReads: List<String> = emptyList(),
    /** 非空时，stub LLM 输出该笔账目（用于校验别名是否真正应用）。 */
    val script: ConsumptionScript? = null,
    @SerialName("expect_final_category") val expectFinalCategory: String? = null,
    val note: String? = null,
)

object MemoryConsumptionDataset {
    const val ALIAS_APPLICATION = "alias_application"
    const val ANALYSIS_READS = "analysis_reads"
    const val PREFERENCE_ANALYSIS = "preference_analysis"
    const val UNAUTHORIZED = "unauthorized"
    const val DELETED_REUSE = "deleted_reuse"
    const val NO_MEMORY = "no_memory"

    val BUCKETS = listOf(ALIAS_APPLICATION, ANALYSIS_READS, PREFERENCE_ANALYSIS, UNAUTHORIZED, DELETED_REUSE, NO_MEMORY)

    fun load(): List<MemoryConsumptionCase> {
        val stream = MemoryConsumptionDataset::class.java.getResourceAsStream("/expensebench/memory-consumption-cases.jsonl")
            ?: error("找不到测试资源 expensebench/memory-consumption-cases.jsonl")
        val json = Json { ignoreUnknownKeys = true }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .mapIndexed { index, line ->
                runCatching { json.decodeFromString(MemoryConsumptionCase.serializer(), line) }
                    .getOrElse { error("memory consumption 数据集第 ${index + 1} 行解析失败：${it.message}") }
            }
    }

    fun datasetSha256(): String {
        val stream = MemoryConsumptionDataset::class.java.getResourceAsStream("/expensebench/memory-consumption-cases.jsonl")
            ?: error("找不到测试资源 expensebench/memory-consumption-cases.jsonl")
        return ExpenseBenchDataset.sha256Hex(stream.use { it.readBytes() })
    }
}
