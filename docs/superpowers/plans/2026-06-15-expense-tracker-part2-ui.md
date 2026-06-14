# 智能记账 App 实现计划 — Part 2: UI 与对话

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **前置：** 必须先完成 [Part 1: 基础架构](./2026-06-15-expense-tracker-part1-foundation.md)

**Goal:** 实现 ChatGPT 风格主页面、Interactive Dock、模板/LLM 双模式输入

**Architecture:** Compose 单页面 + ViewModel + Flow，Dock 用 `Animatable` + `spring()` 实现弹簧

**Tech Stack:** Compose, Animatable, kotlinx.coroutines

---

## 任务总览（Part 2: 8 个任务）

| # | 任务 |
|---|---|
| 8 | ChatRepository / ExpenseRepository |
| 9 | ChatViewModel |
| 10 | TopBar（顶部白圆按钮） |
| 11 | MessageList + MessageBubble |
| 12 | LiquidGlassButton（🧠 ON/OFF） |
| 13 | InputBar |
| 14 | TemplateCard（关闭 LLM 时） |
| 15 | InteractiveDock（弹簧动画） + 主屏装配 |

---

### Task 8: 仓库层（ExpenseRepository + ChatRepository）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/repo/ExpenseRepository.kt`
- Create: `app/src/main/java/com/expense/tracker/data/repo/ChatRepository.kt`
- Create: `app/src/test/java/com/expense/tracker/data/repo/RepositoryTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/AppContainer.kt`

- [ ] **Step 1: 写测试 `RepositoryTest.kt`（用 fake Dao）**

```kotlin
package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeExpenseDao : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(expense: ExpenseEntity): Long {
        seq++
        state.value = state.value + expense.copy(id = seq)
        return seq
    }
    override fun observeAll(): Flow<List<ExpenseEntity>> = state
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> =
        kotlinx.coroutines.flow.flow {
            emit(state.value.filter { it.occurredAt in from until to })
        }
    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

private class FakeChatDao : ChatMessageDao {
    private val state = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++
        state.value = state.value + msg.copy(id = seq)
        return seq
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = state
    override suspend fun clearAll() { state.value = emptyList() }
}

class RepositoryTest {
    @Test fun expenseRepoAddPersists() = runBlocking {
        val repo = ExpenseRepository(FakeExpenseDao())
        val id = repo.add(amount = 35.0, categoryId = "food", note = "午饭", occurredAt = 1L)
        assertThat(id).isGreaterThan(0L)
        val all = repo.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].amount).isEqualTo(35.0)
    }

    @Test fun chatRepoAppendsBoth() = runBlocking {
        val repo = ChatRepository(FakeChatDao())
        repo.appendUser("hello", at = 100L)
        repo.appendAssistant("hi", at = 101L, relatedExpenseId = 7L)
        val all = repo.observeAll().first()
        assertThat(all).hasSize(2)
        assertThat(all[1].relatedExpenseId).isEqualTo(7L)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.data.repo.RepositoryTest"`
Expected: FAIL — `Unresolved reference: ExpenseRepository`

- [ ] **Step 3: 写 `ExpenseRepository.kt`**

```kotlin
package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {

    fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()

    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<ExpenseEntity>> =
        dao.observeInRange(fromMillis, toMillis)

    suspend fun add(amount: Double, categoryId: String, note: String, occurredAt: Long): Long {
        val now = System.currentTimeMillis()
        return dao.insert(ExpenseEntity(
            amount = amount,
            categoryId = categoryId,
            note = note,
            occurredAt = occurredAt,
            createdAt = now,
        ))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
```

- [ ] **Step 4: 写 `ChatRepository.kt`**

