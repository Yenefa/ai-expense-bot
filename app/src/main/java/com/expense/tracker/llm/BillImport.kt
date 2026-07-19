package com.expense.tracker.llm

import com.expense.tracker.data.prefs.UserPrefsSnapshot

sealed interface BillImportResult {
    data class Ok(val expenses: List<ParsedExpense>, val reply: String) : BillImportResult
    data class Error(val message: String) : BillImportResult
}

/** 截图录账：把账单 OCR 文本喂给文本 LLM，解析出多笔 expense（不直接入库，交确认页编辑）。 */
suspend fun importFromBillText(
    client: LlmClient,
    prefs: UserPrefsSnapshot,
    ocrText: String,
): BillImportResult = runCatching {
    val raw = client.chatJson(
        baseUrl = prefs.baseUrl,
        apiKey = prefs.apiKey,
        model = prefs.model,
        userText = LlmPrompt.billImportPrompt(ocrText),
        systemPrompt = LlmPrompt.billImportSystemPrompt(),
    )
    val parsed = LlmResponseParser.parse(raw)
    BillImportResult.Ok(parsed.expenses, parsed.reply)
}.getOrElse { BillImportResult.Error(it.message ?: "未知错误") }
