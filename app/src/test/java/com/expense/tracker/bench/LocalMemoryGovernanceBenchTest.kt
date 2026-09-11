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
import com.expense.tracker.data.repo.MutationApplyResult
import com.expense.tracker.llm.ChatLlmCoordinator
import com.expense.tracker.memory.FakeUserProfileStore
import com.expense.tracker.memory.MemoryGovernor
import com.expense.tracker.memory.MemoryType
import com.expense.tracker.ui.chat.LlmResult
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * MemoryGovernanceBench v1（纯本地，无网络）：
 * 四类提案 + 拒绝集，验证提案/类型校验/不误记支出/确认后才持久化。
 * 首要指标：Silent Memory Write Rate 必须为 0。
 */
class LocalMemoryGovernanceBenchTest {

    private val cases = MemoryBenchDataset.load()
    private val zone = ExpenseBenchDataset.benchZone
    private val now = ExpenseBenchDataset.benchNowMillis

    private fun prefs() = UserPrefsSnapshot(
        llmEnabled = true,
        baseUrl = "https://example.com/v1",
        apiKey = "bench",
        model = "bench",
        themeMode = ThemeMode.SYSTEM,
    )

    private fun benchAgent(
        dao: FakeExpenseDao,
        governor: MemoryGovernor,
        onLlm: () -> Unit,
        onApply: () -> Unit,
        onRoute: (AgentRoute) -> Unit,
    ): ExpenseAgent {
        val coordinator = ChatLlmCoordinator(
            expenseRepository = ExpenseRepository(dao),
            chatRepository = ChatRepository(FakeChatDao()),
            requestJson = { _, _, _, _, _ ->
                onLlm()
                """{"reply":"ok","expenses":[],"actions":[]}"""
            },
            applyPlan = {
                onApply()
                MutationApplyResult(emptyList(), emptyList(), 1L)
            },
            nowProvider = { now },
            zone = zone,
        )
        return ExpenseAgent(
            coordinator = coordinator,
            toolContext = AgentToolContext(ExpenseRepository(dao), { BudgetSnapshot() }, zone),
            escalateIntent = null,
            nowProvider = { now },
            zone = zone,
            onRouteResolved = onRoute,
            memoryGovernor = governor,
        )
    }

    @Test
    fun `数据集完整性`() {
        assertThat(cases).hasSize(36)
        assertThat(cases.map { it.id }.toSet()).hasSize(36)
        val byBucket = cases.groupBy { it.bucket }
        assertThat(byBucket.keys).containsExactlyElementsIn(MemoryBenchDataset.BUCKETS)
        MemoryBenchDataset.BUCKETS.forEach { bucket ->
            assertThat(byBucket.getValue(bucket)).hasSize(if (bucket == MemoryBenchDataset.REJECT) 12 else 6)
        }
        cases.forEach { case ->
            case.expectType?.let { assertThat(MemoryType.fromWire(it)).isNotNull() }
            case.expectCategory?.let { assertThat(Category.byId(it)).isNotNull() }
            case.expectAmountCents?.let { assertThat(it).isGreaterThan(0L) }
            case.expectRoute?.let { assertThat(it).isAnyOf("MUTATION", "QUERY", "CHAT") }
        }
    }

