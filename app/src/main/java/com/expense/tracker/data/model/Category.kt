package com.expense.tracker.data.model

data class Category(
    val id: String,
    val emoji: String,
    val displayName: String,
    /** true = 投资类（短线/股票/基金等），不计入消费分析图表，避免扭曲洞察 */
    val isInvestment: Boolean = false,
) {
    companion object {
        val ALL: List<Category> = listOf(
            Category("food",          "🍜", "餐饮"),
            Category("transport",     "🚗", "交通"),
            Category("shopping",      "🛒", "购物"),
            Category("drink",         "☕", "饮品"),
            Category("entertainment", "🎮", "娱乐"),
            Category("housing",       "🏠", "住房"),
            Category("medical",       "💊", "医疗"),
            Category("investment",    "💹", "投资", isInvestment = true),
            Category("other",         "📦", "其他"),
        )
        private val byIdMap: Map<String, Category> = ALL.associateBy { it.id }
        fun byId(id: String): Category? = byIdMap[id]
        fun byIdOrOther(id: String): Category = byIdMap[id] ?: byIdMap.getValue("other")
    }
}
