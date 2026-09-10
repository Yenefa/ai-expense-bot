package com.expense.tracker.bench

import com.expense.tracker.bench.AgentBehaviorDataset.NEGATIVE_FP
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 评测器纯逻辑：gold 语义见 docs/expensebench-v2.md。 */
class AgentBehaviorEvaluatorTest {

    private fun case(
        id: String,
        bucket: String = NEGATIVE_FP,
        route: String? = null,
        tools: List<String>? = null,
        count: Int? = null,
        pending: Boolean = false,
        active: Int? = null,
        expect: List<BenchExpect> = emptyList(),
    ) = BehaviorCase(
        id = id,
        bucket = bucket,
        text = "text-$id",
        expect = expect,
        expected_route = route,
        expected_tools = tools,
        expected_mutation_count = count,
        expected_pending = pending,
        expected_active_count = active,
    )

    private fun item(
        amount: Long,
        category: String = "food",
        date: String? = null,
    ) = ObservedItem(
        kind = "insert",
        amountCents = amount,
        categoryId = category,
        occurredAtMillis = date?.let {
            java.time.LocalDate.parse(it).atTime(12, 0)
                .atZone(ExpenseBenchDataset.benchZone).toInstant().toEpochMilli()
        },
    )

    @Test
    fun `零期望却产生变更记为 false mutation`() {
        val cases = listOf(
            case("ok", count = 0),
            case("bad", count = 0),
            case("pending-bad", count = 0),
        )
        val observations = mapOf(
            "ok" to BehaviorObservation(),
            "bad" to BehaviorObservation(appliedCount = 1, appliedItems = listOf(item(1000))),
            "pending-bad" to BehaviorObservation(pending = true, pendingCount = 1),
        )

        val report = AgentBehaviorEvaluator.evaluate(cases, observations)

        assertThat(report.overall().falseMutationCases).isEqualTo(2)
        assertThat(report.falseMutationRate()).isWithin(0.0001).of(2.0 / 3)
        assertThat(report.overall().e2eOk).isEqualTo(1)
    }

    @Test
    fun `删除确认门按拟变更计数且不视为落库`() {
        val cases = listOf(
            case("del", route = "MUTATION", count = 1, pending = true, active = 1),
        )
        val observations = mapOf(
            "del" to BehaviorObservation(
                route = "MUTATION",
                pending = true,
                pendingCount = 1,
                activeCount = 1,
            ),
        )

        val report = AgentBehaviorEvaluator.evaluate(cases, observations)
        val overall = report.overall()

        assertThat(overall.countOk).isEqualTo(1)
        assertThat(overall.falseMutationCases).isEqualTo(0)
        assertThat(overall.e2eOk).isEqualTo(1)
        assertThat(overall.mutationPrecision()).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `路由与工具集合精确匹配`() {
        val cases = listOf(case("q", route = "QUERY", tools = listOf("query_expenses", "analyze_expenses"), count = 0))
        val observations = mapOf(
            "q" to BehaviorObservation(route = "QUERY", tools = listOf("analyze_expenses", "query_expenses")),
        )

        val overall = AgentBehaviorEvaluator.evaluate(cases, observations).overall()

        assertThat(overall.routerOk).isEqualTo(1)
        assertThat(overall.queryHit).isEqualTo(1)
        assertThat(overall.toolOk).isEqualTo(1)
    }

    @Test
    fun `日期绑定漏记按错计`() {
        val expect = listOf(BenchExpect(amount_cents = 1000, category = "food", date = "2026-09-05"))
        val cases = listOf(case("dated", route = "MUTATION", count = 1, expect = expect))
        val observations = mapOf(
            "dated" to BehaviorObservation(
                route = "MUTATION",
                appliedCount = 1,
                appliedItems = listOf(item(1000, "food", date = null)),
            ),
        )

        val overall = AgentBehaviorEvaluator.evaluate(cases, observations).overall()

        assertThat(overall.dateScored).isEqualTo(1)
        assertThat(overall.dateOk).isEqualTo(0)
        assertThat(overall.e2eOk).isEqualTo(0)
    }

    @Test
    fun `该 update 却 insert 由活跃账目数抓住`() {
        val expect = listOf(BenchExpect(amount_cents = 4000, category = "food", date = "2026-09-06"))
        val cases = listOf(case("update", route = "MUTATION", count = 1, active = 1, expect = expect))
        val observations = mapOf(
            "update" to BehaviorObservation(
                route = "MUTATION",
                appliedCount = 1,
                appliedItems = listOf(item(4000, "food", "2026-09-06")),
                activeCount = 2,
            ),
        )

        val overall = AgentBehaviorEvaluator.evaluate(cases, observations).overall()

        assertThat(overall.countOk).isEqualTo(1)
        assertThat(overall.e2eOk).isEqualTo(0)
    }

    @Test
    fun `Query Recall 只看 QUERY 期望`() {
        val cases = listOf(
            case("q1", route = "QUERY", tools = emptyList(), count = 0),
            case("q2", route = "QUERY", tools = emptyList(), count = 0),
            case("c1", route = "CHAT", tools = emptyList(), count = 0),
        )
        val observations = mapOf(
            "q1" to BehaviorObservation(route = "QUERY"),
            "q2" to BehaviorObservation(route = "CHAT"),
            "c1" to BehaviorObservation(route = "CHAT"),
        )

        val overall = AgentBehaviorEvaluator.evaluate(cases, observations).overall()

        assertThat(overall.queryExpected).isEqualTo(2)
        assertThat(overall.queryHit).isEqualTo(1)
        assertThat(overall.queryRecall()).isWithin(0.0001).of(0.5)
    }
}
