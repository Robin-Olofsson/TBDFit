# Product Decisions

This file records product-direction decisions that carry architectural consequences but are not
themselves ADRs — no technology, protocol, or alternative comparison is recorded here. Entries are
proposed candidates during architecture inception (status **PROPOSED — FOR TEAM REVIEW**) and
become Accepted only after the second developer has reviewed the initial architecture baseline and
any material concerns have been resolved. Once a decision below matures into a technology/
structural choice (e.g. a sync protocol, an ownership algorithm, a persistence engine), that choice
is recorded separately as an ADR in `docs/architecture/adr/`, and this file is updated to
reference it.

## PD-001: Independent Watch Workout Execution

**Status:** PROPOSED — FOR TEAM REVIEW (put forward as a candidate decision; becomes Accepted
only after the second developer has reviewed the initial architecture baseline and any material
concerns have been resolved)

**Decision**

Apple Watch and Wear OS must be able to operate as fully independent workout clients. A user must
be able to:

- start a workout on the watch without the phone nearby
- execute the entire workout on the watch
- record the data required by that workout
- complete the workout on the watch
- retain the completed workout locally
- do all of the above without network connectivity

When the watch later comes into contact with the phone, the devices must be able to synchronize
relevant workout data.

The watch is a first-class client with its own durable workout state. It is **not** modeled as a
remote control, display, or temporary mirror of phone-owned workout state. This decision applies
conceptually to both Apple Watch and Wear OS; their native implementations may differ (see
[ADR-001](../architecture/adr/0001-native-client-architecture.md)).

**Accepted architectural consequences**

- Active workout state must survive temporary phone disconnection.
- Active workout state must not depend on backend availability.
- Completed workouts must be durable locally on the watch until synchronization succeeds.
- Phone/watch communication must tolerate delayed delivery.
- Synchronization may happen substantially later than workout completion.
- Retries must not accidentally create duplicate workouts.
- The phone cannot be assumed to always be the authoritative writer.
- Process termination/restart must be considered during workout execution.
- Synchronization failure must not invalidate a successfully completed local workout.

**Distinction preserved**

Independent workout execution does not mean every future phone feature must exist on the watch.
The invariant is specifically that the watch can independently execute and persist the workout
experience *that the watch supports*. Planning, deep analytics, configuration, and other
larger-screen functionality may remain phone/desktop responsibilities.

**Explicitly not decided by PD-001**

The following require additional product decisions before they can be chosen, and are deliberately
left open here:

- conflict-resolution algorithm
- event sourcing
- CRDTs
- sync transport
- backend protocol
- phone/watch ownership protocol
- database technology

**Downstream effect on existing documentation**

This resolves, in direction only (not in mechanism), the "degree of watch independence" item
previously tracked as undecided in
[ADR-001](../architecture/adr/0001-native-client-architecture.md), and narrows several items
previously flagged in architectural risk discussion (active workout ownership, offline workout
durability, duplicate synchronization) from open product questions to accepted constraints that
any future sync/ownership ADR must satisfy.

## PD-002: Local-First Workout Execution (Phone and Watch)

**Status:** PROPOSED — FOR TEAM REVIEW (put forward as a candidate decision; becomes Accepted
only after the second developer has reviewed the initial architecture baseline and any material
concerns have been resolved)

**Decision**

> Workout execution is local-first and must not depend on network or backend availability,
> regardless of whether the workout is executed on a phone or a watch.

A workout must be startable, executable, completable, and durably persisted locally without
internet connectivity, on either device type.

**Scope**

This generalizes [PD-001](#pd-001-independent-watch-workout-execution)'s offline requirement — 
previously stated for the watch specifically — to the phone's own execution path as well, so both
devices offer the same offline-durability guarantee for active workout execution. It does **not**
extend beyond execution: it does not imply the entire application must work offline.

Connectivity requirements for account operations, synchronization itself, remote/shared content,
analytics, and other future functionality are separate decisions, not settled by this entry.

**Relationship to PD-001**

PD-001's accepted architectural consequences (state must survive disconnection, must not depend on
backend availability, retries must not create duplicate workouts, synchronization failure must not
invalidate a completed local workout, etc.) now apply uniformly to phone-executed and
watch-executed workouts alike, not only to the watch.

**Verification status**

- **Phone, process-death survival — DEVICE VERIFIED (2026-09-05).** Manually verified on an
  Android emulator: created local records (via the technical `LocalRecordEntity`/Room slice, not
  a workout), force-stopped the process (`adb shell am force-stop com.tbdfit.phone`), relaunched
  from the launcher, and confirmed the same records were still present. This verifies the
  local-durability mechanism (Room, committed to disk) survives real OS-level process termination
  on the phone — it does not yet verify this for a workout specifically, since no workout domain
  model exists yet, and it does not yet cover the watch.
- **Watch, process-death survival — not yet verified.** No Wear OS persistence implementation
  exists yet.

## PD-003: Workout Domain Scope — Deferred

**Status:** Deferred — decide when planning the first workout implementation slice. The retained
constraint below is itself a candidate baseline item (PROPOSED — FOR TEAM REVIEW), not yet accepted.

**What is deferred**

Whether the initial implemented workout domain is strength training (discrete set/rep sessions),
running/cardio (continuous sensor/time-series sessions), or both, is treated as a product-scope /
implementation-sequencing decision, not a foundational architecture decision. It is intentionally
**not** recorded as an accepted decision at this stage.

**Retained architectural constraint**

> The architecture must not assume that all workouts are only discrete set/rep-based sessions, nor
> that all workouts require continuous sensor/time-series data.

This exists only to prevent a foundational decision made now from obviously foreclosing one domain
later. It does **not** authorize designing a generalized "universal workout model" now — unifying
strength and cardio into one abstraction before concrete domain models exist for either is
premature generalization and is explicitly avoided.

**Distinctions to keep separate**

- *Product/domain capability direction* — long-term, both discrete and continuous-sensor workouts
  are expected to be supported.
- *First implemented workout type* — deferred; decided when the first workout implementation slice
  is planned.
- *First platform-risk investigation* — may be triggered independently and earlier than the scope
  decision above, if a proposed architecture starts depending on unverified assumptions (e.g.
  background GPS behavior, long-running independent watch sessions). Such a trigger is a targeted
  platform investigation, not a commitment to cardio-as-V1-scope.

**Explicitly not decided by PD-003:** which domain ships first, whether both ship together, and
the workout data model itself.
