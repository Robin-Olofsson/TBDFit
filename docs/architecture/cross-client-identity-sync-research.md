# Cross-Client Identity + Sync Research

**Status: `RESEARCH / ARCHITECTURE INVESTIGATION — NOT A DECISION`**

This document prepares for the future point where Android Phone (Room, local-first) and Web
(Supabase/Postgres, central) must represent and eventually synchronize the same product data. It
is architecture research grounded in distributed-systems/mobile-sync reasoning and this
repository's own already-established local-first principles, not a competitor UX survey. No code,
migration, or ADR status was changed to produce this document.

Terminology fixed for the rest of this document (Part 8's requirement — used consistently below,
not reintroduced ambiguously per section):

- **Central authority** — the durable, cross-client-shared truth for planning data (Routine,
  Program, Exercise catalog) once it has synced to Supabase. A client may create/edit this data
  offline first; Supabase becomes authoritative for it only after a successful write.
- **Local execution authority** — Room, for an in-progress `Workout`. Network unavailability must
  never block starting, logging, or completing a workout (PD-001/PD-002). Supabase has no opinion
  on this data until it syncs.
- **Replicated durable state** — a completed `Workout` after it has successfully synced: now held
  consistently by both Room and Supabase, neither one "more correct" than the other for that row.
- **Cache** — TanStack Query's Web-side read cache. Not authoritative for anything; a performance
  layer over Supabase reads, invalidated/refetched, never a source of truth.

---

### CURRENT CROSS-CLIENT STATE

Two independent, unconnected persistence stores exist today, both real and tested, with **no sync
between them**:

- **Android (Room)**: `LocalAccountEntity` (id = Supabase auth user id — the local ownership root,
  not a competing identity), `ExerciseEntity` (`BUILT_IN` with fixed string IDs like
  `builtin_bench_press`, `CUSTOM` with a real `ownerId`), `WorkoutEntity`/`WorkoutExerciseEntity`/
  `WorkoutSetEntity` (real local-first execution, `WorkoutSet.targetReps`/`targetWeight` frozen at
  START), `RoutineEntity`/`RoutineExerciseEntity`/`RoutinePlannedSetEntity` (real, IDs are
  `UUID.randomUUID().toString()` generated at the repository layer).
- **Web (Supabase/Postgres)**: `exercises` (fresh `gen_random_uuid()` rows, built-ins seeded with
  `owner_id = null`), `routines`/`routine_exercises`/`routine_planned_sets` (owner_id → `auth.users`
  CASCADE, full RLS, an atomic `save_routine(...)` RPC doing full-replace-by-delete-then-reinsert).
  A Program schema is being added in a concurrent, separate task at the time of this research.

Both sides independently arrived at the same domain shape (`Routine → RoutineExercise →
RoutinePlannedSet`, planned ≠ actual) without copying one from the other — a genuinely good sign for
future reconciliation, since the *semantics* already agree; only *identity* does not yet.

---

### CURRENT IDENTITY MISMATCHES

One concrete, already-live mismatch, and it is the most consequential finding in this document:

- **Built-in Exercise IDs disagree today.** Android's `builtin_bench_press` (a fixed string,
  `BuiltInExerciseCatalog.kt`) and Supabase's `exercises` row for "Bench Press" (a `gen_random_uuid()`
  value, different on every fresh migration run) are **not the same identifier** and were never
  intended to be — the Routine migration's own comment states this explicitly: "there is no
  cross-client sync yet... so forcing the ID spaces to match would be a false consistency with no
  actual behavior behind it." That reasoning was correct *at the time* (sync was genuinely out of
  scope), but it is exactly the gap this research exists to close before sync begins.
- **No mismatch yet for user-created content** (Routine, custom Exercise) because Room and Postgres
  Routines are two disjoint sets of rows belonging to the same human but never compared — there is
  no "same Routine, different ID" case yet, only "two Routines that happen to look similar." This
  will become a real mismatch class the moment any reconciliation is attempted, unless client-
  generated IDs are adopted before then (see ROUTINE IDENTITY, CUSTOM EXERCISE IDENTITY).

---

### GLOBAL ACCOUNT IDENTITY

No mismatch here — this is already correct and does not need research, only restating for
completeness. `auth.users.id` (Supabase Auth) is the one global account identity across every
client. Android's `LocalAccountEntity.id` is set to that same value and exists only because Room
needs a real local row to `FOREIGN KEY` against (Room cannot reference a remote `auth.users` table)
— it is local ownership *context*, not a second identity. Web needs no equivalent: it reads
`auth.uid()` directly from the live session on every RLS-protected query, so there is nothing to
introduce. This asymmetry (Android needs a local shadow row, Web does not) is a genuine, permanent
platform difference — Room requires a real referenced row for a `FOREIGN KEY`; Postgres already
has `auth.users` locally. It is not a gap to close.

