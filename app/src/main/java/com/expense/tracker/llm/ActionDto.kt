package com.expense.tracker.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LLM 提议的"操作"。注意是【提议】 — App 会让用户在 ActionCard 上确认后才真正执行。
 *
 * op 取值：
 *  - "delete" — 用 match 在 DB 里搜候选，弹卡片让用户勾选要删的
 *  - "update" — 用 match 搜候选 + patch 描述要改成什么；患者勾选后批量 patch
 *  - "query"  — 只读聚合，不写库
 *
 * 设计取舍：
 *  - 不让 LLM 输出 expense.id —— 用户只会说"昨天那笔咖啡"，让 LLM 出 id 等于让它出幻觉
 *  - match 字段全 nullable —— null 表示"不限制"，对应 SQL "(:x IS NULL OR col = :x)"
 *  - patch 字段全 nullable —— null 表示"不改该字段"，避免 LLM 把没说的字段填默认值覆盖真实数据
 */
@Serializable
data class LlmAction(
    val op: String,
    val match: ActionMatch? = null,
    val patch: ActionPatch? = null,
    val aggregate: String? = null, // "sum" | "count" | null（仅 query 用）
)

@Serializable
data class ActionMatch(
    val category: String? = null,
    val amount: Double? = null,
    @SerialName("date_from") val dateFrom: String? = null, // ISO LocalDateTime "yyyy-MM-ddTHH:mm:ss"
    @SerialName("date_to")   val dateTo: String? = null,
    @SerialName("note_contains") val noteContains: String? = null,
)

@Serializable
data class ActionPatch(
    val amount: Double? = null,
    val category: String? = null,
    val note: String? = null,
)
