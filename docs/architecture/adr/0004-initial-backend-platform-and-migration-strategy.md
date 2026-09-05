# ADR-004: Initial Backend Platform and Migration Strategy

## Status

Accepted

This decision originated from the second developer and has been discussed and agreed by both
developers. It is accepted on its own terms and does not imply acceptance of any other pending
ADR or product decision — ADR-001, ADR-002, ADR-003, PD-001, PD-002, and the remaining inception
proposals remain `PROPOSED — FOR TEAM REVIEW` until reviewed separately.

## Context

Workout execution is local-first on phone and watch and must not depend on backend availability
([PD-001](../../product/decisions.md#pd-001-independent-watch-workout-execution),
[PD-002](../../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch)). A
backend is still needed for account-level and shared concerns — authentication, account/profile
data, and eventual cross-device/cross-session persistence of workout results — none of which have
an implementation yet. ADR-002 deliberately deferred creating a `backend/` directory until a
backend technology was chosen; this ADR makes that choice for the *initial* backend platform, not
for the application's backend architecture as a whole.

The project has two developers, no existing backend investment, and no concrete server-side
requirements yet beyond "get authentication and account-level data working." Building a fully
custom backend API immediately would require owning infrastructure, hosting, and platform work
before any product requirement demands it. At the same time, the team does not want backend
platform convenience today to become deep, hard-to-remove coupling in the native clients.

## Proposed decision

> Use Supabase as the initial backend platform, while structuring backend-facing functionality as
> small, purpose-scoped capabilities so that individual concerns can later migrate to a custom
> backend API without requiring a full client or backend rewrite.

Supabase accelerates early development but is not the application's architecture. The current
intent is to use Supabase where it provides clear value, potentially including hosted PostgreSQL,
authentication, account-level/shared persistence, and basic remote data access — other Supabase
capabilities (Realtime, Edge Functions, Storage, etc.) are adopted only when an actual product
requirement justifies them, not merely because they exist.

### Architectural principle: purpose-scoped capability boundaries

Client code should be structured as:

```
UI / application / domain
        ↓
purpose-scoped capability boundary
        ↓
Supabase-backed implementation
        ↓
Supabase / PostgreSQL
```

Conceptual capabilities may include things like authentication, account/profile data, workout
planning data, workout synchronization, and analytics — these are illustrative, not a mandate to
create all of these modules now. Each boundary is a small, purpose-built seam around a real
responsibility, not a generic repository, backend provider, or portability framework. No universal
abstraction is introduced merely so that "every backend is theoretically replaceable."

### Future migration model

Individual backend concerns may migrate to a custom API independently, while continuing to share
the same underlying PostgreSQL database. An intermediate future state might look like:

```
Auth            → Supabase
Profile         → Supabase
Planning        → Supabase
Workout Sync    → Custom API
                     ↓
              same PostgreSQL
```

Migration happens by responsibility/capability, one at a time, not as a single flag-day rewrite of
all backend access at once.

### Ownership rule

> Every remotely mutable data capability must have a clearly defined mutation authority.

When a capability moves from direct Supabase access to a custom backend API, that custom API
becomes the sole authoritative mutation path for that capability. Clients must not continue
independently mutating the same state through both the Supabase Data API and a custom API unless a
future design explicitly defines safe coordination semantics. This is the same principle already
applied to phone/watch active-workout ownership, applied here to backend mutation paths instead of
devices.

- **Good**: Supabase Data API owns simple profile operations; a custom Workout API owns workout
  synchronization and workout-domain mutations. Two different capabilities, two different owners,
  no overlap.
- **Bad**: a client writes workout state through the Supabase Data API *and* through a custom API,
  with both independently applying business rules to the same data.

### Central database clarification

The shared PostgreSQL database is backend infrastructure, not something native clients connect to
directly. Each executing device retains its own local durable persistence, because workout
execution must keep working without backend/network availability:

```
Phone local persistence      Watch local persistence
              \                      /
               local/cloud synchronization
                          ↓
                   Backend boundary
                          ↓
                    PostgreSQL
```

Phone and watch may additionally synchronize relevant data directly over native paired-device
communication when locally reachable, without requiring backend connectivity for that exchange.

### Schema coupling

The PostgreSQL/Supabase schema must not become the application's domain API by accident. Calls
equivalent to `supabase.from("table").select(...)` should not be spread through UI, application, or
domain code. Supabase-specific SDK types, table names, query syntax, realtime APIs, and other
infrastructure concerns stay inside the relevant capability's backend implementation as much as
reasonably possible — without over-engineering the boundary into something more elaborate than the
current small number of capabilities warrants.

### Authorization and RLS

Where clients access Supabase directly, Row Level Security (RLS) may provide the server-side
authorization boundary and is treated as part of the security architecture, not as disposable
implementation plumbing. If a capability later moves behind a custom API, that API must explicitly
own authorization for that capability itself:

```
Client → Custom API → server-side authorization → PostgreSQL
```

RLS may remain in place as defense-in-depth, but a future custom API must not accidentally depend
on undocumented RLS behavior as its primary authorization mechanism.

### Authentication

Authentication is treated separately from general data-access portability. The current candidate
direction is that Supabase Auth may remain the identity provider even after some data capabilities
migrate to a custom backend API — a future custom backend should be able to validate identities/
tokens originating from Supabase Auth where appropriate. Replacing Supabase Auth itself is a
separate, later migration concern, not something required merely because *a* custom backend API
exists. This ADR does not decide that Supabase Auth must remain forever, and does not design an
identity-provider migration.

### Supabase capabilities and lock-in

Relatively portable: PostgreSQL itself, standard relational data, and simple data-access behavior
hidden behind narrow capability boundaries — a custom backend would offer functionally the same
shape of capability, so the boundary's contract doesn't need to change even if its implementation
does.

Potentially more coupling-heavy: Supabase Auth, RLS tied strongly to Supabase's identity semantics,
Realtime, Edge Functions, Storage, and any schema-shaped direct client access. These are not
forbidden — adopting them is acceptable when their product value outweighs their migration cost.
The project is optimizing for pragmatic development, not zero vendor dependency.

## Why currently preferred

Building a full custom backend immediately would require the team to own substantially more
infrastructure and platform work before the product has concrete server-side requirements that
justify it. Supabase provides a faster initial route for common backend concerns (auth, basic
persistence), while PostgreSQL underneath and purpose-scoped capability boundaries around it
preserve a reasonable evolutionary path — individual concerns can move to a custom API later,
against the same database, as real requirements emerge (e.g. complex workout synchronization
semantics, server-side workflow/domain rules, advanced authorization, asynchronous processing,
complex analytics, external integrations, or operations that no longer map cleanly to simple
Supabase-backed data access). None of those requirements are asserted to exist yet.

## Alternatives considered

1. **Supabase-first with modular migration path (recommended)** — fastest initial route for
   auth/basic persistence; preserves an incremental, capability-by-capability migration path if
   Supabase's fit degrades for a specific concern; requires discipline to keep capability
   boundaries narrow so coupling doesn't spread.
2. **Custom backend API from day one** — maximum control and no vendor dependency from the start,
   but requires the team to build and operate infrastructure, auth, and data access before any
   concrete server-side requirement demands it; slows initial development for capabilities Supabase
   already provides adequately.
3. **Deep direct-client Supabase architecture with minimal separation** — fastest possible initial
   development, but lets the Supabase/PostgreSQL schema become the de facto application API and
   spreads Supabase SDK/query-shape dependencies through UI/domain code; makes any future migration
   (backend or auth) far more expensive because there is no boundary to migrate behind.

Option 1 is recommended: it gets the speed benefit of option 3 without its schema-coupling cost,
and avoids paying option 2's infrastructure cost before it's justified.

## Tradeoffs

- Faster initial backend availability (auth, persistence) in exchange for a real, if currently
  small, vendor dependency on Supabase.
- Capability boundaries add a small amount of indirection compared to calling Supabase directly,
  in exchange for keeping the Postgres/Supabase schema from becoming a de facto public API.
  contract.
- Keeping Supabase Auth as the identity provider even after a hypothetical partial migration to a
  custom backend avoids a much larger, harder migration, in exchange for a longer-lived dependency
  on Supabase for identity specifically.
- Allowing Supabase and a future custom API to coexist against the same PostgreSQL database enables
  incremental migration, in exchange for needing the ownership rule above to prevent two paths from
  mutating the same capability's state.

## Known risks

- **Supabase/vendor dependency**: pricing changes, service changes, or project-level limits are a
  business-continuity risk, not an architectural one, but are worth acknowledging.
- **Accidental client/schema coupling**: if capability boundaries are not enforced in practice
  (e.g. no rule against importing the Supabase SDK outside the data-access layer), the Postgres
  schema becomes the application's public contract by accident, undermining this ADR's central
  premise.
- **Authorization/RLS coupling**: if RLS becomes the *only* authorization enforcement point and
  that fact is undocumented, a future custom API for the same capability could silently omit
  authorization logic that RLS was implicitly providing.
- **Authentication migration cost**: replacing Supabase Auth itself (as opposed to replacing a data
  capability) is a materially larger effort, since RLS policies, session/token handling, and
  authorization assumptions elsewhere are typically built on top of whichever identity provider is
  active.
- **Over-abstracting for portability**: introducing a generic backend-provider abstraction or a
  repository-per-entity framework to make every capability theoretically swappable would itself be
  the kind of premature architecture this project is trying to avoid.
- **Multiple mutation paths to the same state**: if a capability is only partially migrated, or the
  ownership rule above is not respected, both Supabase and a custom API could independently apply
  business rules to the same data, reintroducing the multi-writer problem this project has
  otherwise avoided (phone/watch active-workout ownership).
- **Operational/pricing/data-residency considerations**: fitness/health-adjacent data draws
  increasing regulatory attention (e.g. GDPR for EU users, expanding US state-level health-data
  laws). Supabase project region and data-processing terms need due diligence before production use
  — this is a pre-production checklist item, not an architectural blocker now.

## Open questions

- Exact initial Supabase capabilities beyond PostgreSQL, Auth, and basic account-level persistence
  (e.g. whether Realtime or Storage are needed at all initially).
- Exact authorization/RLS policy design, which depends on concrete data categories that don't exist
  yet.

## Explicitly deferred

- Custom backend language/framework, if and when a custom API is built.
- Local client persistence technology (phone/watch).
- Detailed PostgreSQL schema.
- Synchronization protocol and conflict-resolution mechanism.
- Realtime/live-companion protocol (the future Hevy/Strong-like companion model discussed
  separately).
- Desktop technology.
- Concrete workout domain model.
- Identity-provider migration mechanism (if Supabase Auth is ever replaced).

## What would cause reconsideration

- Server-side domain complexity grows significantly beyond simple data access.
- Supabase-specific features begin shaping the domain architecture rather than just implementing
  it behind a boundary.
- Portability requirements become materially stronger than "pragmatic, not zero-dependency."
- Cost, scaling, or operational constraints change materially.
- Security or compliance requirements demand a different architecture.
- A custom API becomes clearly simpler than continuing to express a given capability through
  Supabase.
