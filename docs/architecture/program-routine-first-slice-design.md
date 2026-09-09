# Program / Routine First-Slice Design

**Status: `DESIGN PROPOSAL — FOR TEAM REVIEW`**

> **Implementation note**: Slice A (real `Routine`/`RoutineExercise`/`RoutinePlannedSet`, the
> execution target snapshot, and `Workout.originRoutineId` provenance) has been **implemented
> provisionally**, under this project's `IMPLEMENTED ≠ TEAM ACCEPTED` governance rule — see
> `docs/product/frontend-prototype-notes.md`'s "Routine domain (Slice A)" section for exactly what
> exists and how it was verified. This document's own status is unchanged by that: it remains a
> proposal, not an accepted decision, and implementation proceeding does not promote it. Slice B
> (Program/ProgramWeek/ProgramSession) and Slice C (ProgramSession execution) remain unimplemented.

This is not an ADR and not a product decision. It translates the conclusions of
[`docs/product/program-routine-domain-investigation.md`](../product/program-routine-domain-investigation.md)
(`RESEARCH / DESIGN INVESTIGATION — NOT A PRODUCT DECISION`, unchanged by this document) plus the
explicit product rules the developer locked in afterward into a concrete, implementation-ready
Room schema and repository-API direction. No Kotlin entity, DAO, repository, migration, Supabase
schema, Web UI, or Android UI has been written to produce this document — see FILES CHANGED.

Labels used throughout: **FACT** (verified against current code), **DECISION** (this document's
concrete recommendation — not yet Accepted), **DEFERRED** (explicitly out of scope), **OPEN
QUESTION** (needs human product judgment, not resolvable from the repository alone).

---

## Context

The research document identified that Hevy's own "Program" degrades into a flat `Folder` on
import — literally no week counter, no order semantics beyond display order, no progress state —
and that this is exactly the anti-pattern TBDFit must avoid. It also converged on one
high-confidence, consequential decision: **a Program must own snapshots of its planned sessions,
never live-reference a mutable Routine.** The developer has since locked in four additional V1
rules (quoted in full, they are binding input to this design, not re-litigated):

1. `ProgramWeek` is a **logical** training-program week, never a calendar week — Week 1 having
   Session 1/2/3 does not mean Monday/Wednesday/Friday. Calendar scheduling is deferred entirely.
2. Editing a Program never retroactively changes an already-completed `Workout`.
3. `Workout` retains provenance back to the `ProgramSession` it came from — enough to eventually
   answer "Program: 12-Week Strength, Week 4, Session 2 → Workout abc" — but a `Workout`'s
   historical content must never depend on current, mutable Program/ProgramSession state.
4. V1 progress modeling is minimal: only make it possible to later derive which `ProgramSession`s
   have led to a `Workout` (e.g. "5/36 sessions completed"). No pause/restart/skip/repeat-week/
   adaptive-progression/calendar/deload engine now.

Also locked: **Routine and Program are two parallel, independent paths to execution.**
`Routine → Start → Workout` must work with zero Program involvement, ever. Program is strictly
additive — never a requirement for ordinary training.

---

## Terminology

`Routine`, `Program`, `ProgramWeek`, `ProgramSession`, `Exercise`, `Workout` — unchanged from the
research document's recommendation. `Folder`/`Collection` are not modeled. "Training Day" is UI
copy only; the domain entity is `ProgramSession`. "Plan" remains a nav/product-area name only,
never an entity class.

---

## Product Scope

**In this design's schema:**

```text
Routine → RoutineExercise → RoutinePlannedSet
Program → ProgramWeek → ProgramSession → ProgramSessionExercise → ProgramSessionPlannedSet
Workout (existing) gains two nullable, informational provenance columns
```

**Explicitly out of scope for this design** (see Deferred Capabilities): `Folder`/`Collection`,
calendar scheduling/weekdays, `Block`/`Phase`, an automatic progression/RPE-RIR engine,
percentage-based progression, adaptive programming, pause/restart/skip UX, `ProgramDefinition` vs.
`ProgramInstance`/enrollment, publishing, creator marketplace, program versioning, public sharing,
social discovery, Supabase sync implementation, Web Program UI, Wear Program UI.

---

## Core Invariants

These restate, at one more layer, invariants this codebase already enforces for `Workout` — they
are not new rules:

- **Routine independence**: a `Routine` never requires a `Program`. `Routine → Start → Workout`
  is a complete, self-sufficient lifecycle.
- **Program is additive**: a `Program` never blocks or gates ordinary Routine-based training.
- **Snapshot, never live reference**: once a `ProgramSession`'s planned content is created (whether
  authored directly or seeded from a `Routine`), it is that `ProgramSession`'s own row-owned data.
  Editing or deleting the source `Routine` afterward never changes it.
- **Planned ≠ recorded**: `RoutinePlannedSet`/`ProgramSessionPlannedSet` (intent) and `WorkoutSet`
  (actual result) are separate entities, always. `WorkoutSet.weight`/`reps` are never pre-filled
  with a target value disguised as a performed one.
- **History is durable**: a completed `Workout`'s content is never rewritten by a later edit to, or
  deletion of, the `Routine` or `Program` it came from.
- **Ownership**: `Routine` and `Program` are `LocalAccount`-owned exactly like `Workout` and custom
  `Exercise` already are. Children are owned transitively through their parent — no redundant
  `ownerId` columns, matching the existing `WorkoutExercise`/`WorkoutSet` precedent.

---

## Routine Model

