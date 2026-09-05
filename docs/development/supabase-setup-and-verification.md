# Supabase Setup and Verification

This is an operational runbook: how to stand up a Supabase project this codebase can talk to, how
to configure a client against it, and how to verify it actually works. It is not an architecture
document — for the backend strategy, migration model, and exit-path reasoning, see
[ADR-004](../architecture/adr/0004-initial-backend-platform-and-migration-strategy.md).

## Architecture context

- Supabase is the current shared backend platform for this project (ADR-004) — not the permanent
  application architecture.
- `supabase/migrations/` at the repository root is the source of truth for the versioned remote
  schema. It is not owned by any one client.
- Each native client has its own Supabase implementation, confined below a purpose-scoped
  capability boundary (see `CLAUDE.md`'s Supabase boundary rule and ADR-004). Android's
  implementation is documented here as the current example; a future Apple implementation would
  follow the same setup against the same backend, with its own native Supabase SDK usage.
- This document covers *setup and verification*, not *why Supabase* or *how migration to a custom
  API would work* — that's ADR-004's job.

## Fresh Supabase project setup

Steps to stand up a new Supabase project this codebase can use, in the order we actually used them:

1. Create or open a Supabase project at [supabase.com](https://supabase.com).
2. Row Level Security may already be enabled by default on new projects as a safe default —
   harmless either way, since the migration below enables it explicitly if it isn't already.
3. **Authentication → Sign In / Providers** → explicitly enable **"Allow anonymous sign-ins"**.
   This is off by default and does not follow from RLS being enabled — see the troubleshooting
   section below; missing this step is the most likely first failure you'll hit.
4. Apply the migration in the SQL editor:
   [`supabase/migrations/20260905120000_create_local_records.sql`](../../supabase/migrations/20260905120000_create_local_records.sql).
5. Confirm table-level privileges match the intent: `authenticated` can `select`/`insert`/`update`
   on `public.local_records`; `anon` has no access. The migration file includes the explicit
   `grant`/`revoke` statements for this — applying the migration as written already does it.
6. Confirm RLS is enabled on `public.local_records` and that the `select own local_records`,
   `insert own local_records`, and `update own local_records` policies exist, each scoped
   `to authenticated` (no delete policy — that's deliberate, not missing).
7. Confirm the table has exactly the columns the migration defines: `id`, `user_id`, `created_at`,
   `value`.

**`local_records` and everything in it is disposable technical-proof data** — it exists only to
prove the local → Supabase sync mechanism end to end. It is not, and must not be treated as, the
workout domain model (that remains deferred per PD-003).

## Android configuration

The Android phone client reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` from
`android/local.properties`, which is gitignored and never committed. Add these two lines yourself
(placeholders only — never commit or paste real values into this repo or its docs):

```properties
sdk.dir=<local Android SDK path>
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=sb_publishable_<...>
```

**Naming note:** the variable is still called `SUPABASE_ANON_KEY` for historical reasons, but the
value that belongs there is the current **publishable client key** from your project's API
settings — Supabase's newer terminology for the same client-safe key. This key is safe to ship in
a client app by design (Supabase's security boundary is RLS, not secrecy of this key), but it's
still kept out of git for environment hygiene, not because it's a true secret.

**Never** put any of the following in `local.properties`, anywhere in this app, or in any
document: the service-role key, any other secret/API key, the database password, or any access or
refresh token. `local.properties` is confirmed covered by the root `.gitignore`.

## Project URL: which value goes where

Use the project's base URL as `SUPABASE_URL`:

```
https://<project-ref>.supabase.co
```

**Do not** use a service-specific path like:

```
https://<project-ref>.supabase.co/rest/v1/
```

The Supabase client library constructs each service's specific endpoint (`/rest/v1/`, `/auth/v1/`,
etc.) itself from the base URL — passing a service path as the base URL breaks every service the
client talks to, not just the one whose path you happened to paste.

## Troubleshooting: anonymous sign-in disabled

**Observed error** (from the `TBDFit.sync` diagnostic log, see below):

```
anonymous sign-in failed status=422 error=anonymous_provider_disabled description=Anonymous sign-ins are disabled: anonymous_provider_disabled
```

**Root cause:** anonymous sign-in had not actually been enabled on the live Supabase project —
enabling RLS or applying the migration does not enable it; it's a separate, explicit toggle.

**Resolution:** Supabase Dashboard → **Authentication** → **Sign In / Providers** → enable
**"Allow anonymous sign-ins"**, and confirm it actually saves (some dashboard versions need an
explicit save action, not just flipping the toggle).

**No Android rebuild was required** after fixing this server-side — retrying "Sync now" in the
already-running app succeeded immediately once anonymous sign-in was enabled.

## Diagnostic logging

The Android client logs sync progress under a single tag: `TBDFit.sync`.

```bash
adb logcat -s TBDFit.sync:* AndroidRuntime:E
```

What gets logged, and why it's safe:

- Configuration presence (`SUPABASE_URL configured=true/false`, host if present) — never the key
  value itself.
- Sync orchestration: `sync requested`, `pendingCount=<n>`, `syncing id=<id>`, remote
  success/failure, local acknowledgement (`markSynced ...`).
- The authenticated user's UID (`uid=<uid>`) — a Supabase user id is not sensitive.
- Remote write start/success/failure, including structured PostgREST/Postgres error fields
  (status, error code, hint, details) when available — this is what made the anonymous-auth
  failure above immediately diagnosable instead of an opaque failure.

**Never logged, under any circumstance:** access tokens, refresh tokens, database passwords,
secret/service-role keys, or any other bearer credential. UID-only logging is intended as a
temporary verification aid, not a permanent logging policy — revisit whether it's still needed
once verification is complete.

## Verification status

### VERIFIED (as of 2026-09-05)

- Android phone can create a `LocalRecord` locally.
- Local creation does not require Supabase or network availability.
- The record is stored in Room as `pending`.
- Android successfully performs anonymous Supabase authentication against the live project.
- The authenticated remote write reaches `public.local_records`.
- The remote write succeeds against the real, live Supabase project (not a fake/test double).
- Local acknowledgement is written, and the record transitions `pending` → `synced`.
- The existing JVM/Robolectric test suite (`LocalRecordDaoTest`, `LocalRecordSyncCoordinatorTest`,
  the `FoundationTest`s) remained green before this live verification.
- `local_records.user_id` matches the authenticated Supabase Auth user identity.
- Offline local creation succeeds without backend/network availability.
- Reconnect + retry successfully synchronizes the pending record.
- Repeated synchronization produces exactly one logical remote row for the same LocalRecord ID.
- Room record data and pending/synced state survive Android process force-stop/restart.
- The anonymous Supabase Auth session survives a normal Android process restart.
- User A can create/read A-owned data.
- User B cannot read User A's row.
- User B cannot INSERT a row claiming User A's `user_id` — live test returned HTTP 403.
- User B cannot UPDATE User A's row.
- Unauthenticated/public access cannot expose `local_records` — live test returned HTTP 401.
- Concurrent calls to the current LocalRecord sync operation are serialized within the coordinator
  instance, verified by a deterministic concurrent-sync test showing exactly one effective remote
  upsert attempt for the same pending record.
- Credential-bearing Supabase exception messages are not logged by the coordinator; structured,
  safe diagnostics (status, error code, description) are used instead.

### STILL TO VERIFY

Not yet checked — do not assume this passes until actually tested and reported:

- GitHub Android CI is green after the latest Supabase changes.

## Remaining verification runbook

### A. Ownership

Compare **Authentication → Users** (the anonymous user's ID) against **Table Editor →
local_records → user_id** for a row that user created. They must match exactly.

### B. Offline → retry

1. Disable network on the device/emulator.
2. Create a record — confirm it shows `pending` and the app does not crash or hang.
3. Restore network.
4. Press "Sync now" — confirm the record becomes `synced`.
5. Confirm exactly one matching row exists in `local_records` (not zero, not two).

### C. Process restart

1. With at least one record present, force-stop the app: `adb shell am force-stop com.tbdfit.phone`.
2. Relaunch manually from the launcher (not from Android Studio's run button).
3. Confirm the Room data and each record's pending/synced status are exactly as before.

### D. Session persistence

1. Note the UID logged before the restart.
2. Force-stop and relaunch (as in C).
3. Perform another sync and note the UID logged this time.
4. Compare the two UIDs and record what was actually observed — do not assume persistence either
   way without checking.

### E. RLS / user isolation

Goal, without instructing anyone to log or paste tokens into anything:

- A second user (user B) must not be able to read user A's row.
- User B must not be able to insert a row claiming user A's `user_id`.
- Unauthenticated/public access must not expose any `local_records` rows.

If a safe method for testing this (e.g. a scripted API check using two distinct authenticated
sessions) is chosen later, document the exact method here at that time — none is prescribed yet.
