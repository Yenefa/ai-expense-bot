# Y.E cost One-Time Redemption Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy a CloudBase-backed one-time redemption service, generate ten unused 30-day codes, migrate Y.E cost Android from direct API-key redemption to server-issued access tokens, and publish a verified same-signature APK.

**Architecture:** A Node.js 20 HTTP cloud function keeps only SHA-256 hashes of available codes and random subscription tokens in CloudBase NoSQL. Redemption atomically deletes the available-code document, writes an audit receipt and token record, while AI requests validate the opaque token and use the cloud function's same-environment identity to call the CloudBase `hy3` model. Android stores the installation ID in DataStore, stores the access token with the existing Keystore-backed storage, and keeps BYOK as fallback.

**Tech Stack:** Node.js 20.19, `@cloudbase/node-sdk` 3.18.3, Node test runner, CloudBase HTTP cloud functions and NoSQL transactions, Kotlin 1.9.22, OkHttp 4.12, kotlinx.serialization 1.6.3, DataStore, Android Keystore, Jetpack Compose, JUnit 4, Gradle, Android SDK Build Tools, CloudBase static hosting

---

## Workspace and release constraints

- Android root: `C:\Users\fuker\Desktop\app\account app\expense-tracker`.
- CloudBase/site worktree: `C:\Users\fuker\Desktop\app\codex change\ye-cost-site\.worktrees\public-download-site`.
- The Android tree contains many valid uncommitted changes. Never use reset, checkout, clean, or broad replacement. Stage and commit only files owned by each task.
- The site worktree is already isolated on `feature/public-download-site`; add the cloud function there.
- Preserve application ID `com.expense.tracker`, versionName `3.6`, versionCode `27`, minSdk 26, OCR dependency `com.google.mlkit:text-recognition-chinese:16.0.0`, Chinese OCR models, and four native ABIs.
- Do not place CloudBase API keys, plaintext redemption codes, access tokens, or administrator credentials in Git, Gradle source, APK resources, website assets, tests, logs, or command transcripts.
- Use CloudBase Node SDK same-environment identity for `hy3`; the previously exposed API key is only allowed transiently for CLI authentication and must be removed from the process environment followed by `tcb logout`.
- Final APK path: `C:\Users\fuker\Desktop\app\codex change\ai-expense-bot-v3.6-server-one-time-redemption-2026-08-02.apk`. Refuse to overwrite it if it already exists.
- Existing signing certificate SHA-256 must remain `e5ac68a1544da24122eee453eebc758286644940167fce1a72e983455b3c2b5c`.

## File map

### CloudBase/site worktree

- `cloudfunctions/ye-cost-api/src/security.cjs`: code normalization, hashing, random token generation.
- `cloudfunctions/ye-cost-api/src/redemption-service.cjs`: framework-independent redemption, access validation, and renewal rules.
- `cloudfunctions/ye-cost-api/src/cloudbase-store.cjs`: CloudBase transaction and token lookup adapter.
- `cloudfunctions/ye-cost-api/src/cloudbase-ai.cjs`: same-environment `hy3` call.
- `cloudfunctions/ye-cost-api/src/http-app.cjs`: HTTP parsing, stable JSON errors, routes, request limits.
- `cloudfunctions/ye-cost-api/index.cjs`: CloudBase initialization and port 9000 server bootstrap.
- `cloudfunctions/ye-cost-api/scf_bootstrap`: Linux startup script with LF endings and executable bit.
- `cloudfunctions/ye-cost-api/scripts/generate-codes.cjs`: generate ten codes, write only hashes to import JSON, print plaintext once to ignored administrator file.
- `cloudfunctions/ye-cost-api/scripts/import-codes.cjs`: import hashed codes using transient local CloudBase authentication.
- `cloudfunctions/ye-cost-api/test/*.test.cjs`: service, transaction adapter, HTTP, AI adapter, and code generator tests.
- `cloudbaserc.json`: register the HTTP function without secret environment variables.
- `.gitignore`: ignore `admin-output/` and local redemption artifacts.

