# Learning Category and 30-Day AI Subscription Beta Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the 「学习与创作」 expense category and a 30-day redeemable AI test subscription that securely stores a tester-provided CloudBase credential without embedding any API Key in the APK.

**Architecture:** Keep `Category.ALL` as the single category source. Add a separate subscription preference/store with Keystore-protected credential storage, validate a redemption credential against the OpenAI-compatible CloudBase gateway, calculate a 30-day entitlement, and resolve subscription AI before the existing BYOK configuration. The test APK uses the CloudBase API Key itself as the high-entropy redemption credential because no deployable backend exists in this workspace; server-issued short codes remain a later compatible phase.

**Tech Stack:** Kotlin, Android Keystore AES-GCM, DataStore, OkHttp, kotlinx.serialization, Jetpack Compose, JUnit, Gradle, PowerShell APK contracts

---

## Frozen scope and delivery rules

- Never place the user-provided or any replacement CloudBase API Key in source, resources, tests, docs, BuildConfig, logs, command output, or APK assets.
- Preserve the existing dirty working tree; no reset, checkout, commit, or unrelated cleanup.
- Preserve OCR dependency `com.google.mlkit:text-recognition-chinese:16.0.0`, OCR flow, bundled models, and all four native ABIs.
- Preserve application ID, versionName 3.6, versionCode 27, minSdk 26, and the existing signing certificate.
- Deliver the final APK only to `C:\Users\fuker\Desktop\app\codex change` and refuse to overwrite existing artifacts.

## Task 1: Add the learning and creation category

**Files:**
- Modify: `app/src/test/java/com/expense/tracker/data/model/CategoryTest.kt`
- Modify: `app/src/test/java/com/expense/tracker/llm/LlmPromptContractTest.kt`
- Modify: `app/src/test/java/com/expense/tracker/llm/LlmResponseParserTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/model/Category.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmPrompt.kt`

- [x] Add a failing category test asserting `Category.byId("education")` has emoji `📚`, display name `学习与创作`, and `isInvestment == false`.
- [x] Add a failing parser test showing `category="education"` remains `education` rather than falling back to `other`.
- [x] Run the focused tests and verify failure because `education` is absent.
- [x] Add `Category("education", "📚", "学习与创作")` before `investment` in `Category.ALL`.
- [x] Add an explicit prompt rule mapping books, courses, electronic components, API/model calls, and cloud compute to `category=education`.
- [x] Run focused category, prompt, parser, CSV, and analytics tests and verify success.

## Task 2: Define deterministic 30-day entitlement behavior

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionModels.kt`
- Create: `app/src/test/java/com/expense/tracker/data/subscription/SubscriptionModelsTest.kt`

- [x] Add failing tests for inactive, active, expired, 30-day activation, active-period extension, and same-installation duplicate credential rejection.
- [x] Verify RED because the subscription models do not exist.
- [x] Implement `SubscriptionSnapshot`, `SubscriptionStatus`, `SubscriptionConfig`, and pure `SubscriptionPolicy.nextExpiry(now, currentExpiry)` using exactly `30 * 24 * 60 * 60 * 1000` milliseconds.
- [x] Implement SHA-256 credential fingerprints using lowercase hexadecimal output; never expose the credential from model `toString()`.
- [x] Verify GREEN with `SubscriptionModelsTest`.

## Task 3: Store subscription credentials with Android Keystore

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/data/prefs/AndroidKeystoreApiKeyStorage.kt`
- Create: `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionPrefs.kt`
- Create: `app/src/test/java/com/expense/tracker/data/subscription/SubscriptionPrefsTest.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `tools/test-api-key-backup-rules.ps1`

- [x] Add failing tests proving redemption persists encrypted credential through an injected storage interface, stores only fingerprint/expiry in DataStore, extends with a different credential, rejects a duplicate fingerprint, and clears expired credentials.
- [x] Verify RED because `SubscriptionPrefs` is absent.
- [x] Generalize the existing Keystore storage constructor to accept an alias and preference file while preserving current API Key defaults.
- [x] Implement a separate subscription DataStore and `secure_subscription_credential.xml` using alias `y_e_cost_subscription_credential_aes_v1`.
- [x] Add `secure_subscription_credential.xml` to Android 8-11 cloud backup and Android 12+ cloud/device-transfer exclusions.
- [x] Verify GREEN with focused tests and the backup contract.

## Task 4: Validate redemption and resolve AI access

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmClient.kt`
- Create: `app/src/main/java/com/expense/tracker/llm/AiAccessResolver.kt`
- Create: `app/src/test/java/com/expense/tracker/llm/AiAccessResolverTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/AppContainer.kt`

