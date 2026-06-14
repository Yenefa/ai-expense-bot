package com.expense.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库 — 存储在 Android 内部存储：/data/data/com.expense.tracker/databases/expense.db
 *
 * === 数据持久化保证 ===
 * Room 数据库（expense.db）和 DataStore 偏好（user_prefs.preferences_pb）存储于
 * App 私有内部存储目录。同一 App（相同签名、相同 applicationId）升级时，
 * Android 不会删除内部存储数据——你的记账记录和 API 配置不会被清空。
 *
 * === schema 升级策略 ===
 * - version = 1 且 exported schema 在 git 中（见 app/schemas/）。
 * - 如果未来必须改 Entity 字段（如加新列），必须：
 *    1. 从 app/schemas/ 找到当前 schema JSON
 *    2. 写一个 Room Migration（从 v1 → v2）
 *    3. 在 databaseBuilder 里 .addMigrations(MIGRATION_1_2)
 *    4. 不要改 fallbackToDestructiveMigration() 为默认——已启用作为 crash 防护
 * - fallbackToDestructiveMigration() 的意思是：如果 schema version 变了但没找到对应 Migration，
 *   Room 会删除旧 DB 重建空表（比 app 直接 crash 好）。不要依赖它作为升级手段。
 */
@Database(
    entities = [ExpenseEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun chatDao(): ChatMessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "expense.db",
            ).fallbackToDestructiveMigration()
             .build().also { instance = it }
        }
    }
}
