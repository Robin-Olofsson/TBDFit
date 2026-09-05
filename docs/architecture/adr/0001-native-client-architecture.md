# ADR-001: Native Client Architecture

## Status

PROPOSED — FOR TEAM REVIEW

This has been reasoned through and is put forward as a candidate decision. It becomes Accepted
only after the second developer has reviewed the initial architecture baseline and any material
concerns have been resolved.

## Context

The initial native client scope targets four surfaces: iPhone, Apple Watch, Android phones, and
Wear OS watches. Workout execution is expected to depend heavily on watch capabilities, health
APIs (HealthKit / Health Connect), sensors, background execution, battery-sensitive workloads,
phone/watch communication, and native platform UX and lifecycle behavior.

This ADR governs these four native mobile/watch clients specifically. It does not assume the
product can only ever have these four clients — additional client surfaces (for example, a
possible future desktop application for planning, history review, and analytics) may be
introduced later under their own architectural decisions, without reopening this one.

## Decision

Clients are built fully native, per ecosystem:

**Apple**
- Swift
- SwiftUI
- Native iOS application
- Native watchOS application

**Android**
- Kotlin
- Jetpack Compose
- Native Android application
- Native Wear OS application

Native code sharing **within** one ecosystem is allowed when justified by genuinely overlapping
responsibilities (e.g. a Swift package shared between the iOS and watchOS targets, or a Kotlin
module shared between the phone and Wear OS modules).

**No shared client implementation crosses the Apple ↔ Android boundary.** Cross-platform behavior
is coordinated through shared specification (documented invariants, state transitions, test
scenarios), not shared code. See the behavioral parity principle recorded alongside this ADR set.

## Alternatives considered and rejected

- **Flutter** — cross-platform UI framework; rejected due to friction and lag on deep
  platform/health/sensor/background-execution integration, which this product depends on heavily.
- **React Native** — same rejection rationale as Flutter.
- **Kotlin Multiplatform (shared client core)** — rejected as the primary architecture; would
  introduce a shared abstraction layer over exactly the platform behaviors (HealthKit vs. Health
  Connect, watchOS vs. Wear OS lifecycle, background execution models) that differ most and matter
  most here.
- **Shared cross-platform UI frameworks generally** — rejected for the same reasons.

## Consequences

- Duplicated application/domain logic across Apple and Android is accepted where genuinely
  necessary, in exchange for full native platform control.
- Behavioral drift between platforms is a real risk that must be managed through shared
  specification and independent conformance testing, not through shared code.
- Do not mechanically translate one ecosystem's framework patterns (e.g. Android ViewModels,
  coroutines, Room, WorkManager, foreground services) into the other ecosystem's idioms. Each
  native implementation should follow its own platform's natural model.

## Deliberately undecided

- Exact module boundaries within each ecosystem beyond the top-level app/watch split.
- Timing of any shared-package/module extraction — deferred until real duplication exists to
  justify it.
- Degree of watch independence from the phone — decided in direction by
  [PD-001](../../product/decisions.md#pd-001-independent-watch-workout-execution) (the watch is a
  fully independent workout client); the sync/ownership *mechanism* implementing that decision
  remains undecided.
- Whether additional client surfaces (e.g. a future desktop application) will exist, and what
  architecture they would use if so — out of scope for this ADR.
