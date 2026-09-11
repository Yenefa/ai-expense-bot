package com.expense.tracker.bench

import com.expense.tracker.agent.AgentRoute
import com.expense.tracker.agent.AgentToolContext
import com.expense.tracker.agent.ExpenseAgent
import com.expense.tracker.agent.FakeChatDao
import com.expense.tracker.agent.FakeExpenseDao
import com.expense.tracker.data.budget.BudgetSnapshot
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.ThemeMode
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.expense.tracker.data.repo.LlmMutationApplier
import com.expense.tracker.data.repo.TransactionRunner
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.memory.FakeUserProfileStore
import com.expense.tracker.memory.MemoryFact
import com.expense.tracker.memory.MemoryGovernor
import com.expense.tracker.memory.MemoryType
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * MemoryConsumptionBench v1（纯本地，零网络）：
 * 种入已确认记忆后跑真实 Agent + stub LLM，检验授权读取、正确应用、删除后不复用。
 */
class LocalMemoryConsumptionBenchTest {

    private val cases = MemoryConsumptionDataset.load()
    private val zone = ExpenseBenchDataset.benchZone
    private val now = ExpenseBenchDataset.benchNowMillis

    private class InlineRunner : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.com/v1",
        apiKey = "bench",
        model = "bench",
        themeMode = ThemeMode.SYSTEM,
    )

    private fun scriptJson(script: ConsumptionScript?): String =
        if (script == null) {
            """{"reply":"ok","expenses":[],"actions":[]}"""
        } else {
            """{"reply":"已记录","expenses":[{"amount":${script.amount},"category":"${script.category}",""" +
                """"note":"${script.note}","occurred_at":null}],"actions":[]}"""
        }

    @Test
    fun `数据集完整性`() {
        assertThat(cases).hasSize(38)
        assertThat(cases.map { it.id }.toSet()).hasSize(38)
        val byBucket = cases.groupBy { it.bucket }
        assertThat(byBucket.keys).containsExactlyElementsIn(MemoryConsumptionDataset.BUCKETS)
        assertThat(byBucket.getValue(MemoryConsumptionDataset.ALIAS_APPLICATION)).hasSize(8)
        assertThat(byBucket.getValue(MemoryConsumptionDataset.ANALYSIS_READS)).hasSize(8)
        assertThat(byBucket.getValue(MemoryConsumptionDataset.PREFERENCE_ANALYSIS)).hasSize(4)
        assertThat(byBucket.getValue(MemoryConsumptionDataset.UNAUTHORIZED)).hasSize(8)
        assertThat(byBucket.getValue(MemoryConsumptionDataset.DELETED_REUSE)).hasSize(6)
        assertThat(byBucket.getValue(MemoryConsumptionDataset.NO_MEMORY)).hasSize(4)
        val types = MemoryType.entries.map { it.wire }.toSet()
        cases.forEach { case ->
            case.memory.forEach { seed ->
                assertThat(types).contains(seed.type)
                seed.categoryId?.let { assertThat(Category.byId(it)).isNotNull() }
            }
            case.deleteTypes.forEach { assertThat(types).contains(it) }
            case.expectRoute?.let { assertThat(it).isAnyOf("MUTATION", "QUERY", "CHAT") }
            case.expectFinalCategory?.let { assertThat(Category.byId(it)).isNotNull() }
        }
    }

    @Test
    fun `记忆消费基准`() = runBlocking<Unit> {
        val observations = mutableMapOf<String, MemoryConsumptionEvaluator.Observation>()
        val problems = mutableListOf<String>()

        cases.forEach { case ->
            val store = FakeUserProfileStore()
            val seeded = case.memory.mapIndexed { index, seed ->
                MemoryFact(
                    type = MemoryType.fromWire(seed.type)!!,
                    amountCents = seed.amountCents,
                    merchant = seed.merchant,
                    categoryId = seed.categoryId,
                    rawText = seed.rawText,
                    createdAt = now,
                    id = "seed-$index",
                )
            }
            store.save(seeded)
            val governor = MemoryGovernor(store, nowProvider = { now }, tokenProvider = { "tok" })
            case.deleteTypes.forEach { wire ->
                seeded.firstOrNull { it.type.wire == wire }?.let { governor.delete(it.id) }
            }

            val prompts = mutableListOf<String>()
            var route: String? = null
            val dao = FakeExpenseDao()
            val chatDao = FakeChatDao()
            val applier = LlmMutationApplier(ExpenseRepository(dao), ChatRepository(chatDao), InlineRunner())
            val response = scriptJson(case.script)
            val coordinator = ChatLlmCoordinator(
                expenseRepository = ExpenseRepository(dao),
                chatRepository = ChatRepository(chatDao),
                requestJson = { _, _, systemPrompt, _, _ ->
                    prompts += systemPrompt
                    response
                },
                applyPlan = applier::apply,
                nowProvider = { now },
                zone = zone,
            )
            val agent = ExpenseAgent(
                coordinator = coordinator,
                toolContext = AgentToolContext(ExpenseRepository(dao), { BudgetSnapshot() }, zone),
                escalateIntent = null,
                nowProvider = { now },
                zone = zone,
                onRouteResolved = { route = it.name },
                memoryGovernor = governor,
            )
            agent.submit(case.text, prefs())
            val finalCategory = dao.getAllActiveOnce().lastOrNull()?.categoryId

            observations[case.id] = MemoryConsumptionEvaluator.Observation(
                prompts = prompts,
                route = route,
                finalCategory = finalCategory,
            )

            if (case.expectRoute != null && route != case.expectRoute) {
                problems += "${case.id}: 路由期望 ${case.expectRoute} 实际 $route"
            }
            if (case.expectFinalCategory != null && finalCategory != case.expectFinalCategory) {
                problems += "${case.id}: 最终分类期望 ${case.expectFinalCategory} 实际 $finalCategory"
            }
        }

        val report = MemoryConsumptionEvaluator.evaluate(cases, observations)
        val markdown = report.toMarkdown(
            source = "本地确定性管线 + 生产 Agent（stub LLM，零网络）",
            metadata = listOf(
                "dataset_sha256 = ${MemoryConsumptionDataset.datasetSha256()}",
                "ran_at = ${OffsetDateTime.now()}",
                "授权范围：MUTATION→商户别名；QUERY→月收入/储蓄目标/常用分类；CHAT→无",
            ),
        )
        val outFile = File(repoRoot(), "docs/memory-consumption-local-report.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(markdown)
        println(markdown)

        val all = report.overall()
        assertThat(problems).isEmpty()
        assertThat(all.unauthorizedReads).isEqualTo(0)
        assertThat(all.deletedReuse).isEqualTo(0)
        assertThat(all.applicationScored).isGreaterThan(0)
        assertThat(all.applicationOk).isEqualTo(all.applicationScored)
        assertThat(all.routeOk).isEqualTo(all.routeScored)
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