---

### BUILT-IN EXERCISE IDENTITY OPTIONS

Three options, evaluated against the required properties (rename/localization/alias/merge safety;
no display-name-as-identity):

**Option A — Stable canonical UUIDs, defined once as literals.** A single, hand-picked UUID
literal per built-in exercise (e.g. `Bench Press` = a specific, permanently fixed UUID), written
into both Room's seed data (`BUILT_IN_EXERCISE_SEED`) and the Postgres migration's `insert into
exercises` statement, as literal values, not generated at insert time. **Recommended.** This is a
real fix, not a runtime translation layer: the two clients insert *the same identifier*, so
"Bench Press on Web" and "Bench Press on Phone" become literally the same row's ID, with zero
ongoing mapping-table maintenance. A rename is safe (the UUID never changes); localization is safe
(display name is a separate column/i18n concern entirely, identity is the UUID); merging two
built-ins later is the one case this doesn't solve for free, but it doesn't need to — a merge is a
rare, deliberate catalog-curation action that can carry its own one-time migration whenever it
happens, not something the everyday identity scheme needs to anticipate.

**Option B — Stable semantic keys (`barbell_bench_press`) + separate surrogate UUIDs per client.**
Rejected as the primary mechanism: this is exactly a mapping layer by another name — Postgres would
still need a `canonical_key` column distinct from its real `id`, and Android would need to either
adopt the same string as its real PK (a real option, see below) or maintain `canonical_key ↔ id`
correspondence somewhere. It adds a genuine benefit only if the *string itself* carries useful
meaning beyond identity (e.g. future URL slugs) — not evidenced as needed today.

**Option C — Runtime mapping table (`android_exercise_id ↔ postgres_exercise_id`).** Rejected.
This is the exact "ID translation layer" Part 1 explicitly says to avoid unless genuinely necessary,
and it is not necessary here — Option A removes the need for it entirely for the one class of data
(built-ins) where IDs are chosen at build/seed time, not created dynamically by a user.

**Recommendation: Option A**, with a lightweight refinement — keep Android's existing
`builtin_bench_press`-style strings as the *canonical* identifier (they already exist, are already
in production Room databases, are human-readable in logs, and Postgres has no technical reason to
require a UUID type for this specific table — `id text primary key` works identically to `id uuid
primary key` for a small, hand-seeded catalog). This means: **change Supabase's `exercises` seed
data to use `builtin_bench_press` etc. as literal primary keys instead of `gen_random_uuid()`**,
rather than inventing a third ID scheme neither client has used yet. Custom exercises keep using
real UUIDs on both sides (see next section) — only the built-in *catalog* needs a fixed, shared key
scheme, because only the catalog is authored once, by the product, and inserted identically
everywhere.

---

### CUSTOM EXERCISE IDENTITY

**Recommendation: client-generated UUIDs**, created at the moment of authoring (Room's
`UUID.randomUUID().toString()` today, `crypto.randomUUID()` on Web already used for local draft IDs
in `RoutineEditorPage.tsx`), inserted as the literal primary key on whichever backend eventually
receives the row.

Why this is the right default for *user-created* content specifically (as opposed to built-ins,
which are better served by Option A above): a client-generated ID makes offline creation
naturally idempotent. Concretely:

```text
Phone (offline)
→ create custom Exercise, id = X (generated locally, immediately)
→ user can use it in a Routine right away (X already exists locally)
→ later: reconnect
→ INSERT INTO exercises (id, name, owner_id) VALUES (X, ...) ON CONFLICT (id) DO NOTHING
→ if the insert already happened once and this is a retry: no-op, still id = X
→ Web (same account) later reads the exercises table: sees id = X, same row
```

No ID ever changes across the offline→synced transition, so nothing that referenced `X` locally
(a `RoutineExercise.exerciseId`) needs to be rewritten after sync. This directly satisfies Part 20
(idempotency): a retried INSERT with the same client-chosen ID is naturally safe via `ON CONFLICT
(id) DO NOTHING` (or `DO UPDATE` if the retry should also apply a concurrent edit — a V1 choice,
not investigated further here since plain custom-exercise-create has no edit-on-retry need today).