- [x] Add failing tests that an active subscription resolves to the fixed CloudBase Base URL and model `hy3`, an expired subscription falls back to configured BYOK, and no usable mode returns a clear configuration error.
- [x] Add a failing HTTP test using a fake OkHttp interceptor to prove credential validation sends only a minimal prompt and succeeds only on a valid Chat Completions response.
- [x] Verify RED because the resolver and validation API are absent.
- [x] Add `LlmClient.validateCredential(baseUrl, apiKey, model)` and return the HTTP `Date` time when present; never log or include the credential in errors.
- [x] Implement `AiAccessResolver` so subscription is preferred while valid and BYOK remains available when subscription is absent/expired.
- [x] Update chat, analytics, and bill-import handlers in `AppContainer` to resolve one `AiServiceConfig` before calling `LlmClient`.
- [x] Verify GREEN with focused resolver/client and existing LLM tests.

## Task 5: Add subscription UI and navigation

**Files:**
- Create: `app/src/main/java/com/expense/tracker/ui/subscription/SubscriptionViewModel.kt`
- Create: `app/src/main/java/com/expense/tracker/ui/subscription/SubscriptionScreen.kt`
- Create: `app/src/test/java/com/expense/tracker/ui/subscription/SubscriptionViewModelTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/settings/SettingsMenuScreen.kt`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`
- Modify: `tools/test-navigation-state.ps1`

- [x] Add failing ViewModel tests for successful 30-day status, invalid credential error, duplicate credential error, blank input, and automatic AI enablement.
- [x] Verify RED because the ViewModel does not exist.
- [x] Implement the ViewModel with injected validator, clock, and `SubscriptionPrefs`; trim only leading/trailing whitespace and clear the input after success.
- [x] Add an 「AI 会员」 settings row and a saveable `Subscription` sub-screen destination.
- [x] Build a screen that displays inactive/active/expired state, exact expiry date, password-style credential input, 「兑换 30 天」 button, renewal guidance, and an explicit test-only security notice.
- [x] Extend the navigation contract and verify it remains saveable.
- [x] Run focused tests and Kotlin compilation.

## Task 6: Security, regression, and APK delivery

**Files:**
- Create: `tools/test-subscription-secrets.ps1`
- Modify: `docs/superpowers/specs/2026-08-01-learning-creation-category-design.md`
- Modify: `docs/superpowers/specs/2026-08-02-ai-subscription-redemption-design.md`
- Verify all production/test sources, XML, plans, scripts, and the Release APK.

- [x] Add a static contract that rejects JWT-shaped credential literals and embedded bearer-JWT values from source, docs, tools, and APK entries while allowing the non-secret gateway URL.
- [x] Run privacy-log, backup, navigation, splash, and subscription-secret contracts.
- [x] Run `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `lintRelease`, and `assembleRelease` with the Android Studio JBR.
- [x] Confirm the built APK contains the new category text, contains no CloudBase credential, retains the Chinese OCR models and four OCR native ABIs.
- [x] Align and sign a uniquely named Release APK with the existing certificate; verify package/version/SDK, V2/V3 signatures, certificate SHA-256, alignment, size, and APK SHA-256.
- [x] Record that real credential redemption and Android 8-13 physical-device behavior remain unverified because no physical device was attached.
- [x] Deliver the APK and implementation notes, then schedule the user-requested Windows shutdown only after all verification output has been captured.
