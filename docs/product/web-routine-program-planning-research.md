# Web Routine + Program Planning Research

**Status: `RESEARCH / PRODUCT INVESTIGATION — NOT A PRODUCT DECISION`**

This document supersedes an earlier, cancelled research pass ("Web Program Builder Research") that
the developer stopped mid-run specifically because its framing risked treating Program as the
primary planning surface. This version corrects that: **Routine is the standalone core planning
object and must never become subordinate to or require Program.** A user must be able to use
TBDFit indefinitely without ever creating a Program. Every section below is written with that
constraint as a hard boundary, not a preference.

Labels used throughout: **VERIFIED** (confirmed via official product documentation/help centers),
**PARTIALLY VERIFIED** (found via search-surfaced summaries of official sources, not a direct
primary-source fetch — most official help-center pages returned HTTP 403 to direct fetch in this
environment, consistent with this session's established pattern), **NOT VERIFIED** (third-party or
unconfirmed), **RECOMMENDATION** (this document's judgment, not a decision).

Locked product decisions this research does not reopen (from prior work this session): `Routine`
standalone; `Program → ProgramWeek → ProgramSession`; `ProgramWeek` is a logical, not calendar,
week; Routine→ProgramSession is a copy/snapshot, never a live reference; planned ≠ actual, always;
completed `Workout` history is immutable; `Folder`/`Collection` is not V1; `ProgramInstance`/
Enrollment/`activeProgram` is deferred pending evidence; `ProgramSession.name` is optional with a
display fallback.

Grounded in the actual current implementation: Android has a real Room-based Routine domain
(snapshot-on-start, target-vs-actual separation). Web just got its own independent Supabase-backed
Routine implementation — real `/plan`, `/plan/new`, `/plan/:id/edit`, `/plan/:id` — see
`web/src/pages/{PlanPage,RoutineEditorPage,RoutineDetailPage}.tsx` and `web/src/data/routines.ts`.
The two do not sync. This research evaluates that real current Web IA/UX, not a hypothetical one.

---

### COMPETITOR ROUTINE MODELS

**Hevy** — **VERIFIED**, official help center: "A routine is a plan for a workout, while a workout
is when you are actively logging what you are doing at the gym." A routine is "a reusable workout
template you create once... and can reuse unlimited times." Starting a routine as a workout lets
you deviate from it during logging — the routine is the template, the workout is what actually
happened. This is **exactly** TBDFit's own `Routine`/`Workout` split, independently converged on,
not copied from Hevy — strong external validation of an already-locked decision.

**Hevy's "Program Library"** — **VERIFIED**: 26 pre-built "programs" organized into beginner/
intermediate/advanced tiers, browsed via an Explore screen with Level/Goal/Equipment filters. These
are **not** a week-structured builder — they are curated bundles of Routines a user saves, which
(per the earlier corrected competitor benchmark this session already produced) land as a flat
Folder on import, with no week counter or progression state surviving. Hevy's own product therefore
has no real multi-week Program concept at all — "Program" is marketing language for "a themed
Routine bundle," not a structural entity. This is the single most important negative finding: the
market leader in workout logging has not solved this problem; it has a well-known-weak workaround
for it (Folder).

**Boostcamp** — **PARTIALLY VERIFIED**: explicit multi-week program structure with a custom
program builder supporting **duplicate day** and **duplicate week** as named features, specifically
described as existing "so if your program calls for a certain amount of sets and reps for a certain
exercise multiple times, you can just duplicate it" — i.e., built specifically to solve TBDFit's
own Part 3/6 question (repetitive multi-week entry).

**TrainHeroic** — **VERIFIED** (official support articles): a genuinely rich session/week
manipulation model. Coaches drag-select sessions on a calendar (like highlighting text in a word
processor), which surfaces a persistent action bar with **copy, delete, save-as-program, repeat,
publish/unpublish**. Pasting is a right-click or keyboard-paste on a target date. Whole weeks can be
selected and copied the same way individual sessions can. Programs themselves are described as "a
fixed length of programming that athletes can begin at any time by **adding a copy to their
calendar**" — an explicit, official confirmation of copy-semantics at the *Program* level too, not
just the session level (see ROUTINE → PROGRAM RELATIONSHIP below).

**Trainerize** — **VERIFIED** (official blog): duplicating an exercise within a workout preserves
"all the sets, targets, and rest info." Coach-to-client program copying exists via a "copy to"
action that creates an independent, editable instance per client. Trainerize is B2C-coach-oriented
(programs are assigned to clients, not self-authored by the end user), so its multi-client-copy
patterns are less directly relevant to TBDFit's single-user self-service model, but its
duplicate/copy semantics corroborate the same pattern everyone else uses.

**Strong** — carried forward from this session's earlier, already-corrected primary benchmark:
Strong has only a Routine/Workout split (with company-acknowledged user confusion about the
distinction) — no multi-week Program builder was found in that pass, and a third-party claim of
"full periodization" could not be verified against any official source. Not a Program Builder
precedent.

**Fitbod** — **PARTIALLY VERIFIED**: periodization is handled *algorithmically* and *dynamically* —
official copy: "your next workout isn't just 'Week 5, Day 2 of the plan,' it's a tailored session
based on what your body needs today." There is no user-editable multi-week template to inspect —
Fitbod is architecturally the opposite of what TBDFit is building (generated-per-session vs.
authored-in-advance). Useful as a contrast, not a UX precedent for a Program *Builder* specifically.

**StrongLifts 5×5** — **VERIFIED** (official site): the program is exactly two workouts, A and B,
alternated automatically — "the app doesn't use a weekly structure... you alternate workouts A and
B each time you train," and the app itself "remembers which workout you did last time." This is
the clean real-world example of a program with **no week concept at all** — see WEEK-BASED MODEL
LIMITATIONS.

---

### ROUTINE BUILDER UX PATTERNS

**Creation flow** — **VERIFIED** (Hevy): name the routine → add exercises from a library → set
per-exercise details (sets/reps/weight, optional notes, optional supersets) → save. This matches
TBDFit's Web `RoutineEditorPage.tsx` almost exactly already: name field → per-exercise add-set
table with target reps/weight → Save via one atomic call. **RECOMMENDATION**: the current
implementation's shape is correct and evidenced; no structural change needed here.

**What's good about Hevy's flow for TBDFit to keep learning from**: the exercise library is treated
as a genuine second-class citizen of the builder (persistent, searchable, filterable), not an
afterthought modal you dismiss once. **What's weaker**: routine-level notes and superset grouping
add real UI complexity for marginal V1 value — explicitly DEFER both (matches the design doc's own
existing "no supersets/drop-sets" scope boundary).

---

### EXERCISE LIBRARY UX

**VERIFIED** (Hevy): a 400+ exercise library with **search**, **muscle-group filters**, and
**equipment filters**, plus custom-exercise creation. TBDFit's current Web picker
(`RoutineEditorPage.tsx`'s inline list + "Create & add" custom-exercise flow) has search/filtering
entirely absent — it lists all visible exercises unfiltered. With only 6 built-in exercises today
this is a non-issue, but it will not scale.

**MUST HAVE for V1** (given TBDFit's actual current exercise count): none of search/filter — the
list is short enough to scroll.
**SHOULD HAVE** (as the exercise catalog grows past ~20-30 rows, which will happen quickly once
custom exercises accumulate): a plain text search box. This is cheap and should be added the moment
the exercise list becomes annoying to scroll, not necessarily in this exact task.
**DEFER**: muscle-group/equipment filters (require tagging every exercise with metadata TBDFit
doesn't have yet), recently-used/favorites (requires usage tracking that doesn't exist).

---

### SET EDITING UX

**VERIFIED/PARTIALLY VERIFIED** across Hevy/Trainerize: add set, remove set, duplicate/copy a
set's values to a new set, and reorder exercises are the consistently-present interactions.
"Edit all sets at once" (apply one target to every set in an exercise) appears in some products'
marketing copy but wasn't confirmed as a named, documented feature anywhere — treat as **NOT
VERIFIED**, not a real pattern to build around yet.

Ranked for Routine V1 (this section, not the overall Program ranking below):
- **MUST HAVE**: add set, remove set (already real in TBDFit's implementation).
- **SHOULD HAVE**: "duplicate last set" (copy the previous set's target reps/weight into a new row)
  — a small, cheap win that directly addresses the same repetitive-entry problem Part 6/7 raises at
  the Program level, just one level down. Currently TBDFit's "+ Add Set" always creates a blank
  row; a "duplicate last set" variant is a minor addition to the same code path.
- **DEFER**: drag-to-reorder exercises/sets (currently append-only by design, matches the existing
  Android Routine UX completion slice's own documented limitation — consistent, not a gap unique to
  Web), bulk multi-set editing UI.

---

### ROUTINE MANAGEMENT UX

**VERIFIED** (Hevy) + already real in TBDFit: create, open, edit, delete, start are the baseline
set every product examined supports, and TBDFit's Web implementation already has all five except
"duplicate."

- **MUST HAVE**: create, open/view, edit, delete (all real today).
- **SHOULD HAVE**: **Duplicate Routine** — not currently in TBDFit's Web implementation at all, and
  a real gap: every competitor examined treats "make a variant of an existing routine" (e.g. "Push
  A" → "Push A — Heavy") as a first-class, cheap action, and it is structurally almost free to add
  (it's the same `save_routine` RPC called with `routine_id: null` and the existing routine's
  content as the payload).
- **DEFER**: sharing a routine (a real Hevy feature, but TBDFit's social/publishing direction is
  explicitly future work per prior research), routine-level notes, supersets.

---

### ROUTINE INFORMATION ARCHITECTURE

The corrected `web-information-architecture.md` already places `Plan` (Routine building) as its own
sidebar/nav item, not nested inside a Program section — this was already right, independent of any
Program work. The current real implementation goes further and makes Routine the entire content of
that destination today (Program doesn't exist yet), which trivially satisfies "Routines must never
read as subordinate content" — there is currently nothing for them to be subordinate to.

The live question is what happens **once Program ships**. Three options:

**Option A — Sibling tabs within the same nav destination**
```
Plan
├── Routines   (default view)
└── Programs
```
A single "Plan" nav item with an in-page tab switcher. **Strengths**: minimal nav surface change;
keeps the existing URL structure (`/plan/...`) largely intact; Routines stays the *default* view,
which is the correct signal that it's the primary object. **Weaknesses**: a tab switcher can read
as "these are two views of the same thing" when they are actually two independent object types.

**Option B — Two separate top-level nav items**
```
Routines
Programs
```
**Strengths**: maximally clear that both are first-class, neither is nested in the other — directly
satisfies the hard requirement with zero ambiguity. **Weaknesses**: grows the nav bar from its
current 4 items (Home/Plan/History/Profile) to 5; "Plan" as a nav label would need to be retired or
repurposed, a small but real naming churn against the already-shipped `/plan` route and its
existing "Routine" heading.

**Option C — Routines stays exactly where it is now (`Plan`), Programs gets its own new nav item**
```
Plan       (unchanged — Routines, exactly as implemented today)
Programs   (new)
```
**Strengths**: zero disruption to the just-shipped, real, tested Routine implementation or its URLs
— Programs is purely additive. Cleanly reads as "Plan is where you build things to reuse; Programs
is where you assemble them into a schedule," which is an accurate mental model given the copy/
snapshot relationship (Part 4). **Weaknesses**: "Plan" as a name doesn't obviously say "Routines" to
a new user — but this is an existing naming choice, not one this research introduces or needs to
fix.

**RECOMMENDATION: Option C.** It requires zero change to the real, tested, already-shipped Routine
UI/routes, and it structurally cannot let Program subordinate Routine because they'd be sibling nav
items from the moment Program exists. Option A is the second choice if a 5-item nav bar is judged
too crowded later. Option B is not recommended now — it forces a naming decision on "Plan" this
research has no evidence requires solving today.

---

### PROGRAM MODELS

Every real Program-capable product examined (Boostcamp, TrainHeroic) represents a program as an
**ordered sequence of weeks, each containing an ordered sequence of sessions**, with **no
requirement that every week be identical** — the "later weeks differ completely" case (Part 3) is
the normal case these products are designed around, not an edge case. None of them force calendar
dates onto this structure at the *authoring* stage — TrainHeroic explicitly separates "build the
program" (ordered, undated) from "assign it to an athlete's calendar" (dated, a separate action)
per its own support docs on "Assigning Programs to a Team or Athlete Calendar." This is strong,
direct evidence for TBDFit's own `ProgramWeek ≠ calendar week` decision (see WEEK MODEL FINDINGS).

---

### ROUTINE → PROGRAM RELATIONSHIP

**Direct, official confirmation of copy-not-live-reference at the Program level**: TrainHeroic
describes starting a program as "adding **a copy** to their calendar" — the athlete's instance is
explicitly a copy, not a reference to the coach's master definition. This validates TBDFit's
already-locked Routine→ProgramSession snapshot decision one layer up: the same copy discipline the
design doc already applies to Routine→ProgramSession should extend to Program→(future)
ProgramInstance when that layer is eventually built (still correctly deferred for now).

At the session level, TrainHeroic's drag-select-then-copy UX (see PROGRAM BUILDER UX PATTERNS)
is real, concrete evidence that **"Copy from My Routines" + "Duplicate Session" + "Duplicate Week"
are the actual load-bearing workflows** — not an attempt to maintain a live link. No product
examined maintains a live link between a reusable template and its use inside a schedule; every one
copies. TBDFit's decision is not just internally consistent, it's the industry-universal pattern
among every product with both concepts.

---

### PROGRAM BUILDER UX PATTERNS

Evaluating the three candidate desktop layouts from the brief against real precedent:

**Option A — Three-pane (weeks left / sessions center / editor right)**: no direct precedent was
found as a distinct three-pane layout in the products examined, but it is structurally close to
TrainHeroic's actual calendar-plus-detail-panel model. **Usability at 8-12 weeks**: works if the
weeks list scrolls independently and stays visually compact (a numbered list, not full cards) —
degrades if each week entry is large.

**Option B — Week cards with expandable sessions**: closer to how Boostcamp's own program pages
present a plan publicly (week-by-week accordion-style sections, each listing its sessions) —
**PARTIALLY VERIFIED** by inspecting Boostcamp's own public program pages' structure, not an
explicit design-doc citation. **Usability at 8-12 weeks**: this is the layout most likely to need a
"collapse all / jump to week N" affordance once past ~6 weeks, or it becomes a very long scroll.

**Option C — Timeline/grid**: no clean precedent found in any product examined at this scale;
TrainHeroic's calendar view is date-driven (a different, larger concept than TBDFit's undated
week-ordering), not a good structural fit for a *logical*-week model.

**RECOMMENDATION: Option A (three-pane), with the week list rendered as a compact numbered sidebar
list (not cards)**, closest to TrainHeroic's actual coach-side authoring experience (a persistent
session/week selector alongside a detail editor) and the most naturally extensible to
drag-select-multiple (Part 6) without a layout change. Option B is a reasonable fallback if
three-pane proves visually cramped on smaller desktop widths — it degrades more gracefully at
narrower viewport widths than a fixed three-pane split would.

---

### COPY / DUPLICATE WORKFLOWS

This is, across every product examined, the single most consistently-implemented and
most-explicitly-named category of feature in program building — stronger evidence here than for
almost any other question in this research:

- **Duplicate Week** — **VERIFIED** (Boostcamp, named feature) + **VERIFIED** (TrainHeroic,
  drag-select whole week → copy → paste onto a new week).
- **Copy Routine into Program** — this exact action isn't named identically anywhere (none of the
  competitors examined have a *separate* standalone-Routine object the way TBDFit does — Hevy's
  routines live inside Folders, TrainHeroic's building blocks are sessions, not reusable templates
  outside a program), but "start from an existing template rather than a blank session" is the same
  problem every one of them solves, just from within a different source concept. TBDFit's
  standalone-Routine-first model gives it a *cleaner* version of this pattern than any competitor
  actually has: copying a **real, already-built, already-used, standalone object** into a program
  is strictly better UX than any of theirs, which either have no separable-Routine concept at all
  (TrainHeroic) or bury it inside an organizational Folder with no independent identity (Hevy).
- **Duplicate Session** — **VERIFIED** (TrainHeroic, explicit named action in the same
  drag-select action bar).

---

### WEEK MODEL FINDINGS

TBDFit's `ProgramWeek = logical, not calendar, week` decision is well-supported: TrainHeroic
explicitly separates program *authoring* (ordered, undated) from *assignment* (a program becomes
dated only when placed on an athlete's calendar, a distinct step). This is structurally the same
separation TBDFit has already chosen — **RECOMMENDATION: no change, this is confirmed, not merely
untested.**

**What is genuinely lost by deferring calendar scheduling**: the ability to answer "what should I
train today" directly (TrainHeroic's calendar view exists specifically for this), and the ability
to notify/remind a user on a schedule. Neither of these is required to *build* a program — they are
required to *live inside* one day-to-day, which is downstream of Program even existing yet in
TBDFit at all. **RECOMMENDATION**: defer, consistent with the existing decision; the loss is real
but not a V1 blocker.

---

### REAL PROGRAM TYPE COMPATIBILITY

| Program type | Fit against `Program → Week → Session` |
|---|---|
| Push/Pull/Legs (fixed weekly repeat) | **Works well** — 1 week, 3 sessions, duplicated N times |
| Upper/Lower | **Works well** — same shape, 2 sessions/week |
| 3-day full body | **Works well** |
| 4-6 day bodybuilding split | **Works well** — more sessions/week, same model |
| 8-12 week hypertrophy block (progressive overload week to week) | **Works well** — this is the exact case the model was designed for; each week's sessions are independently editable, no forced duplication |
| Strength blocks (distinct phases, e.g. volume → intensity → peak) | **Possible but awkward** — representable as consecutive differently-configured weeks with no explicit "phase" boundary marker; usable, but the user must track phase transitions mentally since `Block`/`Phase` is correctly deferred |
| 5/3/1-style (a repeating 3-4 week wave keyed off a training max, not raw reps/weight) | **Possible but awkward** — the *week structure* fits fine, but the *prescription* (percentage-of-max-based, not fixed target reps/weight) is a real content-model gap, not a structural one — TBDFit's `plannedReps`/`plannedWeight` are absolute values, not formulas. This is correctly out of scope (see PART 12/DEFER: "percentage-based programming") and should stay deferred rather than half-solved. |
| StrongLifts-style A/B alternating, no week boundary | **Requires future evolution to fit as a "Program"** — there is no natural week boundary at all (it's not "Week 1: A, B, A" repeating; it's literally "whatever you did last time, do the other one next," open-ended). Forcing this into `ProgramWeek`/`ProgramSession` would be exactly the kind of premature-abstraction stretch this project has explicitly avoided elsewhere. **This case is correctly served today by plain Routine → Start with two routines named "A" and "B"** — Program is simply the wrong tool for it, not a gap to fix. |

**RECOMMENDATION**: do not redesign the model for the StrongLifts case — it is genuinely,
permanently better served by two standalone Routines than by forcing it into Program. This is
further, concrete evidence (beyond the earlier domain research's own conclusion) that Routine's
independence from Program is not just an implementation nicety but a real product requirement some
training styles structurally need.

---

### PROGRAM PROGRESS

TrainHeroic's own progress/analytics tooling (1RM-over-time graphs, volume, PRs) is built entirely
from **logged training history**, not from a separately-maintained "program progress" record — it
computes "change in performance... from the starting date to the finish" by querying actual
performed data over a date range. This is direct, if imperfect (TrainHeroic's version is
date-range-based, not program-instance-based, since it operates in a dated-calendar context TBDFit
doesn't have yet), support for the already-planned approach: **derive "Week 4 of 12" / "11 of 36
sessions completed" by counting completed `Workout`s whose `originProgramSessionId` resolves into
this Program's session tree, rather than persisting a counter anywhere.**

**RECOMMENDATION**: no `ProgramInstance`/progress-counter table needed for V1. This is now
supported by both the earlier domain-hardening work (which reached the same conclusion from a
schema-design angle) and this competitor evidence (which reaches it from a UX-precedent angle) —
two independent lines of reasoning converging on the same answer is meaningfully stronger than
either alone.

---

### CURRENT / ACTIVE PROGRAM FINDINGS

TrainHeroic's "current program" is not a separate concept the user manages — it **is** the program
they most recently added a copy of to their calendar; "assigned programs" appear directly on the
athlete's calendar/dashboard as a consequence of that one action, not a separately toggled "make
this my active program" state. Boostcamp's marketing language ("your program," singular) suggests a
similar one-at-a-time framing, but this was not independently confirmed at the UI-mechanics level —
**NOT VERIFIED** beyond the copy quoted above.

**RECOMMENDATION**: TBDFit V1 does **not** need a persisted "Current Program" concept. The
cheapest correct behavior, consistent with both competitor evidence and the "don't persist what can
be derived" principle already established: show "My Programs" as a plain list, and derive "next
unfinished session" per-program the same way overall progress is derived (Part 10) — a user with
exactly one Program in progress experiences this identically to a dedicated "Current Program" field
without TBDFit having built one. If a user genuinely runs multiple programs in parallel, that's an
edge case worth revisiting only once real usage shows it matters, not something to design around
speculatively now.

---

### CALENDAR SCHEDULING FINDINGS

Covered above (WEEK MODEL FINDINGS) — restated for completeness: every product with a real calendar
concept (TrainHeroic explicitly, Boostcamp implicitly via its "4 training days per week" framing)
treats calendar placement as a *separate, later* step from program authoring, not a property of the
program definition itself. This is exactly TBDFit's own already-planned separation.

---

### WEEK-BASED MODEL LIMITATIONS

Summarized from REAL PROGRAM TYPE COMPATIBILITY above: the model's one genuine, permanent
limitation is open-ended/non-terminating rotation programs (StrongLifts-style), which are correctly
served by plain Routine instead, not by stretching Program to fit them. Percentage-of-max-based
prescriptions (5/3/1-style) are a content-model gap, not a structural one, and are already correctly
scoped out (percentage-based programming is on the explicit DEFER list). No other real, common
training style examined exposed a structural gap.

---

### CREATOR-FUTURE IMPLICATIONS

The strongest evidence-based guardrail from this research: **do not let "start a program" become a
different code path than "copy a program."** TrainHeroic's own model treats *every* program start —
whether it's your own authored program or one a coach assigned — as "add a copy to your calendar."
If TBDFit's V1 `startProgramSession`/program-usage logic is built as a special case that only works
for programs you personally authored (e.g. by assuming `ownerId` never changes across the
Program→ProgramSession→Workout chain), a future creator-publishes/user-copies flow would require
rearchitecting that assumption rather than just adding a new entry point in front of it. The
concrete trap to avoid: nothing in the current or planned schema should assume the Program the user
is *running* was authored by that same user — even though V1 has no way to author someone else's
program yet, the copy operation itself should not encode that assumption structurally. This
requires no new tables now, only a review pass on the eventual Postgres schema to confirm.

---

### MUST HAVE — ROUTINE

Create, open/view, edit, delete, start (all real today) + **Duplicate Routine** (evidenced gap, see
ROUTINE MANAGEMENT UX — cheap to add, high competitor consistency).

### SHOULD HAVE — ROUTINE

Plain text search once the exercise library grows; "duplicate last set" quick-entry affordance.

### MUST HAVE — PROGRAM

Add Week, Add Session, **Copy Session from My Routines**, Edit exercises/planned sets on a session,
Delete Week, Delete Session, Rename Session (already decided optional-with-fallback, confirmed
sufficient — see below), Save Program.

### SHOULD HAVE — PROGRAM

**Duplicate Week** (the single highest-value action across every product examined for solving the
"12 weeks of mostly-repeated content" problem — recommend prioritizing this over Reorder if forced
to choose), **Duplicate Session**, Reorder Week/Session.

### DEFER

Calendar scheduling/weekdays, `ProgramInstance`/Enrollment/`activeProgram`, creator marketplace,
publication/versioning, coach/client assignment features, adaptive/automatic progression, automatic
deloading, RPE/RIR prescription engine, percentage-of-max-based programming, arbitrary/
non-terminating microcycles, `Block`/`Phase` grouping above `ProgramWeek`, drag-and-drop everywhere
(only the specific week/session duplicate-and-reorder actions above are justified; a generically
draggable UI for every list is not), AI-generated programming. Each of these has a real, evidenced
product (Fitbod for adaptive/algorithmic, TrainHeroic/Trainerize for coach/client and marketplace
features, 5/3/1-style programs for percentage-based prescription) doing it well — none of that
evidence changes that building it now would be solving a problem TBDFit does not yet have paying
users to justify, consistent with the project's own stated MVP-by-December risk posture.

---

### RECOMMENDED TBDFIT WEB ROUTINE EXPERIENCE

No structural change to what's already shipped. Two small, evidence-backed additions:
1. **Duplicate Routine** — one new button, reusing the existing `saveRoutine(null, ...)` path with
   the source routine's content as the payload.
2. A plain **search input** above the exercise picker list, added whenever the exercise count makes
   scrolling annoying (not urgent today at 6 built-ins).

Routine's information architecture position: **Option C** (stays at `Plan`, unchanged) — see
ROUTINE INFORMATION ARCHITECTURE above.

### RECOMMENDED TBDFIT WEB PROGRAM EXPERIENCE

```
Programs                                    [ + Create Program ]

My Programs
  12 Week Strength           [ Open ]
  Hypertrophy Block          [ Open ]

────────────────────────────────────────────────────────────────

12 Week Strength                                    [ Save ]

Weeks                    Week 3 — Sessions           [ Session editor ]
  Week 1                   Upper A     [Copy from Routine ▾]
  Week 2                   Lower A     [Duplicate] [Delete]
  Week 3 ◀ selected        + Add Session
  Week 4
  ...
  Week 12
[ + Add Week ]  [ Duplicate Week ▾ ]
```

A user building "12 Week Strength" would: Create Program → name it → Add Week → for Week 1, "Copy
from Routine" three times (Push A, Pull A, Legs, all already-built standalone Routines) → Duplicate
Week three more times for Weeks 2-4 → open Week 5, edit each session's sets/reps directly (per Part
7's evidenced UX pattern — no bulk "apply to weeks 5-8" tool needed for V1, just repeat the
duplicate-then-edit pattern) → repeat through Week 12 → Save. This never requires re-adding
exercises from scratch after Week 1, matching the "avoid painful repetitive manual editing" success
criterion from the brief, using only MUST/SHOULD-tier actions.

Program never gates or wraps Routine creation — a user who never opens "Programs" experiences no
difference in their ability to create, edit, and start Routines.

---

### PRODUCT RISKS / FAILURE MODES

1. **The Hevy Folder trap, restated precisely**: if Program IA work is ever rushed, the path of
   least resistance is "just let a Routine belong to a Program" (a loose grouping field) instead of
   building the real `ProgramWeek`/`ProgramSession` copy hierarchy — this is exactly Hevy's Folder
   failure mode, now with direct official-source confirmation of what it actually looks like
   (zero week/order/progress semantics). The already-locked snapshot decision is the correct
   defense; this risk is about implementation discipline, not a design gap.
2. **Building Duplicate Week last instead of first**: every product examined treats week/session
   duplication as core, not a nice-to-have polish item. If Program V1 ships without it, the builder
   will be technically complete but practically painful for exactly the 8-12 week use case it
   exists to serve — this is the one MUST-tier-adjacent item most likely to be under-prioritized by
   accident.
3. **Assuming the Program author always equals the Program runner** (see CREATOR-FUTURE
   IMPLICATIONS) — a silent architectural assumption that costs nothing to avoid now and a real
   rearchitecture to fix later.

---

### HUMAN DECISIONS STILL REQUIRED

1. Routine IA: Option C (keep `Plan` as-is, add a separate `Programs` nav item) vs. Option A
   (tabs within `Plan`) — this research recommends C but it is a naming/nav-surface preference, not
   something the evidence forces.
2. Whether "Duplicate Routine" ships as part of the *next* Program-focused implementation task or as
   a small standalone Routine polish item first — it has no dependency on Program existing.
3. Whether the three-pane Program Builder layout (recommended) or week-cards (fallback) is worth a
   quick low-fidelity comparison before committing engineering time, given neither has a strong
   direct precedent from a primary comparator (TrainHeroic's actual visual layout was not directly
   inspectable — its interaction *model*, not its literal layout, is what's evidenced here).

---

### SOURCES

- Hevy — "Workouts vs Routines in Hevy" (official help center) — VERIFIED — https://help.hevyapp.com/hc/en-us/articles/33703513582871-Workouts-vs-Routines-in-Hevy-What-They-Mean-and-How-to-Use-Them
- Hevy — "How to Create Folders and Gym Routines" (official) — VERIFIED — https://www.hevyapp.com/features/gym-routines/
- Hevy — "Build a Workout Program: Create & Organize Routines" (official help center) — PARTIALLY VERIFIED (search-surfaced) — https://help.hevyapp.com/hc/en-us/articles/34953606698903-Build-a-Workout-Program-Create-Organize-Routines
- Hevy — "Explore the Gym Workout Routine Library (25+ Programs)" (official) — VERIFIED — https://www.hevyapp.com/features/gym-workout-routines/
- Boostcamp — Program Creator / Features pages (official) — PARTIALLY VERIFIED — https://www.boostcamp.app/program-creator, https://www.boostcamp.app/features
- TrainHeroic — "Programming Shortcuts: Copy, Paste, Delete and Repeat Sessions" (official support) — VERIFIED (search-surfaced quotes) — https://support.trainheroic.com/hc/en-us/articles/18156803899661-Programming-Shortcuts-Copy-Paste-Delete-and-Repeat-Sessions
- TrainHeroic — "For Coaches: Copying a Program" (official support) — VERIFIED — https://support.trainheroic.com/hc/en-us/articles/18156951622669-Copying-a-Program
- TrainHeroic — "Assigning Programs to a Team or Athlete Calendar" (official support) — referenced via search index, title confirms calendar/authoring separation — https://support.trainheroic.com/hc/en-us/articles/18171008686349-Assigning-Programs-to-a-Team-or-Athlete-Calendar
- TrainHeroic — "For Athletes: I just purchased a Program. How do I view it?" (official support, "adding a copy to their calendar" quote) — VERIFIED — https://support.trainheroic.com/hc/en-us/articles/18156853284237-For-Athletes-I-just-purchased-a-Program-How-do-I-view-it
- TrainHeroic — "Analytics for Lifts and Working Maxes" (official support) — PARTIALLY VERIFIED — https://support.trainheroic.com/hc/en-us/articles/18156762137741-Analytics-for-Lifts-and-Working-Maxes
- Trainerize — "A Better Way to Build Workouts with Timed Sets and a Duplicating Exercise Shortcut" (official blog) — VERIFIED — https://www.trainerize.com/blog/trainerize-update-a-better-way-to-build-workouts-with-timed-sets-and-a-duplicating-exercise-shortcut/
- StrongLifts — "Stronglifts 5×5 Workout Program: Quick Start Guide" (official) — VERIFIED — https://stronglifts.com/stronglifts-5x5/workout-program/
- Fitbod — "Static Workout Plans Vs. Adaptive Training Apps" and "My Plan" (official blog/help) — PARTIALLY VERIFIED — https://fitbod.me/blog/static-workout-plans-vs-adaptive-training-apps-why-fitbod-adjusts-to-you/, https://help.fitbod.me/hc/en-us/articles/34336407191191-My-Plan
- Strong — carried forward from this session's earlier corrected primary competitor benchmark (not re-fetched in this task).
- TBDFit internal: `docs/product/program-routine-domain-investigation.md`, `docs/architecture/program-routine-first-slice-design.md`, `docs/product/web-information-architecture.md`, `docs/product/frontend-prototype-notes.md`, `web/src/pages/{PlanPage,RoutineEditorPage,RoutineDetailPage}.tsx`, `web/src/data/routines.ts`.

`WEB ROUTINE + PROGRAM PLANNING RESEARCH STATUS: COMPLETE — NO PRODUCT DECISION MADE`