```kotlin
package com.expense.tracker.data.repo

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {

    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()

    suspend fun appendUser(text: String, at: Long = System.currentTimeMillis()): Long =
        dao.insert(ChatMessageEntity(role = "user", content = text, createdAt = at))

    suspend fun appendAssistant(
        text: String,
        at: Long = System.currentTimeMillis(),
        relatedExpenseId: Long? = null,
    ): Long = dao.insert(ChatMessageEntity(
        role = "assistant",
        content = text,
        createdAt = at,
        relatedExpenseId = relatedExpenseId,
    ))

    suspend fun clear() = dao.clearAll()
}
```

- [ ] **Step 5: 在 `AppContainer.kt` 增加仓库引用**

把整个文件替换为：

```kotlin
package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository

class AppContainer(context: Context) {
    private val appCtx = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appCtx) }
    val userPrefs: UserPrefs by lazy { UserPrefs.fromContext(appCtx) }

    val expenseRepo: ExpenseRepository by lazy { ExpenseRepository(db.expenseDao()) }
    val chatRepo: ChatRepository by lazy { ChatRepository(db.chatDao()) }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.data.repo.RepositoryTest"`
Expected: 2 PASS

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/expense/tracker/data/repo/ \
        app/src/main/java/com/expense/tracker/AppContainer.kt \
        app/src/test/java/com/expense/tracker/data/repo/
git commit -m "feat(repo): expense + chat repositories with fake-dao tests"
```

---

### Task 9: ChatViewModel（无 LLM 流程，只走模板模式 / 占位 LLM 调用）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/chat/ChatUiState.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/chat/ChatViewModel.kt`
- Create: `app/src/test/java/com/expense/tracker/ui/chat/ChatViewModelTest.kt`

- [ ] **Step 1: 写 `ChatUiState.kt`**

```kotlin
package com.expense.tracker.ui.chat

import com.expense.tracker.data.db.ChatMessageEntity

data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val llmEnabled: Boolean = false,
    val selectedCategoryId: String = "food",
    val sending: Boolean = false,
)
```

- [ ] **Step 2: 写测试 `ChatViewModelTest.kt`（先只测模板模式）**

```kotlin
package com.expense.tracker.ui.chat

import com.expense.tracker.data.db.ChatMessageDao
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.data.db.ExpenseDao
import com.expense.tracker.data.db.ExpenseEntity
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    private fun makeVm(initialLlm: Boolean = false): Triple<ChatViewModel, FakeExpenseDao, FakeChatDao> {
        val ed = FakeExpenseDao()
        val cd = FakeChatDao()
        val prefs = FakeUserPrefs(initialLlm)
        val vm = ChatViewModel(
            expenseRepo = ExpenseRepository(ed),
            chatRepo = ChatRepository(cd),
            userPrefs = prefs,
            llmHandler = { _, _ -> error("unused in template mode") },
        )
        return Triple(vm, ed, cd)
    }

    @Test fun submitTemplateAddsExpenseAndTwoMessages() = runTest {
        val (vm, ed, cd) = makeVm()
        vm.selectCategory("food")
        vm.submitTemplate(amount = 35.0)
        assertThat(ed.snapshot()).hasSize(1)
        assertThat(ed.snapshot()[0].amount).isEqualTo(35.0)
        val msgs = cd.flow.first()
        assertThat(msgs.map { it.role }).containsExactly("user", "assistant").inOrder()
        assertThat(msgs[1].content).contains("餐饮")
    }

    @Test fun submitTemplateRejectsNonPositive() = runTest {
        val (vm, ed, _) = makeVm()
        vm.submitTemplate(amount = 0.0)
        vm.submitTemplate(amount = -1.0)
        assertThat(ed.snapshot()).isEmpty()
    }

    @Test fun toggleLlmFlipsState() = runTest {
        val (vm, _, _) = makeVm(initialLlm = false)
        assertThat(vm.uiState.value.llmEnabled).isFalse()
        vm.toggleLlm()
        assertThat(vm.uiState.value.llmEnabled).isTrue()
    }
}

private class FakeExpenseDao : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    fun snapshot(): List<ExpenseEntity> = state.value
    override suspend fun insert(expense: ExpenseEntity): Long {
        val id = state.value.size + 1L
        state.value = state.value + expense.copy(id = id)
        return id
    }
    override fun observeAll(): Flow<List<ExpenseEntity>> = state
    override fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>> = flowOf(emptyList())
    override suspend fun deleteById(id: Long) {}
}
private class FakeChatDao : ChatMessageDao {
    val flow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var seq = 0L
    override suspend fun insert(msg: ChatMessageEntity): Long {
        seq++
        flow.value = flow.value + msg.copy(id = seq)
        return seq
    }
    override fun observeAll(): Flow<List<ChatMessageEntity>> = flow
    override suspend fun clearAll() { flow.value = emptyList() }
}
private class FakeUserPrefs(initial: Boolean) : UserPrefs(FakeStore(initial)) {
    private class FakeStore(initial: Boolean) : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        private val state = MutableStateFlow(
            androidx.datastore.preferences.core.mutablePreferencesOf().apply {
                set(LLM_ENABLED, initial)
            } as androidx.datastore.preferences.core.Preferences
        )
        override val data: Flow<androidx.datastore.preferences.core.Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences,
        ): androidx.datastore.preferences.core.Preferences {
            val n = transform(state.value); state.value = n; return n
        }
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.ui.chat.ChatViewModelTest"`
Expected: FAIL — `Unresolved reference: ChatViewModel`

