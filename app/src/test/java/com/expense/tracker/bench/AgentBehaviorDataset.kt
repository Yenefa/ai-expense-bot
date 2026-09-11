package com.expense.tracker.bench

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ExpenseBench v2 / AgentBehaviorBench 数据集。
 *
 * 与 v1（只评 LLM 提取）不同，v2 评的是完整 Agent 行为：
 * 路由 → 是否调用工具 → 是否产生变更 → 产生几笔 → 最终数据库状态。
 * 每条 gold 可声明：expected_route / expected_tools / expected_mutation_count / expected_pending，
 * 以及 v1 同构的提取预期 expect（金额/分类/日期/商户）。
 */
@Serializable
data class BehaviorHistoryTurn(
    val role: String,
    val content: String,
    /** assistant 消息：把 seed_expenses 的 id 挂到 relatedExpenseIdsCsv（模拟"刚才那批"）。 */
    val link_seed: Boolean = false,
)

@Serializable
data class BehaviorSeedExpense(
    val amount_cents: Long,
    val category: String,
    val date: String,
    val time: String = "12:00",
    val note: String = "",
)

@Serializable
data class BehaviorCase(
    val id: String,
    val bucket: String,
    val text: String,
    val history: List<BehaviorHistoryTurn> = emptyList(),
    val seed_expenses: List<BehaviorSeedExpense> = emptyList(),
    /** 本轮期望产生的变更（insert 的最终状态 / update 的最终状态；delete 用空表示）。 */
    val expect: List<BenchExpect> = emptyList(),
    /** MUTATION | QUERY | CHAT；null = 本条不评路由（首看最终数据库状态）。 */
    val expected_route: String? = null,
    /** QUERY 轮期望调用的工具集合（精确匹配）；null = 不评。 */
    val expected_tools: List<String>? = null,
    /** 期望的"拟变更笔数"= 已应用 + 待确认；null = 不评。 */
    val expected_mutation_count: Int? = null,
    /** 期望进入删除确认门（拟变更但不落库）。 */
    val expected_pending: Boolean = false,
    /** 本轮结束后期望的活跃账目总数（含 seed；用于抓"该 update 却 insert"的重复记账）。 */
    val expected_active_count: Int? = null,
    /**
     * 上一轮生效路由（v3.9.3 会话上下文模拟）：用于更正/续记门控与 Query 回承。
     * bench 按此 hydrate ConversationActionContext；null = 无上一轮。
     */
    val previous_route: String? = null,
    /** 人工标注理由/已知缺口说明。 */
    val note: String? = null,
)

object AgentBehaviorDataset {
    const val NEGATIVE_FP = "negative_false_positive"
    const val MULTI_TEMPORAL = "multi_temporal"
    const val ROUTER_AMBIGUOUS = "router_ambiguous"
    const val MULTI_TURN = "multi_turn"

    val BUCKETS = listOf(NEGATIVE_FP, MULTI_TEMPORAL, ROUTER_AMBIGUOUS, MULTI_TURN)

    fun load(): List<BehaviorCase> {
        val stream = AgentBehaviorDataset::class.java.getResourceAsStream("/expensebench/cases-v2.jsonl")
            ?: error("找不到测试资源 expensebench/cases-v2.jsonl")
        val json = Json { ignoreUnknownKeys = true }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .mapIndexed { index, line ->
                runCatching { json.decodeFromString(BehaviorCase.serializer(), line) }
                    .getOrElse { error("v2 数据集第 ${index + 1} 行解析失败：${it.message}") }
            }
    }

    fun datasetSha256(): String {
        val stream = AgentBehaviorDataset::class.java.getResourceAsStream("/expensebench/cases-v2.jsonl")
            ?: error("找不到测试资源 expensebench/cases-v2.jsonl")
        return ExpenseBenchDataset.sha256Hex(stream.use { it.readBytes() })
    }
}
