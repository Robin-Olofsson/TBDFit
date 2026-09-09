# ADR-005: Multi-Client Responsibility Strategy

## Status

PROPOSED — FOR TEAM REVIEW

This has been reasoned through and is put forward as a candidate decision. It becomes Accepted only
after the second developer has reviewed it and any material concerns have been resolved.

## Context

[ADR-0001](0001-native-client-architecture.md) decided the *implementation technology* for TBDFit's
clients: fully native per ecosystem, no shared cross-platform UI/core code. It deliberately left
open "whether additional client surfaces (for example, a possible future desktop application...)
will exist, and what architecture they would use if so" — that sentence named a possible future
client as an example, not a decision about how it would relate to the responsibilities of the
clients that already exist.

Since then, four independent research/design passes have been produced:
[`frontend-product-ux-research.md`](../../product/frontend-product-ux-research.md),
[`product-information-architecture.md`](../../product/product-information-architecture.md) (the
latter already adversarially reviewed once), and a web research pass — corrected once already, see
[`web-product-ux-research.md`](../../product/web-product-ux-research.md) §0 — comprising
[`web-product-ux-research.md`](../../product/web-product-ux-research.md) and
[`web-information-architecture.md`](../../product/web-information-architecture.md). Synthesized in
[`multi-client-product-vision.md`](../multi-client-product-vision.md), the *phone/watch* evidence
converges cleanly: every competitor examined that serves this product category across more than one
device type assigns each device a different primary job. The corrected *web* evidence is weaker and
more mixed than that — among TBDFit's own three primary mobile comparators (Hevy, Strong, Fitbod),
only one (Hevy) shows a real, medium-confidence web pattern at all, and even there, whether execution
belongs on web is genuinely inconclusive, not a settled "never." Phone/Watch execution authority is
already governed in direction by
[PD-001](../../product/decisions.md#pd-001-independent-watch-workout-execution) and
[PD-002](../../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch); this ADR
generalizes the same underlying principle — device-specialized responsibility, not required feature
parity — into a durable statement that governs *any* current or future client, including a Web
client if one is ever built. **This ADR does not itself decide, and does not depend on, whether Web
ever executes a workout** — see the Decision section below, narrowed accordingly.

This decision does not depend on Web being built soon. It is equally about the Phone/Watch
relationship that already exists today.

## Decision

> TBDFit treats each native client (Phone, Watch, and any future client such as Web or Apple) as a
> first-class client of one shared product, while intentionally assigning each a different primary
> user responsibility rather than requiring feature parity across clients.

Consequences of this framing:

- No client is required to implement every product capability. A capability may be PRIMARY on one
  client, a SECONDARY supported capability on another, and NOT PLANNED on a third — this is a
  deliberate design outcome, not a temporary gap to be closed by "catching up" each client to
  feature parity.
- No client holds permanent, exclusive authority over a capability just because it is that
  capability's primary home elsewhere. Where a capability is legitimately useful on more than one
  client (e.g. starting a workout, which already exists on Phone and is intended for Watch), each
  client implements it independently, with its own purpose-built UI — never as a shared/responsive
  layer, and never with one device treated as permanently authoritative over another (this restates
  PD-001's own "the phone cannot be assumed to always be the authoritative writer," generalized
  beyond Phone/Watch specifically).
- A client's absence of a capability is not itself a defect. If Web does not support live workout
  execution, that is not automatically a gap to close — it may simply be Web correctly not attempting
  a job that belongs elsewhere. **This ADR does not decide that Web never executes a workout** — the
  evidence for that specific claim is inconclusive, not unanimous (see Context) — it only states that
  *if* a capability's primary home turns out to be elsewhere, that is an acceptable, non-defective
  outcome, not a decision this ADR makes on Web's behalf in advance.
- This decision governs *responsibility allocation*, not navigation, screens, or the specific
  capabilities named in current research documents. Those remain in product/IA documents
  ([`product-information-architecture.md`](../../product/product-information-architecture.md),
  [`web-information-architecture.md`](../../product/web-information-architecture.md)) and may change
  without reopening this ADR, the same way ADR-0001 already separates technology choice from screen
  design.

## Alternatives considered

1. **Feature-parity mandate** — every client eventually implements every capability. Rejected: the
   Phone/Watch evidence is strong and consistent against this (it would pressure Watch toward a full
   browsing/social/planning UI that every competitor examined avoids); the Web evidence is weaker but
   still does not support parity as a mandate — no primary comparator (Hevy, Strong, Fitbod) treats
   its web surface, where one exists at all, as a full peer of its mobile app.
2. **Phone-primary, other clients as accessories** — treat Phone as the "real" client and Watch/Web
   as reduced companions. Rejected: this directly contradicts PD-001's already-decided direction that
   Watch is a first-class, independent execution client, not a phone accessory, and would misdescribe
   Web's actual evidenced job (large-screen planning/analytics is not an accessory function, it is a
   job Phone genuinely does worse).