- [ ] **Step 4: 写 `ChatViewModel.kt`**

```kotlin
package com.expense.tracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.model.Category
import com.expense.tracker.data.prefs.UserPrefs
import com.expense.tracker.data.repo.ChatRepository
import com.expense.tracker.data.repo.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** LLM 调用接口 — 输入用户文本 + 当前 prefs，返回助手要展示的文本。Part 3 任务再实现真实版本。 */
typealias LlmHandler = suspend (text: String, prefs: com.expense.tracker.data.prefs.UserPrefsSnapshot) -> LlmResult

sealed interface LlmResult {
    data class Ok(val replyText: String, val expenseId: Long?) : LlmResult
    data class Error(val message: String) : LlmResult
}

class ChatViewModel(
    private val expenseRepo: ExpenseRepository,
    private val chatRepo: ChatRepository,
    private val userPrefs: UserPrefs,
    private val llmHandler: LlmHandler,
) : ViewModel() {

    private val internal = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = internal.asStateFlow()

    init {
        viewModelScope.launch {
            combine(chatRepo.observeAll(), userPrefs.snapshot) { msgs, p ->
                msgs to p.llmEnabled
            }.collect { (msgs, enabled) ->
                internal.update { it.copy(messages = msgs, llmEnabled = enabled) }
            }
        }
    }

    fun selectCategory(id: String) {
        if (Category.byId(id) == null) return
        internal.update { it.copy(selectedCategoryId = id) }
    }

    fun toggleLlm() {
        viewModelScope.launch { userPrefs.setLlmEnabled(!internal.value.llmEnabled) }
    }

    /** 关闭 LLM 时的快速记账。 */
    fun submitTemplate(amount: Double) {
        if (amount <= 0.0) return
        val cat = Category.byIdOrOther(internal.value.selectedCategoryId)
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            chatRepo.appendUser("[模板] ${cat.emoji} ${cat.displayName} ¥${"%.2f".format(amount)}")
            val expenseId = expenseRepo.add(
                amount = amount,
                categoryId = cat.id,
                note = "",
                occurredAt = System.currentTimeMillis(),
            )
            chatRepo.appendAssistant(
                text = "✅ 已记录 · ${cat.emoji} ${cat.displayName} ¥${"%.2f".format(amount)}",
                relatedExpenseId = expenseId,
            )
            internal.update { it.copy(sending = false) }
        }
    }

    /** 开启 LLM 时的自由文本输入。 */
    fun submitFreeText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            chatRepo.appendUser(trimmed)
            val prefs = userPrefs.snapshot.let { f ->
                kotlinx.coroutines.flow.first(f)
            }
            val result = runCatching { llmHandler(trimmed, prefs) }
                .getOrElse { LlmResult.Error("调用失败：${it.message ?: "未知错误"}") }
            when (result) {
                is LlmResult.Ok -> chatRepo.appendAssistant(result.replyText, relatedExpenseId = result.expenseId)
                is LlmResult.Error -> chatRepo.appendAssistant("⚠️ ${result.message}")
            }
            internal.update { it.copy(sending = false) }
        }
    }
}

private suspend fun <T> kotlinx.coroutines.flow.first(flow: kotlinx.coroutines.flow.Flow<T>): T =
    kotlinx.coroutines.flow.first(flow)
```

