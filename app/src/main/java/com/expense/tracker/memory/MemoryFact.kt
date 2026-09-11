package com.expense.tracker.memory

import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Memory v1 只支持四类长期信息（不是会话状态，也不是长期画像全集）。 */
@Serializable
enum class MemoryType {
    @SerialName("monthly_income") MONTHLY_INCOME,
    @SerialName("savings_goal") SAVINGS_GOAL,
    @SerialName("merchant_alias") MERCHANT_ALIAS,
    @SerialName("category_preference") CATEGORY_PREFERENCE;

    val wire: String
        get() = when (this) {
            MONTHLY_INCOME -> "monthly_income"
            SAVINGS_GOAL -> "savings_goal"
            MERCHANT_ALIAS -> "merchant_alias"
            CATEGORY_PREFERENCE -> "category_preference"
        }

    val typeLabel: String
        get() = when (this) {
            MONTHLY_INCOME -> "月收入"
            SAVINGS_GOAL -> "储蓄目标"
            MERCHANT_ALIAS -> "商户别名"
            CATEGORY_PREFERENCE -> "常用分类"
        }

    companion object {
        fun fromWire(value: String): MemoryType? = entries.firstOrNull { it.wire == value }
    }
}

/** 一条已确认的长期记忆事实。只有 MemoryGovernor.confirm 会创建并持久化它。 */
@Serializable
data class MemoryFact(
    val type: MemoryType,
    @SerialName("amount_cents") val amountCents: Long? = null,
    val merchant: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("raw_text") val rawText: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    val id: String = "",
) {
    fun summary(): String = when (type) {
        MemoryType.MONTHLY_INCOME -> "月收入 ¥${Money.formatYuan(amountCents ?: 0L)}"
        MemoryType.SAVINGS_GOAL -> "储蓄目标 ¥${Money.formatYuan(amountCents ?: 0L)}"
        MemoryType.MERCHANT_ALIAS ->
            "「${merchant.orEmpty()}」→ ${Category.byIdOrOther(categoryId.orEmpty()).displayName}"
        MemoryType.CATEGORY_PREFERENCE ->
            "常用分类：${Category.byIdOrOther(categoryId.orEmpty()).displayName}"
    }
}

/** 待确认提案：token 一次性，确认前不写任何持久化存储。 */
data class MemoryProposal(
    val token: String,
    val fact: MemoryFact,
) {
    val summary: String get() = fact.summary()
    val typeLabel: String get() = fact.type.typeLabel
}
