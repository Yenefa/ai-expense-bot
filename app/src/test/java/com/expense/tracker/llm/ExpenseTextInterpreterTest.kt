package com.expense.tracker.llm

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class ExpenseTextInterpreterTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 3, 0, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test fun exactReportedInputKeepsEveryAmountOnItsNearestExplicitDate() {
        val interpreted = ExpenseTextInterpreter.interpret(REPORTED_INPUT, now, zone)

        assertThat(interpreted.normalizedText).contains("下午 16点，6元")
        assertThat(interpreted.normalizedText).contains("下午 3点，3元")
        assertThat(interpreted.normalizedText).contains("晚上 7点，7元")
        assertThat(interpreted.expenseHints.map { it.amountCents }).containsExactly(
            690L, 1_800L, 1_490L, 600L, 300L, 700L, 1_900L, 8_390L, 760L,
        ).inOrder()
        assertThat(interpreted.expenseHints.map { it.date }).containsExactly(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 2),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 1),
        ).inOrder()
        assertThat(interpreted.expenseHints.map { it.time }).containsExactly(
            LocalTime.of(22, 1),
            LocalTime.of(18, 58),
            null,
            LocalTime.of(16, 0),
            LocalTime.of(15, 0),
            LocalTime.of(19, 0),
            LocalTime.of(13, 0),
            LocalTime.of(11, 30),
            LocalTime.of(11, 0),
        ).inOrder()
        assertThat(interpreted.hasMultipleDates).isTrue()
        assertThat(interpreted.hasCompleteExpenseHints).isTrue()
    }

    @Test fun singleDateBatchAlsoProducesCompleteDeterministicHints() {
        val interpreted = ExpenseTextInterpreter.interpret(
            "8月1日早上8点早餐6元，中午12点午饭13元",
            now,
            zone,
        )

        assertThat(interpreted.expenseHints.map { it.amountCents })
            .containsExactly(600L, 1_300L).inOrder()
        assertThat(interpreted.expenseHints.map { it.date })
            .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)).inOrder()
        assertThat(interpreted.expenseHints.map { it.time })
            .containsExactly(LocalTime.of(8, 0), LocalTime.of(12, 0)).inOrder()
        assertThat(interpreted.hasMultipleDates).isFalse()
        assertThat(interpreted.hasCompleteExpenseHints).isTrue()
    }

    @Test fun ordinaryAfternoonDecimalAmountIsNotRewrittenWithoutTheVoiceShorthandShape() {
        val interpreted = ExpenseTextInterpreter.interpret("8月1号下午买水16.6元", now, zone)

        assertThat(interpreted.normalizedText).isEqualTo("8月1号下午买水16.6元")
        assertThat(interpreted.expenseHints.single().amountCents).isEqualTo(1_660L)
    }

    @Test fun colloquialYuanJiaoAmountMatchesModelResponseThroughPlanner() {
        val interpreted = ExpenseTextInterpreter.interpret("昨天打车花了23块5", now, zone)

        // 「23块5」= 23.5 元，端侧提示必须是 2350，模型返回 2350 时不能被金额一致性 guard 拒绝。
        assertThat(interpreted.expenseHints.map { it.amountCents }).containsExactly(2_350L)
        assertThat(interpreted.hasCompleteExpenseHints).isTrue()

        val plan = LlmMutationPlanner.create(
            result = LlmParseResult(
                reply = "候选1笔",
                expenses = listOf(ParsedExpense(2_350L, "transport", "打车", null)),
            ),
            nowMillis = now,
            availableRecords = emptyList(),
            lastBatchIds = emptyList(),
            currentText = interpreted.normalizedText,
            targetDate = null,
            sourceExpenseHints = interpreted.expenseHints,
            zone = zone,
        )

        assertThat(plan.preview.count).isEqualTo(1)
        assertThat(plan.preview.totalCents).isEqualTo(2_350L)
        assertThat(plan.result.expenses.map { it.amountCents }).containsExactly(2_350L)
    }

    @Test fun halfYuanColloquialAmountProduces350Cents() {
        // hint 依赖日期锚点，故用「昨天」验证 3块半 = 3.5 元。
        val interpreted = ExpenseTextInterpreter.interpret("昨天奶茶3块半", now, zone)

        assertThat(interpreted.expenseHints.map { it.amountCents }).containsExactly(350L)
    }

    @Test fun decimalAmountAfterPeriodWordStaysAnAmountWhenFollowedByItem() {
        val interpreted = ExpenseTextInterpreter.interpret("今天下午3.5元咖啡", now, zone)

        // 「3.5元」后接商品（咖啡），不能再改写成「下午 3点，5元」。
        assertThat(interpreted.normalizedText).isEqualTo("今天下午3.5元咖啡")
        assertThat(interpreted.expenseHints.map { it.amountCents }).containsExactly(350L)
    }

    @Test fun invalidLaterClockDoesNotEraseAnEarlierValidClockInTheSameExpense() {
        val interpreted = ExpenseTextInterpreter.interpret(
            "8月1日上午8点，口误晚上25点，早餐6元",
            now,
            zone,
        )

        assertThat(interpreted.expenseHints.single().time).isEqualTo(LocalTime.of(8, 0))
    }

    companion object {
        const val REPORTED_INPUT =
            "8 月 1 号晚上 10:01，一杯红茶八喜桶 6.9 元。18:58，muji 编织购物袋 18 元。" +
                "餐饮，盒马麻薯 14.9 元。下午 16.6 元，两瓶矿泉水。地铁漫展，下午 3.3 元。" +
                "回家地铁，晚上 7.7 元。炸鸡，8 月 2 号中午 1 点。19 元。" +
                "柳真真，8 月 1 号上午 11:30，83.9 元。8 月 1 号上午 11 点坐地铁到正弘城，7.6 元。"
    }
}
