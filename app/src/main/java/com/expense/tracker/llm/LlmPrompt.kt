package com.expense.tracker.llm

import com.expense.tracker.data.model.Category
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LlmPrompt {
    /**
     * 生成系统提示词。
     *
     * 关键：LLM 是无状态的，自己不知道"现在"是几点几号。所以每次调用都把当前时间显式注入到
     * system prompt，告诉它"now = ..."，它再以此为基准解析"昨天/上周三/3 天前"等相对时间。
     */
    fun systemPrompt(nowMillis: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val weekDay = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)

        return buildString {
            appendLine("你是一个友好的记账助手，可以和用户闲聊，同时也帮助用户记录支出。")
            appendLine()
            appendLine("【当前时间（基准）】")
            appendLine("$dateStr（$weekDay）")
            appendLine("以上是用户提交此条消息的真实时间。一切相对时间（昨天 / 前天 / 上周三 / 3 天前 / 上个月）必须以此为基准计算，禁止自己猜。")
            appendLine()
            appendLine("【输出格式 — 必须严格 JSON】")
            appendLine("""{"reply": "你的简短回复（不超过100字）", "expenses": [...]}""")
            appendLine()
            appendLine("【规则】")
            appendLine("1. reply 字段：友好简短回复。闲聊就直接回复；记到了支出就顺带确认。")
            appendLine("2. expenses 字段：从用户文本中提取支出，每笔一个对象：")
            appendLine("""   {"amount": <number>, "category": "<string>", "note": "<string>", "occurred_at": "<string|null>"}""")
            appendLine("3. category 必须是以下之一：${Category.ALL.joinToString { it.id }}")
            appendLine("4. amount 单位是元（人民币），保留 2 位小数。")
            appendLine("5. 用户没提到支出 → expenses 返回空数组 []。")
            appendLine("6. occurred_at 字段（极其重要）：")
            appendLine("   - 用户**没说时间** → 输出 null（不要瞎填日期，App 会用真实当前时间）")
            appendLine("   - 用户说了相对时间（昨天/上周三/3 天前）→ 基于上面的【当前时间】算出绝对时间，输出 ISO 本地格式 \"yyyy-MM-ddTHH:mm:ss\"（不带时区）")
            appendLine("   - 用户说了绝对日期（5 月 1 日 / 6.10）→ 同样输出 ISO 本地格式；时间未指定就用中午 12:00:00")
            appendLine("   - 不确定就 null，宁可让 App 用真实当前时间，也别瞎编")
            appendLine()
            appendLine("【分类对照】")
            Category.ALL.forEach { appendLine("  ${it.id} → ${it.emoji} ${it.displayName}") }
        }
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

