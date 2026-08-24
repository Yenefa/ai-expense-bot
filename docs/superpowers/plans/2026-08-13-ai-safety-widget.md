# AI Safety and Widget Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent silent AI mis-edits and deterministic date/time drift while moving widget storage work off the main thread.

**Architecture:** `ExpenseTextInterpreter` extracts source-owned amount/date/time hints, `ChatLlmCoordinator` enables complete hints for both single- and multi-date additions, and `LlmMutationPlanner` applies those facts before producing confirmation previews. `ExpenseWidgetProvider` owns an asynchronous IO refresh launched from `goAsync()` and receives app refreshes through an explicit broadcast.

**Tech Stack:** Kotlin, JUnit 4, Truth, coroutines, Room, DataStore, Android AppWidgetProvider, Gradle/AGP.

---

### Task 1: Deterministic source hints

**Files:**

- Modify: `app/src/test/java/com/expense/tracker/llm/ExpenseTextInterpreterTest.kt`
- Modify: `app/src/test/java/com/expense/tracker/llm/ChatLlmCoordinatorTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/ExpenseTextInterpreter.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/ChatLlmCoordinator.kt`

- [ ] Add failing tests asserting a complete single-date batch and the nine exact times from the reported input.
- [ ] Run the focused tests and verify they fail because complete single-date hints and hint times do not exist.
- [ ] Add `LocalTime?` to `SourceExpenseHint`, parse only the time segment since the previous amount, and expose `hasCompleteExpenseHints` without a multi-date requirement.
- [ ] Pass complete hints from the coordinator and rerun the focused tests.

### Task 2: Safe mutation planning

**Files:**

- Modify: `app/src/test/java/com/expense/tracker/llm/LlmMutationPlannerTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmMutationPlanner.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/chat/ChatScreen.kt`

- [ ] Add failing tests asserting explicit hint times override model times and only deletes require confirmation.
- [ ] Run the focused planner tests and verify the expected failures.
- [ ] Apply exact date/time hints while preserving immediate updates and delete-only confirmation.
- [ ] Rerun planner, coordinator, and ViewModel tests.

### Task 3: Non-blocking widget refresh

**Files:**

- Create: `tools/test-widget-nonblocking.ps1`
- Modify: `app/src/main/java/com/expense/tracker/widget/ExpenseWidgetProvider.kt`

- [ ] Add and run a failing contract test that rejects `runBlocking` and direct `ExpenseWidgetProvider().onUpdate` calls.
- [ ] Convert snapshot loading to a suspend function launched on `Dispatchers.IO` from `goAsync()` and always finish the pending result.
- [ ] Make `requestRefresh` send an explicit `ACTION_APPWIDGET_UPDATE` broadcast and rerun the contract test.

### Task 4: Release verification and delivery

**Files:**

- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`
- Create: release APK under `C:/Users/fuker/Desktop/app/codex change`

- [ ] Bump to versionCode 29 / versionName 3.7.1 and record the four fixes.
- [ ] Run `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `lintRelease`, and `assembleRelease` with the production subscription API base URL.
- [ ] Zipalign and sign with the existing signing identity, then verify alignment, APK signature certificate, package/version, and SHA-256.
- [ ] Confirm `git diff` contains no OCR changes and deliver the APK plus verification summary.
