# Privacy-Safe Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent Y.E cost from logging user text, financial records, LLM content, API configuration, and exception details while retaining minimal structured diagnostics in Debug builds only.

**Architecture:** Route all application-owned Android logging through a small structured logger whose methods accept only fixed events and integer counts. Guard the sink with `BuildConfig.DEBUG`, remove every direct log call from `AppContainer`, and enforce the boundary with a project-source contract test.

**Tech Stack:** Kotlin, Android `Log`, generated `BuildConfig`, PowerShell contract testing, Gradle unit tests and Lint

---

## File map

- Create `tools/test-privacy-logs.ps1`: rejects direct application logging and validates the structured safe logger contract.
- Create `app/src/main/java/com/expense/tracker/util/PrivacySafeLog.kt`: the only application-owned Android Log sink.
- Modify `app/src/main/java/com/expense/tracker/AppContainer.kt`: replace sensitive direct logging with fixed safe events.
- Modify `app/build.gradle.kts`: generate `BuildConfig.DEBUG` for the build-type guard.

No database, UI, API request, response parsing, backup, or signing behavior changes.

### Task 1: Define the privacy logging contract

**Files:**
- Create `tools/test-privacy-logs.ps1`.

- [x] **Step 1: Add the failing contract test**

Create a PowerShell test that scans `app/src/main/**/*.kt`, permits `android.util.Log` and `Log.d` only in `PrivacySafeLog.kt`, rejects `println` and `printStackTrace`, requires the `BuildConfig.DEBUG` guard, requires structured methods with only integer/no arguments, and requires `AppContainer` to call those methods.

- [x] **Step 2: Verify the RED state**

Run:

```powershell
.\tools\test-privacy-logs.ps1
```

Expected: exit code 1 with `Missing privacy-safe logger` because production code has not been added.

### Task 2: Add the structured Debug-only logger

**Files:**
- Create `app/src/main/java/com/expense/tracker/util/PrivacySafeLog.kt`.
- Modify `app/build.gradle.kts`.

- [x] **Step 1: Add the minimal logger**

```kotlin
package com.expense.tracker.util

import android.util.Log
import com.expense.tracker.BuildConfig

internal object PrivacySafeLog {
    private const val TAG = "YECost"

    fun llmRequestStarted(recentRecordCount: Int) = debug {
        "llm_request_started recent_record_count=$recentRecordCount"
    }

    fun llmResponseParsed(expenseCount: Int, actionCount: Int) = debug {
        "llm_response_parsed expense_count=$expenseCount action_count=$actionCount"
    }

    fun llmRequestFailed() = debug { "llm_request_failed" }

    private inline fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }
}
```

The API intentionally cannot accept user strings, entities, URLs, API Keys, response bodies, exceptions, or record IDs.

- [x] **Step 2: Enable the generated build-type guard**

Expand the existing build features block:

```kotlin
buildFeatures {
    compose = true
    buildConfig = true
}
```

This keeps release behavior controlled by the generated constant without adding runtime configuration or a new dependency.

### Task 3: Replace the sensitive call sites

**Files:**
- Modify `app/src/main/java/com/expense/tracker/AppContainer.kt`.
- Test `tools/test-privacy-logs.ps1`.

- [x] **Step 1: Remove direct logging dependencies**

Delete `import android.util.Log` and the `TAG` companion object. Add:

```kotlin
import com.expense.tracker.util.PrivacySafeLog
```

- [x] **Step 2: Keep only structured events**

After loading recent records:

```kotlin
PrivacySafeLog.llmRequestStarted(recentRecords.size)
```

After parsing the response:

```kotlin
PrivacySafeLog.llmResponseParsed(
    expenseCount = result.expenses.size,
    actionCount = result.actions.size,
)
```

In the error branch:

```kotlin
PrivacySafeLog.llmRequestFailed()
LlmResult.Error(it.message ?: "未知错误")
```

Keep the existing user-facing error result unchanged; only the log sink loses exception messages and stack traces.

- [x] **Step 3: Remove per-record and per-mutation logging**

Delete logs containing user text, timestamps, IDs, categories, amounts, notes, raw LLM output, reply text, parsed actions, mutation targets, exception messages, and throwable objects. Do not replace them with hashed or partially masked user data.

- [x] **Step 4: Verify GREEN**

Run:

```powershell
.\tools\test-privacy-logs.ps1
```

Expected: `Privacy logging contract: PASS` and exit code 0.

### Task 4: Regression verification

**Files:**
- Verify only; do not stage or commit the dirty workspace.

- [x] **Step 1: Run unit tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [x] **Step 2: Run Android Lint**

```powershell
.\gradlew.bat lintRelease
```

Expected: `BUILD SUCCESSFUL` with no Lint errors.

- [x] **Step 3: Build Release**

```powershell
.\gradlew.bat assembleRelease
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/release/app-release-unsigned.apk` exists.

- [x] **Step 4: Review the scoped diff**

```powershell
git diff -- tools/test-privacy-logs.ps1 app/build.gradle.kts app/src/main/java/com/expense/tracker/util/PrivacySafeLog.kt app/src/main/java/com/expense/tracker/AppContainer.kt docs/superpowers/plans/2026-08-01-v3.6-hardening-optimization-roadmap.md docs/superpowers/plans/2026-08-01-privacy-safe-logging.md
```

Expected: only the two plan documents, privacy contract, safe logger, and direct-log replacements appear. Do not commit because `AppContainer.kt` already contains unrelated valid user changes that must remain user-owned.

### Task 5: Sign and deliver the APK

**Files:**
- Read `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Create `C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-privacy-log-cleanup-2026-08-01.apk`.

- [x] **Step 1: Align and sign with the existing certificate**

Use Android SDK Build Tools 37.0.0 `zipalign` and `apksigner` with the existing debug keystore. Refuse to overwrite an existing delivery artifact.

- [x] **Step 2: Verify package, platform, signature, and checksum**

Verified values:

```text
package=com.expense.tracker
versionCode=27
versionName=3.6
minSdk=26
targetSdk=34
certificateSha256=e5ac68a1544da24122eee453eebc758286644940167fce1a72e983455b3c2b5c
apkSha256=4C6A90DC771DB2416574A14671D632E9CE56D32226CB0B5C630F44F4D584721A
```
