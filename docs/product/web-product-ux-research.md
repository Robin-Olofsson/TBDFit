# Web Product UX Research

**STATUS: RESEARCH — NOT A PRODUCT DECISION**

This is a research document, not an ADR and not a product decision. It exists to inform
[`web-information-architecture.md`](web-information-architecture.md) and the multi-client product
vision. Nothing here is accepted. Where a recommendation appears, it is product/design judgment
based on the evidence gathered, not a decision.

Classification vocabulary, reused from
[`frontend-product-ux-research.md`](frontend-product-ux-research.md) and
[`competitor-benchmark-hevy-strong-fitbod.md`](competitor-benchmark-hevy-strong-fitbod.md):
**OBSERVED PRODUCT BEHAVIOR** (directly evidenced), **INFERENCE** (a reasoned conclusion drawn from
observed behavior), **TBDFIT RECOMMENDATION** (design judgment for TBDFit, not a decision), **OPEN
DECISION** (requires developer/product input). Additionally, per-capability evidence is classified
**VERIFIED** / **NOT VERIFIED** / **UNKNOWN** — "not found in official documentation" is never
converted to "NO," and browser functionality is never inferred from marketing screenshots or from
silence.

---

## 0. Correction notice — replaces a prior version of this document

An earlier version of this document based its headline conclusions primarily on TrainingPeaks,
Strava, Garmin Connect, TrainHeroic, and Boostcamp, treating them as the main evidence base. That was
a scoping error: **the intended primary benchmark is exactly Hevy, Strong, and Fitbod** — the same
three products [`competitor-benchmark-hevy-strong-fitbod.md`](competitor-benchmark-hevy-strong-fitbod.md)
already covers for mobile. This version redoes the research with Hevy/Strong/Fitbod as the sole
primary evidence base. The other five products now appear only in §12, **Supplementary References**,
and do not drive any conclusion in this document. Where the corrected primary-three evidence turns
out to be thinner or less decisive than the previous version implied, that is stated plainly rather
than papered over — a weaker, more honest conclusion is the point of this correction.

---

## 1. Existing research context — what this pass does not re-litigate

[`frontend-product-ux-research.md`](frontend-product-ux-research.md) and
[`product-information-architecture.md`](product-information-architecture.md) (phone-focused, already
adversarially reviewed once) already establish, and this document treats as settled input:

- **Native-client direction** ([ADR-0001](../architecture/adr/0001-native-client-architecture.md)):
  no shared cross-platform UI/core technology. A web client, if built, is a separate implementation
  sharing product model and identity with phone/watch, not shared code.
- **`LocalAccount`-scoped local ownership** — already implemented on phone, a real Room FK.
- **Copy-creates-independent-object precedent** — Hevy's "Save as Routine"/"Copy Workout" verbs,
  already validated in the phone research as real vendor evidence (mobile app, not web-specific).
- **Follow-only social direction** and the **`PRIVATE / FOLLOWERS / PUBLIC` visibility model**.
- **PD-001/PD-002's local-first execution direction**: no permanent Phone execution authority; Watch
  can be first-class; sync is not durability; execution remains locally durable. This document does
  not redesign these rules — it only asks where, if anywhere, Web participates.

**Genuinely web-specific and unanswered**, and therefore this document's actual scope: what do
Hevy, Strong, and Fitbod specifically — TBDFit's own primary mobile comparators — actually do on the
web, if anything; does that evidence support workout execution on web; and what, if anything, should
TBDFit build there.

**The competitor benchmark for mobile contains no existing web-specific findings** (checked
directly — [`competitor-benchmark-hevy-strong-fitbod.md`](competitor-benchmark-hevy-strong-fitbod.md)
has zero mentions of "web," "browser," or "desktop"), so this document starts from zero prior
internal evidence on the topic, not from a gap in an existing web section.

**One relevant piece of internal evidence already on record**:
[ADR-0001](../architecture/adr/0001-native-client-architecture.md) names *"a possible future desktop
application for planning, history review, and analytics"* as a future, separately-decided surface.
That sentence predates any web research; this document is the first evidence-based test of it.

