package com.expense.tracker.data.export

import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.db.RecurringRuleEntity
import com.expense.tracker.data.db.toExpenseIdsCsvOrNull
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.model.Money
import com.expense.tracker.data.prefs.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class BackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

data class BackupPreferences(
    val llmEnabled: Boolean,
    val baseUrl: String,
    val model: String,
    val themeMode: ThemeMode,
)

data class BackupData(
    val formatVersion: Int,
    val sourceAppVersion: String,
    val exportedAt: String,
    val expenses: List<ExpenseEntity>,
    val chatMessages: List<ChatMessageEntity>,
    val preferences: BackupPreferences,
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
)

/** Versioned JSON backup codec plus the existing spreadsheet-oriented CSV export. */
object DataExporter {
    const val CURRENT_FORMAT_VERSION = 2

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class BackupEnvelope(
        val formatVersion: Int,
        val sourceAppVersion: String,
        val exportedAt: String,
        val expenses: List<ExpenseDto>,
        val chatMessages: List<ChatMessageDto>,
        val preferences: PreferencesDto,
        val recurringRules: List<RecurringRuleDto> = emptyList(),
    )

    @Serializable
    private data class RecurringRuleDto(
        val id: Long,
        val amountCents: Long,
        val categoryId: String,
        val note: String,
        val periodType: String,
        val dayOfMonth: Int,
        val dayOfWeek: Int,
        val monthOfYear: Int,
        val nextDueAt: Long,
        val enabled: Boolean,
        val createdAt: Long,
    )

    @Serializable
    private data class ExpenseDto(
        val id: Long,
        val amountCents: Long,
        val categoryId: String,
        val note: String,
        val occurredAt: Long,
        val createdAt: Long,
        val deletedAt: Long? = null,
    )

    @Serializable
    private data class ChatMessageDto(
        val id: Long,
        val role: String,
        val content: String,
        val createdAt: Long,
        val relatedExpenseId: Long? = null,
        val relatedExpenseIds: List<Long> = emptyList(),
    )

    @Serializable
    private data class PreferencesDto(
        val llmEnabled: Boolean,
        val baseUrl: String,
        val model: String,
        val themeMode: String,
    )

    @Serializable
    private data class LegacyEnvelope(
        val version: String,
        val exportedAt: String,
        val expenses: List<LegacyExpenseDto>,
        val chatMessages: List<ChatMessageDto>,
    )

    @Serializable
    private data class LegacyExpenseDto(
        val id: Long,
        val amount: JsonElement,
        val categoryId: String,
        val note: String,
        val occurredAt: Long,
        val createdAt: Long,
    )

    fun toBackupJson(
        expenses: List<ExpenseEntity>,
        chatMessages: List<ChatMessageEntity>,
        preferences: BackupPreferences,
        sourceAppVersion: String,
        exportedAtIso: String,
        recurringRules: List<RecurringRuleEntity> = emptyList(),
    ): String {
        val data = BackupData(
            formatVersion = CURRENT_FORMAT_VERSION,
            sourceAppVersion = sourceAppVersion,
            exportedAt = exportedAtIso,
            expenses = expenses,
            chatMessages = chatMessages,
            preferences = preferences,
            recurringRules = recurringRules,
        )
        validate(data)
        return json.encodeToString(data.toEnvelope())
    }

