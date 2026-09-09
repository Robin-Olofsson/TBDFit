# TBDFit First Strength-Workout Slice — Design Proposal

**STATUS: DESIGN PROPOSAL — FOR TEAM REVIEW**

**This is not an ADR and not a product decision.** It is a design proposal synthesizing
[`docs/product/competitor-benchmark-hevy-strong-fitbod.md`](../product/competitor-benchmark-hevy-strong-fitbod.md)
with TBDFit's existing architecture/product principles, for the two human developers to review
before any implementation begins. It does not promote, accept, or modify PD-001, PD-002, PD-003, or
any ADR. No code, schema, migration, Wear code, auth code, or sensor code has been touched to
produce this document.

Labels used throughout: **FACT** (verified against current code/docs), **INFERENCE** (reasoned
conclusion from facts), **RECOMMENDATION** (design judgment, not a decision), **OPEN DECISION**
(requires developer approval).

Per the competitor benchmark's own evidence discipline, this document keeps three things visibly
separate wherever competitor research is cited: **vendor/product fact** (what a competitor actually
ships, documented), **secondary user-feedback finding** (directional, not representative — see the
benchmark's Evidence Quality section), and **TBDFit architectural inference** (a conclusion this
document draws, not a claim about how any competitor is built internally).

---

## Starting premise (FACT + INFERENCE)

**FACT**: the existing `LocalRecordEntity`/`LocalRecordDao` infrastructure (Phone and Wear) is
explicitly documented in its own code comments as disposable technical-proof data, immutable/
create-once, synced via upsert-with-`ignoreDuplicates`, and the Phone/Wear Room databases both use
`fallbackToDestructiveMigration` with `exportSchema = false` for exactly that reason. `docs/product/
decisions.md`'s PD-002 verification section already names the precondition this design must
satisfy: *"destructive migration must stop being acceptable once real durable workout data is
introduced."*

**INFERENCE**: a real workout — mutable during execution, incrementally persisted, process-death
sensitive, eventually executable on Phone or Watch, eventually replicated, and expected to survive
app upgrades as real user history — cannot be shaped around `LocalRecordEntity`'s create-once/
disposable/destructive-migration model without violating that precondition. This design proposes an
independent set of entities and an independent migration policy, coexisting with (not replacing)
the existing technical-proof tables in the same Phone database (see PERSISTENCE/MIGRATION POLICY
below).

---

## RECOMMENDED FIRST STRENGTH SLICE

The smallest credible real strength-workout product slice, matching the goal exactly:

```
Start workout → add exercise → record sets → edit while training
→ complete workout → persist durably → view completed workout
```

No routines/templates, no cloud sync, no Wear execution, no rest timer, no recommendations, no
physiological data. Previous-performance display is evaluated below — its semantics are now fully
resolved and cheap, and this document leans toward including it, but whether it ships in this exact
slice or the one immediately after remains an explicit human decision (see HUMAN DECISIONS
REQUIRED), not something assumed either way here.