    @Test
    fun `治理基准与首要指标`() = runBlocking<Unit> {
        val observations = mutableMapOf<String, MemoryGovernanceEvaluator.MemoryObservation>()
        val problems = mutableListOf<String>()

        cases.forEach { case ->
            val store = FakeUserProfileStore()
            var seq = 0
            val governor = MemoryGovernor(
                store = store,
                nowProvider = { now },
                tokenProvider = { "tok-${case.id}-${++seq}" },
            )

            // 字段级提案（检测 + 类型校验）
            val proposal = governor.propose(case.text)

            // 生产 Agent 全链路（stub LLM，无网络）：验证提案轮不调模型、不写账目、不写记忆
            var llmCalls = 0
            var applyCalls = 0
            var route: String? = null
            val agent = benchAgent(
                FakeExpenseDao(),
                governor,
                onLlm = { llmCalls++ },
                onApply = { applyCalls++ },
                onRoute = { route = it.name },
            )
            val result = agent.submit(case.text, prefs())
            val agentProposed = result is LlmResult.MemoryProposalRequired
            if (agentProposed != (proposal != null)) {
                problems += "${case.id}: agent 提案=$agentProposed 检测提案=${proposal != null}"
            }
            if (applyCalls != 0) problems += "${case.id}: 产生账目写入 $applyCalls 次"
            if (case.expectType == null && proposal != null) {
                problems += "${case.id}: 不该提案却提案 ${proposal.fact.type.wire}"
            }
            if (case.expectType != null && proposal?.fact?.type?.wire != case.expectType) {
                problems += "${case.id}: 期望 ${case.expectType} 实际 ${proposal?.fact?.type?.wire}"
            }
            if (case.expectAmountCents != null && proposal?.fact?.amountCents != case.expectAmountCents) {
                problems += "${case.id}: 金额期望 ${case.expectAmountCents} 实际 ${proposal?.fact?.amountCents}"
            }
            if (case.expectMerchant != null && proposal?.fact?.merchant != case.expectMerchant) {
                problems += "${case.id}: 商户期望 ${case.expectMerchant} 实际 ${proposal?.fact?.merchant}"
            }
            if (case.expectCategory != null && proposal?.fact?.categoryId != case.expectCategory) {
                problems += "${case.id}: 分类期望 ${case.expectCategory} 实际 ${proposal?.fact?.categoryId}"
            }
            if (case.expectRoute != null && route != case.expectRoute) {
                problems += "${case.id}: 路由期望 ${case.expectRoute} 实际 $route"
            }

            // 确认前快照：这里是"无确认是否已写入"的观测点
            val silentPersisted = store.snapshot().isNotEmpty()

            var confirmedPersisted = false
            var confirmSingleUse = false
            if (proposal != null) {
                confirmedPersisted = governor.confirm(proposal.token)?.let { true } ?: false
                if (store.snapshot().size != 1) {
                    confirmedPersisted = false
                    problems += "${case.id}: 确认后画像条数 ${store.snapshot().size} != 1"
                }
                confirmSingleUse = governor.confirm(proposal.token) == null && store.snapshot().size == 1
            }

            observations[case.id] = MemoryGovernanceEvaluator.MemoryObservation(
                proposalType = proposal?.fact?.type?.wire,
                proposalAmountCents = proposal?.fact?.amountCents,
                proposalMerchant = proposal?.fact?.merchant,
                proposalCategoryId = proposal?.fact?.categoryId,
                llmCalls = llmCalls,
                route = route,
                silentPersisted = silentPersisted,
                confirmedPersisted = confirmedPersisted,
                confirmSingleUse = confirmSingleUse,
            )
        }

        val report = MemoryGovernanceEvaluator.evaluate(cases, observations)
        val markdown = report.toMarkdown(
            source = "本地确定性管线 + 生产 Agent（stub LLM，零网络）",
            metadata = listOf(
                "dataset_sha256 = ${MemoryBenchDataset.datasetSha256()}",
                "ran_at = ${OffsetDateTime.now()}",
                "记忆写入口：只有 MemoryGovernor.confirm（人类确认）",
            ),
        )
        val outFile = File(repoRoot(), "docs/memory-governance-local-report.md")
        outFile.parentFile?.mkdirs()
        outFile.writeText(markdown)
        println(markdown)

        val all = report.overall()
        assertThat(problems).isEmpty()
        assertThat(all.silentWrites).isEqualTo(0)
        assertThat(all.falseProposals).isEqualTo(0)
        assertThat(all.proposalOk).isEqualTo(all.proposalScored)
        assertThat(all.routeOk).isEqualTo(all.routeScored)
        assertThat(all.confirmScored).isEqualTo(24)
        assertThat(all.confirmOk).isEqualTo(all.confirmScored)
        assertThat(all.proposalTurnsWithLlm).isEqualTo(0)
        assertThat(report.silentMemoryWriteRate()).isEqualTo(0.0)
    }

    private fun repoRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(System.getProperty("user.dir"))
}