---

## 2. Evidence-access note and discipline

Every direct fetch attempted against hevy.com, strong.app, app.fitbod.me, and their respective help
centers returned **HTTP 403 Forbidden** in this research pass (consistent with the prior pass's own
note — these properties appear to block automated fetching generally, not selectively). No primary
source could be retrieved and read in full. All findings below are therefore **search-engine-synthesized**
— built from search result summaries citing (but not always quoting verbatim from) official help-center
articles, App Store listings, and vendor blog/feature pages — not primary-source full-text
verification. This is explicitly a lower confidence tier than a directly-fetched quote, and is
labeled as such throughout rather than presented as equivalent.

Two disciplines applied strictly throughout this document, per explicit instruction:

1. **"Not found" is never "NO."** Where a capability was searched for and not surfaced, the entry is
   `UNKNOWN` or `NOT VERIFIED`, never `NO`. `NO` is reserved for cases with a specific, repeated,
   converging signal of absence (e.g., Strong's platform list, checked from multiple independent
   angles, never once includes a web/browser/desktop surface).
2. **Live web execution is never assumed from bundled/general marketing language.** Several
   search-synthesized answers described "workout logging" or "training" as available "across
   platforms including web" in ways that, on inspection, bundle general product capability with a
   specific execution claim no single source actually substantiates. Every such instance below is
   flagged and downgraded rather than accepted at face value.

---

## 3. Hevy web

**Important distinction the prior version of this document blurred**: **Hevy** (the consumer
product, `hevy.com`) and **Hevy Coach** (`hevycoach.com`, a separate coach-facing SaaS product for
personal trainers to manage clients) are two different products built by the same company. The prior
document cited Hevy Coach marketing pages ("Client App," "Client Management") as if they described
the ordinary consumer web experience. They do not — they describe a coaching platform's own desktop
dashboard and its clients' mobile app usage. This section covers **consumer Hevy web (`hevy.com`)**
only; Hevy Coach is addressed separately in §12 (Supplementary References) as a distinct, coach-facing
product.