```text
Routine
├── id            : String (UUID, generated at the repository layer — same as every existing entity)
├── ownerId       : String → LocalAccount.id, ON DELETE RESTRICT
├── name          : String
├── createdAt     : Long
└── lastModifiedAt: Long?   (null until first edit after creation — mirrors Workout's own field)

RoutineExercise
├── id         : String (UUID)
├── routineId  : String → Routine.id, ON DELETE CASCADE   (genuinely owned child)
├── exerciseId : String → Exercise.id, ON DELETE RESTRICT (preserves exercise identity — same
│                pattern as WorkoutExercise → Exercise)
└── position   : Int (deterministic ordering, same sparse/no-compaction pattern as WorkoutExercise)

RoutinePlannedSet
├── id                : String (UUID)
├── routineExerciseId : String → RoutineExercise.id, ON DELETE CASCADE
├── position          : Int
├── plannedReps       : Int?     (nullable — a set may specify only load, or neither, for V1)
└── plannedWeight     : Double?  (nullable — bodyweight movements have no target load, mirrors
                                   WorkoutSet.weight's own nullability)
```

No `isCompleted`/`completedAt` on `RoutinePlannedSet` — completion is an execution-time concept
that does not apply to a plan. `RoutineExercise` never snapshots the `Exercise`'s display name (a
rename must transparently propagate), identical reasoning to `WorkoutExercise`.

---

## Program Model

```text
Program
├── id            : String (UUID)
├── ownerId       : String → LocalAccount.id, ON DELETE RESTRICT
├── name          : String
├── createdAt     : Long
└── lastModifiedAt: Long?
```

`Program` intentionally carries **no** "current week," "current session," or completion-state
field. Progress is always *derived* at read time (see Local-First / Repository API sections below),
never stored as mutable state on the Program row — this is what keeps §15's eventual
Definition/Instance split (deferred, see below) cheap to add later: nothing about "whose progress
this is" is baked into the definition row today.

---

## ProgramWeek Semantics

```text
ProgramWeek
├── id        : String (UUID)
├── programId : String → Program.id, ON DELETE CASCADE (genuinely owned child)
└── position  : Int (ordering only — NOT a calendar week number, NOT tied to any date)
```

**DECISION**: no `startDate`, `endDate`, `calendarWeekNumber`, or weekday field of any kind. "Week 4
of 12" in a future UI is derived purely from `position` (0-indexed internally, displayed as
`position + 1`) and the count of sibling `ProgramWeek` rows — exactly the same displayed-vs-stored
distinction `WorkoutSet`'s display numbering already uses (see `ActiveWorkoutScreen`'s own doc
comment on deriving display numbers from list index, never the raw `position` column).

A repeating microcycle (e.g. PPL run for 12 weeks) is represented in V1 by **authoring multiple
`ProgramWeek` rows with equivalent content** — an authoring-tool convenience question (a future
"duplicate week" button), not a schema requirement. A genuinely *infinite*, non-terminating
rotation (Starting Strength's alternate-A/B-forever model) is **out of scope for V1 Program** — see
Deferred Capabilities. Starting Strength-style training remains fully supported today via plain
`Routine → Start → Workout`, with no Program involved at all; that is precisely why Routine
independence is a core invariant, not a limitation.

---

## ProgramSession Model

```text
ProgramSession
├── id             : String (UUID)
├── programWeekId  : String → ProgramWeek.id, ON DELETE CASCADE
├── position        : Int
├── name            : String?  (nullable — see decision below)
└── sourceRoutineId : String? → Routine.id, ON DELETE SET NULL  (informational only)

ProgramSessionExercise
├── id               : String (UUID)
├── programSessionId : String → ProgramSession.id, ON DELETE CASCADE
├── exerciseId       : String → Exercise.id, ON DELETE RESTRICT
└── position         : Int

ProgramSessionPlannedSet
├── id                       : String (UUID)
├── programSessionExerciseId : String → ProgramSessionExercise.id, ON DELETE CASCADE
├── position                 : Int
├── plannedReps              : Int?
└── plannedWeight            : Double?
```

**DECISION — `ProgramSession.name` is nullable.** If null, the UI derives "Day {position + 1}" —
matching §12's "avoid Day-1-means-Monday" guidance while not forcing every session to be manually
named (most real programs just say "Day 1/2/3", not a bespoke name per session). An author may
still set an explicit name (e.g. "Heavy Bench Day") when it adds value. This is a light UX
convenience decision, not a data-integrity one — safe to revisit without a migration (adding
meaning to an already-nullable column is free).

**`sourceRoutineId` is `ON DELETE SET NULL`, not `RESTRICT`.** This is a deliberate, explained
deviation from the RESTRICT-almost-everywhere pattern used elsewhere in this codebase
(`Workout.ownerId`, `WorkoutExercise.exerciseId`, etc.): those RESTRICTs protect columns the
referencing row's *current meaning* depends on. `sourceRoutineId` is the opposite — purely a
traceability breadcrumb the `ProgramSession` does not depend on for its own (already-copied)
content. Blocking a user from deleting a `Routine` merely because a `Program` was once seeded from
it would be a real, needless UX cost with no integrity benefit, since the `ProgramSession`'s
planned content is already fully independent (see Snapshot / Copy Semantics below). `SET NULL`
preserves that independence explicitly at the schema level.

---

## Planning vs. Execution

`RoutinePlannedSet` / `ProgramSessionPlannedSet` (intent: "3 sets of 8 at 80 kg") and `WorkoutSet`
(fact: "what was actually done") remain two separate entity families, full stop — never merged,
never reused for the other's purpose, exactly matching the existing
`strength-workout-first-slice-design.md` PLAN/INTENT → execution → recorded-result boundary
extended one layer up. See "Starting a Routine/ProgramSession" below for the precise mechanism that
prevents a planned target from ever being silently written into `WorkoutSet.weight`/`reps` as if it
were a performed value.

---

## Snapshot / Copy Semantics

**This is the single highest-confidence decision in the research and remains the highest-confidence
decision here.** When a `ProgramSession` is created from a `Routine` (an authoring convenience, not
a requirement — a `ProgramSession` may equally be authored directly with no source `Routine` at
all), the operation is a **copy**:

```text
Routine
  ├── RoutineExercise ("Bench Press", position 0)
  │     └── RoutinePlannedSet × 3 (8 reps, 80 kg)
  └── RoutineExercise ("Overhead Press", position 1)
        └── RoutinePlannedSet × 3 (10 reps, 40 kg)

        │  COPY (one-time, at ProgramSession-creation time)
        ▼

ProgramSession (sourceRoutineId = the Routine's id, informational only)
  ├── ProgramSessionExercise ("Bench Press", position 0)
  │     └── ProgramSessionPlannedSet × 3 (8 reps, 80 kg)   ← independent rows, own IDs
  └── ProgramSessionExercise ("Overhead Press", position 1)
        └── ProgramSessionPlannedSet × 3 (10 reps, 40 kg)
```

There is **no foreign key anywhere from a `ProgramSessionExercise`/`ProgramSessionPlannedSet` row
back to `RoutineExercise`/`RoutinePlannedSet`** — the schema is structurally incapable of a live
dependency, not merely disciplined not to use one. Editing `Routine: Push A` in January and again
in March never touches a `ProgramSession` that was seeded from it back in February. This is the
literal mechanism satisfying the developer's locked rule #2 one layer down (Routine edits must
never retroactively change a Program), by construction rather than by convention.