This is deliberately **not** the union of Hevy/Strong/Fitbod's feature sets — it is the smallest
slice that produces TBDFit's first durable, non-disposable workout-domain data while remaining
honest about what it does not yet solve (sync, watch, corrections' downstream consequences).

---

## USER FLOW

1. **Workout history / start** — list of past completed workouts (empty on first use) plus a
   prominent "Start Workout" action. If a workout is already `ACTIVE` (see lifecycle below),
   this screen routes straight into it instead of offering to start a new one.
2. **Active workout** — the exercise list for the current session; add-exercise entry point; per-
   exercise set rows (weight, reps, complete toggle); add/remove/reorder exercises; add/edit/
   delete sets; "Complete workout" action.
3. **Exercise picker / creation** — search the seeded catalog + user's own prior custom exercises;
   "create new exercise" as a fallback when nothing matches, not a separate feature.
4. **Completed workout detail** — read view of a finished workout; edit entry point if completed-
   workout editing is approved (see HUMAN DECISIONS REQUIRED).

Four screens/states — matching the instruction's own "likely candidates" almost exactly, and no
larger. Minimizing taps during active training (the strongest, if only secondary-synthesis-level,
competitor pattern found) is a UI-implementation concern for whoever builds screen 2, not something
this data-model-level design needs to solve architecturally beyond not getting in its way (e.g., no
extra confirmation dialog required to log a set — see DURABILITY MODEL for why that's safe).

---

## CONCEPTUAL DATA MODEL

### Exercise identity (OPEN DECISION #1 — see HUMAN DECISIONS REQUIRED)

Four options were compared, per the benchmark's own duplicate-identity evidence (Hevy maintains a
dedicated official help article for cleaning up duplicated exercises/routines — **FACT**, Layer 1
of the benchmark, not sentiment-derived):

| Option | Verdict |
|---|---|
| **A. Free-text only** | Rejected. No stable identity means no reliable previous-performance query, guaranteed duplicate/variant fragmentation (Hevy's exact documented problem), and no analytics path without later renormalization. |
| **B. Built-in catalog only** | Rejected. Blocks logging anything not in a fixed list — fails on real gym use immediately (machines/variants vary by gym). Also requires TBDFit to build and maintain a catalog now, which the benchmark explicitly warns against ("do not build a huge catalog merely because competitors have one"). |
| **C. Persistent custom entity only (no seed catalog)** | Rejected. Empty on first run (user must create "Bench Press" themselves), and forgoes a good first-run experience for no real savings — the seeding cost is trivial either way. |
| **D. Hybrid — one persistent `Exercise` entity, small seeded catalog + user-created rows in the same table** | **RECOMMENDATION.** |

**RECOMMENDATION**: Option D. A single `Exercise` table, seeded at first run with a small
(dozens, not hundreds) set of common lifts flagged `source = BUILT_IN`, with user-created exercises
inserted into the exact same table (`source = CUSTOM`) using the exact same identity system.

#### Correction from the hardening pass: the username-normalization analogy does not transfer

The original version of this document justified `Exercise.normalizedName` by direct analogy to
`public.profiles.normalized_username`. **That analogy does not hold, on two separate grounds, and
is retracted:**

1. **Semantic mismatch.** Username normalization exists because `Robin`/`robin`/`ROBIN` must
   collapse to *the same identity* — case carries no meaning for a username. Exercise names do not
   work this way: "Bench Press," "Barbell Bench Press," "Smith Machine Bench Press," "Incline Bench
   Press," and a user's own "Custom Bench Press" are not case-variants of one identity — they may
   legitimately be five different exercises. A case-insensitive uniqueness constraint would be
   *wrong* here, not merely unnecessary: it would either block legitimate distinct exercises that
   happen to normalize similarly, or fail to distinguish exercises that should be distinguished
   (equipment variants are a naming problem, not a case-folding problem).
2. **Mechanical mismatch.** `normalized_username` is a Postgres `GENERATED ALWAYS AS … STORED`
   column on a Supabase-hosted table. `Exercise` lives in the Phone's local Room/SQLite database —
   Room has no equivalent first-class generated-column feature the same way, so "reuse the same
   mechanism" was never literally accurate either.

**Corrected principle, adopted as the actual foundation for this section:**

> **Exercise identity is the stable Exercise `id`, not its display name.**

This resolves every concern the hardening instruction raised, without a uniqueness constraint on
name at all:

- **Renaming** — a plain `UPDATE` on `name`; every `WorkoutExercise` row still references the same
  `id`, so no history, no previous-performance query, and no analytics are affected.
- **Custom exercises** — a user-created row with its own stable `id`; the name is a label, not the
  identity.
- **Built-in exercises** — same table, same rule; a seed-catalog copy fix is a name update, not a
  data migration (see Seed Catalog Lifecycle below for how the `id` itself stays stable).
- **Similar exercises / equipment variants** ("Bench Press" vs. "Incline Bench Press" vs. "Smith
  Machine Bench Press") — these are simply different `Exercise` rows with different `id`s. This is
  a content/taxonomy question (out of scope — "do not build a huge catalog"), not an identity
  problem at all.
- **Future localization** — because identity is the `id`, a future localized display name (or a
  separate name-by-locale table) can be added without ever touching identity, previous-performance
  queries, or historical references. A pattern that had `normalizedName` double as identity would
  have made this materially harder.
- **Previous-performance lookup / analytics continuity** — both must query by `exerciseId`, never
  by name string-matching. Stated here as an explicit rule for whoever implements them.
- **Future cloud replication** — an `Exercise`'s `id` must be stable and consistent across devices
  (see Seed Catalog Lifecycle); its `name` can even be edited independently per device later without
  breaking replication identity — a separate, harder question (name-conflict-on-sync) that is
  explicitly not designed here, since cloud sync is deferred.

**So what is `normalizedName` actually for, if not uniqueness?** Two narrower, non-blocking roles —
neither a database-level constraint:

- **A search key** — case/accent-insensitive matching in the exercise picker.
- **A duplicate-*warning* hint** — at custom-exercise creation time only, scoped to that user's own
  custom exercises, surfaced as a dismissible prompt ("You already have an exercise named 'Bench
  Press' — use it instead?") rather than a hard block. This is the actual, minimal fix for Hevy's
  documented problem: it catches the accidental-duplicate case (a user re-creating the same exercise
  by typo or forgetting one already exists) without ever preventing two legitimately different
  exercises that happen to share or nearly share a name from coexisting.

Concretely: `Exercise.normalizedName` is a plain app-computed `String` field (lower-cased at
write time by application code, not a database-generated column — there is no Postgres-style
generated-column mechanism being reused here), with no `UNIQUE` constraint. "Alias" (e.g., letting
"Barbell Bench Press" also match a search for "Bench Press") is a plausible future search-quality
feature built on the same field, explicitly **not** part of this slice.

**Provenance/origin without overengineering**: `Exercise.source ∈ {BUILT_IN, CUSTOM}` is
**descriptive metadata only** — it exists so the exercise picker can group/label results ("My
Exercises" vs. a seeded list) and so a future seed-catalog-update migration knows which rows it is
allowed to touch. No behavioral logic (identity, previous-performance, analytics, or replication)
should ever branch on `source` beyond that. Built-in and custom exercises are, deliberately, the
same kind of row in every way that matters to the rest of the model.

**Migration-pain check** (per the instruction not to create obvious pain): renaming any exercise
(built-in or custom) is a plain `UPDATE` (display-only, no versioning needed for v1); growing the
seed catalog later is just inserting more `BUILT_IN` rows, not a schema change; adding equipment/
category tagging later is an additive nullable column, not a redesign; deleting an exercise is
deliberately **not built** in this slice — sidesteps "what happens to history when an exercise
disappears" entirely rather than half-solving it.

### Seed catalog lifecycle

The hybrid model above is only safe if built-in identity is genuinely stable. Concretely:

- **Stable built-in IDs**: built-in `Exercise` rows use fixed, hand-assigned string slugs (e.g.,
  `"builtin_bench_press"`, `"builtin_back_squat"`), assigned once, in a namespace visually and
  structurally distinct from the UUID format used for everything else. A slug is never reassigned
  or reused, even if the exercise is renamed, recategorized, or the seed list is reorganized later —
  the slug *is* the identity; `name` is just an attribute of it.
- **Adding catalog entries later**: a future migration inserts new `BUILT_IN` rows with new,
  never-before-used slugs. This touches nothing existing — no risk to any historical
  `WorkoutExercise`/`WorkoutSet` row, since none of them reference the new slugs.
- **Renaming a built-in entry**: a plain `UPDATE name` on the existing slug's row. The `id` is
  untouched, so every historical reference through every past workout remains valid and correct
  automatically.
- **User-created exercise IDs**: standard random UUIDs, as used everywhere else in this codebase.
  Because built-in IDs are always slugs in a distinct, reserved namespace (e.g., always prefixed
  `builtin_`) and UUIDs are never generated to look like that namespace, **collision between a
  built-in ID and a custom ID is impossible by construction** — no runtime collision check is
  needed.
- **Collisions between custom and built-in *names*** (not IDs) are expected and harmless at the
  data level — see Failure Timeline D below for the specific case of a catalog update introducing a
  built-in exercise that happens to share a display name with an existing custom exercise.

This is deliberately not a catalog-design exercise — the point is only that the hybrid model's
lifecycle is safe, not what the seed list should actually contain.

### Minimum set model

Core `WorkoutSet` fields, evaluated against the instruction's own list:

- **stable ID** — client-generated UUID (`String`), consistent with `LocalRecordEntity`'s existing
  pattern in this codebase and necessary for future offline creation + idempotent replication.
- **order** — an explicit integer `position` column, not insertion order (insertion order is not
  guaranteed to survive reordering or later replication).
- **reps** (`Int?`) and **load/weight** (`Double?`) — both nullable, since a bodyweight-only set may
  have no logged weight.
- **completion state** — `isCompleted: Boolean` + `completedAt: Long?`.

**Advanced fields — evaluated individually, not adopted as a bundle:**

| Field | Verdict | Reasoning |
|---|---|---|
| Warm-up vs. working set (`setType` enum) | **DEFER** | **Clarified by this pass**: not introduced for this slice, including alongside previous-performance. Warm-up/drop/failure set types are intentionally outside the first slice's scope entirely (see the same DEFER verdicts below) — a set-type abstraction must not be introduced solely to support previous-performance filtering. See PREVIOUS PERFORMANCE below for what this means for that feature's first-slice semantics. |
| Drop sets | **DEFER** | Requires modeling a linked/nested set relationship — real complexity, no first-slice need. |
| Failure sets | **DEFER** | A boolean flag, trivially addable later; no first-slice need. |
| RPE / RIR | **DEFER** | Nullable numeric columns, trivially addable later; no first-slice need. |
| Duration / distance sets | **DEFER**, but see structural note below. |
| Notes | **DEFER** | Trivial nullable column later; not required to prove the core loop. |

**Honesty check from the hardening pass, stated plainly**: `WorkoutSet` as designed here **is a
repetition/load strength-set model for this slice — it is not intended, and must not be read, as a
universal model for all future exercise types.** `reps` + `load` + `position` + completion state is
sufficient and correct for strength logging; it is not trying to also be correct for cardio by
being vague about it.

No generic `Metric`/`Measurement`/`SetValue`/arbitrary-typed-value abstraction is introduced to
hedge against a hypothetical future — per the instruction, that would be exactly the kind of
speculative generality this design avoids. **If/when PD-003 is eventually resolved toward including
a duration/distance-based (cardio) domain, the correct extension is a new, separate table** — e.g.
a future cardio-segment table, shaped for its own domain, referencing `WorkoutExercise` (or
whatever cardio's own grouping construct turns out to be) the same way `WorkoutSet` does now — not
new nullable columns bolted onto `WorkoutSet`, and not a retrofit of `WorkoutSet` into something
generic. This is purely additive: it invalidates none of `WorkoutSet`'s existing strength history,
requires no migration of existing rows, and requires no change to `Exercise` or `Workout` at all
(neither of which are reps/weight-specific to begin with). Stated now only so the extension seam is
known and honest — no cardio design work is done here, matching PD-003's own instruction not to
design a "universal workout model" prematurely.

### Persistence model

```
Exercise         Workout                WorkoutExercise         WorkoutSet
---------        --------               ---------------         ----------
id (PK)          id (PK)                id (PK)                 id (PK)
name             status (ACTIVE/        workoutId (FK)          workoutExerciseId (FK)
normalizedName    COMPLETED)            exerciseId (FK)         position
source           startedAt              position                weight?
createdAt        completedAt                                    reps?
                 lastModifiedAt?                                 isCompleted
                 notes? (later)                                  completedAt?
                 createdAt
```

- **IDs**: client-generated UUIDs everywhere except `Exercise`'s built-in seed rows, which use
  stable, hand-assigned slugs (not random UUIDs) so seeded data means the same thing across every
  install/device — important once Wear execution exists and must agree on exercise identity without
  a network round-trip.
- **Foreign keys**: `WorkoutExercise.workoutId → Workout.id` (cascade — deleting/discarding a
  workout's exercises is fine); `WorkoutExercise.exerciseId → Exercise.id` (restrict — exercises are
  never deleted in this slice, so this never fires); `WorkoutSet.workoutExerciseId →
  WorkoutExercise.id` (cascade).
- **Deterministic order**: explicit `position` integers on `WorkoutExercise` and `WorkoutSet`, not
  inferred from timestamps.
- **Transactions**: multi-row mutations (reordering exercises/sets, completing a workout) wrapped in
  a single `@Transaction` DAO method, so a crash mid-reorder cannot leave partial ordering.
- **Timestamps**: `createdAt` throughout; `Workout.completedAt` (when the session actually ended —
  never changes once set) kept explicitly distinct from `Workout.lastModifiedAt` (when the record
  was last corrected after the fact — see COMPLETED-WORKOUT EDITING for why these must not be the
  same field).
- **Active/completed state**: `Workout.status ∈ {ACTIVE, COMPLETED}` — **revised by the hardening
  pass**; `DISCARDED` is no longer a persisted status (see ACTIVE-WORKOUT STATE MODEL below).
- **Delete behavior**: discarding an active workout is a **transactional row deletion**, not a
  status transition — **revised by the hardening pass**, see ACTIVE-WORKOUT STATE MODEL.

**Credible alternatives considered and rejected**:
- *A single denormalized "workout blob" row* (serialized JSON per workout) — rejected: breaks the
  durability invariant's granularity (any single-field edit rewrites the whole blob, a larger
  partial-write risk window), fights SQL's native querying for "last time for exercise X," and
  doesn't naturally support explicit ordering/FKs.
- *Separate tables per set-type* (e.g., `WeightRepsSet`, `DurationSet` now) — rejected as premature
  given PD-003 explicitly defers the cardio-scope decision; a single `WorkoutSet` table with
  nullable columns is the smaller sufficient structure for strength-only v1.

---

## ACTIVE-WORKOUT STATE MODEL

States: **(none active)** → **ACTIVE** → **COMPLETED**. Discard is **not** a third persisted state
(revised by the hardening pass — see below).

### Discard, re-examined

The original version of this document resolved discard as a soft `DISCARDED` status "to not lose
data." That reasoning does not survive scrutiny and is retracted:

- **Does the user expect discarded workouts to appear in history?** No. A discarded workout is, by
  the user's own explicit statement, something that didn't happen. Showing it anywhere in workout
  history is noise, not value — no competitor evidence in the benchmark suggests otherwise, and
  ordinary product expectation runs the other way.
- **Was soft-discard actually motivated by a present requirement, or by anticipating future sync
  tombstones?** The latter. That is future-replication concern leaking into the current local
  domain model — exactly the pattern this hardening pass exists to catch. If/when cloud sync of
  workouts is eventually designed, tombstone/deletion-event handling belongs to *that* design, at
  that time, and can be added without having pre-built a fake retained row today to support it.
- **Does local-first durability require retaining something the user explicitly told us to
  discard?** No. Durability protects against *involuntary* loss (a crash, a killed process, a
  network outage) — it is not an argument against honoring the user's own *voluntary*, explicit
  deletion request. These are different concerns, and conflating them was the actual error.

**Revised RECOMMENDATION**: discarding an `ACTIVE` workout **transactionally deletes** the
`Workout` row (cascading to its `WorkoutExercise`/`WorkoutSet` children via the foreign keys already
defined above) — Option B, not Option A. `Workout.status` is therefore a two-value enum
(`ACTIVE`/`COMPLETED`); there is no `DISCARDED` value to represent, filter around, or later have to
explain to a future contributor. The deletion is still awaited before the UI navigates away, for the
same reason every other mutation in this model is awaited (see DURABILITY MODEL).

### Single active workout per device

**Exactly one `ACTIVE` `Workout` row may exist in this local database at a time.** This is a **FIRST
IMPLEMENTATION LIMIT**, not a permanent product invariant — PD-001 already commits to independent
watch execution, meaning a future watch and phone are separate execution authorities that may each
legitimately hold their own active workout simultaneously. What this slice commits to is narrower
and local only: *this one Room database* cannot hold two concurrent `ACTIVE` rows. This is a
statement about local data, not about cross-device authority — **Phone remains not permanently
authoritative**, and nothing here decides how a future watch and phone reconcile two simultaneously
active sessions; that is explicitly deferred (see FUTURE WATCH/SYNC IMPLICATIONS).

Making this concrete, since correctness must not rely on UI behavior alone — **and, clarified by
this pass, must not depend on one particular repository/coordinator instance either**, since nothing
in Android's process/lifecycle model guarantees only one such instance is ever alive (e.g., a second
`ViewModel`/coordinator instance created during a configuration change or a rare double-init, or a
future entry point this document hasn't anticipated):

- **The check-then-insert must be one atomic database operation, not two app-level steps.** "Check
  whether an `ACTIVE` workout exists, then insert a new one if not" is only actually safe if that
  read-then-write happens as a single atomic transaction at the database layer — an in-memory guard,
  a mutex held by one coordinator object, or a sequence of two separate DAO calls from application
  code are all forms of app-level serialization that only hold correctness *within* one process/
  instance, not against a second one racing it. This document intentionally does not design the
  concrete Room implementation of that transaction here (e.g., the exact DAO method shape) — only
  the requirement that it must be atomic at the database level, evaluated at implementation time.
- **Whether a SQLite/database-level constraint is warranted as defense-in-depth is an
  implementation-time evaluation, not a decision made here.** A partial unique index (or equivalent)
  enforcing "at most one `ACTIVE` row" would make the invariant hold even against a bug in the
  transaction logic above, independent of any particular calling code path. This document does not
  specify that mechanism now — it states that the atomic-transaction requirement above is the
  minimum bar, and that whoever implements this should explicitly evaluate a DB-level constraint
  against that bar rather than skip the question.
- **The restore query's ordering is recovery behavior, not invariant enforcement — these are two
  different things and must not be conflated.** `SELECT * FROM Workout WHERE status = 'ACTIVE'
  ORDER BY startedAt DESC LIMIT 1` describes what the app does *if* it is ever asked to resume with
  more than one `ACTIVE` row present (deterministically resume the most recently started one, rather
  than an unspecified/arbitrary one) — it is a defensive, deterministic response to an unexpected
  state, not a mechanism that prevents that state from occurring. Preventing it is the job of the
  atomic transaction (and, optionally, the DB-level constraint) described above; this query's only
  job is to behave sensibly if prevention ever fails.

### Process death / restore

On launch, the same query above — "is there an `ACTIVE` `Workout` row?" — is the entire recovery
mechanism. No separate "recovery" code path exists: this is the same query used for ordinary
navigation (deciding whether to show the start screen or route into an active session), so
process-death recovery and simple app-backgrounding recovery share one mechanism rather than two.

---

## PRODUCT INVARIANTS

Distinguishing invariant from first-implementation limit, as instructed:

- **PRODUCT INVARIANT**: an active or completed workout must not be lost because of process death,
  app crash, missing network, or backend unavailability (this restates PD-002's already-accepted
  consequences for the workout domain specifically).
- **PRODUCT INVARIANT**: sync failure must never invalidate a completed local workout (already
  accepted under PD-001/PD-002).
- **PRODUCT INVARIANT**: retrying an operation must not create a duplicate logical workout (already
  accepted under PD-001; satisfied here by client-generated UUIDs as primary keys, the same
  mechanism already proven for `LocalRecordEntity`/`WearReplicaEntity`).
- **FIRST IMPLEMENTATION LIMIT**: exactly one active workout per device/app instance. Explicitly
  not extended into a cross-device account-wide invariant — doing so would contradict PD-001.
- **FIRST IMPLEMENTATION LIMIT**: no Phone-as-permanent-owner assumption is encoded anywhere in this
  model — `Workout` carries no concept of "owning device," only local existence. Cross-device
  ownership/authority is explicitly deferred to the future sync/replication design (see FUTURE
  WATCH/SYNC IMPLICATIONS).

---

## DURABILITY MODEL

**Candidate invariant (accepted as correct — RECOMMENDATION)**: *every meaningful mutation to an
active workout becomes locally durable before the UI treats it as committed.* This is not a new
pattern for this codebase — it is exactly what `LocalRecordDao.insert` already does (an awaited
suspend call before the UI reflects success) — applied here to a richer entity set.

| Action | Durability treatment |
|---|---|
| Start workout | `INSERT Workout(status=ACTIVE)`, awaited before navigating into the active screen |
| Add exercise | `INSERT WorkoutExercise` |
| Reorder exercise(s) | `@Transaction` bulk `UPDATE position` |
| Add set | `INSERT WorkoutSet` |
| Change reps/weight | `UPDATE` — only on explicit confirm, not per keystroke (an in-progress, unconfirmed keystroke is not yet "data") |
| Complete set | `UPDATE isCompleted, completedAt` |
| Delete set | `DELETE` (a set that was never completed was never "real" history; deleting a completed set is still a purely local, pre-completion-of-workout operation) |
| Complete workout | `@Transaction` `UPDATE status=COMPLETED, completedAt=now` |
| Discard workout | `@Transaction` `DELETE Workout` (cascades to children) — awaited before the UI navigates away, same as every other action here (revised by the hardening pass; see ACTIVE-WORKOUT STATE MODEL) |
| Edit a completed workout | `UPDATE` the corrected field(s) + `Workout.lastModifiedAt=now`; `completedAt` and `status` are never touched by this action (see COMPLETED-WORKOUT EDITING) |

**Backend sync is explicitly not durability** — none of the above involves Supabase. A workout is
fully "real" the moment it is committed to local Room, regardless of connectivity (Timeline C
below).

---

## PERSISTENCE/MIGRATION POLICY

**FACT (current state)**: Phone `AppDatabase` is version 3, `exportSchema = false`,
`fallbackToDestructiveMigration(dropAllTables = true)`, explicitly justified in-code by the
disposable nature of `LocalRecordEntity`/`WearReplicaEntity`.

**RECOMMENDATION — the exact policy change required**, addressing the instruction's explicit
question of coexistence vs. a separate database:

1. **Coexist in the same Phone database** (reject a second database). A separate
   `tbdfit-phone-workouts.db` alongside the existing one would isolate blast radius but adds a
   second `AppDatabase` class, a second connection, and duplicated migration/testing machinery for
   no benefit proportionate to the cost — `LocalRecordEntity`'s own schema is stable and rarely
   touched. This matches the instruction to avoid unnecessary database proliferation.
2. **Turn on `exportSchema = true`** the moment `Workout`/`WorkoutExercise`/`WorkoutSet`/`Exercise`
   are added, and commit the generated schema JSON under `android/phone/schemas/` — this is what
   makes real migrations reviewable and testable at all; it is currently impossible with
   `exportSchema = false`.
3. **Remove the blanket `fallbackToDestructiveMigration`.** Every future version bump — even one
   that only touches `LocalRecordEntity` — must ship an explicit `Migration` object from this point
   forward. This is the direct, load-bearing consequence of introducing real durable data into a
   database that also holds disposable data: Room's destructive-fallback flag is database-scoped,
   not table-scoped, so it cannot be kept "on" for the old tables and "off" for the new ones
   automatically.
4. **A migration touching only the disposable technical-proof tables may still have a trivial/lossy
   body** (e.g., `DROP TABLE`/`CREATE TABLE` inside an explicit, reviewed `Migration`) — this
   preserves the *intent* of "disposable data stays disposable" without relying on Room's blanket
   mechanism, which is the actual thing that must stop being acceptable per PD-002's own recorded
   precondition. A migration touching `Workout`/`WorkoutExercise`/`WorkoutSet`/`Exercise` must
   actually preserve existing rows.
5. **Add a Room migration test** (`MigrationTestHelper`) verifying at least the specific transition
   this slice introduces: old schema (v3, `LocalRecordEntity`/`WearReplicaEntity` only) → new schema
   (workout tables added) preserves existing `LocalRecordEntity` rows and creates the new tables
   correctly empty.

**Required precondition, made explicit by the hardening pass, before the first workout schema
ships**: once `fallbackToDestructiveMigration` is removed, Room's behavior on a *missing* migration
path changes from "silently wipe the database" to **a hard runtime crash** ("a migration … was
required but not found"). This is the correct, load-bearing property the instruction asks for —
**a missing migration must never be allowed to destroy the whole database**, and removing the
fallback is exactly what converts that failure mode from silent data loss into a loud, blocking,
pre-release-catchable crash instead. This must be true before the first workout table ships, not
retrofitted after.

The second half of that precondition is a review discipline, not a schema feature: **every
`Migration` object must be considered table-by-table, not written as one blanket body.** A
migration's SQL body may legitimately `DROP TABLE`/`CREATE TABLE` for `LocalRecordEntity`/
`WearReplicaEntity` specifically (the disposable tables), while the *same* migration must use
data-preserving `ALTER TABLE`/copy-based SQL for anything touching `Workout`/`WorkoutExercise`/
`WorkoutSet`/`Exercise`. The migration test recommended above should specifically assert that
pre-existing durable-table rows survive a version bump that also happens to touch a disposable
table — this is precisely the scenario a careless "just drop everything" migration would get wrong,
and the test exists to catch that mistake before it ships, not to prove the happy path alone.

This same policy should extend to Wear's own database whenever Wear workout execution is eventually
built — noted here for continuity, not acted on now (see next section).

---

## COMPETITOR LESSONS APPLIED

Classified per the instruction's own vocabulary — deliberately not the union of competitor
features:

| Pattern | Classification | Basis |
|---|---|---|
| Empty workout (0 exercises, transiently) | SUPPORT ARCHITECTURALLY | Allowed as a transient state; no special validation built now |
| Routine/template vs. executed workout | DEFER the feature; SUPPORT ARCHITECTURALLY the boundary | See PLAN VS EXECUTION below — `Workout` stays self-contained |
| Persistent exercise identity | **ADOPT EARLY** | Hevy's duplicate-cleanup doc is FACT-grounded, cheap now, costly later |
| Custom exercises | **ADOPT EARLY** (minimal, same table as built-ins) | Real gym use requires it; no separate feature needed |
| Weight/reps entry | **ADOPT EARLY** | The core mechanic; the goal cannot be met without it |
| Set completion | **ADOPT EARLY** (data model only — UI polish is implementation's job) | Required for the core loop |
| Editing during execution | **ADOPT EARLY** | Explicitly required by the goal's own flow |
| Completed-workout corrections | **ADOPT EARLY** (pending OPEN DECISION #2) | See COMPLETED-WORKOUT EDITING below |
| Previous performance | Leans **ADOPT EARLY**, human-decided (OPEN DECISION #3) | Competitor pattern is FACT-grounded (Hevy, Strong both ship it); direct user-preference evidence is weak per the benchmark's own hygiene pass; semantics are now fully specified and cheap (see below) — remaining open only as a scope-priority call |
| Rest timers | **DEFER (later)**, resolved | Weak/insufficient competitor-feedback evidence; fully decoupled from the historical model; zero migration cost to add later |
| Watch execution | **DEFER (explicitly out of scope this slice)**; SUPPORT ARCHITECTURALLY | Schema choices (UUIDs, `position`, `completedAt`/`lastModifiedAt`) already avoid foreclosing it |
| Recommendation / automation | **DO NOT COPY** | Fitbod's own evidence shows trust requires fast-override infrastructure that is itself nontrivial product work; TBDFit's differentiation hypothesis does not depend on it |

### Plan vs. execution

```
PLAN / INTENT  →  EXECUTION  →  RECORDED RESULT
```

**RECOMMENDATION**: this slice does not implement a Routine/template entity at all. What it
preserves structurally, so a future routine feature cannot become the historical record by
accident, is that `Workout` is fully self-contained (its own `WorkoutExercise`/`WorkoutSet` rows) —
a future `Routine` would only ever *seed* a new `Workout`'s initial exercise list, optionally
leaving a nullable, informational `sourceRoutineId` reference on `Workout` for provenance, never a
live dependency the completed record's meaning relies on. This directly reflects Strong's own
company-acknowledged confusion (**FACT**, benchmark Layer 1) about whether editing during a session
changes the saved template — by never coupling `Workout` to a live `Routine` reference, that
specific ambiguity has no way to arise in this slice, deferred cleanly rather than half-built.

### Exercise identity, minimum set model, persistence, migration policy, active-workout lifecycle

See CONCEPTUAL DATA MODEL, ACTIVE-WORKOUT STATE MODEL, and PERSISTENCE/MIGRATION POLICY above.

### Completed-workout editing

**RECOMMENDATION (OPEN DECISION #2)**: completed workout = **editable history**, not immutable.

Product-semantics reasoning, not synchronization convenience: mis-entered weight/reps during a real
gym session (heavy plates, fatigue, wrong exercise selected) is an ordinary occurrence, not an edge
case — blocking correction would make the product feel broken for its actual purpose. All three
benchmarked competitors support some form of post-hoc correction; Fitbod's own official
documentation goes further and explicitly names the consequence (editing a past set can retroactively
move derived Strength Scores) — that is FACT-grounded evidence of a real, manageable coupling, not
evidence against allowing edits.

**Scope for this slice**: editing corrects existing values (weight, reps, exercise, set order) on a
`COMPLETED` workout. Reopening a completed workout back into `ACTIVE` execution is a different,
riskier operation and is not part of this recommendation — out of scope, not decided either way.

**Precise semantics, made explicit by the hardening pass** — two timestamps with different
meanings must not be conflated:

- **`completedAt`** — *when the training session actually ended.* Set once, when the workout
  transitions from `ACTIVE` to `COMPLETED`. **A historical edit never changes `completedAt`,
  under any circumstance.** A correction made a week later does not retroactively move when the
  workout is considered to have happened.
- **`lastModifiedAt`** — *when the recorded history was last corrected.* Bumped by any edit to a
  completed workout's data. Absent (or equal to `completedAt`) until the first correction is ever
  made.

Consequences, walked through directly:

- A historical edit **does not reopen the workout** — `status` remains `COMPLETED` throughout;
  there is no transition through `ACTIVE`. Editing history is a distinct "correct the record"
  operation, not "resume execution."
- A historical edit **modifies only** the corrected field(s) plus `lastModifiedAt` — `status` and
  `completedAt` are untouched.
- A historical edit **must not affect duration** (if a duration display is ever computed as
  `completedAt − startedAt`) — correcting a set's weight or reps must never implicitly alter
  `startedAt` or `completedAt`.
- A historical edit **does, correctly, affect previous-performance queries** — since a
  previous-performance lookup reads live `WorkoutSet` data (see PREVIOUS PERFORMANCE below), a
  corrected value is exactly what should surface the next time someone looks it up. This is a
  deliberate, desirable consequence of querying live state rather than a snapshot, not an
  overlooked side effect — nobody wants "previous performance" to keep showing a value they know
  was a typo.

**Future sync consequence — explicitly flagged, not solved here**: the existing
`LocalRecordSyncCoordinator`/`SupabaseLocalRecordRemoteStore` pattern is built and tested around
create-once/immutable/upsert-with-`ignoreDuplicates` semantics; its own code comment explicitly
states this must not be generalized to mutable entities. Editable workout history means any future
cloud-sync design for workouts needs real update-conflict semantics (e.g., `lastModifiedAt`-based
last-write-wins, or something stricter) — a genuinely different remote-write shape than anything
currently built. This is not designed here (cloud sync is explicitly deferred, see below); it is
named now so a future sync ADR does not accidentally inherit the wrong pattern by default.

### Previous performance

**Precise candidate semantics, clarified by this pass:**

> Previous performance is derived from the most recent **`COMPLETED`** workout containing the same
> stable `exerciseId`.

**No set-type filtering in this slice.** The earlier version of this document conditioned this
query on "`WORKING`-type sets only," implying a `setType` distinction that does not exist in the
first slice (warm-up/drop/failure sets are all `DEFER`, per Minimum Set Model above) — a set-type
abstraction must not be introduced solely to make this feature technically correct. For this slice,
**every completed, persisted `WorkoutSet` is an ordinary strength set and is eligible to
participate in previous-performance display**, with no filtering by kind. If explicit set types
(warm-up, drop, failure, …) are introduced in a later capability, previous-performance's filtering
semantics can be revisited at that time — not designed preemptively now.

Checked against each concern the hardening instruction raised:

- **Current active workout excluded** — yes: the query filters `Workout.status = 'COMPLETED'`
  only. *Known, accepted limitation*: if the same exercise is logged twice within one still-`ACTIVE`
  session (e.g., as a finisher), this query will not show the earlier set from *this* session — it
  shows the last *completed* workout's data instead. Cheap to improve later (a same-session lookup
  is a small additive query); not required for correctness now, and not silently hidden here.
- **Discarded workouts excluded** — now automatic, and simpler than originally designed: because
  discard is a hard deletion (see ACTIVE-WORKOUT STATE MODEL, revised above), a discarded workout no
  longer exists as a row at all, so no `status` filter for it is needed anywhere, including here.
  This is a direct, positive consequence of the discard revision.
- **Historical corrections reflected naturally** — yes, and desirable (see COMPLETED-WORKOUT
  EDITING above): the query reads live `WorkoutSet` state, not a snapshot, so a corrected value is
  exactly what surfaces.
- **Exercise rename does not break continuity** — guaranteed structurally: the query matches only
  by `exerciseId`, never by name (see EXERCISE IDENTITY above).
- **Same-name, different-`id` exercises are never conflated** — guaranteed structurally, for the
  same reason: two rows can share a display name and never be confused, because the query only ever
  matches the stable `id`.

**Cost re-assessment**: with these semantics settled, the feature is genuinely cheap — one indexed,
read-only query over existing tables, requiring no new entity and no write-path change. The one
previously-open ambiguity (same-workout vs. same-routine scoping, which Hevy makes configurable) no
longer applies: this slice has no `Routine` concept at all (see Plan vs. Execution), so "most recent
completed workout, any workout" is the only sensible scope — there is nothing left to configure.

**RECOMMENDATION (OPEN DECISION #3, narrowed by this hardening pass)**: given the semantics are now
fully specified and the implementation cost is low, this document leans toward **FIRST SLICE**
rather than the earlier "next slice" framing. It remains a human decision, not because the
architecture is unresolved, but because "how minimal should the very first release be" is itself a
scope-priority/taste call the two developers should make explicitly rather than have decided for
them by a cost argument alone. Adopting this for the first slice requires no additional schema
beyond what Minimum Set Model already defines — no `setType` column is needed to ship it correctly
for this slice's scope.

### Rest timer

**RECOMMENDATION, resolved — not escalated as a human decision**: **LATER**.

Rest-timer evidence in the benchmark is `INSUFFICIENT EVIDENCE` across all three products — no
recurring pattern, positive or negative, was found. Structurally, a rest timer is **ephemeral
execution state** (a countdown irrelevant once elapsed), not workout-result data, and should never
be persisted as part of the historical `WorkoutSet` record. Because it is fully decoupled from the
schema above, deferring it costs nothing structurally — no migration risk, no design debt — which is
why this document resolves it directly rather than adding it to the human-decision list.

---

## COMPETITOR FEATURES DELIBERATELY DEFERRED

| Feature | Why safe to defer |
|---|---|
| Recommendation engine / generated training | Fitbod's own evidence shows trust requires fast-override infrastructure that is itself nontrivial; TBDFit's differentiation hypothesis (physiological-capabilities research) does not depend on it; nothing in this schema assumes or blocks a future suggestion layer |
| Social features | No competitor evidence this is required for a credible logging product; fully orthogonal to `Workout`/`WorkoutSet`, addable later without touching this schema |
| Routines/programs | Explicitly deferred (see Plan vs. Execution); `Workout` is self-contained, so nothing here depends on a `Routine` existing |
| Advanced analytics (volume trends, muscle-group breakdowns, PR detection) | All derivable as read-time queries over existing `Workout`/`WorkoutSet` history; the schema doesn't need to anticipate them |
| Cloud workout sync | Local-first per PD-002; a completed workout is fully valid and durable without ever reaching Supabase; layered on later, mirroring the existing `LocalRecord` separation of local durability from remote sync |
| Wear workout execution | Explicitly out of scope for this slice; schema choices already avoid foreclosing it (see below) |
| Physiological metrics | Zero coupling — a workout is valid with none, per the physiological research doc's own "abstract product meaning, not hardware reality" constraint |
| Supersets | **Not fully free later** — the current linear `position` model doesn't represent exercise grouping; a future addition would need a nullable `supersetGroupId`-style column. Flagged explicitly as the one deferred item with a (small, additive) future schema cost, rather than implied to be entirely free |
| Progression algorithms | Same bucket as recommendation engines — a read-time computed suggestion over existing history, no schema dependency |

---

## FUTURE WATCH/SYNC IMPLICATIONS

No Wear code is implemented in this slice. The long-term direction — a watch eventually capable of
independently executing and durably retaining a workout — is preserved, not built:

- Client-generated UUIDs on every entity (already this codebase's proven pattern for
  `LocalRecordEntity`/`WearReplicaEntity`) are chosen specifically so a future watch-originated
  `Workout` can be created offline and later reconciled without ID collision or re-minting.
- Explicit `position` columns and the `completedAt`/`lastModifiedAt` timestamp split are chosen
  because any future replication and (per the completed-workout-editing recommendation)
  conflict-resolution design will need them — not because this slice implements replication.
- Per the benchmark's own corrected lesson: **watch capability and watch reliability are separate
  product qualities.** This design does not treat "the schema supports it" as evidence that
  replication would be reliable. A future architecture must still separately account for local
  durability (this slice establishes the pattern, Phone-side only), one execution authority per
  device (this slice's single-active-workout-per-device limit is compatible with, but does not
  itself solve, multi-device authority), idempotent replication (UUID primary keys are necessary
  but not sufficient), stale-device conflict protection (not designed here), and recovery after
  interrupted sync (not designed here).
- No synchronization protocol is designed in this document, per instruction.

---

## TEST STRATEGY

- **Pure/domain tests**: position/ordering invariants after a reorder operation; set-validity rules
  (e.g., is a bodyweight set with null weight valid); previous-performance selection logic once
  built (which workout counts as "most recent completed" for a given `exerciseId` — no set-type
  filtering exists in this slice) as a pure function over a list.
- **Room/database tests**: start workout → `ACTIVE` row created; add exercise → correct
  `WorkoutExercise` + position; add/edit/delete set → correct CRUD + position renumbering;
  completion → status transition + `completedAt`; discard → row (and cascaded children) actually
  deleted, not merely status-flagged; a concurrency test proving two simultaneous start-workout
  attempts against the same database produce exactly one `ACTIVE` row, not two (exercising the
  atomic transaction itself, not just its happy path); the restore query returns the most recently
  started row even if a test deliberately inserts more than one `ACTIVE` row directly; restore-
  active-workout after a
  simulated close/reopen of the same database file (the same Robolectric proxy pattern already used
  and explicitly documented in this codebase for `LocalRecordDaoTest`); completed-workout edit (if
  approved) updates the right row and `lastModifiedAt` while leaving `completedAt`/`status`
  untouched; a durable-rows-survive-a-disposable-table-migration test (see PERSISTENCE/MIGRATION
  POLICY); a transaction-failure test proving a mid-transaction exception during reorder leaves no
  partial state.
- **UI/application tests**: pure state-mapping functions (given `Workout.status` + presence of an
  active workout, which screen shows) — the same pattern already used for
  `SignedOutNavigationTest`/`EmailAuthNavigationTest` in this codebase, not full Compose UI tests.
- **Manual process-death tests**: an explicit on-device script entry (start workout, log several
  sets, `adb shell am force-stop com.tbdfit.app`, relaunch, confirm identical state restored),
  tracked with the same honesty this codebase already applies elsewhere — a Robolectric
  close/reopen test is an automated **proxy**, not a substitute for this real on-device check.

---

## Error and interruption timelines (walked through, informing the above)

- **A — kills process after 5 sets logged**: each set's `INSERT` was already durably committed
  before being shown as logged; on relaunch, the `ACTIVE` workout is found with all 5 sets intact.
- **B — crash immediately after editing weight**: the durability invariant means either the write
  had not yet been confirmed (change lost, but it was never shown as committed — expected) or it had
  (change persisted, safe) — no window exists where the UI believes success before Room does.
- **C — completes workout with no internet**: `status → COMPLETED` is a pure local write; the
  workout is fully valid and viewable regardless of connectivity; sync is a separate, later,
  best-effort step, exactly like the existing `LocalRecordSyncCoordinator` pattern.
- **D — leaves the app 45 minutes, returns**: no special handling needed — the same "load `ACTIVE`
  workout if one exists" query used for process-death recovery handles this identically; process-
  death recovery and ordinary backgrounding recovery are one mechanism, not two.
- **E — accidentally starts a second workout**: per the single-active-workout-per-device limit, the
  "start workout" action detects the existing `ACTIVE` row and routes back into it rather than
  creating a second concurrent one — the friendliest resolution of the two considered (versus a
  blocking warning dialog), and consistent with how every benchmarked competitor behaves.

### Hardening-pass failure timelines (pressure-testing the revisions above)

- **A — user enters "Bench Press, 100 kg × 8," process dies immediately.** Two sub-cases, resolved
  by the durability invariant's exact boundary: if the kill happens *before* the log-set action is
  confirmed, the typed-but-unconfirmed values are lost — expected, since they were never treated as
  committed. If the kill happens *after* confirmation (the `WorkoutSet` `INSERT` was awaited), the
  row exists with `weight=100, reps=8, isCompleted=true` inside the still-`ACTIVE` workout on
  relaunch, and the user resumes exactly there.
- **B — user completes the workout, then edits one historical set tomorrow.** Per COMPLETED-WORKOUT
  EDITING: `completedAt` is untouched (the session still "happened" at its original time); `status`
  remains `COMPLETED` (no reopening); the specific `WorkoutSet`'s value changes; `Workout.
  lastModifiedAt` is set to today. Any future previous-performance query for that exercise reflects
  the corrected value from this point on.
- **C — user creates a custom exercise, records several workouts with it, then renames it.**
  Because identity is the `exerciseId`, not the name (see EXERCISE IDENTITY), renaming is a plain
  `UPDATE` to `Exercise.name`. Every historical `WorkoutExercise` row still references the same
  `id`; all past workouts continue to display correctly under the new name automatically. No
  migration, no broken links, no analytics discontinuity.
- **D — a future catalog update introduces a built-in exercise sharing a display name with an
  existing custom exercise.** Safe by construction: the new built-in row gets its own new, distinct
  stable slug (see SEED CATALOG LIFECYCLE), entirely separate from the user's existing custom
  exercise's UUID. Both rows now coexist with the same display name — a picker-display/
  disambiguation question for implementation (e.g., grouping by `source`), not a data-integrity
  problem, since the two `id`s never collide and each keeps its own independent history. The
  duplicate-warning hint only fires at *creation* time and is not retroactively re-run against
  catalog changes that happen later — no retroactive scanning or merge logic is implied or needed.
- **E — user starts a workout and immediately chooses Discard.** Per the revised discard semantics:
  the `ACTIVE` `Workout` row (with zero or minimal children at that point) is transactionally
  deleted; the user returns to the start/history screen; the workout leaves no trace in history,
  matching "this didn't happen."

---

## HUMAN DECISIONS REQUIRED

Held at **three** after this hardening pass. Rest-timer timing and discard semantics are resolved
by design reasoning, not escalated (discard was re-examined from first principles this pass and
confirmed resolvable — see ACTIVE-WORKOUT STATE MODEL — rather than merely carried over). No new
subjective decision emerged from the hardening pass that isn't already one of these three.

1. **Exercise identity/catalog strategy** — approve the hybrid model (Option D: one persistent
   `Exercise` entity, identity = stable `id` not display name, `normalizedName` used only as a
   search key / non-blocking duplicate-creation hint — never a uniqueness constraint) and bless the
   "small, curated seed list, not an exhaustive catalog" approach. The structural recommendation
   (including the retraction of the username-normalization analogy) is confident; the catalog's
   initial content/scope is a product-content call.
2. **Completed-workout editing** — approve the editable-history direction (with `completedAt` never
   changing and `lastModifiedAt` tracking corrections separately), understanding it commits to a
   future sync/conflict model that does not yet exist and differs from the currently-proven
   immutable-upsert pattern (`LocalRecordSyncCoordinator`).
3. **Previous-performance timing** — the semantics are now fully specified and the cost is low (see
   PREVIOUS PERFORMANCE above); this document leans toward shipping it in this first slice rather
   than the next one. Still a human decision because "how minimal should the very first release be"
   is a scope-priority call, not something architecture reasoning alone should decide on the team's
   behalf.

## HUMAN RECOMMENDATION PACKET

**These are recommendations for the two human developers to review and agree on — nothing below
changes any decision's status.** Each corresponds directly to one of the three open decisions above,
stated as a single concrete line for ease of sign-off:

1. **Exercise identity**: *Hybrid — small stable built-in catalog + persistent custom exercises
   using stable Exercise IDs.*
2. **Completed-workout editing**: *Allow corrections after completion; preserve `completedAt` and
   update `lastModifiedAt`.*
3. **Previous performance**: *Include in the first slice, derived from prior completed workouts
   using the same stable Exercise ID.*

These remain recommendations, not decisions, until the two human developers agree on them.

---

## IMPLEMENTATION SEQUENCE AFTER APPROVAL

Not started now — sequencing only, for after the human decisions above are resolved:

1. Add `Exercise`/`Workout`/`WorkoutExercise`/`WorkoutSet` Room entities + DAOs to the Phone module;
   turn on `exportSchema`, commit the schema JSON, write the transition `Migration` covering both
   the existing disposable tables and the new ones (per PERSISTENCE/MIGRATION POLICY).
2. Seed the small built-in `Exercise` catalog (as part of that migration or a first-run routine).
3. Build the four minimal screens (workout history/start, active workout, exercise picker/creation,
   completed detail), wired directly to the new DAOs — no network/sync involved.
4. Add the Room/domain tests and the manual on-device process-death script entry from TEST STRATEGY.
5. Add previous-performance display as a pure read-side addition, once the durable core above is
   tested — in this same slice if OPEN DECISION #3 is approved for first-slice inclusion, or as the
   first item of the immediately following slice otherwise; either way it depends on nothing beyond
   step 4.
6. Everything in COMPETITOR FEATURES DELIBERATELY DEFERRED stays deferred until explicitly
   re-scoped in a future design pass.

---

## Status

**STATUS: DESIGN PROPOSAL — FOR TEAM REVIEW** (unchanged by this revision). Nothing in this
document is implemented. No PD-001/PD-002/PD-003 status was changed, and no ADR was created. This
document does not authorize implementation — it exists to be reviewed and either approved (with the
three open decisions resolved), amended, or rejected before any code is written.

**Revision history:**
- Initial version: first-slice design synthesizing the competitor benchmark with TBDFit's existing
  architecture/product principles.
- **Adversarial hardening pass (this version)**: retracted the username-normalization analogy for
  `Exercise.normalizedName` (semantically and mechanically mismatched) in favor of the principle
  "exercise identity is the stable `id`, not its display name," with `normalizedName` demoted to a
  non-blocking search key / duplicate-creation hint; specified the built-in seed-catalog ID
  lifecycle explicitly; reversed the discard recommendation from a soft `DISCARDED` status to a
  transactional deletion, after recognizing the soft-status reasoning was future-sync-tombstone
  thinking leaking into the local domain model rather than a present requirement; split
  `completedAt` (session end time, immutable) from `lastModifiedAt` (last correction time) for
  completed-workout editing; stated plainly that `WorkoutSet` is a strength-set model for this
  slice, not a universal one, with a future cardio domain extending via a new table rather than
  generic typed-value columns; made the single-active-workout restore query and start-guard
  explicit and deterministic rather than UI-assumed; added the precondition that removing
  `fallbackToDestructiveMigration` converts a missing migration from silent data loss into a loud
  crash, plus the table-by-table migration-review discipline; added five new pressure-test
  timelines. No PD/ADR status changed.
- **Final clarification pass (this version)**: clarified that single-active-workout correctness
  requires the check-then-insert to be one atomic database transaction, not app-level serialization
  tied to a particular repository/coordinator instance, with a DB-level constraint left as an
  explicit implementation-time evaluation rather than a decision made here; clarified that the
  `ORDER BY startedAt DESC LIMIT 1` restore query is deterministic recovery behavior for an
  unexpected multi-`ACTIVE` state, not itself enforcement of the invariant; removed the `setType`
  (`WORKING`/`WARMUP`) conditioning from previous-performance's first-slice semantics — no set-type
  abstraction exists in this slice, so all completed persisted strength sets participate, with
  filtering deferred to whenever explicit set types are actually introduced; appended a Human
  Recommendation Packet restating the three open decisions as concrete recommended lines, pending
  the two developers' agreement. No PD/ADR status changed; no decision was marked resolved.
