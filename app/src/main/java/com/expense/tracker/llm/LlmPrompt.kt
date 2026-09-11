package com.expense.tracker.llm

import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
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
        lastBatchIds: List<Long> = emptyList(),
    ): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val weekDay = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
        val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

        return buildString {
            appendLine("你是 Y.E cost 的记账 AI。你的唯一输出格式是 JSON：{\"reply\":\"...\",\"expenses\":[...],\"actions\":[...]}。")
            appendLine("不要在 JSON 外加任何文字。")
            appendLine()
            appendLine("当前时间：$dateStr（$weekDay）")
            appendLine()
            appendLine("=== 第一步（优先）：判断用户是否想删除/修改 ===")
            appendLine("先检查用户意图。如果用户表达了\"删除 / 取消 / 不要了 / 改成 / 修改\"的意思，你必须：")
            appendLine("1. 在下方【你的记录】中找到最匹配的那一行")
            appendLine("2. 输出对应的 action，格式：")
            appendLine("""   {"action":"delete","expense_id":<记录ID>}""")
            appendLine("""   {"action":"update","expense_id":<记录ID>,"amount":<新金额|null>,"category":<新分类|null>,"note":<新备注|null>,"occurred_at":<新ISO时间|null>}""")
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
            appendLine("- 用户明确给出的日期优先于历史对话；续句才可继承上一轮日期")
            appendLine("- 多笔新增必须按原文顺序输出；每个金额只能对应一笔，禁止重复或漏记")
            appendLine("- 历史对话里已经确认记录过的账目（前面用户说过、且助手已回复\"已记录\"的）以及下方【你的记录】中已存在的账目，不得再次提取；只提取本轮新出现的消费")
            appendLine("- 还有/也买/又买/再记 表示追加新账目，不是把历史里已记录的那笔重新记一遍")
            appendLine("- 同一句出现多个日期时，每笔沿用它前面最近一个明确日期，直到下一个明确日期出现")
            appendLine("- 你只提出候选变更，客户端会预览和确认；reply 不得声称已经执行")
            appendLine("- 出现它们/这些/刚才那批时，只能使用【最近一次明确批次】；无法确定具体记录时，actions 必须为空并要求澄清")
            appendLine("- 用户要求把已有账目改到某个日期或时间（如\"刚才那杯咖啡记到前天\"\"把刚才那笔挪到昨天\"）时，必须输出 update 动作，occurred_at 填基于当前时间算出的新 ISO 时间；禁止用新增一笔代替修改")
            appendLine("- 投资类 → category=investment")
            appendLine("- 书籍、课程、文具、笔、电子元器件、API、模型调用、云算力 → category=education")
            appendLine("- 日用品、超市、理发/剪发等生活服务开支 → category=shopping")
            appendLine("- 水电、物业、燃气、宽带、酒店/住宿 → category=housing")
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
            appendLine("=== 例4：追加式表达只记本轮新账目 ===")
            appendLine("用户：还有一杯奶茶16（历史里已记录过打车50）")
            appendLine("""输出：{"reply":"已记 ¥16 饮品","expenses":[{"amount":16,"category":"drink","note":"奶茶","occurred_at":null}],"actions":[]}""")
            appendLine()
            appendLine("=== 例5：修改已有账目的日期 ===")
            appendLine("用户：刚才那杯咖啡记到前天")
            appendLine("假设当前时间 2026-09-06，记录里有 7|09-06 15:00|☕饮品|¥18.00|咖啡，前天=09-04")
            appendLine("""输出：{"reply":"那杯咖啡已改到前天","expenses":[],"actions":[{"action":"update","expense_id":7,"occurred_at":"2026-09-04T15:00:00"}]}""")
            appendLine()
            appendLine("【分类对照】${Category.ALL.joinToString { c -> "${c.id}→${c.emoji}${c.displayName}" }}")
            appendLine()

            if (lastBatchIds.isNotEmpty()) {
                appendLine("【最近一次明确批次】")
                appendLine("最近一次明确批次 ID：${lastBatchIds.distinct().joinToString(",")}")
                appendLine("它们/这些/刚才那批只能指向上述 ID，禁止扩大范围。")
                appendLine()
            }

            if (recentRecords.isNotEmpty()) {
                appendLine("【你的记录 — 只能操作这些 ID】")
                recentRecords.forEach { e ->
                    val ts = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(timeFmt)
                    val cat = Category.byIdOrOther(e.categoryId)
                    val noteStr = if (e.note.isNotBlank()) e.note else "-"
                    appendLine("${e.id}|$ts|${cat.emoji}${cat.displayName}|¥${Money.formatYuan(e.amountCents)}|$noteStr")
                }
            }
        }
    }

    /** 智核分析专用协议，不能复用记账协议。 */
    fun analyticsSystemPrompt(): String = buildString {
        appendLine("你是一个消费数据分析助手。")
        appendLine("你的回答必须是纯 JSON 对象，禁止添加文字、emoji 或 markdown 围栏。")
        appendLine("严格使用 schema：{\"insights\":[\"洞察1\",\"洞察2\",\"洞察3\"]}")
        appendLine("insights 必须包含 3-5 条，每条不超过 50 字。")
        appendLine("禁止输出 reply、expenses、actions 字段。")
        appendLine("即使数据较少，也要基于已有数据给出可执行建议，不能返回空数组。")
    }

    /** 智核分析 user prompt */
    fun analyticsPrompt(
        periodName: String,
        totalAmount: Double,
        count: Int,
        topCategories: List<Pair<String, Double>>,
    ): String = buildString {
        appendLine("请分析我的${periodName}支出：")
        appendLine("总支出 ¥${"%.2f".format(java.util.Locale.US, totalAmount)}，共 $count 笔。")
        append("主要消费在：")
        appendLine(topCategories.joinToString("、") { "${it.first} ¥${"%.2f".format(java.util.Locale.US, it.second)}" })
        appendLine("请给出 3-5 条具体洞察：必须引用金额、笔数或分类；至少一条指出最大消费类别；至少一条给出下一步建议。")
        appendLine("严格按 system 要求输出 JSON。")
    }

    /** 账单截图 OCR 文本拆单专用协议。 */
    fun billImportSystemPrompt(): String = buildString {
        appendLine("你是账单解析助手。用户会给你一张支付账单截图的 OCR 文本，里面可能含多笔交易。")
        appendLine("你的唯一输出格式是 JSON：{\"reply\":\"...\",\"expenses\":[...]}。不要在 JSON 外加任何文字。")
        appendLine("把每一笔交易提取成一个 expense：{\"amount\":<数字>,\"category\":\"<分类>\",\"note\":\"<商户/对方名>\",\"occurred_at\":<ISO时间|null>}。")
        appendLine("分类只能从这些里选：${Category.ALL.joinToString { it.id }}")
        appendLine("规则：")
        appendLine("- 金额取实际支出金额（正数）；退款和不计入的行不要提取。")
        appendLine("- note 使用商户名或交易对方；没明确分类的归 other。")
        appendLine("- 书籍、课程、文具、笔、电子元器件、API、模型调用、云算力归 category=education。")
        appendLine("- 日用品/超市、理发剪发等生活服务归 shopping；水电物业燃气宽带、酒店住宿归 housing。")
        appendLine("- occurred_at 按 OCR 文本里的日期时间生成 ISO 本地时间；没有就填 null。")
        appendLine("- 忽略标题、余额、合计、分页等非交易行。")
        appendLine("- reply 用一句话总结（不超过 30 字）。")
    }

    fun billImportPrompt(ocrText: String): String = buildString {
        appendLine("以下是账单截图的 OCR 文本：")
        appendLine(ocrText.take(20_000))
        appendLine("请提取其中的所有交易。")
    }
}
