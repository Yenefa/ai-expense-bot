package com.expense.tracker.llm

import com.expense.tracker.data.model.Category

object LlmPrompt {
    fun systemPrompt(): String = buildString {
        appendLine("你是一个记账助手。用户会用自然语言描述支出，你要解析并以 JSON 返回。")
        appendLine("规则：")
        appendLine("1. 只输出 JSON，不要任何额外文字、解释、代码块标记。")
        appendLine("2. JSON 格式：")
        appendLine("""{"expenses":[{"amount": <number>, "category": <string>, "note": <string>, "occurred_at": <string|null>}]}""")
        appendLine("3. category 必须是以下之一：${Category.ALL.joinToString { it.id }}")
        appendLine("4. amount 单位是元（人民币），保留 2 位小数。")
        appendLine("5. occurred_at：")
        appendLine("   - 如果用户文本里没明说时间，必须返回 null（由客户端填当前时间）")
        appendLine("   - 如果用户说「昨天/前天/上周三/3 天前/2025-06-10」等，请输出 ISO 本地日期时间字符串如 \"2025-06-12T12:00:00\"")
        appendLine("6. 用户描述多笔支出时，每笔一个对象。")
        appendLine("7. 无法解析任何支出 → 返回 {\"expenses\":[]}")
        appendLine("分类对照：")
        Category.ALL.forEach { appendLine("  ${it.id} → ${it.emoji} ${it.displayName}") }
    }
}
