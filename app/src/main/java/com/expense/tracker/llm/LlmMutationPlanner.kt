package com.expense.tracker.llm

import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MutationSafetyException(message: String) : IllegalArgumentException(message)

class MutationConflictException(message: String) : IllegalStateException(message)

data class MutationPreview(
    val title: String,
    val detail: String,
    val count: Int,
    val totalCents: Long,
    val sourceDateLabels: List<String>,
    val targetDateLabel: String?,
)

data class LlmMutationPlan(
    val result: LlmParseResult,
    val targetSnapshots: Map<Long, ExpenseEntity>,
    val preview: MutationPreview,
    val requiresConfirmation: Boolean,
)

object LlmMutationPlanner {
    private val batchReference = Regex("它们|他们|这些|那些|这批|那批|刚才|上一批|那一堆|上面那")
    private val standaloneAllMove = Regex("(?:全部|全都|都).{0,8}(?:改|修改|记|移|挪|放).{0,2}(?:到|至|成|为|在)")
    private val standaloneAllDelete = Regex("(?:全部|全都|都).{0,8}(?:删|删除)")
    private val explicitDateMention = Regex(
        "(?:(?:\\d{4})\\s*年\\s*)?\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]|" +
            "(?<!\\d)\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}(?!\\d)|今天|昨天|前天",
    )
    /** 用户文本是否提到了任何时间概念；没提到时新增一律用当前时间、修改一律不改日期。 */
    private val timeMention = Regex(
        "昨天|今天|前天|上周|这周|本周|下周|上上?个?周|" +
            "周[一二三四五六日天末]|星期[一二三四五六日天]|" +
            "\\d{1,4}\\s*年|\\d{1,2}\\s*月|\\d{1,2}\\s*[日号]|" +
            "\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d+\\s*天前|" +
            "改到|移到|挪到|记到|时间|日期",
    )

