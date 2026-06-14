# 智能记账 App 实现计划 — Part 1: 基础架构

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Android Compose 项目骨架，建立数据层（Room + DataStore）、配色系统、Application 容器

**Architecture:** 单 Activity + Compose 导航，Room SQLite 持久化，DataStore 存用户偏好，手动构造 DI 容器（YAGNI 不引入 Hilt）

**Tech Stack:** Kotlin 1.9, Jetpack Compose BOM 2024.02, Room 2.6, DataStore 1.0, Coroutines 1.7, JUnit4 + Truth

---

## 任务总览（Part 1: 7 个任务）

| # | 任务 | 关键产物 |
|---|---|---|
| 1 | 创建 Gradle 项目骨架 | `build.gradle.kts`, `settings.gradle.kts`, `AndroidManifest.xml` |
| 2 | 添加依赖 | Compose / Room / DataStore / 测试库 |
| 3 | 配色与字体系统 | `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Shadow.kt` |
| 4 | Category 数据模型 | `data/model/Category.kt` |
| 5 | Room 实体 + Dao | `ExpenseEntity`, `ExpenseDao`, `ChatMessageEntity`, `ChatMessageDao`, `AppDatabase` |
| 6 | DataStore UserPrefs | `data/prefs/UserPrefs.kt` |
| 7 | Application + DI 容器 | `ExpenseApp.kt`, `MainActivity.kt` |

---

### Task 1: 创建 Gradle 项目骨架

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/proguard-rules.pro`

- [ ] **Step 1: 写 `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ExpenseTracker"
include(":app")
```

- [ ] **Step 2: 写根 `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
```

- [ ] **Step 3: 写 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: 写 `app/build.gradle.kts`（最小可编译版，依赖下个任务补全）**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.expense.tracker"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.expense.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // 占位，下个任务补
}
```

- [ ] **Step 5: 写 `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:name=".ExpenseApp"
        android:label="记账助手"
        android:theme="@style/Theme.ExpenseTracker"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: 写 `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.ExpenseTracker" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 7: 写 `proguard-rules.pro`**（空文件即可）

```
# Add project specific ProGuard rules here.
```

- [ ] **Step 8: 验证 gradle 文件能被解析**

Run: `cd C:/Users/fuker/expense-tracker && gradle help --offline 2>&1 | head -20`
Expected: 不报语法错误（gradle wrapper 未生成时显示 "Welcome to Gradle"）

- [ ] **Step 9: 提交**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts \
        app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml \
        app/proguard-rules.pro
git commit -m "chore: gradle skeleton for ExpenseTracker"
```

---

### Task 2: 添加 Compose / Room / DataStore 依赖

**Files:**
- Modify: `app/build.gradle.kts`（替换 dependencies 块）

- [ ] **Step 1: 替换 `app/build.gradle.kts` 中的 `dependencies { }` 块**

```kotlin
dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking (LLM)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Charts
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 2: 把 kotlinx serialization 插件加到 `app/build.gradle.kts` 顶部 plugins 块**

替换 `plugins { ... }` 为：

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("com.google.devtools.ksp")
}
```

- [ ] **Step 3: 验证依赖能拉取**

Run: `cd C:/Users/fuker/expense-tracker && gradle :app:dependencies --configuration debugCompileClasspath 2>&1 | tail -20`
Expected: 列出 androidx.compose, androidx.room 等条目，无 "Could not resolve" 错误

- [ ] **Step 4: 提交**

```bash
git add app/build.gradle.kts
git commit -m "chore: add compose / room / datastore / vico dependencies"
```

---

### Task 3: 配色 + 字体 + 投影系统

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/theme/Color.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/theme/Type.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/theme/Shadow.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/theme/Theme.kt`
- Create: `app/src/test/java/com/expense/tracker/ui/theme/ColorTest.kt`

- [ ] **Step 1: 写失败测试 `ColorTest.kt`**

```kotlin
package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ColorTest {
    @Test fun bgIsWhite() = assertThat(AppColors.Bg).isEqualTo(Color(0xFFFFFFFF))
    @Test fun textPrimaryIsAlmostBlack() = assertThat(AppColors.TextPrimary).isEqualTo(Color(0xFF0D0D0D))
    @Test fun textSecondaryIsMidGray() = assertThat(AppColors.TextSecondary).isEqualTo(Color(0xFF5D5D5D))
    @Test fun textMutedIsLightGray() = assertThat(AppColors.TextMuted).isEqualTo(Color(0xFF8E8E8E))
    @Test fun chipFillIsVeryLightGray() = assertThat(AppColors.ChipFill).isEqualTo(Color(0xFFF4F4F4))
    @Test fun accentIsAppleBlue() = assertThat(AppColors.Accent).isEqualTo(Color(0xFF0A84FF))
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.ui.theme.ColorTest"`
Expected: FAIL — `Unresolved reference: AppColors`