    fun parseBackup(content: String): BackupData = try {
        val root = json.parseToJsonElement(content).jsonObject
        val formatVersion = root["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull()
        val data = if (formatVersion == null) {
            json.decodeFromString<LegacyEnvelope>(content).toBackupData()
        } else {
            if (formatVersion != CURRENT_FORMAT_VERSION) {
                throw BackupFormatException("不支持的备份格式版本：$formatVersion")
            }
            json.decodeFromString<BackupEnvelope>(content).toBackupData()
        }
        validate(data)
        data
    } catch (e: BackupFormatException) {
        throw e
    } catch (e: Exception) {
        throw BackupFormatException("备份文件格式无效", e)
    }

    fun toCsv(expenses: List<ExpenseEntity>): String {
        val header = "id,amount,categoryId,note,occurredAt,createdAt"
        val rows = expenses.joinToString("\n") { expense ->
            listOf(
                expense.id.toString(),
                Money.formatYuan(expense.amountCents),
                csvEscape(expense.categoryId),
                csvEscape(expense.note),
                expense.occurredAt.toString(),
                expense.createdAt.toString(),
            ).joinToString(",")
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    private fun validate(data: BackupData) {
        invalidIf(data.formatVersion != CURRENT_FORMAT_VERSION, "不支持的备份格式版本：${data.formatVersion}")
        invalidIf(data.sourceAppVersion.isBlank(), "备份缺少应用版本")
        invalidIf(runCatching { Instant.parse(data.exportedAt) }.isFailure, "导出时间无效")
        invalidIf(data.expenses.map { it.id }.toSet().size != data.expenses.size, "账目 ID 重复")
        invalidIf(data.chatMessages.map { it.id }.toSet().size != data.chatMessages.size, "聊天消息 ID 重复")
        data.expenses.forEach { expense ->
            invalidIf(expense.id <= 0L, "账目 ID 无效")
            invalidIf(expense.amountCents <= 0L, "账目金额必须大于零")
            invalidIf(Category.byId(expense.categoryId) == null, "未知账目分类：${expense.categoryId}")
            invalidIf(expense.occurredAt <= 0L || expense.createdAt <= 0L, "账目时间无效")
            invalidIf(expense.deletedAt != null && expense.deletedAt <= 0L, "删除时间无效")
        }
        data.chatMessages.forEach { message ->
            invalidIf(message.id <= 0L, "聊天消息 ID 无效")
            invalidIf(message.role !in setOf("user", "assistant"), "聊天消息角色无效")
            invalidIf(message.createdAt <= 0L, "聊天消息时间无效")
            invalidIf(message.relatedExpenseId != null && message.relatedExpenseId <= 0L, "关联账目 ID 无效")
            invalidIf(message.relatedExpenseIds().any { it <= 0L }, "关联账目批次 ID 无效")
        }
    }

    private fun invalidIf(condition: Boolean, message: String) {
        if (condition) throw BackupFormatException(message)
    }

    private fun BackupData.toEnvelope() = BackupEnvelope(
        formatVersion = formatVersion,
        sourceAppVersion = sourceAppVersion,
        exportedAt = exportedAt,
        expenses = expenses.map {
            ExpenseDto(it.id, it.amountCents, it.categoryId, it.note, it.occurredAt, it.createdAt, it.deletedAt)
        },
        chatMessages = chatMessages.map {
            ChatMessageDto(
                it.id,
                it.role,
                it.content,
                it.createdAt,
                it.relatedExpenseId,
                if (it.relatedExpenseIdsCsv == null) emptyList() else it.relatedExpenseIds(),
            )
        },
        preferences = PreferencesDto(
            preferences.llmEnabled,
            preferences.baseUrl,
            preferences.model,
            preferences.themeMode.name,
        ),
        recurringRules = recurringRules.map {
            RecurringRuleDto(
                id = it.id,
                amountCents = it.amountCents,
                categoryId = it.categoryId,
                note = it.note,
                periodType = it.periodType,
                dayOfMonth = it.dayOfMonth,
                dayOfWeek = it.dayOfWeek,
                monthOfYear = it.monthOfYear,
                nextDueAt = it.nextDueAt,
                enabled = it.enabled,
                createdAt = it.createdAt,
            )
        },
    )

    private fun BackupEnvelope.toBackupData() = BackupData(
        formatVersion = formatVersion,
        sourceAppVersion = sourceAppVersion,
        exportedAt = exportedAt,
        expenses = expenses.map {
            ExpenseEntity(it.amountCents, it.categoryId, it.note, it.occurredAt, it.createdAt, it.deletedAt, it.id)
        },
        chatMessages = chatMessages.map {
            if (it.relatedExpenseIds.any { id -> id <= 0L } ||
                it.relatedExpenseIds.distinct().size != it.relatedExpenseIds.size
            ) {
                throw BackupFormatException("关联账目批次 ID 无效")
            }
            ChatMessageEntity(
                role = it.role,
                content = it.content,
                createdAt = it.createdAt,
                relatedExpenseId = it.relatedExpenseId,
                relatedExpenseIdsCsv = it.relatedExpenseIds.toExpenseIdsCsvOrNull(),
                id = it.id,
            )
        },
        preferences = BackupPreferences(
            llmEnabled = preferences.llmEnabled,
            baseUrl = preferences.baseUrl,
            model = preferences.model,
            themeMode = runCatching { ThemeMode.valueOf(preferences.themeMode) }
                .getOrElse { throw BackupFormatException("主题设置无效") },
        ),
        recurringRules = recurringRules.map {
            RecurringRuleEntity(
                amountCents = it.amountCents,
                categoryId = it.categoryId,
                note = it.note,
                periodType = it.periodType,
                dayOfMonth = it.dayOfMonth,
                dayOfWeek = it.dayOfWeek,
                monthOfYear = it.monthOfYear,
                nextDueAt = it.nextDueAt,
                enabled = it.enabled,
                createdAt = it.createdAt,
                id = it.id,
            )
        },
    )

    private fun LegacyEnvelope.toBackupData() = BackupData(
        formatVersion = CURRENT_FORMAT_VERSION,
        sourceAppVersion = version,
        exportedAt = exportedAt,
        expenses = expenses.map {
            ExpenseEntity(
                amountCents = runCatching { Money.parseYuanToCents(it.amount.jsonPrimitive.content) }
                    .getOrElse { throw BackupFormatException("旧备份中的金额无效") },
                categoryId = it.categoryId,
                note = it.note,
                occurredAt = it.occurredAt,
                createdAt = it.createdAt,
                id = it.id,
            )
        },
        chatMessages = chatMessages.map {
            ChatMessageEntity(
                role = it.role,
                content = it.content,
                createdAt = it.createdAt,
                relatedExpenseId = it.relatedExpenseId,
                relatedExpenseIdsCsv = it.relatedExpenseIds.toExpenseIdsCsvOrNull(),
                id = it.id,
            )
        },
        preferences = BackupPreferences(
            llmEnabled = false,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            themeMode = ThemeMode.SYSTEM,
        ),
    )

    private fun csvEscape(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (!needsQuote) value else "\"" + value.replace("\"", "\"\"") + "\""
    }
}
