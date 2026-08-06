package com.expense.tracker.llm

sealed interface BillImportResult {
    data class Ok(val expenses: List<ParsedExpense>, val reply: String) : BillImportResult
    data class Error(val message: String) : BillImportResult
}

object BillImportParser {
    fun parse(raw: String): List<ParsedExpense> = parseResult(raw).expenses

    internal fun parseResult(raw: String): LlmParseResult {
        LlmResponseParser.extractJsonObjects(raw).forEach { candidate ->
            val result = runCatching { LlmResponseParser.parseBillImportJsonObject(candidate) }.getOrNull()
            if (result != null && result.expenses.isNotEmpty()) return result
        }
        throw IllegalArgumentException("没有从截图中识别到可导入的交易")
    }
}

suspend fun importFromBillText(
    client: LlmClient,
    config: AiServiceConfig,
    ocrText: String,
): BillImportResult = runCatching {
    require(ocrText.isNotBlank()) { "截图中没有识别到文字" }
    val raw = client.chatJson(
        baseUrl = config.baseUrl,
        apiKey = config.apiKey,
        model = config.model,
        userText = LlmPrompt.billImportPrompt(ocrText),
        systemPrompt = LlmPrompt.billImportSystemPrompt(),
        installationId = config.installationId,
    )
    val parsed = BillImportParser.parseResult(raw)
    BillImportResult.Ok(expenses = parsed.expenses, reply = parsed.reply)
}.getOrElse { BillImportResult.Error(it.message ?: "未知错误") }