- [ ] **Step 3: 写 `Color.kt`**

```kotlin
package com.expense.tracker.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val Bg = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF0D0D0D)
    val TextSecondary = Color(0xFF5D5D5D)
    val TextMuted = Color(0xFF8E8E8E)
    val ChipFill = Color(0xFFF4F4F4)
    val Accent = Color(0xFF0A84FF)
    val DockBgSimulated = Color(0xFFD4D4D4) // 仅用于设计参考，实际页面背景是白
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.ui.theme.ColorTest"`
Expected: 6 PASS

- [ ] **Step 5: 写 `Type.kt`**

```kotlin
package com.expense.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal),  // 金额数字
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal), // ¥ 符号
    titleLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),  // 顶部标题
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),     // 聊天正文
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),    // 副文
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),   // 分类标签
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),  // 状态提示
)
```

- [ ] **Step 6: 写 `Shadow.kt`（投影 Modifier 工具）**

```kotlin
package com.expense.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ChatGPT 风格投影：边框靠 spread shadow + 主投影做层次。
 */
fun Modifier.softShadow(
    elevation: Dp = 4.dp,
    cornerRadius: Dp = 22.dp,
    ambientAlpha: Float = 0.04f,
    spotAlpha: Float = 0.10f,
): Modifier = this.shadow(
    elevation = elevation,
    shape = RoundedCornerShape(cornerRadius),
    ambientColor = Color.Black.copy(alpha = ambientAlpha),
    spotColor = Color.Black.copy(alpha = spotAlpha),
    clip = false,
)

fun Modifier.dockShadow() = softShadow(elevation = 10.dp, cornerRadius = 28.dp, spotAlpha = 0.10f)
fun Modifier.inputShadow() = softShadow(elevation = 4.dp, cornerRadius = 28.dp, spotAlpha = 0.10f)
fun Modifier.iconBtnShadow() = softShadow(elevation = 3.dp, cornerRadius = 18.dp, spotAlpha = 0.12f)
```

- [ ] **Step 7: 写 `Theme.kt`**

```kotlin
package com.expense.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = AppColors.TextPrimary,
    onPrimary = Color.White,
    secondary = AppColors.Accent,
    background = AppColors.Bg,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Bg,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.ChipFill,
    onSurfaceVariant = AppColors.TextSecondary,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = AppTypography,
        content = content,
    )
}
```

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/expense/tracker/ui/theme/ \
        app/src/test/java/com/expense/tracker/ui/theme/
git commit -m "feat(theme): chatgpt-style colors, typography, shadow modifiers"
```

---

### Task 4: Category 数据模型

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/model/Category.kt`
- Create: `app/src/test/java/com/expense/tracker/data/model/CategoryTest.kt`

- [ ] **Step 1: 写失败测试 `CategoryTest.kt`**

```kotlin
package com.expense.tracker.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryTest {
    @Test fun all8CategoriesExist() {
        assertThat(Category.ALL.map { it.id })
            .containsExactly("food", "transport", "shopping", "drink",
                             "entertainment", "housing", "medical", "other")
    }
    @Test fun foodCategoryHasEmoji() {
        assertThat(Category.byId("food")!!.emoji).isEqualTo("🍜")
    }
    @Test fun unknownIdReturnsOther() {
        assertThat(Category.byIdOrOther("nonexistent").id).isEqualTo("other")
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.data.model.CategoryTest"`
Expected: FAIL — `Unresolved reference: Category`

- [ ] **Step 3: 写 `Category.kt`**

```kotlin
package com.expense.tracker.data.model

data class Category(
    val id: String,
    val emoji: String,
    val displayName: String,
) {
    companion object {
        val ALL: List<Category> = listOf(
            Category("food",          "🍜", "餐饮"),
            Category("transport",     "🚗", "交通"),
            Category("shopping",      "🛒", "购物"),
            Category("drink",         "☕", "饮品"),
            Category("entertainment", "🎮", "娱乐"),
            Category("housing",       "🏠", "住房"),
            Category("medical",       "💊", "医疗"),
            Category("other",         "📦", "其他"),
        )
        private val byIdMap: Map<String, Category> = ALL.associateBy { it.id }
        fun byId(id: String): Category? = byIdMap[id]
        fun byIdOrOther(id: String): Category = byIdMap[id] ?: byIdMap.getValue("other")
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.data.model.CategoryTest"`
Expected: 3 PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/expense/tracker/data/model/Category.kt \
        app/src/test/java/com/expense/tracker/data/model/CategoryTest.kt
