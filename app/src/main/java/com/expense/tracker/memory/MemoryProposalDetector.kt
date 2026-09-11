package com.expense.tracker.memory

import com.expense.tracker.agent.AgentCategories

/** 检测器输出：尚未通过类型校验的草稿。 */
data class MemoryDraft(
    val type: MemoryType,
    val amountCents: Long? = null,
    val merchant: String? = null,
    val categoryId: String? = null,
    val rawText: String = "",
)

/**
 * Memory v1 的确定性检测器（本地、免费、不调用 LLM）：
 * 把「月收入 / 储蓄目标 / 商户别名 / 常用分类」表达转成草稿；
 * 是否合法由 [MemoryTypeValidator] 校验，是否落库由 [MemoryGovernor] 的确认门决定。
 *
 * 边界：只识别高置信句式；第三方转述（同事/朋友/网上…）一律不提案。
 */
object MemoryProposalDetector {

    private val THIRD_PARTY = Regex("同事|朋友|别人|网上|听说|群里|他说|她说")
    private val INCOME = Regex("月收入|月薪|每月工资|每月收入|每月到手|到手工资|工资")
    private val SAVINGS = Regex("每(?:月|次)(?:都)?(?:想|要|先|计划)?存|储蓄|攒")
    private val ALIAS = Regex(
        "(?:以后)?把?(.{1,10}?)(?:都|全部|统一)?(?:算作?|记成|记到|归到|归类?)(.{1,10})",
    )
    private val PREFERENCE = Regex(
        "(?:主要|大部分|大多数)(?:的)?(?:花销|消费|支出|开销)(?:都|集中)?(?:在|是)(.{1,8})|" +
            "(?:平时|经常|常常)(?:消费|花钱)(?:最多|主要)?(?:的)?(?:是|在)?(.{1,8})",
    )
    private val AMOUNT = Regex("(\\d{1,9}(?:\\.\\d{1,2})?)\\s*(万|千|w|k)?")

    /** 金额必须紧邻关键词，避免"发工资后打车花了23"被读成月收入 23。 */
    private const val MAX_KEYWORD_AMOUNT_GAP = 5

    fun detect(text: String): MemoryDraft? {
        val normalized = text.trim()
        if (normalized.isEmpty() || THIRD_PARTY.containsMatchIn(normalized)) return null
        val amount = firstAmount(normalized)

        // 收入与储蓄可能同句出现（"每次发工资先攒3000"）：取离金额更近的关键词。
        val scored = buildList {
            if (amount != null) {
                INCOME.find(normalized)?.let { keyword ->
                    if (amount.isNear(keyword.range.last)) {
                        add(
                            amount.distanceTo(keyword.range.last) to
                                MemoryDraft(MemoryType.MONTHLY_INCOME, amountCents = amount.cents, rawText = normalized),
                        )
                    }
                }
                SAVINGS.find(normalized)?.let { keyword ->
                    if (amount.isNear(keyword.range.last)) {
                        add(
                            amount.distanceTo(keyword.range.last) to
                                MemoryDraft(MemoryType.SAVINGS_GOAL, amountCents = amount.cents, rawText = normalized),
                        )
                    }
                }
            }
        }
        scored.minByOrNull { it.first }?.let { return it.second }

        ALIAS.find(normalized)?.let { match ->
            val merchant = match.groupValues[1].trim().trim('的', '都', '，', ',', ' ')
            val categoryId = resolveSingleCategory(match.groupValues[2])
            return MemoryDraft(
                MemoryType.MERCHANT_ALIAS,
                merchant = merchant.ifBlank { null },
                categoryId = categoryId,
                rawText = normalized,
            )
        }
        PREFERENCE.find(normalized)?.let { match ->
            val categoryText = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
            return MemoryDraft(
                MemoryType.CATEGORY_PREFERENCE,
                categoryId = resolveSingleCategory(categoryText),
                rawText = normalized,
            )
        }
        return null
    }

    private data class AmountMatch(val cents: Long, val start: Int, val endExclusive: Int) {
        fun distanceTo(keywordEnd: Int): Int = kotlin.math.abs(start - keywordEnd)

        fun isNear(keywordEnd: Int): Boolean = distanceTo(keywordEnd) <= MAX_KEYWORD_AMOUNT_GAP
    }

    /** 解析首个金额（支持 元/块/万/千/k/w），返回分与位置。 */
    private fun firstAmount(text: String): AmountMatch? {
        val match = AMOUNT.find(text) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "万", "w" -> 10_000.0
            "千", "k" -> 1_000.0
            else -> 1.0
        }
        val cents = Math.round(number * multiplier * 100)
        return cents.takeIf { it > 0L }?.let { AmountMatch(it, match.range.first, match.range.last + 1) }
    }

    private fun resolveSingleCategory(text: String): String? =
        AgentCategories.resolve(text.trim()).singleOrNull()
}
