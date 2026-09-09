# Frontend / Product UX Research

**STATUS: RESEARCH — NOT A PRODUCT DECISION**

This is a research document, not an ADR and not a product decision. It exists to inform
[`docs/product/product-information-architecture.md`](product-information-architecture.md) and
future frontend design work. Nothing here is accepted. Where a recommendation appears, it is
product/design *judgment* based on the evidence gathered, not a decision.

Classification vocabulary, reused from
[`competitor-benchmark-hevy-strong-fitbod.md`](competitor-benchmark-hevy-strong-fitbod.md):
**FACT** (observed/documented product behavior), **INFERENCE** (a reasoned conclusion drawn from
facts), **RECOMMENDATION** (design judgment for TBDFit, not a decision), **OPEN DECISION**
(requires developer/product input).

---

## 1. Existing research gap

[`competitor-benchmark-hevy-strong-fitbod.md`](competitor-benchmark-hevy-strong-fitbod.md) already
answers, in depth, with an explicit evidence-tier discipline this document reuses:

- Logging-loop UX for Hevy/Strong/Fitbod specifically (tap count, previous-performance display,
  rest timer).
- Exercise-library/identity risk (Hevy's documented duplicate-exercise problem).
- Routine-vs-execution ambiguity (Strong's own acknowledged confusion).
- Watch capability vs. watch reliability, kept explicitly separate.
- Pricing/subscription friction, data-loss precedents, and cross-app switching patterns.

It does **not** answer, and was never scoped to answer:

- How any of these products organize their **overall navigation/information architecture** (tab
  bars, top-level destinations, what's nested vs. first-class).
- **Social/profile mechanics** at all — followers, feeds, public/private visibility, discovery of
  other users. None of Hevy/Strong/Fitbod's social surfaces were investigated in that document.
- **Routines/programs as a shared, discoverable, ownable object** (creating, browsing, saving,
  copying) — the prior document deliberately deferred routines/templates as a feature entirely.
- **Athlete/creator/coach ecosystems** (Boostcamp, TrainHeroic) — not examined at all previously.
- **Cross-app navigation patterns for phone-vs-watch responsibility** beyond the capability/
  reliability split already covered — this document adds the *navigational* question: which
  screens live on which device.
- A **screen inventory or MVP scope recommendation** for TBDFit's actual frontend.

This document exists specifically to close those gaps. It reuses, rather than re-verifies, the
prior document's Hevy/Strong/Fitbod official-behavior findings where they're still relevant (e.g.
routine/workout split, watch independence) and does not re-litigate them.

**Evidence-access note**: like the prior benchmark, direct fetch access to Reddit, app store review
bodies, and several vendor community pages was blocked in this research pass. Findings below rely on
official help-center pages, vendor marketing pages, and app-store listings (all Layer-1/FACT-tier
for the specific behavior they describe), plus indexed search-result synthesis for anything softer.
No primary user-sentiment text was read for this pass either — the same limitation the prior
document names. Where a claim is genuinely just observed product behavior from an official source,
it is marked FACT; nothing here should be read as verified user sentiment.

---

## 2. Competitors reviewed

| Product | Why it was included | Primary focus of this pass |
|---|---|---|
| Hevy | Logging + social + profiles + feed + sharing/copying, all in one product | Navigation, social/feed mechanics, routine sharing/copying |
| Strong | Minimal logging UX, independent watch execution | Navigation, active-workout screen shape |
| Fitbod | Recommendations/generated workouts, exercise substitution | Navigation, substitution/override UX |
| Strava | Mature social graph, feed, public/private visibility model | Privacy tiers, clubs/groups, profile structure |
| Boostcamp | Community programs, creator-authored plans, has *just* added a social feed | Program discovery/marketplace, creator model, nascent social layer |
| TrainHeroic | Full coach↔athlete ecosystem, marketplace | Coach/athlete relationship, program delivery, communication |
| Apple Fitness / Watch Workout app | Native phone↔watch handoff, independent watch execution | Phone vs. watch responsibility split, device-native history |
| Garmin Connect | Device-first activity recording, phone as companion/analysis surface | Phone-as-browser-not-logger pattern, training-status framing |

No ninth product was added — the eight above already cover every UX pattern the brief named
(manual logging, algorithmic logging, social graph, community/creator programs, coach/athlete
software, and two different phone/watch relationship models).

---

## 3. Key UX findings

### Navigation structures observed (FACT, from official sources)

- **Hevy**: bottom navigation includes a **Home** tab (a content feed of people you follow — workout
  cards with duration/volume/PR overview), a **Workout** tab (start an empty workout *or* start a
  saved Routine, with an "Explore" entry point into a routine/program library), a **Discover** /
  Discovery Feed surface for finding new users to follow, and a **Profile** tab showing workout
  count, followers, following, and a bio. [Hevy Help Centre — Social Guide](https://help.hevyapp.com/hc/en-us/articles/35688036014231-Hevy-App-Social-Guide-Connect-Follow-and-Share-Your-Workouts),
  [Hevy — Workouts vs Routines](https://help.hevyapp.com/hc/en-us/articles/33703513582871-Workouts-vs-Routines-in-Hevy-What-They-Mean-and-How-to-Use-Them),
  [Hevy — Discovery Feed](https://www.hevyapp.com/features/discovery-feed/),
  [Hevy — User Profiles](https://www.hevyapp.com/features/user-profiles/).
- **Strong**: a **History** section (calendar + past workouts, tap-through to set-by-set detail), a
  **Routines** section, and an active-workout **Overview** screen once a routine (or empty workout)
  is started; per-exercise detail has its own **About / History / Charts / Records** sub-tabs.
  [Strong Help Center — Exercise Detail](https://help.strongapp.io/article/237-about-exercise-detail).
  No dedicated social surface exists in Strong at all (consistent with the prior benchmark's finding
  that Strong is a pure manual logger).
- **Fitbod**: the **Workout** tab is the landing/home surface (today's algorithm-generated session);
  a separate **Log** tab holds workout history. [Fitbod Blog — "A Better Workout Tab"](https://fitbod.me/blog/a-better-workout-tab/).
  No social surface exists.
- **Strava**: **Home** (feed), **Groups** (clubs), and **You** (profile + settings, reached via a
  gear icon) are the primary mobile tabs. [Strava — Clubs on the Mobile App](https://support.strava.com/en-us/articles/15401837-clubs-on-the-mobile-app).
  Notably, Strava does **not** give "Social/Feed" its own top-level identity distinct from Home —
  the feed *is* Home.
- **Boostcamp**: primarily organized around **programs** (11,000+, filterable by coach/goal) as the
  core browsing surface, with history/progress tracking alongside; a **Community** feed (follow
  friends, share completed workouts) was added recently as an explicit new surface, not part of the
  original structure. [Boostcamp — Programs](https://www.boostcamp.app/programs), [Fortune —
  Boostcamp Review 2026](https://fortune.com/article/boostcamp-review/).
- **TrainHeroic**: structured around a coach-authored **program/calendar** delivered to the athlete
  (an access code links an athlete to a coach's team), plus **TH Chat** for coach↔athlete
  communication, plus a **Marketplace** for discovering/purchasing programs from coaches directly.
  [TrainHeroic — Athlete FAQ](https://www.trainheroic.com/athlete-faq/), [TrainHeroic — Coach FAQ](https://www.trainheroic.com/coach-faq/).
- **Apple Fitness (iPhone)**: the Fitness app's primary organization is **Summary** (activity
  rings) and a **Sessions** list (workout history) — there is no separate "start a workout" surface
  on iPhone; workouts are started on the Watch. [Apple Support — Get started with Fitness on
  iPhone](https://support.apple.com/guide/iphone/get-started-with-fitness-ipha5dddb411/ios).
- **Garmin Connect**: organized around **Activities/History**, **Training Status**, and
  **Navigation**, with the phone app functioning as an analysis/history surface for data
  *originating on the watch*, not a place workouts are started or logged from. [Garmin Support —
  Training Status](https://support.garmin.com/en-US/?faq=VxKazDQ2mkAmDoQbJriEBA).

**INFERENCE**: across every product that has a social layer (Hevy, Strava, and now Boostcamp),
**"social" is never a bare top-level concept called "Social."** It is either the content of the
Home feed (Strava, Hevy) or a distinctly-named added surface bolted on later once the core product
was already established (Boostcamp's "Community"). No product in this set promotes social to a
peer-level tab with the same weight as the core logging/training function.

### Routine/program sharing mechanics (FACT)

Hevy's copy mechanics are the clearest documented example of the exact distinction section 8 of the
brief asks about:

- **"Save as Routine"** on someone else's workout stores it as a reusable template *in your own
  profile* — a new object you own, not a live reference to theirs.
- **"Copy Workout"** starts a new active session pre-filled with the same structure, which you then
  log independently.
- Sharing a routine via link shares **exercises only — not the logged reps/weights**.

[Hevy Help Centre — How to Share Workouts and Routines](https://help.hevyapp.com/hc/en-us/articles/34953501503895-How-to-Share-Workouts-and-Routines-Step-by-Step),
[Hevy — Share Folders & Routines](https://www.hevyapp.com/features/share-folders-routines/).

**INFERENCE**: this is real vendor evidence for the exact ownership model the brief asks TBDFit to
adopt — copying/saving another user's workout or routine produces the recipient's own independent
object, never shared mutable ownership. This is not merely a TBDFit design preference; it's the
pattern the most social-native competitor in this set already ships.

---

## 4. Product areas — analysis (not all deserve first-class navigation)

Per section 3/4 of the brief, evaluated against what was actually observed:

| Area | Observed treatment across competitors | TBDFit implication |
|---|---|---|
| Home | Universally the landing surface; either a feed (Hevy/Strava) or "today's workout" (Fitbod) | Home's *meaning* depends on whether social exists yet — see IA options below |
| Workout | Always a first-class tab/surface; the single most important screen in every product | Must be first-class, always |
| Routines/Programs | First-class in Hevy/Strong/Boostcamp/TrainHeroic; **absent** in Fitbod (generation replaces it) and Garmin (device-driven) | Not universal — depends on whether TBDFit is manual-plan or generation-based (TBDFit is manual, per the existing design doc) |
| Exercise discovery | Never a top-level tab anywhere — always nested inside the workout/routine-building flow | Confirms: do not give this its own tab |
| History | First-class in Strong/Fitbod/Garmin; folded into Profile in Hevy (history is "your workouts" on your own profile) | Nesting under Profile is a legitimate, precedented pattern — not mandatory to be its own tab |
| Progress/PRs | Present in all three logging apps' benchmark (per prior doc) but never as its OWN top-level tab in the products checked this pass — usually nested in profile/exercise detail | Lean toward nested, not a 5th/6th tab, pending IA decision below |
| Profile | Universal, always first-class where any identity/history exists at all | First-class |
| Social feed | First-class only in Hevy/Strava; nonexistent in Strong/Fitbod; newly-added in Boostcamp | Optional/deferrable — see MVP section |
| Followers/friends | One-directional "follow" model in both Hevy and Strava (not mutual "friends") | Adopt one-directional follow, not a separate friends concept — see section 8 |
| Workout/program sharing | First-class in Hevy (workout+routine), Boostcamp (programs), TrainHeroic (programs, monetized) | Real, validated pattern — but a **post-MVP** capability, not a launch requirement |
| Athlete/creator profiles | Central to Boostcamp and TrainHeroic; absent from Hevy/Strong/Fitbod | A distinct, later capability layered on top of ordinary profiles, not needed for MVP |
| Discovery | A dedicated feature in Hevy ("Discovery Feed"); a marketplace in Boostcamp/TrainHeroic | Deferrable; meaningless until enough users/creators exist |
| Watch interaction | Every logging app treats this as a *capability*, not a nav destination — the watch has its own UI entirely separate from phone nav | Confirms: Watch is a separate product surface, not a phone screen |
| Privacy/visibility | First-class settings surface in Strava (three explicit tiers) and Hevy (public/private + follow approval); absent (no concept needed) in Strong/Fitbod/Garmin | Only relevant once social exists — see section 9 |
| Settings | Universal, always present, always low-visibility (buried behind profile/gear icon) | Standard placement; nothing new to learn here |

---

## 5. Navigation comparison

Three shapes actually observed, abstracted from the raw findings above:

**Shape 1 — Logging-first, no social** (Strong, Fitbod): 2–3 tabs. Workout/Home, History/Log,
sometimes Routines. No profile-as-identity concept beyond account settings.

**Shape 2 — Logging + social, feed-as-home** (Hevy, and Strava for its domain): 4 tabs. Home *is*
the social feed; Workout is a separate first-class tab; Discover/Groups is a distinct third surface;
Profile/You is the fourth, holding identity + history + settings.

**Shape 3 — Program/community-first** (Boostcamp, TrainHeroic): navigation organized around
programs/plans as the primary object, with logging as a consequence of "doing today's session from
the plan," and community/marketplace layered in afterward, not foundational.

TBDFit's own product intent (a serious logging app, not a coaching marketplace, with a possible
future social layer "inspired partly by Hevy" per the brief) maps most closely to **Shape 2**, but
notably every Shape-2 example still treats social as *content inside Home*, never a separate tab —
see the recommended IA below.

---

## 6. Social / profile findings

### The core distinction the brief asks about — validated against real competitor behavior

| Concept | Hevy's realization | TBDFit implication |
|---|---|---|
| Private historical Workout | A logged session, visible only to its owner unless explicitly shared | `Workout` (already implemented) — never has a "visibility" concept baked into its own row |
| PublishedWorkout | The *same* workout, exposed to followers via the feed/profile, but still fundamentally the owner's row (not copied at publish time) | A separate concept — likely a visibility flag or reference on `Workout`, not a new copy, until someone actually copies it |
| A copy/derived object | "Save as Routine" / "Copy Workout" produces a **new, independently-owned row** for the copier | Confirms: never share mutable ownership; a copy is a new domain object owned by the copier, per the brief's own instruction |

**FACT**: Hevy implements exactly the three-way distinction the brief specifies, and does so with
two different verbs ("Save as Routine" vs. "Copy Workout") depending on whether the result is a
*template* or an *immediately-startable session* — both still independently owned by the copier
either way.

### Follow model

**FACT**: Both Hevy and Strava use a **one-directional follow** model (like Instagram/Twitter), not
mutual "friends." Hevy additionally supports a private-profile approval step (follow requests must
be accepted before that user's workouts become visible to the requester).
[Hevy — Social Guide](https://help.hevyapp.com/hc/en-us/articles/35688036014231-Hevy-App-Social-Guide-Connect-Follow-and-Share-Your-Workouts).

Boostcamp's newly-added Community feature is described (in its own marketing) as letting users
"follow friends" — language that blends the "friend" concept with a follow mechanism, but no
evidence was found that Boostcamp implements a *mutual-acceptance* friends graph distinct from
following. [Fortune — Boostcamp Review 2026](https://fortune.com/article/boostcamp-review/).

**RECOMMENDATION**: adopt a single one-directional `Follow` relationship (optionally gated by an
approval step for private accounts, matching Hevy), and do not introduce a separate mutual
"Friends" concept. No competitor evidence in this research supports needing both models
simultaneously — the brief's own "friends if needed" framing appears correctly hedged; the evidence
says it isn't needed.

### Athlete/creator profiles

**FACT**: Boostcamp and TrainHeroic both distinguish ordinary user profiles from **creator/coach
profiles** that publish structured programs, in Boostcamp's case explicitly named and attributed
(dozens of named coaches with their own program libraries), in TrainHeroic's case tied to a
monetized marketplace. Neither Hevy, Strong, nor Fitbod has this distinction — an ordinary Hevy user
and Hevy's most-followed user have the same profile *type*, just different follower counts.

**INFERENCE**: "athlete/creator profile" is not a variant of the ordinary user profile that every
app needs — it is a **distinct, additive capability** (verified/creator status, a program-authoring
and -publishing surface, possibly monetization) that only becomes relevant once ordinary
social/sharing already works and there's a real population of contributors to distinguish. Building
it before ordinary follow/publish exists would be building a capability with nothing to attach it
to.

---

## 7. Wearable / Phone↔Watch findings

**FACT**: none of the researched products treat the watch as a "remote display" of the phone in the
way the brief warns against.

- **Strong**: the watch app can start, execute, and complete an entire workout independently, phone
  left elsewhere (confirmed again in this pass via [Strong Help Center — Perform a Workout on Apple
  Watch](https://help.strongapp.io/article/224-workout-on-apple-watch), consistent with the prior
  benchmark).
- **Apple Watch / Workout app**: built-in GPS and independent sensor recording mean a workout can be
  started, executed, and completed with zero iPhone involvement; data syncs to the iPhone's Fitness
  app only on reconnect. The iPhone side (Fitness app) has **no workout-starting UI at all** for
  Watch-originated workouts — its role is purely history/summary (`Sessions` list).
  [Apple Support — Use Apple Watch without its paired iPhone](https://support.apple.com/en-us/108300),
  [Apple Support — Get started with Fitness on iPhone](https://support.apple.com/guide/iphone/get-started-with-fitness-ipha5dddb411/ios).
- **Garmin Connect**: the most extreme example of phone-as-non-execution-surface — activities are
  always recorded on the device (watch or bike computer), and the Connect phone app exists
  specifically for *history, training status, and navigation planning*, never live logging.

**INFERENCE**: the strongest, most consistent competitor pattern in this entire research pass is
that **phone-side apps do not attempt to replicate the active-execution UI on the watch, or vice
versa** — the two surfaces have almost entirely non-overlapping screen sets. The phone owns
browsing/planning/history/social; the watch owns only the moment of execution. This directly
corroborates PD-001/ADR-0001's existing direction and gives a much more concrete phone/watch
screen-split answer than the prior research provided (see the IA document's Phone/Watch split).

---

## 8. Privacy findings

**FACT**: Strava documents three explicit activity-visibility tiers — **Everyone**, **Followers**,
**Only You** — plus a separate, additive **map-visibility privacy zone** control for start/end
locations specifically. [Strava — Activity Privacy Controls](https://support.strava.com/hc/en-us/articles/216919377-Activity-Privacy-Controls),
[Strava — Profile Page Privacy Controls](https://support.strava.com/en-us/articles/15401967-profile-page-privacy-controls).

**FACT**: Hevy uses a two-state profile visibility (public/private) with a follow-request-approval
gate for private profiles, rather than Strava's three explicit named tiers.

**RECOMMENDATION**: a three-tier `PRIVATE / FOLLOWERS / PUBLIC` visibility model (Strava's shape,
minus its geo-specific privacy-zone concept, which has no TBDFit analogue) is sufficient — it
subsumes Hevy's simpler two-state model (Hevy's "private" ≈ `FOLLOWERS`-after-approval, its
"public" ≈ `PUBLIC`) without needing a fourth tier. No competitor evidence in this pass supports a
separate `FRIENDS` visibility tier distinct from `FOLLOWERS`.

**Reiterating the brief's own required separation, now grounded in evidence**: `LocalAccount`
(already implemented — a local durable identity root) answers "whose local device data is this,"
and is completely orthogonal to any future `visibility` field on a published object, which answers
"who besides the owner can see this." No competitor's data model conflates who-owns-a-row with
who-can-see-a-row either — Strava's and Hevy's privacy settings are properties of the *content*
(an activity, a profile), never a substitute for authentication/ownership.

---

## 9. UX principles (derived, not accepted wholesale from the brief's examples)

Evaluated against the evidence gathered, not merely copied from the brief:

1. **Workout logging must remain extremely fast — validated.** Every logging-first competitor
   (Hevy, Strong, Fitbod) treats the active-workout screen as the product's core, per the prior
   benchmark's own findings; this pass adds no new evidence but confirms nothing contradicts it.
2. **The watch and phone have almost entirely disjoint *screen designs* — validated, strengthened.**
   Section 7 above found zero competitor evidence of a phone screen's *UI* being replicated on a
   watch or vice versa. Elevate this from "principle" to "structural constraint": TBDFit's
   Phone/Watch IA split (below) should be designed as two different screen inventories, not one
   inventory with a watch-sized variant of each screen.
   **Adversarial-review caveat**: this is a claim about UI/screen design, not about *capability*.
   None of the research sources establish that a *capability* such as "start a workout" or "mark a
   workout complete" is exclusive to one device — Strong and Apple Watch both let a workout be
   **started** on the watch independently, which is itself evidence that the same capability
   (starting a workout) exists on both phone and watch in those products, just via different,
   purpose-built UI on each. See the IA document's revised Phone/Watch section for why "zero screen
   overlap" must not be read as "zero capability overlap," and why TBDFit's own phone-side code
   already contradicts the stronger reading (the phone can already start a workout).
3. **Social, when present, augments Home rather than competing with it — validated.** No competitor
   promotes "Social" to a tab with equal navigational weight to the core logging function. Demoted
   from the brief's framing of "must not obstruct training" (defensive) to a stronger, evidence-based
   claim: **social does not need its own tab at all** in any product examined, including the most
   social-native one (Hevy).
   **Adversarial-review caveat**: this is an absence-of-precedent finding, not evidence that a
   dedicated social surface would be *wrong* for TBDFit. "No competitor gives social a top-level tab
   today" only supports a placement/sequencing recommendation (don't build one now; fold social into
   Home/Profile while it's small) — it does not support a permanent claim that TBDFit must never have
   one. If TBDFit's social graph eventually reaches Instagram/Strava-Groups scale, a dedicated
   discovery surface remains a legitimate future option, to be justified by usage evidence at that
   time, not foreclosed by this document.
4. **A copy is a new object, never shared mutable ownership — validated, elevated to a hard rule.**
   Hevy's dual "Save as Routine"/"Copy Workout" verbs are direct, concrete precedent, not just
   TBDFit's own preference.
5. **Programs/routines and social are genuinely separable capabilities, addable independently and
   later — validated by product segmentation, not just architecture reasoning.** Strong/Fitbod ship
   with zero social. Boostcamp shipped programs *years* before adding any social feed. Neither
   capability requires the other to exist first.
6. **Advanced/creator functionality is additive, not a variant of the base experience — validated.**
   Athlete/creator profiles (Boostcamp, TrainHeroic) are layered onto, not substituted for, an
   ordinary profile — see section 6.
7. **Historical data (Progress/PRs) does not need its own top-level tab to be taken seriously —
   observed, not merely asserted.** It is nested under Profile/exercise-detail in most examined
   products; only Garmin (a fundamentally different, device-centric product) gives training status
   its own primary destination.

The brief's other candidate principles ("active workout requires very few taps," "durable local
workout state always has priority during execution," "historical data should be easy to inspect,"
"advanced data should progressively disclose") are reasonable but were not independently
re-validated by this specific research pass — they restate the prior competitor benchmark's already
-evidenced conclusions (see that document's `RECOMMENDED FIRST-SLICE SCOPE`) rather than new findings
from this pass, so they are carried forward as still-valid but not re-derived here.

---

## 10. Screen inventory (labeled MVP / POST-MVP / FUTURE)

See [`product-information-architecture.md`](product-information-architecture.md) for the full
labeled inventory with rationale — summarized here for completeness of this research document:

- **MVP**: Home/Root, Active Workout, Exercise Picker, Workout Summary/Complete, History (list +
  detail), minimal Profile, Settings.
- **POST-MVP**: Routine Library, Routine Detail/Builder, Progress/Exercise Progress, richer Profile
  (public visibility, follow), basic Feed.
- **FUTURE**: Discover, Athlete/Creator Profile, Program Detail/Marketplace, Coach↔Athlete
  messaging-equivalent.

---

## 11. MVP recommendation (summary)

See the IA document for the full three-bucket breakdown with reasoning. In short: the current
technical foundation (durable local Workout/Exercise/WorkoutExercise/WorkoutSet, ownership,
offline access) supports finishing the **core logging loop** (start → log → complete → view
history) as the credible first product, matching every logging-first competitor's own MVP shape
(Strong and Fitbod both shipped, and remain viable, with zero social). Routines are the most
evidence-backed "important next" addition (present in 3 of the other 4 non-Fitbod-style products).
Social/athlete/creator capabilities are differentiators, not launch requirements, per the segmented
rollout pattern observed at Boostcamp specifically.

---

## 12. Domain/backend implications discovered (discovery only — not implemented)

| Frontend capability | Likely future domain implications |
|---|---|
| Home feed of followed users' workouts | `UserProfile`, `Follow` (one-directional, optional approval state), a feed query/index over followed accounts' published workouts |
| Publishing a workout | A `visibility` concept on `Workout` (or a separate `PublishedWorkout` projection) — `PRIVATE / FOLLOWERS / PUBLIC` |
| Copying a friend's workout/routine | A `Routine`/template entity distinct from `Workout`; provenance metadata (`sourceWorkoutId`/`sourceRoutineId`, informational only, never a live dependency — consistent with the existing strength-slice design doc's Plan-vs-Execution section) |
| Athlete/creator profiles | A `creator`/`verified` flag or distinct profile subtype; a program-publishing surface; possibly a marketplace/monetization concern (explicitly future, not scoped) |
| Discovery | A search/index surface over public profiles and published content — meaningless until real content volume exists |
| Privacy settings | A visibility enum + (if `FOLLOWERS` is chosen) a `Follow.status` (pending/accepted) for private-account gating |
| Watch execution (already directionally decided via PD-001) | Confirmed, not changed, by this research: watch and phone need separate screen inventories, reinforcing that no shared "responsive" UI layer should be attempted across them |

None of the above is implemented, migrated, or scheduled by this document.

---

## Sources

- [Hevy Help Centre — Social Guide](https://help.hevyapp.com/hc/en-us/articles/35688036014231-Hevy-App-Social-Guide-Connect-Follow-and-Share-Your-Workouts)
- [Hevy — Content Feed](https://www.hevyapp.com/features/content-feed/)
- [Hevy — User Profiles](https://www.hevyapp.com/features/user-profiles/)
- [Hevy — Discovery Feed](https://www.hevyapp.com/features/discovery-feed/)
- [Hevy Help Centre — Workouts vs Routines](https://help.hevyapp.com/hc/en-us/articles/33703513582871-Workouts-vs-Routines-in-Hevy-What-They-Mean-and-How-to-Use-Them)
- [Hevy Help Centre — How to Access and Use Hevy's Routine and Program Library](https://help.hevyapp.com/hc/en-us/articles/36011518408983-How-to-Access-and-Use-Hevy-s-Routine-and-Program-Library)
- [Hevy Help Centre — How to Share Workouts and Routines Step-by-Step](https://help.hevyapp.com/hc/en-us/articles/34953501503895-How-to-Share-Workouts-and-Routines-Step-by-Step)
- [Hevy — Share Folders & Routines](https://www.hevyapp.com/features/share-folders-routines/)
- [Strong Help Center — About Exercise Detail Screen](https://help.strongapp.io/article/237-about-exercise-detail)
- [Strong Help Center — Perform a Workout with Strong for Apple Watch](https://help.strongapp.io/article/224-workout-on-apple-watch)
- [Fitbod Blog — A Better Workout Tab](https://fitbod.me/blog/a-better-workout-tab/)
- [Fitbod — FAQs](https://fitbod.me/faqs/)
- [Strava Help Center — Clubs on the Mobile App](https://support.strava.com/en-us/articles/15401837-clubs-on-the-mobile-app)
- [Strava Help Center — Activity Privacy Controls](https://support.strava.com/hc/en-us/articles/216919377-Activity-Privacy-Controls)
- [Strava Help Center — Profile Page Privacy Controls](https://support.strava.com/en-us/articles/15401967-profile-page-privacy-controls)
- [Boostcamp — Programs](https://www.boostcamp.app/programs)
- [Fortune — Boostcamp App Review (2026)](https://fortune.com/article/boostcamp-review/)
- [TrainHeroic — Athlete FAQ](https://www.trainheroic.com/athlete-faq/)
- [TrainHeroic — Coach FAQ](https://www.trainheroic.com/coach-faq/)
- [Apple Support — Get started with Fitness on iPhone](https://support.apple.com/guide/iphone/get-started-with-fitness-ipha5dddb411/ios)
- [Apple Support — Use Apple Watch without its paired iPhone](https://support.apple.com/en-us/108300)
- [Apple Support — End and view a summary of your workout on Apple Watch](https://support.apple.com/guide/watch/end-and-view-a-summary-of-your-workout-apd95450de2a/watchos)
- [Garmin Support — What Is the Training Status Feature](https://support.garmin.com/en-US/?faq=VxKazDQ2mkAmDoQbJriEBA)
- [Garmin — How to use Garmin Connect to track your health and wellness](https://www.garmin.com/en-US/blog/fitness/unlocking-the-potential-of-garmin-connect/)

---

## Status

**STATUS: RESEARCH — NOT A PRODUCT DECISION.** This document does not promote, accept, or modify
any entry in `docs/product/decisions.md`, any ADR, or the strength-workout-first-slice design. It
exists solely to inform [`product-information-architecture.md`](product-information-architecture.md)
and future team review.
