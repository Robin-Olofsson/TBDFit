# TBDFit

TBDFit is a training platform, not a single app: one product, shared across native/device-specialized
clients (Android phone, Wear OS, Web today; iPhone/Apple Watch planned), covering the training
lifecycle — plan, execute, record, review, improve, and eventually share/discover. Clients share a
product model and a single cross-client identity (one Supabase-authenticated account), not a shared
UI or shared framework code (see [ADR-001](docs/architecture/adr/0001-native-client-architecture.md)
and [ADR-005](docs/architecture/adr/0005-multi-client-responsibility-strategy.md)).

Workout execution is a core goal, not an afterthought: a device should be able to start, run, and
durably record a workout without depending on network or backend availability at that moment (see
[PD-002](docs/product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch)).

## Status

TBDFit is in active architecture/product inception. Most architecture and product decisions below
are **proposed candidates** — reasoned through by one developer, not yet reviewed by the second, and
become Accepted only after that review. Treat every `PROPOSED` item as provisional even where
implementation has already proceeded on top of it — **implementing something is not the same as
verifying it, and verifying it is not the same as the team accepting it.**

| Client | Implementation | Verification |
| --- | --- | --- |
| **Android Phone** | Real: Supabase auth (email/password + Google), account-scoped local workout persistence (start/attach exercise/log sets/edit/delete), a real standalone Routine domain (Room, `Routine → Start → Workout`), plus a prototype navigation shell layered on top for the rest (see below) | Robolectric unit/Room tests only — no physical device/emulator run in this environment |
| **Wear OS** | Foundation scaffold + a real (tested) phone↔watch record-replication mechanism; **no workout-domain code yet** | Unit-tested; no paired device/emulator run performed |
| **Web** | Real Supabase auth (`/login`, session restore, logout); real Routine and Program planning (RLS-isolated); a real, optional Profile (`/profile`) — identity/bio, Follow-graph counts, Statistics (Movement steps chart, Workout streak), and a Calendar (schedule/reschedule/unschedule a Routine or ProgramSession, exact time + IANA timezone, completed-Workout correlation via `origin_scheduled_session_id`) — layered on a prototype shell for Home/History/settings content (see below); Web reads completed Workout history but never executes a Workout | Build/lint/type-check/unit tests pass; no real-browser or live-Supabase manual verification performed in this environment |
| **Apple (iPhone/Watch)** | Not yet scaffolded | N/A |
| **Supabase** | Backend platform (Accepted, [ADR-004](docs/architecture/adr/0004-initial-backend-platform-and-migration-strategy.md)); auth, profiles (username + display name + bio), follows (social graph), Routine, Program, Exercise metadata, completed Workout History (`workouts`/`workout_exercises`/`workout_sets`, idempotent `record_completed_workout` RPC), daily Movement (`daily_activity`, steps/day), Scheduling (`scheduled_sessions`), and RLS-hardening schema areas migrated (see `supabase/migrations/`) | Used by both Android and Web against the same project |

## Product direction

- **Same product, specialized clients.** No feature is assumed to exist on every client just
  because it exists on one; nothing here requires shared UI or core technology across ecosystems.
- **No permanent execution authority on one device.** A workout's durable record lives wherever it
  was executed; sync (not yet built) is a replication concern, never a precondition for local use.
- **Ownership ≠ visibility.** Who locally owns a piece of data and who is allowed to see it are
  different questions, kept separate on purpose — see
  [the multi-client vision](docs/architecture/multi-client-product-vision.md) and the
  [Web IA](docs/product/web-information-architecture.md) for where this currently matters.
