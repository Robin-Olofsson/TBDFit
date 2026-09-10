# TBDFit — Session Handoff (2026-09-10)

> **WORKING HANDOFF — NOT AN ARCHITECTURE DECISION.**
> This document is a snapshot for continuing work across a context reset. It is not an ADR, is not
> reviewed for long-term accuracy, and should be treated as stale the moment new commits land.
> Nothing here overrides `docs/architecture/adr/`.

## 1. Product direction (settled, not open)

- TBDFit is a multi-client fitness platform: Android Phone/Wear OS (native execution), Web
  (planning/scheduling/history), Supabase (shared backend, client-agnostic).
- **Web is explicitly NOT a workout-execution client.** Web's scope is: plan Routines/Programs,
  (future) scheduling, and read-only completed-history/statistics/calendar. Starting, logging sets
  during, and finishing a workout is Phone/Watch-only, now and later — this was an explicit
  developer correction this session, not an inference. `HomePage.tsx`'s former "Start Workout"
  button was renamed to "Browse Routines" (navigates to `/plan`, does not start anything).
  `auth/ProductPreview.tsx`'s decorative phone-mockup content showing execution UI was deliberately
  **kept** — it illustrates the native/mobile side of this exact boundary, not a Web capability, and
  the developer explicitly confirmed it should stay.
- **Architectural principle (explicit developer correction, must not be re-violated):** Supabase's
  `authenticated` role means "any signed-in TBDFit account," never "Web" specifically. The
  Web-doesn't-execute restriction is enforced ONLY by Web's own product code choosing never to call
  the write RPC — never by database-level role/claim logic pretending to distinguish clients. Do
  not write future migrations that grant/deny by an assumed client identity.

## 2. Profile / identity model (implemented, stable)

