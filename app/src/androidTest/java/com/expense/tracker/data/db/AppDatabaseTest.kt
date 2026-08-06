package com.expense.tracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun insertAndQueryExpense() = runBlocking {
        val dao = db.expenseDao()
        val id = dao.insert(ExpenseEntity(
            amountCents = 3_500L, categoryId = "food", note = "午饭",
            occurredAt = 1_000L, createdAt = 1_000L,
        ))
        assertThat(id).isGreaterThan(0L)
        val all = dao.observeActive().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].amountCents).isEqualTo(3_500L)
        assertThat(all[0].categoryId).isEqualTo("food")
    }

    @Test fun insertChatMessageRoundTrip() = runBlocking {
        val dao = db.chatDao()
        dao.insert(ChatMessageEntity(role = "user", content = "午饭35", createdAt = 100L))
        dao.insert(ChatMessageEntity(role = "assistant", content = "已记录", createdAt = 101L))
        val all = dao.observeAll().first()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.role }).containsExactly("user", "assistant").inOrder()
    }

    @Test fun expensesInRangeFiltersByOccurredAt() = runBlocking {
        val dao = db.expenseDao()
        dao.insert(ExpenseEntity(1_000L, "food", "", 100L, 100L))
        dao.insert(ExpenseEntity(2_000L, "food", "", 500L, 500L))
        dao.insert(ExpenseEntity(3_000L, "food", "", 1000L, 1000L))
        val mid = dao.observeInRange(200L, 800L).first()
        assertThat(mid).hasSize(1)
        assertThat(mid[0].amountCents).isEqualTo(2_000L)
    }
}