git commit -m "feat(data): fixed 8-category model"
```

---

### Task 5: Room 数据库（实体 + Dao + Database）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/db/ExpenseEntity.kt`
- Create: `app/src/main/java/com/expense/tracker/data/db/ExpenseDao.kt`
- Create: `app/src/main/java/com/expense/tracker/data/db/ChatMessageEntity.kt`
- Create: `app/src/main/java/com/expense/tracker/data/db/ChatMessageDao.kt`
- Create: `app/src/main/java/com/expense/tracker/data/db/AppDatabase.kt`
- Create: `app/src/androidTest/java/com/expense/tracker/data/db/AppDatabaseTest.kt`

- [ ] **Step 1: 写失败的 instrumentation 测试 `AppDatabaseTest.kt`**

```kotlin
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
            amount = 35.0, categoryId = "food", note = "午饭",
            occurredAt = 1_000L, createdAt = 1_000L,
        ))
        assertThat(id).isGreaterThan(0L)
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].amount).isEqualTo(35.0)
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
        dao.insert(ExpenseEntity(10.0, "food", "", 100L, 100L))
        dao.insert(ExpenseEntity(20.0, "food", "", 500L, 500L))
        dao.insert(ExpenseEntity(30.0, "food", "", 1000L, 1000L))
        val mid = dao.observeInRange(200L, 800L).first()
        assertThat(mid).hasSize(1)
        assertThat(mid[0].amount).isEqualTo(20.0)
    }
}
```

- [ ] **Step 2: 运行测试确认失败（实体未定义）**

Run: `gradle :app:connectedDebugAndroidTest --tests "com.expense.tracker.data.db.AppDatabaseTest"`
Expected: FAIL — 编译错误 `Unresolved reference: AppDatabase`（需连接 Android 模拟器，若没模拟器，写完代码后跳到 Step 8 验证编译）

- [ ] **Step 3: 写 `ExpenseEntity.kt`**

```kotlin
package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    val amount: Double,
    val categoryId: String,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
)
```

- [ ] **Step 4: 写 `ExpenseDao.kt`**

```kotlin
package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt ASC")
    fun observeInRange(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 5: 写 `ChatMessageEntity.kt`**

```kotlin
package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    val role: String,                 // "user" | "assistant"
    val content: String,
    val createdAt: Long,
    val relatedExpenseId: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
)
```

- [ ] **Step 6: 写 `ChatMessageDao.kt`**

```kotlin
package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
```

- [ ] **Step 7: 写 `AppDatabase.kt`**

```kotlin
package com.expense.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExpenseEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
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
            ).build().also { instance = it }
        }
    }
}
```

- [ ] **Step 8: 验证编译**

Run: `gradle :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL（KSP 生成 Dao 实现，无报错）

- [ ] **Step 9: 跑 instrumentation 测试（需 Android 模拟器/真机）**

Run: `gradle :app:connectedDebugAndroidTest --tests "com.expense.tracker.data.db.AppDatabaseTest"`
Expected: 3 PASS

> 若没有模拟器：先跳过此步骤，编译通过即可，等到 UI 任务里启动模拟器再补跑。

- [ ] **Step 10: 提交**

```bash
git add app/src/main/java/com/expense/tracker/data/db/ \
        app/src/androidTest/java/com/expense/tracker/data/db/
git commit -m "feat(data): room database with expenses and chat_messages tables"
```

---

