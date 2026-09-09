# Room schema history — `com.tbdfit.phone.localstorage.AppDatabase`

This directory is committed version control, not generated/ignored output — see
`docs/architecture/strength-workout-first-slice-design.md`'s PERSISTENCE/MIGRATION POLICY. Do not
delete or regenerate historical entries; each `<version>.json` is the record every future migration
is checked against, and `MigrationTestHelper` reads them directly to construct real "old version"
databases in tests.

## `3.json` — an exceptional historical reconstruction

Version 3 predates this database's durable-data transition: `exportSchema` was `false` at the
time, so no schema was ever actually exported when v3 shipped. `3.json` in this directory was
generated **retroactively**, during the transition to v4, by temporarily reverting the `@Database`
annotation to its original v3 entity set with `exportSchema = true`, running the Room/KSP schema
export once, and capturing the result — then restoring the real v4 database code. It is a faithful
reconstruction of what v3's schema actually was (verified against the unedited v3 entity source),
not a schema captured live at the time v3 was originally shipped.

This is documented here explicitly so it is never mistaken for an ordinary, normally-generated
schema snapshot, and so nobody "corrects" it by regenerating it against current entity code (which
would silently corrupt it, since the current entities are the v4 set, not v3's).

## The rule going forward

**Every version from 4 onward must have its schema JSON generated normally, at the time that
version is introduced** — i.e., by writing the new `@Database(version = N, exportSchema = true, ...)`
and letting KSP export `N.json` as part of that same change, never reconstructed after the fact.
`4.json`, `5.json`, and `6.json` all follow this rule: each was generated directly from the real,
final entity set at the time. `6.json` in particular reflects the local-account-ownership
correction: `local_accounts` is new, and `workouts.ownerId` / `exercises.ownerId` are real foreign
keys against it rather than free-form strings — see `MIGRATION_5_6` in `AppDatabase.kt` for how an
existing column got a foreign key added via SQLite's create-copy-drop-rename technique (SQLite has
no `ALTER TABLE ADD CONSTRAINT`).

`fallbackToDestructiveMigration(...)` must remain absent from `AppDatabase.build()` permanently —
see the design doc for why its removal is the load-bearing property of this whole transition (a
missing migration must fail loudly, not silently destroy data).