---

## Execution Lifecycles

Two parallel, independent paths, both terminating in the same real `Workout`:

```text
Routine ──────────────────────────┐
   (Start)                        │
                                   ▼
                               Workout ── WorkoutExercise ── WorkoutSet
                                   ▲
   (Start)                        │
Program → ProgramWeek → ProgramSession ─┘
```

Neither path duplicates execution architecture. Both call the same underlying atomic start
operation described below; they differ only in what they read as the source of planned content and
which provenance column they populate.

### Starting a Routine or ProgramSession

```text
startRoutine(routineId, ownerId)          startProgramSession(programSessionId, ownerId)
```

Both are proposed as `@Transaction` repository/DAO operations (the same atomicity pattern already
used by `WorkoutDao.startWorkoutIfNoneActive`, `WorkoutSetDao.appendSet`,
`WorkoutSetDao.completeIfRepsPresent`), returning the existing `StartWorkoutResult`
(`Started`/`AlreadyActive`) — no new result type invented. Each operation:

1. Validates the `Routine`/`ProgramSession`'s owning `Program`/`Routine` actually belongs to
   `ownerId` (cross-account isolation — see below).
2. Delegates to the existing `startWorkoutIfNoneActive` one-active-workout-per-owner guarantee —
   no parallel active-workout concept is introduced. If a workout is already active, planned
   content is **not** attached to it (mirrors the existing prototype's chosen `AlreadyActive`
   behavior exactly — the caller is routed to the existing active workout, untouched).
3. On success: creates the `Workout` row with `originRoutineId` (direct-Routine path) or
   `originProgramSessionId` (Program path) set; creates `WorkoutExercise` rows, one per
   `RoutineExercise`/`ProgramSessionExercise`, preserving `position` and `exerciseId`.
