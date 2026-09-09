# Competitor Benchmark — Hevy, Strong, Fitbod

**STATUS: RESEARCH — FOR TEAM REVIEW**

This is a research document, not an ADR and not a product decision. It records official product
facts and separately-researched user-feedback patterns for three competitor workout-logging apps,
for the two human developers to weigh when shaping TBDFit's early scope. Nothing in this document
is an accepted decision. Where a recommendation appears, it is architectural/product *judgment*
based on the evidence gathered, not a decision — see [`docs/product/decisions.md`](decisions.md)
for what is actually decided.

Every finding is labeled per the classification vocabulary below. **Official product facts and
user sentiment are never merged into one claim** — a finding is always clearly one or the other.

**This document has been through one evidence-hygiene revision** (see the Status section at the
bottom). The original pass over-stated how strong some user-sentiment conclusions were, given how
little of the primary review/community landscape could actually be reached. This revision does not
add new sources — it re-labels existing findings so the document never expresses more confidence
in user sentiment than the underlying evidence supports.

---

## Evidence quality and limitations

Read this before any other section.

- **Official feature behavior** (Part 1) is primarily supported by vendor documentation — help
  centers, official websites, and store listing text. This is comparatively solid: it is what each
  vendor states its product does, though it is not independent verification that the feature works
  exactly as described in practice.
- **Community/user sentiment** (Part 2 onward) was materially harder to verify directly. In this
  research environment, direct `WebFetch` access was blocked (HTTP 403 or fully refused) for
  `reddit.com` in every form, the Apple App Store, Google Play's review body, Trustpilot,
  JustUseApp, several vendor community pages, and most independent blogs
  (`hevyapp.com/support`, `strong.app`, `fitbod.me`, `help.fitbod.me` among them).
  **No primary user review or discussion text was directly read in this research pass.** Every
  Layer-2 finding instead comes from `WebSearch`'s own indexed snippets and summaries of those
  sources — a synthesis of a synthesis, in several cases. A number of recurring sources
  (`sensai.fit`, `aitoolsbakery.com`, `repreturn.com`, `trysuperset.app`, `setgraph.app`,
  `dr-muscle.com`) are themselves **content-marketing sites run by competing workout-tracker apps**,
  carrying an incentive to frame these three products unfavorably.
- Because primary sources were unreachable, **blocked access reduces confidence in every Layer-2
  finding below**, not just the ones this revision flags individually.
- **User-feedback conclusions should therefore be treated as directional, not quantitative or
  representative.** No percentage, population share, or "most users"/"users want X" claim should be
  inferred from anything in this document — none is stated, and none should be added later without
  independently gathered primary evidence.
- Genuine primary-source spot-checking (actually reading a sample of real App Store/Play Store
  reviews and Reddit threads) remains a worthwhile follow-up before any Layer-2 finding here is
  treated as load-bearing for a scope decision.

### Absence of complaints is not evidence of satisfaction

Given how much of the primary landscape was unreachable, **the fact that this research did not find
a complaint about something is not positive evidence that it is loved**, let alone "universally
liked" or "best feedback." It may simply mean this research could not see that part of the
conversation. Wherever a feature is described positively below, this document distinguishes:

- an **official competitor pattern** (the vendor built and ships it) — Layer 1, FACT;
- a **secondary-synthesis positive characterization** (review/comparison sites describe it
  favorably) — Layer 2, weighted per the evidence tier below;
- **direct positive user evidence** (a real review or discussion actually read) — this research
  pass has none of this tier; flagged explicitly wherever it would otherwise be implied;
- or **insufficient user-feedback evidence** — stated plainly where that is the honest state,
  rather than treating silence as a compliment.

### Classification vocabulary

Two independent axes are used together for every Layer-2 finding.

**Evidence tier — how directly was this actually checked:**

- **PRIMARY USER EVIDENCE** — a real user review, forum post, or discussion actually read and
  inspected directly. *No finding in this document reaches this tier* (see above) — noted
  explicitly rather than left implicit.
- **SECONDARY SYNTHESIS** — another site (review blog, comparison article, aggregator)
  characterizing user sentiment, without this research reading the underlying primary source.
- **SEARCH-SNIPPET / INDIRECT** — only an indexed search-result snippet or fragmentary evidence was
  available; a fuller source could not be read.
- **INSUFFICIENT EVIDENCE** — a conclusion cannot responsibly be drawn either way; a gap, not a
  null result.

**Pattern strength — how consistently that evidence agreed, within its tier:**

- **RECURRING** — the same pattern appeared across multiple *independent* sources within a tier.
  Always paired with its tier (e.g. "RECURRING SECONDARY SYNTHESIS") — never stated alone, since
  "recurring" alone does not say whether that means several blogs independently agreeing or several
  blogs repeating the same original claim.
- **MIXED** — sources within a tier clearly disagree with each other.
- **ISOLATED / WEAK EVIDENCE** — one or few reports; interesting, but weak.
- **OUTDATED / VERSION-SENSITIVE** — likely tied to an older version, possibly since fixed.

The bare label "RECURRING FEEDBACK" used in the original version of this document is retired: it
did not distinguish primary user consensus from several secondary sources repeating each other, and
this research never reached primary sources. Every sentiment-derived claim below now carries an
explicit evidence tier.

Independently of both axes above, every feedback finding is also typed as one of:

- **PRODUCT-DESIGN FEEDBACK**
- **BUG / RELIABILITY FEEDBACK**
- **PRICING / SUBSCRIPTION FEEDBACK**

These three are never conflated. A pricing complaint is never read as a verdict on the underlying
workout model.

---

## Part 1 — Official Product Documentation (FACT layer)