> 修正：用标准库 `first()`：

替换文件末尾两行：

```kotlin
// 删除最后那个手写 first 工具
```

并把 `submitFreeText` 中 `userPrefs.snapshot.let { f -> kotlinx.coroutines.flow.first(f) }` 替换为：

```kotlin
            val prefs = kotlinx.coroutines.flow.first(userPrefs.snapshot)
```

最终 `ChatViewModel.kt` 的 `submitFreeText` 应是：

```kotlin
    fun submitFreeText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            internal.update { it.copy(sending = true) }
            chatRepo.appendUser(trimmed)
            val prefs = kotlinx.coroutines.flow.first(userPrefs.snapshot)
            val result = runCatching { llmHandler(trimmed, prefs) }
                .getOrElse { LlmResult.Error("调用失败：${it.message ?: "未知错误"}") }
            when (result) {
                is LlmResult.Ok -> chatRepo.appendAssistant(result.replyText, relatedExpenseId = result.expenseId)
                is LlmResult.Error -> chatRepo.appendAssistant("⚠️ ${result.message}")
            }
            internal.update { it.copy(sending = false) }
        }
    }
```

并在文件顶部 import：

```kotlin
import kotlinx.coroutines.flow.first
```

然后把 `submitFreeText` 中那行简化成 `val prefs = userPrefs.snapshot.first()`，移除 `kotlinx.coroutines.flow.first(...)` 写法。

- [ ] **Step 5: 跑测试确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.ui.chat.ChatViewModelTest"`
Expected: 3 PASS

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/chat/ChatUiState.kt \
        app/src/main/java/com/expense/tracker/ui/chat/ChatViewModel.kt \
        app/src/test/java/com/expense/tracker/ui/chat/ChatViewModelTest.kt
git commit -m "feat(chat): viewmodel with template + free-text submit, llm handler typealias"
```

---

### Task 10: TopBar — 顶部白圆按钮

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/chat/TopBar.kt`

- [ ] **Step 1: 写 `TopBar.kt`**

```kotlin
package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.iconBtnShadow

@Composable
fun TopBar(
    onMenuClick: () -> Unit,
    onEditClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CircleIconBtn(onClick = onMenuClick) { Icon(Icons.Outlined.Menu, contentDescription = "菜单") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleIconBtn(onClick = onEditClick) { Icon(Icons.Outlined.Edit, contentDescription = "编辑") }
            CircleIconBtn(onClick = onMoreClick) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多") }
        }
    }
}

@Composable
private fun CircleIconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .iconBtnShadow()
            .clip(CircleShape)
            .background(AppColors.Bg)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
```

- [ ] **Step 2: 编译检查**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/chat/TopBar.kt
git commit -m "feat(ui): TopBar with three white-circle shadowed buttons"
```

---

### Task 11: 消息列表（无气泡 AI + 浅灰胶囊用户）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/chat/MessageList.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/chat/MessageBubble.kt`

- [ ] **Step 1: 写 `MessageBubble.kt`**

```kotlin
package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity
import com.expense.tracker.ui.theme.AppColors

@Composable
fun MessageBubble(message: ChatMessageEntity, modifier: Modifier = Modifier) {
    if (message.role == "user") {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.ChipFill)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = message.content,
                    color = AppColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                )
            }
        }
    } else {
        // 助手：无气泡，纯文字
        Text(
            text = message.content,
            color = AppColors.TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 2: 写 `MessageList.kt`**

```kotlin
package com.expense.tracker.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.ChatMessageEntity

