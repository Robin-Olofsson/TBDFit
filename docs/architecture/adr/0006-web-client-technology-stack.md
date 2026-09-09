# ADR-006: Web Client Technology Stack

## Status

PROPOSED — FOR TEAM REVIEW

This has been reasoned through and is put forward as a candidate decision. It becomes Accepted
only after the second developer has reviewed it and any material concerns have been resolved. The
current Web prototype's stack (React 19, TypeScript, Vite, React Router, plain CSS,
`@supabase/supabase-js`) was a reversible implementation choice made to build a UX prototype
quickly — it was never itself put forward as an architecture decision until now.

## Context

TBDFit Web began as a no-backend UX prototype (see
[`web-information-architecture.md`](../../product/web-information-architecture.md)): a handful of
prototype screens (Plan, Routine Detail, History, Workout Detail, Profile) plus one genuinely
production-backed capability, real Supabase authentication (`/login`, session restoration,
sign-up/confirmation, sign-out — see `docs/product/frontend-prototype-notes.md`). That prototype
picked React 19 + TypeScript + Vite + React Router + plain CSS as "the smallest reversible choice"
for a stack with zero backend requirements at the time.

That premise no longer fully holds. Real authentication now exists, `/login` has been designed as
a real product surface against an approved visual reference, and
[`multi-client-product-vision.md`](../multi-client-product-vision.md) and
[ADR-005](0005-multi-client-responsibility-strategy.md) treat Web as a plausible first-class client
of the same TBDFit product, alongside Phone and Watch. Before materially more Web implementation
accumulates on an unreviewed technology choice, this ADR evaluates whether Vite remains the right
foundation or whether a framework with built-in server-rendering capability (Next.js, or another
option) should be adopted now, while the codebase is still small.

This ADR does **not** decide:

- Whether TBDFit Web ever supports live workout execution (remains open per
  [ADR-005](0005-multi-client-responsibility-strategy.md) and
  [`web-product-ux-research.md`](../../product/web-product-ux-research.md) §11 — that question is
  independent of which framework renders the pages).