| | Hevy | Strong | Fitbod |
|---|---|---|---|
| **Pricing (free tier)** | Fully usable free tier: logging, social, performance review; capped at 4 routines, 7 custom exercises, ~3 months history | Unlimited logging; capped at 3 saved routines | No real free tier — 7-day trial / 3 free trial workouts only |
| **Paid tier** | ~$2.99–3.99/mo, $23.99/yr, or $74.99 lifetime | $4.99/mo, $29.99/yr, or $99.99 lifetime (one third-party source claimed $9.99/mo with different gating — unresolved discrepancy, not treated as reliable) | $15.99/mo or $95.99/yr (legacy $12.99/$79.99 grandfathered) |
| **Core model** | Self-directed manual logging | Self-directed manual logging | Algorithm-generated workouts (Exercise Selector + Capability Recommender) |
| **Exercise library** | 400+, filterable, custom exercises (7 free / unlimited Pro) | 200+, with growing animated-video coverage, custom exercises | 1000+, with video, custom exercises for grip/stance/machine variants |
| **Routine/plan model** | Explicit routine (plan) vs. workout (execution) split; end-of-session prompt to update or discard routine changes | Same routine/workout split; **Strong's own help center states they plan to change this flow specifically to reduce confusion** | "My Plan" — configuration of the generator (goals, equipment, gyms), not manual routine-building |
| **Previous-performance display** | Inline "PREVIOUS" column per set, source configurable (any workout vs. same routine) | Automatic prior weight/reps shown when reaching an exercise again | **Not surfaced inline by default** — accessed via Trends/Exercise History/Results tab; the algorithm substitutes a suggested target weight instead |
| **Rest timer** | Automatic, per-exercise configurable | Present, per-exercise capable | On/off per exercise, adjustable pre- or mid-workout |
| **Apple Watch app** | Live-sync with phone; can use routines, mark set type, HR tracking; auto-resyncs after reconnect (implies disconnected recording) | **Fully independent** — can start and complete an entire workout with the phone left elsewhere; optional live-sync | **Companion only** — must start on iPhone; cannot swap/add exercises or finalize/save from the watch |
| **Wear OS app** | Exists — live-sync, routines, Tile support | **None found** — Apple Watch only | Exists — auto-advances exercise list; **rest timer does not trigger from a watch-logged set** (official limitation) |
| **Offline behavior (official)** | Offline logging with sync-on-reconnect (no further technical detail published) | Not documented in comparable detail | Documented: generates via a lighter offline algorithm, logs locally, syncs later; **official docs explicitly warn not to delete the app/data before syncing, or local data is lost** |
| **History editing** | Not detailed in official docs found | Not detailed in official docs found | **Editable**, but explicitly documented to retroactively regenerate recommendations and move derived Strength Scores |
| **Analytics (official)** | Weight-progression charts, automatic PR detection, workout frequency, volume-per-muscle-group, (Pro) sets-per-muscle-group over/under-training flags | Progress charts, 1RM estimate, PR tracking, volume tracking, body-measurement + Apple Health integration | Strength Scores, per-exercise 1RM trend, muscle-group recovery heatmap, PR/milestone tracking, training streaks |
| **Data-loss precedent (official)** | Dedicated help article for duplicate exercise/routine cleanup (implies a known duplication artifact) | Dedicated "Lost Data" help article — documented root cause is **sign-in-method/account mismatch across devices creating a silent second empty account**, not server-side loss | Official docs acknowledge a genuine loss window if the app/data is deleted before an offline session syncs |

---

## Part 2 — User Feedback by Product

### Hevy

- **Logging speed** — RECURRING SECONDARY SYNTHESIS, PRODUCT-DESIGN: several independent review
  sites describe it as fast/clean, often cited as a reason people choose it — no primary user
  evidence. SEARCH-SNIPPET / INDIRECT, PRODUCT-DESIGN, WEAK EVIDENCE: a claim that there's no
  automatic focus-advance between weight/reps fields, requiring a manual tap per field, appeared in
  one non-Reddit source with a paraphrase elsewhere — downgraded from the original "isolated-to-
  moderate" framing to WEAK EVIDENCE; treat as a plausible, specific, but unconfirmed friction
  point, not a demonstrated pattern.
- **Active workout UX (routine vs. workout split)** — the explicit end-of-session "update routine
  or discard" prompt is a FACT (Layer 1, official documentation). INSUFFICIENT EVIDENCE on whether
  real users find it confusing or valuable — this remains a genuine research gap, not resolved by
  this revision.
- **Previous-performance display** — Layer 1 FACT: Hevy ships an inline "PREVIOUS" column with a
  configurable source. SECONDARY SYNTHESIS (not RECURRING FEEDBACK): several review sites list it
  as one of the "basics done right." **This research found no complaint about it — but per the
  Evidence Quality section above, that absence is not itself positive evidence**; it may simply
  reflect that primary sources (App Store/Play Store/Reddit) were unreachable. Treat the "users
  value this" framing as INSUFFICIENT EVIDENCE at the direct-user-evidence tier, resting only on the
  official feature existing plus favorable secondary characterization.
- **Exercise library** — Layer 1 FACT + reasonable INFERENCE, not a user-feedback classification:
  Hevy maintains a dedicated official help article for cleaning up duplicated exercises/routines.
  The existence of first-party documentation for this is evidence the problem recurs often enough
  to warrant a support article — this is a vendor-documentation-derived inference, not a "RECURRING
  FEEDBACK" label, and is directly relevant to TBDFit's exercise-identity question regardless.
- **Rest timer** — INSUFFICIENT EVIDENCE. One SEARCH-SNIPPET / INDIRECT mention of timer issues in
  a general complaints roundup, uncorroborated; not treated as a pattern.
- **History and editing** — INSUFFICIENT EVIDENCE; this pass could not surface direct discussion.
  Flag as a research gap, not a clean bill of health.
- **Watch experience** — Layer 1 FACT: watch-first, phone-optional use is officially marketed
  ("leave your phone in the locker room"), and the official auto-resync-on-reconnect behavior
  implies Hevy's own architecture assumes disconnected watch recording with later reconciliation.
  **This is a vendor claim about a shipped capability, not verified user-experienced reliability.**
  INSUFFICIENT EVIDENCE on real-world watch failure modes specific to Hevy — see the dedicated
  Watch and Reliability sections below for why "supports independent execution" must not be read as
  "the watch architecture is reliable."
- **Reliability/offline** — SEARCH-SNIPPET / INDIRECT, MIXED, BUG/RELIABILITY, weak evidence: one
  third-party claim of a version update increasing 1-star reviews (crashes, timer issues),
  contrasted with Hevy's reported ~4.9/5 aggregate rating from multiple sources. Not corroborated as
  a genuine data-loss pattern in anything reachable this session.
- **Analytics** — RECURRING SECONDARY SYNTHESIS, PRODUCT-DESIGN: multiple independent review sites
  converge on muscle-group volume/balance tracking as the analytics feature people actually value,
  more so than chart quantity — still secondary-source convergence, not primary user evidence.
- **Pricing** — RECURRING SECONDARY SYNTHESIS, PRICING/SUBSCRIPTION ONLY: the 4-routine and
  7-custom-exercise free-tier caps (Layer 1 FACT) are the most-cited friction points across review
  sources. Signals what's considered essential (routines, custom exercises), not a verdict on the
  logging model.
- **Love-it-but pattern (weak evidence)**: secondary sources praise logging speed/basics while one
  weakly-evidenced source separately flags missing auto-advance between fields during rapid
  multi-exercise logging. If this friction point holds up under closer primary-source review, the
  lesson would be "make the tap sequence itself contiguous," not "don't build fast tap logging" —
  stated conditionally, since the underlying complaint itself is only WEAK EVIDENCE.

