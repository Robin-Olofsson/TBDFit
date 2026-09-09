# TBDFit Multi-Client Product Vision

**STATUS: DESIGN DIRECTION — FOR TEAM REVIEW**

This is a living, explicitly challengeable design direction, not a claim that every detail here is
Accepted. It synthesizes the accumulated product/UX/architecture/device research produced so far —
it does not perform new research, and it does not redesign anything an existing ADR or product
decision already governs. Where this document states a rule, it cites the document that actually
established it; where it states a hypothesis, it says so. Future implementation proposals should be
checked against this document, and this document should be revised whenever new evidence
contradicts it — it is a map of current direction, not a constitution.

**Correction notice**: the web-facing claims in this document (§3 Web, parts of §4/§6/§9/§10) were
originally synthesized from a web research pass that incorrectly weighted TrainingPeaks/Strava/
Garmin/TrainHeroic/Boostcamp as primary evidence instead of the intended Hevy/Strong/Fitbod
comparison set. That research has been redone — see
[`web-product-ux-research.md`](../product/web-product-ux-research.md) §0 — and this document has
been updated accordingly. The corrected picture is **more open and less confident** about web
execution than the original synthesis stated, and explicitly separates a near-term **Web UX
prototype** (no backend, hardcoded content) from a **production-backed Web client** (not currently
justified) — a distinction the original version of this document did not make.

Labels reused throughout, consistent with every prior research/design document in this project:
**FACT** (verified from code or cited research), **INFERENCE** (reasoned conclusion), **CURRENT
DIRECTION** (already established by an existing ADR/PD, restated here for synthesis, not
re-decided), **STRONG HYPOTHESIS** (well-evidenced but not yet product-approved), **OPEN QUESTION**
(genuinely unresolved).

---

## 1. Product thesis

**FACT, current real implementation**: TBDFit today is a native Android phone app and a native Wear
OS app sharing one Supabase backend project, with a local-first durable strength-workout domain
(`Workout`/`Exercise`/`WorkoutExercise`/`WorkoutSet`) implemented on Phone only. No Web client, no
Apple client, no Routine/Program model, no social capability, and no cross-device replication of
workout data exist yet anywhere in the codebase (verified: `apple/` does not exist;
`wear/src/main/kotlin/com/tbdfit/wear/` contains only the disposable `LocalRecord` replication proof,
no workout domain code at all).

**INFERENCE, derived from the accumulated research** (not asserted before it): TBDFit is one
training product, expressed as several independent native clients, that share a product model and a
user identity but not a UI or a framework. The lifecycle the product exists to support is:

```
PLAN / INTENT → EXECUTE → RECORD → REVIEW → IMPROVE → (optionally) SHARE / DISCOVER
```

