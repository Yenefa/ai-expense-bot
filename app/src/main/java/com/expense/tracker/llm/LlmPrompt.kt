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
     * v3.0 重写 system prompt — 删改优先级放在新增之前，缩小注入范围到 3 天。
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
            appendLine("你是记账助手。你的唯一输出格式是 JSON：{\"reply\":\"...\",\"expenses\":[...],\"actions\":[...]}。")
            appendLine("不要在 JSON 外加任何文字。")
            appendLine()
            appendLine("当前时间：$dateStr（$weekDay）")
            appendLine()
            appendLine("=== 第一步（优先）：判断用户是否想删除/修改 ===")
            appendLine("先检查用户意图。如果用户表达了\"删除 / 取消 / 不要了 / 改成 / 修改\"的意思，你必须：")
            appendLine("1. 在下方【你的记录】中找到最匹配的那一行")
            appendLine("2. 输出对应的 action，格式：")
            appendLine("""   {"action":"delete","expense_id":<记录ID>}""")
            appendLine("""   {"action":"update","expense_id":<记录ID>,"amount":<新金额|null>,"category":"<新分类|null>"...}""")
            appendLine("3. 只在 reply 里确认操作结果，不新增 expense")
            appendLine()
            appendLine("=== 第二步：如果没有删改意图，再看是否有新增 ===")
            appendLine("新增 expense 格式：{\"amount\":<数字>,\"category\":\"<分类>\",\"note\":\"<备注>\",\"occurred_at\":<ISO时间|null>}")
            appendLine("分类：${Category.ALL.joinToString { it.id }}")
            appendLine()
            appendLine("=== 第三步：既无删改也无新增 ===")
            appendLine("闲聊 → expenses=[], actions=[]，reply 是闲谈回复。")
            appendLine()
            appendLine("=== 关键规则 ===")
            appendLine("- 用户没指定时间 → occurred_at=null")
            appendLine("- 用户指定了相对时间（昨天/上周三/3天前）→ 基于当前时间算出 ISO 本地时间")
            appendLine("- 投资类 → category=investment")
            appendLine("- reply ≤100 字")
            appendLine("- **只能操作下方【你的记录】中出现的 expense_id，找不到就回复\"没找到\"**")
            appendLine()
            appendLine("=== 例1：删除 ===")
            appendLine("用户：菠萝百香果不要了")
            appendLine("假设记录里有 42|06-20 21:00|☕饮品|¥7.59|菠萝百香果")
            appendLine("""输出：{"reply":"已删除饮品 ¥7.59 菠萝百香果","expenses":[],"actions":[{"action":"delete","expense_id":42}]}""")
            appendLine()
            appendLine("=== 例2：修改 ===")
            appendLine("用户：晚饭改成 10 块")
            appendLine("假设记录里有 41|06-20 21:00|🍜餐饮|¥6.80|晚饭米饭")
            appendLine("""输出：{"reply":"晚饭已改为 ¥10.00","expenses":[],"actions":[{"action":"update","expense_id":41,"amount":10}]}""")
            appendLine()
            appendLine("=== 例3：新增 ===")
            appendLine("用户：午饭35")
            appendLine("""输出：{"reply":"已记 ¥35 餐饮","expenses":[{"amount":35,"category":"food","note":"午饭","occurred_at":null}],"actions":[]}""")
            appendLine()
            appendLine("【分类对照】${Category.ALL.joinToString { c -> "${c.id}→${c.emoji}${c.displayName}" }}")
            appendLine()

            if (recentRecords.isNotEmpty()) {
                appendLine("【你的记录 — 只能操作这些 ID】")
                recentRecords.forEach { e ->
                    val ts = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(timeFmt)
                    val cat = Category.byIdOrOther(e.categoryId)
                    val noteStr = if (e.note.isNotBlank()) e.note else "-"
                    appendLine("${e.id}|$ts|${cat.emoji}${cat.displayName}|¥${"%.2f".format(e.amount)}|$noteStr")
                }
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

    /**
     * 智核分析专用 system prompt：只要求 insights schema。
     * 不能复用默认的记账助手 systemPrompt（它要求 {reply,expenses,actions}），
     * 否则 system 与 analyticsPrompt 的 {insights} 要求冲突，模型在两套 schema 间反复横跳 -> 间歇性"暂无洞察"。
     */
    fun analyticsSystemPrompt(): String = buildString {
        appendLine("你是消费分析助手。你的唯一输出格式是 JSON：{\"insights\": [\"洞察1\", \"洞察2\", ...]}。")
        appendLine("只输出这个 JSON，不要加任何其他文字或字段。")
        appendLine("给出 3-5 条简洁、有针对性的消费洞察，每条不超过 50 字。")
    }

    /**
     * 账单截图导入专用 system prompt：从 OCR 文本提取每笔交易，输出 {reply,expenses}。不做删改。
     */
    fun billImportSystemPrompt(): String = buildString {
        appendLine("你是账单解析助手。用户会给你一张支付账单截图的 OCR 文本，里面可能含多笔交易。")
        appendLine("你的唯一输出格式是 JSON：{\"reply\":\"...\",\"expenses\":[...]}。不要在 JSON 外加任何文字。")
        appendLine("把每一笔交易提取成一个 expense：{\"amount\":<数字>,\"category\":\"<分类>\",\"note\":\"<商户/对方名>\",\"occurred_at\":<ISO时间|null>}。")
        appendLine("分类只能从这些里选：${Category.ALL.joinToString { it.id }}")
        appendLine("规则：")
        appendLine("- 金额取实际支出金额（正数）；退款/不计入的行不要提取。")
        appendLine("- note 用商户名或交易对方；没明确分类的归 other。")
        appendLine("- occurred_at 按 OCR 文本里的日期时间算 ISO 本地时间；没有就 null。")
        appendLine("- 忽略标题、余额、合计、分页等非交易行。")
        appendLine("- reply 用一句话总结（≤30 字），如\"已识别 3 笔，合计 ¥XX\"。")
    }

    /** 账单导入 user prompt：把 OCR 文本喂给模型。 */
    fun billImportPrompt(ocrText: String): String = buildString {
        appendLine("以下是账单截图的 OCR 文本：")
        appendLine(ocrText)
        appendLine("请提取其中的所有交易。")
    }
}