### Strong

- **Logging speed** — RECURRING SECONDARY SYNTHESIS, PRODUCT-DESIGN: multiple independent review
  sites describe the core weight→reps→complete loop as fast/frictionless, including framing it as
  important "when you're resting between heavy sets" — still secondary-source convergence, no
  primary user evidence. SEARCH-SNIPPET / INDIRECT, BUG/RELIABILITY, OUTDATED/VERSION-SENSITIVE
  (2021 source): a specific complaint that the completion tap sometimes just scrolled the field,
  and the rest-timer overlay blocked next-set input — a single old source, not treated as current.
- **Active workout UX** — SEARCH-SNIPPET / INDIRECT, WEAK EVIDENCE, PRODUCT-DESIGN: "reordering
  exercises is cumbersome" from one uncorroborated review-blog source. Evidence here is generally
  thin for Strong.
- **Previous-performance display** — Layer 1 FACT: prior weight/reps surface automatically when
  reaching an exercise again. SECONDARY SYNTHESIS (not RECURRING FEEDBACK): several review sources
  describe this favorably ("no hunting through history"). **No complaint was found for this
  feature, but per the Evidence Quality section above, that absence is not itself evidence of
  satisfaction** — treat "one of Strong's clearest strengths" as INSUFFICIENT EVIDENCE at the
  direct-user-evidence tier, resting on the official feature plus favorable secondary
  characterization only.
- **Exercise library** — INSUFFICIENT EVIDENCE; no substantive independent discussion found either
  way.
- **Routines/templates** — the update-template-vs-workout distinction is a **Layer 1 FACT**: Strong's
  own help center states a plan to change this flow, which is vendor self-acknowledgment of a real
  design issue — stronger evidence than ordinary secondary-sourced sentiment, and retained as such.
  RECURRING SECONDARY SYNTHESIS, PRICING/SUBSCRIPTION: the 3-routine free cap is cited across
  multiple independent review sources as blocking normal training patterns (e.g., an upper/lower
  split plus a deload variant already exceeds it).
- **Rest timer** — INSUFFICIENT EVIDENCE beyond its existence and per-exercise capability (Layer 1).
- **History and editing** — INSUFFICIENT EVIDENCE. Full history access is itself Pro-gated per
  Layer 1.
- **Watch experience** — Layer 1 FACT: Strong's watch app can start and complete an entire workout
  independently, phone left elsewhere. RECURRING SECONDARY SYNTHESIS, PRODUCT-DESIGN: multiple
  independent review sources describe genuinely leaving the phone behind as a standout feature —
  this is the strongest *secondary-synthesis* pattern found for any product in this research, but it
  is still secondary synthesis, not primary user evidence, and it is a finding about whether the
  capability is valued, not about whether the underlying sync/reliability is trustworthy (see the
  Watch and Reliability sections below — do not conflate the two). SEARCH-SNIPPET / INDIRECT,
  ISOLATED, dated (~2021): the on-watch UI itself called "a bit clunky" in one review. SEARCH-
  SNIPPET / INDIRECT (feature request): some users want a Wear OS equivalent, consistent with none
  existing officially. No evidence found on battery/disconnect/sync-failure complaints specifically
  for Strong — this is an evidence gap, not proof Strong's watch sync is reliable.
- **Reliability/offline** — SEARCH-SNIPPET / INDIRECT, MIXED, BUG/RELIABILITY: general
  "buggy"/"rarely updated" characterizations exist but concentrate in sources with a plausible
  competitive incentive — not confirmed consensus. The genuinely stronger signal is **Layer 1 FACT**:
  Strong maintains an official "Lost Data" help article, and its documented root cause (sign-in-
  method/account mismatch across devices creating a silent second empty account, not server-side
  loss) is a concrete, real-world precedent directly relevant to TBDFit's own identity-linking open
  question (see [`docs/product/decisions.md`](decisions.md) and the phone-authentication audit).
  This conclusion rests on vendor documentation, not user sentiment, so it is not downgraded by the
  Layer-2 access limitation.
- **Analytics** — RECURRING SECONDARY SYNTHESIS, PRODUCT-DESIGN: progress charts, 1RM/best-set
  progression, and PR tracking are repeatedly described favorably across independent review sources,
  specifically for restraint ("clean," "not overcomplicated") — a value-quality signal in the
  secondary sources, not confirmed primary user sentiment.
- **Pricing** — RECURRING SECONDARY SYNTHESIS, PRICING/SUBSCRIPTION ONLY: the 3-routine cap (Layer 1
  FACT) is the most consistently named friction point across review sources; core set/rep/weight
  logging itself is unrestricted on free.
- **Love-it-but pattern (secondary synthesis, not confirmed user consensus)**: review sources frame
  watch independence favorably ("amazing," "download it right now") while one dated, isolated
  source separately calls the on-watch interaction clunky. If both hold up, the lesson would be that
  the capability/concept is right and on-watch interaction polish is the gap — but see the Watch
  section below for why the capability itself must not be read as proof of reliability either.

### Fitbod

- **Logging speed** — INSUFFICIENT EVIDENCE for a direct tap-count claim. SECONDARY SYNTHESIS,
  PRODUCT-DESIGN: several reviewers frame the algorithm auto-prescribing weight/reps as reducing
  in-session *decisions* (a different lever than tap-count) — not corroborated as RECURRING beyond a
  handful of sources making the same framing.
- **Active workout UX** — SEARCH-SNIPPET / INDIRECT, ISOLATED, BUG-adjacent: one App-Store-review-
  aggregation-sourced report of the app "resetting prior exercises already completed" mid-session;
  not corroborated elsewhere, possibly version-specific.
