package com.expense.tracker.data.importer

import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money

data class ImportedExpense(
    val amountCents: Long,
    val categoryId: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
)

data class CsvImportIssue(
    val recordNumber: Int,
    val message: String,
)

data class CsvImportResult(
    val expenses: List<ImportedExpense>,
    val issues: List<CsvImportIssue>,
) {
    val minOccurredAt: Long? get() = expenses.minOfOrNull { it.occurredAt }
    val maxOccurredAt: Long? get() = expenses.maxOfOrNull { it.occurredAt }
}

object CsvExpenseImporter {
    private val requiredHeaders = listOf(
        "id",
        "amount",
        "categoryId",
        "note",
        "occurredAt",
        "createdAt",
    )

    fun parse(csv: String): CsvImportResult {
        val records = parseRecords(csv)
        require(records.isNotEmpty()) { "CSV 文件为空" }

        val headers = records.first().fields.mapIndexed { index, value ->
            if (index == 0) value.trim().trimStart('\uFEFF') else value.trim()
        }
        val indexes = headers.withIndex().associate { it.value to it.index }
        val missing = requiredHeaders.filterNot(indexes::containsKey)
        require(missing.isEmpty()) { "CSV 缺少字段：${missing.joinToString()}" }

        val expenses = mutableListOf<ImportedExpense>()
        val issues = mutableListOf<CsvImportIssue>()

        records.drop(1).forEach { record ->
            if (record.fields.all(String::isBlank)) return@forEach

            fun field(name: String): String =
                record.fields.getOrElse(indexes.getValue(name)) { "" }

            val amountCents = runCatching {
                Money.parseYuanToCents(field("amount"))
            }.getOrNull()
            val categoryId = field("categoryId").trim()
            val occurredAt = field("occurredAt").trim().toLongOrNull()
            val createdAt = field("createdAt").trim().toLongOrNull()
            val errors = buildList {
                if (amountCents == null) {
                    add("金额必须是大于零的数字")
                }
                if (Category.byId(categoryId) == null) {
                    add("未知分类：$categoryId")
                }
                if (occurredAt == null || occurredAt <= 0L) {
                    add("发生时间无效")
                }
                if (createdAt == null || createdAt <= 0L) {
                    add("创建时间无效")
                }
            }

            if (errors.isNotEmpty()) {
                issues += CsvImportIssue(record.recordNumber, errors.joinToString("；"))
            } else {
                expenses += ImportedExpense(
                    amountCents = amountCents!!,
                    categoryId = categoryId,
                    note = field("note"),
                    occurredAt = occurredAt!!,
                    createdAt = createdAt!!,
                )
            }
        }

        return CsvImportResult(expenses, issues)
    }

    /**
     * 解码 CSV 字节：优先 UTF-8（含 BOM），失败回退 GBK（微信/支付宝导出常见编码）。
     * 压缩包直接给出明确提示：官方账单的邮件/消息附件常常是**带密码的 ZIP**，
     * 若按文本解码只会得到乱码，用户看到的却是「缺少字段」这类风马牛不相及的错误。
     */
    internal fun decodeCsvBytes(bytes: ByteArray): String {
        require(!looksLikeZip(bytes)) { "这是压缩包（.zip），请先解压出 CSV 再导入" }
        val utf8 = runCatching {
            val text = String(bytes, Charsets.UTF_8)
            if (text.contains('\uFFFD')) throw IllegalArgumentException("not utf8")
            text
        }.getOrNull()
        return utf8?.trimStart('\uFEFF')
            ?: String(bytes, java.nio.charset.Charset.forName("GBK")).trimStart('\uFEFF')
    }

    /** ZIP 魔数：PK\x03\x04（本地文件头）/ PK\x05\x06（空压缩包）/ PK\x07\x08（跨卷）。 */
    internal fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

    internal data class CsvRecord(
        val fields: List<String>,
        val recordNumber: Int,
    )

    internal fun parseRecords(csv: String): List<CsvRecord> {
        val records = mutableListOf<CsvRecord>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordStartLine = 1
        var index = 0

        fun finishRecord() {
            fields += field.toString()
            field.setLength(0)
            records += CsvRecord(fields.toList(), recordStartLine)
            fields.clear()
            recordStartLine = line + 1
        }

        while (index < csv.length) {
            val char = csv[index]
            if (inQuotes) {
                when {
                    char == '"' && index + 1 < csv.length && csv[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    char == '"' -> inQuotes = false
                    char == '\r' -> {
                        if (index + 1 < csv.length && csv[index + 1] == '\n') index++
                        field.append('\n')
                        line++
                    }
                    char == '\n' -> {
                        field.append('\n')
                        line++
                    }
                    else -> field.append(char)
                }
            } else {
                when (char) {
                    // RFC 4180：只有**字段开头**的 `"` 起始引用；字段中间出现的 `"` 是普通字符。
                    // 旧实现把任何位置的 `"` 都当引号开关，账单里一个落单引号就能让整份文件被判「未闭合」而整体拒绝导入。
                    '"' -> if (field.isEmpty()) inQuotes = true else field.append('"')
                    ',' -> {
                        fields += field.toString()
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (index + 1 < csv.length && csv[index + 1] == '\n') index++
                        finishRecord()
                        line++
                    }
                    '\n' -> {
                        finishRecord()
                        line++
                    }
                    else -> field.append(char)
                }
            }
            index++
        }

        require(!inQuotes) { "CSV 存在未闭合的引号（第 $recordStartLine 行）" }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            finishRecord()
        }
        return records
    }
}