@Composable
fun MessageList(messages: List<ChatMessageEntity>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(items = messages, key = { it.id }) { msg ->
            MessageBubble(msg)
        }
    }
}
```

- [ ] **Step 3: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/chat/MessageList.kt \
        app/src/main/java/com/expense/tracker/ui/chat/MessageBubble.kt
git commit -m "feat(ui): message list — gray-pill user, plain-text assistant"
```

---

### Task 12: LiquidGlassButton（🧠 ON/OFF）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/liquidglass/LiquidGlassButton.kt`

- [ ] **Step 1: 写 `LiquidGlassButton.kt`**

```kotlin
package com.expense.tracker.ui.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🧠 液态玻璃按钮：ON 时高透白光；OFF 时几乎隐形。
 * 状态由父级控制（不在内部存），点击调 [onToggle]。
 */
@Composable
fun LiquidGlassButton(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (on) 0.06f else 0.02f,
        animationSpec = tween(300),
        label = "bg",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (on) 0.08f else 0.04f,
        animationSpec = tween(300),
        label = "border",
    )
    val shadowDp by animateFloatAsState(
        targetValue = if (on) 6f else 0f,
        animationSpec = tween(300),
        label = "shadow",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (on) 1f else 0.22f,
        animationSpec = tween(300),
        label = "icon",
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .shadow(shadowDp.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f), clip = false)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = bgAlpha))
            .pointerInput(on) { detectTapGestures(onTap = { onToggle() }) },
        contentAlignment = Alignment.Center,
    ) {
        // 描边（极淡）
        Canvas(Modifier.size(34.dp)) {
            drawCircle(
                color = Color.Black.copy(alpha = borderAlpha),
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
            )
        }
        // 顶部高光椭圆
        if (on) {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 4.dp)
                    .align(Alignment.TopStart)
                    .rotate(-25f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f))
                    .alpha(0.85f),
            )
        }
        Text(
            text = "🧠",
            style = TextStyle(fontSize = 15.sp),
            modifier = Modifier.alpha(iconAlpha),
        )
    }
}
```

- [ ] **Step 2: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/liquidglass/LiquidGlassButton.kt
git commit -m "feat(ui): liquid-glass toggle button (alpha+shadow only, no metaphor switch)"
```

---

### Task 13: InputBar — 输入栏

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/chat/InputBar.kt`

- [ ] **Step 1: 写 `InputBar.kt`**

```kotlin
package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.BasicTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.liquidglass.LiquidGlassButton
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.inputShadow

@Composable
fun InputBar(
    llmEnabled: Boolean,
    onToggleLlm: () -> Unit,
    onSend: (String) -> Unit,
    onPlusClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .inputShadow()
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.Bg)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier
            .size(22.dp)
            .pointerInput(onPlusClick) { detectTapGestures(onTap = { onPlusClick() }) }) {
            Icon(Icons.Outlined.Add, contentDescription = "新增", tint = AppColors.TextPrimary)
        }
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.TextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(text = placeholder, color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
        LiquidGlassButton(on = llmEnabled, onToggle = onToggleLlm)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AppColors.Accent)
                .pointerInput(text) {
                    detectTapGestures(onTap = {
                        if (text.isNotBlank()) {
                            onSend(text); text = ""
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "发送", tint = Color.White)
        }
    }
}

private fun Modifier.weight(f: Float): Modifier = this.then(
    androidx.compose.foundation.layout.weight(f, fill = true)
)
```

> **修正：** 不要自己写 `Modifier.weight`，那是 RowScope 的扩展。把 `Box(Modifier.weight(1f))` 改为：

```kotlin
        androidx.compose.foundation.layout.Row(modifier = Modifier.weight(1f)) {
            // BasicTextField + placeholder
        }
```

