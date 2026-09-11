plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("com.google.devtools.ksp")
}

val subscriptionApiBaseUrl = providers
    .gradleProperty("YE_COST_SUBSCRIPTION_API_BASE_URL")
    .orElse("")
    .get()
val escapedSubscriptionApiBaseUrl = subscriptionApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.expense.tracker"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.expense.tracker"
        minSdk = 26
        targetSdk = 34
        // 版本管理规范（2026-08-08 起强制）：
        // - versionCode 每次交付 +1，永不回退
        // - versionName 语义化：主版本.次版本.修订（修复=修订+1，新功能=次版本+1）
        // - 每次版本变更必须同步更新 CHANGELOG.md
        versionCode = 34
        versionName = "3.9.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "SUBSCRIPTION_API_BASE_URL",
            "\"$escapedSubscriptionApiBaseUrl\"",
        )
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }
}

tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "lintRelease")) {
        doFirst {
            if (subscriptionApiBaseUrl.isBlank()) {
                throw GradleException(
                    "Release requires -PYE_COST_SUBSCRIPTION_API_BASE_URL with the deployed HTTPS endpoint",
                )
            }
            if (!subscriptionApiBaseUrl.startsWith("https://")) {
                throw GradleException("Subscription API base URL must use HTTPS")
            }
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
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

    // Daily reminder scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Networking (LLM)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // On-device Chinese OCR for payment bill screenshots (model bundled in APK)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    // Latin OCR for mixed-language / English receipts (parallel recognizer)
    implementation("com.google.mlkit:text-recognition:16.0.0")

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
    androidTestImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ExpenseBench 评测：EXPENSEBENCH_* 环境变量变化时强制重跑（默认 test 任务对 env 不敏感，会 UP-TO-DATE 跳过）
tasks.withType<Test>().configureEach {
    inputs.property("expensebenchModel") { System.getenv("EXPENSEBENCH_MODEL").orEmpty() }
    inputs.property("expensebenchLimit") { System.getenv("EXPENSEBENCH_LIMIT").orEmpty() }
    inputs.property("expensebenchConcurrency") { System.getenv("EXPENSEBENCH_CONCURRENCY").orEmpty() }
}
