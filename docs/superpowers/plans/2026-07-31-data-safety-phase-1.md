# Data Safety Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve existing user data across schema upgrades, store money exactly as cents, provide a complete restorable backup, and prevent accidental permanent deletion.

**Architecture:** Room schema v3 becomes the canonical store for integer-cent expenses. A pure JSON codec handles versioning and validation, while a repository coordinates transactional database replacement and non-secret preference restoration. Compose screens only coordinate document pickers, previews and confirmations.

**Tech Stack:** Kotlin, Room 2.6.1, kotlinx.serialization 1.6.3, Preferences DataStore, Jetpack Compose, JUnit 4, Truth.

---

### Task 1: Integer money and migration

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/model/Money.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/db/ExpenseEntity.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/db/AppDatabase.kt`
- Modify: all callers that construct, aggregate or format `ExpenseEntity`
- Create: `app/src/test/java/com/expense/tracker/data/model/MoneyTest.kt`
- Create: `app/src/androidTest/java/com/expense/tracker/data/db/AppDatabaseMigrationTest.kt`

- [ ] Write tests requiring deterministic yuan/cents conversion and v2-to-v3 preservation of amount, ID and `deletedAt`.
- [ ] Run the focused tests/compilation and confirm failure because cents APIs/schema v3 do not exist.
- [ ] Implement `amountCents: Long`, conversion helpers and `MIGRATION_2_3`; remove `fallbackToDestructiveMigration()`.
- [ ] Update persistence, analytics, UI formatting, CSV and LLM boundaries to use cents internally.
- [ ] Run focused tests and confirm they pass.

### Task 2: Complete backup codec and restore

**Files:**
- Replace: `app/src/main/java/com/expense/tracker/data/export/DataExporter.kt`
- Create: `app/src/main/java/com/expense/tracker/data/backup/BackupRepository.kt`
- Modify: `ExpenseDao.kt`, `ChatMessageDao.kt`, `UserPrefs.kt`, `AppContainer.kt`
- Create: `app/src/test/java/com/expense/tracker/data/export/DataExporterTest.kt`
- Create: `app/src/androidTest/java/com/expense/tracker/data/backup/BackupRepositoryTest.kt`

- [ ] Write failing codec tests for full round-trip, soft-deleted expenses, settings, legacy JSON and malformed backups.
- [ ] Add DAO replacement operations and an instrumentation test proving IDs and relationships survive restore.
- [ ] Run focused tests/compilation and confirm the expected missing-code failures.
- [ ] Implement a versioned codec with validation and a repository that replaces both Room tables inside `withTransaction` and then restores non-secret preferences.
- [ ] Run focused tests and confirm they pass.

### Task 3: Backup UI and permanent-delete confirmation

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/DataExportScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/history/DeletedItemsScreen.kt`

- [ ] Add a JSON document picker, parsed-backup preview and explicit replace confirmation; export all rows and settings and disclose that API Key is excluded.
- [ ] Change permanent delete so the first tap only opens an `AlertDialog`; call `purge` only from its destructive action.
- [ ] Compile the Android tests and app to catch Compose/API regressions.

### Task 4: Full verification

**Files:**
- Modify: existing unit and instrumentation tests that construct `ExpenseEntity`.

- [ ] Run `testDebugUnitTest` and require zero failures.
- [ ] Run `compileDebugAndroidTestKotlin` and require exit code 0.
- [ ] Run `assembleDebug` and require exit code 0.
- [ ] Inspect `git diff` and confirm every change traces to the four requested safety items.
