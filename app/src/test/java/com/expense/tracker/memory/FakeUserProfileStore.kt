package com.expense.tracker.memory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** JVM 测试用内存画像存储；用于证明"确认前零写入"。 */
class FakeUserProfileStore : UserProfileStore {
    private val state = MutableStateFlow<List<MemoryFact>>(emptyList())

    override val facts: Flow<List<MemoryFact>> = state.asStateFlow()

    override suspend fun append(fact: MemoryFact) {
        state.value = state.value + fact
    }

    fun snapshot(): List<MemoryFact> = state.value
}