- Whether/when a production Web client is actually built at all (remains
  [`web-information-architecture.md`](../../product/web-information-architecture.md)'s own
  recommendation — not yet, though the prototype has already gone further than "no backend at
  all" by way of real auth).
- Any specific navigation, screen, or CSS/styling decision.

## Decision Drivers

Derived from TBDFit's current product direction, not from what happens to already be installed:

1. **TBDFit Web is a substantial authenticated application, not a marketing site.** Planning/
   routines, workout history, progress, account/profile, and possibly social/discovery are all
   expected authenticated-application surfaces (see
   [`web-information-architecture.md`](../../product/web-information-architecture.md)).
2. **Public/shareable content is a plausible future requirement, not a committed one.** Athlete
   profiles, published workouts/routines/programs, creator pages, and share links are named as
   future hypotheses in the corrected research — with real SEO/metadata/link-preview/SSR/SSG
   implications *if* they materialize.
3. **Highly interactive UX.** A routine builder (drag/reorder, inline set/rep editing), and
   eventually analytics/progress visualization, need a stack that doesn't make client-side
   interaction awkward.
4. **Supabase compatibility.** Browser auth, RLS-protected authenticated data access, and (only if
   a genuine need arises) server-side access using credentials that must never reach the browser —
   see [ADR-004](0004-initial-backend-platform-and-migration-strategy.md)'s capability-boundary and
   authorization principles, which this ADR must not contradict.
5. **No shared UI/core architecture with native clients.** Per
   [ADR-0001](0001-native-client-architecture.md) and [ADR-005](0005-multi-client-responsibility-strategy.md):
   same product, device-specialized clients. Choosing a Web framework must not imply or invite a
   cross-platform UI layer shared with Phone/Watch/Apple.
6. **Developer complexity proportionate to actual need.** Local dev experience, build complexity,
   deployment, debugging, onboarding, framework "magic," and upgrade burden all matter for a
   two-developer team — architecture for hypothetical complexity with no current product value is
   exactly what this project's own CLAUDE.md guidance warns against.
7. **Migration cost is lower now than later.** The current Web codebase (see Migration Impact
   below) is still small; this is deliberately being decided before that stops being true.

## Options Considered

### Option A — React + TypeScript + Vite (current)

The prototype's actual stack today: React 19.2.8, TypeScript ~6.0.2, Vite 8.2.2,
react-router-dom 7.18.3 (client-side `BrowserRouter` mode), plain CSS, `@supabase/supabase-js`
2.116.0, oxlint + vitest.

**Strengths (verified against the real codebase, not assumed):** minimal configuration
(`vite.config.ts` is 6 lines); very fast local dev (Vite's native ESM dev server, no bundling in
dev); an explicit, easy-to-reason-about client architecture — every page is a plain component,
data flow is visible, nothing runs on a server the team doesn't otherwise have; trivial static
deployment (any static host/CDN); low framework-upgrade surface (Vite major-version bumps are
comparatively shallow compared to a full-stack framework's).

**Honest weaknesses:** no built-in SSR/SSG — a future public/shareable page would need either a
separate static-site tool, a thin custom prerender step, or a framework migration; no built-in
metadata/OpenGraph conventions (would be hand-rolled per page); no server-side data-loading
convention (not currently a problem, since there is no data to load server-side yet); no
integrated place to put a future server-only Supabase call other than standing up a separate
minimal server, which is exactly the kind of infrastructure ADR-004 says shouldn't be built before
a concrete requirement demands it.

A Vite SPA is not weak at being a capable authenticated product — the real `/login` flow already
proves this stack can carry production-grade auth UX. Its actual gap is specifically
public-content rendering, which does not exist as a requirement yet.

### Option B — React + TypeScript + Next.js

Evaluated on concrete TBDFit requirements, not popularity. Next.js (App Router) would provide,
out of the box: server components and SSR/SSG per-route; a built-in metadata API (`generateMetadata`)
for OpenGraph/Twitter-card previews on any future public page; file-based routing; a place to put a
genuinely server-only Supabase call (e.g. a service-role read used to build a public share page)
behind a server component or route handler, without exposing it to the browser; and a mature,
well-documented deployment story that is not limited to one vendor (see Deployment Comparison).

**What would concretely justify it:** a committed requirement for at least one public,
search-indexable, link-preview-able page (a published workout, an athlete profile, a share link).
None of these are committed yet — they are named as future hypotheses in
[`web-information-architecture.md`](../../product/web-information-architecture.md), not accepted
scope.

**Honest costs:** materially more framework surface to learn/maintain (server vs. client
component boundaries, the App Router's caching model, which has changed meaningfully across
Next.js major versions); a real risk of framework coupling — it becomes easy to let Next.js route
handlers start owning business logic that belongs in TBDFit's actual backend (Supabase today, a
future custom API per ADR-004), which this ADR explicitly warns against (see Backend/BFF
Implications below); more moving parts for a two-developer team to operate day to day for a
product that, right now, has zero pages that need any of this.

Next.js is not rejected because it's a bad framework — it is not yet justified because TBDFit has
no requirement today that a static SPA cannot serve.

### Option C — Alternative: React Router v7 "framework mode"

Chosen as the single alternative worth serious comparison specifically *because* TBDFit already
depends on `react-router-dom` v7.18.3 for Option A. React Router v7 merged Remix's framework
capabilities into itself: the same library already in `package.json` can be run in a "framework
mode" that adds file-based routes, server-side data loading (`loader`/`action` functions per
route), and SSR — without changing UI libraries, and with a substantially smaller migration
surface than adopting Next.js, since routing/data-loading primitives would extend the existing
dependency rather than replace it with a different framework's conventions.

**Why it doesn't yet win over staying on Option A:** it still requires standing up a Node
server-rendering runtime and adopting `loader`/`action` conventions the current prototype doesn't
use anywhere — real infrastructure and a real convention shift for zero currently-committed public
pages, the same objection that applies to Option B, just with a shorter migration path if the team
ever needs it. It is recorded here specifically so that "Vite vs. Next.js" is not framed as the
only decision space — if server-rendering capability is ever needed, upgrading react-router's own
mode is a materially cheaper path than migrating to Next.js, and should be evaluated first when
that day comes.