完整修正版的 InputBar 中间块（替换 `Box(Modifier.weight(1f)) { ... }` 整段）：

```kotlin
        androidx.compose.foundation.layout.Box(modifier = Modifier
            .androidx.compose.foundation.layout.weight(1f, fill = true)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.TextPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(text = placeholder, color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
```

并删除文件末尾的 `private fun Modifier.weight(...)` 函数（用 RowScope 内置的）。

- [ ] **Step 2: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/chat/InputBar.kt
git commit -m "feat(ui): input bar with plus, text field, liquid-glass toggle, blue send"
```

---

### Task 14: TemplateCard — 关闭 LLM 时的快捷模板

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/template/CategoryChip.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/template/AmountInput.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/template/TemplateCard.kt`

- [ ] **Step 1: 写 `CategoryChip.kt`**

```kotlin
package com.expense.tracker.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.softShadow

@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AppColors.TextPrimary else AppColors.Bg
    val fg = if (selected) Color.White else AppColors.TextPrimary
    val mod = if (selected) modifier else modifier.softShadow(elevation = 3.dp, cornerRadius = 18.dp, spotAlpha = 0.10f)
    Box(
        modifier = mod
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = "${category.emoji} ${category.displayName}",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
```

- [ ] **Step 2: 写 `AmountInput.kt`**

```kotlin
package com.expense.tracker.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors

@Composable
fun AmountInput(onSubmit: (Double) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.displayMedium,
            color = AppColors.TextPrimary,
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { v -> if (v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))) text = v },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = MaterialTheme.typography.displayLarge.fontSize,
                ),
                cursorBrush = SolidColor(AppColors.TextPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (text.isEmpty()) {
                Text(
                    text = "0",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.displayLarge,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AppColors.TextPrimary)
                .pointerInput(text) {
                    detectTapGestures(onTap = {
                        val v = text.toDoubleOrNull()
                        if (v != null && v > 0) {
                            onSubmit(v); text = ""
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ArrowUpward,
                contentDescription = "提交",
                tint = Color.White,
            )
        }
    }
}
```

- [ ] **Step 3: 写 `TemplateCard.kt`**

```kotlin
package com.expense.tracker.ui.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.model.Category
import com.expense.tracker.ui.theme.AppColors

@Composable
fun TemplateCard(
    selectedCategoryId: String,
    onSelectCategory: (String) -> Unit,
    onSubmit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Text(
            text = "选择分类，然后输入金额",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = Category.ALL, key = { it.id }) { c ->
                CategoryChip(
                    category = c,
                    selected = c.id == selectedCategoryId,
                    onClick = { onSelectCategory(c.id) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        AmountInput(onSubmit = onSubmit)
    }
}
```

- [ ] **Step 4: 编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/template/
git commit -m "feat(ui): template card — category chips + large amount input"
```

---

### Task 15: InteractiveDock + 主屏装配

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/dock/DockItem.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/dock/InteractiveDock.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`

- [ ] **Step 1: 写 `DockItem.kt`（带 spring 弹跳）**

```kotlin
package com.expense.tracker.ui.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun DockItem(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScopeCompat()
    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    coroutineScope.launch {
                        scale.animateTo(
                            targetValue = 1.2f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        )
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        )
                    }
                    onClick()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = AppColors.TextPrimary)
    }
}

@Composable
private fun rememberCoroutineScopeCompat(): kotlinx.coroutines.CoroutineScope =
    androidx.compose.runtime.rememberCoroutineScope()
```

- [ ] **Step 2: 写 `InteractiveDock.kt`**

```kotlin
package com.expense.tracker.ui.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.dockShadow

@Composable
fun InteractiveDock(
    onNew: () -> Unit,
    onAnalytics: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .dockShadow()
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.Bg)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        DockItem(Icons.Outlined.Add,           "新建", onNew)
        DockItem(Icons.Outlined.BarChart,      "分析", onAnalytics)
        DockItem(Icons.Outlined.CalendarMonth, "历史", onHistory)
        DockItem(Icons.Outlined.Settings,      "设置", onSettings)
    }
}
```

