# TBDFit Frontend Prototype Notes

## Status

`UX PROTOTYPE — FOR HUMAN EVALUATION`

This document describes the first clickable Web and Android Phone product prototypes, built to let a
human evaluate TBDFit's proposed information architecture (Home | Workout | You on Phone; Plan /
History / Profile on Web) before further domain/backend implementation. Nothing in this document, and
nothing built for it, constitutes an Accepted product or architecture decision. See:

- `docs/product/product-information-architecture.md` — `DESIGN PROPOSAL — FOR TEAM REVIEW`
- `docs/product/web-information-architecture.md` — `DESIGN PROPOSAL — FOR TEAM REVIEW`
- `docs/architecture/multi-client-product-vision.md` — `DESIGN DIRECTION — FOR TEAM REVIEW`
- `docs/architecture/adr/0005-multi-client-responsibility-strategy.md` — `PROPOSED FOR TEAM REVIEW`

**Core prototyping rule applied throughout**: no fake repositories, no fake persistence layers, no
backend services were created to support prototype screens. Capabilities without a real domain model
(History, Progress, Profile-beyond-username, social) use representative hardcoded content and
local/transient UI state (Compose `remember`/`mutableStateOf` on Phone; React `useState` on Web).
Real, already-implemented capabilities (LocalAccount ownership, Workout/Exercise/WorkoutExercise/
WorkoutSet, start/restore, attach exercise, set logging, `SessionUnavailable` local-workout access,
and — as of two later slices — Routine, real on both Android/Room and Web/Supabase independently) are
preserved unchanged and wrapped into the new shell, not replaced.

---

## Web

### Technology / project structure

New top-level `web/` directory (sibling of `android/`/`apple/`, per ADR-0002's monorepo layout — not a
contradiction of that PROPOSED-status ADR, just an uncontroversial extension already anticipated by
ADR-005). Stack chosen as the smallest reversible option for a pure client-side UX prototype with zero
backend: **Vite + React 19 + TypeScript + React Router (client-side only)**, plain CSS (no component
library, no state-management library, no SSR framework — Next.js/similar would imply server assumptions
this prototype doesn't have and shouldn't pretend to have).

### Web information architecture implemented

Originally exactly the corrected `web-information-architecture.md` §2 nav map: **Plan / History /
Profile**, plus a disabled **Feed (coming soon)** entry (Open Decision #6 — social doesn't exist on any
client yet), with Plan as the landing route (no Home destination — the corrected doc's nav map does not
include one).

**Since amended** by a follow-up authenticated-dashboard redesign task, driven by direct human UX
feedback that the authenticated shell should visually match an approved `/login` reference's laptop
mockup: a **Home dashboard was added as a PROTOTYPE HYPOTHESIS layered on top of that document, not a
replacement of it** — `web-information-architecture.md` itself was not edited and still recommends Plan
as the landing page. Nav is now **Home / Plan / History / Profile** (Feed remains deferred and is no
longer shown at all, not even disabled — see "Authenticated dashboard redesign" below for why). Whether
Home earns a permanent place, or Plan should remain the true landing page with Home removed, is an open
question for human evaluation, not a decided change to the corrected IA doc.

One deviation worth naming explicitly: the review brief's generic section 7 examples suggested a
separate "Exercises" browse/search destination. The corrected Web IA's nav map does not list one, so
exercise search was built **inside** the Routine Builder's "Add Exercise" flow instead of as its own nav
item — staying faithful to the corrected document's 3-item nav rather than the brief's unfiltered
example list.

### Web screens implemented

