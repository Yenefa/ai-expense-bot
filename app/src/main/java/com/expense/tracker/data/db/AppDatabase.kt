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
 * === schema 升级历史 ===
 * - v1（2026-06-15）：首版。expenses + chat_messages 两张表。
 * - v2（2026-06-16）：expenses 加 deletedAt 列实现"软删 + 最近删除"。
 *                     Migration 用 ALTER TABLE 加列默认 NULL，老数据全部视为活动记录。
 *
 * === 升级策略原则 ===
 * - **绝不**用 fallbackToDestructiveMigration() 作为升级手段（会清空用户数据）
 * - 改 Entity 字段必须配套写 Migration
 * - app/schemas/ 下要导出对应 version 的 JSON（KSP 自动生成，git 跟踪）
 */
@Database(
    entities = [ExpenseEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun chatDao(): ChatMessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 → v2：expenses 加 deletedAt 列。
         * 默认 NULL —— 升级前的所有支出都视为"活动记录"，不会出现在最近删除里。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "expense.db",
            ).addMigrations(MIGRATION_1_2)
             .build().also { instance = it }
        }
    }
}
