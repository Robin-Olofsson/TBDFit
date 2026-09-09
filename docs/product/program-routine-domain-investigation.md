# TBDFit Program / Routine Domain Investigation

## Status

`RESEARCH / DESIGN INVESTIGATION — NOT A PRODUCT DECISION`

This document investigates how TBDFit should model and present `Routine`, `Program`, `Week`,
`Training Day` / `Planned Session`, `Workout`, and `Collection`/`Folder`. It is not an ADR, not a
product decision, and does not modify any existing ADR or PD status. No backend, schema, or UI has
been touched to produce it — see [`docs/product/product-information-architecture.md`](product-information-architecture.md)
§5 and [`docs/product/web-information-architecture.md`](web-information-architecture.md) for the
existing (unresolved) Routine/Program open decisions this document investigates in depth, and
[`docs/architecture/multi-client-product-vision.md`](../architecture/multi-client-product-vision.md)
for the broader direction this fits into.

Labels used throughout: **FACT** (verified from code or official documentation), **INFERENCE**
(reasoned conclusion from facts), **RECOMMENDATION** (design judgment, not a decision), **OPEN
QUESTION** (requires developer/product approval).

---

## 1. Current TBDFit model (FACT, verified against code)

**Android Phone** (`android/phone/src/main/kotlin/com/tbdfit/phone/workout/`) has a real, persisted
Room domain: `ExerciseEntity` (built-in or custom, account-scoped via nullable `ownerId` FK to
`LocalAccount`), `WorkoutEntity` (an actual execution session/record — `ACTIVE`/`COMPLETED` status,
owned by `LocalAccount`), `WorkoutExerciseEntity` (Workout↔Exercise with position),
`WorkoutSetEntity` (weight/reps/completion/position). **There is no `Routine` or `Program` Room
entity anywhere** (grep-confirmed). `Workout` in the real model means *what actually happened*, not
a plan.

A prototype `Routine` concept exists in `com.tbdfit.phone.shell.RoutinePrototype.kt`: three
hardcoded routines built from **real built-in `Exercise` IDs**, with no persisted `Routine` row at
all. Pressing "Start Routine" calls the real `WorkoutRepository.startWorkout` +
`addExercise` — so the *routine content* is prototype, but the *resulting Workout* is genuinely
real and durable. This is deliberately documented (`frontend-prototype-notes.md`) as a "MIXED"
integration, not a real Routine feature.

**Web** (`web/src/types.ts`, `web/src/data/prototypeData.ts`) has an equivalent prototype-only
`Routine { id, name, exercises: RoutineExercise[] }` shape — pure local state, no backend, no
bridge to real execution at all (Web has no execution capability, real or prototype).

