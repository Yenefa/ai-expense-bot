package com.expense.tracker.bench

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ProactiveInsightBench v1 数据集：给定结构化财务事实 + 提醒状态，验证
 * "规则该不该提醒" 与五类硬约束（冷启动/冷却/每日额度/开关/漏报）。
 */
@Serializable
data class ProactiveBenchCase(
    val id: String,
    val bucket: String,
    @SerialName("rule") val rule: String? = null,
    @SerialName("limit_cents") val limitCents: Long = 0L,
    @SerialName("spent_cents") val spentCents: Long = 0L,
    @SerialName("record_count") val recordCount: Int = 10,
    @SerialName("elapsed_days") val elapsedDays: Int = 15,
    @SerialName("days_in_month") val daysInMonth: Int = 30,
    @SerialName("current_week_cents") val currentWeekCents: Long = 0L,
    @SerialName("weeks_cents") val weeksCents: List<Long> = listOf(0L, 0L, 0L, 0L),
    val income: Long? = null,
    val savings: Long? = null,
    val enabled: List<String> = listOf("budget_threshold", "anomalous_spending", "savings_goal_deviation"),
    @SerialName("state_day_key") val stateDayKey: String? = null,
    @SerialName("state_count_today") val stateCountToday: Int = 0,
    @SerialName("state_last_at_type") val stateLastAtType: String? = null,
    @SerialName("state_last_at_offset_ms") val stateLastAtOffsetMs: Long = 0L,
    @SerialName("state_last_severity") val stateLastSeverity: String? = null,
    @SerialName("expect_type") val expectType: String? = null,
    @SerialName("expect_severity") val expectSeverity: String? = null,
    val note: String? = null,
)

object ProactiveBenchDataset {
    const val SHOULD_ALERT = "should_alert"
    const val NO_TRIGGER = "no_trigger"
    const val SUPPRESSED_COLD_START = "suppressed_cold_start"
    const val SUPPRESSED_COOLDOWN = "suppressed_cooldown"
    const val SUPPRESSED_DAILY_BUDGET = "suppressed_daily_budget"
    const val SUPPRESSED_DISABLED = "suppressed_disabled"

    val BUCKETS = listOf(
        SHOULD_ALERT,
        NO_TRIGGER,
        SUPPRESSED_COLD_START,
        SUPPRESSED_COOLDOWN,
        SUPPRESSED_DAILY_BUDGET,
        SUPPRESSED_DISABLED,
    )

    fun load(): List<ProactiveBenchCase> {
        val stream = ProactiveBenchDataset::class.java.getResourceAsStream("/expensebench/proactive-cases.jsonl")
            ?: error("找不到测试资源 expensebench/proactive-cases.jsonl")
        val json = Json { ignoreUnknownKeys = true }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .mapIndexed { index, line ->
                runCatching { json.decodeFromString(ProactiveBenchCase.serializer(), line) }
                    .getOrElse { error("proactive 数据集第 ${index + 1} 行解析失败：${it.message}") }
            }
    }

    fun datasetSha256(): String {
        val stream = ProactiveBenchDataset::class.java.getResourceAsStream("/expensebench/proactive-cases.jsonl")
            ?: error("找不到测试资源 expensebench/proactive-cases.jsonl")
        return ExpenseBenchDataset.sha256Hex(stream.use { it.readBytes() })
    }
}