### Android root

- `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionModels.kt`: endpoint-independent token and subscription models.
- `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionApiClient.kt`: redeem/status HTTP client and DTOs.
- `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionPrefs.kt`: installation ID, Keystore token persistence, legacy direct-key invalidation.
- `app/src/main/java/com/expense/tracker/ui/subscription/SubscriptionViewModel.kt`: call server redemption and display stable errors.
- `app/src/main/java/com/expense/tracker/llm/AiAccessResolver.kt`: route active subscription through proxy while retaining BYOK.
- `app/src/main/java/com/expense/tracker/llm/LlmClient.kt`: optional installation header and safe HTTP error mapping.
- `app/src/main/java/com/expense/tracker/AppContainer.kt`: pass proxy configuration through chat, analytics, and bill import.
- `app/src/main/java/com/expense/tracker/MainActivity.kt`: inject `SubscriptionApiClient` into the subscription ViewModel.
- `app/build.gradle.kts`: inject the deployed API base URL through a Gradle property and fail closed for Release when absent.
- `app/src/test/java/com/expense/tracker/data/subscription/*.kt`, `app/src/test/java/com/expense/tracker/llm/*.kt`, `app/src/test/java/com/expense/tracker/ui/subscription/*.kt`: Android TDD coverage.
- `tools/test-subscription-secrets.ps1`: reject direct gateway keys/tokens and verify the proxy contract.

## Task 1: Build security primitives and code generator

**Files:**
- Create: `cloudfunctions/ye-cost-api/package.json`
- Create: `cloudfunctions/ye-cost-api/src/security.cjs`
- Create: `cloudfunctions/ye-cost-api/scripts/generate-codes.cjs`
- Create: `cloudfunctions/ye-cost-api/scripts/import-codes.cjs`
- Create: `cloudfunctions/ye-cost-api/test/security.test.cjs`
- Create: `cloudfunctions/ye-cost-api/test/generate-codes.test.cjs`
- Create: `cloudfunctions/ye-cost-api/test/import-codes.test.cjs`
- Modify: `.gitignore`

- [ ] **Step 1: Add failing tests for normalization, hashes, randomness, and safe output**

```js
test("normalizes a human-entered code without weakening entropy", () => {
  assert.equal(normalizeCode("  ye30-abcd-2345-wxyz  "), "YE30-ABCD-2345-WXYZ");
});

test("generates ten unique codes while import data contains hashes only", () => {
  const batch = generateBatch({ count: 10, batchId: "friends-2026-08-02" });
  assert.equal(new Set(batch.codes).size, 10);
  assert.equal(batch.documents.length, 10);
  assert.equal(JSON.stringify(batch.documents).includes("YE30-"), false);
});
```

- [ ] **Step 2: Run tests and verify RED**

Run: `npm.cmd test --prefix cloudfunctions/ye-cost-api`

Expected: FAIL because the modules do not exist.

- [ ] **Step 3: Implement minimal primitives**

```js
const { createHash, randomBytes } = require("node:crypto");

function normalizeCode(value) {
  return String(value).trim().toUpperCase();
}

function sha256Hex(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function newAccessToken() {
  return randomBytes(32).toString("base64url");
}

function newRedemptionCode() {
  const body = randomBytes(9).toString("base64url").toUpperCase().replace(/[-_]/g, "A").slice(0, 12);
  return `YE30-${body.slice(0, 4)}-${body.slice(4, 8)}-${body.slice(8, 12)}`;
}
```

`generate-codes.cjs` must expose `generateBatch({count, batchId, now})`; its CLI must refuse any count other than `10`, write plaintext to `admin-output/ye-cost-redemption-codes-2026-08-02.txt`, write hashed documents to `admin-output/ye-cost-redemption-import-2026-08-02.json`, and refuse to overwrite either file.

