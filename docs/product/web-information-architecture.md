# TBDFit Web Information Architecture

**STATUS: DESIGN PROPOSAL — FOR TEAM REVIEW**

This is a design proposal, not an ADR and not an accepted product decision. It synthesizes the
corrected [`web-product-ux-research.md`](web-product-ux-research.md) with TBDFit's existing
architecture ([ADR-0001](../architecture/adr/0001-native-client-architecture.md)'s native-client
direction, PD-001/PD-002's local-first execution model, `LocalAccount`-scoped ownership, and
[`product-information-architecture.md`](product-information-architecture.md)'s phone-side
conclusions) into a proposed web scope, navigation structure, and core journeys.

**This version replaces a prior version that was built on an incorrect benchmark** (TrainingPeaks/
Strava/Garmin/TrainHeroic/Boostcamp as primary evidence, rather than Hevy/Strong/Fitbod). See
[`web-product-ux-research.md`](web-product-ux-research.md) §0 for the correction notice. The
conclusions here are, honestly, less confident and less unanimous than the prior version's — that is
a direct consequence of grounding them in the correct, thinner primary evidence base, not an error.

Labels: **OBSERVED PRODUCT BEHAVIOR** (from the corrected research), **INFERENCE** (reasoned
conclusion), **TBDFIT RECOMMENDATION** (design judgment, not a decision), **OPEN DECISION** (requires
developer approval).

---

## 1. Candidate web information architectures

All three candidates are informed by [`web-product-ux-research.md`](web-product-ux-research.md) §11's
corrected finding: web execution is **`NOT VERIFIED`/inconclusive** across all three primary
comparators, not a proven-absent hard rule. The options below differ in how much they bet on
Hevy's specific (medium-confidence) plan/review/social pattern versus staying minimal versus
deliberately diverging from all three comparators.

### Option A — Account/minimal portal

```
Login → Account / Subscription / Settings
```

No training-adjacent content at all — mirrors Fitbod's confirmed (account/subscription) and Strong's
evidenced-absent web patterns.

- **Strengths**: cheapest to build and reason about; zero risk of building something with no real
  content behind it (the "empty destination" failure the phone IA's adversarial review already
  flagged once); matches 2 of 3 primary comparators' actual evidenced behavior.
- **Weaknesses**: no planning or review value at all; if TBDFit's own Routine/history value ever
  matches Hevy's, this option would need to be rebuilt, not extended, to capture it.
- **Scalability**: poor for the plan/review job specifically; fine for its own narrow job
  indefinitely.

### Option B — Planning + Review + light-social companion (Hevy pattern)

```
Sidebar (or equivalent desktop-native shape — see Open Decision #2):
  Plan     (Routine/Program building and editing)
  History  (workout list + detail, read)
  Profile  (own profile, edit)
  Feed     (view followed users' updates — if/when TBDFit social exists at all)
  Settings
```

Mirrors `hevy.com`'s own evidenced (medium-confidence) capability set: create/edit routines, review
history, edit profile, view feed. No execution.

- **Strengths**: the only option with a real, evidenced precedent from TBDFit's own closest primary
  mobile comparator; captures actual demonstrated user-facing value (routine building genuinely
  benefits from more screen space and a keyboard); leaves room to add a Progress/analytics surface
  later without restructuring, once TBDFit itself has enough history to make one meaningful (Hevy's
  own analytics were the one capability this pass could **not** confirm exists on its web surface —
  see the research document §3 — so this option deliberately excludes analytics from its initial
  scope rather than assuming it belongs there by precedent).
- **Weaknesses**: still a real, non-trivial second client to build; the precedent is medium-confidence
  and single-sourced (only Hevy, not a 3-for-3 pattern); a Feed item with nothing behind it (no
  TBDFit social feature exists yet) would itself be an empty-destination risk unless deferred until
  social exists on phone first.
- **Scalability**: good — directly extensible toward a Program/multi-week layer or an eventual
  analytics surface without a rewrite.

### Option C — Broad first-class client (potentially including execution)

```
Full parity ambition: Plan, Review, Execute, Social, Discovery — Web as a true peer to Phone/Watch
```

- **Strengths**: none of the three primary comparators constrain this option's ambition (since none
  of them fully closes the "does web execute" question — §11's `NOT VERIFIED` leaves it technically
  open); could be a genuine product differentiator if user research ever supports it.
- **Weaknesses**: not supported by any primary-comparator precedent; directly cuts against the
  device-specialization pattern every other research pass in this project has found; bets the most
  engineering effort on the least-validated assumption (web execution) in the entire multi-client
  research base.