- [ ] **Step 3: 写 `ChatScreen.kt`（装配整页）**

```kotlin
package com.expense.tracker.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.expense.tracker.ui.dock.InteractiveDock
import com.expense.tracker.ui.template.TemplateCard
import com.expense.tracker.ui.theme.AppColors

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenAnalytics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsState()
    Box(modifier = modifier.fillMaxSize().background(AppColors.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                onMenuClick = onOpenAnalytics,
                onEditClick = { /* 第一版不实现 */ },
                onMoreClick = { /* 第一版不实现 */ },
            )
            MessageList(messages = state.messages, modifier = Modifier.weight(1f))
            if (!state.llmEnabled) {
                TemplateCard(
                    selectedCategoryId = state.selectedCategoryId,
                    onSelectCategory = vm::selectCategory,
                    onSubmit = vm::submitTemplate,
                )
            }
            InputBar(
                llmEnabled = state.llmEnabled,
                onToggleLlm = vm::toggleLlm,
                onSend = vm::submitFreeText,
                onPlusClick = { /* 第一版不实现 */ },
                placeholder = if (state.llmEnabled) "随便怎么说..." else "回复记账助手",
            )
            InteractiveDock(
                onNew = { /* 第一版不实现 */ },
                onAnalytics = onOpenAnalytics,
                onHistory = { /* 第一版不实现 */ },
                onSettings = { /* 第一版不实现 */ },
            )
        }
    }
}
```

- [ ] **Step 4: 修改 `MainActivity.kt` 装载 ChatScreen**

完整内容：

```kotlin
package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expense.tracker.data.prefs.UserPrefsSnapshot
import com.expense.tracker.ui.chat.ChatScreen
import com.expense.tracker.ui.chat.ChatViewModel
import com.expense.tracker.ui.chat.LlmResult
import com.expense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val chatVm: ChatViewModel by viewModels {
        val container = (application as ExpenseApp).container
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                expenseRepo = container.expenseRepo,
                chatRepo = container.chatRepo,
                userPrefs = container.userPrefs,
                llmHandler = { _: String, _: UserPrefsSnapshot ->
                    LlmResult.Error("LLM 客户端尚未接入，请关闭 🧠 用模板模式")
                },
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ChatScreen(vm = chatVm, onOpenAnalytics = { /* Part 3 实现 */ })
            }
        }
    }
}
```

- [ ] **Step 5: 编译并打 APK**

Run: `gradle :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 安装并打开 App，手动验证**

Run: `gradle :app:installDebug` → 在设备上打开「记账助手」

手动验证清单：
- 顶部三个白圆按钮可见有投影
- 默认 🧠 关闭，下方出现分类标签 + 大数字输入
- 点击分类标签，选中态变黑底白字
- 输入数字 35 → 点 ↑ → 出现「[模板] 🍜 餐饮 ¥35.00」+「✅ 已记录...」
- 重启 App，记录还在（Room 持久化）
- 点击 🧠 切到 ON：图标变亮，下方分类区消失，输入栏占位变「随便怎么说...」
- 点击 🧠 切回 OFF，重启 App 仍是 OFF
- Dock 4 个图标，点击会弹一下

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/dock/ \
        app/src/main/java/com/expense/tracker/ui/chat/ChatScreen.kt \
        app/src/main/java/com/expense/tracker/MainActivity.kt
git commit -m "feat(ui): assemble ChatScreen with topbar + messages + input + template + dock"
```

---

## Part 2 完成验收

- ✅ App 能跑，模板模式可记账，数据落库
- ✅ 🧠 切换记忆持久化
- ✅ Dock 弹簧动画工作
- ✅ 视觉风格符合脑暴最终稿（白底无边框靠投影）

完成 Part 2 后请进入 [Part 3: LLM + 分析](./2026-06-15-expense-tracker-part3-llm-analytics.md)
