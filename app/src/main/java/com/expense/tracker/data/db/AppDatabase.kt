package com.expense.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库 — 存储在 Android 内部存储：/data/data/com.expense.tracker/databases/expense.db
 *
 * === 数据持久化保证 ===
 * Room 数据库（expense.db）和 DataStore 偏好（user_prefs.preferences_pb）存储于
 * App 私有内部存储目录。同一 App（相同签名、相同 applicationId）升级时，
 * Android 不会删除内部存储数据——你的记账记录和 API 配置不会被清空。
 *
 * === schema 升级策略 ===
 * 每个版本变更都写一个 Migration（如 MIGRATION_1_2），在 databaseBuilder 里 .addMigrations()。
 * 缺少 Migration 时必须启动失败，绝不能用清空用户数据来换取启动成功。
 */
@Database(
    entities = [ExpenseEntity::class, ChatMessageEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun chatDao(): ChatMessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2：为 expenses 表添加 deletedAt 列（软删除支持） */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
        }

        /** v2 → v3：金额由 REAL 元迁移为 INTEGER 分，保留全部行和主键。 */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE expenses_new (
                        amountCents INTEGER NOT NULL,
                        categoryId TEXT NOT NULL,
                        note TEXT NOT NULL,
                        occurredAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
                    )""".trimIndent(),
                )
                db.execSQL(
                    """INSERT INTO expenses_new
                        (amountCents, categoryId, note, occurredAt, createdAt, deletedAt, id)
                        SELECT CAST(ROUND(amount * 100.0) AS INTEGER),
                               categoryId, note, occurredAt, createdAt, deletedAt, id
                        FROM expenses
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE expenses")
                db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")
            }
        }

        /** v3 → v4：聊天消息记录一次 AI 操作涉及的完整账目 ID 批次。 */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN relatedExpenseIdsCsv TEXT DEFAULT NULL")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "expense.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
