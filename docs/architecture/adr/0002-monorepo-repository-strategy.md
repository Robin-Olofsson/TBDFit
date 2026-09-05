# ADR-002: Monorepo Repository Strategy

## Status

PROPOSED — FOR TEAM REVIEW

This has been reasoned through and is put forward as a candidate decision. It becomes Accepted
only after the second developer has reviewed the initial architecture baseline and any material
concerns have been resolved.

## Context

The product requires at least two native client ecosystems (Apple: iOS + watchOS, Android:
phone + Wear OS) and, eventually, a backend whose technology has not yet been selected. The team
is currently small. Architecture decisions need a single, reviewable, shared home (see ADR
practice generally) rather than being duplicated or scattered across repositories.

A backend, once selected, may also serve as a shared synchronization and data-access boundary for
additional future clients — for example, a possible future desktop application for planning,
history review, and analytics. This is one reason a backend may eventually become necessary, but
backend architecture and timing remain deliberately undecided by this ADR.

## Decision

A single monorepo houses the project:

```
/
  apple/
  android/
  supabase/
    migrations/
  docs/
    architecture/
      adr/
  README.md
  .gitignore
```

`backend/` is intentionally **not** created yet. It will be added once a backend *service*
technology is actually selected — creating an empty placeholder now would imply a decision that
has not been made. `supabase/migrations/` is not that placeholder: it holds only the shared
Supabase/PostgreSQL schema definition (see ADR-004), platform-neutral and consumed by every
client, not a backend service of its own.

## Alternatives considered and rejected

- **Separate repositories per platform** (Apple, Android, backend) — rejected for now: clean CI
  isolation is a real benefit, but at the current team size it is outweighed by the cost of
  coordinating cross-cutting changes (e.g. an API contract change and its client consumers) across
  multiple repositories with no atomic guarantee, and by the lack of a natural single home for
  shared ADRs/docs.
- **Polyrepo plus a separate documentation/architecture hub repository** — rejected as strictly
  worse than the monorepo for solving the same docs-ownership problem, without buying any of
  polyrepo's CI-isolation benefit that isn't already available via path-scoped CI in a monorepo.

## Consequences

- CI, once introduced, will need path-scoped triggers so platform-only changes don't run unrelated
  pipelines.
- ADRs and architecture documentation have one unambiguous home.
- Splitting this monorepo later (e.g. via `git subtree split` / `git filter-repo`) remains a cheap,
  well-understood migration if a concrete trigger appears (a dedicated backend team, an
  independent backend release cadence). Merging already-split repositories back together is not
  similarly cheap — this asymmetry is why the monorepo is the starting point.

## Deliberately undecided

- The specific trigger conditions for a future repository split.
- Whether/when `backend/` is added, and what it will contain — deferred to backend selection.
- Whether/when additional client surfaces beyond the current four (e.g. a future desktop
  application) are added, and how they would consume a future backend.
