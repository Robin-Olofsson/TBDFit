# Product Decisions

This file records accepted product-direction decisions that carry architectural consequences but
are not themselves ADRs — no technology, protocol, or alternative comparison is recorded here.
Once a decision below matures into a technology/structural choice (e.g. a sync protocol, an
ownership algorithm, a persistence engine), that choice is recorded separately as an ADR in
`docs/architecture/adr/`, and this file is updated to reference it.

## PD-001: Independent Watch Workout Execution

**Status:** Accepted

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
