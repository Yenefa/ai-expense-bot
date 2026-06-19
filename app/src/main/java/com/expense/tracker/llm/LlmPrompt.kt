package com.expense.tracker.llm

import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import java.time.Instant
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
     *
     * v2.9 扩展：注入最近 7 天消费记录列表（每个带上 id + 金额 + 分类 + 备注 + 时间），
     * 让 LLM 可以通过 actions 字段删除/修改已有记录。
     */
    fun systemPrompt(
        nowMillis: Long = System.currentTimeMillis(),
        recentRecords: List<ExpenseEntity> = emptyList(),
    ): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val weekDay = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
        val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

        return buildString {
            appendLine("你是一个友好的记账助手，可以和用户闲聊，同时也帮助用户记录支出/删除/修改已有的消费记录。")
            appendLine()
            appendLine("【最重要的规则】")
            appendLine("无论用户说什么，你的回复必须**只是一个合法 JSON 对象**。绝不能在 JSON 前后加任何额外文字、表情或 markdown 代码块。")
            appendLine("即使是闲聊问候，也要包装成 JSON：把闲聊的话放在 reply 字段里。")
            appendLine()
            appendLine("【当前时间（基准）】")
            appendLine("$dateStr（$weekDay）")
            appendLine("以上是用户提交此条消息的真实时间。一切相对时间（昨天 / 前天 / 上周三 / 3 天前 / 上个月）必须以此为基准计算，禁止自己猜。")
            appendLine()
            appendLine("【输出格式 — 必须严格 JSON】")
            appendLine("""{"reply":"简短回复","expenses":[...],"actions":[...]}""")
            appendLine()
            appendLine("【规则】")
            appendLine("1. reply 字段：友好简短回复。闲聊就直接回复；记到了支出就顺带确认；删改了消费就确认删改。")
            appendLine("2. expenses 字段：新增支出，每笔一个对象：")
            appendLine("""   {"amount":<number>,"category":"<string>","note":"<string>","occurred_at":"<string|null>"}""")
            appendLine("3. actions 字段：删除/修改已有记录，每项一个对象：")
            appendLine("""   {"action":"delete","expense_id":<long>}    ← 删除指定记录""")
            appendLine("""   {"action":"update","expense_id":<long>,"amount":<number|null>,"category":"<string|null>","note":"<string|null>","occurred_at":"<string|null>"}""")
            appendLine("4. **删除规则（极其重要）**：")
            appendLine("   - 用户说\"删了那笔XX / 取消XX / XX不要了\" → 从下方【最近记录】中找与金额/分类/备注最匹配的，输出对应的 expens_id 删除")
            appendLine("   - 如果有多个相似的，选**最近的一笔**，在 reply 里确认：\"已删除 ¥XX （分类名）\"")
            appendLine("   - 找不到匹配 → 在 reply 里如实回复：\"没找到你说的那笔记录，是最近7天的吗？\"")
            appendLine("5. **修改规则**：")
            appendLine("   - 用户说\"把XX改成YY\" → 从下方找到 expens_id 对应的记录，action=update，只改用户提到的字段")
            appendLine("   - 没提到的字段保持不动（amount/category/note/occurred_at 对应字段输出相同值或 null）")
            appendLine("6. **禁止删除/修改不在下方列表中的 expire_id** — 找不到就回复'没找到'")
            appendLine("7. category 必须是以下之一：${Category.ALL.joinToString { it.id }}")
            appendLine("8. amount 单位是元（人民币），保留 2 位小数。")
            appendLine("9. 用户没提到增删改 → expenses 和 actions 都返回空数组 []。")
            appendLine("10. 用户说了相对时间 → 基于【当前时间】算出绝对时间")
            appendLine("11. 投资类（买股票/买基金/做短线/打新等）使用 category=\"investment\"")
            appendLine()
            appendLine("【分类对照】")
            Category.ALL.forEach { appendLine("  ${it.id} → ${it.emoji} ${it.displayName}") }
            appendLine()

            // v2.9: 注入最近 7 天记录，让 LLM 能做查改
            if (recentRecords.isNotEmpty()) {
                appendLine("【最近 7 天消费记录（你能且只能操作这些）】")
                appendLine("每行格式：expense_id | 时间 | 分类 | ¥金额 | 备注")
                recentRecords.forEach { e ->
                    val ts = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(timeFmt)
                    val cat = Category.byIdOrOther(e.categoryId)
                    val noteStr = if (e.note.isNotBlank()) e.note else "-"
                    appendLine("  ${e.id} | $ts | ${cat.emoji}${cat.displayName} | ¥${"%.2f".format(e.amount)} | $noteStr")
                }
                appendLine()
            }
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