Compare to server-generated IDs: the client would have to create the row *with a temporary local
ID*, wait for the server's real ID on the first successful sync, then rewrite every local reference
to that temporary ID — a real remapping step, with its own retry/idempotency problems (what if the
remap itself is interrupted?). Client-generated UUIDs remove this step entirely. The security
implication (Part 24) is addressed below (RLS VS LOCAL OWNERSHIP / CLIENT-GENERATED UUID SECURITY
is folded into that section, since it's the same reasoning).

---

### ROUTINE IDENTITY

Same reasoning, same recommendation, applied one level up: `Routine`, `RoutineExercise`,
`RoutinePlannedSet` should all use client-generated UUIDs, generated once at creation (whichever
client creates them) and never reassigned. This is not a new recommendation for Android (it already
does this) — it is the recommendation for **how Web's future "receive a Routine created offline on
Phone" sync path should work**: Phone already has real IDs the moment a Routine is created, so a
future sync operation is a plain `INSERT ... ON CONFLICT (id) DO NOTHING` (create) or `... DO
UPDATE` (edit-then-sync) using Phone's own IDs, never a server-assigned replacement.

```text
Phone (offline)
→ create Routine, id = R (local, immediate)
→ edit it (still offline)
→ reconnect
→ sync: upsert Routine R + its RoutineExercise/RoutinePlannedSet rows (same IDs) into Supabase
→ Web reads: sees the same Routine R
```

This is why Part 12's "aggregate vs. row-level" question (answered below) matters here specifically:
Android's `RoutineRepository` already builds a Routine's full exercise/set tree with fresh UUIDs at
creation time — a future sync operation can push that whole tree in one call, structurally similar
to how `save_routine`'s RPC already accepts a full nested payload today (just from Phone instead of
the browser).

---

### PROGRAM IDENTITY

Identical reasoning: `Program`/`ProgramWeek`/`ProgramSession`/`ProgramSessionExercise`/
`ProgramSessionPlannedSet` should all use client-generated UUIDs from the moment they exist, on
whichever client creates them. The brief specifically flags "Program may be created on Web first and
later consumed on Phone" — client-generated UUIDs make this direction-agnostic: it does not matter
which client authors a Program first, because neither client needs to wait for or remap a
server-assigned ID before the object is usable locally.

One Program-specific nuance worth flagging now (not resolving): the hardened Android design already
gives `ProgramSession.sourceRoutineId` `ON DELETE SET NULL`, and `Workout.originProgramSessionId`
`ON DELETE RESTRICT` (see `program-routine-first-slice-design.md`). A future Postgres Program schema
should preserve the *same* two decisions with the *same* reasoning (traceability vs. load-bearing
for derived progress) — this is a semantic-consistency requirement, not an identity one, but it
belongs in the same document lineage so a future Postgres Program migration does not silently drift
from the hardened Android reasoning.

---

### WORKOUT IDENTITY

This is the most operationally important identity decision, because `Workout` is where PD-001/
PD-002's offline-execution guarantee actually lives.

**Recommendation: client-generated UUID, assigned at the instant of `START`, on whichever device
executes the workout (Phone or Watch).** This ID never changes across the workout's entire
lifecycle — local creation, local logging, local completion, and (whenever it happens) sync to
Supabase.

```text
Phone (offline, no network at all)
→ START Workout, id = W (generated locally, immediately)
→ durable in Room immediately (PD-002: this must never depend on network)
→ log sets, complete workout — all still id = W, all still local-only
→ (later) network returns
→ sync: INSERT Workout W (+ its WorkoutExercise/WorkoutSet rows) into Supabase,
  ON CONFLICT (id) DO NOTHING — a retried sync attempt after a timeout cannot produce
  a duplicate Workout, because it always targets the same id = W
```

This directly satisfies PD-001's explicit "retries must not accidentally create duplicate
workouts" consequence and Part 20's idempotency requirement, using the same mechanism as
Routine/Program/custom-Exercise — one consistent identity strategy across every user-created entity
type, not a special case for Workout. This document does **not** design Workout's remote schema
(explicitly out of scope, Part 6) — only its ID-assignment timing and the idempotency property that
timing buys.

---

### LOCAL-FIRST AUTHORITY MODEL

Using the four terms fixed at the top of this document:

| Data | Authority | Why |
|---|---|---|
| Exercise catalog (built-in) | Central authority | Product-authored, identical everywhere, never created offline by a user |
| Custom Exercise | Created locally, becomes central authority once synced | A user can create one offline; until synced, the client holding it is the only copy |
| Routine, Program (+ children) | Created locally, becomes central authority once synced | Same reasoning — "planning data," per Part 8's own example |
| Active `Workout` | Local execution authority (Room, or Watch's own local store) | PD-001/PD-002: must never require network to start/log/complete |
| Completed `Workout` | Replicated durable state, once synced | After a successful sync, Room and Supabase both hold the same true record; before sync, Room alone does |
| Web's read of any of the above | Cache (TanStack Query) | Never authoritative — a performance layer over whatever Supabase currently holds |

The one rule this table enforces: **"Supabase is source of truth" is never stated as a blanket
claim in this document**, because it is only true for the *central authority* and *replicated
durable state* rows above — never for an in-progress Workout, which is locally authoritative by
explicit product decision (PD-002) regardless of what Supabase does or does not know about it yet.

---

### WEB / PHONE / WEAR SYNC BOUNDARIES

**Recommendation: hub-and-spoke for Phone/Web (`Phone ↔ Supabase`, `Web ↔ Supabase`, no direct
`Phone ↔ Web` channel), with Wear kept on its current, separate `Wear ↔ Phone` channel** rather than
Wear talking to Supabase directly.

Reasoning for hub-and-spoke over direct Phone↔Web: Phone and Web are not expected to be
simultaneously active with the same user in the same moment the way Wear and Phone are during a
single workout — there is no low-latency requirement between them, and Supabase already needs to be
the durable central store regardless (Web has no local persistence of its own to sync *from* — it
only reads/writes Postgres directly). A direct Phone↔Web channel would duplicate exactly what
Supabase already provides with no latency benefit, for two clients that are essentially never both
mid-interaction with the same object at the same instant.

Reasoning for keeping `Wear ↔ Phone` separate rather than routing through `Wear ↔ Supabase ↔ Phone`:
this is the one place low latency and offline-together operation genuinely matter (Watch and Phone
may be physically together, both without network, executing the same workout) — PD-001 already
requires the watch to operate with the phone "not nearby," which specifically implies the watch
must **not** need to reach Supabase to reconcile with the phone at all. Routing Wear's replication
through Supabase would add a mandatory network dependency to a relationship PD-001 explicitly
designed to survive *without* one. The existing `WearReplicationCoordinator`/`WearRecordTransport`
mechanism (proven for the disposable `LocalRecord` proof) should extend to real Workout data as a
**separate concern from** — not a special case of — the eventual Phone↔Supabase sync. Concretely:
Wear syncs to Phone (existing mechanism, offline-capable); Phone, independently, later syncs
whatever it now holds (its own + relayed-from-Watch Workouts) to Supabase using the same
Phone↔Supabase mechanism it uses for its own directly-created Workouts. Wear never needs its own
Supabase credentials or network stack for this.

```text
Wear  ↔  Phone  ↔  Supabase
              ↕
             Web
```

This is not a redesign of the existing architecture — it is a statement that the existing
`Wear ↔ Phone` boundary should remain exactly where it is, and the new `Phone ↔ Supabase` /
`Web ↔ Supabase` boundaries should be added alongside it, not through it.

---

### AGGREGATE VS ROW-LEVEL SYNC

**Recommendation: sync Routine and ProgramSession as whole aggregates**, matching how they are
already written (`save_routine`'s full-replace-per-save RPC). A "Routine" for sync purposes is one
unit: itself + its `RoutineExercise` + `RoutinePlannedSet` rows, moved and conflict-checked
together, not as N independently-reconciled child rows.

Why this is simpler, not just consistent: conflict detection (next section) needs exactly one
`updated_at`/version check per sync unit. If children synced independently, a partial conflict
(e.g. exercise 2 of 3 changed remotely, exercise 1 and 3 did not) would require row-level merge
logic this product has no evidence it needs — the existing `save_routine` RPC already treats
"replace the whole exercise/set list" as the natural unit of change from the authoring UI's
perspective (the editor holds the *entire* Routine as one in-memory draft before Save), so syncing
at that same granularity requires no new concept, only extending the existing full-replace pattern
across the network boundary instead of within one browser session.

`Program` is a deeper tree (`Program → Week → Session → Exercise → PlannedSet`) — the same
aggregate principle applies, but the practical sync *unit* is more likely to be one `ProgramSession`
at a time (matching `save_program_session`'s own scope in the concurrently-developed Program slice)
rather than an entire 12-week Program in one payload, purely for payload-size/practicality reasons,
not a different conflict philosophy. `Program`/`ProgramWeek` themselves (name, ordering) can sync as
their own small aggregates independently of their sessions' content.

---

### CONFLICT STRATEGIES

**Recommendation: optimistic concurrency via a server-set `updated_at` (or an explicit integer
`version` column) checked on write, with last-write-wins as the actual resolution — no manual merge
UI, no CRDTs, no event sourcing.**

Concretely: every sync-eligible aggregate's write path (whether from Web's existing RPC pattern or
a future Phone sync operation) includes "what `updated_at`/`version` did I last see" in its request;
if the row's current `updated_at`/`version` in Postgres no longer matches, the write is rejected and
the caller re-fetches the current row before deciding whether to overwrite. The actual resolution
when a real conflict occurs is **last-write-wins** — whichever client's corrected retry lands second
simply overwrites — not a merge dialog.

This is explicitly a real, accepted data-loss risk in the rare case: the same account editing the
same Routine from Phone and Web within the same short window, disconnected during the edit, could
lose one side's changes. This is judged acceptable for TBDFit's actual current risk profile (a
single-user product, one person editing their own data, simultaneous multi-device editing of the
exact same object is a genuinely rare event) rather than a reason to build CRDT/event-sourcing
infrastructure whose engineering cost is disproportionate to a rare, low-severity failure mode (lost
work the user can simply redo, not corrupted or duplicated data).

**Clock skew**: `updated_at` must be **server-set** (Postgres `default now()` + a trigger/explicit
`SET updated_at = now()` on every UPDATE, exactly as `save_routine` already does), never a
client-supplied timestamp. This is already how the existing `routines` table behaves — no new
mechanism needed, only extending the same pattern to Program and (eventually) a synced-Workout
table. A client-set timestamp would make conflict detection dependent on device clocks being
correct and synchronized, which cannot be relied on (a device with a wrong clock could silently
"win" every conflict or falsely appear stale).

---

### VERSION / UPDATED_AT STRATEGY

`updated_at` (timestamp) is sufficient for TBDFit's V1 needs and is preferred over an explicit
integer `version` column: it is already the pattern in use (`routines.updated_at`), requires no new
column type, and doubles as genuinely useful information (last-modified display) beyond its role as
a concurrency token — a plain incrementing integer buys nothing extra for a last-write-wins
resolution strategy, since the comparison is always "is this still the version I last saw," not
"which version number is higher across a distributed counter" (which would matter for a fancier
resolution strategy this document is explicitly not recommending). Revisit only if a future
resolution strategy genuinely needs monotonic ordering guarantees timestamps can't provide (e.g.
clock adjustments, leap seconds) — no evidence that's needed now.

---

### DELETION / TOMBSTONE STRATEGY

**Recommendation: soft-delete (a nullable `deleted_at` column) for sync-eligible aggregates
(Routine, Program and its children, custom Exercise), not a full change-log/event-sourcing
mechanism.**

The scenario the brief names is the real one to solve: Web deletes a Routine while Phone is offline
with a local copy; Phone must not resurrect it on reconnect. A soft-delete satisfies this cheaply:
the delete becomes an ordinary `UPDATE ... SET deleted_at = now()` (itself subject to the same
`updated_at`/optimistic-concurrency check as any other write), and a future Phone pull-sync simply
also asks "what changed since my last sync" inclusive of `deleted_at` — a row with `deleted_at` now
set is a signal to remove or hide the local copy, not to re-upload it as if it were new.

Hard-delete-plus-change-log (a separate table recording "row X of type Y was deleted at time T") is
the more general/robust mechanism and was considered, but rejected for V1 as more machinery than
this problem currently needs — it exists to solve N-way multi-table deletion history at scale, and
TBDFit's actual need is "don't resurrect a row the user already deleted," which a `deleted_at`
column already answers. This can evolve into a real change-log later without a breaking change (see
CHANGE DETECTION below — a `deleted_at`-aware `updated_at` cursor query already gets most of the
value a change-log would add).

Genuine limitation, stated honestly: a soft-deleted row is never actually removed from storage by
this mechanism alone — a real hard-delete/vacuum policy for old soft-deleted rows is a future
housekeeping decision, explicitly deferred here (not urgent at TBDFit's current data volume).

---

### CHANGE DETECTION

**Recommendation: `updated_at`-cursor pull-sync as the MVP mechanism** — a client stores "the
`updated_at` of the newest row I've successfully synced" per table/aggregate type, and on
reconnect/periodic-sync issues a plain query: `SELECT * FROM routines WHERE owner_id = auth.uid()
AND updated_at > :last_synced_cursor`. This requires no new Supabase infrastructure (no Realtime, no
change-log table, no external queue) — it is a standard indexed range query against a column that
already exists (or needs to exist) on every sync-eligible table.

This is deliberately the simplest mechanism that satisfies the actual MVP need (Phone eventually
learning what changed on Web while it was offline, and vice versa) without committing to a more
elaborate protocol before real usage data suggests one is needed.

---

### IDEMPOTENCY

Already established structurally by the client-generated-UUID recommendation across every entity
type (GLOBAL ENTITY IDENTITY / CUSTOM EXERCISE IDENTITY / ROUTINE IDENTITY / PROGRAM IDENTITY /
WORKOUT IDENTITY above): every create operation targets a specific, pre-chosen ID, so `INSERT ...
ON CONFLICT (id) DO NOTHING` (create-only) or `... DO UPDATE ... WHERE updated_at = :expected` (the
optimistic-concurrency-checked edit path) makes a retried mutation safe by construction. A timed-out
"create Routine" retry can never produce two Routines, because both the original attempt and the
retry carry the exact same client-generated `id` — the second attempt either finds nothing to do
(the first one actually succeeded server-side despite the client never seeing the response) or
performs the original create exactly once (the first one never reached the server at all). No
separate idempotency-key mechanism is needed beyond "the entity's own ID is already the idempotency
key," because IDs are chosen before the network round-trip, not returned by it.

---

### REALTIME ROLE

Per the brief's own explicit framing, restated and applied: **Supabase Realtime is a notification
that something changed, not a mechanism for determining or applying correct state.** Its correct
future role in this architecture is narrow and specific:

- On Web: invalidate the relevant TanStack Query cache entries when Realtime reports a change to a
  table the current view depends on — prompting a normal refetch through the existing, already-
  authoritative Supabase read path. Realtime never supplies the actual new data to trust directly;
  it only says "go re-ask Postgres."
- On Phone (future): a Realtime event could wake a background sync check sooner than the next
  scheduled poll — again, purely a *prompt* to run the real `updated_at`-cursor sync described
  above, never a payload trusted as-is.

Realtime must never become the reconciliation protocol itself — it has no offline queue, no
delivery guarantee across a period of disconnection, and no conflict semantics of its own. The
`updated_at`-cursor pull-sync (CHANGE DETECTION above) remains correct and complete even if Realtime
is never wired up at all; Realtime is a latency optimization layered on top, not a dependency.

---

### RLS VS LOCAL OWNERSHIP

Supabase RLS protects **remote access** — it answers "may this authenticated `auth.uid()` read/
write this Postgres row," and it is the only thing standing between one user's data and another's
once a request reaches Supabase. Room has no equivalent enforcement mechanism; it answers a
different question entirely — "which rows does this Room database even contain," since Room, being
local-first, only ever holds one account's data on a device at a time in practice, scoped by
`LocalAccount`-based query filtering (`getVisibleTo(ownerId)`, `getActiveWorkout(ownerId)`, etc., as
already implemented and tested).

These two layers complement rather than duplicate each other precisely because they protect
different attack surfaces: RLS defends against a malicious or buggy *client* trying to read/write
data belonging to a *different Supabase account* over the network; Room's owner-scoped queries
defend against a *different local account switching context on the same device* (the
`LastSignedInAccountCache`/cross-account-isolation work already implemented for Workout/Routine)
seeing stale rows left behind by a previous session. Neither layer can substitute for the other:
Room's local scoping does nothing once a row reaches the network; RLS does nothing for a purely
local, never-synced row, since it never reaches Postgres to be checked.

**Client-generated UUID security (folds in Part 24)**: choosing or knowing a UUID grants no access
by itself in either layer — RLS policies check `owner_id = auth.uid()` (or the equivalent
`EXISTS`-based ownership chain for child tables, per the already-proven `routine_exercises`/
`routine_planned_sets` pattern), never "does the caller happen to know this row's ID." An attacker
who guesses or is given another user's Routine UUID still cannot `SELECT`, `UPDATE`, or `DELETE` it
— the already-executed adversarial RLS testing for the Routine slice (a real local Postgres
container, User B attempting exactly this against User A's rows) is direct evidence this holds in
practice for the existing tables, and the same policy shape must be replicated for every future
sync-eligible table for the same property to hold there too.

---

### PLANNING SNAPSHOT IMPLICATIONS

Sync must never "helpfully" propagate a Routine's change into an already-created `ProgramSession` —
this is not a new rule, it is the existing hardened invariant (`program-routine-first-slice-design.md`'s
Snapshot/Copy Semantics: no FK from `ProgramSessionExercise`/`ProgramSessionPlannedSet` back to
`RoutineExercise`/`RoutinePlannedSet`) restated for the sync context specifically, because sync is
exactly the kind of mechanism that *could* be built carelessly to "keep things in sync" across that
boundary if someone assumed "sync" meant "everything stays identical everywhere." It explicitly does
not: `Routine` and `ProgramSession` are sync targets *independently*, each with its own `updated_at`/
version and its own conflict resolution, and the copy operation that created the `ProgramSession` in
the first place already happened at a specific point in time that no later sync operation should
revisit or "correct."

---

### WORKOUT SNAPSHOT IMPLICATIONS

Same reasoning, one layer down: syncing later changes to a `Routine`/`ProgramSession` must never
rewrite an already-started or already-completed `Workout`'s `targetReps`/`targetWeight` (frozen at
START) or its `reps`/`weight`/`isCompleted`/`completedAt` (the actual recorded result). A `Workout`
row, once created, is sync-eligible as its own independent aggregate — its historical content is
exactly as durable across a future sync implementation as it already is across a local
Routine/Program edit today (per the design doc's already-tested "editing a Program never rewrites a
completed Workout" invariant). Sync introduces no new risk here as long as `Workout` is treated as
its own aggregate (per AGGREGATE VS ROW-LEVEL SYNC) rather than being re-derived from whatever the
current Routine/ProgramSession happens to say.

---

### STORAGE PARITY VS DOMAIN PARITY

**Recommendation: share domain semantics, not storage implementation.** Concretely, what must be
semantically identical across Postgres/Room/(future Swift persistence): entity identity (the UUID),
the ownership relationship (who this row belongs to), the parent/child structure and its cascade/
restrict-on-delete meaning, the planned-vs-actual distinction, and the copy/snapshot boundary
between Routine/Program and Workout. What is legitimately allowed to differ per platform: exact
column types (Postgres `numeric` vs. Room `Double`, `timestamptz` vs. Room's `Long` millis),
ordering-constraint mechanics (Postgres's `unique(parent_id, position)` + always-contiguous
positions vs. Room's sparse never-compacted positions — already a deliberate, already-documented
divergence justified by each platform's actual write pattern), index choices, and RLS itself (a
Postgres-only concept with no Room equivalent, addressed instead by owner-scoped queries). Forcing
identical storage schemas across platforms for convenience would fight each platform's own idioms
for no shared-semantics benefit — the two `routines`/`Routine` schemas already diverge exactly this
way today and it is the right amount of divergence, not a gap to close.

---

### FUTURE CREATOR IMPLICATIONS

The client-generated-UUID recommendation is naturally compatible with a future creator/marketplace
model without any structural change: a "copy this Program" or "start this Routine" operation
performed by a *different* user always mints a *new* UUID, owned by whoever performed the copy —
identity assignment is inherently owner-neutral, never baked to "the ID's original author." Nothing
in this document's recommendations assumes `owner_id` on a planning row equals the eventual
performer of any `Workout` derived from it; a future `Creator publishes → User copies/starts → User
owns their instance` flow is a new *operation* (a copy, exactly like `Routine → ProgramSession`
already is) producing new, independently-owned rows — not a change to how identity or ownership is
assigned to those rows. No creator schema is designed here, only confirmed as unblocked.

---

### CURRENT DEVELOPMENT-DATA MIGRATION OPTIONS

This is the most immediately actionable section, per the brief's own flag. TBDFit is pre-production
— no real user depends on today's Supabase built-in-exercise IDs being stable, so **now is
structurally the cheapest this normalization will ever be.**

Concrete recommended action (research-level detail, not a migration to write here): replace the
Routine migration's `insert into exercises (name, owner_id) values ('Bench Press', null), ...` (which
relies on `gen_random_uuid()` defaults) with explicit literal-ID inserts using Android's existing
canonical strings as the primary key value — i.e. `insert into exercises (id, name, owner_id) values
('builtin_bench_press', 'Bench Press', null), ...` — bringing Supabase's built-in catalog into
identity agreement with Android's `BUILT_IN_EXERCISE_SEED` before any real user's custom-exercise
row or Routine references the current, soon-to-be-orphaned random UUIDs. Concretely this requires:
changing `exercises.id`'s column type from `uuid` to `text` (or keeping `uuid` and switching custom
exercises to a different generation strategy while built-ins use a `text`-shaped value — the cleaner
option is almost certainly making `id` a plain `text primary key`, since Postgres does not require
`uuid` typing for a UUID-*shaped* value and custom exercises' client-generated UUIDs serialize to
text identically either way), and re-running the migration (or a follow-up migration) before any
real signed-up user has created a custom Exercise or Routine referencing the old random IDs — since
this is still pre-production, a full local-database reset/reseed is very plausibly cheaper than
writing a compatibility migration for data that (per this session's own established local-first
research) likely does not exist as real user data yet.

**Recommendation: normalize now, via reset/reseed if no real user data yet exists on the live
Supabase project; via a proper data migration only if it does.** The developer is the one who can
confirm which is actually true of the live project at this moment — this document cannot verify
that from the sandbox.

---

### RECOMMENDED MVP SYNC ARCHITECTURE

Synthesizing the above into one coherent shape, at the level of detail appropriate for
"architecture research," not an implementation spec:

```text
Identity:      client-generated UUIDs for all user-created content (Routine, Program, custom
               Exercise, Workout); fixed literal canonical keys for the built-in Exercise catalog,
               shared verbatim between Room's seed and Postgres's seed.

Authority:     central authority for synced planning data; local execution authority for an
               in-progress Workout; replicated durable state for a synced-completed Workout;
               TanStack Query is a cache, never authoritative, on Web.

Direction:     hub-and-spoke — Phone ↔ Supabase, Web ↔ Supabase. Wear stays on its existing,
               separate Wear ↔ Phone channel; Phone relays Wear-originated Workouts to Supabase
               using the same mechanism it uses for its own.

Sync unit:     whole aggregates (Routine; ProgramSession, not the whole Program at once; Workout),
               matching the write granularity already established by save_routine's full-replace
               pattern — never independent per-child-row reconciliation.

Conflict:      optimistic concurrency via a server-set updated_at, last-write-wins resolution,
               explicit accepted data-loss risk in the rare simultaneous-multi-device-edit case.

Deletion:      soft-delete (deleted_at), not a full change-log/event-sourcing mechanism.

Change
detection:     updated_at-cursor pull-sync; Realtime (if ever added) only invalidates a cache or
               wakes a sync check — never a substitute for the cursor query itself.
```

---

### DECISIONS NEEDED BEFORE PHONE SYNC IMPLEMENTATION

Genuinely open, requiring human product/architecture judgment, not resolvable from repository
evidence alone:

1. Whether to reset/reseed current Supabase development data now to adopt canonical built-in
   Exercise IDs, or whether real user data already on the live project requires a compatibility
   migration instead (only the developer can confirm the live project's actual current state).
2. Exact sync trigger cadence for Phone (app-foreground only? periodic background? both?) — this
   document recommends the mechanism (cursor pull-sync) but not its scheduling policy.
3. Whether custom-Exercise conflict handling ever needs anything beyond last-write-wins (e.g. if a
   rename genuinely conflicts) — no evidence yet that it does, flagged only because it wasn't
   explicitly tested against a concrete scenario the way Routine editing was.
4. Who owns writing and maintaining the actual sync engine on Phone (a new Kotlin module, its own
   background-work scheduling, retry/backoff policy) — an implementation-ownership question, not an
   architecture one, but real enough to need a concrete answer before work starts.

---

### WHAT CAN SAFELY BE DEFERRED

Consistent with this session's established "do not overbuild V1" discipline:

- CRDTs, event sourcing, or any general-purpose conflict-resolution engine.
- A full change-log/audit-trail table (the `deleted_at` approach covers the MVP need).
- Supabase Realtime wiring of any kind (Change Detection's cursor approach works without it).
- Any Wear → Supabase direct channel (Wear stays behind Phone).
- A generic "sync engine" abstraction shared across entity types beyond "the same identity/
  conflict/deletion conventions applied consistently" — each entity type (Routine, Program,
  Workout, custom Exercise) can have its own small, concrete sync function following the same
  pattern, mirroring this codebase's existing preference for explicit domain-specific code over
  generic frameworks (`save_routine` itself is exactly this: small and domain-specific, not a
  generic mutation engine).
- Cross-client schema type parity (Postgres `numeric`/Room `Double`, etc. — divergence here is
  fine, per STORAGE PARITY VS DOMAIN PARITY).
- Any Apple/iOS-specific identity work — no Apple client exists yet to have an identity scheme for.

---

### RISKS / FAILURE MODES

1. **The built-in Exercise ID mismatch calcifies if not fixed before real users exist.** Every day
   the current mismatch persists in an active development/beta project increases the chance a real
   custom Exercise or Routine references one of the soon-to-be-replaced random UUIDs, turning a
   cheap reset into an expensive migration. This is the single most time-sensitive finding in this
   document.
2. **Last-write-wins silently discards data in the rare simultaneous-edit case.** Accepted as a
   reasonable V1 trade-off (see CONFLICT STRATEGIES), but worth surfacing explicitly rather than
   letting the risk go unstated: a user who edits the same Routine from two devices within the same
   disconnected window can lose one side's edit with no warning shown. A future improvement (a
   "your change conflicts with a newer version — reload?" prompt) is cheap to add on top of the
   `updated_at` check already recommended, and should be considered before this ships broadly, even
   though this document does not treat it as an MVP blocker.
3. **Client-generated UUID collision** is astronomically unlikely (a real, but negligible, risk of
   any UUIDv4-based scheme) and is not treated as a practical concern here — noted only for
   completeness, not as an open risk requiring mitigation.
4. **RLS policy drift**: every new sync-eligible table (a future Program schema, a future synced
   Workout table) must replicate the exact ownership-chain + exercise-visibility-recheck pattern
   already proven for `routine_exercises`/`routine_planned_sets` — the risk is not in the pattern
   itself (already adversarially tested) but in a future implementer forgetting to re-apply it to a
   new table under time pressure. Recommend the same real local-Postgres adversarial testing
   discipline already used for the Routine slice be treated as mandatory for every future
   sync-eligible table, not optional polish.
5. **Treating this document as more decided than it is.** Nothing here is Accepted. In particular,
   the built-in-Exercise-ID fix (the most actionable finding) still requires an explicit human
   decision about whether real Supabase project data currently depends on the existing random UUIDs
   before anyone resets/reseeds anything.

`CROSS-CLIENT IDENTITY + SYNC RESEARCH STATUS: COMPLETE — NO ARCHITECTURE DECISION MADE`