- **Previous-performance display** — Layer 1 FACT: Fitbod does not surface a "last time" number
  inline by default, substituting an algorithmic weight suggestion instead, with the raw history
  accessible via Trends/Exercise History. SECONDARY SYNTHESIS, PRODUCT-DESIGN: comparison sources
  repeatedly frame this as a genuine philosophical fork versus Hevy/Strong ("Fitbod asks you to
  trust the output; Hevy asks you to understand the input") — this is evidence of a documented
  *design difference*, not a quoted user complaint or praise, and is not upgraded beyond that.
- **Exercise library** — INSUFFICIENT EVIDENCE; an evidence gap, not a clean bill of health.
- **Routines/templates** — largely inapplicable in the Hevy/Strong sense (generation-based, not
  manual); SEARCH-SNIPPET / INDIRECT, ISOLATED, PRODUCT-DESIGN (positive): one review praised ease
  of entering home-gym equipment specifics.
- **Rest timer** — INSUFFICIENT EVIDENCE beyond the official capability description, aside from the
  watch-specific gap below.
- **History and editing** — Layer 1 FACT, architecturally significant, not itself a user complaint:
  official docs confirm editing a past logged set can retroactively move derived Strength
  Scores/recommendations, since those are computed from recent history — a real, structural
  coupling between history mutability and algorithm state, established by vendor documentation
  rather than by sentiment volume, so it is not downgraded by the Layer-2 access limitation.
- **Watch experience** — SECONDARY SYNTHESIS / SEARCH-SNIPPET, RECURRING within that tier,
  BUG/RELIABILITY: App-Store-review-aggregation and JustUseApp-sourced summaries report Apple
  Watch↔phone sync failures during an active workout that erased in-progress watch data, forcing
  re-logging from memory, plus a separate report of timer behavior diverging between watch and
  phone — these are secondary aggregations of reviews, not primary text this research read
  directly. Combined with **Layer 1 FACT** (the watch officially cannot start/finish a workout or
  change exercises independently, and the Wear OS rest timer officially does not trigger from a
  watch-logged set): the *capability ceiling* is a verified vendor fact, while the *sync-failure*
  reports sit at secondary/search-snippet tier. Together they describe Fitbod's watch as a logging
  accessory rather than an independent client, with reliability complaints layered on top of that
  capability ceiling — but the reliability portion specifically should be read as secondary-tier
  evidence, not confirmed primary consensus.
- **Reliability/offline** — SECONDARY SYNTHESIS / SEARCH-SNIPPET, RECURRING within that tier,
  BUG/RELIABILITY: multiple independently-sourced review aggregations report crashes specifically
  while creating/updating a workout or logging a set mid-workout, one source describing this as
  "persisted for months"; also workout-duration values syncing incorrectly to Apple Health (e.g.,
  299 minutes instead of 60). **What raises this above ordinary secondary-sourced sentiment**: Fitbod's
  own **Layer 1 FACT** documentation independently acknowledges a real data-loss window if the app/
  data is deleted before an offline session syncs. An official vendor admission of a loss window,
  plus independent (if secondary-tier) reports describing a matching failure mode from the user
  side, is stronger corroboration than either alone — still not primary user evidence, but a
  meaningfully better-supported conclusion than most other Layer-2 findings in this document.
- **Analytics** — RECURRING SECONDARY SYNTHESIS, PRODUCT-DESIGN: the Strength Score / 1RM trend and
  muscle-group heatmap are repeatedly named across independent review/comparison sources as
  Fitbod's most distinctive analytics feature — secondary-source convergence, not primary user
  evidence, though the repeated "signature feature" framing across independent source *types*
  (review aggregation, comparison articles) is a moderately meaningful convergence within that tier.
- **Recommendations/adaptive programming** — SECONDARY SYNTHESIS, PRODUCT-DESIGN, RECURRING within
  that tier: this is the most cross-corroborated Layer-2 finding in the whole document — the same
  pattern appears across independent *source types* (review-aggregation summaries, comparison
  articles, and a dedicated advanced-lifter review piece), which is a meaningfully stronger form of
  convergence than several similarly-themed blogs alone, even though none of it is primary user
  evidence:
  - Beginner-facing suggestions are sometimes inappropriate at first (unrealistic dumbbell loads,
    beginner-scaled cardio needing manual replacement) — sources consistently note this improves as
    the algorithm ingests more logged history (a cold-start problem, not a permanent flaw, per
    those sources).
  - For experienced/specialized lifters, the general-purpose algorithm is repeatedly described as
    "good enough but not optimized" — one review specifically noted same-session programming
    choices violating basic principles (two max-effort same-muscle-group lifts back to back).
  - The mitigation repeated across sources: overriding the algorithm (flag an exercise for
    more/less/never) is described as easy, and framed as *why* long-term users stay comfortable —
    trust attributed to fast override, not to the algorithm always being right. Still
    SECONDARY SYNTHESIS, not confirmed by this research reading primary reviews directly.
  - **TBDFit architectural interpretation** (judgment drawn from this pattern, not a verified
    competitor fact): the risk of any future automation is not "automation is bad" but "opaque
    automation with poor day-one calibration and slow/hard override erodes trust fast" — if TBDFit
    ever adds suggested progression, override speed should be designed alongside the suggestion.
- **Pricing** — RECURRING SECONDARY SYNTHESIS, PRICING/SUBSCRIPTION ONLY: billing/refund friction is
  the most consistent theme across review sources (App-Store-purchase refund limitations, repeat-
  billing-after-cancellation, general "expensive for what it does," often framed as 3–4x pricier
  than logging-only competitors). Aggregate rating reported at 4.6/5 on Google Play (13,000+
  ratings) is a real aggregator data point, not sentiment content — per instruction, not treated as
  evidence of anything beyond general satisfaction; the negative-review content under that same
  aggregate is where billing complaints concentrate (SECONDARY SYNTHESIS). Separately, no documented
  data-export feature was found — raised by comparison sources as a lock-in concern distinct from
  price, but the two showed up together in switching discussions (SEARCH-SNIPPET / INDIRECT).

---

## Cross-app switching findings

Same access limitation applies (WebFetch blocked on Reddit/forums/Blind); every row below sits at
the SECONDARY SYNTHESIS or SEARCH-SNIPPET / INDIRECT evidence tier — none reaches PRIMARY USER
EVIDENCE, and none should be read as a verified primary thread regardless of its pattern-strength
label.

| Direction | Finding | Strength |
|---|---|---|
| Strong → Hevy | Hevy's one-time lifetime price ($74.99) vs. Strong's ongoing subscription cited as a driver, alongside Strong's more limited free tier | WEAK EVIDENCE (near-identical phrasing across content-mill sites, no primary corroboration) |
| Hevy → Strong | One account of Hevy's UI/navigation and lack of unilateral (left/right) tracking driving a move to Strong's free tier | ISOLATED (one thread, two commenters) |
| Fitbod → Strong | Cancelling Fitbod after ceasing to use its programming, downgrading to a simpler/cheaper logger; some lifters use both apps together (Strong/Hevy in-gym, Fitbod at-home planning) rather than a clean switch | MODERATE PATTERN (two independent secondary sources), mixes PRODUCT-DESIGN and PRICING |
| Strong → Fitbod | Only an indirect, uncorroborated complaint about Fitbod's progression handling for custom workouts | INSUFFICIENT EVIDENCE as a named switching pattern |
| Hevy ↔ Fitbod (either direction) | No switching-specific accounts found; only vendor/comparison framing describing the two as serving different intents (automated programming vs. self-directed + social) rather than substitutes | INSUFFICIENT EVIDENCE of actual migration either direction |

**Synthesized cross-cutting patterns:**

1. Pricing model (subscription vs. one-time) is the most-repeated stated driver for Strong→Hevy
   movement — **WEAK EVIDENCE**.
2. Fitbod's automated programming is the clearest differentiator driving movement away from it
   toward manual loggers once a user stops wanting AI-driven programming, and conversely the draw
   toward it for users who want programming decided for them — **MODERATE PATTERN**, converging
   across independent sources though none primary-quote-verified.
3. No credible evidence of significant two-way churn between Hevy and Fitbod specifically — they
   read as serving different intents more than being substitutes people migrate between.
4. Fitbod's inability to cleanly re-edit/progress custom workouts surfaced once as a specific
   technical gripe, independent of any switching decision.
5. A separate, out-of-matrix mention: a user leaving Fitbod for a third app (JuggernautAI) over
   "bugs and poor support" — flagged for context, not part of the three-app matrix.

---

## Cross-Product Feedback Matrix

| Product area | Hevy feedback | Strong feedback | Fitbod feedback | TBDFit lesson |
|---|---|---|---|---|
| **Logging speed** | SECONDARY SYNTHESIS praise for speed; WEAK EVIDENCE re: no auto-advance between fields | SECONDARY SYNTHESIS praise for the core loop's speed; one OUTDATED, single-source complaint about a completion-tap bug | INSUFFICIENT EVIDENCE on tap-count; automation reduces *decisions*, a different lever (SECONDARY SYNTHESIS) | Fast tap-based logging looks like table stakes across secondary sources for all three; the possible differentiator (contiguous focus-flow between fields) rests on weak evidence and needs primary confirmation before being treated as validated |
| **Exercise library** | FACT + inference: official duplicate-cleanup doc implies a real fragmentation problem | INSUFFICIENT EVIDENCE | INSUFFICIENT EVIDENCE | Design exercise identity to prevent duplication/fragmentation from day one — the evidence for this risk (Hevy) came from the vendor's own support docs, not user sentiment, so it stands regardless of the Layer-2 access limitation |
| **Routines** | Explicit plan/execution split with an end-of-session reconciliation prompt (FACT: design intent confirmed; sentiment: INSUFFICIENT EVIDENCE) | FACT: company-acknowledged confusion in the update-template-vs-workout flow; RECURRING SECONDARY SYNTHESIS: 3-routine free cap is a pricing complaint | Not directly comparable (generation-based, not manual) | The plan/execution split itself looks sound (all manual-logging apps converge on it) but the "did this change the template or not" moment is a documented (vendor-acknowledged, for Strong), cross-product weak point worth designing deliberately |
| **Previous performance** | FACT: inline "PREVIOUS" column; SECONDARY SYNTHESIS positive characterization; INSUFFICIENT EVIDENCE at the direct-user tier | FACT: automatic prior weight/reps shown; SECONDARY SYNTHESIS positive characterization; INSUFFICIENT EVIDENCE at the direct-user tier | A deliberate design *difference* (FACT: suggestion instead of raw recall), not confirmed user backlash | Inline previous-performance display is a validated *competitor design pattern* with favorable secondary-source characterization — worth prioritizing on product-design grounds, but not yet backed by direct user evidence; see the roadmap note below for how this is phrased there |
| **Rest timer** | INSUFFICIENT EVIDENCE | INSUFFICIENT EVIDENCE | FACT: official per-exercise on/off + pre/mid-workout adjustment; Wear OS timer doesn't trigger from a watch-logged set | Evidence is generally thin across all three for this category — treat rest-timer priority as lower-confidence than logging speed or previous-performance in this research pass |
| **History/editing** | INSUFFICIENT EVIDENCE (research gap) | INSUFFICIENT EVIDENCE; full history is Pro-gated (FACT) | FACT: editable, but officially documented to retroactively move derived scores/recommendations | Mutable history has a real, structural side effect once any derived statistic exists (established by Fitbod's own documentation, not sentiment volume) — evidence for treating completed-workout mutability as a deliberate decision with named consequences |
| **Watch** | FACT: headline marketed feature; official auto-resync-after-reconnect implies disconnected recording + reconciliation. Reliability: INSUFFICIENT EVIDENCE | FACT: fully independent execution, phone genuinely optional. RECURRING SECONDARY SYNTHESIS: this capability is described favorably across independent review sources — the strongest secondary-synthesis pattern in this research. Reliability: INSUFFICIENT EVIDENCE (an evidence gap, not proof of reliability) | FACT: companion-only by design. SECONDARY SYNTHESIS / SEARCH-SNIPPET, RECURRING within that tier: reported sync failures erasing in-progress watch data, on top of that capability ceiling | Independent watch execution is a valued *capability* per FACT + secondary-synthesis evidence (Hevy, Strong), and its absence compounds with reliability problems where evidence exists (Fitbod) — but "supports independent execution" must not be equated with "the watch architecture is reliable"; see the dedicated Watch and Reliability sections below |
| **Sync/reliability** | SEARCH-SNIPPET / INDIRECT, MIXED: weak evidence of a version regression | FACT (official "Lost Data" doc) traces most documented loss to identity/sign-in mismatches, not server loss — this conclusion rests on vendor documentation, not sentiment | SECONDARY SYNTHESIS / SEARCH-SNIPPET, RECURRING within that tier, corroborated by a Layer 1 FACT (official acknowledgment of a real offline-delete-before-sync loss window) | Two differently-caused data-loss precedents (Strong: identity mismatch, FACT-grounded; Fitbod: offline-sync window, FACT + secondary-tier corroboration) both map onto invariants TBDFit's local-first/PD-001/PD-002 direction already targets — see the decomposed Reliability section below before drawing architecture conclusions |
| **Analytics** | SECONDARY SYNTHESIS: muscle-group volume/balance specifically described as valued over chart quantity | SECONDARY SYNTHESIS: progress/1RM/PR charts described favorably, specifically for restraint ("clean," "not overcomplicated") | SECONDARY SYNTHESIS, RECURRING across independent source types: Strength Score + muscle heatmap named as a signature feature | Across all three, secondary sources converge on "a small number of well-chosen, trusted metrics" over "more charts" as the pattern — directionally useful, not primary-evidence-backed |

---

## WHAT USERS CONSISTENTLY LIKE

Read as: *what secondary sources consistently describe positively* — this research reached no
primary user evidence for any of the following, and none of these are population-level claims.

- Fast, low-friction core logging (weight → reps → mark set complete) — **RECURRING SECONDARY
  SYNTHESIS** across Hevy and Strong specifically; not corroborated by any primary review text.
- Previous-performance data shown automatically, inline, without extra navigation — official FACT
  for both Hevy and Strong, with favorable SECONDARY SYNTHESIS characterization. **This research did
  not find a complaint about it, but that absence is not itself positive evidence** (see Evidence
  Quality section) — treat "users like this" as INSUFFICIENT EVIDENCE at the direct-user tier,
  resting on the feature's existence plus favorable secondary framing.
- A small set of well-chosen, restrained analytics (volume/muscle-balance, 1RM/PR trend, Fitbod's
  heatmap) over raw chart quantity — **RECURRING SECONDARY SYNTHESIS**, converging across
  independent secondary sources for all three products, not primary user evidence.
- Independent watch execution being *valued as a capability* (Hevy, Strong) — **RECURRING SECONDARY
  SYNTHESIS**, the strongest secondary-synthesis pattern in this research pass. This is a finding
  about the capability being valued, not about the underlying sync/reliability being trustworthy —
  see WATCH USER-FEEDBACK LESSONS below, which must be read together with this point, not
  separately from it.
- Fast, easy override of automated suggestions, where automation exists (Fitbod) — **RECURRING
  SECONDARY SYNTHESIS**, repeated across independent source types (review aggregation, comparison
  articles, an advanced-lifter review), a meaningfully stronger convergence within that tier than
  most other findings in this document, though still not primary user evidence.

## WHAT USERS CONSISTENTLY DISLIKE

- Ambiguity about whether editing during an active workout changed the saved plan or not — **FACT**
  for Strong (company-acknowledged in its own help center); design-intent-confirmed but
  INSUFFICIENT EVIDENCE on user sentiment for Hevy.
- Restrictive free-tier routine/exercise caps blocking normal (not edge-case) usage patterns —
  **RECURRING SECONDARY SYNTHESIS**, but explicitly a **pricing**, not logging-model, complaint.
- A watch that can't operate independently of the phone, especially once reliability issues compound
  the limitation (Fitbod) — the capability ceiling is **FACT** (official documentation); the
  reliability reports are **SECONDARY SYNTHESIS / SEARCH-SNIPPET, RECURRING within that tier** —
  together a meaningfully corroborated pattern, but not primary user evidence.
- Automation with poor day-one calibration and slow override (Fitbod's cold-start recommendation
  quality) — **SECONDARY SYNTHESIS, RECURRING across independent source types**, though sources
  themselves frame this as improving with more logged history rather than a permanent flaw.
- Exercise-library duplication/fragmentation (Hevy) — **FACT + inference** from the vendor's own
  cleanup doc, not sentiment-derived; INSUFFICIENT EVIDENCE either way for the other two products
  (a research gap, not a clean bill of health for them).

## FEATURES USERS MISS WHEN SWITCHING APPS

See the cross-app switching table above. The one pattern strong enough to call moderate: people
who stop wanting Fitbod's automated programming move to manual loggers (Strong/Hevy), and the
reverse draw (wanting programming decided for you) pulls the other way — **moderate pattern**.
Pricing-model preference (one-time vs. subscription) is a repeatedly *claimed* Strong→Hevy driver
but only **weak evidence** in this pass. Everything else in the switching matrix is isolated or
insufficient — do not treat the matrix as proof of large migration volumes in any direction.

## LOVE-IT-BUT... PATTERNS

All three below rest on SECONDARY SYNTHESIS / SEARCH-SNIPPET evidence, not primary user text — read
as plausible hypotheses this pattern-shape suggests, not confirmed user narratives.

- **Hevy**: secondary sources describe logging speed and the basics favorably, while one
  weakly-evidenced source separately flags missing auto-advance between fields during rapid entry.
  If confirmed, the lesson would be to keep the tap sequence itself contiguous, not "don't build
  fast logging" — but the underlying complaint is WEAK EVIDENCE, so this is offered conditionally.
- **Strong**: watch independence is described favorably across secondary sources ("amazing"), while
  one dated, isolated source separately calls the on-watch interaction clunky. If confirmed, the
  lesson would be that the *capability/concept* (independent watch execution) is well-received and
  only the on-watch interaction polish is the gap — but neither half of this pairing is primary
  user evidence, and the capability being liked says nothing about whether the underlying watch↔
  phone sync is reliable (see WATCH USER-FEEDBACK LESSONS below).
- **Fitbod**: secondary sources describe long-term trust in the recommendation engine as
  conditional on fast, easy override — this is the most cross-corroborated pattern in the document
  (converging across independent source types, see the Fitbod recommendations section), though
  still not primary user evidence. Lesson, offered as a TBDFit architectural interpretation rather
  than a verified fact: automation's trustworthiness looks like a function of override speed, not
  suggestion accuracy alone.

## RELIABILITY / DATA-LOSS LESSONS

**Important scoping note before the findings**: this research observed *product behavior and
vendor documentation*, not internal architecture. None of what follows should be read as "Strong's
sync protocol is X" or "Fitbod's replication is Y" — no competitor's internal implementation was
inspected. Where competitor evidence genuinely supports a specific claim, it is marked FACT (Layer
1) or its Layer-2 evidence tier; everywhere else, this document says plainly that the underlying
mechanism **cannot be inferred from available evidence**, rather than guessing at an architecture
from an observed symptom.

To keep "the watch can do X" separate from "the watch is trustworthy," findings are organized by
five distinct qualities. Not every product has evidence for every quality — gaps are stated as
gaps, not filled with inference:

| Quality | Question | Hevy | Strong | Fitbod |
|---|---|---|---|---|
| **Feature availability** | Can the watch perform the action? | FACT: independent execution, live-sync optional | FACT: fully independent execution, phone optional | FACT: companion-only — cannot start/finish/change exercises from the watch |
| **Local durability** | Can a completed workout survive locally (on-device) before/without syncing? | Not documented in enough detail to conclude either way — INSUFFICIENT EVIDENCE | Not documented in enough detail to conclude either way — INSUFFICIENT EVIDENCE | FACT: offline sessions are saved locally, but the vendor's own docs describe a real window where that local copy can be lost (see Recovery below) |
| **Replication** | Can phone/watch actually exchange the workout? | FACT: "auto-resync after reconnect" is stated to exist; the underlying mechanism/robustness is **not documented** — cannot be inferred beyond "some reconciliation process exists" | Not documented in comparable detail — INSUFFICIENT EVIDENCE on the mechanism | FACT: sync-on-reconnect is documented to exist; SECONDARY SYNTHESIS / SEARCH-SNIPPET reports describe sync failures erasing in-progress watch data — this describes a *symptom*, not a confirmed cause, and should not be read as "Fitbod's replication logic is unsafe," only as "replication has been reported to fail in ways that lose data" |
| **Conflict safety** | Can one device overwrite newer work from another? | No evidence either way — cannot be inferred | No evidence either way — cannot be inferred | No evidence either way — the reported "erased in-progress data" symptom is consistent with a conflict-safety gap, but this research cannot distinguish that from a plain sync-failure-with-no-retry; **not inferred as a specific mechanism** |
| **Recovery** | Can a failed sync be retried without loss or duplication? | No evidence either way — cannot be inferred | FACT (via the official "Lost Data" article): the dominant *documented* loss scenario is an identity/sign-in mismatch across devices creating a silent second empty account — this is a recovery/identity-resolution failure, not a proven phone↔watch replication failure specifically; do not generalize it into a sync-protocol claim | FACT: the vendor's own docs warn against deleting the app/data before an offline session syncs, i.e. **the vendor itself states recovery is not guaranteed in at least that one scenario** — the strongest direct evidence of a real, named recovery gap found for any product in this research |

**Synthesis** (TBDFit architectural interpretation, not a verified competitor implementation fact):
Strong's and Fitbod's two data-loss precedents are caused by different failure classes — Strong's by
an **identity/account-resolution** gap, Fitbod's by an **acknowledged local-durability/sync-timing**
gap — and neither, on the evidence available, proves anything about the other's phone↔watch
*replication* correctness specifically. What both do support, independent of mechanism, is that
**"a workout was recorded" is not the same claim as "a workout is durably, recoverably represented
across devices and identity boundaries,"** which is exactly the invariant TBDFit's own CLAUDE.md
already names (an active or completed workout must not disappear because of a device/network/
identity boundary crossing badly). Treat this as corroborating evidence that the invariant is worth
enforcing structurally, not as proof of how any competitor actually enforces (or fails to enforce)
it internally.

## WATCH USER-FEEDBACK LESSONS

This section deliberately keeps two findings separate, because the original version of this
document conflated them. **"Supports independent watch execution" is not the same claim as "the
watch architecture is reliable"** — the evidence base for each is different, and treating one as
proof of the other is exactly the overreach this hygiene pass exists to correct.

**Finding 1 — users appear to value watch-based / phone-light execution.** The two products that
ship fully independent watch execution (Hevy, Strong — both **FACT**, Layer 1) are also the two
where secondary sources most consistently describe that capability favorably (**RECURRING SECONDARY
SYNTHESIS** — genuinely leaving the phone behind is described as a headline draw, not a
nice-to-have). This is real signal about what's *valued*, but it rests entirely on secondary-source
characterization plus the vendors' own feature claims — no primary user evidence was reached.

**Finding 2 — watch↔phone synchronization and reliability remain a potentially serious, separately-
evidenced failure surface.** The clearest evidence for this comes from Fitbod: a **FACT** (Layer 1)
capability ceiling (cannot start/finish/change exercises from the watch) compounded by **SECONDARY
SYNTHESIS / SEARCH-SNIPPET** reports of sync failures erasing in-progress workout data. Critically,
**this research found no comparable reliability evidence — positive or negative — for Hevy's or
Strong's watch sync specifically.** That silence is an evidence gap (see Evidence Quality section),
not proof that Hevy's or Strong's watch sync is reliable. Do not read "no complaints found" as "no
problems exist" for either product.

**These two findings must not be equated.** A product can genuinely be valued for offering
phone-light execution while still having an unresolved, serious reliability gap in exactly the
mechanism that makes that execution trustworthy (Fitbod's own case shows both at once, for the
capability it does offer). Conversely, the absence of a documented reliability complaint for Hevy
or Strong is not evidence their mechanism is sound — it is simply an area this research could not
verify.

**TBDFit architectural interpretation** (explicitly a TBDFit-side inference, not a verified
competitor implementation fact — no competitor's internal architecture was inspected):

> Independent watch execution is valuable, but it only becomes trustworthy when combined with
> local durability, explicit execution authority, idempotent replication, and protection against
> stale-device overwrite.

This framing is offered because it is the only way to reconcile Finding 1 and Finding 2 without
overclaiming either — the competitor evidence supports that phone-light execution is wanted and
that at least one implementation of it (Fitbod's, in its more limited companion form) has a real,
documented reliability gap; it does not support a claim about *how* any competitor actually
achieves (or fails to achieve) the four properties named above internally. Those four properties
are exactly what TBDFit's own Wear→Phone replication design already targets — this section is
corroborating evidence that the invariant matters, not a description of how any competitor
implements it.

Separately, on-watch interaction *polish* (distinct from the reliability/architecture question
above) is where the one available complaint (Strong, "clunky," dated, SEARCH-SNIPPET / INDIRECT)
lands — a UI-quality risk, not an architectural one, and not strong enough evidence to weight
heavily either way.

## FEEDBACK THAT SHOULD INFLUENCE TBDFIT'S FIRST SLICE

- **Previous-performance display, shown inline without extra navigation**, is a validated
  *competitor design pattern* (both Hevy and Strong ship it as a core feature, FACT) with favorable
  secondary-source characterization and no direct user evidence either way (absence of a found
  complaint is not itself evidence of love — see Evidence Quality section). On that basis it is
  reasonable to pull earlier in the roadmap as a low-cost, well-precedented pattern, not because
  user sentiment has been confirmed strongly positive.
- **Independent watch execution with later reconciliation** is a valued *capability* per
  FACT + secondary-synthesis evidence (Hevy, Strong), and its absence compounds with real,
  separately-evidenced reliability problems where a more limited version exists (Fitbod) — but per
  the corrected WATCH USER-FEEDBACK LESSONS above, this supports committing to the capability *and*
  to the durability/authority/idempotency/anti-stale-overwrite mechanisms that make it trustworthy,
  not to the capability alone. This is evidence supporting TBDFit's existing PD-001 direction, with
  the reliability half made explicit rather than assumed.
- **Exercise-identity design deserves deliberate attention before the exercise library grows**,
  given Hevy's own vendor-documented duplication problem — cheap to get right early, evidenced as
  costly to unwind later.
- **If any suggested-progression/automation feature is ever considered, override speed must be
  designed alongside the suggestion, not after** — Fitbod's trust pattern is conditional on fast
  override, and this is exactly the kind of "avoid taking into first slices" caution the parent
  request asked this research to surface.
- **Completed-workout mutability has a real structural consequence once any derived statistic
  exists** (Fitbod's documented score-recalculation-on-edit) — worth deciding deliberately as part
  of TBDFit's completed-workout mutability question, rather than defaulting to "of course workouts
  are editable" without naming the consequence.
- **Identity-linking behavior is not a purely hypothetical risk** — Strong maintains a dedicated,
  named official help article for exactly this failure mode (FACT, Layer 1). A vendor writing and
  maintaining standing documentation for a problem is evidence it happens often enough to warrant
  that, though this research cannot state how often, since no primary incident reports were read.

## FEEDBACK WE SHOULD NOT OVERREACT TO

- **Isolated/dated bug reports** (Hevy's 2021-ish timer complaint sources, Strong's 2021 completion-
  tap/timer-overlay bug) — these read as version-specific and plausibly already fixed; do not let a
  single old source drive a current architectural decision.
- **Fitbod's cold-start recommendation-quality complaints** — sources themselves note this improves
  as the algorithm ingests more logged history; this is evidence about *automation calibration*,
  not evidence that automation itself is unwanted (and TBDFit isn't building automated programming
  in its first slice regardless).
- **Pricing/subscription complaints generally** — real and worth noting for what they reveal about
  perceived-essential features (routine counts, custom exercises), but explicitly not a verdict on
  any product's underlying logging model, per the parent request's own instruction.
- **Any single-thread "switched because of X" claim** in the cross-app matrix — most of that table
  is WEAK EVIDENCE or ISOLATED; only the Fitbod-programming-driven-movement pattern reached
  MODERATE. Do not let one compelling secondary-source paragraph read as settled user consensus.
- **Any bare "RECURRING FEEDBACK"-sounding claim in this document without an accompanying evidence
  tier** — every sentiment-derived finding here tops out at SECONDARY SYNTHESIS or SEARCH-SNIPPET /
  INDIRECT; none reaches PRIMARY USER EVIDENCE. "Recurring" means recurring across the secondary
  sources this research could reach, not verified recurrence across real users.
- **The exact numeric details** (pricing figures, rating counts, "months" of persisted bugs) — these
  came through indexed secondary sources under significant access constraints (see Evidence Quality
  and Limitations) and should be spot-checked against primary sources before being quoted externally
  or used in a pricing decision.

---

## RECOMMENDED FIRST-SLICE SCOPE

Combining this research's strongest user-feedback evidence with what is already decided/proposed
in [`docs/product/decisions.md`](decisions.md) and the physiological-capabilities research —
**official facts and sentiment are kept distinguishable below by construction: nothing here
restates a Layer-1 fact as a scope item, only feedback-informed judgment.**

**RECOMMENDATION** (architectural/product judgment, not a decision):

1. Prioritize a **fast, contiguous logging loop** (weight → reps → complete, minimal taps, no dead
   clicks between fields) as a first-slice quality bar, not a later polish pass — this reflects a
   pattern that recurs across secondary sources for all three products (SECONDARY SYNTHESIS,
   RECURRING within that tier); it is a reasonable design bar to set on that basis, but is not
   backed by primary user evidence.
2. Surface **previous-performance data inline during logging**, sourced from TBDFit's own local
   data, as early as the first real workout-logging slice — this is a well-precedented competitor
   *design pattern* (FACT, Hevy and Strong both ship it) with favorable secondary-source
   characterization; reasonable to prioritize on cost/precedent grounds, not because user sentiment
   has been confirmed strongly positive (see the Evidence Quality section on absence-of-complaint
   reasoning).
3. Treat **exercise identity** (naming, custom exercises, avoiding duplicate/fragmented history) as
   a first-slice design concern, not a later cleanup — Hevy's own support burden (FACT, Layer 1) is
   direct evidence this is expensive to leave implicit; this conclusion does not depend on the
   Layer-2 access limitation.
4. Continue committing to **independent watch execution**, but explicitly *as a package with the
   mechanisms that make it trustworthy* — local durability, explicit execution authority, idempotent
   replication, and protection against stale-device overwrite — not as a capability alone. This
   research adds evidence (Hevy, Strong: FACT + favorable secondary synthesis) that the capability is
   valued, and evidence (Fitbod: FACT capability ceiling + secondary-tier reliability reports) of
   what a limited, less-reliable version costs — but no competitor's internal reliability mechanism
   was verified, so the reliability half of this recommendation is a TBDFit architectural
   interpretation, not evidence that any competitor has solved it. See WATCH USER-FEEDBACK LESSONS
   above.
5. Treat the **plan-vs-execution boundary** (routine vs. active workout) as needing a clear,
   explicit reconciliation moment (something like "update the plan or not") designed on purpose —
   Strong's own team publicly acknowledging this as unresolved is evidence it's a genuinely hard
   design problem, not a solved one to copy blindly.
6. **Do not build suggested/automated programming into the first slice** — not because the
   underlying idea is bad (Fitbod's users who want it are genuinely well served), but because the
   trust-and-override mechanics this research found to be load-bearing are themselves nontrivial
   product work, and TBDFit's differentiation hypothesis (per the physiological-capabilities
   research) does not depend on it.
7. When completed-workout editing is designed, **name the consequence explicitly** if any derived
   statistic (PRs, volume trends) will exist — Fitbod's documented score-recalculation-on-edit shows
   this is a real coupling, not a hypothetical one.
8. When identity/session/auth work continues, **treat cross-method identity linking as informed by
   real precedent**, not just internal reasoning — Strong maintains dedicated, named official
   documentation (FACT, Layer 1) for exactly this failure mode, which is external validation the
   concern is real enough for a mature, popular app to document, even though this research cannot
   state how frequently it actually occurs.

**OPEN DECISION**: none of the above are decisions. They are candidate scope-shaping inputs for the
two developers to weigh against `docs/product/decisions.md`'s existing PD-001/002/003 status and
whatever the first workout-implementation-slice planning session decides.

## Status

**STATUS: RESEARCH — FOR TEAM REVIEW.** This document does not promote, accept, or modify any
entry in `docs/product/decisions.md` or any ADR, and this revision changed no PD/ADR status.

**Revision history:**
- Initial version: two-layer benchmark (official documentation + user feedback) across Hevy,
  Strong, and Fitbod.
- **Evidence-hygiene revision (this version)**: re-labeled Layer-2 findings against an explicit
  evidence-tier vocabulary (PRIMARY USER EVIDENCE / SECONDARY SYNTHESIS / SEARCH-SNIPPET-INDIRECT /
  INSUFFICIENT EVIDENCE) after confirming this research reached **zero PRIMARY USER EVIDENCE**
  anywhere in the document; corrected several instances of absence-of-complaint being read as
  positive sentiment; separated the watch-execution finding into "valued as a capability" versus
  "reliability is a separately-evidenced, unresolved concern" rather than treating the former as
  proof of the latter; restructured the reliability findings around five distinct qualities
  (feature availability, local durability, replication, conflict safety, recovery) so that
  competitor evidence is used only where it actually supports a claim, without inferring internal
  architecture from observed symptoms. No new sources were gathered for this revision.

Confidence in Layer 2 (user feedback) throughout this document is constrained by the source-access
limitation described in Evidence Quality and Limitations above — treat it as a well-organized
starting hypothesis set, not a substitute for direct primary-source review before a scope
commitment is finalized.