4. **Planned-set seeding — hardened (supersedes the base design's "read the target via a live join
   at render time," which violated the START-creates-a-snapshot invariant below)**: for each
   attached exercise, pre-create as many `WorkoutSet` rows as there are planned sets. Each row's new
   `targetReps`/`targetWeight` columns (see Execution Target Snapshot, below) are **copied once, at
   this moment, from the corresponding `RoutinePlannedSet`/`ProgramSessionPlannedSet`** — this copy
   *is* the execution snapshot. `reps = null`, `weight = null`, `isCompleted = false`,
   `completedAt = null`, exactly as before: unperformed work stays unperformed. After this point,
   nothing ever reads `targetReps`/`targetWeight` back from the source plan — there is no live join,
   no re-fetch, no path by which editing the `Routine`/`ProgramSession` afterward changes what an
   already-created `WorkoutSet` reports as its target. A `WorkoutSet` created with no source plan
   (a plain "Add Set" tap, as today) simply has `targetReps = null, targetWeight = null` — the same
   nullable columns, no target to show, no special case.

### Execution Target Snapshot (hardening addition)

**DECISION**: the target snapshot lives directly on `WorkoutSet`, as two new nullable columns —
not a separate execution-snapshot table.

```text
WorkoutSet (existing entity, additive columns)
├── ... existing fields, unchanged: id, workoutExerciseId, position, reps, weight, isCompleted,
│                                    completedAt ...
├── targetReps?   : Int?     (execution-time snapshot of planned reps — copied once, at START,
│                              from RoutinePlannedSet/ProgramSessionPlannedSet; null if this set
│                              has no source plan)
└── targetWeight? : Double?  (same semantics, for weight)
```

Evaluated and rejected: a separate `ExecutionTargetSnapshot`/`WorkoutSetTarget` table keyed 1:1 to
`WorkoutSet`. It would carry the exact same two nullable value columns and the exact same
lifecycle (written once, at creation, never updated) — the extra table buys no additional
constraint or integrity property, only an extra join for every read that wants to show a target
alongside an actual value. Two plain nullable columns on the entity that already represents "one
set, at this position, in this execution" is the smaller, equally-safe option — consistent with
§28's "prefer explicit fitness-domain concepts over generic abstractions," re-applied here rather
than introducing a `Measurement`/`Metric`-shaped indirection layer.

This satisfies all five stated requirements:

1. **Target survives source-plan edits** — the value is copied at creation time into a column the
   source plan has no further write access to; there is no FK, no join, no dependency left after
   the copy.
2. **Actual values remain actual** — `reps`/`weight` are untouched by this change; they still mean
   exactly what they meant before (a genuinely logged result), and `targetReps`/`targetWeight` are
   new, separate columns that never get copied into `reps`/`weight` or vice versa.
3. **Active Workout is self-contained after START** — every `WorkoutSet` a Workout owns carries its
   own target (if any) at creation time; nothing about rendering the active-workout screen needs to
   re-read `Routine`/`Program`/`ProgramSession` state at all.
4. **Completed Workout history remains independent** — `targetReps`/`targetWeight` are exactly as
   durable as `reps`/`weight` already are; no new dependency on mutable planning data is introduced
   for historical display.
5. **Target may be shown alongside previous performance and actual input** — three distinct data
   sources, never merged: `targetReps`/`targetWeight` (this Workout's own stored snapshot),
   `reps`/`weight` (this Workout's own stored actual, the input field before it's filled), and
   **PREVIOUS** (a separate, *derived*, read-time query over the most recent prior `COMPLETED`
   `WorkoutSet` for the same `Exercise` — not stored anywhere, not part of this schema, exactly as
   TBDFit's existing "previous performance" concept from the original strength-slice design already
   works). TARGET and ACTUAL are the only two values ever persisted on `WorkoutSet`; PREVIOUS is
   computed, not stored, on every client that wants to show it.

---

## Provenance

```text
Workout (existing entity, additive columns)
├── ... existing fields, unchanged ...
├── originRoutineId        : String? → Routine.id,        ON DELETE SET NULL
└── originProgramSessionId : String? → ProgramSession.id, ON DELETE RESTRICT   ← hardened, was SET NULL
```

**Hardening pass — these two FKs deliberately no longer match.** The base design gave both
`SET NULL`, reasoning by symmetry. Re-evaluated against three options for `originProgramSessionId`
specifically:

- **Option A — RESTRICT** (chosen): a `ProgramSession` that has produced any `Workout` cannot be
  hard-deleted while that reference exists. Simple, no new tables, and — critically — it's the same
  mechanism this codebase already uses for exactly this kind of "don't let deletion silently orphan
  history" problem (`WorkoutExerciseEntity.exerciseId → Exercise`, `RESTRICT`, proven in
  `WorkoutExerciseDaoTest.deletingAnExerciseReferencedByWorkoutHistoryIsRestrictedNotCascaded`).
- **Option B — archive/soft-delete `ProgramSession`** (rejected): would preserve provenance and let
  a user "remove" a session from active planning, but introduces a real lifecycle/state dimension
  (an archived-but-undeletable row, a filter every planning query must now apply) for a V1 problem
  Option A already solves with zero new concepts. No evidence in the base research or this hardening
  pass justifies that cost now.
- **Option C — `SET NULL` + a denormalized historical-context snapshot on `Workout`** (rejected for
  now): would let the source `ProgramSession` be deleted freely while `Workout` keeps enough of its
  own copy (program name, week/session position, display name) to still show "Week 4, Session 2" in
  history. This is a real, evaluated option — not a bad one — but it adds three-to-four denormalized
  columns and a second copy-at-creation-time rule for a capability (deleting a `ProgramSession` that
  has already produced training history) nothing in the product decisions says is actually needed in
  V1. RESTRICT is strictly simpler and can be *relaxed* into Option C later without a breaking
  schema change (adding nullable snapshot columns is always additive); the reverse — tightening
  `SET NULL` into `RESTRICT` after users have already deleted referenced rows — would not be
  possible without a data-integrity problem. Preferring the tighter, later-relaxable constraint now
  is the safer order of operations.

`originRoutineId` deliberately **keeps `SET NULL`** — evaluated separately, not by forced symmetry.
A `Workout` started directly from a `Routine` (no `Program` involved) uses that provenance purely as
a display convenience ("started from: Push A"); nothing in this design derives progress, completion
counts, or any load-bearing computation from `originRoutineId`. `originProgramSessionId` is
different: it is the *only* signal `getProgramProgress`/`getNextSession` (see Repository API
Direction) can use to answer "which sessions have been done" — silently losing that link on delete
would silently corrupt a Program's derived progress, which is exactly the class of problem RESTRICT
exists to prevent. Where the two columns carry different weight, they get different delete
semantics — forcing symmetry here would either over-constrain Routine deletion for no benefit, or
under-protect Program progress for no benefit.

A `Workout` started directly (no Routine, no Program) simply has both columns null from the start,
same as every `Workout` created before this slice ships (a purely additive migration — see Migration
Impact). This remains the **only** new provenance surface — no `sourceRoutineId` is duplicated onto
`WorkoutExercise`/`WorkoutSet` individually; a `Workout`'s single provenance pair is sufficient to
answer "which Program/Week/Session, if any, produced this whole session."

---

## Ownership Model

```text
Routine.ownerId → LocalAccount.id      (ON DELETE RESTRICT — same as Workout.ownerId, Exercise.ownerId)
Program.ownerId → LocalAccount.id      (ON DELETE RESTRICT)

RoutineExercise, RoutinePlannedSet              → owned transitively via Routine (no own ownerId)
ProgramWeek, ProgramSession,
ProgramSessionExercise, ProgramSessionPlannedSet → owned transitively via Program (no own ownerId)
```

No child table gets its own `ownerId` column — identical reasoning to why `WorkoutExercise`/
`WorkoutSet` have none today: ownership is established once, at the top of the tree, and every
child's identity is only ever reached through a query that already scoped by the parent's owner.
Adding redundant `ownerId` columns on children would duplicate data with no integrity benefit and a
real risk of the two copies drifting.

### Cross-account isolation (repository/query semantics, not FK integrity alone)

FK integrity guarantees a `Routine`/`Program` row references a *real* `LocalAccount`, not that the
*caller* is that account — exactly the same gap this codebase already closed for `Exercise`
(`ExerciseDao.getVisibleTo(ownerId)`) and `Workout` (`WorkoutDao.getActiveWorkout(ownerId)`, no
unscoped variant reachable from application code). The same discipline applies here:

- `RoutineDao`/`ProgramDao` expose **only** owner-scoped queries (`getRoutinesFor(ownerId)`,
  `getProgramsFor(ownerId)`) — no "get all routines" query exists, mirroring `ExerciseDao`'s
  removed `getAll()`.
- `startRoutine`/`startProgramSession` verify `Routine.ownerId == ownerId` /
  `Program.ownerId == ownerId` (via the owning `Program`, for a `ProgramSession`) before doing
  anything, returning a not-found/rejected result for a mismatch rather than silently succeeding —
  the same "no unscoped mutation path" discipline as `WorkoutRepository`'s existing methods.
- **Exercise cross-reference**: `RoutineExercise.exerciseId`/`ProgramSessionExercise.exerciseId`
  can only structurally reference an `Exercise` that exists — the FK does not, and cannot, check
  *whose* custom exercise it is. This is not a new gap: `WorkoutExercise.exerciseId` has the exact
  same property today. The existing mitigation — the exercise picker only ever queries
  `ExerciseDao.getVisibleTo(ownerId)`, so another account's custom exercise is never offered as a
  choice — applies unchanged to the Routine/Program authoring UI. Built-in exercises
  (`ownerId = null`) remain usable by everyone, exactly as today.

---

## Delete / Mutation Rules

| Action | Effect |
|---|---|
| Delete `Routine` | `RoutineExercise`/`RoutinePlannedSet` CASCADE (genuinely owned). Any `ProgramSession.sourceRoutineId` pointing at it → `SET NULL` (unaffected content — the snapshot already stands alone). Any `Workout.originRoutineId` pointing at it → `SET NULL` (history survives; unconditional, never blocked). |
| Delete `ProgramSession` that has **never** produced a `Workout` | Succeeds normally — CASCADEs to its own `ProgramSessionExercise`/`ProgramSessionPlannedSet` rows. |
| Delete `ProgramSession` that **has** produced a `Workout` (active or completed) | **Rejected — `RESTRICT`.** The delete throws (same exception-based pattern as the existing Exercise-deletion protection); nothing is silently cascaded or nulled. The `ProgramSession` and the `Workout` both remain exactly as they were. |
| Delete `Program` where **no** descendant `ProgramSession` has ever produced a `Workout` | Succeeds normally — CASCADEs through `ProgramWeek` → `ProgramSession` → `ProgramSessionExercise` → `ProgramSessionPlannedSet`. |
| Delete `Program` where **any** descendant `ProgramSession` has produced a `Workout` | **Rejected — the whole delete fails.** SQLite evaluates foreign-key constraints as it executes a cascading delete; the moment the cascade reaches a `ProgramSession` still referenced by `Workout.originProgramSessionId`, that row's `RESTRICT` fires and aborts the *entire* statement — the `Program`, all its weeks, and all its sessions remain fully intact, not partially deleted. This is a direct, honest consequence of choosing RESTRICT one level down, not a separate rule: **a Program that has ever been trained from cannot be hard-deleted in V1.** See Open Human Decisions for the resulting UX question. |
| Edit `Routine` (rename, change planned sets/reps) | Affects the `Routine` itself and all **future** direct starts and **future** ProgramSession-from-Routine copies. Never touches already-created `ProgramSession`s or `Workout`s (no live reference exists to touch) — and, per the hardening pass, never touches an already-created `WorkoutSet`'s `targetReps`/`targetWeight` either, since those were copied once at START. |
| Edit `ProgramSession` | Affects that `Program`'s planned prescription for future starts of that session. Never touches the source `Routine` (no reverse reference exists) or any already-created `Workout` (already-started execution rows, including their target snapshot, are independent per the seeding mechanism above — an edit after a `Workout` has started never mutates that `Workout`'s `WorkoutExercise`/`WorkoutSet` rows, including `targetReps`/`targetWeight`). **Explicitly allowed even for a "passed" week/session** — see Open Human Decisions #1. |
| Delete built-in/custom `Exercise` referenced by `RoutineExercise`/`ProgramSessionExercise` | `RESTRICT` — identical to the existing `WorkoutExercise.exerciseId` behavior; an exercise still referenced by planned content cannot be deleted out from under it. |

