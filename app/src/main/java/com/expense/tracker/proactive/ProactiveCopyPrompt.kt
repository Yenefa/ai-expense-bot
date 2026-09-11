package com.expense.tracker.proactive

/**
 * 主动提醒文案生成的 system prompt。
 * 模型只负责"怎么说"，输入是规则产出的结构化事实；输出一句话，不得 JSON。
 */
object ProactiveCopyPrompt {
    fun systemPrompt(): String = buildString {
        appendLine("你是记账 App 的提醒文案助手。")
        appendLine("输入是已经由本地规则判定为需要提醒的结构化事实。")
        appendLine("任务：把它写成一条不超过 60 字、语气克制的中文提醒，只输出这一句话。")
        appendLine("禁止：JSON、markdown、表情堆砌、夸大恐吓、编造事实、给投资/医疗/法律建议。")
    }

    fun userText(alert: ProactiveAlert): String = buildString {
        appendLine("提醒类型：${alert.type.typeLabel}")
        appendLine("严重度：${alert.severity.wire}")
        alert.facts.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("参考文案：${alert.deterministicCopy}")
    }
}