| Capability | Status | Evidence |
|---|---|---|
| Authentication | **VERIFIED** | `hevy.com` supports login via email/password, Google, or Apple, consistent across search results (medium confidence, search-synthesized; direct fetch of hevy.com returned 403). |
| Dashboard/home | **VERIFIED** (some landing surface exists) | Multiple independent search queries converge on "a full desktop view of your workouts at hevy.com" as the landing experience after login. |
| Workout logging (after the fact / manual) | **UNKNOWN** | No source specifically confirms manual/retrospective set entry in the browser, as distinct from routine creation/editing (below). |
| **Live workout execution** | **NOT VERIFIED** | Every specific, repeated description of "logging a workout" (add exercise → add sets → log reps/weight → mark complete → rest timer) that this pass found is attributed to the **mobile app** experience, never to hevy.com specifically. One single aggregated search answer stated live-logging exists "across platforms including web," but this could not be corroborated by any other query or a specific citation, and is inconsistent with every other, more specific finding — treated as an artifact of search-synthesis over-bundling, not evidence. Per the explicit instruction not to assume live execution without direct support: `LIVE WEB EXECUTION: NOT VERIFIED`. |
| Routine creation | **VERIFIED** | Recurring, independently-converging description: hevy.com lets a signed-in user "create routines." |
| Routine editing | **VERIFIED** | Editing exercises, sets, rep targets, and rest periods within a routine is described as available; not explicitly platform-confirmed as web-exclusive-or-inclusive, but bundled consistently with the same "hevy.com" capability list across multiple independent search results. |
| Planning (multi-day/program-level) | **UNKNOWN** | No evidence found of a multi-day or periodized planning surface on hevy.com specifically; Hevy's routine model does not appear to include a Program layer above Routine in any source found. |
| Exercise library | **UNKNOWN** for web specifically | The exercise library is well-documented as a general Hevy product feature; no source specifically confirms it is browsable/manageable on hevy.com as distinct from the app. |
| Workout history | **VERIFIED** | Recurring description: hevy.com lets a user "review their workouts" and see past workouts via profile. |
| Workout detail | **VERIFIED** (medium confidence) | Tapping into a listed workout to see details, likes, and comments is described in the context of the profile/history view. |
| Exercise progress | **UNKNOWN** for web specifically | Per-exercise progress tracking (heaviest weight, best set, estimated 1RM) is well-documented as a general Hevy feature; not specifically confirmed on hevy.com. |
| Analytics/charts | **UNKNOWN** for web specifically | Bar-graph/volume/muscle-distribution analytics are well-documented as a general Hevy product feature (from `hevyapp.com` feature pages describing the product broadly); no source in this pass specifically confirmed these charts render on hevy.com rather than only in the mobile app. This is a meaningful gap, not a minor one — Hevy's most web-suited feature (analytics) is exactly the one this pass could not confirm is actually on the web. |
| PRs | **UNKNOWN** for web specifically | Same caveat as analytics — well-documented generally, not web-confirmed. |
| Own profile | **VERIFIED** | "Edit their profile settings" is part of the recurring hevy.com capability description. |
| Other profiles | **UNKNOWN** | Not specifically addressed by any source found for the web surface. |
| Feed/social activity | **VERIFIED** | "See recent updates from people they follow" recurs across independent search results as a stated hevy.com capability. |
| Following | **UNKNOWN** for web specifically (following/follow-management action itself, as distinct from viewing feed content) | Viewing followed users' updates is verified (above); whether the follow/unfollow action itself is performable on hevy.com was not specifically confirmed. |
| Copying/saving workouts | **UNKNOWN** for web specifically | Well-documented as a general Hevy mobile-app feature (see phone research); not web-confirmed. |
| Saving routines | **VERIFIED** | Routine creation/editing on hevy.com (above) inherently implies save; explicitly part of the recurring capability description. |
| Settings | **VERIFIED** | "Edit their profile settings" recurs; general account settings presumed included but not itemized by any source. |
| Account/subscription | **UNKNOWN** for web specifically | Not directly addressed for hevy.com; Hevy's subscription management surface was not investigated in this pass. |

**Recurring core finding (medium confidence, search-synthesized, converging across independent
queries but never directly fetched)**: `hevy.com` supports a genuinely broad **plan + review +
light-social** experience — create/edit routines, review past workouts and performance, edit
profile, see followed users' feed activity — but **no source found in this pass, however phrased,
specifically and reliably confirms live, real-time set-by-set workout execution happens in the
browser.** This is the single most load-bearing finding of this document: among the three primary
comparators, Hevy is the only one with a genuinely broad consumer web surface, and even there,
execution is not verified.

---

## 4. Strong web

**`WEB TRAINING CLIENT: NOT VERIFIED`** — and this is the strongest absence-leaning finding in this
document, though still stated as `NOT VERIFIED` rather than `verified absent`, per the instruction
that silence must never become "NO."

Official/near-official sources consistently describe Strong's platforms as **iPhone, Android, and
Apple Watch only** — no source found across multiple independent search angles (official site,
official help center, App Store listing context, Apple Watch companion documentation) ever mentions
a web, browser, or desktop surface for actually training.

**"Strong Cloud" / "Strong Account" — investigated specifically, and it is a sync/backup mechanism,
not a web client.** The Strong Help Center explains a Strong Account exists to keep workout data
"automatically synced between devices" and is required to "share your workouts or templates with
others" — this is backend account/sync infrastructure, not a browser-accessible training surface. No
source describes logging in to any Strong website to train, view history, edit routines, or view
analytics.

| Capability | Status | Evidence |
|---|---|---|
| Authentication (web) | **NOT VERIFIED** | No web login surface found; "Strong Account" is confirmed but its access surface is the mobile app, not a website. |
| Dashboard/home (web) | **NOT VERIFIED** | No evidence of any web landing page beyond the marketing site (`strong.app`, which is not an authenticated product surface). |
| Live workout execution (web) | **NOT VERIFIED**, strongly leaning absent | No web training surface of any kind was found. |
| Routine editing (web) | **NOT VERIFIED** | Same. |
| History (web) | **NOT VERIFIED** | Same. |
| Analytics (web) | **NOT VERIFIED** | Same. |
| Account/subscription (web) | **UNKNOWN** | Not specifically investigated separately from the "Strong Cloud" sync mechanism; plausible a subscription-management web page exists (common for App Store apps to offer web-based subscription management) but not confirmed either way. |