No cascading delete anywhere in this design can destroy a completed `Workout`. `originRoutineId`
stays `SET NULL` (never blocks anything). `originProgramSessionId` is now `RESTRICT` — the one
deliberate, hardened departure, and the reason a trained-from `Program`/`ProgramSession` cannot be
hard-deleted in V1.

---

## Local-First Implications

Whatever `Routine`/`Program`/`ProgramWeek`/`ProgramSession` data a user is actively relying on must
be fully present in Room for Phone to start today's session **even if a backend is temporarily
unavailable** — a direct extension of the already-shipped `SessionUnavailable` local-workout-access
pattern (`android/phone/.../auth/SessionUnavailableWorkoutAccess.kt`), not a new principle. Since
this design keeps everything local-first from day one (no remote-only representation is proposed),
this falls out for free: there is no "Program only exists remotely" failure mode to design against,
because there is no remote Program representation yet at all. Whatever future Supabase replication
mechanism is chosen (still undecided — PD-001/PD-002/ADR-005) will need to carry this data too, but
that is an additional payload shape for an already-open question, not a new one this design
introduces.

**Web** does not execute anything (still an open question per ADR-006/the web research,
unaffected by this document) — Web's role, if/when built, is authoring/reviewing Program and
Routine content, not running it. **Watch** gets, at most, "current/next planned session" read
access in a future slice — never Program authoring — consistent with ADR-005's execution-only Watch
role. Neither is designed or implemented here.

---

## Proposed Room Schema

Eight new tables, plus additive columns on **two** existing tables — `workouts` (as in the base
design) and, new in this hardening pass, `workout_sets` (the execution target snapshot). All fields
not listed explicitly as nullable are `NOT NULL`.