**Other alternatives considered and set aside without full write-ups:** Astro+React (excellent for
content-heavy/mostly-static sites; TBDFit Web's actual known-needed surface is an authenticated
app, not a content site, so Astro's island-architecture strength is not where TBDFit's real need
is); SvelteKit/Nuxt (would mean leaving React entirely — see React Assessment below for why that
is not justified by anything in this decision).

## Decision

> TBDFit Web remains on **React + TypeScript + Vite + React Router (client-side SPA mode)** as its
> permanent stack for the current phase of the product, with **React Router v7's own "framework
> mode"** (not Next.js) identified as the preferred first upgrade path *if and when* a concrete
> requirement for server rendering or public/shareable content actually materializes.

This affirms the existing prototype choice as a real, reviewed architecture decision rather than
leaving it as an unexamined accident of "it's already there" — the review in this ADR concluded it
independently, using the requirements above, not by default.

## Rationale

- No TBDFit requirement today needs SSR/SSG, metadata generation, or a server runtime — every
  currently real Web capability (auth, the prototype screens) is client-rendered and
  authentication-gated, which Vite/React Router already serves well, proven by the real `/login`
  implementation.
- The public/shareable-content driver that would justify Next.js is a named future hypothesis, not
  committed scope — [`web-information-architecture.md`](../../product/web-information-architecture.md)
  itself has not moved a production Web client (let alone a public one) past "not yet."
- Migrating later, if that hypothesis is validated, is a bounded, well-understood cost (see
  Migration Impact) — smaller than the ongoing cost of running Next.js's larger framework surface
  today for capabilities that don't exist.
- React Router v7's framework mode being available as a lower-cost intermediate step means "stay
  on Vite" does not foreclose SSR forever; it defers a real decision to when real evidence exists,
  consistent with [ADR-004](0004-initial-backend-platform-and-migration-strategy.md)'s own
  "don't build infrastructure before a concrete requirement demands it" principle applied to
  Supabase.
- Keeping the client stack simple also keeps the ADR-0001/ADR-005 boundary clean: a Vite SPA has no
  server runtime tempting business logic to live inside the Web project instead of behind TBDFit's
  actual backend boundary.

## Consequences

- The current `/login`, prototype screens, and their CSS/component structure are unaffected — this
  ADR ratifies, not changes, what already exists.
- Any future page requiring SEO/social-preview/indexing must either be built as a deliberately
  separate, small static/prerendered surface (e.g. a handful of hand-rolled static share pages) or
  trigger the revisit process below — it should not be quietly hacked around inside the SPA (e.g.
  client-side-injected `<meta>` tags do not satisfy real crawler/link-preview requirements).
- Engineering effort for any genuinely server-only Supabase operation (if one is ever needed) must
  go through a real, separately-justified minimal server component — not be smuggled into the SPA
  via an exposed privileged key, and not used to justify adopting Next.js purely to get "a server."
- This decision does not block or authorize building more Web product surface — that remains
  [`web-information-architecture.md`](../../product/web-information-architecture.md)'s call, per
  [ADR-005](0005-multi-client-responsibility-strategy.md).

## Security / Supabase Boundary

