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
3. **Authentication → Sign In / Providers → Email** should already be enabled (Supabase's
   default). Do **not** enable the **Anonymous** provider — the application no longer uses it (see
   [Anonymous authentication — retired](#anonymous-authentication--retired) below); a fresh project
   needs no anonymous-auth configuration at all.
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
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
```

**Naming note:** the variable is still called `SUPABASE_ANON_KEY` for historical reasons, but the
value that belongs there is the current **publishable client key** from your project's API
settings — Supabase's newer terminology for the same client-safe key. This key is safe to ship in
a client app by design (Supabase's security boundary is RLS, not secrecy of this key), but it's
still kept out of git for environment hygiene, not because it's a true secret.

**`GOOGLE_WEB_CLIENT_ID`** is the OAuth 2.0 **Web application** Client ID from Google Cloud
Console — not the Android Client ID (see "Google authentication" below). It's client-safe
configuration, not a secret, kept out of git the same way as the values above.

**Never** put any of the following in `local.properties`, anywhere in this app, or in any
document: the service-role key, any other secret/API key, the database password, any access or
refresh token, or a Google OAuth **client secret**. `local.properties` is confirmed covered by the
root `.gitignore`.

## Email verification

Email/password account creation requires email ownership verification before the account becomes
usable — verify this is actually enabled on the live project (**Authentication → Sign In /
Providers → Email → Confirm email**); do not assume a prior technical-proof-era setting is still
what's wanted.

The confirmation link uses a deep link back into the app:

- Redirect URL `tbdfit://auth-callback` must be added under **Authentication → URL Configuration →
  Redirect URLs** on the Supabase Dashboard.
- `scheme`/`host` are configured to match in `SupabaseClientProvider`'s `install(Auth) { ... }`,
  and the matching intent-filter is declared on `MainActivity` in `AndroidManifest.xml`
  (`android:launchMode="singleTask"` avoids stacking a duplicate activity instance when the link is
  tapped from an email app while TBDFit is already running).
- The Auth plugin is configured with `flowType = FlowType.PKCE` rather than the default
  `FlowType.IMPLICIT`: Supabase's own docs note that some email clients/scanners strip URL
  fragments (which implicit-flow tokens are carried in) before the link ever reaches the app; PKCE
  carries a `code` query parameter instead, which survives that.
- `MainActivity.onCreate`/`onNewIntent` call `SupabaseClientProvider.client.handleDeeplinks(intent)`
  — this parses the link and establishes the session itself; no further wiring is needed since
  `AuthState` is already derived from the same session state.

`AuthState` never claims `SignedIn` merely because a signup request was accepted — see
`requiresEmailVerificationPrompt` in `EmailAuthScreen.kt`. Until the link is followed, the app shows
a "Check your email" screen with a resend option (`AuthGateway.resendEmailVerification`).

## Google authentication

Native ID-token flow via Android Credential Manager — not the deprecated `GoogleSignIn` API, and
not an OAuth browser-redirect flow.

**Google Cloud Console** — two separate OAuth 2.0 Client IDs are required:

- A **Web application** Client ID. Its value is what goes in `GOOGLE_WEB_CLIENT_ID` above, and is
  also what gets registered in the Supabase Dashboard (next section) — **not** the Android Client
  ID.
- An **Android** Client ID, configured with package name `com.tbdfit.app` and the SHA-1 signing
  certificate fingerprint of the app (register both the debug and release fingerprints — they
  differ). This authorizes the app itself to request ID tokens; its value is not pasted anywhere in
  app code or in the Supabase Dashboard.

**Supabase Dashboard** — **Authentication → Sign In / Providers → Google**: enable the provider and
add the **Web** Client ID under **Client IDs**. No client secret is required for this native
ID-token flow (unlike the OAuth browser-redirect flow, which this app does not use) — confirm this
directly in the Dashboard UI for your project version.

**Wear note:** `:phone` and `:wear` share `applicationId = com.tbdfit.app`, but Google auth is
Phone-only in this slice — the Android Client ID/SHA-1 registration above is unaffected by Wear,
since Wear has no Google auth code at all here.

**Identity linking:** Supabase Auth automatically links a new Google identity to an existing
email/password user when the emails match **and** the existing identity is already verified (no
separate dashboard toggle needed for this — the "Manual Linking" toggle that exists is a different,
unrelated feature for linking *different*-email identities, which this app does not use). This
means a user who verifies `alice@example.com` via email/password and later taps "Continue with
Google" with the same address converges to the same account automatically.

## Android application identity (Phone ↔ Wear)

Not Supabase-specific, but recorded here as the closest existing Android operational runbook:

- `:phone` and `:wear` require the **same installed application/package identity (`applicationId`)
  and the same signing certificate** — this is a Google Play services requirement for Wear Data
  Layer (DataClient/MessageClient) communication to work at all, not a project preference.
- The shared `applicationId` (`com.tbdfit.app`) is deliberately **product-neutral** — it identifies
  the whole TBDFit Android product across both form factors, not one of them.
- Each module's Kotlin **namespace** (`com.tbdfit.phone`, `com.tbdfit.wear`) is unaffected and
  remains platform/module-specific source organization — only the installed app identity is shared.
- Release signing configuration is still deferred (only debug builds exist so far), but future
  Phone and Wear release artifacts must use a compatible (matching) signing identity for the same
  Data Layer reason.

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

## Web Routine data (first real Supabase product domain table)

`routines` / `routine_exercises` / `routine_planned_sets` / `exercises`
([`supabase/migrations/20260910120000_create_routines.sql`](../../supabase/migrations/20260910120000_create_routines.sql))
are the first real, centrally-persisted TBDFit product domain tables — not disposable technical
proof like `local_records`. They back the Web client's Routine screens only (`web/src/data/routines.ts`)
as of this migration. **Android's Routine domain (Room) and this Postgres schema are two
independent, unconnected persistence stores right now — there is no cross-client sync.** See
`docs/product/frontend-prototype-notes.md`'s "Routine Supabase + Web vertical slice" section for the
product-level summary, and the migration file's own extensive header comments for the schema
reasoning (why Postgres-native RLS/ownership semantics were used instead of a mechanical translation
of the Android Room entities, why `save_routine(...)` is a Postgres function rather than several
independent browser writes, and the explicit reasoning behind the one deliberate RLS subtlety: a
foreign key alone does not stop User A from attaching User B's private custom exercise to a routine,
since FK checks bypass RLS on the referenced table — the `routine_exercises` insert/update policies
re-check exercise visibility explicitly for exactly this reason).

**Applying this migration to a live project** — no `supabase/config.toml` exists in this repository
yet (the CLI has never been linked to a project here), so use whichever of these is more convenient:

- **Supabase CLI** (requires `supabase login` once, then a one-time link):
  ```bash
  npx supabase login
  npx supabase link --project-ref <project-ref>
  npx supabase db push
  ```
- **Dashboard SQL Editor** (no CLI setup needed): paste the full contents of
  `supabase/migrations/20260910120000_create_routines.sql` into **SQL Editor** and run it once.

**Validation already performed for this migration** (see the implementation task's own report for
full detail): the exact SQL was applied to a disposable local PostgreSQL 16 container (not the real
project — this sandbox's network policy blocks that, same limitation noted throughout this
document's other sections) with a minimal `auth.users`/`auth.uid()` shim, and every RLS scenario in
the migration's own security reasoning was exercised directly as a non-superuser role: cross-account
SELECT/UPDATE/DELETE isolation, direct `routine_exercises` insertion into another user's routine,
attaching another user's private custom exercise (both via a raw insert and via `save_routine`), and
`anon`-role access — all behaved exactly as the policies intend. This is real evidence the SQL is
correct, not a substitute for also confirming it against the actual live project once applied.

## Web Program data (second real Supabase product domain, optional/additive)

`programs` / `program_weeks` / `program_sessions` / `program_session_exercises` /
`program_session_planned_sets`
([`supabase/migrations/20260911120000_create_programs.sql`](../../supabase/migrations/20260911120000_create_programs.sql))
follow the exact same pattern as the Routine tables above — same RLS/GRANT discipline, same local-
Postgres validation method — and back the Web client's `Programs`/Program Builder screens
(`web/src/data/programs.ts`) only. Program is strictly optional: nothing in the Routine tables or
screens depends on it. See `docs/product/frontend-prototype-notes.md`'s "Program Supabase + Web
vertical slice" section for the full product-level summary.

Two things worth knowing before touching this schema:

- There is **no foreign key anywhere** from `program_session_exercises`/`program_session_planned_sets`
  back to `routine_exercises`/`routine_planned_sets` — this is deliberate, not an oversight. Copying
  a Routine into a Program session (`copy_routine_to_program_session(...)`) reads the routine's
  current content once and writes independent rows; the schema is structurally incapable of a live
  reference back to the source Routine, which is exactly what makes "editing the Routine later never
  changes an existing Program session" a guaranteed fact rather than a convention to remember.
- Ordering here is deliberately **not** required to be contiguous (unlike the Routine tables, which
  assume contiguous positions because every save fully replaces a routine's content) — Program's
  access pattern is incremental (add one week, duplicate one week, delete one session), so positions
  are sparse and never renumbered on delete, the same discipline Android's `WorkoutExercise`/
  `WorkoutSet` already use.

**Applying this migration to a live project**: same two options as above —
`npx supabase db push --dry-run` then `npx supabase db push` once linked, or paste the migration's
full contents into the Dashboard's SQL Editor.

**Validation already performed**: applied to the same kind of disposable local PostgreSQL 16
container (with the same `auth.users`/`auth.uid()` shim) as the Routine migration. Verified for
real, not just reasoned about: the snapshot invariant (copy a Routine into a session, edit/delete
the original Routine, confirm the session's content is untouched and `source_routine_id` becomes
`NULL` on delete rather than blocking it), week duplication producing a fully independent copy, and
the full RLS adversarial suite — cross-account SELECT/UPDATE/DELETE isolation on every table,
direct-insert and RPC-invocation attempts to attach another account's private custom exercise or to
mutate another account's program/week/session, and `anon`-role denial — all behaved exactly as the
policies intend.

## Shared username + display name (profiles.display_name)

[`supabase/migrations/20260912120000_add_profile_display_name.sql`](../../supabase/migrations/20260912120000_add_profile_display_name.sql)
adds `display_name` to `public.profiles`. `username` is **untouched** by this migration — it stays
exactly as `20260906120000_create_profiles.sql` defined it: required, syntax-checked, uniquely
normalized. This is a **shared, cross-client product decision** (Android/Web/Wear/future Apple all
use the same account model), not a Web-specific change — see `web/README.md`'s "Identity model" for
the full rationale, including why the schema is deliberately kept ready for a future
username-based login (a unique, unambiguous mapping to exactly one `auth.users` row) that is
explicitly **not implemented** here.

This migration was never applied to any live project, so it was revised in place rather than
superseded by a new migration file — a deliberate, one-time exception to this repo's normal
migration-immutability rule (its original, unapplied version assumed a Display-Name-only Web signup
with a nullable `username`, a design superseded before ever reaching production).

`display_name` is **required** (NOT NULL). A profile row is either valid and complete (both
`username` and `display_name` set) or does not exist at all — there is no "profile with a missing
display_name" onboarding state, and no client is allowed to insert one field without the other.

**Account creation and TBDFit profile creation are two separate lifecycle steps** for every client
(see `web/README.md`'s "Identity model" for the full rationale — this is a shared, cross-client
decision, not Web-specific):

- **Android**: `ProfileCompletionScreen` collects a username after `SignedIn`; `createOwnProfile`
  (`SupabaseProfileGateway.kt`) inserts `{username, display_name: username}` in one call —
  `display_name` defaults to `username` since Android's onboarding UI only asks for one field, but
  this is an application-level default, not a database invariant.
- **Web**: `signUp()` (`AuthContext.tsx`) collects only email + password — no username, no display
  name, nothing transported through `auth.users.raw_user_meta_data`. **Authentication is the only
  thing that gates access to the authenticated app** — `App.tsx` checks auth phase only; a signed-in
  user with no `profiles` row gets `/`, `/plan`, `/programs`, etc. exactly as normal. Profile
  existence is a concern local to `/profile` alone: `web/src/pages/ProfilePage.tsx` reads
  `web/src/auth/profileState.ts`'s `LOADING`/`MISSING`/`COMPLETE`/`UNAVAILABLE` (derived from the
  `profiles` query) and decides locally whether to render `ProfileSetupForm.tsx` inline or the normal
  profile view — nothing outside `/profile` reads this state. ProfileSetupForm collects both Username
  and Display Name explicitly (Display Name mirrors Username until manually edited) and calls
  `profileCreation.ts`'s `createOwnProfile(username, displayName)`, an explicit `INSERT` triggered by
  pressing Save, using the browser's own authenticated session (no service-role key, no `SECURITY
  DEFINER` function, no auth metadata transport). A `profiles_normalized_username_key` violation
  (username claimed by a different account) is caught there and surfaced as a recoverable inline form
  error — the form stays open, both fields keep their values, the user retries with a different
  username. An earlier version of this app used `profileState.ts` as a second, app-wide access gate
  in `App.tsx` (`MISSING` blocked the entire authenticated shell behind a full-screen
  `ProfileSetupForm`) — that was a product-behavior mistake (it made an optional feature mandatory
  onboarding) and has been removed; the description above is the corrected, current behavior.

An earlier Web design collected the username at signup time, transported it through
`auth.users.raw_user_meta_data` (`options.data.desired_username`), and auto-inserted the profile row
via `useProfileBootstrap.ts` the first time `SIGNED_IN` fired. That design has been **removed**: it
conflated Supabase Auth account creation with TBDFit profile creation, and — because the conflict
could only surface asynchronously after email confirmation, with no form left open — made a
signup-time username race an unrecoverable dead-end reachable only by "contact support." The current
design makes that conflict an ordinary, synchronous, recoverable form validation instead.

**Applying this migration to a live project**: same two options as above — `npx supabase db push
--dry-run` then `npx supabase db push` once linked, or paste the migration's full contents into the
Dashboard's SQL Editor. **Not applied to any live project by this change** — no Dashboard/CLI-linked
access from this environment.

**Validation already performed**: applied to the same kind of disposable local PostgreSQL 16
container (with the same `auth.users`/`auth.uid()` shim) as the Routine/Program migrations, on top
of all prior migrations applied in order, including the later `rls_auto_enable` adoption. Verified
for real, against the exact insert shapes both clients now actually use: an explicit
`{username, display_name}` insert (Web's `ProfileSetupForm`/`profileCreation.ts` shape, and Android's
now-corrected `NewProfileRow` shape — see "Android impact" below) succeeds and defaults `user_id` to
`auth.uid()`; a second account can neither `SELECT` nor `UPDATE` the first account's row; `anon` gets
`permission denied` on both `SELECT` and `INSERT` (table-grant level, not just RLS); and a
case-varied duplicate username (`Robin_92` vs. an existing `robin_92`) fails with `23505` on
`profiles_normalized_username_key` specifically — the exact signal `profileCreation.ts` detects to
produce the recoverable "Username is already taken" error. Blank/whitespace and 81-character
`display_name` values were verified rejected, and exactly 80 characters accepted, during this
migration's original authoring; not repeated in this pass since the constraints themselves were not
touched.

**Android impact**: `SupabaseProfileGateway.kt`'s `NewProfileRow` previously sent only `username`.
Once `display_name` became `NOT NULL` with no `DEFAULT`, that insert would fail outright
(`null value in column "display_name" violates not-null constraint`) — confirmed locally before
fixing it. `NewProfileRow` now also sends `display_name = username`, matching the same
default-equals-username convention Web's ProfileSetupForm mirrors before the user edits it.
Android's `ProfileCompletionScreen` UI, `ProfileState` semantics, and non-null `username` contract
are otherwise unchanged.

## Database source-of-truth principle

**The version-controlled migrations in `supabase/migrations/` are the sole authoritative
definition of the TBDFit database.** A developer must be able to clone this repository, provision a
fresh compatible Postgres/Supabase database, apply every migration in order, and get the intended
TBDFit database behavior — tables, columns, constraints, indexes, RLS enablement, RLS policies,
functions, RPCs, triggers, event triggers, and grants/revokes — without knowing about, or needing,
any SQL executed manually in the Supabase Dashboard's SQL Editor. The Dashboard SQL Editor may be
used for inspection, debugging, and temporary diagnostics; if it changes intended persistent
database behavior, that change must be captured in a migration or it does not count as shipped.

**Resolved** (see "Security Advisor: rls_auto_enable hardening" below): `public.rls_auto_enable()`
and the `ensure_rls` event trigger existed on the live project only, created directly against it
outside this migration history. The developer captured the live definitions via the Dashboard SQL
Editor (`pg_get_functiondef`, `pg_event_trigger` metadata, ownership, and grants) and
[`supabase/migrations/20260913120000_adopt_rls_auto_enable.sql`](../../supabase/migrations/20260913120000_adopt_rls_auto_enable.sql)
now creates both objects from that exact captured definition, so a fresh database built from this
repo's migrations alone converges to the same state as the live project — see below for the
convergence proof.

## Security Advisor: rls_auto_enable hardening

Supabase Security Advisor flagged `public.rls_auto_enable()` — `SECURITY DEFINER`, returns
`event_trigger`, `search_path` pinned to `pg_catalog` — because `PUBLIC` (and therefore `anon`,
`authenticated`, `service_role`, which inherit every `PUBLIC` grant) held `EXECUTE` on it, the
default Postgres grants on function creation.

**Live definition captured** via the Dashboard SQL Editor (this sandbox cannot reach the live
project directly — confirmed by request: `curl https://ufydtwkcddznxpfxoecz.supabase.co/rest/v1/` →
`403 Blocked by network policy`). The developer ran `pg_get_functiondef`, the `pg_event_trigger`
metadata query, the ownership query, and the grants query, and provided the exact output. Read from
the real body (not inferred from the name): on `CREATE TABLE` / `CREATE TABLE AS` / `SELECT INTO`
producing a table or partitioned table in the `public` schema, it force-enables row level security
on it (`alter table if exists <table> enable row level security`, with the failure path logged via
`RAISE LOG` rather than aborting the triggering DDL). Tables in any other schema are explicitly
skipped. Owner: `postgres`. Event trigger: `ensure_rls`, on `ddl_command_end`, tags `CREATE TABLE` /
`CREATE TABLE AS` / `SELECT INTO`, enabled (`O`). Pre-hardening grants: `PUBLIC`/`postgres`/`anon`/
`authenticated`/`service_role` all `EXECUTE`.

[`supabase/migrations/20260913120000_adopt_rls_auto_enable.sql`](../../supabase/migrations/20260913120000_adopt_rls_auto_enable.sql)
(revised in place — never applied to the live project, so no already-applied migration was rewritten)
`CREATE OR REPLACE FUNCTION`s `rls_auto_enable()` with that exact captured body,
`DROP EVENT TRIGGER IF EXISTS` + `CREATE EVENT TRIGGER` to deterministically (re)establish
`ensure_rls` with the exact captured event/tag list/function (event triggers have no `CREATE OR
REPLACE`/`ALTER` form for changing those, so drop+create is the only deterministic option — safe
here because the definition being recreated is identical to what's already live), then
unconditionally revokes `EXECUTE` from `PUBLIC`/`anon`/`authenticated`/`service_role`. Unlike the
prior guarded-REVOKE-only version of this same file, the revokes are no longer existence-gated: this
migration now creates the function itself, so it's guaranteed to exist by that point in the chain on
every database. `postgres` needs no explicit grant — it's the function's owner, and owner privilege
is implicit and untouched by `REVOKE`.

**Not a live, directly exploitable path today** (verified locally, not assumed): Postgres refuses to
invoke any function whose return type is `event_trigger` via ordinary SQL/RPC regardless of
`EXECUTE` grants — calling it directly fails with "trigger functions can only be called as
triggers", a type-level restriction, not a permission one. Revoking `EXECUTE` is still the correct
hardening step Supabase's advisory recommends (least privilege), and it turns that failure into an
explicit permission-denied error for any still-unauthorized caller.

**Validation performed** against disposable local PostgreSQL 16 containers (same method as every
other migration in this document), in two configurations, to prove convergence:

1. **Fresh database**: all 8 migrations applied in order from zero (neither `rls_auto_enable()` nor
   `ensure_rls` pre-existing). Confirmed after: function definition present with `md5(pg_get_functiondef(...))`
   matching the captured live definition exactly; `security_definer = true`; `search_path =
   pg_catalog`; `ensure_rls` exists, `evtenabled = 'O'`, tags and target function match exactly;
   grants reduced to `postgres` only; `has_function_privilege` returns `true` for `postgres` and
   `false` for `anon`/`authenticated`/`service_role`.
2. **Live-drift simulation**: a separate database was seeded with the exact captured live
   definition *and* the exact captured (broad) pre-hardening grants — i.e. what the real project
   looks like today — then the same 8 migrations were applied on top. Result: **identical** to the
   fresh-database outcome on every measure above, including a byte-for-byte identical
   `md5(pg_get_functiondef(...))` — proving `CREATE OR REPLACE` + drop/recreate + revoke converges a
   database that already has the historical drift to the exact same final state as a database that
   never did.
3. **Event trigger functional test against the real captured body** (not the old synthetic
   stand-in): a plain `CREATE TABLE` in `public` → `relrowsecurity = true`; a `CREATE TABLE AS` in
   `public` → `relrowsecurity = true` (proving the second declared tag actually works, not just the
   first); a table created in a non-`public` schema → `relrowsecurity = false` (proving the body's
   explicit schema restriction is real, not assumed).
4. **Negative security tests**: direct calls to `public.rls_auto_enable()` as `anon`, `authenticated`,
   and `service_role` (`set role ...; select public.rls_auto_enable();`) all fail with `permission
   denied for function rls_auto_enable`, matching the `has_function_privilege` results above.
5. **Full migration chain unaffected**: `profiles.username`/`display_name` NOT NULL, and RLS enabled
   on `profiles`/`routines`/`programs`, all verified unchanged after adopting this migration — this
   change only affects tables created *after* it runs in the chain; every existing TBDFit table
   already enables RLS explicitly in its own migration.

Both local containers were torn down after verification. **Not applied to any live project by this
change** — the developer will review and push separately.

The separate "Leaked Password Protection Disabled" Security Advisor finding is intentionally
deferred — it requires a Supabase plan tier this project is not currently on.

## Anonymous authentication — retired

**Current application: anonymous auth is no longer used or required.** Anonymous Supabase
authentication was introduced early on only to exercise real, authenticated Supabase sync for the
disposable `LocalRecord` technical proof, before any real login existed. Now that Phone
email/password authentication exists, anonymous sign-in has been removed from the application:

- `SupabaseLocalRecordRemoteStore` no longer calls `signInAnonymously()`. It checks for an
  existing session (`client.auth.currentSessionOrNull()`) and, if none exists, fails explicitly
  with `NoAuthenticatedSessionException` — it never creates a session of its own. Establishing/
  restoring a session is the auth capability's (`com.tbdfit.phone.auth`) responsibility alone.
- A `LocalRecord` sync attempt with no authenticated session now fails safely: the local record is
  left pending (never marked synced, never deleted), and the failure is logged distinctly from a
  network/Postgres failure.
- **The application can now run with the Supabase Anonymous provider disabled.** Email/password
  auth is unaffected and remains the only provider the app uses.
- Existing remote `local_records`/`wear_replicas` rows owned by old anonymous users are unaffected
  by this change — they remain disposable technical-proof data (see above) and are not migrated to
  any real account. They may be left in place or cleaned up manually; there is no code-level
  migration path for them, deliberately.

The **Verification status** and **Troubleshooting** material immediately below predates this
change and describes the anonymous-auth-based technical proof as it was tested on 2026-09-05 —
those bullets are a historical record of what was actually run at the time, not current guidance,
and are left as originally written rather than rewritten for consistency. Do not read them as
describing current application behavior.

## Historical troubleshooting note: anonymous sign-in disabled (technical-proof era, retired)

This section describes a failure mode that could only occur while the application still called
`signInAnonymously()`. It no longer applies to the current codebase (see
[Anonymous authentication — retired](#anonymous-authentication--retired) above) and is kept only as
a historical record of what was diagnosed and fixed at the time.

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

## Current verification status — Auth & Profile

Levels used below: **IMPLEMENTED** (code exists) / **AUTOMATED TESTED** (covered by the JVM unit
test suite) / **LIVE VERIFIED** (exercised against a real Supabase project and the result recorded)
/ **MANUAL VERIFICATION PENDING** (a runbook procedure exists below but no result is recorded yet)
/ **DEFERRED** (not built).

- Email/password signup, login, verification-gated sign-in, session restore, logout: IMPLEMENTED,
  AUTOMATED TESTED. On-device flow (runbook section G) — MANUAL VERIFICATION PENDING.
- Google native ID-token sign-in code path: IMPLEMENTED, AUTOMATED TESTED. Live Google
  Cloud/Supabase Dashboard configuration — DEFERRED, so on-device flow (runbook section H) is
  blocked until that configuration exists, not merely unrun.
- Anonymous-auth retirement (`NoAuthenticatedSessionException`, no `signInAnonymously()` calls):
  IMPLEMENTED, AUTOMATED TESTED. On-device retirement check (runbook section F) — MANUAL
  VERIFICATION PENDING.
- `profiles` migration (`20260906120000_create_profiles.sql`): IMPLEMENTED (file authored). Applied
  to a live Supabase project — MANUAL VERIFICATION PENDING.
- Profile RLS (select/insert/update own, no delete, no anon policy): IMPLEMENTED in the migration.
  Live proof via real per-user JWTs against the Data API (runbook section I) — MANUAL VERIFICATION
  PENDING; do not treat SQL Editor testing as a substitute (see section I's own caveat).
- Username syntax check + case-insensitive uniqueness constraint: IMPLEMENTED in the migration,
  AUTOMATED TESTED at the Android UX-mirror layer only. Live database-level proof (runbook
  section J) — MANUAL VERIFICATION PENDING.
- Username-or-email login backend / custom API: DEFERRED — discussion only, not an accepted
  decision, no Edge Function or endpoint exists.
- Shared username + display name model (`20260912120000_add_profile_display_name.sql`): username
  required/unique, display_name required and independently editable — IMPLEMENTED, LIVE VERIFIED
  against a disposable local Postgres instance (constraints, RLS, concurrency-conflict cases). Web
  profile CREATION is now a separate, explicit, optional, in-app step
  (`profileState.ts`/`profileCreation.ts`/`ProfileSetupForm.tsx`), owned entirely by `/profile`
  (`ProfilePage.tsx`) — not collected at signup, not auto-inserted on `SIGNED_IN`, and not gated at
  the application level: `App.tsx` grants full app access on authentication alone, regardless of
  profile existence (an earlier `useProfileBootstrap.ts` design that transported a username through
  auth metadata, and a later `App.tsx`-level profile gate, have both been removed — see "Shared
  username + display name" above). Android's `Profile`/`ProfileFlow`/
  `SupabaseProfileGateway` non-nullable-username contract is preserved; `NewProfileRow` now also
  sends `display_name` (see "Android impact" above — a required, not optional, fix once
  `display_name` became NOT NULL). AUTOMATED TESTED (Web: `usernameValidation.ts`,
  `displayNameValidation.ts`, `profileState.ts`'s `deriveProfileState`, plus
  `npm run test`/`npm run build` passing; Android: `ProfileFlowTest`, `UsernameValidationTest`, a
  full `:phone:testDebugUnitTest` run). Username-based login itself is explicitly DEFERRED — not
  implemented, by design. Applied to a live Supabase project — MANUAL VERIFICATION PENDING (not done
  by this change).

## Verification status

The dated section below predates anonymous auth's retirement (see
[Anonymous authentication — retired](#anonymous-authentication--retired)) and is left exactly as
recorded at the time, including its references to anonymous auth — it is a historical record of
what was actually tested on that date, not current guidance.

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
- Live, on-device verification of anonymous-auth retirement: with a fresh install and the
  Supabase Anonymous provider disabled on the dashboard, confirm login/create-account still work,
  and that triggering "Sync now" while signed out fails safely (record stays `pending`, no crash,
  no anonymous session appears under **Authentication → Users**). See the exact steps in
  [Anonymous authentication — retired](#anonymous-authentication--retired)'s live verification
  runbook entry below (section F).

## Remaining verification runbook

### A. Ownership

Compare **Authentication → Users** (the signed-in user's ID) against **Table Editor →
local_records → user_id** for a row that user created. They must match exactly.

### B. Offline → retry

1. Disable network on the device/emulator.
2. Create a record — confirm it shows `pending` and the app does not crash or hang.
3. Restore network.
4. Press "Sync now" — confirm the record becomes `synced`.
5. Confirm exactly one matching row exists in `local_records` (not zero, not two).

### C. Process restart

1. With at least one record present, force-stop the app: `adb shell am force-stop com.tbdfit.app`.
   `:phone` and `:wear` now share this applicationId (required for Wear Data Layer communication —
   see below), so when both are installed on different devices/emulators, target the right one
   explicitly, e.g. `adb -s <phone-emulator-serial> shell am force-stop com.tbdfit.app`.
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

### F. Anonymous auth retirement

To be run once the Supabase Anonymous provider is disabled on the dashboard (**Authentication →
Sign In / Providers → Anonymous → off**):

1. Confirm **Authentication → Sign In / Providers → Email** is still on.
2. Fresh install the Phone app (or clear its data) so no session is persisted yet.
3. Launch → confirm the login/create-account screen appears (`AuthState.SignedOut`), not a crash
   and not an automatically-created anonymous session.
4. Create a real account and log in → confirm the profile screen appears normally.
5. Log out → back at the login/create-account screen → confirm no new row appears under
   **Authentication → Users** as a result of being signed out (no anonymous fallback fires).
6. While signed out, use `adb` or a debug affordance to trigger the LocalRecord sync path (or
   simply note that the current UI makes this unreachable, since sync is only available while
   signed in) → confirm: no crash, no new row under **Authentication → Users**, and any
   already-pending `LocalRecord` stays `pending` rather than being marked `synced` or deleted.
7. Log back in with the same real account → confirm "Sync now" succeeds normally using that
   session, exactly as before this change.

### G. Email landing + verification flow

```
fresh SignedOut app (landing screen: "Continue with Google" / "Continue with Email")
→ tap "Continue with Email" → confirm the slide-in transition, and that back navigation
  (system back gesture/button) slides back to the landing screen
→ tap "Create account", enter a real email + a valid password → submit
→ confirm the "Check your email" screen appears (NOT the profile screen) — this is the point that
  proves signup-accepted is not being confused with authenticated
→ open the verification email, tap the link → confirm it opens TBDFit (not a browser dead end)
→ back in the app (may need to bring it to foreground), confirm AuthState became SignedIn without
  any further action
→ force-stop the app, relaunch → confirm still SignedIn (no re-login prompt)
→ logout → confirm the landing screen reappears
→ tap "Continue with Email" → "Log in" with the same credentials → confirm SignedIn again
```

Also verify: tapping "Resend email" on the "Check your email" screen results in a second email
arriving; and in **Supabase Dashboard → Authentication → Users**, the new user's row shows a
confirmed email timestamp only after the link was followed, not at signup time.

### H. Google sign-in

```
SignedOut (landing screen)
→ tap "Continue with Google"
→ Google account chooser appears → select an account (or cancel, and confirm the app returns
  cleanly to the landing screen with no error message shown for a plain cancellation)
→ confirm AuthState becomes SignedIn immediately, no email/password step, no verification screen
→ force-stop the app, relaunch → confirm still SignedIn
→ logout → confirm the landing screen reappears
```

Also verify in **Supabase Dashboard → Authentication → Users**: the signed-in row's provider shows
Google; and, separately, sign up with email/password using the same address as a Google account,
verify it, then "Continue with Google" with that same address on a fresh sign-out — confirm both
methods resolve to the **same** user row (same id), not two separate rows.

### I. Profiles: authenticated RLS verification (Data API, not SQL Editor)

**Do not use the SQL Editor as evidence that RLS works.** The SQL Editor runs as the database
owner/service role — it can bypass RLS entirely or behave materially differently from a normal
`authenticated`-role request, so a query succeeding there proves nothing about what a real signed-in
user's app request can or cannot do. The only valid proof is exercising the actual Data API with
real per-user JWTs, exactly as the Android app does.

Use two disposable throwaway accounts (e.g. created via the app's own signup + verification, or any
already-available test accounts) — never real user accounts. **Never print, log, or paste an access
token anywhere, including into this document or any AI/chat tool.** Keep tokens only in local shell
variables for the duration of the test, in a terminal you control.

```bash
export SUPABASE_URL="https://<project-ref>.supabase.co"
export ANON_KEY="<publishable/anon key>"          # safe, client-facing — see "Android configuration" above

# --- Sign in as user A; the token never leaves this shell variable ---
A_TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d '{"email":"<user-a-email>","password":"<user-a-password>"}' | jq -r '.access_token')

# A inserts their own profile — also doubles as the "generated column" check (D.4 below): the
# response body's normalized_username must read back lowercase. display_name is required (NOT
# NULL) alongside username — an insert with username only now fails with a not-null violation, not
# an RLS error, so it must be included here even though this section is about RLS, not the
# display_name contract itself (see "Shared username + display name" above).
curl -s -X POST "$SUPABASE_URL/rest/v1/profiles" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $A_TOKEN" \
  -H "Content-Type: application/json" -H "Prefer: return=representation" \
  -d '{"username":"Testusera","display_name":"Testusera"}'
# Expect: 201, one row, user_id = A's own auth uid, normalized_username = "testusera".

A_USER_ID="<paste A's user_id from the response above — not a secret, just an id>"

# A reads their own profile
curl -s "$SUPABASE_URL/rest/v1/profiles?select=*" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $A_TOKEN"
# Expect: exactly A's row, nothing else.

# --- Sign in as user B ---
B_TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d '{"email":"<user-b-email>","password":"<user-b-password>"}' | jq -r '.access_token')

# B cannot read A's row
curl -s "$SUPABASE_URL/rest/v1/profiles?select=*" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $B_TOKEN"
# Expect: empty array — RLS silently excludes A's row, not an error response.

# B cannot update A's row
curl -s -X PATCH "$SUPABASE_URL/rest/v1/profiles?user_id=eq.$A_USER_ID" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $B_TOKEN" \
  -H "Content-Type: application/json" -H "Prefer: return=representation" \
  -d '{"username":"hijacked"}'
# Expect: empty array / 0 rows affected. Re-check with $A_TOKEN that A's row is unchanged.

# B cannot insert a profile claiming A's user_id
curl -s -X POST "$SUPABASE_URL/rest/v1/profiles" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $B_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"$A_USER_ID\",\"username\":\"hijacked2\"}"
# Expect: rejected by the insert policy's with-check. Confirm via A_TOKEN that no such row exists.

# --- Unauthenticated / public ---
curl -s "$SUPABASE_URL/rest/v1/profiles?select=*" -H "apikey: $ANON_KEY"
# Expect: empty array / no rows — no Authorization header at all, anon-role request.
```

Record the actual HTTP status/body observed for each step here once run — do not assume the
expected outcome without checking, the same discipline already applied to `local_records`' own
verification above.

### J. Profiles: database constraint verification

Schema/constraint behavior (as opposed to *whose* request it is) may be checked in the SQL Editor,
since RLS identity isn't what's being tested — but case-insensitive uniqueness specifically must
still use separate real authenticated users (continuing directly from Section I), since the point
is that *different accounts* collide, not just that a constraint exists.

**Case-insensitive uniqueness** (continuing Section I's `$A_TOKEN`/`$B_TOKEN`, plus a third user C):
have A, B, and C each attempt to create a profile with `Robin`, `robin`, and `ROBIN` respectively
(one per user, via the same `POST .../rest/v1/profiles` call as above). Expect exactly one `201`
across all three attempts; the other two must fail with a PostgREST error whose underlying Postgres
code is `23505` (unique_violation) on `profiles_normalized_username_key`. Confirm via any one
token's own `select=*` that only one such row's `normalized_username` is `robin`.

**Invalid usernames** (schema-only — SQL Editor is fine here, or reuse `$A_TOKEN` for realism):
confirm each of `ab` (too short), a 31-character string (too long), `röbin` (non-ASCII), `rob in`
(internal whitespace), and `robin!` (disallowed punctuation) is rejected with SQLSTATE `23514`
(check_violation) on `profiles_username_syntax` — independent of whatever Android's own validation
would have allowed through, proving the database is the actual authority.

**Existing user, no prior profile:** confirm a user who already has a Supabase Auth session but no
`profiles` row can successfully complete profile creation with no manual data migration or
fabricated username required — on Android, this means reaching `ProfileState.Missing`'s blocking
`ProfileCompletionScreen`; on Web, it means visiting `/profile` and seeing `ProfileSetupForm` there
(the rest of the Web app remains fully usable in the meantime — see "Shared username + display
name" above for why these two clients' UX deliberately differ here).
