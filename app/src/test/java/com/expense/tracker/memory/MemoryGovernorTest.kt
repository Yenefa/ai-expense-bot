package com.expense.tracker.memory

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MemoryGovernorTest {

    private var now = 1_786_000_000_000L
    private var seq = 0

    private fun governor(store: FakeUserProfileStore) = MemoryGovernor(
        store = store,
        nowProvider = { now },
        tokenProvider = { "token-${++seq}" },
    )

    @Test
    fun `提案阶段零写入`() = runBlocking {
        val store = FakeUserProfileStore()
        val proposal = governor(store).propose("我月收入8000")

        assertThat(proposal).isNotNull()
        assertThat(store.snapshot()).isEmpty()
        assertThat(store.facts.first()).isEmpty()
    }

    @Test
    fun `确认后才持久化且 token 一次性`() = runBlocking {
        val store = FakeUserProfileStore()
        val gov = governor(store)
        val proposal = gov.propose("我月收入8000")!!

        val fact = gov.confirm(proposal.token)

        assertThat(fact?.amountCents).isEqualTo(800_000L)
        assertThat(store.snapshot()).hasSize(1)
        assertThat(gov.confirm(proposal.token)).isNull()
        assertThat(store.snapshot()).hasSize(1)
    }

    @Test
    fun `取消后零写入且 token 失效`() = runBlocking {
        val store = FakeUserProfileStore()
        val gov = governor(store)
        val proposal = gov.propose("以后瑞幸都算饮品")!!

        assertThat(gov.cancel(proposal.token)).isTrue()
        assertThat(gov.confirm(proposal.token)).isNull()
        assertThat(store.snapshot()).isEmpty()
    }

    @Test
    fun `过期 token 无法确认`() = runBlocking {
        val store = FakeUserProfileStore()
        val gov = governor(store)
        val proposal = gov.propose("储蓄目标是5万")!!

        now += MemoryGovernor.PENDING_TTL_MS + 1

        assertThat(gov.confirm(proposal.token)).isNull()
        assertThat(store.snapshot()).isEmpty()
    }

    @Test
    fun `非法分类不生成提案`() {
        val store = FakeUserProfileStore()
        assertThat(governor(store).propose("把瑞幸记成20")).isNull()
    }
}