**No `Program`, `Folder`, or `Collection` concept exists anywhere in this repository** on any
client (grep-confirmed). The existing docs already anticipate a `Program` layer conceptually
(`product-information-architecture.md` Journey E, `web-information-architecture.md` Open Decision
#5, `multi-client-product-vision.md`'s open questions) but have deliberately left it unresolved —
this document is the first dedicated investigation of it.

**Existing invariants this investigation must preserve, not re-derive** (already established
elsewhere this cycle, cited not repeated in full):
- Local-first execution, no permanent Phone execution authority, sync ≠ durability (PD-001/PD-002).
- `LocalAccount` ownership ≠ social visibility.
- A copied object becomes independently owned by the recipient — never a shared mutable reference
  — established via Hevy's own "Save as Routine" vs. "Copy Workout" split
  (`frontend-product-ux-research.md` §3).
- `Workout.sourceRoutineId` is already anticipated as an *informational, non-authoritative* field
  (`strength-workout-first-slice-design.md`'s Plan-vs-Execution section) — a Workout must never be
  live-bound to a mutable template once it starts.

---

## 2. Is Exercise → Routine → Program → Workout the right chain? (RECOMMENDATION)

**Yes, with one addition.** The chain is directionally correct but incomplete: it collapses
"a single planned session" and "the thing that groups sessions over time" into one word
(`Routine`), which is exactly the ambiguity Hevy's own `Program` (see §3) suffers from. The
corrected chain:

```text
Exercise            — Bench Press (identity; never changes when renamed)
Routine             — a single reusable planned session, e.g. "Push A"
Program             — a structured, ordered, multi-week plan composed of scheduled Routines
Workout             — an actual performed session (durable execution record)
```

`Program` sits *above* `Routine`, not beside it — a Program's job is to say *which Routine, in
what order, changing how over time*, not to redefine what a session is.

---

## 3. Hevy findings (FACT, official sources + one direct confirmation of the user's complaint)

**Routine vs. Folder, verified from Hevy's own help documentation**
([Build a Workout Program: Create & Organize Routines](https://help.hevyapp.com/hc/en-us/articles/34953606698903-Build-a-Workout-Program-Create-Organize-Routines),
[How to Create Folders and Gym Routines](https://www.hevyapp.com/features/gym-routines/)):
- A `Routine` is a reusable session template (name + exercise list + planned sets/reps).
- A `Folder` is a flat, unordered container: routines can be dragged in/out, folders can be
  renamed/reordered/deleted/shared. **A Folder carries zero domain semantics beyond
  containment** — no week index, no ordering *meaning* (only display order), no progression rule,
  no schedule, no completion/progress state.

**The direct confirmation of the user's core complaint** (FACT, via
[Program Library](https://help.hevycoach.com/en/articles/8460782-program-library) and search-corroborated help content): Hevy *does* use the word "Program" for its curated library
(26 pre-built multi-week plans). But **saving a Program from that library literally creates a
Folder containing the program's Routines** — nothing more. There is no dedicated `Program` domain
object in the consumer app once imported: no week counter, no "Week 4 of 12," no enforced order
beyond the folder's display order, no distinct progress state separate from ordinary routine usage
stats. **Hevy's own "Program" degrades into exactly the weak Folder-of-Routines structure the user
identified** — this is not a hypothesis, it is what Hevy's own documentation describes.

**Progress tracking**: Hevy tracks exercise/volume statistics over time (graphs, PRs), but this is
*workout-history analytics*, not *program-position tracking* — nothing in official sources
describes a "you are on week 4 of 12" concept.

**Confidence note**: the Hevy help-center pages returned HTTP 403 on direct fetch from this
sandbox; findings above are corroborated across multiple independent search results quoting the
same official pages, which is the same medium-confidence standard used throughout this session's
other Hevy research — not a single authoritative page-read.

---

## 4. Boostcamp findings (FACT, official site)

Boostcamp treats multi-week structure as **first-class**, not a container of routines
([12 Week Program](https://www.boostcamp.app/users/EbZ56K-12-week-program),
[Custom Program Creator](https://www.boostcamp.app/custom-program)):
- Programs run 4–12+ week **blocks** that build volume/intensity, then deload — "the progress
  comes across the block, not within a single session" (a direct structural claim, not folder
  organization).
- Deloads are typically built in every 4–8 weeks as an explicit lighter week.
- Training frequency is a program-level property (e.g. "4 training days per week") independent of
  calendar weekdays.
- Every session is fully specified (exercises/sets/reps/rest) — sessions are the leaf, weeks/blocks
  are real structural levels above them, not just folders.

This is the strongest evidence that a *real* week/block layer is buildable and valuable — Boostcamp
is a program-authoring product first, a routine-logging product second.

---

## 5. TrainHeroic findings (FACT, official/marketplace sources)

TrainHeroic's structure is explicitly hierarchical
([Heroic Athlete Program Overview](https://support.trainheroic.com/hc/en-us/articles/18170959525389-Heroic-Athlete-Program-HAP-Overview),
marketplace program pages): **Block → (sub-phase, e.g. Triphasic 2-week waves) → Week → Session**.
Coaches author periodized cycles and explicitly **reuse template weeks** across a block. Training
frequency and session count vary by program (4-day, 5-day, 6-day splits all exist), confirming
frequency is a program property, not a fixed assumption.

This maps directly to the brief's "Option C" (Program → Blocks/Phases → Weeks → Sessions) — real,
shipped, at the high end of structural ambition. Useful as evidence for *what's possible*, not
necessarily what TBDFit needs at V1 (TrainHeroic is a coach-authoring product for paying athletes,
a materially different context than a solo lifter's personal program).

---

## 6. Strong / Fitbod findings

**Strong**: Search results (third-party SEO content, e.g. RepReturn/setgraph) claimed Strong
supports "full periodization structures" and "program-level cycles across multiple weeks." **This
is NOT VERIFIED against any official Strong source**, and it directly conflicts with this session's
own earlier, official-source-backed competitor benchmark (`competitor-benchmark-hevy-strong-fitbod.md`),
which found Strong's real model is the same routine (template) vs. workout (execution) split as
Hevy, with **Strong's own help center stating they plan to change that flow specifically because
it's confusing** — no mention of a native multi-week Program builder anywhere in official
documentation. **Treat the third-party "full periodization" claim as marketing/SEO overstatement,
not fact.** Strong's real, verified position: no native Program concept.

**Fitbod**: Confirmed (official site/help center, [My Plan](https://help.fitbod.me/hc/en-us/articles/34336407191191-My-Plan),
[How Fitbod Personalizes...](https://fitbod.me/blog/how-fitbod-personalizes-your-workout-plan-using-smart-training-algorithms/)):
periodization is **real-time and adaptive**, computed per-session by an algorithm reacting to
logged performance — not a pre-authored, inspectable multi-week structure. "My Plan" configures
*generation parameters* (goal, equipment, schedule), not a program a user can read week-by-week.
Structurally the opposite end of the spectrum from Boostcamp/TrainHeroic: no Program object exists
to inspect at all, by design.

---

## 7. Real program structure findings (structural analysis, not a "best program" ranking)

Cross-referencing 5/3/1, Starting Strength, StrongLifts 5×5, PPL variants, and powerlifting/
hypertrophy blocks against a Buff-Dudes-style 12-week plan surfaces these **structural
requirements** a model must support:

| Requirement | Example |
|---|---|
| Repeated sessions across weeks | PPL: the same Push/Pull/Legs session type repeats every microcycle |
| Changing sessions across weeks | Buff-Dudes-style: Week 1 and Week 8 use different exercises/volume entirely |
| Session-count progression, not calendar-week progression | Starting Strength / StrongLifts: alternate Workout A/B *every session*, add weight *every session* — there is no "Week 3" concept at all, only session index |
| Load/rep progression tied to a formula | 5/3/1: weekly percentage-of-training-max waves (5s week, 3s week, 1s week) inside a 4-week block, training max increases per cycle |
| Deload as a structural, not incidental, week | 5/3/1's 4th week; Boostcamp's every-4-8-week deload |
| Phase/block transitions with different training emphasis | Accumulation → intensification → peak, each a different rep/intensity zone |
| Variable training frequency per week/program | 3-day vs. 6-day PPL; TrainHeroic's per-program frequency |
| Optional/conditional sessions | TrainHeroic's optional speed/agility day |
| Rest days as absence, not as modeled entities | No program examined models "rest day" as a real object — it's simply a gap in the ordered session list |

**The most important structural finding**: **not every program is organized by calendar week at
all.** Starting Strength and StrongLifts progress purely by *session count* — "next session" means
"whichever of A/B you haven't done most recently," entirely independent of what week or day it is.
A model that hard-codes `Program → Week(1..12) → Day(1..7)` cannot represent these two extremely
common, simple programs without an awkward fiction (e.g. pretending every session is its own
"week"). This directly validates §11's caution.

---

## 8. Folder vs. Collection vs. Program (RECOMMENDATION)

Three genuinely different concepts, evaluated independently rather than assumed:

- **Program** — earns first-class status. It has real semantics research repeatedly confirms are
  valuable (order, progression, week/block structure, completion state) — Boostcamp and
  TrainHeroic both build entire products around it.
- **Routine** — already established, keep as the single-session template concept.
- **Collection/Folder** — **does NOT need to exist in TBDFit's V1, and arguably not at all in the
  near term.** Its only demonstrated value in Hevy is personal organization once a user has *many*
  routines (a scale problem TBDFit doesn't have yet — the prototype has exactly 3). Critically,
  **Hevy's own Folder is precisely the "weak generic container" the user is worried about
  copying** — building it now, before Program exists, would be building the *symptom* the user is
  reacting against. If loose grouping is ever needed later (e.g. "Hotel Workouts" bucket), it
  should be a thin, honestly-scoped `Collection` that explicitly implies **nothing** about order,
  progression, schedule, or completion — never named or modeled as if it were a Program.

**Recommendation: build Program deliberately; do not build Folder/Collection at all right now.**
This is the single clearest way to avoid the exact mistake the user flagged.

---

## 9. Routine terminology (RECOMMENDATION)

Keep **`Routine`** for a single reusable session template. It already matches the vocabulary
established across the Phone and Web prototypes, Hevy's and Strong's own usage (reducing
user-relearning cost), and the existing `sourceRoutineId` precedent in
`strength-workout-first-slice-design.md`. No renaming needed — this term is not the weak link.

## 10. Program terminology (RECOMMENDATION)

Keep **`Program`** as the domain and user-facing term for a structured multi-week plan. It is
already the word Boostcamp, TrainHeroic, and (nominally) Hevy all use for this concept, so it needs
no user re-education, and it's already present as placeholder vocabulary in three existing TBDFit
docs. Avoid "Training Plan" as a synonym in the UI — pick one word and use it everywhere
(see §21's ambiguity notes).

---

## 11. Week / Day / Session semantics (RECOMMENDATION, directly informed by §7)

**Week should NOT be a rigid, mandatory calendar-week entity at V1.** Given that Starting
Strength/StrongLifts-style session-count progression is common and cannot be forced into a 7-day
grid without distortion, the correct minimal model is:

```text
Program
└── ProgramWeek (an ORDERED GROUPING, index-based, not calendar-bound)
    └── ProgramSession (ordered within the week)
```

`ProgramWeek` is real (it's how Boostcamp/TrainHeroic/the Buff-Dudes case all actually organize
content, and where "Week 4 of 12" progress language comes from), but it is a **sequence index**,
not a calendar week — Week 1 doesn't have to mean "the calendar week starting Monday." A
session-count-only program (Starting Strength) is representable as a **degenerate Program with one
`ProgramWeek` containing an alternating A/B `ProgramSession` sequence that repeats** — the
consuming UI can present "next session" without ever surfacing a week number if the program author
chooses not to. This means `ProgramWeek` must support **repetition/looping**, not just a fixed
12-item list, to cleanly represent PPL/StrongLifts-style microcycles without duplicating identical
week data twelve times.

**Day terminology**: avoid "Day 1 = Monday" entirely. Use **`Training Day`** or **`Planned
Session`** (not bare "Day") in any UI copy to make clear it means *position in sequence*, not
*calendar day* — directly addressing §12's concern. Domain name: `ProgramSession` (see §7 hierarchy
options below) — avoids "Day" ambiguity at the schema level too.

---

## 12. Scheduling vs. ordering (RECOMMENDATION)

**Support ordering now; do not build calendar scheduling at all in V1.** Every structural
requirement in §7 is satisfiable with pure ordering (Week N → Session M, in sequence) — none of the
programs examined *require* pinning a session to an actual calendar date to function; users decide
day-to-day when to do "next session." Calendar scheduling (assigning specific dates, push
notifications, missed-session handling) is a real, separate, and more complex future capability —
building it now would be solving a problem the structural research didn't surface as necessary.

---

## 13. Progression (RECOMMENDATION — structural minimum, not an engine)

TBDFit should **not** build a generic progression rule engine now. But the data model must not
foreclose one. The minimum structural requirement, derived from §7: **a `ProgramSession`'s planned
prescription (see §15) must be independently defined per occurrence**, not shared by reference
across weeks. If Week 1 Day 1 and Week 5 Day 1 use the same `Routine` but different loads, the
Program must be able to say so *without* mutating the underlying Routine — this is a direct
consequence of §8's reference-vs-snapshot question (see §14), not a separate progression feature.
As long as each `ProgramSession` owns its own planned sets/reps/load values (rather than pointing
at one shared, single Routine definition for all 12 weeks), future progression logic (auto-fill
week N+1 from a formula) becomes an *authoring convenience* layered on top — never a schema
migration.

---

## 14. Routine reference vs. snapshot (RECOMMENDATION — this is a real, consequential decision)

**A Program must own snapshots of its planned sessions, not live references to mutable Routines.**
Reasoning: if `Program Week 1 Day 1` merely *pointed at* `Routine: Push A`, then editing `Push A`
later (e.g. after finishing the program, or while another program still references it) would
silently rewrite what Week 1 said — indistinguishable from rewriting history. This directly
violates the already-established invariant that a `Workout`'s `sourceRoutineId` is informational,
never authoritative (§1) — the same discipline must apply one level up. A `Routine` may still be
useful as the **authoring convenience** that seeds a `ProgramSession`'s content at creation time
(copy its exercises/sets/reps in), but from that point the `ProgramSession` is its own independent,
owned definition. This is consistent with, and reuses, the already-adopted "copy creates an
independent object" precedent (§1) rather than inventing a new rule.

---

## 15. Program Definition vs. user Program Instance (RECOMMENDATION — likely necessary eventually, not now)

**The distinction is real and will matter, but is SAFE TO DEFER for a single-user V1.** Today,
"Robin creates a Program" and "Robin runs a Program" are the same act on the same object — no
separation is needed while there's exactly one owner and no publishing. The distinction becomes
load-bearing the moment **someone else** can start a program that isn't theirs (§16): a Published
Program (a creator's shared definition) must not be the same mutable row as "Robin's Program, week
4 of 12, with his own substitutions" — otherwise one user's progress/edits would corrupt the shared
definition, or worse, another user's. **Defer building this split now; do not build a Program model
that would make adding the split later a rewrite.** Concretely: keep `Program` and its progress
state (current week/session, completion) as separate concerns from day one internally, even before
a second "instance" object exists — i.e., don't bake "current week index" as a mutable field
directly on the same row future publishing would need to keep immutable.

---

## 16. Creator / published program implications (RECOMMENDATION, extends an already-established pattern)

This is a direct extension of the already-adopted principle (§1, from the Hevy Save-as-Routine/
Copy-Workout precedent): **Publish → an immutable/versioned definition. Start/Copy → a new,
independently-owned instance for the recipient.** Concretely for programs:

```text
Creator's mutable draft  ≠  Published Program (a version, frozen at publish time)  ≠  a starter's owned Program Instance (their own copy, their own progress, their own edits)
```

If the creator edits and republishes, existing starters keep running their already-copied version
unless they explicitly choose to update — mirroring how software versioning avoids silently
breaking existing consumers. **Do not implement any of this now** — it is a consequence of §15's
Definition/Instance split, itself deferred; recorded here only so the eventual split is designed
with this in mind rather than bolted on afterward.

---

## 17. Editing an active program (OPEN QUESTION, discovery only)

Two candidate models, neither implemented here: **(a)** edits to a Program's definition
retroactively apply to all in-progress instances of it (simple, but breaks the moment publishing
exists — see §16), or **(b)** an in-progress Program Instance is "materialized" (its own owned
snapshot) from the moment a user starts it, and edits to the original definition never touch
already-started instances. **(b)** is consistent with §14's snapshot recommendation and should be
the default assumption for any future design — but this is explicitly flagged as needing real
product judgment once personalization/substitution UX is actually designed, not resolved here.

---

## 18. Program → PlannedSession → Workout → history invariant (RECOMMENDATION — restates an existing rule at one more layer)

**This invariant already exists for Routine→Workout (§1) and must extend unchanged to
Program→Workout**: a completed `Workout`'s durable record must never be rewritten by a later edit
to the Program that spawned it. This is not a new rule — it's the same "planned intent ≠ recorded
result" boundary already established in `strength-workout-first-slice-design.md`, just confirmed to
apply transitively through one more layer of planning object.

---

## 19. Workout provenance (RECOMMENDATION, conceptual only — no field names committed beyond illustration)

A `Workout` eventually benefits from recording **which planned session, if any, it came from** —
useful for program-progress calculation ("which sessions has the user completed"), history display
("this was Week 3, Day 2 of 12-Week Hypertrophy"), and (later) creator attribution analytics. The
existing `sourceRoutineId` precedent generalizes naturally to something conceptually like
`sourceProgramSessionId` (illustrative name only) — **informational, never authoritative**, exactly
like its Routine-level counterpart: deleting or editing the originating Program must never cascade
into deleting or rewriting the Workout. This keeps execution fully decoupled from mutable planning
templates, consistent with local-first execution never depending on a remote/mutable object's
continued existence.

---

## 20. "Plan" screen mental model (candidate, NOT accepted IA)

A candidate Phone/Web landing shape worth prototyping later (not decided here):

```text
PLAN

Current Program
12-Week Hypertrophy — Week 4 of 12
[ Continue ]

My Programs
...

My Routines
Push A · Pull A · Leg Day
```

This reads as a training-programming product rather than a file manager — directly answering the
user's motivating concern — but it is a **candidate for future UX-prototype evaluation**, not a
decision. It should be judged the same way the existing Home/Workout/You prototype was: with
disposable, hardcoded content, before any backend commitment.

---

## 21. Terminology comparison

| Term | Likely user meaning | Domain meaning (recommended) | Ambiguity risk | Competitor usage | TBDFit recommendation |
|---|---|---|---|---|---|
| Routine | "My workout template" | Single reusable planned session | Low | Hevy, Strong: "Routine" | **Keep** |
| Workout Template | Same as Routine | — | Redundant with Routine | Occasional third-party synonym | Avoid — pick one word |
| Session | "A single workout" | Synonym-risk with Workout (execution) | Medium — could mean planned OR performed | TrainHeroic: "session" for planned | Use only inside `ProgramSession` (planning), never for the executed record |
| Training Day | "Which day in the plan" | UI label for a `ProgramSession`'s position | Low if paired with explicit sequence, not calendar | Common informal term | **Use in UI copy**, not as the domain entity name |
| Program | "A structured multi-week plan" | Structured, ordered, multi-week plan | Low | Boostcamp, TrainHeroic, (nominally) Hevy | **Keep** |
| Training Plan | Same as Program | — | Redundant with Program | Occasional synonym (TrainingPeaks) | Avoid — pick one word |
| Plan | Ambiguous — could mean "Program," "the Plan nav tab," or "planned vs. executed" | Reserved for the nav/product-area name only, not an entity | High if overloaded | Web's existing "Plan" nav section | Keep as the **nav section name** only; never as an entity class name |
| Folder | "A place I put things" | — (not modeled) | High — implies filesystem, not training structure | Hevy | **Do not build** |
| Collection | "A loose group of things" | Optional, explicitly non-semantic grouping | Medium if it starts accreting order/progress meaning | Not directly seen in competitors under this name | **Defer**; if ever built, keep deliberately inert (no order/schedule/progress meaning) |
| Block | "A training phase" | Optional future grouping above Week | Low | TrainHeroic | Defer to post-V1 |
| Phase | Same as Block | — | Redundant with Block if both exist | TrainHeroic (implicitly), general S&C usage | Pick one (`Block`) if this level is ever added |
| Week | "Calendar week" (risk) | Ordered, index-based grouping — NOT necessarily 7 calendar days | High — users may assume Mon–Sun | Boostcamp, TrainHeroic, Hevy (informally) | **Keep the word**, but the domain model must not require calendar semantics (§11) |
| Workout | "A workout I did" | Durable, actual execution record | Low (already established, real, tested) | Universal | **Keep — already correct and shipped** |

---

## 22. V1 domain options

**NEEDED NOW** (if/when a real Program vertical slice is greenlit — not this task):
- `Routine` (finally made real/persisted — currently prototype-only everywhere)
- `Program`
- `ProgramWeek` (ordered index, supports repetition for session-count-based programs)
- `ProgramSession` (owns its own planned prescription — see §14)
- Program↔Workout provenance field (§19, informational only)

**SAFE TO DEFER**:
- `Block`/`Phase` grouping above Week (TrainHeroic-style) — no evidence TBDFit needs this before a
  real multi-week Program even ships once.
- Program Definition vs. Instance split (§15) — defer until publishing/creator features are
  actually being built.
- Calendar scheduling (§12).
- Formal progression-rule engine (§13) — manual per-session authoring is sufficient for V1.
- Program pause/restart/skip UX (§16 area) — real but not foundational; can be added to
  `ProgramSession` completion tracking later without a model rewrite if §14's snapshot ownership is
  respected from the start.

**SHOULD NOT BE MODELED YET**:
- `Collection`/`Folder` (§8) — actively harmful to build now; it's the anti-pattern being avoided.
- Generic/abstract containers (`Node`, `ScheduleItem`, `GenericTemplate`, etc.) — no evidence any
  competitor or real program structure needs this generality; would only add indirection.
- Published/versioned Program objects (§16) — real future need, zero current user to serve it.

---

## 23. Backend/domain implications (discovery only — no implementation)

- **Identity/ownership**: `Program` and `Routine` should be `LocalAccount`-owned exactly like
  `Workout`/custom `Exercise` already are (§1) — no new ownership primitive needed, reuse the
  existing FK pattern.
- **Ordering**: `ProgramWeek`/`ProgramSession` need an explicit position column, same
  deterministic-position pattern already proven for `WorkoutExercise`/`WorkoutSet`.
- **Deletion semantics**: deleting a `Program` should not delete `Workout` history that references
  it (§18/§19) — same `RESTRICT`-not-`CASCADE` discipline already used for `Exercise`→`WorkoutExercise`.
- **Copying**: starting/copying a Program must produce new, independently-owned rows for the
  recipient (§16), not a shared reference — same discipline already implemented for
  cross-account isolation.
- **Snapshots**: a `ProgramSession`'s planned content must be its own row-owned data, not a live
  join to a `Routine` row that could change later (§14) — this is the single most consequential
  schema decision in this investigation.
- **Active program state**: "current week/session" progress tracking is mutable per-user state —
  keep it logically separable from the (eventually immutable-once-published) Program definition
  itself (§15), even if both live in the same table for a single-user V1.
- **Offline/local-first**: Android's existing local-first execution model means a `Program` a user
  is actively running must be fully present in Room for offline execution — the same principle
  already governing `Workout` (§25 below).
- **Supabase replication**: whatever mechanism eventually replicates `Workout` data across devices
  (still undecided — PD-001/PD-002, ADR-005) will need to carry `Program`/`Routine` data too; no
  new replication *design* is implied, just an additional payload shape.

---

## 24. Multi-client implications

- **Web**: strongest fit for Program/Routine **creation and multi-week editing** — this is the one
  concrete, evidenced Web-primary capability already identified (`web-information-architecture.md`
  Option B, Hevy-sourced). A Routine Builder's reorder/planned-sets-reps UI (already prototyped on
  Web) generalizes naturally to a Program's week/session editor.
- **Phone**: primary for **browsing a Program, seeing "current session," and executing it** — the
  existing "start a real Workout from a prototype Routine" bridge (§1) is the direct precedent for
  how "start today's ProgramSession" should eventually work.
- **Watch**: minimal — at most "here's the current/next planned session," never program authoring
  or editing (consistent with the already-established execution-only Watch role, ADR-005).
- **No forced parity**: Web does not need to execute a Program any more than it needs to execute a
  bare Workout (still an open question per ADR-006/web research) — Web's job is to make the plan
  good; Phone/Watch's job is to run it.

---

## 25. Local-first implications (discovery only)

Whatever `Program`/`ProgramSession`/`Routine` data a user is actively relying on for execution must
be replicated into Room so Phone can start today's session **even if the backend is temporarily
unavailable** — this is a direct extension of the already-implemented `SessionUnavailable` local-
workout-access pattern (`android/phone/.../auth/SessionUnavailableWorkoutAccess.kt`), not a new
principle. A Program authored on Web is not useful to Phone until *some* replication of its
structure reaches the device — this is exactly the still-undecided sync mechanism flagged in
PD-001/PD-002/the multi-client vision doc, not resolved here, but explicitly a dependency any real
Program implementation will need before Phone can execute a Web-authored Program offline.

---

## 26. Competitor comparison table

| Product | Routine/session concept | Multi-week program | Weeks/phases | Progress tracking | Folder/collection | Creator/published model |
|---|---|---|---|---|---|---|
| Hevy | VERIFIED — Routine (template) vs. Workout (execution) split | PARTIALLY VERIFIED — "Program" exists as a curated library concept only | NOT VERIFIED as a real structural entity — importing collapses to a flat Folder | NOT VERIFIED — only workout/exercise history stats, no program-position tracking found | VERIFIED — Folder, flat container, share-by-link | PARTIALLY VERIFIED — pre-built library only; no user-to-user creator/publish flow found for programs specifically (social feed/follow is separate, see prior benchmark) |
| Boostcamp | VERIFIED — sessions inside programs | VERIFIED — 4-12+ week blocks, real structural claim ("progress comes across the block") | VERIFIED — deloads, block progression | PARTIALLY VERIFIED — block-level progress implied, exact UI not confirmed | NOT VERIFIED / not applicable — program-first product, no folder concept found | VERIFIED — 11,000+ community/library programs, creator-authored |
| TrainHeroic | VERIFIED — session is the leaf unit | VERIFIED — explicit Block→Week→Session hierarchy | VERIFIED — sub-phases, template-week reuse | NOT VERIFIED in detail — coach/athlete progress dashboards implied by product category, not directly confirmed here | NOT VERIFIED / not applicable | VERIFIED — coach-authored, athlete-assigned, marketplace programs |
| Strong | VERIFIED (this session's earlier official-source benchmark) — Routine vs. Workout split, company-acknowledged confusion in that flow | NOT VERIFIED (a third-party "full periodization" claim found is NOT corroborated by any official source and contradicts Strong's own documented simple-routine model) | NOT VERIFIED | NOT VERIFIED | PARTIALLY VERIFIED — "folder" organization for exercises mentioned by a third-party source only, not confirmed official | NOT VERIFIED |
| Fitbod | VERIFIED — algorithmically generated per-session, not authored | VERIFIED absent — periodization is real-time/adaptive, not a pre-authored multi-week object | NOT VERIFIED as a real entity | PARTIALLY VERIFIED — "My Plan" configures generation parameters, not inspectable week-by-week progress | NOT VERIFIED / not applicable | NOT VERIFIED |

---

## 27. Buff-Dudes-style 12-week case study (structure only, no copyrighted content reproduced)

Stress-testing the recommended model (`Program → ProgramWeek[] → ProgramSession[] (own snapshot)`)
against a generic 12-week hypertrophy-style plan with: Week 1–4 using one exercise/volume scheme
across 3 ordered sessions/week, Week 5–8 changing exercises and increasing volume, Week 9–11
intensifying further, Week 12 as a deload:

```text
Program: "12-Week Plan" (illustrative name)
├── ProgramWeek 1  → ProgramSession[Day1, Day2, Day3]   (scheme A)
├── ProgramWeek 2  → ProgramSession[Day1, Day2, Day3]   (scheme A, same content — could reuse a saved shape)
├── ProgramWeek 3  → ProgramSession[Day1, Day2, Day3]   (scheme A)
├── ProgramWeek 4  → ProgramSession[Day1, Day2, Day3]   (scheme A)
├── ProgramWeek 5  → ProgramSession[Day1, Day2, Day3]   (scheme B — different exercises/volume, OWN snapshot, not a mutation of Week 1's)
├── ...
└── ProgramWeek 12 → ProgramSession[Day1(deload), Day2(deload), Day3(deload)]
```

This is representable cleanly: each `ProgramWeek`/`ProgramSession` owns its content, weeks that
repeat identical content can be authored once and duplicated (an authoring-tool convenience, not a
schema requirement), and the deload week is simply a `ProgramWeek` like any other with lighter
prescriptions — no special "deload" entity is needed structurally. The model does **not** need
Folder at any point in this case study.

---

## 28. Avoiding premature generic abstractions

No genuinely unavoidable need for `Node`/`Container`/`ScheduleItem`/`GenericTemplate`/
`ProgrammableBlock`/`ArbitraryMetric`/`UniversalTrainingObject` was found anywhere in this
investigation — every structural requirement in §7 and the case study in §27 is representable with
explicit, named fitness-domain concepts (`Program`, `ProgramWeek`, `ProgramSession`, `Routine`,
`Exercise`, `Workout`). Recommend continuing to avoid generic abstraction entirely for this domain.

---

## 29. No implementation performed

Confirmed: no Room entity, Supabase table, migration, repository, or UI change was made as part of
this investigation. This document is the sole output.

---

## 30. Recommended TBDFit model (opinionated)

- **What should TBDFit call one reusable workout template?** `Routine`. No change needed.
- **What should TBDFit call a multi-week structured training plan?** `Program`.
- **Does TBDFit need Folder?** No. Do not build it.
- **If loose grouping is ever useful, should it be called Collection?** Yes, if/when built — but
  defer it, and keep it deliberately semantics-free (no order/progress/schedule implication) if it
  is ever built.
- **Should Week be first-class?** Yes, as an ordered, non-calendar-bound grouping
  (`ProgramWeek`) that supports repetition — not as a rigid 7-day calendar structure.
- **Should Program reference mutable Routines or own planned-session snapshots?** **Own
  snapshots.** This is the single highest-confidence recommendation in this document — a live
  reference would silently corrupt program history the moment a Routine is edited.
- **Should Program Definition and active user Program Instance eventually be separate?**
  Eventually yes, but **not now** — defer until publishing exists; design so today's single-owner
  model doesn't have to be rewritten to add the split later (keep progress-state logically
  separable from definition data now, even in one table).
- **Smallest sensible V1?** `Routine` (made real), `Program`, `ProgramWeek`, `ProgramSession` (each
  owning its own planned prescription), plus an informational `Workout` provenance link. Nothing
  else from this investigation is required to ship a genuinely correct, non-trapping V1.

---

## 31. Proposed conceptual lifecycle

```text
Author a Routine (optional convenience)
        │  (seeds initial content only — not a live link)
        ▼
Author a Program
        │
        ▼
Program owns ProgramWeek[] → ProgramSession[]   (each session's prescription is its own data)
        │
        ▼
User starts today's ProgramSession
        │  (existing real mechanism: WorkoutRepository.startWorkout + addExercise,
        │   seeded FROM the session's snapshot, never live-bound to it afterward)
        ▼
Workout (real, durable, executed)
        │
        ▼
Record result (existing real set-logging mechanism, unchanged)
        │
        ▼
Advance program progress (which session is "current" — separate mutable state,
                           never rewriting the Workout or the session definition)
        │
        ▼
History / analytics (reads completed Workouts + their informational provenance link)
```

This is a direct extension of the plan/intent → execution authority → durable recorded result →
history/analysis lifecycle already established in the multi-client vision doc — not a new
lifecycle shape.

---

## 32. Recommended next step after human review (challenging the stated default hypothesis)

The user's stated default hypothesis was: *research → domain/product decision → minimal real
persistence/domain → frontend wired to real model.* **This investigation partially supports and
partially challenges that ordering.**

**Where it holds**: `Routine`/`Program`/`ProgramWeek`/`ProgramSession`'s core shape (§14's
snapshot-ownership rule especially) is a genuine **durable-data-sensitivity** risk — getting the
reference-vs-snapshot question wrong after real users have real in-progress programs would require
a painful migration (silently-corrupted history, or a breaking schema change). This is a real
argument for deciding the domain model with real rigor *before* building a polished, feature-rich
frontend on top of assumptions that might not hold.

**Where it should be challenged**: this session's own prior work already established (via the
adversarial IA review) that TBDFit's standing prototyping policy — "prototype UI first with
hardcoded content, evaluate with a human, derive backend needs after" — was deliberately chosen
*because* it's cheaper to discover a wrong UX shape before committing backend work than to build
backend first and find the UX doesn't fit it. Nothing in this investigation invalidates that policy
for the **UX/navigation** question (e.g. is a "Plan" screen with "Current Program, Week 4 of 12"
the right shape? — genuinely still open, §20). What's different about `Program` specifically is
that its core *domain semantics* (§14's snapshot rule) are now well-evidenced enough that getting
them wrong isn't a UX-taste question — it's a data-integrity question research has already
answered.

**Recommendation: a thin vertical slice, not either extreme.** Specifically:
1. Treat this document's model recommendation (§30) as settled enough to build the **minimal real
   Room domain** (`Routine`, `Program`, `ProgramWeek`, `ProgramSession` with owned snapshots) —
   this part is low-regret because the structural analysis (§7, §14, §27) is thorough and
   converges strongly.
2. Do **not** simultaneously build a polished, full-featured Program UI (builder, editing,
   published-program flows) — that's still genuinely open UX territory (§20's candidate screen is
   explicitly unvalidated).
3. Wire the *existing* prototype Routine Library UI to the new real backend as the thin slice —
   this validates the domain model against a real, already-built UI surface with minimal new
   frontend risk, rather than building new frontend AND new backend simultaneously.

This is neither "UX prototype first" nor "backend first" in the pure sense the brief offered as
options — it's backend-first for the part evidence already de-risked, reusing already-prototyped UI
rather than building new UI, deferring genuinely new UX (Program authoring/browsing) until that
gets its own dedicated prototype-and-evaluate pass, consistent with how Routine itself was already
handled.

---

## Terminology recommendation summary

Use `Routine`, `Program`, `ProgramWeek`, `ProgramSession`, `Exercise`, `Workout` as the complete
domain vocabulary. Never introduce `Folder`. Reserve `Plan` for the nav/product-area name only. Use
"Training Day" in user-facing copy, `ProgramSession` in the domain model — never bare "Day."

## What to defer

`Block`/`Phase` grouping, Program Definition/Instance split, calendar scheduling, a progression-rule
engine, Collection, pause/restart/skip UX, published/versioned programs — all listed with reasoning
in §22 and §15–17.