Every research pass so far has, independently, found the same underlying shape: competitors that
serve this lifecycle well split it across devices rather than building one screen set that tries to
do all of it everywhere (see §4 for the citations). TBDFit's own architecture already anticipated
this split in direction — [PD-001](../product/decisions.md#pd-001-independent-watch-workout-execution)
and [PD-002](../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch) settled
*execution* authority; this document is the first synthesis of what the *rest* of the lifecycle
(plan, review, share) looks like once a third client type (Web) is considered, and it finds the same
device-specialization pattern holds there too.

This is not a claim that TBDFit must build all of Web/Phone/Watch/Apple immediately, nor that every
capability must eventually exist on every client — see §5's explicit "not currently planned" column
and §10's roadmap for why several of the capabilities this document names are deliberately not being
built yet.

---

## 2. Same product model ≠ same UI everywhere

**CURRENT DIRECTION**, unchanged, restated for synthesis:

```
SAME PRODUCT MODEL           ≠     SAME UI EVERYWHERE
shared product behavior/
invariants                   ≠     shared framework architecture
```

[ADR-0001](adr/0001-native-client-architecture.md) already decided this for Apple vs. Android: full
native implementation per ecosystem, no shared cross-platform UI/core technology, coordination
through "shared specification (documented invariants, state transitions, test scenarios), not
shared code." Nothing in the accumulated Web/Phone/Watch research gives any reason to revisit that —
if anything it strengthens it: every competitor examined across three separate research passes
(phone UX research, web UX research, and this synthesis) uses a *different* navigation paradigm per
platform (bottom tabs on mobile, a left sidebar on web, a distinct minimal execution UI on watch —
[`frontend-product-ux-research.md`](../product/frontend-product-ux-research.md) §3,
[`web-product-ux-research.md`](../product/web-product-ux-research.md) §5 principle 2), not a
responsive layout of one shared design. A future Web client, if and when built, is a separate native
web implementation sharing TBDFit's product model and identity — not a shared-code frontend layered
over the same client logic as Phone/Watch, and not a rendering of the same component tree in a
browser shell.

What *is* shared across clients, per the existing architecture: the underlying Supabase/PostgreSQL
data model ([ADR-0004](adr/0004-initial-backend-platform-and-migration-strategy.md)), the ownership
and visibility *rules* (§7 below), and the behavioral invariants PD-001/PD-002 already established
for execution. None of that requires shared UI code to hold.

---

## 3. Client responsibilities

Four responsibility levels, used consistently across §3 and the capability matrix in §4:

- **PRIMARY** — this client's core job; the UI is purpose-built for it.
- **SECONDARY** — a real, supported capability, but not this client's distinguishing strength.
- **MINIMAL/GLANCEABLE** — a reduced, read-mostly or quick-action presence, not a full surface.
- **NOT PLANNED** — no current direction to build this on this client at all.

### Web

**STRONG HYPOTHESIS overall — no Web client exists.** No *production* Web client is currently
justified (no `Routine`/`Program` model or completed-workout history exists on any client yet — see
[`web-information-architecture.md`](../product/web-information-architecture.md) §5), but a **Web UX
prototype** (hardcoded content, no backend, evaluated the same way the Phone-side prototype is) is a
plausibly worthwhile near-term step, not something to defer — see §10's roadmap. The responsibilities
below describe Web's *eventual* job if and when it is built, per the corrected
[`web-product-ux-research.md`](../product/web-product-ux-research.md) §6-7 (Hevy-only, medium-confidence
evidence — not a 6-product consensus) — they are not a roadmap commitment.

| Job | Level |
|---|---|
| Routine planning & building | PRIMARY (eventual) — evidenced by Hevy's web surface specifically (medium confidence); NOT evidenced by Strong or Fitbod |
| Exercise library management | OPEN — not confirmed on any of the three primary comparators' web surfaces (`web-product-ux-research.md` §3-5) |
| Multi-week/periodized calendar view | OPEN, likely NOT PLANNED near-term — this was previously (incorrectly) treated as PRIMARY based on TrainingPeaks/TrainHeroic (now supplementary-tier evidence, not primary); no evidence any of Hevy/Strong/Fitbod support this on web |
| History review (list/detail) | PRIMARY (eventual) — evidenced by Hevy specifically (medium confidence) |
| Analytics/charts review (bulk) | OPEN, not PRIMARY — Hevy's own web analytics status could not be confirmed either way in the corrected research (`web-product-ux-research.md` §3); this was previously overstated |
| Coach-facing program authoring at scale | SECONDARY (eventual, additive, FUTURE) — same "additive to the ordinary product" pattern as athlete/creator profiles; now understood to be a *supplementary*-tier precedent (TrainHeroic, Hevy Coach — a separate coach product from consumer Hevy), not primary evidence |
| Account/profile/settings | SECONDARY (eventual) — evidenced across all three (Fitbod and Strong for account/subscription; Hevy for profile editing) |
| **Active workout execution / live set logging** | **NOT CURRENTLY PLANNED — but OPEN, not foreclosed.** Corrected finding: `NOT VERIFIED`/inconclusive across all three primary comparators (`web-product-ux-research.md` §11), not a proven-absent hard rule as the prior synthesis stated. TBDFit's own reason for caution is inference about training-floor context, not competitor precedent — see the research document's §11 for why this must stay revisable. |
| Social feed/discovery specific to Web | NOT PLANNED (would only follow an already-successful phone-side social feature) |

### Phone

**CURRENT DIRECTION for what's implemented; STRONG HYPOTHESIS for what's next**, per
[`product-information-architecture.md`](../product/product-information-architecture.md) (already
adversarially reviewed once in this project).

| Job | Level |
|---|---|
| Start/execute a workout (attach exercises, log sets) | PRIMARY — **IMPLEMENTED** (real UI: `ActiveWorkoutScreen`, backed by `WorkoutRepository`/`WorkoutDao`/`WorkoutSetDao`, `LocalAccount`-scoped, offline-tolerant via `SessionUnavailable` access to an already-active local workout) |
| Complete a workout | PRIMARY (eventual) — **TEST VERIFIED at the repository/DAO level only** (`WorkoutRepository.completeWorkout`/`WorkoutDao.completeIfActive` exist and are unit-tested), **no UI path exists** (verified: no screen or button calls `completeWorkout` anywhere in the codebase). Do not read this as "Complete Workout is implemented" — the product-visible capability is not built yet; only its persistence primitive is. |
| Routine browsing/selection (execution entry point) | PRIMARY (eventual) — **PROTOTYPED at most in intent, not yet built**: no screen, no backend (`product-information-architecture.md` §5) |
| Exercise picker / custom exercise creation | PRIMARY — **FACT, implemented** |
| History & Workout Detail (read) | PRIMARY (eventual) — the concrete next MVP gap; no backend or screen exists yet |
| Routine building/editing | SECONDARY (Web, if built, is the better editing surface for anything beyond simple selection — `web-product-ux-research.md` §4's large-screen-planning finding) |
| Progress/PRs | SECONDARY, nested not top-level (`product-information-architecture.md` §1, adversarial-review correction) |
| Profile / social identity | SECONDARY, minimal at MVP (`ProfileSummaryHeader` already implemented; a full profile destination is POST-MVP) |
| Social feed / discovery | MINIMAL/GLANCEABLE at most, folded into Home content, never a peer-weight destination (`frontend-product-ux-research.md` §9 principle 3, with its own adversarial-review caveat that this is a placement finding, not a permanent prohibition) |
| Multi-week/periodized planning UI | NOT PLANNED on Phone specifically — the large-screen case for this belongs to Web once it exists (`web-product-ux-research.md` §4) |

### Watch

**CURRENT DIRECTION in stated intent (PD-001); NOT YET IMPLEMENTED in fact** — this distinction
matters and should not be blurred: PD-001 is itself still `PROPOSED — FOR TEAM REVIEW`, and no
workout-domain code exists on Wear at all yet (verified: only the disposable `LocalRecord`
replication proof exists under `wear/`).

| Job | Level |
|---|---|
| Start/resume a workout independently | PRIMARY (intended, per PD-001) — not yet implemented |
| Current exercise / current set entry (reps, load) | PRIMARY (intended) — not yet implemented |
| Mark set/workout complete | PRIMARY (intended) — not yet implemented |
| Rest timer, quick controls | SECONDARY (intended, later — `strength-workout-first-slice-design.md`'s own "Rest timer: LATER" resolution) |
| Sensors (heart rate, energy, later HRV/SpO2/etc.) | SECONDARY (intended) — see `physiological-capabilities-research.md`'s full capability matrix and its own explicit caution that capability and reliability are separate qualities |
| Browsing/editing Routines | NOT PLANNED — every competitor examined treats the watch as execution-only, never a planning surface (`frontend-product-ux-research.md` §7) |
| History, analytics, social, profile, discovery | NOT PLANNED — same evidence |

### A note on Apple

Out of this document's direct evidence base (no Web/Phone/Watch research pass investigated Apple
specifically), but for completeness: [ADR-0001](adr/0001-native-client-architecture.md) already
commits iOS/watchOS to the same native, no-shared-code direction as Android/Wear OS, and
[ADR-0003](adr/0003-apple-development-and-verification-strategy.md) governs *when* that work can be
verified given no dedicated Mac. Zero Apple code exists yet (`apple/` does not exist in the
repository). Nothing in this document changes that — the responsibility split above should be read
as applying conceptually to "the phone client" and "the watch client" regardless of ecosystem, per
ADR-0001's own framing.

---

## 4. Cross-client capability matrix

Responsibility levels as defined in §3. `?` marks a cell this research genuinely did not resolve —
stated as uncertain rather than guessed.

| Capability | Web | Phone | Watch |
|---|---|---|---|
| Home/dashboard | OPEN — no evidence on any of Hevy/Strong/Fitbod's web surfaces confirms dashboard content depth (`web-product-ux-research.md` §3) | PRIMARY (thin at MVP, distinct from Workout — `product-information-architecture.md` §1 adversarial correction) | NOT PLANNED |
| Exercise library (browse) | OPEN — not confirmed on any primary comparator's web surface | PRIMARY (IMPLEMENTED — picker) | MINIMAL/GLANCEABLE (whatever's needed mid-workout only) |
| Routine planning/building | PRIMARY (eventual) — evidenced by Hevy specifically, medium confidence; not evidenced by Strong or Fitbod | SECONDARY (selection only; PROTOTYPED-in-intent only, not built) | NOT PLANNED |
| Program (multi-week) planning | OPEN, likely NOT PLANNED near-term — corrected finding: not evidenced by any of the three primary comparators (`web-product-ux-research.md` §8); the prior "PRIMARY" claim here came from now-supplementary TrainingPeaks/TrainHeroic evidence | NOT PLANNED | NOT PLANNED |
| Start workout | Corrected: `NOT VERIFIED`/inconclusive whether any comparator does this on web at all — **not currently planned for TBDFit, but not proven impossible** (`web-product-ux-research.md` §11) | PRIMARY (IMPLEMENTED) | PRIMARY (intended, not yet built) |
| Active workout execution / set logging | **NOT CURRENTLY PLANNED — open, not foreclosed.** Corrected from a prior "NOT PLANNED — ever" claim that rested on the wrong evidence base; see `web-product-ux-research.md` §11. | PRIMARY (IMPLEMENTED) | PRIMARY (intended, not yet built) |
| Workout completion | NOT PLANNED | PRIMARY (eventual) — **TEST VERIFIED at DAO/repository level only** (`completeIfActive`/`completeWorkout`); **no UI exists** | PRIMARY (intended, not yet built) |
| Sensors | NOT PLANNED | MINIMAL (whatever a paired/companion sensor exposes to the phone, uncertain — `?`) | PRIMARY (intended, per `physiological-capabilities-research.md`) |
| History (list/browse) | PRIMARY (eventual) — evidenced by Hevy specifically, medium confidence | PRIMARY (eventual; MVP gap today, PLANNED not yet built) | NOT PLANNED |
| Workout detail (read) | SECONDARY (eventual) — evidenced by Hevy specifically | PRIMARY (eventual; MVP gap today, PLANNED not yet built) | NOT PLANNED |
| Exercise progress | OPEN — not confirmed on any primary comparator's web surface | SECONDARY (nested, not top-level) | NOT PLANNED |
| Analytics/trends | OPEN, not PRIMARY — corrected finding: Hevy's own web analytics status could not be confirmed either way (`web-product-ux-research.md` §3/§9); this was previously overstated from supplementary-tier Strava/Garmin evidence | SECONDARY | NOT PLANNED |
| Profile (own) | SECONDARY (eventual) — evidenced by Hevy specifically | SECONDARY (minimal at MVP) | NOT PLANNED |
| Social feed | NOT PLANNED on Web unless phone-side social succeeds first (viewing feed content is evidenced on Hevy's web surface specifically, but TBDFit has no social feature on any client yet) | MINIMAL/GLANCEABLE, folded into Home, never a peer-weight tab | NOT PLANNED |
| Discovery (other users/creators) | NOT PLANNED (FUTURE at best) | NOT PLANNED at MVP (FUTURE) | NOT PLANNED |
| Published workouts/routines | NOT PLANNED at MVP (FUTURE, `product-information-architecture.md` Journey D/E) | NOT PLANNED at MVP (FUTURE) | NOT PLANNED |
| Settings/account | SECONDARY (eventual) — evidenced across all three comparators | PRIMARY (IMPLEMENTED: sign-out; MVP gap for the rest) | MINIMAL/GLANCEABLE at most |

`?` cell called out explicitly: whether a phone-paired external sensor (chest strap, etc.) is ever a
TBDFit concern independent of the watch's own built-in sensors was not investigated by any research
pass to date — genuinely uncertain, not resolved here.

---

## 5. Training lifecycle and where clients participate

```
PLAN / INTENT  →  EXECUTION AUTHORITY  →  DURABLE RECORDED RESULT  →  REPLICATION  →  HISTORY / ANALYSIS
```

**CURRENT DIRECTION, cited not re-derived:**

- **No permanent Phone execution authority.**
  [PD-001](../product/decisions.md#pd-001-independent-watch-workout-execution): "The phone cannot be
  assumed to always be the authoritative writer." Whichever device the user is actually using may
  execute.
- **Watch can be first-class.** PD-001: "The watch is a first-class client with its own durable
  workout state. It is not modeled as a remote control, display, or temporary mirror of phone-owned
  workout state."
- **Sync is not durability.** [`strength-workout-first-slice-design.md`](strength-workout-first-slice-design.md)'s
  DURABILITY MODEL: "Backend sync is explicitly not durability... A workout is fully 'real' the
  moment it is committed to local Room, regardless of connectivity." This holds per-device — each
  executing device (Phone or Watch) has its own durable local persistence
  ([ADR-0004](adr/0004-initial-backend-platform-and-migration-strategy.md)'s "Central database
  clarification").
- **Execution remains locally durable.** [PD-002](../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch)
  generalizes this to both device types uniformly.

**Where each client participates**, synthesizing the above with §3:

- **PLAN/INTENT**: Web (eventual, primary) and Phone (secondary, selection-only) — never Watch,
  never live-authoritative for what "the plan" says once execution starts (a `sourceRoutineId` is
  informational provenance only, per `strength-workout-first-slice-design.md`'s Plan vs. Execution
  section — never a live dependency).
- **EXECUTION AUTHORITY**: Phone or Watch, whichever device the user is actually using, per PD-001 —
  never Web (§3, §6).
- **DURABLE RECORDED RESULT**: committed locally on whichever device executed, before any network
  involvement (`strength-workout-first-slice-design.md` DURABILITY MODEL).
- **REPLICATION**: the mechanism itself remains explicitly undecided by every document that touches
  it — PD-001's own "Explicitly not decided by PD-001" list names "sync transport" and "phone/watch
  ownership protocol" directly; this document does not narrow that.
- **HISTORY/ANALYSIS**: Phone (near-term, once a Workout Summary/history screen exists) and Web
  (eventual, bulk/analytics-oriented) — read-only in both cases, over whichever device's data has
  been replicated to a shared source of truth.

---

## 6. Multi-client journeys

Conceptual only — none of these are backend implementation commitments, and several depend on
mechanisms (sync, a Routine model, backend replication of Workout data) that do not exist yet.

### Journey 1 — Planning → execution → review (aspirational; depends on undecided sync + an unbuilt Web client; the "Web builds Routine" leg rests on Hevy-only, medium-confidence evidence per the corrected research — not a validated pattern)

```
Web builds Routine (once Web exists — currently NOT PLANNED to build, see §3)
→ sync (mechanism undecided — PD-001)
→ Phone/Watch accesses the plan (Routine Library — prototype-only today, no backend)
→ one device executes (Phone: implemented; Watch: intended, not built)
→ result persists locally (implemented, on whichever device executes)
→ replication (mechanism undecided)
→ Web/Phone review (Phone: no history screen yet; Web: not planned yet)
```

**Honesty check**: every arrow in this journey after "Web builds Routine" currently depends on
something that does not exist — a Routine domain model, a sync mechanism, and (for the Web leg) a
Web client at all. This journey is the long-term shape the research supports, not a near-term
sequence. It should not be read as implying any of these pieces are imminent.

### Journey 2 — Phone-first workout

```
Phone: Home/Workout → Start → execute → complete → (once built) Summary → History
```

Real today, minus Summary and History (`product-information-architecture.md` §3 Journey A,
`product-information-architecture.md` §5's MVP gap list).

### Journey 3 — Watch-first workout

```
Watch: Start (independently, no phone nearby) → execute → complete → replication later
```

Directionally intended per PD-001; **zero implementation exists yet** — this is the single largest
gap between "current direction" and "current reality" in the whole multi-client picture. See §10.

### Journey 4 — Social copy

```
Creator publishes → another user discovers → Copy/Save
→ recipient owns an independent, newly-created object (never a shared reference)
→ recipient executes their own copy
```

Squarely FUTURE (`product-information-architecture.md` Journey D/E, §5's FUTURE bucket). Included
here only because it is the clearest illustration of §7's ownership rule, not because it is
scheduled.

---

## 7. Ownership and visibility direction

**CURRENT DIRECTION, already implemented on Phone:**

```
LocalAccount  →  local private ownership (who this device's data belongs to)
                 ≠
              social visibility (who else may ever see it)
```

`LocalAccount` exists for a narrow, mechanical reason: Room (on-device SQLite) cannot declare a
foreign key against a remote `auth.users` row living in Supabase, so a local identity root is needed
to give `Workout`/`Exercise` ownership real referential integrity rather than a free-form string.
**This is not a decision about who may see data — it never was.** The visibility question (§4 of
`frontend-product-ux-research.md`, §8: recommended `PRIVATE / FOLLOWERS / PUBLIC`) is a property of
a piece of *content*, orthogonal to which device or account durably stores it.

**STRONG HYPOTHESIS, flagged not resolved**: `LocalAccount` likely has **no Web equivalent** — Web
is not a durable-execution client, so it has no local Room database whose foreign-key integrity
needs protecting the way Phone's does. Web can plausibly be backend-session-authoritative directly
(`web-information-architecture.md` §4). Not decided; belongs to a future Web-specific ADR if Web is
ever built.

**CURRENT DIRECTION, three distinct concepts that must never collapse into one**
(`product-information-architecture.md` §3 Journey D, restated because it recurs across every social
journey in this document):

```
historical Workout  ≠  PublishedWorkout  ≠  Routine/Program copied by another user
```

A private historical `Workout` is a plain owned row. A `PublishedWorkout` is the *same* row made
visible to others via a visibility property — not a copy. A Routine/Program *copied* by another user
is a **new, independently-owned object** for the copier — never a shared mutable reference to the
original. This is not merely TBDFit's own preference: Hevy's own "Save as Routine" vs. "Copy
Workout" verbs are real vendor precedent for exactly this distinction
(`frontend-product-ux-research.md` §3, "Routine/program sharing mechanics").

---

## 8. UX principles genuinely supported by current direction

Evaluated against the evidence, not accepted from any prior document's example list wholesale:

1. **Execution must remain fast, on whichever device executes.** Validated across every logging
   competitor examined in every research pass to date.
2. **Web should exploit large-screen planning/analysis advantages — but only once there is something
   real to plan or analyze.** `web-product-ux-research.md` §5 principle 4 states this as a
   *structural*, not stylistic, constraint — it is the direct reason Web is currently recommended not
   to be built at all (§10).
3. **Watch is execution-first, never a Phone remote — and Phone is not a Watch remote either.**
   `product-information-architecture.md` §4 (post-adversarial-review): the correct framing is
   *primary responsibility* separated from *shared capability* — both devices may legitimately be
   able to start/resume/complete a workout, with neither holding permanent authority, which is a
   stronger and more accurate statement than "zero overlap."
4. **Phone remains a complete daily-use client** even before Web or a mature Watch implementation
   exist — it currently carries execution, and will carry planning-selection and history/review
   duties too, until/unless Web takes over the planning/review-at-scale job.
5. **Social supports training, it does not dominate it.** No competitor examined across either
   research pass gives social a peer-weight tab (`frontend-product-ux-research.md` §9 principle 3) —
   but this is a placement/sequencing finding, not a permanent prohibition on a future dedicated
   social surface if usage ever justifies it.
6. **Planning objects and recorded results are different concepts, structurally.** A `Routine` seeds
   a `Workout`; it never becomes the historical record, and the historical record never depends on
   the plan continuing to exist unchanged (`strength-workout-first-slice-design.md`'s Plan vs.
   Execution section).
7. **Copied/shared content never implies shared mutable ownership.** §7 above; validated by real
   competitor precedent, not just TBDFit's own architectural taste.
8. **History must represent what actually happened.** `strength-workout-first-slice-design.md`'s
   `completedAt`/`lastModifiedAt` split exists specifically so a later correction can never quietly
   rewrite when a session is considered to have occurred.

---

## 9. Current direction / strong hypotheses / open questions

### Current direction (already established elsewhere; restated, not re-decided)

- Native, per-ecosystem clients; no shared cross-platform UI/core ([ADR-0001](adr/0001-native-client-architecture.md)).
- No permanent Phone execution authority; Watch can be first-class
  ([PD-001](../product/decisions.md#pd-001-independent-watch-workout-execution)).
- Local-first execution, on either device type
  ([PD-002](../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch)).
- Sync is not durability (`strength-workout-first-slice-design.md`).
- `LocalAccount` ownership ≠ social visibility (§7).
- Supabase as initial backend platform behind purpose-scoped capability boundaries
  ([ADR-0004](adr/0004-initial-backend-platform-and-migration-strategy.md)).

All of the above except the last bullet are `PROPOSED — FOR TEAM REVIEW`, not yet `Accepted` (only
ADR-0004 currently carries `Accepted` status among the cited architecture documents) — restating a
direction here does not promote it.

### Strong hypotheses (well-evidenced, not yet product-approved)

- `Home │ Workout │ You` (3-tab) is the right near-term Phone navigation shape
  (`product-information-architecture.md` §1, post-adversarial-review).
- Routine Library matters more for early user value than a standalone Profile screen at this product
  stage (`product-information-architecture.md` §5 adversarial-review note).
- Web's eventual shape, if built, is a Plan/Review/light-social client resembling Hevy's own web
  surface (`web-information-architecture.md` §1 Option B) — a single-sourced, medium-confidence
  hypothesis, not a validated pattern; a production client is not justified yet, but a **UX
  prototype** of this shape is a reasonable near-term step (§10).
- Follow-only (no mutual "Friends") is the right social-relationship starting point, precisely
  because it is cheap to extend later, not because mutuality is disproven as a future need
  (`frontend-product-ux-research.md` §6, adversarial-review framing).
- `PRIVATE / FOLLOWERS / PUBLIC` is sufficient for known visibility use cases
  (`frontend-product-ux-research.md` §8).

### Open questions

- **Full Web workout execution** — genuinely open, not resolved. The corrected research
  (`web-product-ux-research.md` §11) found this `NOT VERIFIED`/inconclusive across all three primary
  comparators (Hevy, Strong, Fitbod), not proven-absent. TBDFit's current stance (not currently
  planned) rests on inference about training-floor UX, not competitor precedent, and should be
  revisited on its own evidence rather than treated as settled.
- Long-term Phone bottom-navigation shape beyond the current 3-tab hypothesis — could still change
  (`product-information-architecture.md` Open Decision #1).
- Whether Progress is ever promoted to top-level Phone navigation, and what usage threshold would
  justify it (`product-information-architecture.md` Open Decision #7).
- How much program/routine planning belongs on Phone vs. Web long-term, once both exist
  (`web-information-architecture.md` Open Decision #2).
- Whether a Community/social destination is ever a first-class top-level surface on any client
  (`product-information-architecture.md` Open Decision #5).
- Follow-only vs. a future mutual-relationship concept — not needed now, not proven unnecessary
  forever (`frontend-product-ux-research.md` §6).
- Profile-level privacy (an account-wide public/private switch) vs. per-item visibility — whether
  TBDFit needs both (`product-information-architecture.md` Open Decision #6).
- Routine/Program model depth — whether multi-week Program is ever built at all
  (`web-information-architecture.md` Open Decision #5, mirroring `product-information-architecture.md`
  Open Decision #5).
- Desktop-specific interactions beyond the sidebar shape (keyboard shortcuts, bulk-edit UX,
  drag/reorder specifics) — not investigated by any research pass yet.
- **The cross-device sync/ownership mechanism itself** — still exactly as undecided as
  [PD-001](../product/decisions.md#pd-001-independent-watch-workout-execution) states; every journey
  in §6 that crosses a device boundary depends on it.
- **Whether `LocalAccount` has a genuine Web equivalent** — flagged, not resolved (§7).

---

## 10. Near-term roadmap (capability milestones, not a feature backlog)

Derived from the actual current technical state and this session's own research findings — not
accepted from any prior document's example sequence wholesale. **Corrected from the prior version**:
production Web remains conditional and late, but a Web UX prototype is pulled forward alongside the
Phone prototype (step 1), since it needs no backend and this project has already chosen
"prototype UI first → human evaluation → derive backend needs later" as its general policy — the
prior version's "Web appears deliberately late and conditionally... defer entirely" framing collapsed
UX exploration and production commitment into one decision; they are kept separate here.

1. **First clickable Phone prototype, and (if capacity allows) a Web UX prototype of the
   Plan/History/Profile shape** — validate the revised 3-tab Phone structure and the
   Routine-Library-before-Profile reprioritization with prototype-only (hardcoded/local Compose
   state) Home dashboard, Routine Library/Detail, and History content, alongside the already-real
   Active Workout/Exercise Picker path. The Web prototype (if pursued) tests the Hevy-inspired
   Plan/History/Profile shape (`web-information-architecture.md` §1 Option B) the same way — hardcoded
   content, no backend — since that hypothesis is currently single-sourced and worth validating with
   real users before any production commitment. Neither prototype implies a backend commitment.
2. **Complete Workout** — a real Workout Summary screen and the domain read work behind it; the one
   concrete MVP gap every research pass in this project agrees on (`product-information-architecture.md`
   §5, `web-product-ux-research.md` §14 — the same missing piece both phone and web research
   separately flagged as more load-bearing than any navigation question). **Currently: TEST VERIFIED
   at the DAO/repository level only (`completeIfActive`/`completeWorkout`); no UI exists yet** — this
   step is what would change that.
3. **History / result review on Phone** — once Complete Workout exists, there is something real to
   list and inspect.
4. **Real Routine vertical slice on Phone** — turn the prototype-only Routine screens into a real
   domain model and backend-free-but-durable feature, informed by whatever the clickable prototype(s)
   revealed.
5. **Watch execution vertical slice** — the largest current gap between direction and reality (§6
   Journey 3): PD-001 is proposed, nothing is built (verified: zero workout-domain code exists under
   `wear/`). This is independent of Web and does not need to wait for it.
6. **Cross-device replication mechanism** — the still-undecided sync/ownership protocol PD-001 and
   every cross-device journey in this document depend on; needed before Journey 1 (planning→
   execution→review) or Journey 3's "replication later" step can be real.
7. **Backend replication of completed Workouts** — needed independently of Web, since it's also what
   makes Phone-side history durable across devices/reinstalls, and is the real blocker
   `web-product-ux-research.md` §14 identified for Journey W2.
8. **Production Web, if the prototype validates it** — per `web-information-architecture.md` §5's own
   stated distinction: a *production* client needs a real Routine/Program model and/or enough
   Phone-side completed-workout history to be worth reviewing elsewhere; the UX prototype from step 1
   does not need either.
9. **Progress/analysis** — largely derivable read-time once history exists on Phone (and later Web);
   not blocking anything else.
10. **Social / creator / marketplace capabilities** — last, and explicitly contingent on the ordinary
    logging product succeeding first, per the segmented-rollout pattern observed at Boostcamp
    specifically (`frontend-product-ux-research.md` §9 principle 5) and both phone and web IA
    documents' own Open Decision #5 (whether an athlete/creator ecosystem is a TBDFit goal at all).

Steps 1-4 involve no new architectural decision beyond what's already proposed. Steps 5-7 are where
a genuinely new ADR (the sync/ownership mechanism) becomes necessary — not attempted here.

---

## 11. ADR assessment

**Evaluated: is `ADR-005 — Multi-Client Responsibility Strategy` justified now?**

**Yes — created, see `adr/0005-multi-client-responsibility-strategy.md`.** Reasoning:

[ADR-0001](adr/0001-native-client-architecture.md) decided *implementation technology* per
ecosystem (native Swift/Kotlin, no shared cross-platform code) but explicitly left "whether
additional client surfaces... will exist, and what architecture they would use if so" as
out-of-scope, and named a possible future desktop client only as an example, not a decision about
*how* it would relate to Phone/Watch responsibilities. That gap is exactly what this document's
research fills: three independent research passes (phone UX, phone IA, web UX, web IA — four
documents total) now converge on the same evidence-backed pattern — device-specialized
responsibility, not feature parity, with execution authority never fixed to one device and never
extended to Web at all. That is a genuine, durable architectural stance, distinct from ADR-0001's
technology choice, and it is durable *regardless* of whether Web is built soon or ever (it governs
how any future client, including a revisited Web, would be scoped). The countervailing
consideration — that Web is being deferred entirely, so there may be "nothing to decide yet" — does
not actually hold: the decision is not "what does Web look like," it is "how does TBDFit allocate
responsibility across clients in general," which already applies to the real Phone/Watch
relationship today, independent of Web's timing.

The ADR does not encode navigation, screens, or the sync mechanism itself (both remain in
product/IA documents and future work respectively) — it states the responsibility-allocation
principle only, which is the correct scope for an ADR per this project's own ADR practice (compare
ADR-0001's own scope discipline).

---

## 12. Traceability index

For a reviewer moving from a claim in this document back to its evidence:

| This document's claim | Source |
|---|---|
| Native, no shared cross-platform UI | [ADR-0001](adr/0001-native-client-architecture.md) |
| Monorepo, backend-as-shared-boundary reasoning | [ADR-0002](adr/0002-monorepo-repository-strategy.md) |
| Apple verification constraints | [ADR-0003](adr/0003-apple-development-and-verification-strategy.md) |
| Supabase capability-boundary architecture | [ADR-0004](adr/0004-initial-backend-platform-and-migration-strategy.md) |
| No permanent Phone execution authority; Watch first-class | [PD-001](../product/decisions.md#pd-001-independent-watch-workout-execution) |
| Local-first execution generalized to Phone | [PD-002](../product/decisions.md#pd-002-local-first-workout-execution-phone-and-watch) |
| Workout domain scope deferred (strength vs. cardio) | [PD-003](../product/decisions.md#pd-003-workout-domain-scope--deferred) |
| Strength-domain data model, durability, plan-vs-execution | [`strength-workout-first-slice-design.md`](strength-workout-first-slice-design.md) |
| Sensor/HealthKit/Health Connect capability differences | [`physiological-capabilities-research.md`](physiological-capabilities-research.md) |
| Hevy/Strong/Fitbod logging-loop and reliability findings | [`competitor-benchmark-hevy-strong-fitbod.md`](../product/competitor-benchmark-hevy-strong-fitbod.md) |
| Phone navigation/social/watch-split research | [`frontend-product-ux-research.md`](../product/frontend-product-ux-research.md) |
| Phone IA, journeys, MVP scope, adversarial corrections | [`product-information-architecture.md`](../product/product-information-architecture.md) |
| Web's evidenced (Hevy-only) plan/review/social pattern; execution finding corrected to inconclusive | [`web-product-ux-research.md`](../product/web-product-ux-research.md) |
| Web IA options, production-vs-prototype distinction | [`web-information-architecture.md`](../product/web-information-architecture.md) |
| Multi-client responsibility principle (new) | [ADR-0005](adr/0005-multi-client-responsibility-strategy.md) |

---

## Status

**STATUS: DESIGN DIRECTION — FOR TEAM REVIEW.** This document does not promote any ADR or product
decision to Accepted, and does not itself require approval to keep existing — it is meant to be
read, challenged, and revised as implementation and further research proceed. Where it conflicts
with a future decision, the decision wins and this document should be updated to match, not treated
as authoritative over it.