Strong — TBDFit's closest minimal-logging-UX sibling per the phone research — appears to be a
genuine example of a successful, mobile-only product with no meaningful web presence at all beyond a
marketing site and cloud sync infrastructure that itself has no browser UI.

---

## 5. Fitbod web

Fitbod's web surface (`app.fitbod.me`) is real and confirmed to exist, but its **purpose is
consistently and repeatedly described as account creation, login, and subscription management** —
never as a place training actually happens.

| Capability | Status | Evidence |
|---|---|---|
| Signup | **VERIFIED** | `app.fitbod.me` is confirmed as a real, reachable login/signup surface across multiple independent searches; a dedicated `app.fitbod.me/login` page was specifically identified. |
| Login | **VERIFIED** | Same. |
| Onboarding (questionnaire) | **UNKNOWN** for web specifically | The onboarding questionnaire (equipment, goals, experience level) is well-documented as a general Fitbod product flow; not specifically confirmed to run inside the browser at app.fitbod.me as opposed to only the mobile app after a web signup hands off to it. |
| Subscription | **VERIFIED** | A dedicated official help-center article exists specifically titled "What's the difference between subscribing through the app vs. the Fitbod website?" — direct evidence that the website is a recognized, distinct subscription-purchase surface. Pricing (monthly/annual) is confirmed purchasable via the website. |
| Account management | **VERIFIED** | Login/account access at `app.fitbod.me` is confirmed. |
| Workout generation | **NOT VERIFIED** on web specifically | Fitbod's core AI-generated-workout mechanic is extremely well documented as a general product capability, but every specific description of the generation flow (the equipment question → generated session → muscle-recovery map → sets/reps/weight targets → video demos → rest timer) that this pass found is framed around "the app," never specifically confirmed to run in a browser at app.fitbod.me. |
| Routine/program planning | **NOT VERIFIED** on web | Same caveat; Fitbod's model is algorithmic per-session generation rather than a user-edited Routine object in the Hevy/Strong sense, and no source confirms this generation happens in-browser. |
| History | **UNKNOWN** | Not specifically addressed for the web surface by any source found. |
| Progress | **UNKNOWN** | Same. |
| Analytics | **UNKNOWN** | Same. |
| Live workout execution | **NOT VERIFIED** | No source found describes doing a Fitbod workout in a browser; every description of actually training is framed around the mobile app. |

**Core finding**: the evidence is consistent with app.fitbod.me being an **account/subscription
portal that hands off to the mobile app for all actual training**, but this pass could not find a
source that explicitly states the web app does *not* also render the training/generation experience
— so this remains `NOT VERIFIED` rather than a confirmed absence, exactly as instructed. The
distinction Fitbod's own help center draws between "subscribing through the app vs. the website" is
the strongest single piece of evidence for a portal-style role, since it implies the website's job is
specifically the commerce/account transaction, with training handled elsewhere.

---

## 6. Corrected capability matrix

`FULL` / `PARTIAL` / `NO` / `UNKNOWN`. `NO` used only where evidence specifically supports absence.