3. **No explicit policy — decide per capability, per client, ad hoc** — rejected as the status quo
   this ADR is meant to replace: without a stated principle, each future capability's client
   placement would be re-litigated from scratch, and "should this exist on Watch/Web" would lack a
   consistent, evidence-grounded default to check against.

Option in the Decision section above is recommended: it is the only one consistent with both
PD-001's already-accepted direction and the newly-gathered cross-client research, and it imposes no
new engineering cost — it is a statement about how to reason about future capability placement, not
a new abstraction or shared component.

## Consequences

- Future feature proposals should state which client(s) a capability is being built for and why,
  rather than defaulting to "every client eventually."
- A capability existing on multiple clients simultaneously (e.g. start-workout on both Phone and
  Watch) is expected and normal, not a sign of undecided ownership — as long as no client is treated
  as permanently authoritative and each implementation is independently correct per PD-001/PD-002's
  existing consistency guarantees (e.g. the per-owner single-active-workout invariant already
  implemented in `WorkoutDao.startWorkoutIfNoneActive` must hold regardless of which client
  initiates the call).
- This ADR does not, by itself, authorize building a production Web client —
  [`web-information-architecture.md`](../../product/web-information-architecture.md)'s own
  recommendation (a production client is not yet justified, though a no-backend UX prototype may be
  worthwhile) stands independently of this decision.
- Whoever eventually designs the cross-device sync/ownership mechanism (still undecided per PD-001)
  must satisfy this ADR's "no permanent authority" consequence, in addition to PD-001's own already-
  accepted list of constraints.

## Risks

- **Risk of use as a license for permanent gaps.** "Different primary responsibility, not parity"
  could be misused to justify never building a genuinely needed capability on a client where it
  would help, by mislabeling it "not this client's job." Mitigation: capability placement should
  still be revisited when real usage evidence contradicts the current placement (the same discipline
  `product-information-architecture.md`'s own Open Decision #7 already applies to Progress-tab
  promotion) — this ADR states a strong evidence-backed default, not an unreviewable rule.
- **Risk of premature scope for an unbuilt client.** Naming Web's eventual responsibilities in
  detail (as `multi-client-product-vision.md` §3 does) before Web is ever built risks those details
  being treated as more settled than they are. Mitigation: this ADR itself commits to nothing about
  Web's timing or existence — only to the responsibility-allocation principle that would apply *if*
  Web is ever built.
- **Risk of the shared-capability consistency guarantee being harder than it sounds.** "Any client
  may act, none holds permanent authority" is easy to state and genuinely hard to implement
  correctly across a real network partition or delayed-sync scenario — this ADR does not solve that;
  it only commits to the requirement, leaving the mechanism to the still-undecided sync/ownership
  design PD-001 already flags as open.

## Relationship to existing material

- **Extends, does not replace, [ADR-0001](0001-native-client-architecture.md)**: ADR-0001 decided
  *how* clients are built (native, no shared code); this ADR decides *what each client is for*
  relative to the others. Both can be true simultaneously without conflict.
- **Generalizes [PD-001](../../product/decisions.md#pd-001-independent-watch-workout-execution) and
  [PD-002](../../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch)**: PD-001
  already established "no permanent Phone authority" and "Watch can be first-class" specifically for
  execution; this ADR states the same underlying principle (device-specialized responsibility, no
  permanent authority) as a general policy covering any capability and any client, not execution
  alone.
- **Does not decide, and is not blocked by**: the sync/ownership mechanism (remains PD-001's open
  item), any navigation or screen design (remains in `product-information-architecture.md` /
  `web-information-architecture.md`), or whether/when Web is actually built (remains
  `web-information-architecture.md`'s own recommendation — a production client, currently: not yet;
  a UX prototype, currently: plausibly worthwhile).

## Deliberately undecided

- The cross-device sync/ownership mechanism itself.
- Whether/when a Web client is built at all.
- Exact per-capability client placement beyond what current product/IA research already proposes —
  future capabilities should be evaluated against this ADR's principle, not against an exhaustive
  list fixed here.
- Apple-ecosystem-specific consequences — this ADR's principle is ecosystem-agnostic, but no
  Apple-specific research exists yet to populate it the way Phone/Watch/Web research does.