`import-codes.cjs` must expose `validateImportDocuments(documents)` and `importDocuments({documents, writeDocument})`. It must reject any document ID that is not 64 lowercase hexadecimal characters, reject any value containing `YE30-`, and call the injected writer once per unique hash. The CLI path initializes `@cloudbase/node-sdk` only from `CLOUDBASE_ENV_ID` and transient `CLOUDBASE_APIKEY` process variables.

- [ ] **Step 4: Verify GREEN and secret exclusions**

Run: `npm.cmd test --prefix cloudfunctions/ye-cost-api`

Expected: all Task 1 tests pass, and `git status --short` does not show `admin-output/`.

- [ ] **Step 5: Commit only Task 1 files in the site worktree**

```powershell
git add -- .gitignore cloudfunctions/ye-cost-api/package.json cloudfunctions/ye-cost-api/src/security.cjs cloudfunctions/ye-cost-api/scripts/generate-codes.cjs cloudfunctions/ye-cost-api/scripts/import-codes.cjs cloudfunctions/ye-cost-api/test/security.test.cjs cloudfunctions/ye-cost-api/test/generate-codes.test.cjs cloudfunctions/ye-cost-api/test/import-codes.test.cjs
git commit -m "feat: generate secure one-time redemption codes"
```

## Task 2: Implement atomic redemption and token validation

**Files:**
- Create: `cloudfunctions/ye-cost-api/src/redemption-service.cjs`
- Create: `cloudfunctions/ye-cost-api/test/redemption-service.test.cjs`

- [ ] **Step 1: Write failing service tests**

Cover these exact cases with an in-memory store implementing the production interface:

```js
test("first redemption deletes the available code and grants thirty days", async () => {});
test("two installations racing for one code produce exactly one success", async () => {});
test("same installation retry returns the original expiry without extension", async () => {});
test("different installation cannot reuse a consumed code", async () => {});
test("a valid current token extends from the current expiry", async () => {});
test("expired, revoked, malformed, and wrong-installation tokens are rejected", async () => {});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `node --test cloudfunctions/ye-cost-api/test/redemption-service.test.cjs`

Expected: FAIL because `createRedemptionService` does not exist.

- [ ] **Step 3: Implement the store boundary and service**

```js
const DURATION_MILLIS = 30 * 24 * 60 * 60 * 1000;

