# AI Date and Batch Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make conversational dates and follow-up messages reliable while preventing unintended multi-record changes.

**Architecture:** Keep the LLM as a candidate parser, then pass its output through deterministic date resolution, persistent batch scoping, a code-level mutation whitelist, and a confirmation gate. Apply confirmed operations atomically with optimistic snapshot validation; leave OCR code untouched.

**Tech Stack:** Kotlin 1.9, Android Compose Material 3, Room 2.6, kotlinx.serialization, coroutines, JUnit 4, Truth.

---

### Task 1: Deterministic Chinese date context

**Files:**
- Create: `app/src/main/java/com/expense/tracker/llm/ChineseDateResolver.kt`
- Test: `app/src/test/java/com/expense/tracker/llm/ChineseDateResolverTest.kt`

- [ ] Write failing tests for `2026年8月1日`, `8月1号`, ISO dates, today/yesterday/day-before-yesterday, year rollover, continuation inheritance, independent-message non-inheritance, and replacing a date while keeping time.
- [ ] Run `./gradlew testDebugUnitTest --tests com.expense.tracker.llm.ChineseDateResolverTest` and confirm unresolved symbols fail.
- [ ] Implement `resolveExplicit`, `resolveForMessage`, `looksLikeContinuation`, and `replaceDateKeepingTime` using `java.time` and the device zone.
- [ ] Re-run the targeted test and confirm all cases pass.

### Task 2: Conversation and persistent batch metadata

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/data/db/ChatMessageEntity.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/db/ChatMessageDao.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/repo/ChatRepository.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/export/DataExporter.kt`
- Modify: fake DAOs in unit tests
- Test: `app/src/test/java/com/expense/tracker/data/repo/RepositoryTest.kt`
- Test: `app/src/test/java/com/expense/tracker/data/export/DataExporterTest.kt`
- Test: `app/src/androidTest/java/com/expense/tracker/data/db/AppDatabaseMigrationTest.kt`

- [ ] Write failing repository/export/migration tests proving multiple related IDs round-trip and v3 rows survive migration.
- [ ] Run targeted JVM tests and `compileDebugAndroidTestKotlin`; confirm they fail because batch metadata and migration are absent.
- [ ] Add optional `relatedExpenseIdsCsv`, conversion helpers, a recent-message query, and `MIGRATION_3_4`; retain `relatedExpenseId` compatibility.
- [ ] Add the optional batch field to backup JSON without rejecting older v2 backup files.
- [ ] Re-run targeted tests and generate/verify the Room v4 schema.

### Task 3: LLM history request contract

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmClient.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmPrompt.kt`
- Test: `app/src/test/java/com/expense/tracker/llm/LlmClientSubscriptionProxyTest.kt`
- Test: `app/src/test/java/com/expense/tracker/llm/LlmPromptContractTest.kt`

- [ ] Write a failing HTTP fixture test asserting `system, history..., current user` ordering with no duplicate current message.
- [ ] Write failing prompt tests for explicit-date precedence, batch markers, clarification on ambiguous pronouns, and no result claims before execution.
- [ ] Run the two test classes and confirm contract failures.
- [ ] Extend `chatJson` with a bounded history list and strengthen the system prompt using batch IDs and safety rules.
- [ ] Re-run the two test classes and confirm pass.

### Task 4: Safe mutation planning and atomic preconditions

**Files:**
- Create: `app/src/main/java/com/expense/tracker/llm/LlmMutationPlanner.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/repo/LlmMutationApplier.kt`
- Test: `app/src/test/java/com/expense/tracker/llm/LlmMutationPlannerTest.kt`
- Modify: `app/src/test/java/com/expense/tracker/data/repo/LlmMutationApplierTest.kt`

- [ ] Write failing planner tests for forced target date, time preservation, recent-record whitelist, last-batch narrowing, duplicate ID rejection, mixed add/action rejection, and confirmation criteria.
- [ ] Write failing applier tests for missing/deleted/changed target rollback and successful snapshot-matched transaction.
- [ ] Run targeted tests and confirm behavioral failures.
- [ ] Implement immutable `LlmMutationPlan`, `ExpenseSnapshot`, `MutationPreview`, typed safety exceptions, and the minimal planner rules.
- [ ] Change the applier to consume only a validated plan and re-check snapshots inside the Room transaction.
- [ ] Re-run targeted tests and confirm pass.

### Task 5: Coordinator and confirmation UI

**Files:**
- Create: `app/src/main/java/com/expense/tracker/llm/ChatLlmCoordinator.kt`
- Modify: `app/src/main/java/com/expense/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/ChatUiState.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`
- Test: `app/src/test/java/com/expense/tracker/llm/ChatLlmCoordinatorTest.kt`
- Modify: `app/src/test/java/com/expense/tracker/ui/chat/ChatViewModelTest.kt`

- [ ] Write failing coordinator tests proving multi-record updates never apply before confirmation, cancel never applies, and confirmation applies once.
- [ ] Write failing ViewModel tests for pending state, cancel text, confirm result, and all inserted IDs saved as one assistant batch.
- [ ] Run targeted tests and confirm failures.
- [ ] Implement the coordinator with bounded in-memory confirmation tokens, expanded active-record context, history context, and safe error messages.
- [ ] Extend `LlmResult` and UI state; add confirm/cancel ViewModel actions and a Material 3 preview dialog.
- [ ] Wire the coordinator through `AppContainer` and `MainActivity` without changing OCR dependencies or handlers.
- [ ] Re-run targeted tests and confirm pass.

### Task 6: Review, regression verification, and release

**Files:**
- Modify only files required by review findings
- Create delivery note under `C:/Users/fuker/Desktop/app/codex change`

- [ ] Review the diff against the design, then request an independent code review and resolve every critical/important finding.
- [ ] Run `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintRelease assembleRelease -PYE_COST_SUBSCRIPTION_API_BASE_URL=https://ilove-d5g0gzrpp375112b9-1413557923.ap-shanghai.app.tcloudbase.com/ye-cost-api`.
- [ ] Run all existing PowerShell contract scripts, including privacy, backup rules, navigation, splash, icon safe-zone, and subscription-secret checks.
- [ ] Sign the release APK with the existing Android debug keystore certificate and verify the certificate digest matches the previous delivered APK.
- [ ] Copy the final APK and a concise Chinese change/verification note to `C:/Users/fuker/Desktop/app/codex change`, calculate SHA-256, and verify both files exist.
- [ ] Update the download website if the existing deployment credentials/session are available; otherwise preserve the verified local deliverables and record the exact limitation without exposing secrets.
- [ ] Execute the explicitly requested one-time Windows shutdown only after every deliverable and checksum verification succeeds.
