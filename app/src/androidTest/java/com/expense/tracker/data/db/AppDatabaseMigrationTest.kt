package com.expense.tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migration2To3ConvertsRealYuanToIntegerCentsWithoutDataLoss() {
        val dbName = "migration-test-2-3"
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """INSERT INTO expenses
                    (amount, categoryId, note, occurredAt, createdAt, deletedAt, id)
                    VALUES (12.345, 'food', '午饭', 1000, 2000, 3000, 42)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        )

        db.query("SELECT amountCents, categoryId, note, occurredAt, createdAt, deletedAt, id FROM expenses").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(1_235L)
            assertThat(cursor.getString(1)).isEqualTo("food")
            assertThat(cursor.getString(2)).isEqualTo("午饭")
            assertThat(cursor.getLong(3)).isEqualTo(1_000L)
            assertThat(cursor.getLong(4)).isEqualTo(2_000L)
            assertThat(cursor.getLong(5)).isEqualTo(3_000L)
            assertThat(cursor.getLong(6)).isEqualTo(42L)
        }
        db.close()
    }

    @Test fun migration1To3ChainsWithoutDeletingExistingExpense() {
        val dbName = "migration-test-1-3"
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """INSERT INTO expenses
                    (amount, categoryId, note, occurredAt, createdAt, id)
                    VALUES (0.29, 'drink', '水', 10, 11, 9)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            3,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
        )

        db.query("SELECT amountCents, deletedAt, id FROM expenses").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(29L)
            assertThat(cursor.isNull(1)).isTrue()
            assertThat(cursor.getLong(2)).isEqualTo(9L)
        }
        db.close()
    }

    @Test fun migration3To4AddsBatchMetadataWithoutChangingChatRows() {
        val dbName = "migration-test-3-4"
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """INSERT INTO chat_messages
                    (role, content, createdAt, relatedExpenseId, id)
                    VALUES ('assistant', '已记', 1000, 42, 9)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            AppDatabase.MIGRATION_3_4,
        )

        db.query(
            "SELECT role, content, createdAt, relatedExpenseId, relatedExpenseIdsCsv, id FROM chat_messages",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("assistant")
            assertThat(cursor.getString(1)).isEqualTo("已记")
            assertThat(cursor.getLong(2)).isEqualTo(1_000L)
            assertThat(cursor.getLong(3)).isEqualTo(42L)
            assertThat(cursor.isNull(4)).isTrue()
            assertThat(cursor.getLong(5)).isEqualTo(9L)
        }
        db.close()
    }
}