- **Scalability**: unknown — no precedent exists to extrapolate scaling concerns from.

### Recommended direction

**RECOMMENDATION: Option B, held as a `CURRENT PRODUCT HYPOTHESIS`, not Accepted truth.** This is a
direct, honest downgrade from the prior version of this document, which recommended deferring even
Option B's shape entirely, and separately overstated its evidentiary basis as a 6-product unanimous
convergence. The corrected picture: Option B rests on real but single-sourced, medium-confidence
evidence (Hevy only, among the three primary comparators), which is enough to make it a reasonable
starting hypothesis but not enough to call it validated.

This remains **OPEN DECISION #1** below — a reasonable developer could instead pick Option A
(cheapest, matches 2 of 3 comparators, zero risk) or defer the whole question further, and the
evidence does not force a single answer.

---

## 2. Recommended navigation map (for if/when web is built)

```
┌───────────────────────────────────────────────────────────┐
│                      TBDFit (Web)                          │
├───────────┬─────────────────────────────────────────────┤
│ Sidebar   │  Content area                                │
│           │                                               │
│ Plan      │  Routine/Program building and editing          │
│ History   │  Completed-Workout list + detail (read)        │
│ Profile   │  Own profile, edit settings                    │
│ Feed      │  (deferred until TBDFit social exists at all   │
│           │   on any client — see Open Decision #6)        │
└───────────┴─────────────────────────────────────────────┘

Not a web destination, per the research's corrected finding (§11 — inconclusive, not proven-absent,
but not currently planned either):
  Active Workout / live set logging  → Phone/Watch, not planned on Web now — revisable, not foreclosed

Explicitly out of scope, deferred indefinitely:
  Coach-facing program authoring at scale (a separate, additive future capability — see the phone
    IA's own athlete/creator Open Decision)
  Multi-week Program / periodization (no evidence any of the three primary comparators support this
    on web either — the prior version's Program-layer emphasis came primarily from TrainingPeaks/
    TrainHeroic, both supplementary-tier under the correction)
  Analytics/charts as a launch-scope item (Hevy's own web analytics status is itself unconfirmed —
    see research §3 — so this is not even a validated pattern to copy yet)
```

The **sidebar shape itself** (as opposed to the specific item set above) is still reasonably
well-supported, but now primarily by the **supplementary** products (TrainingPeaks, Strava, Garmin
Connect, TrainHeroic all use this shape) rather than by Hevy/Strong/Fitbod specifically — this pass
found no direct evidence of `hevy.com`'s actual navigation shape (sidebar, top nav, or otherwise).
This is stated as **Open Decision #2**, not settled: the sidebar recommendation is a reasonable
desktop-UX default, not a Hevy-validated finding.

---

## 3. Core web journeys (conceptual — no backend commitment)

### Journey W1 — Build/edit a routine on web, execute it on Phone/Watch

```
Web: Plan → Routine Builder → save Routine
→ (sync/replication — mechanism undecided, PD-001)
→ Phone or Watch: Routine Library → Start → execute
```

- **Durable data**: a `Routine`/`RoutineExercise` entity (does not exist on any client yet — same
  entity the phone IA already identified as POST-MVP).
- **Ownership/privacy**: `LocalAccount`-equivalent ownership must resolve identically regardless of
  authoring client — this is the same account-scoping discipline already established on phone, now
  required across a genuinely new sync boundary.
- **Backend implications**: the concrete, realistic instance of PD-001's still-undecided sync
  mechanism.

### Journey W2 — Review history on web after phone-logged training

```
Phone/Watch: workouts executed and completed over time (Summary screen not yet built)
→ replication to backend (not yet built)
→ Web: History → browse, inspect detail
```

- **Durable data**: existing `Workout`/`WorkoutSet` rows — no new domain model required.
- **Backend implications**: this journey's real blocker is **not web-specific at all** — no backend
  replication of phone-originated Workout data exists yet, and no Workout Summary/completion screen
  exists on phone to produce reviewable history in the first place. This is arguably more load-bearing
  than any web-navigation question.

### Journey W3 — Manage profile / view feed on web

```
Web: Profile → edit settings
Web: Feed → view followed users' updates (deferred — see Open Decision #6)
```

