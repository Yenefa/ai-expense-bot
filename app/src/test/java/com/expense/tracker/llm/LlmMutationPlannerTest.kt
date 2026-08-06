package com.expense.tracker.llm

import com.expense.tracker.data.db.ExpenseEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LlmMutationPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = millis(2026, 8, 2, 22, 14)
    private val records = listOf(
        ExpenseEntity(600L, "drink", "矿泉水", millis(2026, 8, 2, 16, 0), 1L, id = 7L),
        ExpenseEntity(1_300L, "food", "麻薯", millis(2026, 8, 2, 18, 0), 2L, id = 8L),
        ExpenseEntity(700L, "transport", "地铁", millis(2026, 8, 1, 19, 30), 3L, id = 9L),
    )

    @Test fun forcedDateNormalizesAddsAndKeepsRecognizedTime() {
        val modelTime = millis(2026, 8, 2, 16, 0)
        val result = LlmParseResult(
            reply = "候选2笔",
            expenses = listOf(
                ParsedExpense(600L, "drink", "水", null),
                ParsedExpense(1_300L, "food", "麻薯", modelTime),
            ),
        )

        val plan = LlmMutationPlanner.create(
            result = result,
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = emptyList(),
            currentText = "8月1日两瓶水和麻薯",
            targetDate = LocalDate.of(2026, 8, 1),
            zone = zone,
        )

        assertThat(localDateTime(plan.result.expenses[0].occurredAtMillis!!))
            .isEqualTo(LocalDateTime.of(2026, 8, 1, 22, 14))
        assertThat(localDateTime(plan.result.expenses[1].occurredAtMillis!!))
            .isEqualTo(LocalDateTime.of(2026, 8, 1, 16, 0))
        assertThat(plan.requiresConfirmation).isFalse()
        assertThat(plan.preview.count).isEqualTo(2)
        assertThat(plan.preview.totalCents).isEqualTo(1_900L)
    }

    @Test fun dateOnlyUpdateKeepsEachRecordsOriginalTime() {
        val result = LlmParseResult(
            reply = "候选修改",
            expenses = emptyList(),
            actions = listOf(
                ParsedAction.Update(7L, null, null, null, null),
                ParsedAction.Update(8L, null, null, null, null),
            ),
        )

        val plan = LlmMutationPlanner.create(
            result = result,
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = listOf(7L, 8L),
            currentText = "把它们全部改到7月26号",
            targetDate = LocalDate.of(2026, 7, 26),
            zone = zone,
        )

        val updates = plan.result.actions.map { it as ParsedAction.Update }
        assertThat(localDateTime(updates[0].occurredAtMillis!!))
            .isEqualTo(LocalDateTime.of(2026, 7, 26, 16, 0))
        assertThat(localDateTime(updates[1].occurredAtMillis!!))
            .isEqualTo(LocalDateTime.of(2026, 7, 26, 18, 0))
        assertThat(plan.requiresConfirmation).isFalse()
        assertThat(plan.preview.targetDateLabel).isEqualTo("2026-07-26")
    }

    @Test fun previewShowsModelSuppliedTargetDateWhenLocalRuleDidNotResolveIt() {
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选修改",
                expenses = emptyList(),
                actions = listOf(
                    ParsedAction.Update(
                        7L,
                        null,
                        null,
                        null,
                        millis(2026, 7, 22, 9, 45),
                    ),
                ),
            ),
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = emptyList(),
            currentText = "把水改到上周三",
            targetDate = null,
            zone = zone,
        )

        assertThat(plan.preview.targetDateLabel).isEqualTo("2026-07-22")
        assertThat(localDateTime((plan.result.actions.single() as ParsedAction.Update).occurredAtMillis!!))
            .isEqualTo(LocalDateTime.of(2026, 7, 22, 16, 0))
    }

    @Test fun pronounBatchCannotExpandBeyondLastSuccessfulBatch() {
        val error = assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = updates(7L, 8L, 9L),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = listOf(7L, 8L),
                currentText = "把它们全部改到7月26号",
                targetDate = LocalDate.of(2026, 7, 26),
                zone = zone,
            )
        }

        assertThat(error).hasMessageThat().contains("最近批次")
    }

    @Test fun standaloneAllFollowUpAlsoCannotExpandBeyondLastBatch() {
        val error = assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = updates(7L, 8L, 9L),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = listOf(7L, 8L),
                currentText = "全部记到7月26号吧",
                targetDate = LocalDate.of(2026, 7, 26),
                zone = zone,
            )
        }

        assertThat(error).hasMessageThat().contains("最近批次")
    }

    @Test fun allAndDouFollowUpsCannotExpandBeyondLastBatch() {
        listOf("都改到7月26号", "全部删除").forEach { text ->
            val error = assertThrows(MutationSafetyException::class.java) {
                LlmMutationPlanner.create(
                    result = if (text.contains("删除")) {
                        LlmParseResult("候选删除", emptyList(), listOf(
                            ParsedAction.Delete(7L), ParsedAction.Delete(8L), ParsedAction.Delete(9L),
                        ))
                    } else {
                        updates(7L, 8L, 9L)
                    },
                    nowMillis = now,
                    availableRecords = records,
                    lastBatchIds = listOf(7L, 8L),
                    currentText = text,
                    targetDate = if (text.contains("改")) LocalDate.of(2026, 7, 26) else null,
                    zone = zone,
                )
            }
            assertThat(error).hasMessageThat().contains("最近批次")
        }
    }

    @Test fun mixedUpdateAndDeleteIsRejectedBecausePreviewWouldBeAmbiguous() {
        assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = LlmParseResult(
                    reply = "混合删改",
                    expenses = emptyList(),
                    actions = listOf(
                        ParsedAction.Update(7L, null, null, "水", null),
                        ParsedAction.Delete(8L),
                    ),
                ),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = emptyList(),
                currentText = "修改水并删除麻薯",
                targetDate = null,
                zone = zone,
            )
        }
    }

    @Test fun ambiguousPronounWithoutBatchIsRejected() {
        val error = assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = updates(7L),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = emptyList(),
                currentText = "把这些改到昨天",
                targetDate = LocalDate.of(2026, 8, 1),
                zone = zone,
            )
        }

        assertThat(error).hasMessageThat().contains("无法确定")
    }

    @Test fun actionOutsideProvidedRecordsAndDuplicateIdsAreRejected() {
        assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = updates(99L),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = emptyList(),
                currentText = "修改99",
                targetDate = null,
                zone = zone,
            )
        }
        assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = updates(7L, 7L),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = emptyList(),
                currentText = "修改这笔",
                targetDate = null,
                zone = zone,
            )
        }
    }

    @Test fun modelCannotMixAddsWithUpdatesAndOnlySingleAddSkipsConfirmation() {
        assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = LlmParseResult(
                    reply = "混合",
                    expenses = listOf(ParsedExpense(100L, "other", "新", null)),
                    actions = listOf(ParsedAction.Delete(7L)),
                ),
                nowMillis = now,
                availableRecords = records,
                lastBatchIds = emptyList(),
                currentText = "混合操作",
                targetDate = null,
                zone = zone,
            )
        }

        val single = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "单笔",
                expenses = listOf(ParsedExpense(100L, "other", "新", null)),
            ),
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = emptyList(),
            currentText = "买东西1元",
            targetDate = null,
            zone = zone,
        )

        assertThat(single.requiresConfirmation).isFalse()
    }

    @Test fun sourceHintsOverrideEachAddedDateWithoutMovingTheWholeBatch() {
        val hints = listOf(
            SourceExpenseHint(690L, LocalDate.of(2026, 8, 1)),
            SourceExpenseHint(1_900L, LocalDate.of(2026, 8, 2)),
            SourceExpenseHint(760L, LocalDate.of(2026, 8, 1)),
        )
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选3笔",
                expenses = listOf(
                    ParsedExpense(690L, "drink", "红茶", millis(2026, 8, 1, 22, 1)),
                    // 模型把炸鸡日期猜错时，客户端仍以原文逐笔日期为准。
                    ParsedExpense(1_900L, "food", "炸鸡", millis(2026, 8, 1, 13, 0)),
                    ParsedExpense(760L, "transport", "正弘城地铁", millis(2026, 8, 2, 11, 0)),
                ),
            ),
            nowMillis = now,
            availableRecords = emptyList(),
            lastBatchIds = emptyList(),
            currentText = ExpenseTextInterpreterTest.REPORTED_INPUT,
            targetDate = null,
            sourceExpenseHints = hints,
            zone = zone,
        )

        assertThat(plan.result.expenses.map { localDateTime(it.occurredAtMillis!!).toLocalDate() })
            .containsExactly(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1),
            ).inOrder()
        assertThat(localDateTime(plan.result.expenses[1].occurredAtMillis!!).toLocalTime())
            .isEqualTo(LocalDateTime.of(2026, 8, 2, 13, 0).toLocalTime())
    }

    @Test fun duplicateOrMissingModelExpenseIsRejectedAgainstSourceHints() {
        val hints = listOf(
            SourceExpenseHint(1_900L, LocalDate.of(2026, 8, 2)),
            SourceExpenseHint(760L, LocalDate.of(2026, 8, 1)),
        )
        val duplicate = LlmParseResult(
            reply = "候选3笔",
            expenses = listOf(
                ParsedExpense(1_900L, "food", "炸鸡", null),
                ParsedExpense(760L, "transport", "正弘城地铁", null),
                ParsedExpense(760L, "transport", "正弘城地铁", null),
            ),
        )

        val error = assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = duplicate,
                nowMillis = now,
                availableRecords = emptyList(),
                lastBatchIds = emptyList(),
                currentText = ExpenseTextInterpreterTest.REPORTED_INPUT,
                targetDate = null,
                sourceExpenseHints = hints,
                zone = zone,
            )
        }

        assertThat(error).hasMessageThat().contains("原文")
    }

    @Test fun reorderedOrWrongModelAmountsAreRejectedAgainstSourceHints() {
        val hints = listOf(
            SourceExpenseHint(1_900L, LocalDate.of(2026, 8, 2)),
            SourceExpenseHint(760L, LocalDate.of(2026, 8, 1)),
        )

        assertThrows(MutationSafetyException::class.java) {
            LlmMutationPlanner.create(
                result = LlmParseResult(
                    reply = "顺序错误",
                    expenses = listOf(
                        ParsedExpense(760L, "transport", "正弘城地铁", null),
                        ParsedExpense(1_900L, "food", "炸鸡", null),
                    ),
                ),
                nowMillis = now,
                availableRecords = emptyList(),
                lastBatchIds = emptyList(),
                currentText = ExpenseTextInterpreterTest.REPORTED_INPUT,
                targetDate = null,
                sourceExpenseHints = hints,
                zone = zone,
            )
        }
    }

    private fun updates(vararg ids: Long) = LlmParseResult(
        reply = "候选修改",
        expenses = emptyList(),
        actions = ids.map { ParsedAction.Update(it, null, null, null, null) },
    )

    @Test fun batchAddsWithoutAnyDateUseClientNowNotStaleModelDate() {
        val staleModelDate = millis(2026, 8, 1, 9, 0)
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选2笔",
                expenses = listOf(
                    // 模型从历史里带出 8 月 1 日的旧日期——客户端必须用当前时间覆盖。
                    ParsedExpense(600L, "drink", "水", staleModelDate),
                    ParsedExpense(1_300L, "food", "麻薯", null),
                ),
            ),
            nowMillis = now,
            availableRecords = emptyList(),
            lastBatchIds = emptyList(),
            currentText = "买水和麻薯",
            targetDate = null,
            zone = zone,
        )

        assertThat(plan.result.expenses.map { it.occurredAtMillis })
            .containsExactly(now, now).inOrder()
        assertThat(plan.requiresConfirmation).isFalse()
    }

    @Test fun updateWithoutTimeMentionNeverMovesTheDate() {
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选修改",
                expenses = emptyList(),
                actions = listOf(
                    // 模型又给了 8 月 1 日的旧日期，但用户根本没提时间。
                    ParsedAction.Update(7L, 2_000L, null, null, millis(2026, 8, 1, 9, 0)),
                ),
            ),
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = emptyList(),
            currentText = "把这瓶水改成20块",
            targetDate = null,
            zone = zone,
        )

        val update = plan.result.actions.single() as ParsedAction.Update
        assertThat(update.amountCents).isEqualTo(2_000L)
        assertThat(update.occurredAtMillis).isNull()
        assertThat(plan.requiresConfirmation).isFalse()
    }

    @Test fun updateWithRelativeTimeMentionStillHonorsModelDate() {
        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选修改",
                expenses = emptyList(),
                actions = listOf(
                    ParsedAction.Update(7L, null, null, null, millis(2026, 7, 29, 9, 45)),
                ),
            ),
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = emptyList(),
            currentText = "把水改到上周三",
            targetDate = null,
            zone = zone,
        )

        val update = plan.result.actions.single() as ParsedAction.Update
        assertThat(localDateTime(update.occurredAtMillis!!))
            .isEqualTo(LocalDateTime.of(2026, 7, 29, 16, 0))
    }

    @Test fun deletesAlwaysRequireConfirmationButEditsDoNot() {
        val deletePlan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选删除",
                expenses = emptyList(),
                actions = listOf(ParsedAction.Delete(7L), ParsedAction.Delete(8L)),
            ),
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = listOf(7L, 8L),
            currentText = "把这两笔删掉",
            targetDate = null,
            zone = zone,
        )
        assertThat(deletePlan.requiresConfirmation).isTrue()
        assertThat(deletePlan.preview.title).isEqualTo("确认删除账目")

        val editPlan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选修改",
                expenses = emptyList(),
                actions = listOf(
                    ParsedAction.Update(7L, 2_000L, null, null, null),
                    ParsedAction.Update(8L, 500L, null, null, null),
                ),
            ),
            nowMillis = now,
            availableRecords = records,
            lastBatchIds = listOf(7L, 8L),
            currentText = "把它们金额改一下",
            targetDate = null,
            zone = zone,
        )
        assertThat(editPlan.requiresConfirmation).isFalse()
    }


    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun localDateTime(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
}
