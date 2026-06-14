package com.expense.tracker.llm

import com.expense.tracker.data.model.Category

object LlmPrompt {
    fun systemPrompt(): String = buildString {
        appendLine("你是一个友好的记账助手，可以和用户闲聊，同时也帮助用户记录支出。")
        appendLine("你必须始终以 JSON 格式回复，格式固定为：")
        appendLine("""{"reply": "你的简短回复（不超过100字）", "expenses": [...]}""")
        appendLine("规则：")
        appendLine("1. reply 字段：你的友好简短回复。如果用户只是闲聊，直接回复。如果解析到支出，回复中顺带确认。")
        appendLine("2. expenses 字段：从用户文本中提取支出，每笔一个对象：")
        appendLine("""   {"amount": <number>, "category": "<string>", "note": "<string>", "occurred_at": "<string|null>"}""")
        appendLine("3. category 必须是以下之一：${Category.ALL.joinToString { it.id }}")
        appendLine("4. amount 单位是元（人民币），保留 2 位小数。")
        appendLine("5. 如果用户没有提到支出，expenses 返回空数组 []. 不影响 reply 的正常生成。")
        appendLine("6. 如果用户指定了时间（昨天/上周三/3天前/具体日期），occurred_at 输出 ISO 本地时间如 \"2025-06-12T12:00:00\"；没说明时间则输出 null。")
        appendLine("分类对照：")
        Category.ALL.forEach { appendLine("  ${it.id} → ${it.emoji} ${it.displayName}") }
    }

    /** 智核分析 prompt */
    fun analyticsPrompt(
        periodName: String,
        totalAmount: Double,
        count: Int,
        topCategories: List<Pair<String, Double>>,
    ): String = buildString {
        appendLine("请帮我分析我的${periodName}支出：")
        appendLine("总支出 ¥${"%.2f".format(totalAmount)}，共 $count 笔。")
        append("主要消费在：")
        appendLine(topCategories.joinToString("、") { "${it.first} ¥${"%.2f".format(it.second)}" })
        appendLine("请给出 3-5 条简洁的消费洞察（每条不超过 50 字），并用以下 JSON 格式回复：")
        appendLine("""{"insights": ["洞察1", "洞察2", ...]}""")
        appendLine("洞察要有针对性，不要泛泛而谈。")
    }
}
