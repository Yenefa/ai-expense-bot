# Data Safety Phase 1 Design

## Scope

Only four changes are in scope:

1. Remove destructive Room migration fallback.
2. Store monetary amounts as integer cents.
3. Make JSON backups complete and restorable.
4. Require confirmation before permanent deletion.

## Database and money

`AppDatabase` moves from schema version 2 to 3. `ExpenseEntity.amount: Double` is replaced by `amountCents: Long`. `MIGRATION_2_3` creates a replacement `expenses` table, copies every row with `CAST(ROUND(amount * 100.0) AS INTEGER)`, preserves IDs and `deletedAt`, then replaces the old table. The builder registers both migrations and has no destructive fallback.

Yuan values remain `Double` only at existing UI, CSV and LLM boundaries. Conversion to cents happens before persistence; calculations and deduplication use cents. Formatting converts cents back to a two-decimal yuan string.

## Backup and restore

The new JSON envelope has a format version independent from the app version. It contains:

- every expense, including soft-deleted rows and original IDs;
- every chat message and its original ID/expense relationship;
- non-secret settings: LLM enabled state, base URL, model and theme;
- export timestamp and source app version.

The API key is deliberately excluded because the backup is a plain JSON file. The UI states this explicitly.

Restore parses and validates the whole file before changing local state. It rejects unsupported versions, duplicate IDs, invalid money/timestamps, invalid roles/themes, and malformed JSON. After a preview, the user confirms that current data will be replaced. Room tables are replaced in one transaction while preserving IDs; settings are restored after the database transaction. Legacy JSON produced by the existing exporter is accepted with `deletedAt = null` and default settings.

## Permanent deletion

Tapping permanent delete selects the item but does not delete it. An `AlertDialog` names the record, explains that it cannot be recovered, and offers Cancel or Permanently delete. Only the destructive confirmation calls `repo.purge(id)`.

## Verification

- Unit tests cover cents conversion, new backup round-trip, deleted rows, settings, legacy JSON and validation failures.
- Room migration instrumentation tests verify v2 real amounts become v3 integer cents without losing IDs or deletion state.
- Existing tests are updated to assert cents persistence while existing UI-facing behavior remains unchanged.
- The full unit test suite, Android test compilation and debug APK build must pass.