function createRedemptionService({ store, clock = Date.now, tokenFactory = newAccessToken }) {
  return {
    async redeem({ code, installationId, currentToken }) {
      const normalized = normalizeCode(code);
      if (!/^YE30-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(normalized)) {
        throw serviceError("CODE_INVALID", 400);
      }
      const installationIdHash = sha256Hex(installationId);
      const current = currentToken
        ? await store.findActiveToken(sha256Hex(currentToken), installationIdHash, clock())
        : null;
      const token = tokenFactory();
      const result = await store.consumeCode({
        codeHash: sha256Hex(normalized),
        installationIdHash,
        tokenHash: sha256Hex(token),
        nowMillis: clock(),
        currentExpiryMillis: current?.expiresAtMillis ?? 0,
        durationMillis: DURATION_MILLIS,
      });
      return { token, expiresAtMillis: result.expiresAtMillis };
    },
    async authorize({ token, installationId }) {
      return store.findActiveToken(sha256Hex(token), sha256Hex(installationId), clock());
    },
  };
}
```

The service must trim but never log inputs, use stable internal error codes, and issue a fresh random token on same-installation retry while preserving the original expiry.

- [ ] **Step 4: Run focused and full backend tests**

Run: `npm.cmd test --prefix cloudfunctions/ye-cost-api`

Expected: all tests pass with zero warnings.

- [ ] **Step 5: Commit Task 2**

```powershell
git add -- cloudfunctions/ye-cost-api/src/redemption-service.cjs cloudfunctions/ye-cost-api/test/redemption-service.test.cjs
git commit -m "feat: enforce atomic redemption rules"
```

## Task 3: Add CloudBase transaction, HTTP API, and same-environment AI

**Files:**
- Create: `cloudfunctions/ye-cost-api/src/cloudbase-store.cjs`
- Create: `cloudfunctions/ye-cost-api/src/cloudbase-ai.cjs`
- Create: `cloudfunctions/ye-cost-api/src/http-app.cjs`
- Create: `cloudfunctions/ye-cost-api/index.cjs`
- Create: `cloudfunctions/ye-cost-api/scf_bootstrap`
- Create: `cloudfunctions/ye-cost-api/test/cloudbase-store.test.cjs`
- Create: `cloudfunctions/ye-cost-api/test/http-app.test.cjs`
- Create: `cloudfunctions/ye-cost-api/test/cloudbase-ai.test.cjs`
- Modify: `cloudfunctions/ye-cost-api/package.json`
- Modify: `cloudbaserc.json`

- [ ] **Step 1: Add failing adapter and HTTP tests**

Test transaction ordering, write-conflict retry, code deletion, receipt creation, token hashing, health, redeem, status, chat authorization, body limit, method rejection, and privacy-safe errors. The AI adapter test must assert:

```js
assert.deepEqual(captured, {
  provider: "cloudbase",
  model: "hy3",
  messages: request.messages,
});
assert.equal(response.choices[0].message.content, "server result");
```

- [ ] **Step 2: Verify RED**

Run: `node --test cloudfunctions/ye-cost-api/test/cloudbase-store.test.cjs cloudfunctions/ye-cost-api/test/http-app.test.cjs cloudfunctions/ye-cost-api/test/cloudbase-ai.test.cjs`

Expected: FAIL because the adapters and handler do not exist.

- [ ] **Step 3: Implement the CloudBase adapter**

Use `db.runTransaction(callback, 3)`. Inside one callback, read `available_redemption_codes/<codeHash>` and `redemption_receipts/<codeHash>` by document ID. On first use, compute `expiresAtMillis = max(nowMillis, currentExpiryMillis) + durationMillis`, delete the available document, set the receipt, and set `subscription_tokens/<tokenHash>`. If the available document is absent and the receipt installation hash matches, set only the new token document using the receipt expiry. Return `CODE_INVALID_OR_USED` for every other absent-code case.

Token authorization reads `subscription_tokens/<tokenHash>` and returns active only when `installationIdHash` matches, `revokedAt` is absent, and `expiresAtMillis > nowMillis`. `consumeAiQuota()` updates minute/day counters on that same token document in a transaction and rejects more than 5 AI calls per minute or 50 per day with `AI_RATE_LIMITED`.

- [ ] **Step 4: Implement HTTP and AI adapters**

```js
const cloudbase = require("@cloudbase/node-sdk");
const app = cloudbase.init({ timeout: 60000 });
const db = app.database();
const model = app.ai().createModel("cloudbase");

async function generate(messages) {
  const result = await model.generateText({ model: "hy3", messages });
  if (!result.text) throw serviceError("AI_UPSTREAM_FAILED", 502);
  return result.text;
}
```

Routes:

- `GET /health` → `{status:"ok", version:"1"}`.
- `POST /v1/subscriptions/redeem` → `{status:"ACTIVE", accessToken, expiresAtMillis}`.
- `GET /v1/subscriptions/status` → `{status:"ACTIVE", expiresAtMillis}` or `401`.
- `POST /v1/chat/completions` → OpenAI-compatible non-streaming response, forced `hy3`.

Require `Authorization: Bearer <token>` and `X-YE-Cost-Installation-Id` for status/chat. The chat route must authorize and consume quota before calling AI. Cap JSON request bodies at 64 KiB and total message text at 20,000 characters. Release logs may contain only route, status code, latency bucket, and stable error code.

- [ ] **Step 5: Configure the HTTP function**

Append to `cloudbaserc.json`:

```json
"functionRoot": "./cloudfunctions",
"functions": [
  {
    "name": "ye-cost-api",
    "dir": "./cloudfunctions/ye-cost-api",
    "type": "HTTP",
    "runtime": "Nodejs20.19",
    "timeout": 70,
    "memorySize": 256,
    "installDependency": true
  }
]
```

`scf_bootstrap` must contain exactly LF-terminated `#!/bin/bash\nnode index.cjs\n`, and Git must record its executable bit.