| Table | Purpose | PK | FKs (ON DELETE) | Ownership path | Ordering | Nullable fields | Delete behavior |
|---|---|---|---|---|---|---|---|
| `routines` | A standalone reusable session template | `id` | `ownerId → local_accounts.id` (RESTRICT) | Direct | — | `lastModifiedAt` | RESTRICT protects nothing external; deleting a Routine cascades to its own children only |
| `routine_exercises` | One exercise slot within a Routine | `id` | `routineId → routines.id` (CASCADE); `exerciseId → exercises.id` (RESTRICT) | Via `routineId` | `position` | — | Deleted with parent Routine |
| `routine_planned_sets` | One planned set within a RoutineExercise | `id` | `routineExerciseId → routine_exercises.id` (CASCADE) | Via `routineExerciseId` → `routineId` | `position` | `plannedReps`, `plannedWeight` | Deleted with parent |
| `programs` | A structured, ordered multi-week plan | `id` | `ownerId → local_accounts.id` (RESTRICT) | Direct | — | `lastModifiedAt` | Cascades to its own week/session tree only |
| `program_weeks` | An ordered logical week within a Program | `id` | `programId → programs.id` (CASCADE) | Via `programId` | `position` | — | Deleted with parent Program |
| `program_sessions` | One planned executable session within a ProgramWeek | `id` | `programWeekId → program_weeks.id` (CASCADE); `sourceRoutineId → routines.id` (SET NULL) | Via `programWeekId` → `programId` | `position` | `name`, `sourceRoutineId` | Deleted with parent week; source-Routine deletion only nulls the breadcrumb |
| `program_session_exercises` | One exercise slot within a ProgramSession | `id` | `programSessionId → program_sessions.id` (CASCADE); `exerciseId → exercises.id` (RESTRICT) | Via `programSessionId` | `position` | — | Deleted with parent session |
| `program_session_planned_sets` | One planned set within a ProgramSessionExercise | `id` | `programSessionExerciseId → program_session_exercises.id` (CASCADE) | Via chain to `programId` | `position` | `plannedReps`, `plannedWeight` | Deleted with parent |
| `workouts` (existing, additive) | +2 columns | — | + `originRoutineId → routines.id` (SET NULL); + `originProgramSessionId → program_sessions.id` (**RESTRICT**, hardened) | Unchanged (`ownerId`) | Unchanged | Both new columns nullable | `originRoutineId` never blocks Routine deletion. `originProgramSessionId` **blocks deletion of the referenced ProgramSession (and transitively its Program) — see Delete/Mutation Rules and Workout Provenance Decision** |
| `workout_sets` (existing, additive — new in this hardening pass) | +2 columns: execution target snapshot | — | none (plain value columns, no reference) | Unchanged (transitive via `workoutExerciseId`) | Unchanged | `targetReps`, `targetWeight` — both nullable (null when the set has no source plan) | N/A — value columns only; copied once at creation, never updated afterward, no delete semantics of their own |

No generic `PlanNode`/`Container`/`ScheduleItem` abstraction anywhere — every table is an explicit,
named fitness-domain concept, per the research document's §28 conclusion, re-confirmed here: nothing
in this schema needed one.

---

## Migration Impact

Current `AppDatabase` is at **version 6** (`android/phone/schemas/com.tbdfit.phone.localstorage.AppDatabase/6.json`
is the canonical current schema). This design proposes **version 7**, following the exact discipline
already established (`exportSchema = true`, an explicit `Migration` object, a real migration test
against `MigrationTestHelper`, no `fallbackToDestructiveMigration`):

- The eight new tables are pure `CREATE TABLE` additions — no existing data to preserve, same shape
  as `MIGRATION_3_4`'s original workout-domain table introduction.
- The two new `workouts` columns still require a **table recreate** (SQLite cannot `ALTER TABLE ADD
  COLUMN` with a foreign-key constraint reliably, and Room's schema validation must see a real
  declared FK) — the exact same create-new-table/copy-data/drop-old/rename technique already used
  and tested in `MIGRATION_5_6` for adding `Workout.ownerId`'s FK. The only change from the base
  design: `originProgramSessionId`'s `ON DELETE` clause in the recreated table is now `RESTRICT`,
  not `SET NULL` — a one-word difference in the generated `CREATE TABLE` statement, no other change
  to this step. Existing rows still get `originRoutineId = NULL, originProgramSessionId = NULL` —
  correct and lossless either way, since no `Workout` created before this slice ever had a
  Routine/Program origin, and `RESTRICT` only ever fires on an attempted *delete* of a still-referenced
  `program_sessions` row, never on this migration's own inserts.
- **New in this hardening pass**: `workout_sets.targetReps`/`targetWeight` are plain nullable
  columns with **no foreign key**, so they can be added with a simple `ALTER TABLE workout_sets ADD
  COLUMN targetReps INTEGER` / `ADD COLUMN targetWeight REAL` — no table recreate needed for these
  two, unlike the `workouts` columns above. Existing `WorkoutSet` rows get both columns `NULL`
  (correct: no `WorkoutSet` created before this slice was ever seeded from a plan).
- A new migration test (`AppDatabaseMigrationTest`, extending the existing file) should assert:
  existing `workouts` rows survive the 6→7 migration with both new columns `NULL`; existing
  `workout_sets` rows survive with `targetReps`/`targetWeight` both `NULL`; all eight new tables
  exist and are empty; and `runMigrationsAndValidate(..., true, ...)` passes — which, for
  `originProgramSessionId`, is specifically what proves the hand-written migration SQL declares
  `RESTRICT` (not `SET NULL`) in a way that structurally matches the new entity annotation, the same
  validation technique already relied on for every prior migration in this codebase.

No migration is written as part of this document.

---

## Repository API Direction

Not implemented here — conceptual shape only, to keep the eventual implementation from having to
re-derive it:

```text
RoutineRepository (or extend WorkoutRepository, consistent with "one concrete class per domain,
not a generic Repository<T>" — see WorkoutRepository's own doc comment):
  createRoutine(ownerId, name) → Routine
  addExerciseToRoutine(routineId, exerciseId) → RoutineExercise           (position auto-assigned)
  addPlannedSetToRoutineExercise(routineExerciseId, reps?, weight?) → RoutinePlannedSet
  getRoutinesFor(ownerId): Flow<List<Routine>>                            (owner-scoped, no unscoped variant)
  startRoutine(routineId, ownerId): StartWorkoutResult                    (existing result type, reused)

ProgramRepository:
  createProgram(ownerId, name) → Program
  addWeek(programId) → ProgramWeek                                       (position auto-assigned)
  addSession(programWeekId, name?) → ProgramSession
  createSessionFromRoutine(programWeekId, routineId) → ProgramSession    (the COPY operation)
  addExerciseToSession(programSessionId, exerciseId) → ProgramSessionExercise
  addPlannedSetToSessionExercise(programSessionExerciseId, reps?, weight?) → ProgramSessionPlannedSet
  getProgramsFor(ownerId): Flow<List<Program>>
  startProgramSession(programSessionId, ownerId): StartWorkoutResult

Derived (read-only, no stored state):
  getProgramProgress(programId): { totalSessions, completedSessions }
    — computed by counting ProgramSessions with ≥1 COMPLETED Workout referencing them via
      originProgramSessionId; no ProgramProgress table
  getNextSession(programId): ProgramSession?
    — first ProgramSession in (week position, session position) order with no COMPLETED Workout
      referencing it
```

Both `startRoutine`/`startProgramSession` are proposed as `@Transaction` DAO methods following the
existing `startWorkoutIfNoneActive`/`appendSet`/`completeIfRepsPresent` pattern exactly — no new
transactional idiom introduced.

---

## Test Strategy

Described, not implemented — matching the granularity of this codebase's existing DAO/repository
test suites:

- **Routine independence**: a `Routine` (and its exercises/planned sets) can be created and
  persisted with zero `Program` involvement; `startRoutine` produces a real `Workout` with no
  `Program`/`ProgramSession` row ever created.
- **Program hierarchy**: a `Program` owns ordered `ProgramWeek`s; a `ProgramWeek` owns ordered
  `ProgramSession`s; position values are deterministic and independent per parent (mirroring
  existing `WorkoutExercise`/`WorkoutSet` ordering tests).
- **Snapshot immutability**: create a `ProgramSession` from a `Routine`; edit the `Routine`'s
  planned sets afterward; assert the `ProgramSession`'s planned content is byte-for-byte unchanged.
- **Program edit does not touch Routine**: edit a `ProgramSession`'s planned content; assert the
  source `Routine` is unchanged.
- **Execution independence**: start a `ProgramSession` into a `Workout`; edit the `ProgramSession`
  afterward; assert the already-created `Workout`/`WorkoutExercise`/`WorkoutSet` rows are unchanged.
- **Execution target snapshot (Routine path)**: start a `Routine` into a `Workout`; assert each
  resulting `WorkoutSet.targetReps`/`targetWeight` matches the source `RoutinePlannedSet`; edit the
  `Routine`'s planned sets afterward; assert the already-created `WorkoutSet` rows' target values
  are unchanged.
- **Execution target snapshot (ProgramSession path)**: same test, starting from a `ProgramSession`
  instead, editing the `ProgramSession`'s planned content afterward.
- **Target ≠ actual**: immediately after `startRoutine`/`startProgramSession`, assert every
  resulting `WorkoutSet` has `targetReps`/`targetWeight` populated (when the source plan specified
  them) while `reps = null, weight = null, isCompleted = false, completedAt = null` — the target
  being present must never be mistaken for the set being performed.
- **Routine deletion is unconditional (contrast case)**: complete a `Workout` via direct `Routine`
  execution; delete the `Routine`; assert the `Workout` and its `WorkoutSet` rows survive with
  `originRoutineId` now `NULL` — proving the intentionally weaker `SET NULL` semantics for Routine
  provenance actually behave as designed, in contrast to the next two tests.
- **ProgramSession deletion is blocked by history**: complete a `Workout` via a `ProgramSession`;
  attempt to delete that `ProgramSession`; assert the delete is rejected (throws), and that both the
  `ProgramSession` and the `Workout` are unchanged afterward.
- **Program hard-delete is blocked transitively**: complete a `Workout` via one `ProgramSession` in
  a multi-week `Program`; attempt to delete the whole `Program`; assert the delete is rejected and
  that the entire `Program`/`ProgramWeek`/`ProgramSession` tree remains fully intact (not partially
  deleted) — the direct test of the "RESTRICT propagates up through a cascading delete and aborts
  the whole statement" mechanic described in Delete/Mutation Rules. A second variant asserts a
  `Program` with **no** executed sessions deletes cleanly.
- **Ownership / cross-account isolation**: owner A's `Routine`/`Program` is invisible to and
  cannot be started by owner B (mirrors the existing `WorkoutRepositoryTest`/`ExerciseDaoTest`
  cross-account tests exactly).
- **Exercise ownership boundary**: a built-in exercise is usable in any owner's Routine/Program; a
  custom exercise is usable only in its owner's Routine/Program (verified at the repository/query
  layer, since the FK alone cannot express it — same caveat as the existing `WorkoutExercise`
  relationship).
- **Deterministic ordering**: week/session/exercise/planned-set ordering survives deletion of a
  sibling (sparse positions, no compaction — same as `WorkoutSetDao`'s existing behavior) and app
  recreation (same close/reopen-against-the-same-file proxy already used by
  `WorkoutRestoreAfterRecreationTest`).
- **Migration**: the v6→v7 migration test described above.

---

## Deferred Capabilities

Explicitly not modeled or implemented by this design, with reasoning already established in the
research document (not re-derived here): `Folder`/`Collection`, calendar scheduling and weekday
fields, `Block`/`Phase` grouping above `ProgramWeek`, an automatic progression/RPE-RIR engine,
percentage-based/formula progression, adaptive programming, infinite/non-terminating rotation
programs, pause/restart/skip/repeat-week UX, `ProgramDefinition` vs. `ProgramInstance`/enrollment,
publishing, creator marketplace, program versioning, public sharing, social discovery, any Supabase
sync implementation, Web Program UI, Wear Program UI. None of these are hinted at or made harder to
add later by this schema — in particular, keeping "current week/session" fully derived (never
stored) means the eventual Definition/Instance split is additive, not a rewrite.

---

## Risks / Trade-offs

- **Duplicated planned-content tables** (`Routine*` vs. `ProgramSession*`) read as repetitive at
  first glance. This is a deliberate trade-off: the alternative (a shared/generic
  "PlannedPrescription" table referenced by both) would reintroduce exactly the live-reference risk
  §14 of the research document identified as the highest-confidence thing to avoid, or would need
  its own copy-on-write machinery to avoid it — more indirection for the same guarantee this
  design gets for free by having two genuinely separate, explicitly named tables.
- **Pre-creating empty `WorkoutSet` rows on start** (rather than lazily on "Add Set") is a real,
  if small, behavior change to today's flow (`ActiveWorkoutScreen`'s "Add Set" button currently
  always creates exactly one set at a time). If this proves visually or functionally awkward during
  implementation, the fallback described inline above (create zero rows, show planned counts only)
  is a same-schema, repository-only change — not a design failure mode.