    fun create(
        result: LlmParseResult,
        nowMillis: Long,
        availableRecords: List<ExpenseEntity>,
        lastBatchIds: List<Long>,
        currentText: String,
        targetDate: LocalDate?,
        sourceExpenseHints: List<SourceExpenseHint> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LlmMutationPlan {
        if (result.expenses.isNotEmpty() && result.actions.isNotEmpty()) {
            throw MutationSafetyException("AI 同时返回新增和删改，本次未执行。")
        }
        if (result.actions.any { it is ParsedAction.Update } && result.actions.any { it is ParsedAction.Delete }) {
            throw MutationSafetyException("AI 同时返回修改和删除，无法清晰确认，本次未执行。")
        }
        if (result.expenses.size > MAX_MUTATIONS || result.actions.size > MAX_MUTATIONS) {
            throw MutationSafetyException("单次操作超过 $MAX_MUTATIONS 笔，本次未执行。")
        }
        if (sourceExpenseHints.isNotEmpty()) {
            if (result.expenses.size != sourceExpenseHints.size) {
                throw MutationSafetyException("AI 返回笔数与原文金额数不一致，本次未记录。")
            }
            if (result.expenses.map { it.amountCents } != sourceExpenseHints.map { it.amountCents }) {
                throw MutationSafetyException("AI 返回金额或顺序与原文不一致，本次未记录。")
            }
        }

        val activeRecords = availableRecords.filter { it.deletedAt == null }
        val recordsById = activeRecords.associateBy { it.id }
        if (recordsById.size != activeRecords.size) {
            throw MutationSafetyException("可操作记录 ID 重复，本次未执行。")
        }

        val actionIds = result.actions.map { it.expenseId() }
        if (actionIds.any { it <= 0L } || actionIds.distinct().size != actionIds.size) {
            throw MutationSafetyException("AI 返回了无效或重复的账目 ID，本次未执行。")
        }
        if (!recordsById.keys.containsAll(actionIds)) {
            throw MutationSafetyException("AI 返回了不在本次范围内的账目，本次未修改。")
        }

        val refersToBatch = batchReference.containsMatchIn(currentText) ||
            (standaloneAllMove.containsMatchIn(currentText) &&
                explicitDateMention.findAll(currentText).count() <= 1) ||
            (standaloneAllDelete.containsMatchIn(currentText) &&
                explicitDateMention.findAll(currentText).count() == 0)
        if (refersToBatch && actionIds.isNotEmpty()) {
            val batch = lastBatchIds.filter { it > 0L }.distinct().toSet()
            if (batch.isEmpty()) {
                throw MutationSafetyException("无法确定‘刚才那批’具体指哪些账目，本次未修改。")
            }
            if (!batch.containsAll(actionIds)) {
                throw MutationSafetyException("AI 操作范围超出最近批次，本次未修改。")
            }
        }

        val normalizedExpenses = result.expenses.mapIndexed { index, item ->
            val originalMillis = item.occurredAtMillis ?: nowMillis
            val perExpenseDate = sourceExpenseHints.getOrNull(index)?.date ?: targetDate
            val occurredAtMillis = when {
                perExpenseDate != null ->
                    ChineseDateResolver.replaceDateKeepingTime(originalMillis, perExpenseDate, zone)
                // 用户没提任何时间：模型可能从历史里带出旧日期（如 8 月 1 日），
                // 必须用客户端当前时间兜底，防止新账目时间"停住"。
                !timeMention.containsMatchIn(currentText) -> nowMillis
                else -> originalMillis
            }
            item.copy(occurredAtMillis = occurredAtMillis)
        }
        val snapshots = actionIds.associateWith { recordsById.getValue(it) }
        val normalizedActions = result.actions.map { action ->
            when (action) {
                is ParsedAction.Update -> {
                    val userWantsTimeChange = targetDate != null ||
                        timeMention.containsMatchIn(currentText)
                    if (!userWantsTimeChange && action.amountCents == null &&
                        action.categoryId == null && action.note == null
                    ) {
                        throw MutationSafetyException("AI 没有给出要修改的字段，本次未执行。")
                    }
                    val normalizedTime = when {
                        targetDate != null -> ChineseDateResolver.replaceDateKeepingTime(
                            originalMillis = recordsById.getValue(action.expenseId).occurredAt,
                            targetDate = targetDate,
                            zone = zone,
                        )
                        // 用户没提时间：只改金额/分类/备注，绝不顺带改日期。
                        !timeMention.containsMatchIn(currentText) -> null
                        else -> action.occurredAtMillis?.let { modelMillis ->
                            val modelDate = Instant.ofEpochMilli(modelMillis).atZone(zone).toLocalDate()
                            ChineseDateResolver.replaceDateKeepingTime(
                                originalMillis = recordsById.getValue(action.expenseId).occurredAt,
                                targetDate = modelDate,
                                zone = zone,
                            )
                        }
                    }
                    action.copy(occurredAtMillis = normalizedTime)
                }
                is ParsedAction.Delete -> action
                is ParsedAction.Add -> throw MutationSafetyException("不支持 actions 中的新增，本次未执行。")
            }
        }
        val normalized = result.copy(
            expenses = normalizedExpenses,
            actions = normalizedActions,
        )
        val affected = snapshots.values.toList()
        val totalCents = if (normalizedExpenses.isNotEmpty()) {
            normalizedExpenses.sumExact { it.amountCents }
        } else {
            normalizedActions.sumExact { action ->
                when (action) {
                    is ParsedAction.Update -> action.amountCents
                        ?: snapshots.getValue(action.expenseId).amountCents
                    is ParsedAction.Delete -> snapshots.getValue(action.expenseId).amountCents
                    is ParsedAction.Add -> action.amountCents
                }
            }
        }
        val count = normalizedExpenses.size + normalizedActions.size
        val sourceDates = affected
            .map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().toString() }
            .distinct()
            .sorted()
        val derivedTargetDates = buildList {
            normalizedExpenses.mapNotNullTo(this) { expense ->
                expense.occurredAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
                }
            }
            normalizedActions.filterIsInstance<ParsedAction.Update>().mapNotNullTo(this) { action ->
                action.occurredAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
                }
            }
        }.distinct().sorted()
        val targetLabel = targetDate?.toString() ?: derivedTargetDates.takeIf { it.isNotEmpty() }?.joinToString("、")
        val title = when {
            normalizedActions.any { it is ParsedAction.Delete } -> "确认删除账目"
            normalizedActions.isNotEmpty() -> "确认修改账目"
            normalizedExpenses.size > 1 -> "确认批量记账"
            normalizedExpenses.size == 1 -> "确认记账"
            else -> "没有账目变更"
        }
        val detail = buildString {
            append("共 $count 笔")
            if (totalCents > 0L) append("，合计 ¥${Money.formatYuan(totalCents)}")
            if (sourceDates.isNotEmpty()) append("；原日期 ${sourceDates.joinToString("、")}")
            if (targetLabel != null) append("；目标日期 $targetLabel")
        }

        return LlmMutationPlan(
            result = normalized,
            targetSnapshots = snapshots,
            preview = MutationPreview(
                title = title,
                detail = detail,
                count = count,
                totalCents = totalCents,
                sourceDateLabels = sourceDates,
                targetDateLabel = targetLabel,
            ),
            // 按产品准则：只有删除需要用户确认；批量新增和修改直接执行，不弹确认框。
            requiresConfirmation = normalizedActions.any { it is ParsedAction.Delete },
        )
    }

    private fun ParsedAction.expenseId(): Long = when (this) {
        is ParsedAction.Delete -> expenseId
        is ParsedAction.Update -> expenseId
        is ParsedAction.Add -> throw MutationSafetyException("不支持 actions 中的新增，本次未执行。")
    }

    private inline fun <T> Iterable<T>.sumExact(selector: (T) -> Long): Long =
        fold(0L) { total, item ->
            try {
                Math.addExact(total, selector(item))
            } catch (_: ArithmeticException) {
                throw MutationSafetyException("账目合计超出安全范围，本次未执行。")
            }
        }

    private const val MAX_MUTATIONS = 50
}
