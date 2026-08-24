# AI Safety and Widget Reliability Design

## Scope

This release makes four focused changes without altering OCR:

1. AI updates apply immediately. Only destructive deletes wait for explicit user confirmation.
2. Deterministic source hints protect single-date batches as well as multi-date batches whenever every source amount can be matched to an explicit date.
3. Each source hint can carry an exact local time. Explicit times in the user's own text override model-supplied times; missing or ambiguous times continue to use existing fallback behavior.
4. The home-screen widget reads Room and DataStore on an IO coroutine obtained through `goAsync()`. App-triggered refreshes send an explicit widget-update broadcast instead of invoking the provider directly.

## Safety boundaries

- Additions and edits remain immediate; only deletes require confirmation.
- Client parsing controls only facts directly present in the text: amount, explicit date, and explicit clock time. Category and note remain model decisions.
- Time parsing is scoped between consecutive amount mentions so one expense cannot inherit the previous expense's time.
- The widget keeps its current layout and calculations; only its execution context changes.

## Verification

- Unit regression tests cover single-date cardinality, the reported nine-entry input, exact per-entry times, and update confirmation previews.
- A source contract test prevents `runBlocking` and direct provider construction from returning to widget refresh code.
- Full unit tests, Android test compilation, release lint, release build, APK alignment, signature, version, and SHA-256 are verified before delivery.