- **`SET NULL` provenance is a one-way breadcrumb** — once a `Routine`/`Program` is deleted, a
  `Workout`'s "where this came from" link is permanently gone (by design), so any future analytics
  relying on it must tolerate `NULL`. This is the correct trade-off given the locked invariant that
  deletion must never be blocked by, or destroy, history.

---

## Open Human Decisions

Three of the four items the base design left open are now **resolved** by this hardening pass, per
explicit product direction. One new item was introduced by the provenance decision above.

**RESOLVED — editing passed weeks/sessions**: **DECISION**: `ProgramWeek`/`ProgramSession` editing
is allowed unconditionally, including for a week/session that already has a completed `Workout`
against it. No "lock past weeks" concept is introduced. This is safe precisely because of the
target-snapshot hardening above: an edit can never retroactively change what an already-completed
`Workout` shows, so there is no correctness reason to restrict editing — only a possible future UX
argument, not a data-integrity one.

**RESOLVED — "Current Program"**: **DECISION**: deferred, as the base design already proposed — no
`activeProgram`/`currentProgram` entity or column is introduced. If a future Plan screen needs a
"Current Program" concept, it is derived (most-recently-started, or a lightweight user preference)
at the UI/repository layer, never stored as schema.

**RESOLVED — `ProgramSession.name`**: **DECISION**: stays nullable/optional with the UI-derived
"Day N" fallback, as the base design proposed. No change.

**NEW — surfacing an undeletable Program/ProgramSession**: the RESTRICT decision above means a user
can attempt to delete a `Program` or `ProgramSession` that has training history and have that
attempt fail. This is a genuine, still-open product-taste question: should the UI **proactively
hide or disable** the delete action once any descendant session has a `Workout` (requiring a query
before rendering the delete affordance), or should it **let the attempt happen and show a clear
error** ("This program has training history and can't be deleted")? The backend/schema behavior is
decided either way (RESTRICT); only the UI's handling of the resulting failure is open, and it has
no schema impact — it can be answered later, independently of implementing Slices A–C.

---

## Recommended Implementation Slice

Consistent with, and slightly more concrete than, the research document's §32 recommendation
(a thin vertical slice, not "UX-first" or "backend-first" in the pure sense):

**Slice A — Real Routine domain.** `routines`/`routine_exercises`/`routine_planned_sets` +
`workout_sets.targetReps`/`targetWeight` (v7 migration, part 1 — the target-snapshot columns ship
here, since `startRoutine` is the first path that needs to populate them), `RoutineDao`/
`RoutineRepository` additions, `startRoutine` with `originRoutineId` provenance (`SET NULL`,
unchanged by hardening) **and** target-snapshot seeding into each created `WorkoutSet`. Wire the
**existing** prototype Routine Library/Detail screens (`com.tbdfit.phone.shell.RoutinePrototype.kt`)
to this real backend in place — same screens, real data source, no new UI surface. This is the
lowest-risk slice: it replaces hardcoded prototype data with real persistence behind an
already-built, already-reviewed UI.

**Slice B — Real Program domain.** `programs`/`program_weeks`/`program_sessions`/
`program_session_exercises`/`program_session_planned_sets` + `workouts.originRoutineId`/
`originProgramSessionId` (v7 migration, part 2 — the `workouts` table recreate now declares
`originProgramSessionId` as `RESTRICT`, per the hardening decision; both migration parts ship
together as one version bump, not staggered), `ProgramDao`/`ProgramRepository` additions, the
snapshot-copy operation (`createSessionFromRoutine`). No new polished authoring UI in this slice —
exercised via tests and, if useful for manual verification, the same kind of minimal debug-only
screen this codebase already uses elsewhere (`DeveloperSyncProofScreen` precedent), not a product
surface.

**Slice C — ProgramSession execution.** `startProgramSession` with `originProgramSessionId`
provenance and target-snapshot seeding (same mechanism as Slice A's `startRoutine`, sourced from
`ProgramSessionPlannedSet` instead of `RoutinePlannedSet`), `getProgramProgress`/`getNextSession`
derived queries, and the `RESTRICT`-aware delete path for `ProgramSession`/`Program` (surfacing a
rejected-deletion result rather than letting a `SQLiteConstraintException` propagate uncaught —
exact UI treatment is the Open Human Decisions item above, but the repository layer should return a
typed result, mirroring `StartWorkoutResult`'s existing pattern, not a raw exception). Verified by
tests; no UI commitment yet.

**Deliberately not in this sequence**: a polished Program authoring/browsing UI (the research
document's §20 "Plan" screen candidate). That remains genuinely open UX territory and should get
its own dedicated prototype-and-evaluate pass — the same treatment already given to Routine itself
before this slice — rather than being built simultaneously with new backend work.

Each slice ships with its own migration test and repository/DAO test coverage per the Test Strategy
above — not deferred to the end.

---

## Files Changed

Only this document. No Kotlin entity, DAO, repository, Room version bump, migration, Supabase
schema, Web UI, or Android UI change was made to produce it.