- **Browser:** the existing, correct pattern continues — `VITE_SUPABASE_URL` +
  `VITE_SUPABASE_ANON_KEY` (the same publishable, client-safe pair Android already uses via
  `local.properties`/`BuildConfig`), RLS as the actual authorization boundary (per
  [ADR-004](0004-initial-backend-platform-and-migration-strategy.md)), one Supabase Auth user
  identity shared across every client. No Web-specific identity semantics, no second user
  identifier, no `LocalAccount`-equivalent on Web (Web has no durable offline store to scope
  ownership for — see the earlier Web-auth task's explicit reasoning, still valid).
- **Server (not currently applicable):** a service-role key or any other privileged credential
  must never be introduced into `web/` while it remains a pure client-side SPA — there is no server
  runtime to hold it safely. If Option C/B is ever adopted specifically to enable a server-only
  Supabase call, that call must live in a route/loader that never ships its credential to the
  client bundle, and must still respect the ownership rule from ADR-004 (one authoritative mutation
  path per capability).
- This ADR changes nothing about Supabase's role — it only constrains where a privileged
  credential would be allowed to live if the rendering model ever changes.

## Migration Impact

Assessed against the actual current `web/` codebase (11 source files under `web/src/`, one route
tree, one auth context, no data-fetching library, no nested/dynamic routing beyond two `:id`-style
params):

**Reusable largely unchanged if migrating to Next.js or React Router framework mode later:**
component logic (`AuthForm`, `AuthHero`, `ProductPreview`, the five prototype page components) —
these are plain React components with no Vite-specific API usage; all CSS in `index.css`; the
Supabase client construction and every `AuthContext`/`authErrors`/`authPhase` function (pure
TypeScript, framework-agnostic).

**Would need rewriting:** the route tree itself (`App.tsx`'s `<Routes>`/`<Route>` JSX would become
file-based routes in either target); the entry point (`main.tsx`'s `createRoot`/`BrowserRouter`
wiring has no equivalent in Next.js and a different shape in React Router framework mode);
environment-variable prefix and access pattern (`import.meta.env.VITE_*` → `process.env.NEXT_PUBLIC_*`
for Next.js, unchanged for React Router framework mode since it still runs on Vite); build/deploy
configuration (`vite build` + static host → `next build` + a Node/edge/static target, or
`react-router build` for framework mode).

**Assessment: migrating now would be CHEAP.** The codebase is small enough (roughly a dozen files,
no deep routing, no data-fetching conventions to unwind) that a same-day-to-few-days migration is
realistic today. This cost grows with every additional prototype screen, every additional page
that accumulates Vite-specific assumptions, and especially once real backend-integrated Routine/
History features exist with their own data-fetching patterns to rewrite. This is itself part of
why the team should decide *now*, deliberately, rather than let the choice ossify by inertia.

## Alternatives Rejected

1. **Next.js now, on the theory that public pages are coming eventually.** Rejected: no public
   page is committed scope; adopting a framework's full server-rendering surface for capabilities
   that don't exist is exactly the premature-infrastructure pattern
   [ADR-004](0004-initial-backend-platform-and-migration-strategy.md) already rejected for the
   backend, applied here to the frontend.
2. **Astro+React.** Rejected: Astro's strength (mostly-static, content-heavy sites with islands of
   interactivity) does not match TBDFit Web's actual known need (a fully authenticated,
   interactive application) — it would be a better fit for a hypothetical separate marketing site,
   which is not what `web/` currently is or needs to become.
3. **SvelteKit / Nuxt (leaving React).** Rejected: see React Assessment — no requirement in this
   decision argues against React itself, and leaving it would forfeit real, already-built
   investment (the prototype, `/login`, auth integration) for no identified product benefit.
4. **A separate, second Web project/framework specifically for future public pages, kept apart
   from the authenticated app.** Considered as a way to get SSR benefits without touching the
   authenticated SPA. Not rejected outright — noted as a plausible shape *if* public content
   becomes real before the team wants a full framework migration (a small static-site generator
   for share pages, separate from the authenticated app) — but not adopted now since no public
   page exists to build.

## Revisit Triggers

Concrete, tied to this project's actual open questions — not generic "reconsider periodically"
language:

- **A public, indexable, share-preview-needing page becomes committed near-term scope** (e.g. a
  published workout or athlete profile moves from
  [`web-information-architecture.md`](../../product/web-information-architecture.md)'s open-question
  status to an accepted design) — revisit toward React Router framework mode first, Next.js only
  if framework-mode's capabilities prove insufficient for the specific requirement.
- **The Web UX prototype is human-evaluated and the Planning+Review hypothesis is rejected or
  Web is deprioritized entirely** — this ADR's decision is reconfirmed as correct by default; no
  action needed, but the "revisit later" clock effectively resets.
- **Real backend-integrated Routine/History data-fetching accumulates enough custom
  loading/caching logic that it starts to resemble a hand-rolled version of what a framework's
  data layer would give for free** — that pattern, if it emerges, is itself evidence worth
  bringing back to this ADR, independent of whether public pages ever materialize.
