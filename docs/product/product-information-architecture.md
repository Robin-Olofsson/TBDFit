# TBDFit Product Information Architecture

**STATUS: DESIGN PROPOSAL — FOR TEAM REVIEW**

This is a design proposal, not an ADR and not an accepted product decision. It synthesizes
[`frontend-product-ux-research.md`](frontend-product-ux-research.md) with TBDFit's existing
architecture (local-first workout execution, `LocalAccount`-scoped ownership, PD-001/PD-002/PD-003,
ADR-0001's independent-Watch direction) into a proposed navigation structure, core journeys, and
screen scope. It does not promote, accept, or modify any PD or ADR. No code, schema, or UI has been
touched to produce this document.

Labels: **FACT** (verified from research/code), **INFERENCE** (reasoned conclusion),
**RECOMMENDATION** (design judgment, not a decision), **OPEN DECISION** (requires developer
approval).

---

## 1. Candidate information architectures

### Option A — Logging-first, no social tab (Strong/Fitbod shape)

```
Home │ Workout │ Routines │ Progress │ Profile
```

Home = "what should I do today" (active workout state or a start prompt). No feed anywhere. Social,
if it ever exists, lives entirely inside Profile (your own published items, your followers list) —
there is no cross-user discovery surface at all in this option.

- **Strengths**: simplest possible structure; zero navigational cost paid for a feature (social)
  that doesn't exist yet; matches TBDFit's current technical scope almost exactly.
- **Weaknesses**: if social becomes a real differentiator later (per the brief's own Hevy-inspired
  ambition), this shape has no natural home for a feed at all — it would require inserting a new
  tab later, which is a bigger navigational change than growing Home's content.
- **Scalability**: excellent for a pure logger; poor if social becomes a first-class future
  investment.

### Option B — Feed-as-Home, social folded in (Hevy/Strava shape)

```
Home │ Workout │ Progress │ You
```

Home = a feed (which, until any followed user exists, degenerates gracefully into an empty/prompt
state — never a separate "no social yet" mode to build). Workout = start/active/log. Progress =
PRs, volume, trends. **You** = profile, history, followers/following, settings — consolidating
identity + history + configuration into one place, mirroring Strava's own "You" tab exactly.

- **Strengths**: the `Home │ Workout │ You` core is directly validated by both Hevy and Strava
  (research section 3) — Home-as-feed-or-landing, Workout as its own tab, and a consolidated
  identity+settings tab are all real, precedented shapes; Home already has the right *shape* for a
  future feed without a structural change, it just has no content yet.
- **Weaknesses**: "Home is a feed" is a slightly awkward promise to make before any social feature
  exists — the empty state needs real design thought (a start-workout prompt, not a blank feed).
  Slightly higher naming/positioning risk if TBDFit's early identity is "workout logger," not
  "social app," and Home reads as the latter from day one.
- **Scalability**: excellent — this is the structure that survives all the way to a Hevy-scale
  social product without a rename.
- **Adversarial-review correction**: the original version of this option included a 4th tab labeled
  **Progress**, described as "directly validated by both Hevy and Strava." That claim does not
  survive a check against this same research document's own section 3 findings: Hevy's actual
  4-tab bar is **Home / Workout / Discover / Profile** — its 3rd slot is Discover, not Progress.
  Strava's actual bar is only 3 tabs — **Home / Groups / You** — with no Progress tab at all, and no
  dedicated Workout tab either (Strava is an activity tracker, not a manual logger). Neither cited
  precedent actually contains a Progress tab. Combined with research section 4/9's own finding that
  "Progress/PRs [is] never as its OWN top-level tab in the products checked this pass," keeping
  Progress as a 4th top-level destination was an internal inconsistency, not a validated choice —
  see the revised recommendation and nav map below.

### Option C — Explicit Social tab (Strava-clubs shape, adapted)

```
Home │ Workout │ Social │ Progress │ Profile
```

Social/Discovery gets its own first-class tab from day one, separate from Home (which stays a
plain "start/active workout" landing screen, closer to Option A's Home).

- **Strengths**: gives social room to grow into its own dedicated surface (feed + discovery +
  clubs-equivalent) without competing for space with the start-workout prompt.
- **Weaknesses**: **unsupported by any competitor examined** — no product in this research,
  including the two most social-native ones (Hevy, Strava), gives "Social" a bare top-level tab
  distinct from Home (research section 3, section 9 principle 3). This is an absence-of-precedent
  finding, not counter-evidence (see the research document's own adversarial-review caveat on
  principle 3): it means "don't build this now," not "never build this." Also the one option that
  spends a scarce nav slot on a capability TBDFit does not have yet at all.
- **Scalability**: adequate, but current evidence argues against choosing this shape *now*.

### Recommended direction (revised after adversarial review)

**RECOMMENDATION: a 3-tab `Home │ Workout │ You` shape**, not the originally-proposed 4-tab version.

The original recommendation kept Option B's `Home │ Workout │ Progress │ You` shape and cited Hevy
and Strava as direct validation for all four slots. On review, that citation does not hold for the
4th slot: neither Hevy (Home/Workout/**Discover**/Profile) nor Strava (Home/**Groups**/You) actually
puts Progress in a tab bar, and research section 4/9 independently found Progress is never top-level
anywhere examined. Keeping Progress as a permanent nav destination while also listing it as
**POST-MVP** with no screen behind it at launch (section 5) would ship a dead tab — exactly the
"empty destination for future symmetry" the review brief warns against.

**Revised structure**: `Home │ Workout │ You` at launch. Progress/PRs content is nested (under
`You`, alongside History, since both read from the same completed-`Workout` data) rather than
top-level, and is promoted to its own tab later **only if usage evidence justifies it** — adding a
4th tab later is a small, additive nav change, not a disruptive rename, so nothing is lost by not
pre-declaring the slot now.

**Home's MVP content is also revised**, not left as a placeholder duplicate of Workout's own root
screen. The original recommendation's Home content ("active workout or a start prompt") is
*identical* to what `WorkoutRootScreen` already shows when Workout is opened — as written, Home and
Workout would be the same screen wearing two tab labels at launch, with zero unique content on
either side until a feed exists. The revised recommendation gives Home a distinct, if thin, MVP
identity — a short dashboard (last workout snippet, a resume-workout affordance, in the prototype
represented with hardcoded/placeholder content, see the prototype-scope section) — so the tab earns
its slot immediately rather than only once social exists. If, once built, Home and Workout genuinely
cannot be differentiated with real content, that is itself a finding worth surfacing rather than
papering over with a placeholder.

This still follows the brief's own hinted direction ("sociala saker ligger under: Home feed +
Profile... appen fortfarande känns primärt som en workout app") and remains the option with the
most direct competitor precedent — it is now accurate precedent, not overstated precedent.

This is a recommendation, not a decision — **OPEN DECISION #1** below.

---

## 2. Recommended navigation map

```
┌─────────────────────────────────────────────────────┐
│                    TBDFit (Phone)                     │
├─────────────────────────────────────────────────────┤
│  Home                Workout            You           │
│  ├ dashboard/         ├ Active          ├ Profile      │
│  │  resume            │  Workout        │  (own)       │
│  │  (prototype-only   ├ Exercise        ├ History       │
│  │  content until a   │  Picker         │  ├ Workout    │
│  │  real "today"      ├ Routine         │  │  Detail    │
│  │  read model        │  Library*       │  └ Progress /  │
│  │  exists)           └ Routine            PRs (nested, │
│  └ (post-MVP: feed)      Detail*           read-only,   │
│                                             promoted to  │
│                                             its own tab  │
│                                             later only   │
│                                             if usage      │
│                                             justifies it) │
│                                          ├ Followers/     │
│                                          │  Following     │
│                                          │  (post-MVP)    │
│                                          ├ Settings       │
│                                          └ (post-MVP:     │
│                                             public         │
│                                             visibility)    │
└─────────────────────────────────────────────────────┘
* Routine Library / Detail: real backend is POST-MVP, but both should exist as PROTOTYPE-ONLY
  (hardcoded content) screens in the first clickable prototype — see the prototype-scope section.

Not first-class navigation destinations (nested, per research section 4):
  Exercise Picker      → inside Workout flow
  Workout Detail        → inside History (under You)
  Progress / PRs         → nested under You/History, not top-level (see adversarial-review
                            correction above — the original 4-tab version placed this as a top-level
                            "Progress" destination; neither cited precedent (Hevy, Strava) actually
                            does this, and it had no MVP screen behind it)
  Discover              → future, not in MVP nav at all
  Athlete/Creator profile → future, reached via Discover/feed, not its own tab
```

---

## 3. Core user journeys

### Journey A — Start from scratch

```
Home (start prompt) → Start Workout → Add Exercise (picker) → Log Sets → Complete → Summary
```

- **Screens**: Home, Active Workout, Exercise Picker, Workout Summary.
- **Durable data**: `Workout`, `WorkoutExercise`, `WorkoutSet` — all already implemented.
- **Ownership/privacy**: `Workout.ownerId → LocalAccount` (already implemented); no visibility
  concept needed — private by construction, no publish step in this journey.
- **Backend implications**: none beyond what exists — this journey is fully served by current local
  persistence. A future "Complete Workout" summary screen needs no new domain model, only a read
  view over existing tables.
- **Adversarial-review gap**: the journey as originally written ends at "Summary" with no specified
  next step. What does the user do after seeing the summary — is there a "Done" action, and does it
  return to Home, to History, or somewhere else? This is unspecified and should be resolved when
  Summary is actually designed: the natural default is "Done → Home," with History reachable
  separately via `You`, not as a forced next step off of Summary.

### Journey B — Routine

```
Home/Workout → choose Routine → Start → execute (same Active Workout screen as Journey A) → Complete
```

- **Screens**: Routine Library, Routine Detail, then the *same* Active Workout screen as Journey A.
- **Durable data**: a new `Routine`/`RoutineExercise` entity (does not exist yet); `Workout` gains an
  optional, informational `sourceRoutineId` (per the existing strength-slice design doc's Plan vs.
  Execution section — never a live dependency).
- **Ownership/privacy**: routines are `LocalAccount`-owned the same way workouts are, at minimum;
  whether a routine can ever be *published* is a POST-MVP question (section 6 below).
- **Backend implications**: a genuinely new domain object and its own reuse/edit semantics
  (Strong's own acknowledged plan-vs-execution confusion — see the competitor benchmark — is exactly
  the pitfall to design around explicitly when this is built, not now).

### Journey C — History

```
You → History → previous Workout → Workout Detail
```

- **Screens**: History (list, nested under You per Option B), Workout Detail.
- **Durable data**: `Workout` rows where `status = COMPLETED` — already representable, no new
  schema.
- **Ownership/privacy**: purely `ownerId`-scoped read, already the existing query pattern.
- **Backend implications**: none for MVP; a completed-workout detail/edit screen is the main gap
  today (the strength-slice design doc already scoped "editable history," not yet built).

### Journey D — Friend workout

```
Home (feed, post-MVP)          ┐
                                ├→ friend's published workout → inspect → Copy/Save → own routine or workout
Friend's Profile (their list)  ┘
```

- **Adversarial-review correction**: the journey as originally written names only the feed as an
  entry point. Visiting a specific followed account's profile and browsing *their* published-workout
  list directly is an equally normal, precedented entry point (this is how Strava/Instagram-style
  profile-first browsing works, not just feed-scrolling) and is not a variant of Journey D so much as
  a second, equally valid first step feeding into the same downstream screens. Both entry points are
  shown above; only the destination screens (Published Workout Detail onward) were actually
  underspecified before.
- **Screens**: Home/Feed *or* Friend Profile → Published Workout Detail, then either Active Workout
  (if "Copy Workout") or Routine Detail (if "Save as Routine") — mirroring Hevy's own two-verb split
  (research section 6).
- **Durable data**: a `PublishedWorkout`/visibility concept, `Follow`, and — critically — a **new,
  independently-owned copy** of the workout/routine for the recipient, never a shared reference.
- **Ownership/privacy**: this is where `LocalAccount` (who executes/owns the *copy*) and social
  visibility (who could *see* the original) are both in play simultaneously and must stay
  conceptually distinct, per the brief's own instruction.
- **Backend implications**: `UserProfile`, `Follow`, `visibility` enum, a copy/derive operation —
  none exist yet; this is squarely a FUTURE-bucket capability (section 7).

### Journey E — Athlete/creator

```
Discover → athlete/creator profile → workouts/programs → Follow → Save/Copy program
```

- **Screens**: Discover, Athlete/Creator Profile, Program Detail, then the same copy flow as
  Journey D.
- **Durable data**: everything Journey D needs, plus a creator/verified distinction on profiles and
  a `Program` concept (a published, possibly multi-week `Routine` collection) — the largest net-new
  domain surface of any journey here.
- **Ownership/privacy**: same separation as Journey D; additionally, a creator's program is likely
  `PUBLIC` by definition (a marketplace/discovery entry only works if visible), which is a distinct
  visibility posture from an ordinary user's default-`PRIVATE` workout.
- **Backend implications**: the single largest and most speculative bucket — explicitly a FUTURE
  DIFFERENTIATOR (section 7), not scoped further here.

### Journey F — Wearable execution

```
Phone or Watch → workout becomes active → Watch executes independently → result persists
→ later replication
```

- **Screens**: on Phone, none beyond Journey A's Active Workout (or nothing, if started on Watch);
  on Watch, its own independent execution UI — per research section 7, this is a **separate,
  non-overlapping screen set**, not a scaled-down phone screen.
- **Durable data**: `Workout`/`WorkoutExercise`/`WorkoutSet`, replicated per PD-001's already-accepted
  consequences (idempotent, no duplicate creation on retry, no stale-device overwrite) — the
  replication *mechanism* remains explicitly undecided (PD-001 itself says so).
- **Ownership/privacy**: `LocalAccount` ownership must resolve identically regardless of which
  device the workout originated on — not a new concern, but worth stating: Watch-side `ownerId`
  resolution needs the same non-negotiable account-scoping this phone-side work already established.
- **Backend implications**: none beyond what PD-001/PD-002 already name; this journey is a reminder
  that the eventual Watch implementation inherits the same ownership discipline just built on Phone,
  not a new discovery.

---

## 4. Phone / Watch responsibility split

**Adversarial-review correction**: the original version of this section claimed "no competitor's
phone and watch screens overlap" as an absolute, and built a split table with zero shared rows. That
overstates what the research actually supports. The evidence (research section 7) is strong for
**screen/UI design** — no competitor reuses one device's UI on the other — but does not support
**zero capability overlap**. Strong and Apple Watch both let a workout be *started* from the watch
independently, and TBDFit's own phone-side code today (`WorkoutHomeScreen`) already starts a workout
from the phone. Starting a workout is therefore already a capability that exists on **both**
devices, in TBDFit specifically — not a hypothetical. Journey F below also already assumes this
("Phone **or** Watch → workout becomes active"). The correct framing is PRIMARY RESPONSIBILITY
(which device's UI is optimized for a job, and which is authoritative when both could act) separated
from CAPABILITY (which operations may legitimately exist on both devices, each with its own
purpose-built UI):

```
PHONE                                   WATCH

Home (start/resume/feed)                Start/resume workout
Browse & edit Routines                  Current exercise
History & Workout Detail                Current set — reps/load entry
Progress / PRs                          Mark set complete
Social feed / Discover (future)         Rest timer (later)
Athlete/Creator profiles (future)       Quick controls only
Settings / account / privacy
Deep configuration

SHARED CAPABILITY (exists on both, independently, via each device's own purpose-built UI —
not a shared/responsive layer):
  Start workout           — Phone: already implemented (WorkoutHomeScreen). Watch: execution-
                             primary responsibility once built.
  Resume an active workout — both devices must be able to load the same already-active workout's
                             current state (see handoff flow 1 below).
  Complete a workout       — either device may be the one physically present when a session ends.
  Inspect current active-workout state — read-only "what's going on" view, useful on either device.
```

Neither device has *permanent authority* over these shared capabilities — whichever device the user
is actually holding at a given moment should be able to act, subject to the same per-owner
consistency guarantees already implemented (`WorkoutDao.startWorkoutIfNoneActive`'s per-owner
single-active-workout invariant applies regardless of which device initiates the call). This
preserves ADR-0001's independent-Watch direction: Watch is not a remote display *of* Phone, and
Phone is not a remote display *of* Watch either — each is a full, independent client for the shared
capabilities, with execution-specific UI (current set, rest timer, quick controls) remaining Watch's
distinguishing strength and browsing/history/social remaining Phone's.

**Flows needing explicit handoff semantics** (per the brief's own instruction not to treat Watch as
a remote display):

1. **Workout started on Phone, user picks up Watch mid-session** — the Watch must be able to load
   the *already-active* workout's current state (attached exercises, logged sets so far), not start
   a fresh one. This is a read/resume operation on the Watch side, symmetrical with Phone's own
   `SessionUnavailable`-tolerant local-workout resume already implemented.
2. **Workout started on Watch, phone opened later** — Phone must recognize an already-active workout
   exists (once replicated) rather than offering to start a second one — the existing per-owner
   single-active-workout invariant (already implemented, `WorkoutDao.startWorkoutIfNoneActive`)
   already anticipates exactly this, though only proven so far for the phone-only case.
3. **Workout completed on Watch while Phone is offline/absent** — Phone's History must eventually
   show it once replication occurs, with no duplicate row and no user-visible "sync" step required
   before it's usable.

None of these are designed here — they're named because Journey F above and this split make them
concretely visible, which is exactly the discovery this research exists to produce.

---

## 5. Proposed MVP screen set

Labeled per the brief's own vocabulary. "MVP" = required for a credible first release of the core
workout-logging product; "POST-MVP" = important, evidence-backed, but not launch-blocking; "FUTURE"
= differentiator, contingent on MVP traction.

| Screen | Label | Why |
|---|---|---|
| Home (start/resume) | **MVP** | Every competitor's landing surface; already effectively implemented (`WorkoutRootScreen`) |
| Active Workout | **MVP** | The core loop; already implemented through set logging |
| Exercise Picker | **MVP** | Required to attach exercises; already implemented |
| Workout Summary/Complete | **MVP** | Missing today — the next concrete gap (see section 8) |
| History (list) | **MVP** | Table-stakes in every logging competitor |
| Workout Detail (read) | **MVP** | Required for History to be useful at all |
| Profile (own, minimal) | **MVP** | Needed for account/settings context even with zero social |
| Settings | **MVP** | Universal, low-cost, already partially needed (units, sign-out) |
| Routine Library | **POST-MVP** (backend); **prototype-only** in the first clickable prototype | Present in 3 of 4 non-generation-based competitors; real value, not required to prove the core loop — but see adversarial-review note below on why it should still be *shown* (with hardcoded content) before it's *built* |
| Routine Detail/Builder | **POST-MVP** (backend); **prototype-only** in the first clickable prototype | Depends on Routine Library |
| Progress / Exercise Progress | **POST-MVP** | Universally present but never blocking-critical; derivable read-time from existing history once it exists |
| Workout Detail (edit) | **POST-MVP** | The design doc's own "editable history" direction, not yet built |
| Feed (Home content) | **POST-MVP** | Structural slot reserved by the Option B navigation choice; no content until Follow exists |
| Public Profile (others') | **POST-MVP** | Prerequisite for any social feature at all |
| Follow / Followers list | **POST-MVP** | Prerequisite for Feed |
| Discover | **FUTURE** | Meaningless until real user/content volume exists (research section 6) |
| Athlete/Creator Profile | **FUTURE** | Distinct, additive capability (research section 6) |
| Program Detail / Marketplace | **FUTURE** | Largest net-new domain surface (Journey E) |
| Coach↔Athlete messaging-equivalent | **FUTURE** | No evidence TBDFit needs a TrainHeroic-style coaching product; included only for completeness against the research |

**Adversarial-review note on Routines vs. Profile priority**: the table above lists "Profile (own,
minimal)" as MVP and Routine Library as POST-MVP. On direct user-value comparison, this ordering is
questionable for a strength-training product specifically. A standalone Profile screen today would
show, at most, a username and a logout button — the actual username/logout affordance already exists
(`ProfileSummaryHeader`, already implemented) without needing its own nav destination. Routine reuse,
by contrast, is how every real lifter actually trains: nobody re-invents their exercise order from
scratch each session, and *every* manual-logging competitor examined (Hevy, Strong) ships routines as
core, not optional. Judged purely on user value — not implementation cost — Routine Library plausibly
matters more than a standalone Profile screen at this stage.

This does **not** mean committing real backend `Routine`/`RoutineExercise` work now — that is a
genuinely new domain surface and, per the user's own prototyping policy, is out of scope until this
review's screens are actually validated. It does mean the **first clickable prototype** should
*show* a Routine Library/Detail flow with representative hardcoded content (per the prototype-scope
section below) even though no backend exists yet, while a standalone Profile destination can be
deferred or folded into `You`/Settings without a hardcoded stand-in, since it currently has nothing
distinct to show beyond what `ProfileSummaryHeader` already provides.

---

## 6. Explicitly unresolved product decisions

1. **OPEN DECISION #1 — Navigation shape (Option A vs. B vs. C).** This document recommends a
   **revised 3-tab Option B** (`Home │ Workout │ You`, Progress nested rather than top-level — see
   the adversarial-review correction in section 1). Not accepted until both developers agree.
2. **OPEN DECISION #2 — Whether Routines ship before or alongside any social capability.** Research
   shows both orders exist among competitors (Boostcamp shipped programs years before social;
   Hevy/Strong shipped routines with no social ever, in Strong's case). TBDFit's own sequencing is
   not decided by this document.
3. **OPEN DECISION #3 — Visibility model scope.** This document recommends `PRIVATE / FOLLOWERS /
   PUBLIC` (section 8 of the research doc) as sufficient, explicitly rejecting a separate `FRIENDS`
   tier — requires sign-off before any schema work references it.
4. **OPEN DECISION #4 — Whether "You" (Option B) or a separate "Profile" + "Settings" split is
   preferred.** Consolidating into one tab is recommended for nav-slot economy, but some developers
   may prefer Settings to remain separate from social identity for clarity — a legitimate taste call
   this document does not resolve.
5. **OPEN DECISION #5 — Whether an athlete/creator ecosystem (Journey E) is ever a TBDFit goal at
   all**, versus staying a pure logging + lightweight-social product indefinitely (closer to
   Strong/Hevy than to Boostcamp/TrainHeroic). This materially changes how much of section 7's
   FUTURE bucket is ever built, and nothing in this document commits to it.
6. **Not resolved**: exact wording/labeling of the "You" tab, exact Routine data model, and the
   Watch handoff mechanism named in section 4 — all deferred to their own future design passes.
7. **OPEN DECISION #6 (new, from adversarial review) — Profile-level privacy vs. per-item
   visibility.** The recommended `PRIVATE/FOLLOWERS/PUBLIC` model (research section 8) answers "who
   can see this specific workout," but competitors examined (Strava, Hevy) separately also gate
   whether a stranger can find/see the *profile itself* at all (an account-level private/public
   switch). Whether TBDFit needs both a profile-level gate and a per-item visibility field, or
   whether one subsumes the other, was not investigated in this research pass and is not resolved
   here — flagged, not answered.
8. **OPEN DECISION #7 (new, from adversarial review) — Progress promotion threshold.** Progress/PRs
   is nested under `You` rather than top-level in the revised recommendation. What usage evidence
   (e.g. daily-active-use frequency, explicit user request volume) would justify promoting it to its
   own tab later is not defined here and should be decided if/when the question actually arises, not
   pre-committed to now.

---

## Status

**STATUS: DESIGN PROPOSAL — FOR TEAM REVIEW.** Nothing in this document is implemented. No PD/ADR
status is changed. This document does not authorize implementation of any screen, navigation
change, or domain model — it exists to be reviewed and either approved, amended, or rejected before
any frontend or backend work proceeds from it.