- [ ] **Step 6: Verify GREEN, LF endings, and no secrets**

Run:

```powershell
npm.cmd test --prefix cloudfunctions/ye-cost-api
git diff --check
rg -n "eyJ[a-zA-Z0-9_-]+\.|Bearer eyJ|CLOUDBASE_APIKEY\s*=|api\.tcloudbasegateway\.com/v1/ai" cloudfunctions cloudbaserc.json
```

Expected: backend tests pass; diff check passes; secret/direct-gateway scan returns no matches.

- [ ] **Step 7: Commit Task 3 in the site worktree**

```powershell
git add -- cloudfunctions/ye-cost-api cloudbaserc.json
git commit -m "feat: add CloudBase redemption and AI proxy"
```

## Task 4: Implement Android server redemption client

**Files:**
- Create: `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionApiClient.kt`
- Create: `app/src/test/java/com/expense/tracker/data/subscription/SubscriptionApiClientTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionModels.kt`

- [ ] **Step 1: Add failing HTTP contract tests**

Use a real OkHttp interceptor that captures requests and returns fixture JSON. Assert normalization, request ID, installation ID, current-token omission/presence, successful response parsing, `CODE_INVALID_OR_USED`, `401`, malformed body, and absence of response-body leakage in errors.

- [ ] **Step 2: Verify RED**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.expense.tracker.data.subscription.SubscriptionApiClientTest"`

Expected: Kotlin compilation fails because `SubscriptionApiClient` is absent.

- [ ] **Step 3: Add DTOs and client**

```kotlin
@Serializable
data class RedeemRequest(
    val redemptionCode: String,
    val installationId: String,
    val requestId: String,
    val currentAccessToken: String? = null,
)

@Serializable
data class RedeemResponse(
    val status: String,
    val accessToken: String,
    val expiresAtMillis: Long,
)
```

`SubscriptionApiClient.redeem()` posts to `baseUrl.trimEnd('/') + "/v1/subscriptions/redeem"`; `status()` sends the bearer token and installation header. Map stable server codes to typed `SubscriptionApiException` without storing or printing code/token text.

- [ ] **Step 4: Inject deployed endpoint through Gradle**

Add a `YE_COST_SUBSCRIPTION_API_BASE_URL` Gradle property backed by an empty Debug fallback and a Release configuration check. Expose it only as `BuildConfig.SUBSCRIPTION_API_BASE_URL`; it is a public endpoint, not a secret. Unit tests inject their own base URL.

- [ ] **Step 5: Verify GREEN**

Run focused tests and `compileDebugKotlin` with `-PYE_COST_SUBSCRIPTION_API_BASE_URL=https://invalid.local`.

- [ ] **Step 6: Do not commit unrelated Android changes**

The Android tree is dirty. Stage only the four Task 4 files if a commit is made; otherwise preserve them as part of the existing user-owned working tree and record the exact diff.