### Task 6: DataStore 用户偏好（LLM 开关 + API 配置）

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/prefs/UserPrefs.kt`
- Create: `app/src/test/java/com/expense/tracker/data/prefs/UserPrefsTest.kt`

- [ ] **Step 1: 写失败测试 `UserPrefsTest.kt`**

```kotlin
package com.expense.tracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UserPrefsTest {
    private fun fakeStore(initial: Preferences = mutablePreferencesOf()): DataStore<Preferences> {
        val state = MutableStateFlow(initial)
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val new = transform(state.value)
                state.value = new
                return new
            }
        }
    }

    @Test fun defaultsAreCorrect() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        val s = prefs.snapshot.first()
        assertThat(s.llmEnabled).isFalse()
        assertThat(s.baseUrl).isEqualTo("https://api.openai.com/v1")
        assertThat(s.apiKey).isEmpty()
        assertThat(s.model).isEqualTo("gpt-4o-mini")
    }

    @Test fun setLlmEnabledPersists() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        prefs.setLlmEnabled(true)
        assertThat(prefs.snapshot.first().llmEnabled).isTrue()
        prefs.setLlmEnabled(false)
        assertThat(prefs.snapshot.first().llmEnabled).isFalse()
    }

    @Test fun setApiConfigPersists() = runBlocking {
        val prefs = UserPrefs(fakeStore())
        prefs.setApiConfig(baseUrl = "https://x.com/v1", apiKey = "sk-1", model = "claude")
        val s = prefs.snapshot.first()
        assertThat(s.baseUrl).isEqualTo("https://x.com/v1")
        assertThat(s.apiKey).isEqualTo("sk-1")
        assertThat(s.model).isEqualTo("claude")
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.data.prefs.UserPrefsTest"`
Expected: FAIL — `Unresolved reference: UserPrefs`

- [ ] **Step 3: 写 `UserPrefs.kt`**

```kotlin
package com.expense.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserPrefsSnapshot(
    val llmEnabled: Boolean,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

class UserPrefs(private val store: DataStore<Preferences>) {

    companion object Keys {
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val BASE_URL    = stringPreferencesKey("llm_base_url")
        val API_KEY     = stringPreferencesKey("llm_api_key")
        val MODEL       = stringPreferencesKey("llm_model")

        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL    = "gpt-4o-mini"

        fun fromContext(ctx: Context) = UserPrefs(ctx.applicationContext.appDataStore)
    }

    val snapshot: Flow<UserPrefsSnapshot> = store.data.map { p ->
        UserPrefsSnapshot(
            llmEnabled = p[LLM_ENABLED] ?: false,
            baseUrl = p[BASE_URL] ?: DEFAULT_BASE_URL,
            apiKey = p[API_KEY] ?: "",
            model = p[MODEL] ?: DEFAULT_MODEL,
        )
    }

    suspend fun setLlmEnabled(enabled: Boolean) {
        store.edit { it[LLM_ENABLED] = enabled }
    }

    suspend fun setApiConfig(baseUrl: String, apiKey: String, model: String) {
        store.edit {
            it[BASE_URL] = baseUrl
            it[API_KEY] = apiKey
            it[MODEL] = model
        }
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `gradle :app:testDebugUnitTest --tests "com.expense.tracker.data.prefs.UserPrefsTest"`
Expected: 3 PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/expense/tracker/data/prefs/UserPrefs.kt \
        app/src/test/java/com/expense/tracker/data/prefs/UserPrefsTest.kt
git commit -m "feat(prefs): datastore-based user prefs (llm enabled + api config)"
```

---

### Task 7: Application + 手动 DI 容器 + 空 MainActivity

**Files:**
- Create: `app/src/main/java/com/expense/tracker/AppContainer.kt`
- Create: `app/src/main/java/com/expense/tracker/ExpenseApp.kt`
- Create: `app/src/main/java/com/expense/tracker/MainActivity.kt`

- [ ] **Step 1: 写 `AppContainer.kt`（手动 DI 容器，集中持有单例）**

```kotlin
package com.expense.tracker

import android.content.Context
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.prefs.UserPrefs

/**
 * 全局依赖容器。一切单例从这里获取，禁止再用 object 单例存状态。
 * 仓库 / LLM 客户端等会在后续任务中加入。
 */
class AppContainer(context: Context) {
    private val appCtx = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.get(appCtx) }
    val userPrefs: UserPrefs by lazy { UserPrefs.fromContext(appCtx) }
}
```

- [ ] **Step 2: 写 `ExpenseApp.kt`**

```kotlin
package com.expense.tracker

import android.app.Application

class ExpenseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 3: 写最小 `MainActivity.kt`**

```kotlin
package com.expense.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.expense.tracker.ui.theme.AppColors
import com.expense.tracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Box(
                    Modifier.fillMaxSize().background(AppColors.Bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("记账助手 — 骨架就绪")
                }
            }
        }
    }
}
```

- [ ] **Step 4: 编译并组装 APK**

Run: `gradle :app:assembleDebug 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`，生成 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/expense/tracker/AppContainer.kt \
        app/src/main/java/com/expense/tracker/ExpenseApp.kt \
        app/src/main/java/com/expense/tracker/MainActivity.kt
git commit -m "feat(app): application + manual DI container + skeleton MainActivity"
```

---

## Part 1 完成验收

- ✅ `gradle :app:assembleDebug` 成功，APK 能装到设备并显示「记账助手 — 骨架就绪」
- ✅ `gradle :app:testDebugUnitTest` 全部通过（Color / Category / UserPrefs 测试）
- ✅ Room 实体编译通过
- ✅ Git 至少 7 次提交，每个任务一次

完成 Part 1 后请进入 [Part 2: UI 与对话](./2026-06-15-expense-tracker-part2-ui.md)。
