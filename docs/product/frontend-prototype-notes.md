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
(Routine, History, Progress, Profile-beyond-username, social) use representative hardcoded content and
local/transient UI state (Compose `remember`/`mutableStateOf` on Phone; React `useState` on Web).
Real, already-implemented capabilities (LocalAccount ownership, Workout/Exercise/WorkoutExercise/
WorkoutSet, start/restore, attach exercise, set logging, `SessionUnavailable` local-workout access) are
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
| Home (dashboard) | MIXED | Real greeting identity (email, or username if a real `profiles` row exists); Start Workout button, 3 stat cards, "Your Plan" and "Recent Activity" are all representative prototype content reusing the SAME sample data as Plan/History (not a second, disconnected data set) — see "Authenticated dashboard redesign" below |
| Plan (Routine Library) | PROTOTYPE-ONLY | Table of 3 sample routines (Push/Pull/Leg Day) |
| Routine Detail / Builder | PROTOTYPE-ONLY | Interactive: reorder (↑/↓), edit planned sets/reps inline, add exercise (from a representative library list), remove exercise. All edits are local component state — reload resets to sample data. "Start Routine" shows an explicit alert explaining execution-on-Web is an open question, not implemented, and not foreclosed — see below. |
| History (list) | PROTOTYPE-ONLY | Table + a folded-in Progress stat row (not a separate nav destination — see Progress note below) |
| Workout Detail | PROTOTYPE-ONLY | Read-only sample workout detail |
| Profile | MIXED | Real signed-in identity (email, and username if a real `profiles` row exists — same table Android reads) + real sign-out, both now shown directly on the page (previously only in the shell); the Profile *page* content itself (activity, etc.) remains sample data; a minimal Settings section folded in (per Journey W3), Feed marked "Coming soon" |

### Auth / session (Web)

Added after the initial Web/Phone prototype pass, closing a gap the initial pass left open: the Web
prototype previously opened directly inside the authenticated shell with no login. This is now real,
not prototype data — see `web/README.md` for setup and `web/src/auth/` for the implementation
(`AuthContext.tsx`, `AuthForm.tsx`, `authErrors.ts`, `useOwnProfile.ts`).

- **One identity across TBDFit**: the Web client points at the exact same Supabase project/anon key
  as Android (copied from `android/local.properties` into `web/.env`, gitignored) — no separate Web
  user system, no second user identifier. Signing in with an Android account works here too.
- States implemented: `RESTORING_SESSION`, `SIGNED_OUT`, `AWAITING_CONFIRMATION`, `SIGNED_IN`,
  `AUTH_ERROR` (reserved for an unexpected session-restore failure, not ordinary wrong-password —
  that's an inline form error instead, mirroring Android's `Result<Unit>`-based `AuthGateway`).
  Session persists across a normal page reload (Supabase's default `localStorage` persistence).
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
| Workout → Routine Library / Detail | MIXED | Routine content (3 sample routines) is PROTOTYPE-ONLY, but composed entirely from the **real built-in exercise catalog** IDs. "Start Routine" calls the real `WorkoutRepository.startWorkout` + `addExercise` for each planned exercise, producing a genuinely durable, ownership-scoped `Workout` — not a fabricated one. See "Routine prototype" below. |
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

### Prototype-only UI

- Home's "Recent activity" sample list
- Routine content (names/exercises/planned sets-reps as *displayed* — though see Mixed below for what
  "Start" actually does)
- History list/detail, Progress stats
- Profile beyond the real header (nothing was added here — the real header was reused as-is)
- Settings body (aside from the real Developer/Sync Proof link)
- Every Web screen (Plan, Routine Detail/Builder, History, Workout Detail, Profile) — Web has zero
  backend of any kind at this stage

### Mixed UI

- **Routine → Start**: prototype routine content, real resulting `Workout`/`WorkoutExercise` rows (see
  "Routine prototype" below)
- **Workout Summary**: prototype completion interaction (no real completion persisted), but the
  exercise list it displays is read live from the real repository
- **Home dashboard**: real active-workout state drives the primary card; the activity list below it is
  sample data

### Routine prototype

`android/phone/src/main/kotlin/com/tbdfit/phone/shell/RoutinePrototype.kt` defines 3 sample routines
built **entirely from the existing real built-in exercise catalog** (`builtin_bench_press`,
`builtin_overhead_press`, `builtin_deadlift`, `builtin_barbell_row`, `builtin_pull_up`,
`builtin_back_squat` — see `BuiltInExerciseCatalog.kt`). Because every planned exercise references a
real, stable `Exercise.id`, pressing "Start Routine" can call the real, already-tested
`WorkoutRepository.startWorkout(ownerId)` followed by `addExercise(workoutId, exerciseId)` for each
planned exercise — producing a genuinely durable, per-owner-scoped `Workout` with real attached
exercises, without inventing any `Routine`/`RoutineExercise` domain model. If the routine's exercise
list ever needed to reference something outside the built-in catalog (a custom exercise, or a real
future `Routine` entity), this exact integration would need real `Exercise` rows to point to — that gap
is left visible here, not papered over. If an owner already has an active workout, "Start Routine"
intentionally does **not** attach exercises to it (avoids silently mutating a workout the routine didn't
start); the user is simply routed back to their existing active workout.

The Web Routine Builder, by contrast, is fully prototype-only (no equivalent "Start" integration) —
Web has no `WorkoutRepository` to call into; see "Backend/domain requirements discovered" below.

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

- **Routine / RoutineExercise** — needed on both clients; Phone's version would need an owner FK
  identical in spirit to `Workout.ownerId → LocalAccount`; Web's version has no local durability
  concern (per `web-information-architecture.md` §4) and could be backend-session-authoritative
  directly.
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
- `vite build`: succeeds — `dist/index.html`, one JS bundle (~470 kB, ~134 kB gzip, grown from
  `@supabase/supabase-js` + the auth/login/dashboard work across three Web tasks), one CSS bundle
  (~15 kB).
- `oxlint` (lint): exit code 0, 2 pre-existing unrelated warnings (`useOwnProfile.ts`,
  `AuthContext.tsx`), no new findings.
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
2. Is the "Browse Routines" entry point (appearing below the Start Workout prompt, only when no workout
   is active) discoverable, or should Routines get more visual weight given the adversarial review's
   finding that Routine Library may matter more than Profile at this stage?
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

---

## Recommended next step

Have a human click through both prototypes and answer the questions above before committing to any of:
Routine backend, Complete Workout production implementation, History backend, social backend, Watch
execution, or cloud sync — per this task's explicit stop condition.

`UX STATUS: PROTOTYPE — REQUIRES HUMAN EVALUATION`
