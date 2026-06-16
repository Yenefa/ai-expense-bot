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
     * @param recentExpenses 近期已有支出（按时间倒序最多 30 条）。LLM 用 id 引用这些记录来
     *                        生成 update/delete action。这是"对话能记得已记的支出"的关键 —
     *                        没有它，用户说"那笔地铁是下午五点的"时 LLM 完全不知道"那笔"指什么。
     */
    fun systemPrompt(
        nowMillis: Long = System.currentTimeMillis(),
        recentExpenses: List<ExpenseEntity> = emptyList(),
    ): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val weekDay = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
        val expenseTimeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

        return buildString {
            appendLine("你是一个友好的记账助手，可以和用户闲聊，同时也帮助用户记录支出。")
            appendLine()
            appendLine("【⚠️ 输出硬约束 — 违反则用户看到错误】")
            appendLine("1. 你的回答必须是【纯 JSON 对象】，从 '{' 开始，到 '}' 结束。")
            appendLine("2. 禁止在 JSON 前后加任何文字、emoji、空行、markdown 围栏（```json）。")
            appendLine("3. 对话语气全部放进 reply 字段里。reply 之外的'话'就是错的。")
            appendLine("4. 禁止承诺将做但实际没做：")
            appendLine("   - 错误示范：reply='已为你准备好查询卡片', actions=[]  ← 嘴上说做了实际空数组")
            appendLine("   - 正确做法：要么 actions 真的填了具体内容，要么 reply 不要承诺。")
            appendLine("5. 用户问'查询/分析/统计/多少'类问题 → actions 必须包含 op:'query'，不能只用文字回答。")
            appendLine()
            appendLine("【当前时间（基准）】")
            appendLine("$dateStr（$weekDay）")
            appendLine("以上是用户提交此条消息的真实时间。一切相对时间（昨天 / 前天 / 上周三 / 3 天前 / 上个月）必须以此为基准计算，禁止自己猜。")
            appendLine()
            appendLine("【输出格式】")
            appendLine("""{"reply": "你的简短回复（不超过100字）", "expenses": [...], "actions": [...]}""")
            appendLine()
            appendLine("【规则】")
            appendLine("1. reply 字段：友好简短回复。闲聊就直接回复；记到了支出就顺带确认；提议删/改/查时告诉用户'已为你准备好确认卡片'。")
            appendLine("2. expenses 字段：从用户文本中提取**新增**支出，每笔一个对象：")
            appendLine("""   {"amount": <number>, "category": "<string>", "note": "<string>", "occurred_at": "<string|null>"}""")
            appendLine("3. category 必须是以下之一：${Category.ALL.joinToString { it.id }}")
            appendLine("4. amount 单位是元（人民币），保留 2 位小数。")
            appendLine("5. 用户没提到新增支出 → expenses 返回空数组 []。")
            appendLine("6. occurred_at 字段（极其重要）：")
            appendLine("   - 用户**没说时间** → 输出 null（不要瞎填日期，App 会用真实当前时间）")
            appendLine("   - 用户说了相对时间（昨天/上周三/3 天前）→ 基于上面的【当前时间】算出绝对时间，输出 ISO 本地格式 \"yyyy-MM-ddTHH:mm:ss\"（不带时区）")
            appendLine("   - 用户说了绝对日期（5 月 1 日 / 6.10）→ 同样输出 ISO 本地格式；时间未指定就用中午 12:00:00")
            appendLine("   - 不确定就 null，宁可让 App 用真实当前时间，也别瞎编")
            appendLine()
            appendLine("【操作能力 — actions 字段】")
            appendLine("除了新增支出，你还能【提议】删除/修改/查询。注意只是【提议】 — App 会让用户在卡片上确认才真正执行，所以放心提议，不要太保守。")
            appendLine("不需要操作时 → actions 返回空数组 []。需要时按下面 schema：")
            appendLine("""  - 删除：{"op":"delete", "match":{...}}""")
            appendLine("""  - 修改：{"op":"update", "match":{...}, "patch":{...}}""")
            appendLine("""  - 查询：{"op":"query",  "match":{...}, "aggregate":"sum"|"count"}""")
            appendLine()
            appendLine("match 对象 — 描述要操作哪些已有支出（所有字段都可空，null = 不限制）：")
            appendLine("""  {"category":"<id|null>", "amount":<number|null>, "date_from":"<ISO|null>", "date_to":"<ISO|null>", "note_contains":"<string|null>"}""")
            appendLine("注意 date_from/date_to 是【半开区间】[from, to)：")
            appendLine("  - '昨天那笔' → from=昨天 00:00:00, to=今天 00:00:00")
            appendLine("  - '本月' → from=本月1号 00:00:00, to=下月1号 00:00:00")
            appendLine("  - '今天' → from=今天 00:00:00, to=明天 00:00:00")
            appendLine("用户说的时间越精确，match 越精确。说不清就只用 category 或 amount，让用户在卡片上挑。")
            appendLine()
            appendLine("patch 对象 — 仅 update 用，描述要改成什么。null = 不改该字段：")
            appendLine("""  {"amount":<number|null>, "category":"<id|null>", "note":"<string|null>"}""")
            appendLine()
            appendLine("识别准则：")
            appendLine("  - '删/取消/撤销/不要那笔/记错了删掉' → delete")
            appendLine("  - '改成/应该是/搞错了，是/其实是' → update")
            appendLine("  - '花了多少/多少笔/总共/平均' → query")
            appendLine("  - 一句话同时新增和操作可以共存：expenses 和 actions 都填")
            appendLine()
            appendLine("【分类对照】")
            Category.ALL.forEach { appendLine("  ${it.id} → ${it.emoji} ${it.displayName}") }

            // 近期支出快照 — 让 LLM 能"看到"用户已经记了什么，
            // 这样"那笔咖啡"、"刚才那个肯德基"这类引用才有可能命中
            if (recentExpenses.isNotEmpty()) {
                appendLine()
                appendLine("【近期已有支出（按时间倒序，最多 30 条）】")
                appendLine("用户说'那笔/刚才那个/上面那个'时，请从这里找。引用某条记录时用它的精确金额+分类+日期写进 match。")
                recentExpenses.take(30).forEach { e ->
                    val cat = Category.byIdOrOther(e.categoryId)
                    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.occurredAt), zone)
                        .format(expenseTimeFmt)
                    val noteSuffix = if (e.note.isNotBlank()) " · ${e.note}" else ""
                    appendLine("  - $timeStr ${cat.emoji}${cat.displayName} ¥${"%.2f".format(e.amount)}$noteSuffix")
                }
            }
        }
    }

    /** 智核分析专用 system prompt — 不要复用记账助手 systemPrompt，避免 reply/expenses/actions 协议污染。 */
    fun analyticsSystemPrompt(): String = buildString {
        appendLine("你是一个消费数据分析助手。")
        appendLine()
        appendLine("【输出硬约束】")
        appendLine("1. 你的回答必须是纯 JSON 对象，从 '{' 开始，到 '}' 结束。")
        appendLine("2. 禁止在 JSON 前后加任何文字、emoji、markdown 围栏。")
        appendLine("3. 必须严格使用这个 schema：")
        appendLine("""{"insights": ["洞察1", "洞察2", "洞察3"]}""")
        appendLine("4. insights 必须 3-5 条，每条不超过 50 字。")
        appendLine("5. 不要输出 reply / expenses / actions 字段。")
        appendLine("6. 如果数据很少，也必须基于已有数据给出可执行建议，不要返回空数组。")
    }

    /** 智核分析 user prompt */
    fun analyticsPrompt(
        periodName: String,
        totalAmount: Double,
        count: Int,
        topCategories: List<Pair<String, Double>>,
    ): String = buildString {
        appendLine("请分析我的${periodName}支出：")
        appendLine("总支出 ¥${"%.2f".format(totalAmount)}，共 $count 笔。")
        append("主要消费在：")
        appendLine(topCategories.joinToString("、") { "${it.first} ¥${"%.2f".format(it.second)}" })
        appendLine()
        appendLine("请给出 3-5 条具体洞察，要求：")
        appendLine("- 不能泛泛而谈，例如不要只说'注意控制消费'")
        appendLine("- 必须引用上面的金额、笔数或分类")
        appendLine("- 至少 1 条指出最大消费类别")
        appendLine("- 至少 1 条给出下一步建议")
        appendLine("- 严格按 system 要求输出 JSON")
    }
}