- **Prototype before backend.** Where a product surface's shape is still uncertain, this repository
  favors a real, clickable UI backed by representative/hardcoded content over building a backend
  for it first — see [Prototype boundaries](#prototype-boundaries) below.

These are current working principles, not all individually Accepted decisions — see the ADR/PD
statuses in [Documentation](#documentation).

## Repository structure

```text
/
  android/
    phone/     Android phone app — real local-first workout execution + Supabase auth
    wear/      Wear OS app — foundation scaffold + phone↔watch replication; no workout domain yet
  apple/       Native iOS + watchOS application (not yet scaffolded; see ADR-003)
  web/         Vite + React + TypeScript Web client — real auth, prototype product UI (see ADR-006)
  supabase/
    migrations/   Authoritative, platform-neutral schema/RLS (ADR-004) — owned by no single client
  docs/
    architecture/
      adr/                              Architecture Decision Records (ADR-001..006)
      multi-client-product-vision.md    Cross-client direction synthesis (design direction, not Accepted)
      strength-workout-first-slice-design.md
      physiological-capabilities-research.md
    product/
      decisions.md                          Proposed product decisions (PD-001..003)
      competitor-benchmark-hevy-strong-fitbod.md
      frontend-product-ux-research.md / product-information-architecture.md   (Phone)
      web-product-ux-research.md / web-information-architecture.md           (Web)
      frontend-prototype-notes.md            Current prototype-vs-production classification, per screen
    development/
      supabase-setup-and-verification.md     Supabase setup/config/verification runbook
```

## Getting started

### Web

```sh
cd web
npm install
cp .env.example .env   # fill in the same Supabase project values Android uses — see web/README.md
npm run dev             # http://localhost:5173
npm run build           # tsc -b && vite build
npm run lint             # oxlint
npm run test             # vitest run
```

See [`web/README.md`](web/README.md) for environment variables, the Supabase Dashboard redirect-URL
step required for email confirmation, and the current prototype-content scope. To let a trusted
tester use a locally-running session remotely, see
[Remote Live UX Testing](docs/development/remote-web-testing.md) (Cloudflare Quick Tunnel, no
router changes).

### Android Phone / Wear OS

Requires a JDK and the Android SDK. Both modules build from `android/`:

```sh
cd android
./gradlew clean :phone:assembleDebug :wear:assembleDebug :phone:testDebugUnitTest :wear:testDebugUnitTest
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

Before building, create `android/local.properties` (gitignored) with your Android SDK path and your
Supabase project's client-safe values:

```properties
sdk.dir=<local Android SDK path>
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=sb_publishable_<...>
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
```

See [`docs/development/supabase-setup-and-verification.md`](docs/development/supabase-setup-and-verification.md)
for the full setup (including Google auth's separate Android/Web OAuth client IDs and the required
Dashboard redirect URL). Unit tests here run under Robolectric (real SQLite on the JVM) — this is
**not** a substitute for physical-device or emulator verification, which this development
environment does not currently have available.

### Apple

Not yet scaffolded (see [ADR-003](docs/architecture/adr/0003-apple-development-and-verification-strategy.md)).
Development happens primarily on Windows; Apple-specific verification (Xcode, simulator, signing,
device) is expected to happen in a supported macOS environment when that work begins, not on every
change.

### Supabase

`supabase/migrations/` is the single source of truth for the shared schema — currently: disposable
technical-proof records, a `profiles` table, and the real Routine/Program domain (`routines`,
`programs`, and their child tables, each with RLS). Apply migrations either via the Supabase CLI
(`npx supabase login && npx supabase link --project-ref <project-ref> && npx supabase db push`,
optionally with `--dry-run` first) or by pasting a migration file into the Dashboard's SQL editor —
see the setup runbook linked above for the full procedure, RLS policy verification, and required
Dashboard configuration (email confirmation, redirect URLs).

## Authentication

One Supabase project, one user identity, used identically by Android and Web — there is no
per-client user system. Current real capability:

| Capability | Android Phone | Web |
| --- | --- | --- |
| Email/password sign-up + sign-in | Implemented | Implemented |
| Email confirmation | Implemented (deep-link callback) | Implemented (link-based callback) |
| Session restoration | Implemented | Implemented |
| Logout | Implemented | Implemented |
| Google sign-in | Implemented (native Credential Manager ID-token flow) | Not implemented — Android's flow has no browser-OAuth equivalent configured; this was a deliberate scope decision, not an oversight |
| Password recovery | Not implemented — the UI shows an honest "not available yet" message rather than a dead link | Not implemented |

## Current workout capability

Real, account-scoped, and covered by unit tests on Android Phone: start a workout, attach an
exercise (built-in catalog or a custom exercise), log/edit/delete sets, and resume an active workout
after the app restarts or a signed-in session becomes temporarily unavailable. Workout and
custom-exercise ownership is enforced by a real local identity root
(`LocalAccount`, a Room foreign key — see
[`strength-workout-first-slice-design.md`](docs/architecture/strength-workout-first-slice-design.md)),
not by a free-form string; built-in exercises remain globally visible.

**Complete Workout is not yet a user-facing action.** The repository/DAO layer supports it
(`WorkoutRepository.completeWorkout`, unit-tested) but no production UI currently calls it — the
Phone prototype shell's "finish workout" interaction is intentionally non-durable pending human UX
evaluation. Do not infer from the code's existence that this flow is wired up.

Wear OS has no workout-domain code yet; it currently has only a disposable technical-proof record
type and a real, tested phone↔watch replication mechanism for that record type.

## Prototype boundaries

TBDFit deliberately mixes production-backed capabilities with UX-prototype surfaces, evaluated with
real, clickable UI and representative/hardcoded content — not fake repositories — before committing
backend/domain work. See
[`docs/product/frontend-prototype-notes.md`](docs/product/frontend-prototype-notes.md) for the exact
classification of every current screen on Phone and Web. Summary:

- **Real / production-backed:** Supabase auth (both clients), session restore, logout, Android's
  local workout execution and ownership model, Android's Routine domain (Room), Web's Routine and
  Program domains (Supabase, RLS-isolated), Web's optional Profile creation (`/profile`).
- **Mixed:** Android's Home tab and Web's Home dashboard (real signed-in identity next to
  representative content); Profile screens on both clients (real identity, prototype
  settings/stats).
- **Prototype-only:** Android's in-shell Workout Summary; Web's History and Progress. Neither is
  backed by a History/Progress domain model on any client yet.

## Verification status

All current Phone and Wear unit tests pass, and the Web build/lint/type-check/unit-test commands
succeed, as of this repository state — run the commands under [Getting started](#getting-started) to
reproduce current counts rather than trusting a number written here, since it goes stale the moment
either module changes. None of this substitutes for physical-device, emulator, or real-browser
verification, none of which has been performed in this development environment.

## Documentation

**Architecture decisions** (`docs/architecture/adr/`) — verify exact status in each file; this repo
uses `PROPOSED — FOR TEAM REVIEW` and `Accepted` literally, never implied:

| ADR | Decision | Status |
| --- | --- | --- |
| [001](docs/architecture/adr/0001-native-client-architecture.md) | Native Client Architecture | PROPOSED |
| [002](docs/architecture/adr/0002-monorepo-repository-strategy.md) | Monorepo Repository Strategy | PROPOSED |
| [003](docs/architecture/adr/0003-apple-development-and-verification-strategy.md) | Apple Development and Verification Strategy | PROPOSED |
| [004](docs/architecture/adr/0004-initial-backend-platform-and-migration-strategy.md) | Initial Backend Platform and Migration Strategy | **Accepted** |
| [005](docs/architecture/adr/0005-multi-client-responsibility-strategy.md) | Multi-Client Responsibility Strategy | PROPOSED |
| [006](docs/architecture/adr/0006-web-client-technology-stack.md) | Web Client Technology Stack (React + TypeScript + Vite) | PROPOSED |

**Product decisions:** [`docs/product/decisions.md`](docs/product/decisions.md) (PD-001 Independent
Watch Workout Execution, PD-002 Local-First Workout Execution, PD-003 Workout Domain Scope —
deferred) — all `PROPOSED — FOR TEAM REVIEW`.

**Cross-client direction:**
[Multi-Client Product Vision](docs/architecture/multi-client-product-vision.md) (`DESIGN DIRECTION —
FOR TEAM REVIEW`) synthesizes the ADRs and the product/UX research below into one challengeable
picture of where Web/Phone/Watch are headed.

**Product/UX research and proposals** (`docs/product/`) — research documents (`RESEARCH — ...`)
record historical findings and are not rewritten to match later preference; design proposals
(`DESIGN PROPOSAL — FOR TEAM REVIEW`) are the current best hypothesis, not a decided IA:

- [Competitor benchmark — Hevy, Strong, Fitbod](docs/product/competitor-benchmark-hevy-strong-fitbod.md)
- [Frontend/Product UX Research](docs/product/frontend-product-ux-research.md) →
  [Product Information Architecture](docs/product/product-information-architecture.md) (Phone)
- [Web Product UX Research](docs/product/web-product-ux-research.md) →
  [Web Information Architecture](docs/product/web-information-architecture.md)
- [Frontend Prototype Notes](docs/product/frontend-prototype-notes.md) — current
  production-backed/prototype/mixed classification per screen, both clients

**Strength-workout design:** [`strength-workout-first-slice-design.md`](docs/architecture/strength-workout-first-slice-design.md)
(`DESIGN PROPOSAL — FOR TEAM REVIEW`) — the domain model the current Android workout implementation
follows.

## Development principles

- **`IMPLEMENTED ≠ VERIFIED ≠ TEAM ACCEPTED`.** Code existing does not mean it has been run against
  a real device/browser/backend; either of those does not mean the second developer has reviewed
  and accepted the design behind it. Documentation in this repository is written to preserve that
  distinction — expect to see it stated explicitly rather than implied.
- **Supabase stays behind a boundary.** SDK/table/query specifics stay inside each client's
  infrastructure layer; UI and domain code never depend on Supabase directly (see `CLAUDE.md` and
  ADR-004).
- **A proposal implemented is still a proposal.** `PROPOSED`/`RESEARCH`/`DESIGN PROPOSAL` documents
  do not become `Accepted` because provisional implementation proceeded on top of them — only an
  explicit second-developer review changes a status.

## Contributing

This is presently a two-developer project working through architecture/product inception together.
Product scope, the exact native project structure beyond what's built, and synchronization strategy
remain intentionally undecided — see the documents linked above for what's currently proposed, what
question each is trying to answer, and what's deliberately still open.
