# TBDFit

A serious workout application. Its initial native clients target iPhone, Apple Watch, Android
phones, and Wear OS, with support for workout execution (not only planning) as a core goal.
Additional client surfaces — for example, a possible future desktop application for workout
planning, history review, and analytics — are a potential future direction and are neither part
of the current scope nor a constraint on it.

## Status

This repository currently contains only the architectural foundation. No application code exists
yet. The project is in architecture/product inception: the ADRs and product decisions below are
**proposed candidates**, reasoned through by one developer, not yet reviewed by the second
developer. Per the project's review process, they become Accepted only after that baseline review
has happened and any material concerns have been resolved — at which point implementation begins.
See `docs/architecture/adr/` and `docs/product/decisions.md` for the proposals and their rationale.

## Proposed architecture decisions (pending team review)

- [ADR-001: Native Client Architecture](docs/architecture/adr/0001-native-client-architecture.md)
  — fully native clients (Swift/SwiftUI for Apple, Kotlin/Compose for Android), with native code
  sharing allowed within an ecosystem but not across the Apple/Android boundary.
- [ADR-002: Monorepo Repository Strategy](docs/architecture/adr/0002-monorepo-repository-strategy.md)
  — a single repository for `apple/`, `android/`, and `docs/`; a `backend/` directory is added once
  a backend technology is selected.
- [ADR-003: Apple Development and Verification Strategy](docs/architecture/adr/0003-apple-development-and-verification-strategy.md)
  — Windows is the primary development environment; Apple-specific verification (Xcode, simulator,
  signing, device) happens in a supported macOS environment on Apple hardware when required,
  without the project depending on owning a dedicated Mac.

## Proposed product decisions (pending team review)

- [PD-001: Independent Watch Workout Execution](docs/product/decisions.md#pd-001-independent-watch-workout-execution)
  — Apple Watch and Wear OS must be able to start, execute, complete, and durably retain a
  workout entirely on-device, with no phone or network connectivity required.
- [PD-002: Local-First Workout Execution (Phone and Watch)](docs/product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch)
  — workout execution must not depend on network or backend availability on either device type;
  this does not imply the whole application works offline.
- [PD-003: Workout Domain Scope](docs/product/decisions.md#pd-003-workout-domain-scope--deferred)
  — **deferred**; the architecture must not assume workouts are only discrete-event or only
  continuous-sensor sessions, but which domain ships first is a scope decision, not an
  architecture decision, made later.

## Repository layout

```
/
  apple/     Native iOS + watchOS application (not yet scaffolded)
  android/   Native Android + Wear OS application (not yet scaffolded)
  docs/
    architecture/
      adr/   Architecture Decision Records
    product/
      decisions.md   Proposed product decisions and their architectural consequences
```

## Contributing

Product scope, backend technology, persistence, synchronization, and the exact native project
structure are intentionally undecided at this stage. See the documents above for what is currently
proposed and why, and for what is deliberately left open.
