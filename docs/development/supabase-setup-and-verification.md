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

### Per-exercise Note and Rest Timer

[`supabase/migrations/20260914120000_add_routine_exercise_note_and_rest_timer.sql`](../../supabase/migrations/20260914120000_add_routine_exercise_note_and_rest_timer.sql)
adds two nullable columns to `routine_exercises`: `note` (free text, ≤ 1000 chars, pinned to that
routine-exercise slot — not the exercise definition) and `rest_timer_seconds` (a non-negative
integer; `NULL` means "Off", the same blank-means-null convention `target_reps`/`target_weight`
already use). Both are optional — `NULL` is the common, expected case, not an incomplete-record
signal, so neither has a not-blank constraint. `20260910120000_create_routines.sql` had already been
applied to the live project by this point (confirmed via the developer's own migration-status check),
so `save_routine(...)` could not be edited in place; this migration re-declares it via
`create or replace function`, identically to how `20260910180000_normalize_exercise_identity.sql`
re-declared it for the exercise-identity retype. Web-side: `PlannedExercisesEditor.tsx` renders a
Note textarea and a free numeric Rest Timer (seconds) input per exercise — a plain number field, not
a preset dropdown, same blank-means-null/reject-negative contract as Target reps/Target weight; the
DB column has no opinion on the value beyond `>= 0`. `RoutineDetailPage.tsx`'s read-only display
still formats the stored seconds as mm:ss (see `plannedExerciseDrafts.ts`'s `formatRestTimerLabel`)
even though entry itself is now a raw number, not a picker. **Routine only**, gated behind a
`showNoteAndRestTimer` prop that Program's `SessionEditor` passes as
`false`, since `program_session_exercises` has no equivalent columns and showing the inputs there
would silently discard whatever a user typed. Validated against a disposable local Postgres 16
instance: round-trip persistence, blank/absent → `NULL` (never `''`/`0`), the length/non-negative
CHECK constraints, and a full re-run of the Routine RLS adversarial suite (cross-account isolation,
anon denial, the FK-bypasses-RLS custom-exercise-visibility check) — all unaffected. **Not applied to
any live project by this change.**

### Per-set Set Type

[`supabase/migrations/20260915120000_add_planned_set_type.sql`](../../supabase/migrations/20260915120000_add_planned_set_type.sql)
adds `set_type` to `routine_planned_sets`: `text`, `NOT NULL DEFAULT 'NORMAL'`, CHECK-constrained to
`NORMAL`/`WARMUP`/`FAILURE`/`DROPSET` — the same plain-CHECK-not-enum convention
`exercises.exercise_type`/`exercises.equipment` already established. Unlike `note`/
`rest_timer_seconds`, every set genuinely has *some* type (there is no meaningful "no type" state),
so this column is `NOT NULL` with a constant default rather than nullable — a single
`add column ... not null default 'NORMAL'` statement is sufficient and safe against the already-live
table (no separate backfill `UPDATE` needed, since the default doesn't depend on another column).
`save_routine(...)` is re-declared again (same append-only reasoning as the note/rest-timer
migration) to read/write it, defaulting a blank/absent `setType` to `'NORMAL'` via
`coalesce(nullif(x, ''), 'NORMAL')`. Web-side: a small square `<select>` per set (showing just the
letter N/W/F/D — see `plannedExerciseDrafts.ts`'s `SET_TYPE_OPTIONS`/`setTypeLetter`), in the same
"Set" column header, gated behind the same `showNoteAndRestTimer` prop as Note/Rest Timer (Program
sessions have no `set_type` column either). `RoutineDetailPage.tsx` shows a compact letter badge
next to the set number, but only for a non-`NORMAL` type, to avoid visual noise on the common case.
Validated against a disposable local Postgres 16 instance: an explicit `setType` round-trips
correctly, an omitted one defaults to `NORMAL`, an invalid value is rejected by the CHECK constraint,
a full-replace re-save correctly updates an existing set's type, and the RLS adversarial suite
(cross-account isolation, anon denial) still holds. **Not applied to any live project by this
change.**

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

## Completed Workout History (first execution-domain backend slice)

`workouts` / `workout_exercises` / `workout_sets`
([`supabase/migrations/20260917120000_create_workout_history.sql`](../../supabase/migrations/20260917120000_create_workout_history.sql))
are the first Supabase tables for TBDFit's *execution* domain, as opposed to the *planning* domain
(Routine/Program above). They represent **only already-completed workout history** — there is no
ACTIVE/in-progress state, no `start_workout`/`stop_workout`/`finish_workout` RPC, and no execution
UI anywhere in Web as a result of this change.

**Client/product responsibility split this establishes** (see ADR-005, PROPOSED — FOR TEAM REVIEW,
for the general cross-client principle this instantiates; this section does not mark that ADR
Accepted, only applies its already-proposed direction to one concrete domain):

- **Web** — planning (Routine/Program, existing) plus, going forward, read-only surfaces over this
  table family (Profile completed-Workout count, a statistics tab, Calendar markers, a history
  list/detail). Web does **not** execute, start, stop, finish, or record a workout, and this
  migration adds no Web code, mutation hook, or UI for the write path at all — see "Web write
  integration" below.
- **Native Phone/Watch** (later) — workout execution; the eventual producer of rows in this table
  family via `record_completed_workout(...)`.
- **Supabase** — durable, client-agnostic storage of completed history, independent of any one
  client's local execution state.

**Client-agnostic by design**: nothing in the schema or RLS below distinguishes "Web" from "Phone" —
`authenticated` means any signed-in TBDFit account, not a specific client. `record_completed_workout`
is granted to `authenticated` broadly, exactly like `save_routine`; Web simply never calls it. This
mirrors this document's own established convention (see the Routine section above) of enforcing
ownership via `auth.uid()`, never a client-identity check.

**Historical snapshot stability**: `workout_exercises` snapshots `exercise_name`/`exercise_type`/
`equipment`/`note`/`rest_timer_seconds` at record time rather than relying on a live join to
`exercises`/`routine_exercises` — the same "copy, not reference" principle already established for
`copy_routine_to_program_session` (see the Program section above). A completed Workout remains fully
readable after its source Exercise is renamed or its source Routine is deleted. `origin_routine_id`
and `workout_exercises.exercise_id` are traceability-only foreign keys (`ON DELETE SET NULL`, never
`CASCADE`) — deleting a Routine or (hypothetically, since no delete path exists yet) an Exercise
never deletes or blocks completed history.

**Deliberately diverges from Android's local Room schema** (`WorkoutEntity`/`WorkoutExerciseEntity`/
`WorkoutSetEntity`), not a blind copy: Android's own schema comment states a renamed Exercise should
transparently update past local workout display, and it has no `set_type`/`note`/`rest_timer_seconds`
columns at all. Supabase's completed history is meant to be a durable, potentially cross-client
system of record, so it makes the opposite call on the name snapshot, and it carries `set_type`/
`note`/`rest_timer_seconds` since Routine planning on Supabase already established those concepts.
There is deliberately no `status` column (`ACTIVE`/`COMPLETED`) — unlike Android, this table only
ever represents finished workouts by definition, so there is nothing for a status flag to
disagree with.

**Planned vs. performed independence**: `workout_sets` carries `target_reps`/`target_weight`
(planned) and `reps`/`weight` (performed) as separate nullable columns — neither is derived from or
overwrites the other. `workout_date` is a plain `date`, supplied by the recording client rather than
derived from `started_at`/`completed_at` via UTC truncation, specifically so a late-night or
cross-timezone workout is grouped under the calendar day the user actually experienced — this is
the column any future Calendar/streak feature should group by.

**IDs and idempotent retry**: the Workout aggregate's `id` (and its children's `id`s) are supplied by
the caller rather than server-generated, so a native client can generate a stable id before
attempting the network call and safely retry after a lost response. `record_completed_workout`
treats a resubmission of an `id` already owned by the same caller as an already-succeeded retry
(returns the existing id, does not re-insert or merge); a submission of an `id` already owned by a
*different* account is rejected as a cross-account collision. No general offline sync/conflict
resolution is built — this is a narrow, deliberate idempotency rule, not a sync engine.

**Atomic write RPC, `SECURITY DEFINER`, no direct write grants**: `record_completed_workout(payload)`
is the *only* way to create a row in any of the three tables — none of them grant `INSERT`/`UPDATE`
to `authenticated` at all, so this is a structural guarantee, not a convention. This is a deliberate
divergence from `save_routine`'s `SECURITY INVOKER` + real per-table RLS-insert-policy precedent: a
partially-written Routine draft is a low-cost failure mode, but a partially-written completed Workout
would be a corrupted historical record, so the stronger guarantee was chosen here. Hardening mirrors
`20260913120000_adopt_rls_auto_enable.sql`'s template: fixed `set search_path = public, pg_temp`,
schema-qualified references throughout, `EXECUTE` revoked from `PUBLIC`/`anon` and granted only to
`authenticated`, and owner identity derived exclusively from `auth.uid()` inside the function body
(the payload has no `owner_id` field for a caller to spoof). The function re-checks exercise
visibility explicitly before each `workout_exercises` insert (`owner_id is null or owner_id =
auth.uid()`) for the same FK-bypasses-RLS reason `routine_exercises`' own insert/update policies do
(see the Routine section's migration-header reasoning) — a caller cannot legitimize a reference to
another account's private Exercise merely by also supplying a plausible `exercise_name` snapshot.

**Web write integration: NONE.** Web's own code contains no call to `record_completed_workout`, no
mutation hook, and no UI that could trigger one. The function existing as backend infrastructure
does not change Web's product scope — Web's relationship to this table family is read-only, enforced
by what Web's code simply never does.

**Deletion**: `delete own workouts` lets an owner delete a completed Workout; the `ON DELETE CASCADE`
FKs from `workout_exercises`/`workout_sets` mean this only ever removes that Workout's own aggregate
rows, never a Routine, Program, or Exercise. No partial-edit UI/RPC exists — deleting and re-recording
is the only correction path in this slice.

**Out of scope for this migration** (deliberately not built): any Profile/Statistics/Calendar
frontend, `scheduled_sessions`, daily steps/activity, followers/following, HealthKit/Health Connect,
Phone/Watch sync, persisted streak state (a streak must be computed dynamically from `workout_date`
history, not stored), and any total-volume/PR/aggregate strength statistic.

**Validation performed**: applied to the same kind of disposable local PostgreSQL 16 container (with
the same `auth.users`/`auth.uid()` shim) as every other migration in this document, on top of the
full existing chain. Verified for real: ownership isolation on select/delete/write for two distinct
accounts and `anon`; `anon` and even `authenticated` are structurally blocked from any direct
`INSERT` on all three tables (no such grant exists); the cross-account private-Exercise
visibility-legitimization attack is rejected with zero partial rows; a full-aggregate atomicity test
(a valid first exercise followed by a second exercise referencing a nonexistent Exercise) leaves zero
rows in any of the three tables; date-ordering validation rejects `completedAt < startedAt`; an
idempotent retry of the same `id`/owner returns the original id without altering the stored content;
a cross-account `id` collision attempt is rejected and does not affect the original row; blank names,
negative reps/weight, and an invalid `set_type` are all rejected by CHECK constraints, not just
application logic; renaming the referenced built-in Exercise and deleting the source Routine
afterward leaves the recorded Workout's name, snapshot exercise name, and sets completely unchanged
(only `origin_routine_id` becomes `NULL`); deleting a Workout removes its own `workout_exercises`/
`workout_sets` rows and nothing else, leaving the source Exercise row intact; and the pre-existing
Routine/Program RLS adversarial suite (cross-account isolation) still holds unaffected. The container
was torn down after verification. **Not applied to any live project by this change** — this sandbox
has no CLI link and no Dashboard access (same limitation noted throughout this document); `supabase
db push` was not run.

## Profile Core + Social Graph (backend Slice A)

`profiles.bio`, `follows`, and `get_my_profile_summary()`
([`20260918120000_add_profile_bio.sql`](../../supabase/migrations/20260918120000_add_profile_bio.sql),
[`20260918130000_create_follows.sql`](../../supabase/migrations/20260918130000_create_follows.sql),
[`20260918140000_create_profile_summary.sql`](../../supabase/migrations/20260918140000_create_profile_summary.sql))
are the backend for the upcoming Web Profile header (avatar placeholder, display name, `@username`,
Workouts/Followers/Following counts, bio, Edit Profile). This section documents the exact contract;
the frontend itself is a later slice — nothing here changes `ProfilePage.tsx`'s rendering.

**Profile row optionality — unchanged, reaffirmed.** An authenticated account with no `profiles` row
remains a fully valid state (see "Shared username + display name" above and `web/README.md`'s
Identity model). This slice does not add a completeness gate anywhere; `get_my_profile_summary()`
represents "no profile yet" as data (`hasProfile: false`), never as an error — see below.

**Bio semantics**: `profiles.bio` is nullable text, `NULL` meaning "no bio configured," bounded to
280 characters by a database `CHECK` (`profiles_bio_length`). Not required at profile setup —
`ProfileSetupForm.tsx` collects only username + display name, exactly as before; every profile row
created through it today has `bio = NULL`. No RLS change was needed: bio is just another column on
the same owner-only row, already fully covered by the existing `select`/`insert`/`update own
profile` policies. **No write path exists in Web yet** — there is no Edit Profile UI to call one from
this slice; `useOwnProfile.ts` now reads `bio` (read-only) alongside `username`/`display_name` for a
future consumer, but nothing writes it. Whether bio ever becomes visible to other accounts is an
explicitly deferred product decision (see "Cross-account read boundary" below), not resolved here.

**Social graph — `follows`**: a minimal one-way relationship, `(follower_id, following_id)` both
referencing `auth.users.id` directly (not `profiles.user_id` — see the migration's own header
comment on why: a follow is an account-level relationship, and requiring a `profiles` row to be
followable would silently reintroduce a profile-completeness gate). The composite primary key is the
sole uniqueness mechanism (a duplicate follow fails `23505`, no pre-check needed) and also serves
"accounts followed by X" lookups directly via its leading column; a second index on `following_id`
serves "followers of X" lookups. `ON DELETE CASCADE` on both FKs means account deletion cleans up
every edge that account was part of with no trigger. No blocking, private accounts, follow requests,
or denormalized counters — V1 is deliberately this narrow.

**Follow write contract**: no `follow()`/`unfollow()` RPC — a direct RLS-protected `INSERT`/`DELETE`
is the complete contract, the same "prefer `SECURITY INVOKER` + RLS over a `DEFINER` function when
RLS alone expresses the rule" preference this document's other functions already lean on where there
is no cross-table atomicity requirement. `insert ... with check (auth.uid() = follower_id)` makes it
structurally impossible for a caller to create a follow "on behalf of" another account; self-follow
is rejected by a `CHECK` constraint (`follows_no_self_follow`), not duplicated in RLS; `delete ...
using (auth.uid() = follower_id)` means a caller may only ever remove edges where *they* are the
follower — never force someone else to unfollow, never remove an edge they are merely the target of.

**Follow read privacy — the narrow boundary chosen**: `follows` has **no** broad
"`authenticated` can `SELECT` all rows" policy. The `select own follow edges` policy scopes reads to
rows the caller is personally a party to (`auth.uid() = follower_id OR auth.uid() = following_id`) —
their own following list and their own followers, never an arbitrary third party's graph. This was
chosen over (a) opening the whole table, which would hand every signed-in account the entire social
graph, and (b) a counts-only `SECURITY DEFINER` function, which would add privileged surface this
table doesn't need — the per-row-ownership policy matches every other table in this schema, and it
already supports a future followers/following *list* UI for one's own account with zero further
schema/policy change (that UI is out of scope now). It does **not** expose a third party's followers
or following list to anyone but that party.

**Why counts are derived, not stored**: `get_my_profile_summary()` computes `followerCount`/
`followingCount` by counting `follows` rows at read time (`count(*) ... where following_id =
auth.uid()` / `where follower_id = auth.uid()`), and `workoutCount` by counting `workouts` rows
(`where owner_id = auth.uid()`) — none of these are cached on `profiles`. A stored counter is one
more place for truth to drift from the actual edge/row set, with no benefit at this project's scale.
`workoutCount` means exactly "completed Workout aggregates owned by this account" (the
`record_completed_workout`-authored rows from the previous section) — it does **not** count
Routines, ProgramSessions, or any future scheduled-occurrence concept.

**`get_my_profile_summary()`: `SECURITY INVOKER`, not `DEFINER`**. Every read it performs (own
`profiles` row, own `workouts` rows, own `follows` edges on either side) is already something the
calling user is independently allowed to do under existing RLS — there is no privilege gap to bridge
and no multi-table write atomicity concern (this function performs zero writes), the two reasons
`record_completed_workout` needed `DEFINER`. Running as `INVOKER` means Postgres enforces every one
of those RLS checks exactly as if the caller issued the underlying `SELECT`s directly — no privileged
code path exists here for a bug to widen. It still gets the same explicit-grant treatment as every
`DEFINER` function in this document (`REVOKE` from `PUBLIC`/`anon`, `GRANT EXECUTE` only to
`authenticated`, fixed `search_path = public, pg_temp`, schema-qualified references, an explicit
`auth.uid() is null` check) for consistency and defense-in-depth, even though anon has no `EXECUTE`
grant here regardless. It takes **zero arguments** — the caller cannot pass another account's id even
in principle, since there is no parameter for one; the account is derived exclusively from
`auth.uid()`. A signed-in account with no `profiles` row gets back `hasProfile: false` with null
`username`/`displayName`/`bio` and real (possibly zero) counts — never an error — because the counts
key off `auth.users.id` directly (`workouts.owner_id`, `follows.follower_id`/`following_id`), never
`profiles.user_id`; the `LEFT JOIN` in the function body is what preserves this.

**Local test-shim note** (relevant to anyone re-running this repo's adversarial verification, not a
product concern): a `SECURITY INVOKER` function resolves `auth.uid()` under the *calling* role at
runtime (unlike a `DEFINER` function, which resolves it under the function owner regardless of the
caller's grants), so the disposable local Postgres container's `auth.users`/`auth.uid()` shim needed
an explicit `GRANT USAGE ON SCHEMA auth TO authenticated, anon` and `GRANT EXECUTE ON FUNCTION
auth.uid() TO authenticated, anon` — mirroring grants the real Supabase platform already applies to
its own `auth` schema by default. Every RLS policy in this schema already implicitly relied on this
being true; it simply had never been exercised by a `SECURITY INVOKER` function reading `auth.uid()`
directly in a `plpgsql` body until now.

**Workout History privacy — unaffected.** No new `SELECT` policy was added to `workouts`/
`workout_exercises`/`workout_sets`; `get_my_profile_summary()`'s `workoutCount` subquery runs under
the caller's own existing `select own workouts` policy (`owner_id = auth.uid()`), so it can only ever
count the caller's own rows. Completed Workout detail remains fully owner-private — this slice
exposes one aggregate number about one's own history, nothing else, and nothing about anyone else's.

**Cross-account read boundary — deliberately not built here.** Nothing in this slice makes
`profiles` broadly readable, and no sanitized public-profile view/RPC exists yet. Viewing another
user's profile, follower/following lists, Routine-sharing creator attribution, and social discovery
are all real future needs this schema does not block, but none of them are implemented — in
particular, whether `workoutCount`/`bio` should ever become visible cross-account (the way Hevy
shows workout counts publicly) is an **open product decision**, not something resolved by adding
`get_my_profile_summary()`, which is owner-only by construction (derives its account exclusively from
`auth.uid()`, cannot be pointed at another user).

**Web data-layer changes**: `useOwnProfile.ts` now also selects/returns `bio` (read-only, same
throw-on-real-error convention as before). A new `web/src/auth/profileSummary.ts` adds a
`ProfileSummary` type, `getMyProfileSummary()` (calls `.rpc('get_my_profile_summary')`), and a
`useMyProfileSummary()` hook following `useOwnProfile.ts`'s exact pattern — zero consumers yet, since
the Profile header UI itself is a later slice. `queryKeys.profile.summary(userId)` was added
alongside the existing `profile.detail(userId)` key, following the same `[domain, userId, ...]`
account-isolation shape every other key in this file uses. No changes to `ProfilePage.tsx`,
`ProfileSetupForm.tsx`, `App.tsx`, or routing — no Follow button, no lists, no other-user profile
page; this slice is backend-first.

**Validation performed**: all 15 migrations (the full chain, not just these three) applied cleanly
in order from scratch on a disposable local Postgres 16 container. Adversarial matrix covered: bio
boundary values (`NULL`, 1, 280 valid; 281 rejected by `CHECK`; empty string retained as-is, no
server-side normalization); an account updating another account's bio (rejected, zero rows
affected); follow / duplicate-follow (`23505`, no duplicate row) / self-follow (rejected by `CHECK`)
/ forged-follower-id insert (rejected by RLS) / cross-account delete of someone else's edge (rejected,
zero rows affected) / unfollow (removes exactly one edge); follow read privacy (a user sees only
edges they are a party to); `anon` denial on `follows` insert/delete, `profiles` update/insert, and
`get_my_profile_summary()` execute; `get_my_profile_summary()` for an account with a profile + one
completed Workout + follow edges (all fields/counts correct) and for an account with **no** profile
row (`hasProfile: false`, null identity fields, correct non-zero counts, no error); an unauthenticated
call under the `authenticated` role (no JWT claim set) raising `not authenticated` rather than
returning stale/wrong data; and a three-account follower/following count scenario (A→B, C→B, B→C)
verified via `get_my_profile_summary()` called as B, returning `followerCount: 2, followingCount: 1`
matching the authoritative edge set exactly. The container was torn down after verification.
**Not applied to any live project by this change** — same CLI/sandbox limitation as every other
migration in this document; `supabase db push` was not run.

## Daily Movement / Steps (backend Slice C)

`daily_activity`
([`20260919120000_create_daily_activity.sql`](../../supabase/migrations/20260919120000_create_daily_activity.sql))
is the backend for the future Web Statistics tab's Movement chart. It is a **normalized private
daily aggregate**, not a raw sensor/health-event warehouse — there is no `step_samples`,
`health_events`, `device_samples`, or per-source table. The producer/consumer picture this
establishes:

```
many future device/source observations (Phone, Watch, HealthKit, Health Connect, ...)
  -> a future ingestion/deduplication layer (NOT built here)
  -> one normalized daily total per (account, local date)
  -> daily_activity
  -> Web Statistics (read-only, later)
```

Do not read this as HealthKit or Health Connect integration existing — none does. Do not read it as
Premium existing — no `is_premium`/`plan`/`subscription`/`tier` concept touches this table; the
underlying schema stores correct data regardless of any future entitlement decision about how much
history a free vs. paid account can see.

**Table contract**: `user_id uuid not null default auth.uid() references auth.users(id) on delete
cascade` — the same client-omittable-owner pattern as `workouts.owner_id`, so a writing client's
payload never states its own identity. `activity_date date not null` — the same "explicit local
calendar day, supplied by the client, never derived by truncating a UTC timestamp" convention as
`workouts.workout_date`; a future ingestion layer is responsible for resolving the correct local date
before writing here. `steps integer not null check (steps >= 0)` — no arbitrary maximum. `updated_at
timestamptz not null default now()` — the one minimal fact kept about *when* the current total was
last replaced, without keeping *which* source replaced it; justified because it is a prerequisite for
any future staleness reasoning once multiple device sources exist, even though this table performs
none of that reasoning itself. `primary key (user_id, activity_date)` — one authoritative row per
account per local day.

**No source/provenance columns** (`source`, `device_id`, `health_platform`, ...) — deliberately
deferred. Multiple real ingestion sources don't exist yet, so a provenance column would be
speculative schema for a problem this project doesn't have.

**Missing vs. zero — preserved deliberately.** `NO ROW` for a date means "TBDFit has no movement
data for that day"; a row with `steps = 0` means "TBDFit has data indicating zero steps." Nothing in
this table/policy/query ever fabricates a zero row for a missing day — that stays a future chart's
presentation decision, not this backend's.

**Write contract — plain RLS-protected upsert, no RPC, no `SECURITY DEFINER`.** A single row, single
table, no cross-table atomicity requirement (unlike `record_completed_workout`'s multi-table
aggregate) — RLS alone fully expresses the invariant:

```sql
insert into public.daily_activity (activity_date, steps)
values ($1, $2)
on conflict (user_id, activity_date) do update
  set steps = excluded.steps, updated_at = now();
```

This stores the account's **current normalized total**, never an accumulating counter — the backend
never computes `existing_steps + submitted_steps`, because a future phone/watch integration may
resubmit a complete corrected total for the same day (health platforms can legitimately revise a
total downward during their own source deduplication); blind accumulation would double-count or
diverge from truth. A forged `user_id = <someone else>` fails the `INSERT`'s `WITH CHECK` before
`ON CONFLICT` resolution is ever reached; an `UPDATE`/`DELETE` targeting a row the caller doesn't own
matches zero rows under the `USING` policy — verified adversarially (see below).

**Multi-device conflict arbitration is explicitly out of scope for V1.** If a future Device A submits
10,000 steps and an older Device B later submits 8,000 for the same day, this table does not assume
`MAX(steps)` is correct — a health platform's own deduplication can legitimately lower a total. V1
supports authoritative replacement/upsert only; stale-source arbitration (revisions, vector clocks,
device-priority rules) is deferred to whatever future ingestion/sync layer actually reconciles
multiple real device sources, since no such layer or second source exists yet to design against.

**RLS model — same owner-only pattern as every other table in this schema**: `select`/`insert`/
`update`/`delete` all scoped `to authenticated` with `(select auth.uid()) = user_id`; `revoke all ...
from anon`. Delete is included — not required by this slice's brief, but a deliberate, harmless
extension of the same owner-may-delete-their-own-row precedent `workouts` already established, not an
oversight.

**Movement and Profile/Social are separate security domains.** A `follows` edge between two accounts
grants **zero** extra visibility into `daily_activity` — following someone is a Profile/Social-graph
concept, not an activity-data grant, and this table's policies never reference `follows` at all.
Verified adversarially: A following B does not grant A any rows from B's `daily_activity`.
`get_my_profile_summary()` was not touched by this slice and was verified to return no step/activity
fields at all.

**Read contract — plain RLS-protected `SELECT`, no RPC/view.** Unlike `get_my_profile_summary()`
(which exists specifically to avoid an N+1 fan-out across three unrelated tables), a single-table
date-range read has no such problem to solve:

```sql
select activity_date, steps
from public.daily_activity
where user_id = auth.uid() and activity_date between $1 and $2
order by activity_date;
```

**Range query / index strategy**: the composite primary key `(user_id, activity_date)` already serves
both expected shapes — the point-upsert (`user_id = ? and activity_date = ?`, an exact PK lookup) and
the range-read (`user_id = ? and activity_date between ? and ?` ordered by `activity_date`, since
`activity_date` is the PK's trailing column for a fixed leading `user_id`). No additional index was
added without a demonstrated query need beyond these two shapes; no weekly/monthly denormalization,
no precomputed chart series — the underlying data stays simple and pricing/feature-agnostic.

**Web data-layer changes**: new `web/src/data/dailyActivity.ts` — `DailyActivityPoint { date, steps }`
and `getDailyActivity(startDate, endDate)`, read-only, mirroring the exact query above. Deliberately
**no** insert/update/upsert function and no mutation hook — Web currently has no product requirement
to submit step data (no ingestion source exists on Web), so a write path would be dead code; adding
one is a Web product decision for whenever a real Web ingestion source exists, not implied by this
slice. Also deliberately **no hook** (`useDailyActivity`) in this file: this repo's actual data-layer
convention (see `data/routines.ts` and its callers, e.g. `HomePage.tsx`) is a plain fetch function in
`data/*.ts`, with `useQuery` and the `useAuth()`-derived `userId` wired at the consuming
page/component — `useOwnProfile`/`useMyProfileSummary` embedding that wiring themselves is specific
to `auth/`'s identity concerns, not the general data-layer pattern; a future Statistics-tab page wires
`queryKeys.dailyActivity.range(userId, start, end)` + `getDailyActivity` itself, exactly as
`HomePage.tsx` does today for `listMyRoutines`. `queryKeys.dailyActivity.range(userId, startDate,
endDate)` was added to `queryKeys.ts`, scoped by both account and the requested range (two different
ranges for the same account are genuinely different result sets, not a staleness concern). No changes
to any page, chart, or Profile UI.

**Validation performed**: all 16 migrations (the full chain) applied cleanly in order from scratch on
a disposable local Postgres 16 container. Adversarial matrix covered: own insert, upsert-replaces
(not accumulates — a second upsert of the same date left exactly one row with the new value, not the
sum), `activity_date` round-trips exactly with no UTC-truncation drift, `steps = 0` valid, `steps <
0` rejected by `CHECK`, a missing date returns zero rows rather than a fabricated zero, a forged
`user_id` on insert rejected by RLS, a cross-account `UPDATE`/`DELETE` both matched zero rows (target
row confirmed unmodified afterward), cross-account `SELECT` returns nothing in either direction,
`anon` denied on both read and write, a `follows` edge granting no activity-data visibility, and
`get_my_profile_summary()` confirmed to carry no step/activity fields. One local-test-shim gap was
found and fixed while running this suite (not a product/security issue): the reusable
`auth.users`/`auth.uid()` shim used throughout this document's adversarial tests was missing `GRANT
USAGE ON SCHEMA auth` / `GRANT EXECUTE ON FUNCTION auth.uid()` to `authenticated`/`anon`, which the
real Supabase platform grants by default — this had never surfaced before because every earlier
`auth.uid()` call in this schema ran either inside an RLS policy or inside a `SECURITY DEFINER`
function body (both bypass the calling role's own schema grants), and this slice's test script was the
first to call `auth.uid()` directly from an ad-hoc query issued as `authenticated`/`anon`. The
container was torn down after verification. **Not applied to any live project by this change** — same
CLI/sandbox limitation as every other migration in this document; `supabase db push` was not run.

## Scheduling / Calendar Planning (backend Slice D)

`scheduled_sessions`
([`20260920120000_create_scheduled_sessions.sql`](../../supabase/migrations/20260920120000_create_scheduled_sessions.sql))
is the backend for the future Web Profile Calendar. It is a **user-owned occurrence of an existing
training plan (Routine or ProgramSession) at a specific future instant** — a plan *reference*, not a
plan *snapshot*:

```
Routine or ProgramSession
  -> Schedule (scheduled_sessions)
  -> ScheduledSession, an exact future date/time
  -> Calendar
  -> (later) native client may start that planned session
  -> execution snapshot -> completed Workout (see origin_scheduled_session_id below)
```

A `ScheduledSession` does **not** copy Routine/ProgramSession exercises/sets at scheduling time — it
points at the current plan by id. Editing the source plan after scheduling but before execution is
accepted V1 behavior (a renamed Routine shows its new name in a future Calendar read); snapshotting
happens exactly once, at native execution start, which is what turns a plan reference into a Workout
— the same `PLAN/INTENT -> START SNAPSHOT -> EXECUTION AUTHORITY -> RECORDED RESULT` invariant
already established for Routine -> ProgramSession copying and for completed Workout History.

**What this is not, this slice, deliberately**: not a Workout, not an execution state of any kind,
not a reminder/notification itself (no push tokens, no delivery log, no scheduled job), and not a
state machine — there is no `PLANNED`/`STARTED`/`COMPLETED`/`MISSED`/`ABORTED`/`CANCELLED` status
column. "Row exists" **is** "currently scheduled"; "row deleted" **is** "unscheduled." Whether a
completed Workout resulted from a given occurrence is answered by
`workouts.origin_scheduled_session_id` (below), never inferred from same-date/same-Routine matching.
Also not recurring — one row is one explicit occurrence, no `RRULE`/cron/`repeat_interval` today.

**Table contract**: `id uuid primary key default gen_random_uuid()` — unlike `workouts.id`, there is
no idempotent-retry requirement here (a plain RLS-protected single-row `INSERT`/`UPDATE`/`DELETE`, no
aggregate RPC), so this follows `routines.id`/`programs.id`'s server-generated convention instead.
`owner_id` — the same client-omittable-owner pattern as `workouts.owner_id`/`daily_activity.user_id`.
`routine_id`/`program_session_id`, both nullable FKs, `on delete cascade` (the deliberate *opposite*
of Workout History's provenance columns — a ScheduledSession is future planning intent tied to a
source plan, so when the source plan disappears, there is nothing left to schedule and the occurrence
disappears with it; completed Workout History instead survives source deletion, since it is
historical truth, not future intent). `scheduled_at timestamptz not null` — the actual instant.
`scheduled_timezone text not null` — the user's intended timezone context, preserved *alongside* the
instant rather than discarded, required for later displaying the same wall-clock time across a DST
transition and as a prerequisite for a future reminder subsystem without a schema redesign then; a
real IANA identifier (`Europe/Stockholm`), never a fixed-offset abbreviation like `CET`. Both
`created_at` and `updated_at` are kept (unlike `daily_activity`, which needed only one) — a
`scheduled_sessions` row is a mutable planning object, edited via reschedule potentially many times,
and "originally planned" vs. "last changed" are two different useful facts for a future Calendar UI.
No trigger — a reschedule's own `UPDATE` sets `updated_at = now()` explicitly, the same convention
`daily_activity`'s upsert already established.

**Timezone validation** queries Postgres's own tzdata-backed `pg_timezone_names` view via
`is_valid_iana_timezone(text)`, rather than a hand-maintained whitelist that would silently drift from
whatever timezone database Postgres actually ships:

```sql
create or replace function public.is_valid_iana_timezone(tz text)
returns boolean language sql stable as $$
  select exists (select 1 from pg_timezone_names where name = tz)
$$;
```

This is `STABLE`, not `IMMUTABLE` in the strictest sense (a Postgres/tzdata upgrade could in principle
change `pg_timezone_names`' contents) — atypical for a function backing a `CHECK` constraint, but an
established real-world pattern for exactly this problem, and dramatically more correct than a
hand-written list of abbreviations. `REVOKE`/`GRANT` is applied for this repo's own function-hygiene
convention, not because of any real privilege risk (it's a pure read-only system-view lookup).

**Exactly one plan source, enforced at the database level**: `constraint
scheduled_sessions_exactly_one_source check ((routine_id is not null) <> (program_session_id is not
null))` — both-null and both-set are equally invalid. Explicit FKs to the two real source tables, not
a generic polymorphic `(source_type, source_id)` pair — there are exactly two schedulable plan kinds
today, and a generic pair would trade a real, database-checkable FK for a text discriminator plus
manual polymorphic-integrity discipline for no benefit at two known cases.

**Multiple sessions per day are explicitly valid** — no `UNIQUE(owner_id, date)` or
`UNIQUE(owner_id, routine_id, date)` constraint exists. Each `ScheduledSession` is its own independent
occurrence; a user's day may contain several planned training events, including repeats of the exact
same source plan.

**Source ownership validation — the one genuinely tricky part of this slice.** A bare FK only proves
the referenced Routine/ProgramSession row exists, never that it belongs to the caller. Routine
ownership is direct (`routines.owner_id`); ProgramSession ownership is indirect — `program_sessions ->
program_weeks -> programs -> owner_id`, the *exact* three-table join chain every existing
`program_sessions`/`program_session_exercises` RLS policy already uses, reused verbatim here rather
than reinvented. Both checks are expressed as `EXISTS` subqueries directly inside the `INSERT`/
`UPDATE` `WITH CHECK` clauses:

```sql
with check (
  (select auth.uid()) = owner_id
  and (routine_id is null or exists (
    select 1 from public.routines r where r.id = routine_id and r.owner_id = (select auth.uid())
  ))
  and (program_session_id is null or exists (
    select 1 from public.program_sessions ps
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where ps.id = program_session_id and p.owner_id = (select auth.uid())
  ))
);
```

**Write architecture — plain RLS-protected `INSERT`/`UPDATE`/`DELETE`, no RPC, no `SECURITY
DEFINER`.** The apparent complication ("ordinary FK constraints alone don't enforce cross-table
ownership") does not actually require a privileged function to solve: unlike a plain table `CHECK`
constraint, an RLS `USING`/`WITH CHECK` clause *can* contain arbitrary subqueries and joins, so the
ownership-chain verification above is fully expressible declaratively, evaluated under the caller's
own privileges. This keeps the same "prefer `SECURITY INVOKER` + RLS when it already expresses the
rule correctly" principle already established for `follows` and `daily_activity` — there is no
multi-table write-atomicity requirement here (unlike `record_completed_workout`'s aggregate write) to
justify a `DEFINER` function. A reschedule is just an `UPDATE` of `scheduled_at`/`scheduled_timezone`
(caller sets `updated_at = now()` explicitly); an unschedule is just a `DELETE` — physical deletion is
the accepted V1 semantic, no revision history for schedule edits is kept.

**RLS model — same owner-only pattern as every other table in this schema**: `select`/`insert`/
`update`/`delete` all scoped `to authenticated` with ownership checks as above; `revoke all ... from
anon`. **Scheduling is private planning data, a separate security domain from Profile/Social**: a
`follows` edge between two accounts grants zero schedule visibility — none of this table's policies
reference `follows` at all, and `get_my_profile_summary()` (untouched by this migration) returns no
schedule/calendar field of any kind. Verified adversarially.

**Completed Workout linkage** — `workouts.origin_scheduled_session_id`
([`20260920130000_add_workout_schedule_provenance.sql`](../../supabase/migrations/20260920130000_add_workout_schedule_provenance.sql)),
`on delete set null` (the deliberate *opposite* of `scheduled_sessions`' own cascade from its source —
a completed Workout is permanent historical truth; unscheduling or deleting the `ScheduledSession` it
fulfilled must never delete, block, or otherwise affect that Workout, only clear the provenance link).
This is what lets a future Calendar distinguish (A) a planned session with no completed Workout yet,
(B) a planned session whose execution produced a specific Workout, and (C) an ad-hoc completed Workout
that was never scheduled (`origin_scheduled_session_id = NULL`, fully valid) — never inferred from
same-date/same-Routine coincidence.

**This is a forward migration, not an edit to the already-committed
`20260917120000_create_workout_history.sql`.** That migration is committed to this repository's git
history and is treated as immutable from this point on, independent of live-remote-push status.
`record_completed_workout(...)` is extended via `CREATE OR REPLACE FUNCTION` in the new migration
instead — the same pattern `20260914120000_add_routine_exercise_note_and_rest_timer.sql` already used
to replace `save_routine(...)` in a forward migration rather than editing `create_routines.sql`.

**Idempotency impact**: `origin_scheduled_session_id` is now part of the canonical persisted aggregate
a same-id/same-owner retry is compared against, using the exact typed-value comparison discipline
`record_completed_workout` already established (see the Workout History section above). A retry that
resubmits the same workout id/owner but a *different* `origin_scheduled_session_id` is therefore now
correctly rejected as a conflicting retry — the same treatment a changed performed weight already got
— since which planned occurrence a completed Workout fulfilled, if any, is semantically meaningful
data, not incidental metadata safe to silently overwrite. Nested Exercise/Set reconstruction ordering
is untouched (`order by position` on both sides, already deterministic before this change).

**Web**: left entirely untouched by this slice, deliberately. Unlike `dailyActivity.ts`/
`profileSummary.ts` (simple single-table reads with an obvious, low-risk shape), a genuinely useful
`getScheduledSessions(...)` read needs to decide how to efficiently return enough source display
information (a Routine's name, or a ProgramSession's name/week/program) to avoid per-row N+1 fan-out —
that shape depends on decisions the Calendar frontend slice hasn't made yet (see "Calendar display
information" below). Building it now risked guessing a join shape that gets thrown away; this is
better decided once the actual Calendar frontend slice defines what it needs.

**Calendar read contract** (documented for the future frontend slice, not built here): two
account-scoped, RLS-protected queries — `scheduled_sessions` in a time range, and `workouts` in a
local-date range — composed client-side, the same "simple server-state composition over unnecessary
SQL presentation logic" preference already applied to `daily_activity`'s read contract. No narrow
calendar RPC was judged necessary for this slice; if the two-query composition later proves genuinely
awkward, that can be revisited with real frontend requirements in hand.

**PLANNED / FUTURE DIRECTION — NOT IMPLEMENTED.** The following are explicitly *not* built by this
slice; do not treat any of it as existing:

- **Reminders / notifications**: `scheduled_at` + `scheduled_timezone` exist specifically so a future
  reminder subsystem needs no schema redesign — `ScheduledSession -> future reminder service ->
  notification before/at scheduled time -> Phone/Watch -> user starts or declines/postpones ->
  Workout execution`. No push tokens, no APNs/FCM, no reminder table, no notification preferences, no
  delivery log, no cron/scheduled job of any kind exists today.
- **Start confirmation**: the future native flow `ScheduledSession -> user presses Start -> active
  local Workout execution -> completed Workout -> origin_scheduled_session_id links result to plan`
  is documented intent only — no active remote Workout state exists, and Web still does not and will
  not execute Workouts.
- **Cancelled / missed / aborted semantics**: three genuinely different future concepts —
  *planning cancellation* (deciding not to train before the scheduled time), *missed* (scheduled time
  passed with no execution, possibly a derived state later), and *aborted* (execution started, then
  stopped) — are deliberately **not** collapsed into one field today. No `scheduled_sessions.status`,
  `workouts.status`, or `missed`/`aborted`/`cancelled` boolean exists. Exact state-transition design
  is a **FUTURE DECISION REQUIRED** — do not let a future change add a status field casually without
  revisiting this distinction.
- **Recurrence**: V1 `ScheduledSession` is one explicit occurrence only. No `RRULE`, `recurrence_id`,
  `repeat_interval`, or cron syntax exists. The current model does not make recurrence impossible
  later, but nothing here implements it.

**Premium-agnostic**: no `is_premium`/`plan`/`subscription`/`tier` field touches `scheduled_sessions`;
the schema stores correct data regardless of any future entitlement decision about scheduling/reminder
limits.

**Validation performed**: all 18 migrations (the full chain, not just these two) applied cleanly in
order from scratch on a disposable local Postgres 16 container. Adversarial matrix covered: A
scheduling A's own Routine (succeeds) and A's own ProgramSession (succeeds); A attempting B's Routine
by known id (rejected by RLS) and B's ProgramSession by known id (rejected by RLS through the full
three-table ownership chain — the single most important check in this slice); exactly-one-source
(routine-only valid, program-session-only valid, both-set rejected by `CHECK`, neither-set rejected by
`CHECK`); multiple ScheduledSessions on the same day/same source (allowed, no uniqueness violation);
valid timezone round-tripping exactly, invalid timezone (`CET`) rejected by `CHECK`; reschedule
(own succeeds including a combined time+timezone change; cross-account update matches zero rows,
target row confirmed unmodified); unschedule (own succeeds; cross-account delete matches zero rows;
`anon` denied read and write entirely); source-delete cascade (deleting a Routine removes its
ScheduledSession; deleting an entire Program removes its ProgramSession's ScheduledSession; no
completed Workout is ever affected by either); a `follows` edge granting zero schedule visibility;
`get_my_profile_summary()` confirmed to carry no schedule/calendar field. Workout-schedule-provenance
matrix: A's Workout referencing A's own ScheduledSession succeeds and stores the link; an ad-hoc
Workout with `originScheduledSessionId = NULL` succeeds; A's Workout referencing B's ScheduledSession
id is rejected with zero partial write; deleting a ScheduledSession after a Workout references it
leaves the Workout fully intact with `origin_scheduled_session_id` now `NULL` and all
Exercise/Set snapshot data untouched; deleting the source Routine after Workout completion leaves the
Workout intact. Idempotency regression: an equivalent-payload retry (including a resubmission with
different timestamp/numeric text formatting) remains a no-op; a retry with a changed performed weight
remains a conflict; a retry with the *same* payload but a *changed* `originScheduledSessionId` is now
also correctly rejected as a conflict (the new case this slice adds); a different-owner retry remains
rejected. The container was torn down after verification. **Not applied to any live project by this
change** — same CLI/sandbox limitation as every other migration in this document; `supabase db push`
was not run.

## Web Profile UI (frontend consumer of Slices A/C/D)

A pure-frontend task — no new migration, no backend change of any kind. This section documents what
`web/src/pages/ProfilePage.tsx` and its supporting components actually consume today, now that the
Profile Core + Social (`Slice A`), Daily Movement (`Slice C`), and Scheduling (`Slice D`) backends
above are all implemented and locally verified.

**Profile header**: `useOwnProfile()` (gating: LOADING/UNAVAILABLE/MISSING/COMPLETE, unchanged from
before this task — an account with no `profiles` row still gets the full app and only sees
`ProfileSetupForm` here) + `useMyProfileSummary()` (`get_my_profile_summary()` — display name,
`@username`, bio, and the three real header counts: Workouts = completed Workout History count,
Followers/Following = real `follows` graph counts). Avatar is an initials placeholder
(`lib/profileDisplay.ts`'s `getInitials`) — no avatar upload/Supabase Storage exists. **Edit
Profile** (`auth/EditProfileDialog.tsx`) calls a new `updateOwnProfile(displayName, bio)`
(`auth/profileCreation.ts`) — Display name and Bio only; **username is read-only** in this dialog,
deliberately: a username-*change* policy (distinct from the already-implemented username-*creation*
uniqueness handling) has not been designed or reviewed, so this task does not invent one.

**Statistics card** (`components/ProfileStatistics.tsx`): one card, two tabs.
- *Movement*: `daily_activity` via `getDailyActivity` (`data/dailyActivity.ts`, pre-existing), a
  7-day/4-week/12-week period selector, an average-steps/day headline computed only over days that
  actually have a row (never divided by the full period length), and `components/StepsChart.tsx` — a
  small hand-rolled inline-SVG bar chart (no chart library added; this app had zero charting
  dependencies and one feature didn't justify adding recharts/d3/chart.js). A day with no
  `daily_activity` row renders as a distinct muted marker, never a zero-height bar — missing data
  must never look like a measured zero.
- *Workouts*: a real weekly workout streak (`lib/workoutStats.ts`'s `calculateWorkoutStreak` — see
  its own doc comment for the exact, deliberate rule: consecutive ISO-8601 weeks with ≥1 completed
  Workout, where an unfinished current week with no workout yet does not immediately zero out a real
  ongoing streak) and an optional workouts-per-ISO-week frequency chart (`workoutsPerWeek`), both
  computed from `workouts.workout_date` via a new read-only `data/workoutHistory.ts`
  (`getCompletedWorkouts`). Deliberately no total volume/reps/sets — not designed, not implemented.

**Calendar card** (`components/ProfileCalendar.tsx`): a real Monday-start month grid
(`lib/calendarMonth.ts`), Previous/Current/Next navigation, day markers (scheduled/completed, not
color-only) backed by a new `data/scheduledSessions.ts` (`getScheduledSessions`, embedding
`routines`/`program_sessions`/`program_weeks`/`programs` via PostgREST's foreign-table select to
avoid N+1) and `getCompletedWorkouts`. A selected day's agenda correlates scheduled sessions to
completed Workouts **solely** via `workouts.origin_scheduled_session_id`
(`lib/scheduleCorrelation.ts`'s `buildDayAgenda`) — never by same-date/same-source inference, so two
same-Routine sessions on one day stay independently correlated and an ad-hoc completed Workout
(`origin_scheduled_session_id = NULL`) still appears, uncorrelated. Times are displayed via
`lib/scheduleTime.ts`'s `formatScheduledTime`/`scheduledLocalDate`, which read the instant in the
session's own `scheduled_timezone` via `Intl.DateTimeFormat`/`Intl.formatToParts` — never the
viewer's browser zone, which is exactly the bug `scheduled_timezone` exists to prevent (see that
column's own migration comment). No date library was added; `zonedLocalTimeToInstant`/
`instantToZonedLocalTime` implement the local-time↔instant conversion with a two-pass
Intl-offset-lookup technique instead.

**Scheduling interactions**: `components/ScheduleSessionDialog.tsx` (schedule new — Routine or
Program Session source picker, reads `listMyRoutines()`/`listMyPrograms()` unchanged; reschedule —
same dialog, source fixed, time/timezone only) and unschedule (`ConfirmDialog`, physical delete, no
Workout/history side effects) all call `data/scheduledSessions.ts`'s plain
insert/update/delete functions directly — no RPC, relying entirely on that migration's own RLS
ownership-chain enforcement, exactly as designed in Slice D. `owner_id` is never sent by the client.

**Web is still not a Workout-execution client** — nothing in this task adds Start/Stop/Finish
Workout, active-Workout state, or live set logging. Scheduling is planning only.

**Not implemented by this task** (documented here so it isn't mistaken for existing): avatar
upload/Supabase Storage, richer Workout statistics (total volume/reps/sets, strength trends),
additional Movement metrics beyond steps, HealthKit/Health Connect ingestion, notifications/
reminders of any kind, recurring scheduled sessions, Premium/entitlement gating, public/cross-account
Profile viewing (Followers/Following remain counts only — no list UI, no other-user Profile page, no
Follow button), and Routine Sharing. `IMPLEMENTED != TEAM ACCEPTED` — this section documents current
behavior, not a ratified design.

Validated via `tsc --noEmit`/`tsc -b`, `oxlint`, `vitest run` (pure-logic unit tests for initials,
streak, weekly frequency, calendar-grid math, timezone conversion/formatting, and scheduled↔completed
correlation — this repo has neither jsdom nor a component-rendering framework, so no component test
was added), and `vite build`. No live browser session was exercised in this sandbox; see the manual
browser test plan delivered alongside this change for what a human should verify before shipping.

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
