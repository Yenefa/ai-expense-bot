package com.expense.tracker.bench

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneId

/** ExpenseBench 单条预期：金额（分）、分类、日期（yyyy-MM-dd 或 null）、商户/备注包含串（可空）。 */
@Serializable
data class BenchExpect(
    val amount_cents: Long,
    val category: String,
    val date: String? = null,
    val note_contains: String? = null,
)

@Serializable
data class BenchCase(
    val id: String,
    val bucket: String,
    val text: String,
    val expect: List<BenchExpect>,
)

/** 数据集约定：所有相对时间基于固定基准时刻解析，保证标注稳定可复现。 */
object ExpenseBenchDataset {
    const val BENCH_NOW_ISO = "2026-09-06T12:00:00+08:00"
    val benchZone: ZoneId = ZoneId.of("Asia/Shanghai")

    val benchNowMillis: Long by lazy {
        OffsetDateTime.parse(BENCH_NOW_ISO).toInstant().toEpochMilli()
    }

    fun load(): List<BenchCase> {
        val stream = ExpenseBenchDataset::class.java.getResourceAsStream("/expensebench/cases.jsonl")
            ?: error("找不到测试资源 expensebench/cases.jsonl")
        val json = Json { ignoreUnknownKeys = true }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .mapIndexed { index, line ->
                runCatching { json.decodeFromString(BenchCase.serializer(), line) }
                    .getOrElse { error("数据集第 ${index + 1} 行解析失败：${it.message}") }
            }
    }
}