## Task 5: Migrate subscription storage from direct CloudBase key to opaque token

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionPrefs.kt`
- Modify: `app/src/main/java/com/expense/tracker/data/subscription/SubscriptionModels.kt`
- Modify: `app/src/test/java/com/expense/tracker/data/subscription/SubscriptionPrefsTest.kt`
- Modify: `app/src/test/java/com/expense/tracker/data/subscription/SubscriptionModelsTest.kt`

- [ ] **Step 1: Add failing migration and installation-ID tests**

Cover stable UUID creation, token activation, encrypted-token read, expiry, clear, backup-safe metadata, and upgrade from legacy direct API-key storage. Legacy storage without `TOKEN_FORMAT_VERSION = 2` must clear the old credential and expiry instead of treating it as a server token.

- [ ] **Step 2: Verify RED with focused tests**

Run the two subscription test classes and confirm expected assertion failures.

- [ ] **Step 3: Implement token-specific storage**

Add `INSTALLATION_ID`, `TOKEN_FORMAT_VERSION`, and version `2`. Provide:

```kotlin
suspend fun installationId(): String
suspend fun currentToken(): String?
suspend fun activateServerToken(token: String, expiresAtMillis: Long): SubscriptionSnapshot
suspend fun clearSubscription()
suspend fun activeAccess(nowMillis: Long = clock()): SubscriptionAccess?
```

Keep the existing Keystore alias and backup exclusions so upgrades retain encrypted storage mechanics. Remove local `REDEEMED_FINGERPRINTS` enforcement because global deletion now lives on the server. `SubscriptionAccess.toString()` must continue redacting the token.

- [ ] **Step 4: Verify GREEN and backup contracts**

Run focused unit tests and `tools/test-api-key-backup-rules.ps1`.

## Task 6: Update subscription UI and AI routing

**Files:**
- Modify: `app/src/main/java/com/expense/tracker/ui/subscription/SubscriptionViewModel.kt`
- Modify: `app/src/test/java/com/expense/tracker/ui/subscription/SubscriptionViewModelTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/AiAccessResolver.kt`
- Modify: `app/src/test/java/com/expense/tracker/llm/AiAccessResolverTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/llm/LlmClient.kt`
- Modify: `app/src/test/java/com/expense/tracker/llm/LlmClientCredentialValidationTest.kt`
- Modify: `app/src/main/java/com/expense/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/expense/tracker/MainActivity.kt`
- Modify: `app/src/main/java/com/expense/tracker/ui/subscription/SubscriptionScreen.kt`

- [ ] **Step 1: Add failing ViewModel and routing tests**

Assert server redemption activates the exact server expiry, clears input, preserves input on network failure, maps invalid/used code to `兑换码无效或已使用`, ignores duplicate taps, enables AI, and sends subscription token plus installation header to the proxy. Assert ViewModel initialization checks server status when a token exists, clears local subscription on `401`, and keeps local state on temporary network failure. Assert BYOK still uses its original URL/key with no installation header.

- [ ] **Step 2: Verify RED**

Run the three focused test classes and confirm failures occur because the old validator still treats the input as a CloudBase API key.

- [ ] **Step 3: Replace direct-key redemption**

Inject `redeemer: suspend (code, installationId, currentToken) -> RedeemResponse`. On success call `prefs.activateServerToken(response.accessToken, response.expiresAtMillis)`. Remove `LlmClient.validateCredential()` and old credential-validation DTO/tests.

- [ ] **Step 4: Route subscription AI through the server**

Extend `AiServiceConfig` with optional `installationId`. Subscription resolution returns `BuildConfig.SUBSCRIPTION_API_BASE_URL`, the opaque token, model `hy3`, and installation ID. `LlmClient.chatJson()` adds `X-YE-Cost-Installation-Id` only when non-null. All three AppContainer call sites pass it; BYOK remains unchanged. Map proxy `401` to a typed subscription-unauthorized exception; AppContainer clears the local subscription before returning the safe retry/renewal message.

- [ ] **Step 5: Update user-facing copy**

Replace the test note that says the administrator sends a CloudBase credential with: `每个兑换码只能使用一次；兑换成功后增加 30 天 AI 会员。AI 功能需要联网。` Do not expose implementation details or call the code an API Key.

- [ ] **Step 6: Verify GREEN and Android regression tests**

Run focused tests, then `testDebugUnitTest` with the public API URL Gradle property.

## Task 7: Strengthen static security contracts

**Files:**
- Modify: `tools/test-subscription-secrets.ps1`
- Modify: `app/src/main/res/xml/backup_rules.xml` only if the existing token cipher exclusion is no longer exact
- Modify: `app/src/main/res/xml/data_extraction_rules.xml` only if the existing token cipher exclusion is no longer exact

- [ ] **Step 1: Make the contract fail on the current direct-gateway architecture**

Add checks that production Android files contain no `api.tcloudbasegateway.com/v1/ai`, JWT literals, `validateCredential`, or copy calling redemption input an API Key. Assert Release uses `BuildConfig.SUBSCRIPTION_API_BASE_URL`, subscription token storage remains backup-excluded, and OCR dependency remains present.

- [ ] **Step 2: Run and verify RED before finishing Android changes**

Run: `powershell -ExecutionPolicy Bypass -File tools/test-subscription-secrets.ps1`

Expected: FAIL against the old direct-gateway code.

- [ ] **Step 3: Complete minimal production changes and verify GREEN**

Re-run the contract, privacy log contract, backup contract, navigation contract, and OCR dependency scan. All must exit zero.

## Task 8: Deploy CloudBase, create collections, and import ten codes

**Files:**
- Modify only generated ignored files under `cloudfunctions/ye-cost-api/admin-output/`
- No secrets or plaintext codes may be added to Git

- [ ] **Step 1: Verify backend locally before cloud mutation**

Run `npm.cmd ci --prefix cloudfunctions/ye-cost-api`, backend tests, site tests, site lint, `git diff --check`, and secret scans.

- [ ] **Step 2: Authenticate transiently**

Use the CloudBase API key only as an in-process environment value or CLI login argument, never in a script file. Confirm environment `ilove-d5g0gzrpp375112b9`, then remove the environment variable immediately after authentication.

- [ ] **Step 3: Deploy HTTP function and create the `/ye-cost-api` path**

Run from the site worktree:

```powershell
tcb.cmd fn deploy ye-cost-api --httpFn --path /ye-cost-api --runtime Nodejs20.19 --json
tcb.cmd fn list -e ilove-d5g0gzrpp375112b9 --json
```

Capture the actual HTTPS function base URL from the CLI/HTTP access service response. Probe `/health` until it returns `200` and `{status:"ok"}`. The default CloudBase domain is acceptable only for this friend-test release.

- [ ] **Step 4: Create database collections safely**

Ensure `available_redemption_codes`, `redemption_receipts`, and `subscription_tokens` exist. Never delete or rename unrelated collections. Confirm the function can read/write all three using a dedicated online self-test that removes its own `SELFTEST-` documents.

- [ ] **Step 5: Generate and import ten codes**

Run the generator exactly once. Verify ten unique plaintext codes, ten unique lowercase SHA-256 document IDs, no plaintext in the import JSON, and no tracked output. Import the ten hashes and query the collection count. Do not redeem any of the ten delivery codes.

- [ ] **Step 6: Online negative and AI tests**

Verify an invalid code fails, missing token fails status/chat, and direct `/health` succeeds. Use a separately generated self-test code to redeem, confirm its available document is deleted, retry from the same installation returns the same expiry, another installation fails, and its token successfully calls `hy3`. Delete only the self-test receipt/token afterward.

- [ ] **Step 7: Logout and verify no credentials remain**

Run `tcb.cmd logout --json`, remove any task-specific environment variables, and scan tracked/untracked source plus command-created files for JWT/API-key patterns.

## Task 9: Full Android verification and same-signature APK

**Files:**
- Build output only under `app/build/`
- Create final APK only at the frozen delivery path

- [ ] **Step 1: Run the complete verification matrix with the deployed public API URL**

Resolve Android Studio JBR, set a task-specific Gradle property, and run:

```powershell
.\gradlew.bat testDebugUnitTest -PYE_COST_SUBSCRIPTION_API_BASE_URL=$apiBase
.\gradlew.bat compileDebugAndroidTestKotlin -PYE_COST_SUBSCRIPTION_API_BASE_URL=$apiBase
.\gradlew.bat lintRelease -PYE_COST_SUBSCRIPTION_API_BASE_URL=$apiBase
.\gradlew.bat assembleRelease -PYE_COST_SUBSCRIPTION_API_BASE_URL=$apiBase
```

Also run every PowerShell contract under `tools/` relevant to privacy, backup, navigation, splash, subscription secrets, and OCR.

- [ ] **Step 2: Verify APK contents before signing**

Confirm package/version/SDK, embedded API base URL, absence of CloudBase API keys/JWT plaintext/direct AI gateway URL, presence of `学习与创作`, new redemption copy, Chinese OCR model files, and all four OCR ABIs.

- [ ] **Step 3: Align and sign without overwriting**

Use the newest installed `zipalign` and `apksigner` with the existing debug keystore. Verify V2/V3 signatures, certificate SHA-256, alignment, and package metadata. Refuse to proceed if the certificate differs from the frozen fingerprint.

- [ ] **Step 4: Record artifact evidence**

Print the final absolute path, byte size, SHA-256, certificate SHA-256, versionName, versionCode, minSdk, targetSdk, and signature schemes. Record that no physical Android device test was performed unless a device is actually attached.

## Task 10: Update website and publish the new APK

**Files:**
- Modify: `app/page.tsx`
- Modify: `scripts/build-cloudbase-static.mjs`
- Modify: `tests/site-contract.test.mjs`
- Modify: `tests/rendered-html.test.mjs`
- Modify: `tests/cloudbase-static.test.mjs`
- Modify: website release notes/README only where they name the previous APK

- [ ] **Step 1: Make site tests expect the new artifact name, size, and SHA-256**

Use the exact evidence from Task 9. Run tests and verify RED against the old constants.

- [ ] **Step 2: Update site release metadata and static builder**

Set the filename to `ai-expense-bot-v3.6-server-one-time-redemption-2026-08-02.apk`, update byte size/SHA-256, and retain the CloudBase `/ye-cost/downloads/` rewrite. Update copy to mention global one-time 30-day codes without claiming device tracking or offline AI.

- [ ] **Step 3: Verify site and deploy isolated static hosting**

Run `npm.cmd test`, `npm.cmd run lint`, `npm.cmd run build:cloudbase` with `YE_COST_APK_PATH` set to the final APK, then deploy `cloudbase-dist` to `ye-cost`. Verify page, JS, CSS, icon, and APK return `200`, APK `Content-Type` and `Content-Length` match, and downloaded SHA-256 equals the local artifact.

- [ ] **Step 4: Commit only website/backend release changes**

Review `git diff --check`, secret scans, and `git status`. Commit the site worktree changes without merging or deleting the worktree.

- [ ] **Step 5: Deliver ten codes and release report**

Give the user the ten plaintext codes, CloudBase website URL, direct APK URL, local APK path, SHA-256, signing fingerprint, online API verification summary, and limitations. Do not print access tokens or CloudBase credentials. Do not schedule shutdown unless the user explicitly requests shutdown again in this task.

## Official references

- HTTP cloud functions require port 9000 and an LF/executable `scf_bootstrap`: `https://docs.cloudbase.net/en/cloud-function/quickstart/httpfunc/nodejs`.
- CloudBase CLI HTTP function deployment and `--path`: `https://docs.cloudbase.net/cli-v1/functions/deploy`.
- Server-side NoSQL transactions and ACID semantics: `https://docs.cloudbase.net/database/transaction`.
- Same-environment Node SDK AI call with provider `cloudbase` and model `hy3`: `https://docs.cloudbase.net/api-reference/server/node-sdk/ai`.
- Default HTTP access domains are for development/test use: `https://cloud.tencent.com/document/product/876/122894`.