- Shared cross-client contract: **Username required at signup**, `display_name` initialized from
  `username` at profile creation, both editable later. (This reversed an earlier "Display Name only
  at signup" design — username-required is the current, approved state.)
- `profiles.username` — `not null` (from `create_profiles.sql`), `profiles.display_name` — `text
  not null`, backfilled from `username`, added via
  `supabase/migrations/20260912120000_add_profile_display_name.sql` (edited in place this session;
  confirmed never applied to any live project before editing — see migration-immutability note in
  §9).
- Android/Web both read `display_name` first, falling back to `username`, never inventing a name.

## 3. Routine feature (implemented, stable)

- Full CRUD via `save_routine` (Supabase RPC), planning-only — Routines are templates, never
  executed from Web.
- Per-exercise `note` (≤1000 chars) and `rest_timer_seconds` (≥0) —
  `20260914120000_add_routine_exercise_note_and_rest_timer.sql`.
- Per-set `set_type` (`NORMAL`/`WARMUP`/`FAILURE`/`DROPSET`, default `NORMAL`) —
  `20260915120000_add_planned_set_type.sql`.
- Web editor UX (this session): weight ordered before reps; free numeric entry (no stepper
  dropdown) for reps/weight; Set Type and Rest Timer are both compact `SelectField`-based pickers
  (see §5); "Remove set" disabled when only one set remains; redundant "Set N" text label removed
  from the editable Set Type cell (kept only in the read-only `RoutineDetailPage.tsx` view).
  Save (create flow) now navigates back to the Routine list (`/plan`), not the new routine's detail
  page; Save (edit flow) still navigates to `/plan/:id`.
- `RoutineDetailPage.tsx`: **Start Routine and Delete Routine buttons removed** (explicit developer
  request — Delete lives only on the Routine list now, Start doesn't belong on Web at all). Only
  "Edit Routine" remains on this page.
- `PlanPage.tsx` (Routine list): per-row `RowActionsMenu` (⋯) with **Duplicate** (new
  `duplicateRoutine()` in `data/routines.ts`, reuses `toRoutineExerciseDrafts()`) and **Delete**
  (opens shared `ConfirmDialog`, destructive styling). `window.confirm` is gone from this page.

## 4. Exercise catalog (implemented, stable)

- Built-in catalog expanded from 6 → **30 exercises**, curated V1 list, both sides:
  - Supabase: `20260916120000_expand_builtin_exercise_catalog.sql` (pure data insert, 24 new rows,
    no schema/RLS change; header comment records curation tradeoffs — no grip/angle variants, Leg
    Curl/Calf Raise recorded generically as `MACHINE`).
  - Android: `BuiltInExerciseCatalog.kt`'s `BUILT_IN_EXERCISE_SEED` extended to the same 30
    `(id, name)` pairs in the same order (diffed against the migration this session to confirm
    exact match); a new `BuiltInExerciseCatalogTest.kt` drift-detection test asserts the exact
    30-entry list so a future one-sided edit fails CI.
- Identity contract: built-ins use fixed `builtin_*` string slugs; customs use client-generated
  UUID-as-text. Android has no `exercise_type`/`equipment` field — those are Web+Supabase-only
  metadata.
- Type/Equipment filters and the Custom Exercise modal in `ExerciseLibraryPanel.tsx` use the new
  `SelectField` component (see §5).

## 5. Web UX systems (implemented this session, stable)

- **Toasts** (`lib/toast.ts`): Sonner-based, non-blocking, `<Toaster theme={getAppColorScheme()}
  position="top-center" />` rendered as a persistent sibling across all `App.tsx` phase branches.
  `getAppColorScheme()` (`lib/theme.ts`) derives light/dark from the CSS's own `color-scheme`
  declaration (app has no real light/dark toggle) rather than a hardcoded `'dark'`. Current call
  sites: `notifyRoutineSaved`, `notifyRoutineDeleted`, `notifyRoutineDuplicated`,
  `notifyProgramDeleted`, `notifyProgramWeekDeleted`, `notifyProgramSessionDeleted`,
  `notifyError(message)`. Built on a generic `notifyPromise<T>(promise, {loading,success,error})`
  wrapper — reuse this for any future async mutation instead of inventing a new pattern.
- **Blocking dialogs**: all native `window.confirm`/`alert`/`prompt` removed app-wide, replaced
  with `ConfirmDialog.tsx` (destructive confirmations, `role="alertdialog"`) and `PromptDialog.tsx`
  (name-entry, e.g. Create Program, plain `role="dialog"`). Both wrap a hardened `Modal.tsx` that
  now has real focus-trap + focus-restore (stores/restores `document.activeElement`, traps
  Tab/Shift+Tab inside the dialog).
- **Dropdown/popup-list design system** (developer explicitly asked this be the standard for all
  future popups): shared CSS primitives `.dropdown-panel` / `.dropdown-item` /
  `.dropdown-item-danger` / `.dropdown-item-active` in `index.css`, documented in `web/README.md`'s
  "Dropdown menus / popup lists" section. `SelectField.tsx` is the canonical generic
  field-styled dropdown (replaces any native `<select>` where visual consistency matters) —
  `RestTimerPicker.tsx` is now a thin wrapper around it, and the Set Type selector uses it in a
  compact 40px-square variant (`triggerLabel` prop). **All native `<select>` elements in the app
  have been migrated** — this was confirmed as a completed sweep, not partial.
  - **Known deliberately-untouched popup**: the exercise/routine chip-picker (multi-select chips,
    not a single-value dropdown — different interaction shape, was explicitly flagged as a
    candidate but not converted; revisit only if asked).
- Icon consistency sweep completed: no raw HTML entities (`&larr;`) or bare glyphs (`×`, `✓`, `→`)
  remain — all replaced with `lucide-react` icons (`ArrowLeft`, `Trash2`, `Check`, `ChevronRight`).

## 6. Backend Slice B — Completed Workout History (implemented, UNVERIFIED against live DB)

New migration `supabase/migrations/20260917120000_create_workout_history.sql`, the first Workout
tables to ever exist in Supabase (confirmed zero pre-existing `workout*` tables before this).

- **Tables** (all owner-scoped RLS, no `anon` access):
  - `workouts`: `id uuid` **client-generated PK** (not `default gen_random_uuid()` — deliberate, see
    idempotency note in §7), `owner_id uuid not null default auth.uid() references auth.users(id)
    on delete cascade`, `name`, `origin_routine_id` (FK to `routines`, `on delete set null`),
    `workout_date date`, `started_at`/`completed_at timestamptz` (CHECK `completed_at >=
    started_at`), `created_at`. Grants: `select, delete` only to `authenticated` — **no insert/update
    grant to anyone**; the only write path is the RPC below.
  - `workout_exercises`: FK to `workouts` cascade, `exercise_id` (FK to `exercises`, `on delete set
    null`, traceability only) **plus a full snapshot** of `exercise_name` (not null),
    `exercise_type`, `equipment`, `note`, `rest_timer_seconds` taken at record time. This is a
    deliberate divergence from Android's local Room design (which never snapshots the name) —
    Supabase's Workout History is a durable cross-client record and must survive the source
    Exercise being renamed, deleted, or having its metadata changed later. `position` +
    unique-per-workout.
  - `workout_sets`: FK to `workout_exercises` cascade, `position` unique-per-parent, `set_type`
    (default `NORMAL`), `target_reps`/`target_weight` (the plan snapshot) and `reps`/`weight` (what
    was actually performed) — both pairs nullable, both independently CHECK ≥0.
- **Sole write path**: `record_completed_workout(p_workout jsonb) returns uuid`, `SECURITY DEFINER`,
  `set search_path = public, pg_temp`, ownership derived only from `auth.uid()` (never a
  caller-supplied `owner_id`), revoked from `public`/`anon`, granted execute only to `authenticated`.
  Validates `startedAt`/`completedAt` present and ordered, `workoutDate` present; re-checks exercise
  visibility (`e.owner_id is null or e.owner_id = v_uid`) for every referenced exercise before
  inserting — this is the same "FK bypasses RLS" fix pattern established earlier for
  `routine_exercises`. Positions are always assigned from array order server-side, never
  caller-supplied.
- Validated adversarially against a disposable local Postgres 16 container (cross-account
  isolation, anon denial, FK-bypass-RLS checks) — **not** against the real Supabase project (see §10:
  this sandbox cannot reach it, and the CLI is unauthenticated).
- Android's current `WorkoutEntity`/`WorkoutExerciseEntity`/`WorkoutSetEntity` (Room) were read in
  full this session for grounding; nothing on the Android side has been changed yet — this
  migration is backend-only, Slice B does not include an Android sync implementation.

## 7. ✅ RESOLVED — `record_completed_workout` idempotency-conflict review

**Fixed and locally verified this session** (2026-09-10). Previously this returned the existing id
as a silent no-op for *any* same-id-same-owner call, regardless of payload content — see git history
of this file for the original writeup if needed.

**Fix**: `record_completed_workout` now reconstructs the canonically-persisted aggregate (from the
actual `workouts`/`workout_exercises`/`workout_sets` rows, through the same typed columns the
original write used) and compares it against the incoming payload, parsed through the exact same
casts the function uses to write. Comparison is by typed value, not raw JSON text, so representation
differences (`Z` vs `+00:00`, `100` vs `100.0`) don't cause false conflicts.

- Same id + same owner + **equivalent** payload → idempotent no-op, existing id returned, nothing
  re-written.
- Same id + same owner + **different** payload → rejected with a Postgres exception (`a workout with
  id % already exists with different content — conflicting retries are not accepted`); the persisted
  historical aggregate is left completely untouched (no rows are written on this path at all — every
  insert in the function is guarded by `not v_row_exists`).
- Same id + different owner → rejected, unchanged from the original design.

Adversarially verified against a disposable local Postgres 16 container (all 12 migrations applied
in order, then): initial write → retry with differently-formatted-but-equivalent payload (returns
same id, row counts unchanged) → retry with a changed set weight (rejected, persisted weight
confirmed unchanged) → cross-owner id reuse (rejected) → anon call (permission denied). Also ran
`tsc --noEmit`, `tsc -b`, `oxlint`, `vitest run` (81 tests passed), and `vite build` — all clean (no
Web code changed, this was a SQL-only fix, but the full check suite was run per repo convention).
No migration was pushed remotely.

## 8. Research completed, NOT implemented

- `docs/product/routine-sharing-research.md` — full Routine Sharing via Public Link research
  (22 sections), explicitly READ-ONLY / RESEARCH ONLY. No implementation started, no migration
  written. Do not treat any schema/RLS shape described there as decided.
- Profile Backend Architecture Audit (bio, social/follows, workout history, daily activity,
  scheduling — 17 sections) — read-only audit, no migration written except that its Workout History
  section became the basis for the Slice B spec that WAS implemented (§6). Its recommended slice
  ordering (Profile/Social Slice A → Daily Activity Slice C → Scheduling Slice D → Web Profile
  frontend Slice E) has not been started and is not yet re-confirmed after the Web-execution-boundary
  correction — re-derive from current code before starting any of it, don't assume the audit's
  sketch is still exactly right.

## 9. Migration immutability status

Standing rule: never edit an applied migration. Two narrow, already-closed exceptions exist:
`20260912120000_add_profile_display_name.sql` and `20260913120000_adopt_rls_auto_enable.sql`
(renamed from `20260913120000_revoke_rls_auto_enable_execute.sql`) — both confirmed, before editing,
to have never been applied to any live project. Do not treat these as a precedent for editing a
migration without first confirming (via `npx supabase migration list` against the real project, once
that's possible — see §10) that it was never applied live.

`20260913120000_adopt_rls_auto_enable.sql` captures the exact live `rls_auto_enable()` function body
(force-enables RLS on new `public` schema tables via an event trigger, `SECURITY DEFINER`,
`search_path='pg_catalog'`) plus a deterministic drop/recreate of the `ensure_rls` event trigger and
an unconditional revoke of execute from `public`/`anon`/`authenticated`/`service_role`. This was
validated via a "fresh database vs. live-drift-simulated database converge to an identical final
state" local Postgres test.

## 10. Migration / CLI state (verified fresh this session, NOT from memory)

- `supabase/config.toml` does **not exist** in this repo — confirmed via `test -f`. This project has
  **never been CLI-linked** to any Supabase project from this environment.
- Both `npx supabase migration list` and `npx supabase db push --dry-run` were run fresh this
  session and both failed identically, exit code 1:
  ```
  {"_tag":"Error","error":{"code":"LegacyPlatformAuthRequiredError","message":"Access token not provided. Supply an access token by running `supabase login` or setting the SUPABASE_ACCESS_TOKEN environment variable."}}
  ```
- **Consequence**: this sandbox cannot determine real remote migration status at all — not "all
  applied," not "some pending," genuinely unknown from here. Every migration in
  `supabase/migrations/` must be treated as **status-unknown against any real project** until a
  session with real credentials (`supabase login` or `SUPABASE_ACCESS_TOKEN`) confirms otherwise.
  Do not assume the local-Postgres adversarial validation described throughout this doc is a
  substitute for that confirmation — it validates correctness of the SQL, not whether it has already
  been applied live.
- Full migration file list, oldest to newest (five untracked/new this session are marked):
  1. `20260910120000_create_routines.sql` (pre-existing)
  2. `20260912120000_add_profile_display_name.sql` (edited in place this session, see §9)
  3. `20260913120000_adopt_rls_auto_enable.sql` (renamed + rewritten this session, see §9)
  4. `20260914120000_add_routine_exercise_note_and_rest_timer.sql` *(new, untracked)*
  5. `20260915120000_add_planned_set_type.sql` *(new, untracked)*
  6. `20260916120000_expand_builtin_exercise_catalog.sql` *(new, untracked)*
  7. `20260917120000_create_workout_history.sql` *(new, untracked)*
  (Earlier-dated migrations before `20260910120000` may also exist — check
  `supabase/migrations/` directly rather than trusting this list's completeness for anything before
  Routines; this handoff only tracks what changed this session.)

## 11. Git state (verified fresh this session)

- Branch `main`, working tree **dirty**: 31 tracked files modified, several new untracked files.
- `git diff --stat` (tracked changes only): **31 files changed, 1206 insertions(+), 258
  deletions(-)**.
- Untracked new files: `docs/product/routine-sharing-research.md`, `exersiseinroutine.png`, 4 new
  Supabase migrations (listed in §10), and Web: `ConfirmDialog.tsx`, `PromptDialog.tsx`,
  `RestTimerPicker.tsx`, `RowActionsMenu.tsx`, `SelectField.tsx`, `plannedExerciseDrafts.test.ts`,
  `theme.ts`, `theme.test.ts`, `toast.ts`, `toast.test.ts`.
- **Anomaly, unexplained, flagged not fixed**: `newroutinepage.png` shows as **deleted** in git
  status. No action taken this session (by this coordinator or any fork) is known to have deleted
  it. Investigate before assuming it's safe to `git add -A` — it may be unrelated pre-existing local
  state, but confirm before staging/committing anything in this working tree.
- **Nothing has been committed or pushed this session.** All 31 modified + all new files are
  uncommitted working-tree changes only.

## 12. Fork-delegation pattern (process note for continuation)

Large/multi-file tasks in this session were delegated to `Agent` calls with `subagent_type: "fork"`
— forks inherit full conversation context and run in the background. Before delegating, the
coordinator pre-verifies load-bearing facts directly (Read/grep/local-Postgres test) so the fork
isn't left to guess or re-derive them; after a fork reports back, the coordinator verifies its
actual file output directly rather than trusting the summary. Continue this pattern for
Profile/Social Slice A and later slices — do not skip the pre-verify or post-verify steps.

## 13. Immediate next task (explicit developer instruction)

Do **not** start Profile/Social Slice A, Daily Activity Slice C, Scheduling Slice D, Web Profile
Slice E, or Routine Sharing implementation yet. The next authorized task is exactly: review and fix
`record_completed_workout`'s idempotency-conflict semantics per §7.

## 14. How to resume

1. Read this file in full.
2. Re-run `git status --short` and `git diff --stat` yourself — this file is a snapshot, not a live
   view; if the working tree no longer matches §11, trust the live repo.
3. If real Supabase credentials are available in the new session, run `npx supabase migration list`
   for real and update §10 — do not carry forward "status unknown" once it's actually knowable.
4. Proceed to §7 (`record_completed_workout` idempotency review) as the next task, applying the
   full architectural-review discipline (invariant first, alternatives compared, failure timeline
   traced, local-Postgres adversarial validation) before writing any fix.
