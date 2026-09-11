package com.expense.tracker.memory

/**
 * 长期记忆的**读权限范围**（Memory Consumption Policy）。
 *
 * 写有确认门，读也有边界：不允许把整个 UserProfile 塞进所有 system prompt。
 * 按任务显式授权：
 * - [NONE]：不读任何记忆（闲聊、普通兜底）
 * - [CLASSIFICATION]：只读商户别名（记账分类任务）
 * - [FINANCIAL_ANALYSIS]：读月收入 / 储蓄目标 / 常用分类（预算与分析任务）
 */
enum class MemoryReadScope(val label: String) {
    NONE("无"),
    CLASSIFICATION("记账分类"),
    FINANCIAL_ANALYSIS("财务分析"),
}

object MemoryReadPolicy {

    /** 授权块的固定前缀：生产者与评测器共用同一常量，避免字面量漂移（假阳/假阴）。 */
    const val BLOCK_MARKER = "【已授权记忆"

    fun typesFor(scope: MemoryReadScope): Set<MemoryType> = when (scope) {
        MemoryReadScope.NONE -> emptySet()
        MemoryReadScope.CLASSIFICATION -> setOf(MemoryType.MERCHANT_ALIAS)
        MemoryReadScope.FINANCIAL_ANALYSIS -> setOf(
            MemoryType.MONTHLY_INCOME,
            MemoryType.SAVINGS_GOAL,
            MemoryType.CATEGORY_PREFERENCE,
        )
    }

    fun filter(scope: MemoryReadScope, facts: List<MemoryFact>): List<MemoryFact> {
        val allowed = typesFor(scope)
        return facts.filter { it.type in allowed }
    }

    /** 生成注入 system prompt 的授权块；无授权事实时返回 null（不产生任何标记）。 */
    fun promptBlock(scope: MemoryReadScope, facts: List<MemoryFact>): String? {
        val scoped = filter(scope, facts)
        if (scoped.isEmpty()) return null
        return buildString {
            appendLine("$BLOCK_MARKER · ${scope.label}】")
            scoped.forEach { appendLine("- ${it.promptLine()}") }
            appendLine("（以上是用户已确认的长期信息，仅限本轮授权范围使用，不要扩展用途）")
        }
    }
}
