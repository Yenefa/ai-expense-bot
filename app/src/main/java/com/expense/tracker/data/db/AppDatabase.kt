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
 * fallbackToDestructiveMigration() 仅作为 crash 防护 — 找不到 Migration 时才启用。
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

        /** v1 → v2：为 expenses 表添加 deletedAt 列（软删除支持） */
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
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