- **A genuine need for a server-only privileged Supabase operation appears** (not hypothetical —
  an actual feature requiring it) — revisit the Security/Supabase Boundary section specifically,
  even if the rendering-model question stays closed.
- **Migration cost stops being cheap** (roughly: once `web/` exceeds ~30-40 source files, or once
  more than 2-3 screens have real backend data-fetching) — if none of the above triggers have fired
  by then, that itself is evidence the current choice was correct and should be explicitly
  reconfirmed rather than silently left alone.

## Relationship to Existing ADRs

- **Extends, does not replace, [ADR-0001](0001-native-client-architecture.md)**: ADR-0001 decided
  native-per-ecosystem technology for Phone/Watch/Apple and explicitly deferred "desktop
  technology"; this ADR makes that deferred choice for Web specifically, without altering
  ADR-0001's no-shared-UI/core principle — Web's React/TypeScript choice is not, and must not
  become, a shared framework with any native client.
- **Operates within [ADR-005](0005-multi-client-responsibility-strategy.md)**: ADR-005 decided
  that Web (if built) is a first-class client with its own primary responsibilities, not required
  to match Phone/Watch feature-for-feature; this ADR decides *how* Web is built, not *whether* or
  *what* — it does not reopen ADR-005's deliberately-undecided items (whether/when Web ships,
  whether it ever executes a workout).
- **Applies [ADR-004](0004-initial-backend-platform-and-migration-strategy.md)'s principles to the
  frontend**: the same "don't build infrastructure before a concrete requirement demands it" and
  "one authoritative mutation path per capability" reasoning ADR-004 applied to backend platform
  choice is applied here to frontend rendering-model choice and to any future server-side Supabase
  access.
- **Does not decide, and is not blocked by**: CSS/styling strategy (remains a separate, reversible
  implementation decision — plain CSS today, changeable independently of this ADR); state
  management (no global state library is mandated; the current prototype's needs — one auth
  context, local component state in the routine builder — do not justify one, and one should be
  introduced only when actual state complexity requires it, independently of this ADR); exact
  Web IA/navigation/screens (remains
  [`web-information-architecture.md`](../../product/web-information-architecture.md)'s domain).

## Deliberately Undecided

- Whether/when a production Web client beyond the current auth-gated prototype is actually built.
- CSS/styling technology (plain CSS today; remains reversible).
- State management library (none mandated; introduce only when justified).
- The exact shape of a future server-only Supabase access path, if one is ever needed.
- Testing strategy beyond what already exists (`vitest` unit tests) — component/e2e testing
  tooling is not decided here.

## Competitor Stack Evidence (Supporting Context Only)

Narrow, targeted research on TBDFit's three primary mobile comparators' *web* technology
specifically — evidence-tiered per this project's established discipline. **Competitor stack
choice is supporting context, not a decision driver by itself** — none of the following changed
this ADR's decision; they are recorded because they were investigated.

- **Hevy**: `TECHNICAL FINGERPRINT / THIRD-PARTY DETECTION` only (RocketReach/Crunchbase-style
  technology-detection aggregators, not an official engineering source) — signals suggest
  React/React Native for the product, with Next.js, Node.js/Express, and PostgreSQL named
  elsewhere in the same aggregated profile for web/backend. `NOT VERIFIED` against any official
  Hevy engineering source.
- **Strong**: `Strong does not currently have an official web app` is stated directly on Strong's
  own official Help Center (a web app is listed there as a *future*, not current, feature) — this
  is closer to `VERIFIED` (official first-party source) for the *existence* question, though it
  says nothing about what technology a future Strong web app would use. Consistent with, and
  independent confirmation of, this session's earlier corrected web-benchmark finding.
  `NOT VERIFIED` for any specific technology, since none exists to detect.
- **Fitbod**: only generic, low-signal infrastructure aggregator data was found (Docker, Ruby, EC2,
  ad-tracking tags) with no reliable indication of frontend framework specifically. `NOT VERIFIED`.

No competitor's evidence — official or fingerprinted — argues for or against any option in this
ADR. This section exists purely as recorded supporting context, per the task's explicit
instruction not to let it drive the decision.