| Capability | Hevy Web | Strong Web | Fitbod Web | Confidence |
|---|---|---|---|---|
| Authentication | PARTIAL (login confirmed; no further detail) | UNKNOWN | FULL (login + signup + subscription confirmed) | Medium (search-synthesized throughout) |
| Dashboard | PARTIAL (a "full desktop view" is described, content depth unconfirmed) | NO web surface found at all | UNKNOWN (portal-style page likely, training dashboard not confirmed) | Medium (Hevy, Fitbod); Medium-high (Strong's absence) |
| Live workout execution | UNKNOWN (leans NOT VERIFIED — no reliable specific evidence found) | UNKNOWN (no web surface found to execute on) | UNKNOWN (no reliable specific evidence found) | Low-medium — this is the single most important row and the least confidently answered for all three |
| Log sets (after the fact) | UNKNOWN | UNKNOWN | UNKNOWN | Low |
| Routine builder | PARTIAL (create + edit both described) | NO web surface found | UNKNOWN (Fitbod's model is algorithmic generation, not a user-built Routine object) | Medium (Hevy); Medium-high (Strong absence) |
| Program planning | UNKNOWN (no multi-day/program layer evidenced for Hevy at all, web or app) | UNKNOWN | UNKNOWN | Low |
| Exercise library | UNKNOWN | UNKNOWN | UNKNOWN | Low |
| History | PARTIAL (review of past workouts described) | UNKNOWN | UNKNOWN | Medium (Hevy) |
| Workout detail | PARTIAL | UNKNOWN | UNKNOWN | Medium (Hevy) |
| Exercise progress | UNKNOWN | UNKNOWN | UNKNOWN | Low |
| Analytics | UNKNOWN (a real gap — Hevy's analytics are well-documented generally but not web-confirmed) | UNKNOWN | UNKNOWN | Low |
| PRs | UNKNOWN | UNKNOWN | UNKNOWN | Low |
| Own profile | PARTIAL (edit profile settings described) | UNKNOWN | UNKNOWN | Medium (Hevy) |
| Other profiles | UNKNOWN | UNKNOWN | UNKNOWN | Low |
| Feed/social | PARTIAL (view followed users' updates described) | NO evidence of any social feature at all for Strong | UNKNOWN | Medium (Hevy); Medium (Strong — Strong is not primarily a social product in any source found) |
| Follow users | UNKNOWN (viewing feed confirmed; the follow action itself not specifically confirmed on web) | UNKNOWN | UNKNOWN | Low-medium |
| Copy/save workout | UNKNOWN for web (verified on mobile per phone research) | UNKNOWN | UNKNOWN | Low |
| Save routine | PARTIAL | NO web surface found | UNKNOWN | Medium (Hevy) |
| Settings | PARTIAL | UNKNOWN | UNKNOWN | Medium (Hevy) |
| Subscription | UNKNOWN | UNKNOWN | FULL (dedicated help article confirms website purchase path) | High (Fitbod) |

---

## 7. Web strategy comparison

| Product | Best-evidenced strategy | Why |
|---|---|---|
| **Hevy** | **Broad companion client (plan + review + light social), not a full training client** — none of the five suggested labels fit exactly; closest is "PLANNING/ANALYSIS COMPANION" but Hevy's evidenced web scope is broader than that label implies (it includes profile/social, not just planning/analysis), while still excluding execution. | Hevy's manual-logging mobile app already owns execution; the web surface appears to extend the same account into planning, review, and lightweight social participation without competing for the training moment itself. This is a plausible, low-risk way to give power users (routine builders, people reviewing progress, people engaging with the social feed) a better-suited surface without touching the core mobile logging loop. |
| **Strong** | **MOBILE-FIRST, NO WEB TRAINING SURFACE** — even "limited web" overclaims what evidence supports; the honest label is closer to no web product at all beyond marketing/sync infrastructure. | Strong's own positioning (per the phone research) is minimal, fast, uncluttered logging. A team with that philosophy may have made a deliberate choice that a second, browser-based surface adds engineering and design-consistency cost without serving that core value proposition — but this is INFERENCE, not confirmed by any source; no official statement of *why* was found. |
| **Fitbod** | **ACCOUNT/SUBSCRIPTION PORTAL** | The specific, named help-center distinction between "subscribing through the app vs. the website" is the clearest single piece of evidence in this entire research pass — it implies the website's confirmed job is the commerce/account transaction. Fitbod's core value (AI-generated, adaptive sessions) plausibly benefits less from a second surface than Hevy's routine-building/social use cases do, since generation is inherently tied to recent logged performance, which itself is not confirmed to be enterable on web. |

---

## 8. Planning comparison (Exercise → Routine → Program)

- **Hevy**: real, evidenced web routine creation and editing (exercises, sets, rep targets, rest
  periods). No evidence of a Program layer (multi-day/periodized planning) above Routine in either
  the web or mobile experience, for Hevy specifically. Reorder UX for exercises within a routine is
  plausible given "editing exercises" is confirmed, but not itemized by any source found.
- **Strong**: no web planning surface exists at all per this research; Strong's routine/template
  editing (well documented in the phone research) is a mobile-app-only concern as far as this pass
  can determine.
- **Fitbod**: fundamentally different planning model — algorithmic per-session generation rather than
  a user-authored Routine object. There is effectively nothing to "edit" in the Hevy/Strong sense;
  Fitbod's "planning" is the recovery/goal model driving what gets generated next, not a document a
  user directly manipulates. Whether this generation model is exposed on web at all is unconfirmed.
- **Does web give a genuine planning advantage for any of the three?** Only demonstrably for Hevy,
  and only at medium confidence. This is meaningfully weaker evidence than the prior (incorrectly
  sourced) version of this document implied — one of three primary comparators, not a broad
  competitive consensus.

## 9. History/progress comparison

- **Hevy**: workout history review and workout detail are evidenced for the web surface specifically
  (medium confidence). Analytics/charts/PRs are well-documented as general Hevy features but **not**
  confirmed to render on the web surface — a genuine, load-bearing gap in this research, not a minor
  omission: exactly the feature category (bulk chart/analytics review) that would most benefit from a
  browser is the one most in question here.
- **Strong**: no web history/progress surface found.
- **Fitbod**: no web history/progress surface confirmed either way.
- **Browser vs. mobile capability, kept distinct**: for all three products, essentially all detailed
  analytics/progress feature descriptions found in this pass come from general product marketing/help
  content that does not specify platform — this document does not assume such content describes the
  web surface just because a web surface exists elsewhere for that product.

## 10. Social/profile comparison

- **Hevy**: own profile editing and viewing followed users' feed updates are evidenced for the web
  surface specifically (medium confidence). Public/other-user profile viewing, and the follow/unfollow
  action itself (as distinct from viewing existing feed content), are unconfirmed for web.
- **Strong**: no evidence of any social/profile feature at all, on any platform, in this pass — Strong
  does not appear to be a social product.
- **Fitbod**: no social/profile web evidence found.
- This comparison does **not**, on its own, imply TBDFit needs a dedicated social nav destination on
  any client — consistent with the phone research's own finding that social is folded into
  Home/Profile everywhere it appears at all, not given peer-weight navigation.

---

## 11. Does execution belong on web? (open research question, treated honestly)

**This is the single most important correction in this document.** The prior version concluded "No,
at HIGH confidence" — but that conclusion was built primarily on TrainingPeaks/Strava/Garmin/
TrainHeroic/Boostcamp, not on Hevy/Strong/Fitbod. Redone against the correct primary evidence base:

- **Hevy**: `NOT VERIFIED` — no reliable evidence either confirms or specifically denies live web
  execution. The broad, real plan/review/social web surface makes Hevy the one product where this
  question is genuinely open, not settled.
- **Strong**: `NOT VERIFIED`, strongly leaning toward absent, because no web training surface of any
  kind was found — but this is an absence of evidence for a whole web product, not a specific
  statement that execution was considered and rejected.
- **Fitbod**: `NOT VERIFIED` — evidence points toward the web surface's confirmed job being
  account/subscription, but does not rule out training capability existing there too.

**Per the explicit instruction not to conclude "TBDFit Web should never support execution" from
competitor absence alone**: this document does not do so. What it says instead:

- The corrected primary-three evidence is **inconclusive**, not a unanimous prohibition. This is
  meaningfully different from, and weaker than, the prior version's claim.
- A **separate, genuine product reason** does exist to be cautious about web execution, independent
  of competitor precedent: entering weight/reps/completion mid-set on a desktop browser, away from
  the gym floor, is a plausible poor fit for the actual physical context of lifting (no phone need be
  at the rack; a laptop even less so) — but this is **INFERENCE about TBDFit's own use context**, not
  a competitor-derived fact, and it has not been validated with any TBDFit user.
- **TBDFIT RECOMMENDATION**: do not plan web execution now, but hold this as a **revisable
  hypothesis** (MEDIUM confidence at most), not a hard architectural rule. If a future need emerges
  (e.g., a coach/desk-based use case, or evidence that users want to log a forgotten workout
  retroactively from a browser — which is different from *live* execution and was not investigated
  separately here), this should be revisited on its own evidence, not treated as foreclosed by this
  document.

---

## 12. TBDFit web options (from corrected evidence)

### A — Account/minimal portal

Login, subscription, account settings only — mirroring Fitbod's and Strong's evidenced (or
evidenced-absent) web patterns. No training-adjacent content at all.

- **User value**: low but real (password reset, subscription management without opening the app).
- **Implementation cost**: lowest of the three.
- **Overlap with Phone/Watch**: none — this is the one option with zero functional overlap.
- **Planning/analysis/social value**: none.
- **Execution implications**: none — moot.
- **Future scalability**: poor — would need to be substantially rebuilt, not extended, if TBDFit ever
  wants Hevy-style plan/review value later.

### B — Planning + Review + light-social companion

Routine/Program building and editing, history/workout-detail review, profile editing, feed viewing —
mirroring Hevy's own evidenced (though only medium-confidence) web pattern. No execution.

- **User value**: the only option with a real, evidenced precedent (Hevy) among TBDFit's own primary
  mobile comparators — meaningful if TBDFit's own Routine/history features reach comparable depth.
- **Implementation cost**: substantial — a real second client surface, though scoped away from the
  hardest problem (execution).
- **Overlap with Phone**: routine editing and history review would exist on both Phone and Web
  (acceptable per the shared-capability framing already established in the phone/watch research — no
  client needs exclusive ownership of a capability).
- **Overlap with Watch**: none — Watch remains execution-only in every scenario considered.
- **Planning/analysis value**: the strongest of the three options, directly evidenced.
- **Execution implications**: explicitly out of scope, consistent with §11's cautious-but-revisable
  stance.
- **Future scalability**: good — this is the shape a coach-facing or multi-week-Program layer could
  extend into later, without a rewrite.

### C — Broad first-class client (potentially including execution)

A full-parity ambition, potentially eventually including execution, differentiating TBDFit from all
three primary comparators rather than following any of their precedents.

- **User value**: speculative — no primary-three evidence supports this being what users of this
  product category want from a browser; would be a genuine TBDFit product bet, not a validated
  pattern.
- **Implementation cost**: highest, and specifically includes the hardest, least-precedented problem
  (web execution) that §11 found to be genuinely unresolved rather than solved by anyone examined.
- **Overlap with Phone/Watch**: total, by design — this option treats Web as a true peer to both,
  which cuts directly against every other research pass's device-specialization finding.
- **Future scalability**: unknown — no precedent to extrapolate from.

### Recommended direction

**CURRENT PRODUCT HYPOTHESIS: Option B**, directly because it is the only option matching a real,
evidenced pattern from TBDFit's own closest primary comparator (Hevy), not because it is the most
ambitious or the safest. This is explicitly **not** Accepted truth — it rests on medium-confidence,
search-synthesized evidence for exactly one of three primary comparators, which is a real and
stated limitation of this research pass, not a settled conclusion.

---

## 13. Supplementary references

The following products were examined in the prior (incorrect) version of this document and remain
useful as **secondary, non-driving** context — they must not be read as supporting any conclusion
above on their own:

| Product | Relevant pattern | Status |
|---|---|---|
| TrainingPeaks | Web-first coaching/planning platform; hard mobile/desktop split (mobile browsers unsupported); execution confirmed to happen only on the mobile app, planning/calendar confirmed on web. | Supplementary — informs the *plausibility* of Option B's long-term sidebar shape, does not drive the Hevy/Strong/Fitbod-based recommendation above. |
| Strava | Full web dashboard (Training Log, segment analysis, goals); confirmed no live recording support on web. | Supplementary. |
| Garmin Connect | Web portal for history/reports/training status; device-first, web never records. | Supplementary. |
| TrainHeroic | Coach-facing desktop programming tools, explicitly contrasted with lighter mobile editing. | Supplementary — relevant only if/when a coach-facing capability is ever prioritized (FUTURE, per the phone IA's own athlete/creator open decision). |
| Boostcamp | Added a dedicated "Web Program Creator" to an already-mobile-only product, explicitly for the same "hard to build a multi-week plan on a small screen" reason. | Supplementary — the single most structurally analogous precedent to TBDFit's own likely situation (mobile-first product, web added later, specifically for planning), but still explicitly not one of the three primary comparators and not the basis for §11's execution finding. |
| Hevy Coach | A **separate, coach-facing product** from the same company as consumer Hevy — desktop dashboard for building/assigning programs to clients, distinct from `hevy.com`. Wrongly cited in the prior version as if describing consumer Hevy web behavior; corrected here. | Supplementary — relevant only to a future coach/creator capability, not to ordinary-user web scope. |
| Spreadsheets (practice, not a product) | A real, current planning practice among serious lifters, cited as illustrative status-quo evidence (single-author opinion piece, not a survey). | Supplementary context on what a TBDFit web planning surface would need to beat, not a competitor. |

---

## 14. Domain/backend implications discovered (discovery only — not implemented)

| Web capability (if Option B is ever built) | Likely future domain implications |
|---|---|
| Build/edit a Routine on web, execute on Phone/Watch | Requires the same `Routine`/`RoutineExercise` entity already identified as POST-MVP in the phone research, plus the still-undecided cross-device sync mechanism PD-001 leaves open — this is not a new discovery, but web is the first scenario in this research where it is concretely required. |
| Web account/session | `LocalAccount` likely has no web equivalent — web is not a durable-execution client the way Phone/Watch are, so it may not need a local shadow-identity table; a backend-session-authoritative model is plausible. Flagged, not decided. |
| History/analytics review on web | Requires backend replication of Phone-originated `Workout`/`WorkoutSet` data, which does not exist yet — arguably a more load-bearing missing piece than any web-navigation question. |

None of the above is implemented, migrated, or scheduled by this document.

---

## Sources

Primary-three sources (search-synthesized; every direct fetch attempt returned HTTP 403):

- Hevy: `hevy.com`, `help.hevyapp.com`, `www.hevyapp.com/features/*` (general product features, not
  all web-specific — see per-capability notes above for which claims are web-attributed vs. general)
- Strong: `www.strong.app`, `help.strongapp.io` (articles: "What is Strong?", "About Strong for Apple
  Watch," "Why do I need a Strong account?")
- Fitbod: `app.fitbod.me`, `app.fitbod.me/login`, `help.fitbod.me` (articles: "How to Sign Up & Log
  in," "What's the difference between subscribing through the app vs. the Fitbod website?")

Supplementary sources (see §13; retained from the prior version's citation list — unchanged):

- TrainingPeaks Help Center — Logging your Strength Training Session; What browsers are compatible
  with TrainingPeaks?; `app.trainingpeaks.com`
- Strava Help Center — Recording an Activity; Training Log; Uploading Manual Activities
- Garmin Support — Training Status; Garmin blog — Unlocking the Potential of Garmin Connect;
  `connect.garmin.com`
- TrainHeroic — Coach; Create a Library of Training Programs
- Hevy Coach — Client App; Client Management; Coach Dashboard (a *separate* product from consumer
  Hevy — see §13)
- Boostcamp — Workout Program Creator; Fitt Insider press coverage *(search-synthesized)*
- TechRadar — spreadsheet-logging opinion piece

---

## Status

**STATUS: RESEARCH — NOT A PRODUCT DECISION.** This document does not promote, accept, or modify any
entry in `docs/product/decisions.md`, any ADR, or the strength-workout-first-slice design. It exists
solely to inform [`web-information-architecture.md`](web-information-architecture.md) and the
multi-client product vision. It replaces and corrects the prior version's benchmark scope; where
its conclusions are weaker or more uncertain than the prior version's, that is intentional and
reflects the corrected evidence base, not an oversight.