| Screen | Classification | Notes |
|---|---|---|
| `/login` — auth (sign-in, sign-up, session restore, sign-out) | PRODUCTION-BACKED | Real `@supabase/supabase-js` client against the **same Supabase project and account system as Android** — see "Auth / session (Web)" below |
| `/login` — hero/product-preview visual content | PROTOTYPE/PRESENTATION-ONLY | Marketing copy + a hardcoded desktop/phone product mockup (real HTML/CSS, not an embedded screenshot) — see "Login page visual redesign" below |
| Home (dashboard) | MIXED | Real greeting identity (display name, else username, else email — see "Shared username + display name" in supabase-setup-and-verification.md); Start Workout button and stat cards are still representative prototype content, but "Your Routines" now reads REAL Supabase-backed routines (top 3) — see "Routine Supabase + Web vertical slice" below. "Recent Activity" remains `PROTOTYPE_HISTORY` sample data — History has no backend yet. |
| Plan (Routine Library) | PRODUCTION-BACKED | Real, per-account list fetched from Supabase (`routines`/`routine_exercises`/`routine_planned_sets`, RLS-isolated) — see "Routine Supabase + Web vertical slice" below. Create/Delete are real. A brand-new account correctly sees a real empty state, not a seeded sample list — the 3 sample routines this table previously described no longer exist anywhere in the production-backed path. |
| Routine Detail | PRODUCTION-BACKED | Real per-exercise, per-set display fetched from Supabase by id. `Edit Routine`/`Delete` are real. "Start Routine" still shows the same explicit alert explaining execution-on-Web is an open question, not implemented, and not foreclosed — see below (unchanged from before this slice). |
| Create/Edit Routine (`/plan/new`, `/plan/:routineId/edit`) | PRODUCTION-BACKED | Local draft state (name, exercises, planned sets — add/remove/edit target reps &amp; weight, add exercise via a real exercise picker sourced from Supabase's `exercises` table, inline custom-exercise creation) is held in memory until one Save, which calls the atomic `save_routine` Postgres RPC — see "Routine Supabase + Web vertical slice" below. As of the Program slice, the exercise/set editing UI itself lives in a shared `PlannedExercisesEditor.tsx` component, also used by the Program session editor — see below. |
| Programs (`/programs`) | PRODUCTION-BACKED | New screen — "My Programs" list (name, week count, session count derived from fetched data), Create/Delete — see "Program Supabase + Web vertical slice" below. Reached via its own top-level `Programs` nav item, never nested under Routine. |
| Program Builder (`/programs/:programId`) | PRODUCTION-BACKED | New screen — three-pane builder (Weeks / Sessions in selected week / session editor). Add Week, Duplicate Week, Add Session (from scratch or **Copy from Routine**), Duplicate handled via the same week-duplication RPC, Delete Week/Session, per-session editing (name, exercises, target reps/weight) via the shared `PlannedExercisesEditor.tsx`. See "Program Supabase + Web vertical slice" below. |
| History (list) | PROTOTYPE-ONLY | Table + a folded-in Progress stat row (not a separate nav destination — see Progress note below) |
| Workout Detail | PROTOTYPE-ONLY | Read-only sample workout detail |
| Profile | MIXED | Real signed-in identity (display name, else username, else email — same `profiles` table Android reads, now including `display_name`; see "Shared username + display name" in supabase-setup-and-verification.md) + real sign-out, both now shown directly on the page (previously only in the shell); the Profile *page* content itself (activity, etc.) remains sample data; a minimal Settings section folded in (per Journey W3), Feed marked "Coming soon". **A signed-in account with no `profiles` row yet sees Profile Setup (`ProfileSetupForm.tsx`) here instead of the identity card** — Username + Display Name, real `INSERT` via `profileCreation.ts` on Save. This is local to `/profile` only, not an application-wide gate: every other route (`/`, `/plan`, `/programs`, ...) works normally with no profile at all — see "Shared username + display name" in supabase-setup-and-verification.md. |

### Auth / session (Web)

Added after the initial Web/Phone prototype pass, closing a gap the initial pass left open: the Web
prototype previously opened directly inside the authenticated shell with no login. This is now real,
not prototype data — see `web/README.md` for setup and `web/src/auth/` for the implementation
(`AuthContext.tsx`, `AuthForm.tsx`, `authErrors.ts`, `useOwnProfile.ts`, `profileState.ts`,
`profileCreation.ts`, `ProfileSetupForm.tsx`).

- **One identity across TBDFit**: the Web client points at the exact same Supabase project/anon key
  as Android (copied from `android/local.properties` into `web/.env`, gitignored) — no separate Web
  user system, no second user identifier. Signing in with an Android account works here too.
- States implemented: `RESTORING_SESSION`, `SIGNED_OUT`, `AWAITING_CONFIRMATION`, `SIGNED_IN`,
  `AUTH_ERROR` (reserved for an unexpected session-restore failure, not ordinary wrong-password —
  that's an inline form error instead, mirroring Android's `Result<Unit>`-based `AuthGateway`).
  Session persists across a normal page reload (Supabase's default `localStorage` persistence).
- **Sign-up is email + password only.** TBDFit profile creation (username, display name) is a
  separate, explicit, *optional* step performed later from `/profile` — not onboarding, not gated at
  the application level — see "Shared username + display name" in supabase-setup-and-verification.md
  and the Profile row above.
- Email/password only — Android's Google sign-in is a native Credential-Manager ID-token flow with
  no browser-OAuth-redirect configuration behind it; there was nothing to port, and a *different*
  OAuth-redirect Google flow was out of scope for this task.
- Email confirmation is required on the real configured project — sign-up shows an explicit "check
  your email" state, never a false `SIGNED_IN`.
- **Not done, and cannot be done from this environment**: registering a Web redirect URL (e.g.
  `http://localhost:5173`) in the Supabase Dashboard's Redirect URLs — Android's
  `tbdfit://auth-callback` entry does not cover any Web origin. See `web/README.md` for the exact
  configuration needed. Until this is added, a real confirmation-email click will redirect to a URL
  Supabase rejects (ordinary sign-in for an already-confirmed account is unaffected).
- **Not verified live**: this sandbox's outbound network policy blocks the configured Supabase
  project's domain by default (confirmed via a direct `curl` against the project's own
  `VITE_SUPABASE_URL` — response: `HTTP 403 Blocked by network policy: domain <project-ref>.
  supabase.co:443 ... no matching allow rule`), so no real sign-up/sign-in/confirmation call could
  be executed from here. What *was* verified: build/typecheck/lint/unit tests all pass, and a built
  bundle serves correctly via `vite preview` + `curl`. A developer running this outside the sandbox
  (or after allowing that project's domain via `sbx policy allow network <project-ref>.supabase.co`
  on the host — see the project's own `SUPABASE_URL` in `android/local.properties` for the exact
  domain) should re-verify the live flow end to end.

### Login page visual redesign

`/login` was restyled to closely match an approved visual reference image (a left auth panel / right
marketing-hero split, dark premium theme). This was a presentation-layer change only:

- **`AUTH: PRODUCTION-BACKED`** — unchanged from the "Auth / session (Web)" section above. The
  restyle touched only `AuthForm.tsx` (new, replaces the old `AuthScreen.tsx`) and `LoginPage.tsx`
  (new) — `AuthContext.tsx`'s logic was not modified.
- **`VISUAL HERO CONTENT: PROTOTYPE/PRESENTATION-ONLY`** — the right-hand headline, supporting copy,
  and desktop/phone product mockup (`AuthHero.tsx`, `ProductPreview.tsx`, `DecorativeCurve.tsx`) are
  hardcoded marketing content with no data fetching, built as real HTML/CSS, never an embedded
  screenshot of the reference image.
- Two things were deliberately **not** copied from the reference: (1) its hero mockup's nav labels
  ("Home/Workouts/Routines/Exercises/Progress") were outdated — `ProductPreview.tsx` uses the CURRENT
  real sidebar shape (Plan/History/Profile) instead, so this marketing content can never be mistaken
  for a competing IA proposal; (2) its "Continue with Google" button was omitted entirely — Web has
  no configured Google auth (see above), and a fake button would offer functionality that doesn't
  exist. "Forgot password?" was likewise omitted: no password-recovery method exists in
  `AuthContext.tsx`.
- `/login` is now a real React Router route. A signed-in visit to `/login` redirects to `/plan`; a
  signed-out visit to any other path redirects to `/login`.
- No new frontend framework/library was added (still plain CSS, no Tailwind/component library).
- Manual visual verification was **not performed** — no browser is available in this sandbox.
  Verification consisted of: `tsc -b` (clean), `vite build` (succeeds), `oxlint` (clean, same two
  pre-existing warnings as before, unrelated to this change), `vitest run` (6/6 passing, unchanged),
  and a `vite preview` + `curl` smoke check confirming the built bundle serves. Responsive behavior
  (breakpoints at ~1000px and ~640px) was verified by reading the CSS, not by observing it rendered.
  A developer should open `/login` in a real browser at desktop/tablet/mobile widths before treating
  this as visually approved.

**Web execution remains explicitly OPEN**, per the corrected research — no code or copy states "Web
cannot execute workouts" as a rule. This prototype simply doesn't build an Active Workout screen for
Web in this first pass (consistent with the corrected nav map treating it as "not currently planned,
revisable, not foreclosed" rather than settled). The Routine Detail page's "Start Routine" button is
present (to judge the interaction point exists) but shows a prototype-only explanatory alert rather than
silently doing nothing or fabricating an execution UI.

### Authenticated dashboard redesign

Driven by direct human UX feedback that the authenticated app felt weaker than the product UI shown
inside the approved `/login` reference's laptop mockup. Scope: shell/navigation/Home only — auth
architecture, Supabase, and no new backend/domain persistence were touched.

- **Nav shape changed from a persistent left sidebar to a compact top header bar** — the sidebar read
  as "generic admin dashboard" next to the reference's top-bar product UI. The disabled "Feed (coming
  soon)" pill was dropped from the visible nav entirely in the process (a control with nothing behind
  it looked worse as a top-bar item than as a sidebar footnote); Feed's actual status
  (deferred, Open Decision #6) is unchanged.
- **Two immediate follow-up refinements**, both direct human UX feedback on the first version of this
  top bar: (1) the top bar's right side previously showed a separate "Profile" nav item, an
  always-visible account name, and a standalone "Sign out" button — this is now a **single account
  icon** (`AccountMenu.tsx`) that opens a small menu with "Profile & settings" (→ `/profile`, which
  already has both) and "Sign out"; Profile is no longer a top-level nav item. (2) the active nav item
  (Home/Plan/History) is now highlighted with a **sliding blue underline** that animates to the newly
  selected item (`TopNav.tsx`, measured via refs — `offsetLeft`/`offsetWidth` — and a CSS `transition`
  on the indicator, no animation library) rather than a static background pill.
- **Home dashboard added** (`HomePage.tsx`) as the new landing route (`/` — previously redirected to
  `/plan`), explicitly a PROTOTYPE HYPOTHESIS layered on the corrected IA, not a replacement of it (see
  "Web information architecture implemented" above). Greeting uses real identity; a "Start Workout"
  button navigates to `/plan` and does **not** execute anything — Web execution remains explicitly
  open, unaffected by this button existing. The 3 stat cards (Workouts/Total Volume/Streak) are
  hand-picked representative numbers, explicitly labeled as such on the page, and are **not** derived
  from `PROTOTYPE_HISTORY` (there are only 3 sample history entries, not 24 workouts) — don't read them
  as internally consistent. "Your Plan" and "Recent Activity" reuse the exact same `PROTOTYPE_ROUTINES`/
  `PROTOTYPE_HISTORY` arrays Plan/History already used — no second, disconnected sample data set.
- **Profile page bug fixed in passing**: it previously rendered hardcoded `PROTOTYPE_PROFILE` sample
  data (`sample_lifter@example.com`) instead of the real signed-in identity, even though real auth
  already existed by the time it was last touched — a leftover from before the auth task. Now shows
  real email/username and a real sign-out button directly on the page (in addition to the topbar).
  `PROTOTYPE_PROFILE` and the now-unused `ProfileInfo` type were removed.
- Visual system extended to Plan/Routine Builder/History/Workout Detail/Profile via shared CSS tokens
  and a redesigned `.stat-card` (label → large value → sublabel, instead of value-then-label) — their
  interactive logic (Routine Builder's reorder/edit/add/remove) was not touched, only restyled.
- Five small reusable components added: `StatCard`, `PlanCard`, `ActivityRow`, `TopNav`, `AccountMenu`
  (all in `web/src/components/`) — no design-system abstraction, no new dependency.
- **`/login` itself was not modified** — its own files/logic are untouched. One known resulting
  inconsistency: `/login`'s decorative `ProductPreview.tsx` mockup still depicts the *old* sidebar nav
  shape, since restyling `/login` was explicitly out of scope for this task. It's presentation-only
  marketing content (`aria-hidden`), so this is a visual staleness, not a functional bug — flagged as a
  UX question below rather than fixed unilaterally.
- No new frontend framework/library was added (still plain CSS, no Tailwind/component library, no
  state-management library) — consistent with ADR-006.
- Responsive: nav bar and dashboard verified only via CSS/media-query review (no browser available in
  this sandbox, same limitation as every other Web task this session) — new breakpoints at ~860px
  (dashboard columns stack) and ~640px (stat cards stack, topbar account name hides, header stacks).
- Verification: `tsc -b` clean, `vite build` succeeds (~470 kB JS / ~134 kB gzip, ~15 kB CSS — up
  slightly from the new page), `oxlint` exit 0 (same 2 pre-existing unrelated warnings), `vitest run`
  6/6 passing (unchanged — these test auth-phase/error-mapping logic, untouched by this task), and a
  `vite preview` + `curl` smoke check confirming the built bundle serves (HTTP 200). No manual visual
  verification was performed.

**Progress** was folded into the top of the History page (a 3-stat row) rather than given its own nav
item — the corrected Web IA's nav map does not list Progress as a destination (the earlier, incorrect
version's Program/analytics emphasis came primarily from now-supplementary TrainingPeaks/TrainHeroic
evidence, not the primary Hevy/Strong/Fitbod comparison).

---

## Phone

### Phone information architecture implemented

The corrected `product-information-architecture.md` §1/§2 recommendation, verified as still current in
the document at implementation time: **3-tab `Home │ Workout │ You`** (not the earlier, since-corrected
4-tab `Home │ Workout │ Progress │ You` — Progress was demoted after adversarial review found it had no
MVP screen behind it).

A new `com.tbdfit.phone.shell` package holds the shell (`AppShell.kt`) and every new prototype screen.
`MainActivity.kt`'s `AuthenticatedScreen` now delegates to `AppShell(...)` instead of directly laying
out `ProfileSummaryHeader` + `WorkoutRootScreen` + the old always-visible technical-proof screen.

### Phone screens implemented

| Screen | Classification | Notes |
|---|---|---|
| Home | MIXED | Real active-workout status (`WorkoutRepository.observeActiveWorkout`) drives "Resume Workout" vs. "Start Workout" (the Start button calls the real `repository.startWorkout`); "Recent activity" list below is PROTOTYPE-ONLY sample data (no real completed-history query has anything to show — see below) |
| Workout → Active Workout / Exercise Picker / set logging | PRODUCTION-BACKED | Entirely real, unmodified (`WorkoutRootScreen`, `ActiveWorkoutScreen`, `ExercisePickerScreen`) |
| Workout → My Routines (always-visible hub) / Routine Detail / Create Routine / Edit Routine | PRODUCTION-BACKED | **Routine UX completion slice**, on top of Slice A's already-real domain. "My Routines" is now a first-class, always-visible section of the Workout tab's root screen (in both active- and no-active-workout states) — not a conditional text button shown only when idle, which is what made Routines undiscoverable before this slice (see human UX feedback in Verification below). Real Create Routine (name → add exercises via the existing `ExercisePickerScreen` → add planned sets) and real Edit Routine (rename, add/remove exercise, add/remove/edit a planned set's target reps/weight) now exist — the actual central "create your own routine" action, previously entirely missing despite the repository fully supporting it. Routine Detail shows each exercise's planned sets individually (not a collapsed summary) plus `Start Workout`/`Edit Routine`. Only the 3 *starting sample routines'* content (Push/Pull/Leg Day names/exercises/set counts) remains representative seed data — see "Routine domain (Slice A)" below; the UI and domain around it are fully real. |
| Workout → Workout Summary | MIXED | Shown after a new, additive, opt-in "Finish Workout" button in the real `ActiveWorkoutScreen` (default-null callback, zero effect on any existing call site). The exercise list shown is read live from the real repository. Pressing "Done" does **not** call `WorkoutRepository.completeWorkout` — the underlying `Workout` row is left exactly as it was (still `ACTIVE`). The screen says so explicitly, in red, so this is never mistaken for durable completion. |
| You → Profile (header, logout) | PRODUCTION-BACKED | Real, unmodified `ProfileSummaryHeader`; logout still goes through the real `AccountTransition.performLogout` |
| You → History (list, detail) | PROTOTYPE-ONLY | See "Complete Workout" note below |
| You → Progress | PROTOTYPE-ONLY | Layout/IA only, static sample stats, no analytics engine |
| You → Followers/Following | Not built (visibly disabled row, "coming soon") | POST-MVP per the corrected IA; included only as a visible future-shell marker, not a functioning screen |
| You → Settings | PROTOTYPE-ONLY (mostly) | Minimal by design; contains one real, functioning entry: |
| You → Settings → Developer / Sync Proof | PRODUCTION-BACKED | The pre-existing real local-persistence/Supabase-sync/Wear-replica proof screen, relocated (not deleted) from MainActivity's old always-visible top-level layout, since the corrected product IA has no primary-navigation place for it |

### Production-backed UI

- `WorkoutRootScreen` / `WorkoutHomeScreen` / `ActiveWorkoutScreen` / `ExercisePickerScreen` (start,
  attach exercise, log sets — completely unmodified in behavior; `ActiveWorkoutScreen` only gained one
  new, default-null, purely additive `onFinishWorkout` parameter)
- `ProfileSummaryHeader` and the real logout flow (`AccountTransition.performLogout`)
- `LocalAccount` ownership, `SessionUnavailable` local-workout access — untouched, not exercised by any
  new screen, but not regressed either (verified via the full existing test suite, see Verification)
- The relocated Developer/Sync Proof screen (`LocalRecordDao`, `LocalRecordSyncCoordinator`,
  `WearReplicaDao` — same real wiring as before, just moved under You → Settings)
- **New (Slice A)**: `RoutineEntity`/`RoutineExerciseEntity`/`RoutinePlannedSetEntity` persistence,
  `WorkoutRepository.startRoutine` (atomic, ownership-checked, respects the existing one-active-
  workout-per-owner rule), the execution target snapshot (`WorkoutSet.targetReps`/`targetWeight`),
  and `Workout.originRoutineId` provenance (`ON DELETE SET NULL`) — all real, tested (see Test
  Coverage below), account-isolated the same way Exercise/Workout already are. Program is NOT
  implemented — see program-routine-first-slice-design.md's Slice B/C.
- **New (Routine UX completion slice)**: the always-visible `MyRoutinesSection` (`WorkoutTabScreen`),
  `NewRoutineScreen`, the upgraded `RoutineDetailScreen` (per-set list, `Edit Routine` action), and
  `RoutineEditorScreen` (rename, add/remove exercise via the real `ExercisePickerScreen`, add/remove/
  edit a planned set's target reps/weight) — all real UI wired to real repository methods
  (`WorkoutRepository.removeExerciseFromRoutine`/`removePlannedSet`/`updatePlannedSetReps`/
  `updatePlannedSetWeight`, thin wrappers over the existing DAO layer, same ordering/ownership
  conventions as everything else). Reuses `SetInputParsing.kt`'s blank-is-null/never-coerce-to-zero
  contract for the planned-set target fields, the same as `ActiveWorkoutScreen`'s actual-value
  fields. The old conditional "Browse Routines" text button (visible only when no workout was
  active) is gone, replaced by this always-visible section.

### Prototype-only UI

- Home's "Recent activity" sample list
- The starting CONTENT of the 3 sample routines (Push/Pull/Leg Day names, exercise selection, planned
  set counts/reps) — the Routine/RoutineExercise/RoutinePlannedSet *rows themselves* are real and fully
  user-editable/deletable after the one-time seed; only their initial values are representative sample
  data, not something a real user authored. See "Routine domain (Slice A)" below.
- History list/detail, Progress stats
- Profile beyond the real header (nothing was added here — the real header was reused as-is)
- Settings body (aside from the real Developer/Sync Proof link)
- Web: Home's dashboard chrome (stat cards, "Recent Activity"), History, Workout Detail, and
  Profile's activity/settings body — these still have zero backend. **Web's Plan/Routine
  screens are no longer in this list** — see "Routine Supabase + Web vertical slice" below; they
  moved to production-backed the same way Android's Routine domain did (Slice A), independently.

### Mixed UI

- **Routine → Start**: real `RoutineEntity`/`RoutineExercise`/`RoutinePlannedSet` rows, real atomic
  `startRoutine` transaction, real resulting `Workout`/`WorkoutExercise`/`WorkoutSet` rows with a real
  execution target snapshot — see "Routine domain (Slice A)" below. Only the routines' *starting*
  content is sample data.
- **Workout Summary**: prototype completion interaction (no real completion persisted), but the
  exercise list it displays is read live from the real repository
- **Home dashboard**: real active-workout state drives the primary card; the activity list below it is
  sample data

### Routine domain (Slice A)

As of `docs/architecture/program-routine-first-slice-design.md` Slice A (implemented provisionally —
that document's own status remains `DESIGN PROPOSAL — FOR TEAM REVIEW`; implementation proceeding
under this project's `IMPLEMENTED ≠ TEAM ACCEPTED` governance rule, not a claim of acceptance):
Routine is now a real, standalone, account-owned Room domain — `com.tbdfit.phone.workout.Routine{,Exercise,PlannedSet}Entity`/`Dao`, wired into `WorkoutRepository`. `Routine → Start → Workout` works
with zero Program involvement (Program does not exist yet — Slice B/C). Starting a Routine:

- validates the Routine belongs to the calling owner (`require`, not a new result-type — the same
  "should be structurally impossible via the supported UI path" assertion style `WorkoutDao.
  startWorkoutIfNoneActive` already uses for its own ownerId contract);
- respects the existing one-active-workout-per-owner guarantee unchanged — if a workout is already
  active, planned content is not attached to it, and the caller is routed to the existing workout;
- copies each planned set's target reps/weight into new `WorkoutSet.targetReps`/`targetWeight`
  columns **once, at that moment** — this is the actual mechanism proving editing the Routine
  afterward can never change an already-started Workout's target, not merely a UI convention;
- records `Workout.originRoutineId` (`ON DELETE SET NULL` — deleting the Routine later never blocks
  or destroys the Workout, only the "started from" breadcrumb disappears).

`RoutineLibraryScreens.kt` (renamed from the pre-Slice-A `RoutinePrototype.kt`) seeds 3 sample
routines (Push/Pull/Leg Day, built from the same real built-in exercise catalog IDs as before) into
real, owner-scoped rows exactly once per account (`SampleRoutineSeedMarker`, a small SharedPreferences
marker — deliberately not "seed if the owner currently has zero routines," which would silently
resurrect routines a user deliberately deleted). After that one-time seed, the routines are ordinary,
fully user-editable/deletable data like any other Routine — the seeding mechanism has no further
special status.

`ActiveWorkoutScreen` shows the frozen target ("Target: 80 kg × 8") next to each set's actual-value
inputs, read directly from the `WorkoutSet` row already in hand — no new query, no re-read of the
source Routine.

The Web Routine screens are **no longer** prototype-only — see the next section. Web still has no
execution capability of any kind ("Start Routine" remains an explicit alert, not a fake persisted
Workout) — that is unrelated to, and unaffected by, Routine planning data becoming real.

### Routine Supabase + Web vertical slice

The strategic pivot after Slice A + the Routine UX completion slice: further Android/Room Program
work was paused in favor of building the real product domain centrally in Supabase/Postgres first,
proving it out via a full Web CRUD experience, and reconciling Android/Wear against the stabilized
model later. This is the first step of that — real Routines in Postgres, real Web UI, nothing else
(no Program, no Web execution, no Android/Wear changes).

**Schema** (`supabase/migrations/20260910120000_create_routines.sql`): `exercises` (built-in rows have
`owner_id = null`, visible to everyone; a custom exercise has a real `owner_id`, visible only to its
owner — seeded at the time with the same 6 built-in names as Android's catalog, but fresh
Postgres-native UUIDs, a separate identity space from Android's own stable string IDs). **Superseded
one migration later**: `20260910180000_normalize_exercise_identity.sql` re-keyed `exercises.id` from
`uuid` to `text` and gave the six built-ins Android's own canonical `builtin_*` string IDs (see
"Exercise metadata" below), specifically so a future cross-client sync would not need a permanent
built-in-id mapping table — built-in exercise identity is no longer a separate Android/Supabase space
as of that migration. `routines` → `routine_exercises` → `routine_planned_sets` (real foreign keys,
`unique(parent_id, position)` ordering — a deliberate, explained departure from Android's
sparse-position choice, safe here because every write replaces a routine's entire content in one
transaction and therefore always writes fresh contiguous positions).

**Atomicity**: a single `save_routine(routine_id, name, exercises jsonb)` Postgres function (SECURITY
INVOKER — it does not bypass RLS) handles both create and edit as one atomic full-content replace,
since PostgREST/supabase-js has no general multi-table transaction primitive callable from the
browser. The Web editor holds a routine's full draft (name + exercises + planned sets) in memory and
calls this one RPC on Save.

**RLS, verified against a real local Postgres 16 instance during this task (not just read for
plausibility)**: every cross-account attack the design review calls for was exercised directly —
User B cannot SELECT/UPDATE/DELETE User A's routine (confirmed 0 rows / 0 rows affected, not merely
assumed), cannot INSERT a `routine_exercises` row into User A's routine (confirmed RLS denial error),
and — the subtle one — cannot attach User A's *private custom exercise* to their own routine (confirmed
RLS denial), which a foreign key alone would not have prevented (FK checks bypass RLS on the
referenced table; the `routine_exercises` insert/update policies explicitly re-check exercise
visibility in their `with check` clause for exactly this reason). The `anon` role gets a hard
permission-denied on every table (table grants revoked, not just missing RLS policies).

**Explicitly NOT done in this slice**: Program (any table), Web workout execution, an Android
LocalAccount equivalent on Web (the real Supabase `auth.uid()` is the sole identity authority for
every write), and any live migration application to the real remote Supabase project — this
sandbox's network policy blocks outbound access to the configured project's domain (confirmed via a
direct 403 during this task, consistent with every earlier Web task this session). The migration was
validated end-to-end against a disposable local Postgres 16 container instead; see the task's own
report for the exact command the developer needs to run against the real project.

**Android and Web Routines do NOT sync or share data.** They are two independent, unconnected
persistence stores as of this slice — a routine created on Android does not appear on Web and vice
versa. This is not cross-client sync; do not read it as such.

### Program Supabase + Web vertical slice

The next real product domain after Routine, following the exact same architecture/security pattern
(same migration style, same RLS discipline, same local-Postgres validation method). Program remains
strictly OPTIONAL and ADDITIVE — `Routine → Start → Workout` is completely unaffected, and the Web
nav reflects this: `Programs` is its own sibling top-level nav item next to `Routine`/`Plan`, never
nested under it (Option C from `docs/product/web-routine-program-planning-research.md`'s Routine IA
section — zero change to the shipped Routine nav/routes).

**Schema** (`supabase/migrations/20260911120000_create_programs.sql`): `programs` → `program_weeks`
(logical week — a plain ordered `position`, no calendar/date field of any kind) → `program_sessions`
(optional `name`, optional `source_routine_id` provenance) → `program_session_exercises` →
`program_session_planned_sets`. Deliberately **no foreign key anywhere from these tables back to
`routine_exercises`/`routine_planned_sets`** — the schema is structurally incapable of a live
reference to a Routine, not merely disciplined against one; a copy is the only path that exists.

**Ordering divergence from Routine, explained in the migration itself**: Routine's tables assume
always-contiguous positions because every save is a full-content replace. Program's access pattern
is incremental (add one week, delete one session, duplicate one week — there is no "resave the
whole 12-week program" operation), so its tables keep `unique(parent_id, position)` for integrity
but do **not** require contiguity — the same sparse, never-compacted position discipline Android's
`WorkoutExercise`/`WorkoutSet` already use, for the same reason (no renumbering transaction on
delete). The Web UI derives any displayed "Week N" purely from array index after ordering by
position, never from the raw stored value.

**Atomicity — five narrowly-scoped Postgres functions**, not one big full-tree-replace RPC (a 12-
week × 3-session program is a much bigger tree than one Routine, and "copy this routine into this
session" / "duplicate this week" are the actual UI-triggered actions): `add_program_week`,
`add_program_session`, `copy_routine_to_program_session` (the MUST-HAVE copy operation —
[research](../product/web-routine-program-planning-research.md) found this is the single most
consistently-implemented feature across every competitor with both a routine-like object and a
program builder), `duplicate_program_week` (the research's other identified high-value action),
and `save_program_session` (full-replace of one session's content only, mirroring `save_routine`'s
own strategy at one level of scope narrower). Single-row operations (create/rename/delete Program,
delete Week/Session) are plain RLS-protected `supabase-js` calls — a single-row `DELETE` with `ON
DELETE CASCADE` is already atomic at the database level, no RPC needed.

**Snapshot invariant, verified for real** (not just reasoned about) against a local Postgres 16
container: created a Routine, copied it into a `ProgramSession`, then edited the *original* Routine
(renamed it, changed its planned sets) — the `ProgramSession`'s content was confirmed byte-for-byte
unchanged. Then deleted the source Routine entirely — the `ProgramSession` survived with
`source_routine_id` now `NULL` (an `ON DELETE SET NULL` relationship, the same reasoning the
hardened Android design doc applied to `Workout.originRoutineId` — pure traceability, never load-
bearing for the row's own content). Then duplicated a week and edited the duplicate — the original
week's session was confirmed unchanged.

**RLS adversarial testing, exercised for real** against the same local Postgres instance: as a
second user, every attempt to SELECT/UPDATE/DELETE another account's `programs` row returned 0
rows/0 rows affected; direct `INSERT`s of a week into another account's program, a session into
another account's week, and — the subtle one, closing the identical class of gap the Routine slice
found — attaching another account's *private custom exercise* to one's own session (tried both via
a raw `INSERT` and via the `save_program_session` RPC) all failed with a real RLS policy violation,
confirmed via `SELECT COUNT(*)` returning 0 afterward. Every RPC (`copy_routine_to_program_session`,
`duplicate_program_week`, `add_program_session`, `save_program_session`) was also invoked directly
against another account's data and raised its own explicit "not found or not owned" exception before
writing anything. The `anon` role received a hard permission-denied.

**Web UI**: `ProgramsListPage.tsx` (My Programs), `ProgramBuilderPage.tsx` (the three-pane builder).
The exercise/set editing UI was extracted from `RoutineEditorPage.tsx` into a shared
`PlannedExercisesEditor.tsx` component, used identically by both the standalone Routine editor and
the Program session editor — the same "planned prescription" concept, backed by entirely separate
Postgres tables in each case (the reused TypeScript draft shape is not the same claim as reused
database rows).

**Explicitly NOT done in this slice**: Program execution of any kind, Program progress / "Week 4 of
12" (derivable later from completed-Workout provenance once a central Workout model exists on Web —
not stored anywhere now), `ProgramInstance`/Enrollment/"Current Program" (no such table or concept
exists), calendar scheduling, creator/marketplace features, and — same as the Routine slice — live
migration application to the real remote Supabase project (blocked by this sandbox's network
policy; validated instead against a disposable local Postgres 16 container). Run
`npx supabase db push --dry-run` then `npx supabase db push` against the real project to apply it.

### Exercise metadata (Exercise Type + Equipment)

A narrow, Web-first follow-up to the Routine/Program slices — see
`supabase/migrations/20260911180000_add_exercise_metadata.sql`. Adds two plain, CHECK-constrained
`text` columns to the already-canonical `exercises` table (not a Postgres enum, so a future value —
e.g. `DURATION` — is a trivial constraint-alteration migration, not an identity migration):

| Field | Classification | Notes |
|---|---|---|
| Exercise name | PRODUCTION-BACKED | Unchanged |
| Exercise Type | PRODUCTION-BACKED, limited to `WEIGHT_REPS`/`BODYWEIGHT_REPS` | Deliberately excludes `DURATION`/`DISTANCE`/`CARDIO` — verified directly against `WorkoutSetEntity.kt`/`WorkoutSetDao.kt` that Workout execution (Android; nothing exists on Web) is strength-first only, with no duration/distance field anywhere — exposing a type with nowhere to log it would be worse than not offering it |
| Equipment | PRODUCTION-BACKED | `BARBELL`/`DUMBBELL`/`MACHINE`/`CABLE`/`BODYWEIGHT`/`KETTLEBELL`/`BAND`/`OTHER` |
| Primary Muscle Group | NOT IMPLEMENTED | Explicitly deferred (Equipment/Exercise Type have real consumers today; muscle group currently has none) |
| Secondary/Other Muscles | NOT IMPLEMENTED | Deferred alongside Primary Muscle Group |
| Exercise image | NOT IMPLEMENTED | Would require Supabase Storage (bucket, RLS, upload UI) — a materially larger scope than a metadata column, deliberately out of this slice |
| Duration/distance Workout execution | NOT IMPLEMENTED | No client has any such execution model |

The 6 existing built-in exercises (matched by their canonical `builtin_*` id, never by name-guessing)
were assigned real metadata: `builtin_bench_press`/`builtin_back_squat`/`builtin_deadlift`/
`builtin_overhead_press`/`builtin_barbell_row` → `WEIGHT_REPS` + `BARBELL`; `builtin_pull_up` →
`BODYWEIGHT_REPS` + `BODYWEIGHT` (the one documented judgment call — see the migration's own comment
for why a weighted-pull-up variant is left as a user's own custom exercise rather than modeled as an
optionally-weighted built-in property). Existing custom exercises (created before this migration)
get `NULL` for both fields — never a guessed default — surfaced as "Unspecified" in the UI via
`web/src/lib/exerciseLabels.ts`'s centralized label mapping, not a raw database code.

Both custom-exercise-creation entry points (`ExerciseLibraryPanel.tsx`'s own form and
`PlannedExercisesEditor.tsx`'s inline toggled picker, used by Program's session editor) now collect
Exercise Type and Equipment alongside Name, so a new custom exercise always has real values even
though the column itself remains nullable. `ExerciseLibraryPanel.tsx` also gained Equipment/Exercise
Type filter dropdowns, combined with the existing search via AND — no muscle filters, no image
cards, per the explicit scope boundary above. Custom Exercise editing (renaming/changing metadata
after creation) does not exist anywhere in the product yet — this slice did not add it, since no
prior Exercise edit UI existed to extend.

`routine_exercises`/`program_session_exercises` continue to reference only `exercise_id` — no
metadata duplicated onto either table; `save_routine`/`save_program_session`/
`copy_routine_to_program_session`/`duplicate_program_week` were confirmed unaffected (none of them
reference `exercise_type`/`equipment` anywhere). RLS was re-verified with a real adversarial run
against a disposable local Postgres 16 container (not merely assumed unchanged): User B cannot
SELECT, UPDATE, or attach User A's private custom exercise (including its metadata) to their own
Routine or Program session, via either a direct insert or the `save_routine`/`save_program_session`
RPCs — the `anon` role remains fully denied. Live migration application to the real remote Supabase
project was not attempted (blocked by this sandbox's network policy, consistent with every prior
slice) — run `npx supabase db push --dry-run` then `npx supabase db push` to apply
`20260911180000_add_exercise_metadata.sql` (the fourth of four migrations, after `create_routines`,
`normalize_exercise_identity`, and `create_programs`).

### Web server-state cache

All real Web reads (Routine list/detail, Program list/detail, the Exercise catalog, the signed-in
user's `profiles` row) are now backed by TanStack Query rather than page-local
`useEffect`/`useState` fetch loops — see
[`docs/architecture/web-server-state-cache.md`](../architecture/web-server-state-cache.md) for the
full architecture. This does not change what is production-backed vs. prototype-only (the
classification above is unaffected) — it changes how already-real data is cached client-side.
Concretely: revisiting `/plan`, `/programs`, or a specific Routine/Program after navigating away no
longer re-shows a blocking "Loading…" while the data is still fresh; a real Postgres query only
happens again once the cached entry goes stale or an actual mutation invalidates it. Auth
transitions (sign-out, a different account signing in) clear the entire client cache as a safety
net on top of every private query already being scoped by account id — see that doc's Account
Scoping section for why both layers exist.

---

## Cross-client design language

Web and Phone share conceptual vocabulary (Workout / Routine / Exercise / History / Progress / Profile)
and the same sample data concepts (Push/Pull/Leg Day routines, the same 6 built-in exercise names) so a
reviewer comparing both side by side recognizes the same product — per ADR-005's "same product model,
specialized device responsibilities, no forced feature parity." Navigation, density, and interaction
patterns deliberately differ: Phone uses bottom-tab navigation sized for one-handed thumb reach; Web
uses a compact top navigation bar (previously a persistent sidebar — see "Authenticated dashboard
redesign" above) and dense tables (routine/history rows are `<table>` rows on Web, `Card` rows on
Phone) exploiting the larger screen, per the corrected web research's own finding that no competitor
with a real web surface stretches a mobile card layout onto desktop.

Each client also got one small, consistent internal visual language (not a shared design system across
clients, and not a large abstraction within either client):

- **Phone**: standard Material 3 components throughout (`Card`, `Button`, `Checkbox`,
  `OutlinedTextField`, `NavigationBar`) — no new theming was introduced beyond the existing
  `TbdfitTheme`.
- **Web**: a small hand-written dark theme (`web/src/index.css`) — consistent typography scale, a
  fixed top navigation bar, one table style, one card style, one button hierarchy
  (primary/secondary/link/icon), and explicit "prototype" subtitles/badges on every screen that isn't
  real.

---

## Domain / backend requirements discovered

Nothing below was implemented — discovery only, consistent with every design artifact this prototype
was built from:

- **Routine / RoutineExercise / RoutinePlannedSet** — **now real on Phone** (Slice A of
  program-routine-first-slice-design.md, an owner FK to `LocalAccount` exactly as anticipated here).
  Still not implemented on Web — Web's version has no local durability concern (per
  `web-information-architecture.md` §4) and could be backend-session-authoritative directly, but
  that remains future work, not part of this slice (Phone-only, local-first domain established
  first, per the design doc's explicit scope).
- **Program / ProgramWeek / ProgramSession** — designed on Android (program-routine-first-slice-design.md)
  but deliberately NOT implemented on Phone (Slice B/C remain unbuilt there). **Now real on Web**
  (`programs`/`program_weeks`/`program_sessions`/`program_session_exercises`/
  `program_session_planned_sets` in Supabase/Postgres, RLS-verified) — see "Program Supabase + Web
  vertical slice" above. Routine remains fully independent of Program on both clients. Android's
  Room-based Program design and Web's Postgres implementation are two independent efforts against
  the same domain research, not yet reconciled with each other.
- **Program execution / progress** — not implemented anywhere. "Week 4 of 12" / "N of M sessions
  completed" remains designed to be derived later from completed-Workout provenance, once a central
  Workout/execution model exists on Web (it does not yet).
- **Workout Summary / Complete Workout** — the DAO/repository layer already exists and is unit-tested
  (`WorkoutRepository.completeWorkout`, `WorkoutDao.completeIfActive`) but **no UI anywhere calls it**
  (grep-verified before and after this prototype). This prototype's "Finish Workout" → Summary → "Done"
  flow deliberately does not close that gap — whether to wire real completion is left to human
  evaluation of this exact interaction, not decided here.
- **Completed-workout history/replication** — History (both clients) and Home's "recent activity" all
  need a real query over `COMPLETED` workouts, which will stay empty in practice until Complete Workout
  ships; Web's History additionally needs actual replication of Phone/Watch-originated data to a
  backend, which does not exist.
- **Profile beyond username, Settings beyond sign-out, Followers, Feed** — no backend for any of these.
- **Web account/session integration** — Web currently has no auth of any kind; `LocalAccount` likely has
  no web equivalent at all (already flagged as open in `web-information-architecture.md` §4).

---

## Verification

### Android

`./gradlew clean :phone:assembleDebug :wear:assembleDebug :phone:testDebugUnitTest :wear:testDebugUnitTest`
→ **BUILD SUCCESSFUL**. **Phone: 136 tests, 0 failures. Wear: 7 tests, 0 failures.** — identical to the
pre-prototype baseline; no regression. `git status --short android/wear/` confirms zero Wear source
files were touched.

### Web

- `tsc -b` (typecheck): clean, no errors.
- `vite build`: succeeds — `dist/index.html`, one JS bundle (~490.6 kB, ~138 kB gzip, grown across
  the auth/login/dashboard/Routine/Program work), one CSS bundle (~18.6 kB).
- `oxlint` (lint): exit code 0, 2 pre-existing unrelated warnings (`useOwnProfile.ts`,
  `AuthContext.tsx`), no new findings.
- `vitest run`: **17/17 passing** (11 baseline + 6 new in `programs.test.ts`, mirroring
  `routines.test.ts`'s pure row-mapping/payload-shaping style — no live Supabase connection, no
  component-rendering harness).
- **Smoke check only, not human/browser verification**: `vite preview` was started and `curl` confirmed
  the built `index.html` and JS bundle are served correctly (HTTP 200). This proves the app *builds and
  serves*; it does **not** prove the UI renders correctly or that navigation/interactions work as
  intended — no real browser or visual verification was available in this environment.

### Manual runtime verification

**Not performed.** No Android emulator/device and no browser are available in this sandboxed
environment. Nothing in this document should be read as claiming the UI was visually inspected or
clicked through by anyone other than via the build/serve/test verification described above.

---

## UX questions for human review

1. Does `Home │ Workout │ You` (3 tabs) feel right, or does Home feel redundant with Workout even with
   its distinct dashboard content?
2. ~~Is the "Browse Routines" entry point discoverable?~~ **Resolved by direct human feedback and the
   Routine UX completion slice**: it was not discoverable (a conditional text button, hidden whenever
   a workout was active). My Routines is now an always-visible section of the Workout tab, with real
   Create/Edit Routine actions. Open follow-up: is the resulting Workout-tab layout (active/resuming
   workout + My Routines + Create Routine, all on one scrollable screen) still coherent once Program
   is added in a later slice, or does it need its own sub-navigation by then?
3. Does the Workout Summary's "this doesn't actually complete the workout" caption read as honest
   transparency, or as a confusing half-feature? Should Complete Workout be wired for real next, given
   how it looked here?
4. Does grouping History/Progress/Followers/Settings all under `You` feel coherent, or like a
   catch-all, once there's real content behind more of them?
5. Web now has a Home dashboard (added after this question was first written) — does it earn its place
   next to Plan, or does it feel redundant with Plan the way Phone's Home/Workout split was originally
   questioned? Should Plan revert to being the landing page with Home removed instead?
6. Is the Web Routine Builder's reorder/planned-sets-reps interaction sufficient to judge the concept,
   or does it need drag-and-drop / more visual polish to be evaluated fairly?
7. Should Web's "Start Routine" alert be replaced with *something* (even a disabled state or a
   "send to phone" placeholder) rather than an explanatory dialog?
8. Does the new compact top-nav bar actually read as "a fitness product" rather than "admin
   dashboard," or does it need further work (e.g. a search control, once there's enough content to
   search)?
9. `/login`'s decorative product-preview mockup still shows the old sidebar nav shape, now
   inconsistent with the real top-bar shell — worth a follow-up visual fix, or an acceptable gap in
   presentation-only marketing content?
10. Does the three-pane Program Builder (Weeks / Sessions / Session editor) stay usable at a real
    12-week scale, or does the weeks list need a more compact treatment past ~6-8 weeks?
11. Android's Room-based Program design (program-routine-first-slice-design.md, Slice B/C) and Web's
    now-real Postgres implementation are two independent efforts against the same domain research —
    should Android Program work resume against the stabilized Web/Postgres model, or wait for a real
    sync mechanism first?

---

## Recommended next step

Have a human click through both prototypes and answer the questions above before committing to any of:
Routine backend, Complete Workout production implementation, History backend, social backend, Watch
execution, or cloud sync — per this task's explicit stop condition.

`UX STATUS: PROTOTYPE — REQUIRES HUMAN EVALUATION`
