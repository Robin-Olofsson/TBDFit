# TBDFit

A serious workout application. Its initial native clients target iPhone, Apple Watch, Android
phones, and Wear OS, with support for workout execution (not only planning) as a core goal.
Additional client surfaces — for example, a possible future desktop application for workout
planning, history review, and analytics — are a potential future direction and are neither part
of the current scope nor a constraint on it.

## Status

This repository currently contains the architectural foundation and a minimal, reversible Android
project scaffold (`android/phone`, `android/wear`) proving the toolchain builds — no workout
feature or domain model exists yet. The project is in architecture/product inception. Most ADRs and
product decisions below are **proposed candidates**, reasoned through by one developer, not yet
reviewed by the second developer, and become Accepted only after that baseline review has happened
and any material concerns have been resolved. ADR-004 is the one exception so far: it originated
from the second developer and has been separately discussed and agreed by both, so it is Accepted
on its own — this does not extend acceptance to any other pending decision. See
`docs/architecture/adr/` and `docs/product/decisions.md` for the proposals and their rationale.

## Accepted architecture decisions

- [ADR-004: Initial Backend Platform and Migration Strategy](docs/architecture/adr/0004-initial-backend-platform-and-migration-strategy.md)
  — Supabase is the initial backend platform, reached only through small, purpose-scoped
  capability boundaries so individual concerns can migrate to a custom backend API later without a
  full client or backend rewrite. Accepted by both developers; this does not imply acceptance of
  any other decision below.

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
  android/   Native Android + Wear OS application
    phone/   Android phone module (foundation scaffold only; no product features yet)
    wear/    Wear OS module (foundation scaffold only; no product features yet)
  supabase/
    migrations/   Authoritative, platform-neutral Supabase/PostgreSQL schema, RLS policies, and
                  indexes (ADR-004). Owned by neither client — Android, Apple, and any future
                  client consume this same backend contract rather than defining their own copy.
  docs/
    architecture/
      adr/   Architecture Decision Records
    product/
      decisions.md   Proposed product decisions and their architectural consequences
```

Supabase is the initial shared backend platform, not the permanent application architecture.
Database schema ownership is not platform-specific: `supabase/migrations/` is the single source of
truth for what exists in the shared Supabase project, and Android and future Apple clients consume
that same backend contract through their own native implementations rather than defining or
duplicating it. Provider-specific Supabase SDK usage stays inside each client's platform-specific
infrastructure code (see `CLAUDE.md`'s Supabase boundary rule), and individual backend capabilities
are intentionally able to migrate to a custom API one at a time. See
[ADR-004](docs/architecture/adr/0004-initial-backend-platform-and-migration-strategy.md) for the
rationale and detailed migration rules.

Supabase local setup and live verification:
[docs/development/supabase-setup-and-verification.md](docs/development/supabase-setup-and-verification.md).

## Contributing

Product scope, backend technology, persistence, synchronization, and the exact native project
structure are intentionally undecided at this stage. See the documents above for what is currently
proposed and why, and for what is deliberately left open.