- **Durable data**: existing account/profile concepts; Feed depends entirely on a TBDFit social
  feature existing on some client first, which it currently does not (phone IA's own Open Decision
  #5).
- **Backend implications**: minimal beyond what Phone-side social work would already require.

*(The prior version's Journey W2 "multi-week periodized planning" and Journey W4 "exercise library
management" are removed from this corrected version — neither is evidenced by the corrected
Hevy/Strong/Fitbod primary research; they were downstream of the incorrectly-weighted
TrainingPeaks/TrainHeroic evidence. A Program/multi-week layer remains a possible future concept,
tracked in Open Decision #5, but is no longer presented as a near-term web journey.)*

---

## 4. Relationship to the phone IA's ownership/visibility concepts

Unchanged from the prior version — none of this depended on the incorrect benchmark scope:

**`LocalAccount` does not need a literal web equivalent.** It exists specifically because Room (an
on-device SQLite database) cannot declare a foreign key against a remote `auth.users` row. Web has no
local durable-execution database to protect, so it can plausibly be backend-session-authoritative
directly. **Discovered simplification, not a decision.**

**Visibility (`PRIVATE / FOLLOWERS / PUBLIC`) is unaffected by web's existence.** It is a property of
content, not of which client authors or views it.

**Copy-creates-independent-object is unaffected.** If a web-facing "browse/copy other users'
published Routines" surface is ever built (FUTURE), the same rule applies: a copy produces a new,
independently-owned row for the copier, never a shared reference.

---

## 5. Timing and scope recommendation

**TBDFIT RECOMMENDATION**: hold Option B as the current hypothesis for **what** web would be if
built, while treating **when** as a genuinely open, separate question — do not collapse "we don't
know what to build" with "we shouldn't explore it at all."

Two distinct sub-questions, kept explicitly separate (this project has already chosen "prototype UI
first with hardcoded/local state → human evaluation → derive backend needs later" as its policy, and
that policy applies here too — the absence of a real `Routine` backend does not by itself mean a web
UX exploration has no value):

- **Production-backed Web client**: not justified yet. No `Routine`/`Program` domain model exists on
  any client, and no completed-workout history feature exists on Phone either — a production web
  client would be a planning tool with nothing real to plan and a review tool with nothing to review.
- **Web UX prototype (hardcoded content, no backend)**: plausibly justified *now*, alongside the
  Phone clickable prototype, specifically **because** Option B rests on real (if thin) evidence that
  is worth testing with actual TBDFit users before any backend commitment — the same reasoning the
  Phone-side prototyping policy already applies to Routine Library/History on Phone. A web prototype
  of the Plan/History/Profile shape above would let the team evaluate whether the Hevy-inspired
  pattern (§1 Option B) is actually wanted, at prototype cost, before deciding whether Option B is
  worth production engineering effort at all.

This is a meaningfully different recommendation from the prior version's blanket "defer web
entirely," which conflated "no backend exists" with "no UX exploration has value" — see the
multi-client vision document's own corrected treatment of this distinction.

---

## 6. Explicitly unresolved product decisions

1. **OPEN DECISION #1** — Option A vs. B vs. C, and whether even Option B's evidence (single-sourced
   from Hevy, medium confidence) is strong enough to build a prototype around at all.
2. **OPEN DECISION #2** — The sidebar shape itself is now primarily evidenced by supplementary
   products, not the primary three; whether it's still the right default for TBDFit specifically is
   not settled by this document.
3. **OPEN DECISION #3** — The cross-device sync/ownership mechanism (PD-001) that Journey W1 depends
   on remains exactly as undecided as before.
4. **OPEN DECISION #4** — Whether `LocalAccount` genuinely has no web equivalent, or whether some
   local-durability concern on web (e.g. offline draft editing) would reintroduce a similar need.
5. **OPEN DECISION #5** — Whether a multi-week Program concept, above Routine, is ever built at all —
   now explicitly **not** evidenced by the corrected primary-three research (it was previously
   over-weighted from TrainingPeaks/TrainHeroic); mirrors the phone IA's own athlete/creator Open
   Decision #5.
6. **OPEN DECISION #6** — Whether a web Feed item should exist at all before phone-side social exists
   in any form, or whether it should be removed from Option B's scope entirely until then.
7. **OPEN DECISION #7 (new)** — Whether web execution should remain merely "not currently planned" or
   be explicitly decided against — this document does not force that decision, consistent with the
   corrected research's `NOT VERIFIED`/inconclusive finding in §11 of the research document.

---

## Status

**STATUS: DESIGN PROPOSAL — FOR TEAM REVIEW.** Nothing in this document is implemented. No PD/ADR
status is changed. This document replaces and corrects the prior version's benchmark scope and
resulting overconfidence; its own headline recommendation (Option B, as a prototype-worthy hypothesis
rather than either an accepted plan or something to defer entirely) remains a recommendation for team
review, not a decision.
