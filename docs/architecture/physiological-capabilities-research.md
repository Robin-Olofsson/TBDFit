# Physiological Capability Research — Apple Watch & Wear OS

**STATUS: RESEARCH — FOR TEAM REVIEW**

This is a research document, not an ADR. It records investigation findings and candidate
principles for the two human developers to review. Nothing in this document is an accepted
architecture decision. Where a candidate principle is listed below, it is explicitly marked
`CANDIDATE — NOT YET ACCEPTED` and requires its own future ADR (and, where relevant, an update to
[`docs/product/decisions.md`](../product/decisions.md)) before it governs implementation.

This document consolidates two research passes:

1. An initial capability investigation across Apple Watch (HealthKit/watchOS/Core Motion) and
   Wear OS (Health Services/Health Connect/Android sensors).
2. A follow-up errata pass that corrected several claims in the initial pass against current
   official Apple and Android documentation.

**Where the two passes conflict, the errata pass is authoritative.** This document reflects only
the corrected findings — it does not preserve the superseded original wording. The specific
corrections carried forward are called out in the relevant sections below.

Every finding is labeled:

- **FACT** — verified against current official platform documentation or codebase evidence.
- **INFERENCE** — a reasoned conclusion drawn from facts, not itself directly documented.
- **RECOMMENDATION** — architectural judgment, not a decision.
- **OPEN DECISION** — requires developer/product input; not resolved by this research.

---

## Purpose

TBDFit's product intent is serious, structured workout logging enriched with the best
physiological data a user's actual hardware and platform can legitimately provide — not a
lowest-common-denominator "works everywhere identically" model, and not a platform-specific
rebuild that ignores what phone/watch execution and native platform APIs already do well.

This research exists to answer, before any design work starts: what can each platform actually
tell TBDFit, under what conditions, with what reliability, and where do the two platforms
genuinely differ in ways an abstraction must not paper over.

---

## Core research findings

A concise comparison, not a field-by-field reproduction of the underlying research notes.

| Capability area | Apple Watch (HealthKit) | Wear OS (Health Services / Health Connect) |
|---|---|---|
| Live heart rate during workout | FACT: delivered via `HKLiveWorkoutBuilder`; no documented fixed callback interval | FACT: delivered via `ExerciseClient`; official guidance says "most" types deliver ~1s but batching/backoff is possible and expected |
| Calories, distance (live) | FACT: delivered live via `HKLiveWorkoutDataSource` | FACT: delivered live via `ExerciseClient`, but **not** part of any guaranteed device baseline — must be runtime-checked |
| Step count, heart rate (baseline) | No equivalent documented "guaranteed minimum" concept on Apple | FACT: the only two data types Google's compatibility guidance requires across *all* Wear OS devices |
| GPS/route, elevation, pace, cadence | FACT: live via `HKWorkoutRoute`/route data; cardio-workout-type dependent | FACT: live via `ExerciseClient`; explicitly optional/device-dependent, no baseline guarantee |
| SpO2 | FACT: type exists, live-during-workout behavior unconfirmed/spot-check-like | FACT: type exists in Health Connect; real-world availability gated heavily by OEM (e.g. Samsung's partner-only Privileged Health SDK) |
| ECG | FACT (corrected): read-only access to classification **and** full underlying voltage-measurement time series of a sample already recorded by Apple's own Watch ECG app; no third-party live/raw electrode recording exists | FACT: no standard Google API exposes ECG at all; Samsung-only via a gated, partner-program SDK |
| Skin/wrist temperature | FACT: sleep-context only (`appleSleepingWristTemperature`), never workout-time, by platform design | FACT: standard Health Connect type exists; population is OEM-write-dependent |
| Raw accelerometer/gyroscope | FACT: Core Motion exposes raw signal | FACT: `SensorManager` exposes raw signal, no special health permission |
| Runtime capability discovery | FACT (corrected): **no** per-type hardware-capability query API exists; only a device-availability check (`isHealthDataAvailable()`) | FACT: `ExerciseCapabilities`/`ExerciseTypeCapabilities.supportedDataTypes`, queried live per device, explicitly recommended by Google before every session |
| Read authorization state | FACT: deliberately opaque — denied-read and no-data-present are indistinguishable to the app | FACT: explicit granted/denied permission state, independently queryable |

---

## Workout capture vs health context

The platforms themselves physically enforce a split between two categories of data, and the
architecture should not fight this split:

**Live workout capture** — data that only exists because an active workout/exercise session is
running, delivered in near-real-time to the app while the session is open:
heart rate, active/basal energy, distance, pace/cadence (cardio types), GPS/route, elevation, raw
accelerometer/gyroscope.

**Retrospective / recovery / health-context data** — computed by the platform on its own schedule,
independent of any workout session, and only ever associated with a workout after the fact by
timestamp proximity, never owned by it:
resting heart rate, HRV, VO2 max, respiratory rate, skin/wrist temperature (explicitly
sleep-scoped on Apple), and SpO2/ECG in practice (spot-check/user-initiated, not a workout-session
stream on either platform).

**RECOMMENDATION:** treat these as two different architectural categories from the start. A single
`Workout` aggregate should not attempt to own both a live sensor time-series and a loosely-related
daily/recovery metric — the platforms themselves never deliver the second category as part of a
workout session.

---

## Platform differences that architecture must preserve

These are differences a future abstraction must keep visible, not normalize away.

**Runtime capability differences (errata-corrected).** Wear OS capability support must generally
be queried at runtime per device, per session — Google's own compatibility guidance states this
explicitly for essentially every metric except heart rate and step count, which is the one
confirmed cross-device baseline. Even for that baseline pair, a compatibility guarantee does not
remove the need for session-level `DataTypeAvailability` handling (`AVAILABLE` / `ACQUIRING` /
`UNAVAILABLE`, delivered via `onAvailabilityChanged()`) — a metric being "supported" is not the
same as it being available in a given moment (sensor still acquiring, device off-wrist, etc.).
Apple has **no official per-metric hardware-capability discovery API at all** — not even the
equivalent of Wear OS's coarse baseline. Model-generation inference (Series N / SE / Ultra) is
only a best-effort developer workaround for a real, documented gap in Apple's API surface, not an
Apple-endorsed discovery mechanism, and unsupported types simply fail to arrive silently during a
live workout with no distinguishing error.

**Authorization-state asymmetry (errata-reconfirmed).** Apple HealthKit read authorization cannot
be represented with the same explicit granted/denied model available on Android. Apple's own
documented behavior: an app cannot determine whether read permission was denied — a denial and
"no data of this type exists" are deliberately indistinguishable, by privacy design. Android/Health
Connect gives an explicit, independently queryable granted/denied state per permission, regardless
of whether data exists. A shared model must not present both platforms as if they support equally
precise authorization introspection.

**Source/provenance semantics (errata-corrected).** Source application identity and physical-device
identity have different trust semantics on both platforms. On Apple, `HKSourceRevision` (app
identity) is reliably attached, while `HKDevice` (manufacturer/model/hardware version) is optional
and not guaranteed populated, especially for samples written by third-party sources. On Android,
`Metadata.dataOrigin` (writing app's package name) is platform-attested and cannot be spoofed by
the writer, while `Metadata.device` (manufacturer/model/type) is developer-supplied at write time
and not system-verified. **On neither platform should physical-device provenance be treated as a
platform-guaranteed, tamper-proof fact** — it is a best-effort field whose accuracy depends on
whichever app wrote the record, including TBDFit's own future write path.

**Live vs spot/background acquisition.** Some metrics are architecturally streaming (heart rate,
distance, energy during a session); others are architecturally spot-check or background-computed
regardless of platform (HRV, resting HR, VO2 max, SpO2 in practice, ECG, skin temperature). This
distinction must be visible in any shared data model, not collapsed into "a timestamped reading."

**Raw vs platform-derived data.** Heart rate, ECG classification, and SpO2 are derived-only for
third parties on both platforms — no raw PPG waveform or live electrode signal is exposed to
third-party apps on either platform. Raw accelerometer (and, likely, gyroscope — flagged for
device verification below) is a genuine exception on both platforms: real raw signal, no special
health-data permission gate. These belong in a different trust/precision category than
platform-derived health metrics and should not be modeled identically.

**Sampling/delivery semantics (errata-corrected).** No application-level fixed delivery cadence
may be assumed; consumers must tolerate irregular and batched delivery. This applies on both
platforms: Wear OS's own compatibility guidance explicitly states developers must not assume any
predefined or predictable batching interval, and documents delivery windows stretching to ~150
seconds under low-activity conditions despite ~1 Hz sensor sampling; Apple's `HKLiveWorkoutBuilder`
similarly delivers via an event-driven callback with no published fixed interval. A metric
"sampling at roughly 1 Hz at the sensor" is not the same claim as "delivered to the app roughly
once per second," and only the former is anything close to documented.

---

## Strength-first implications

Candidate first useful measurements for a strength-training workout, evidenced by the capability
matrix above and by what competing products already ship:

- **Live heart rate** — reliably live on both platforms, directly supports "how hard was this
  session."
- **Energy/calories** — live on both platforms, standard baseline expectation.
- **Duration** (active vs total/rest time) — trivially available, high value for strength (time
  under load vs rest).
- **Possible HR-based rest-interval context** — derivable from the same live HR stream already
  needed for the point above; no new platform capability required.

**HRV, SpO2, skin/wrist temperature, ECG, respiratory rate, and VO2 max are later enrichment
candidates, not part of the initial strength-workout scope, unless a future product decision
changes this.** This is driven directly by the research: none of these behave as live,
workout-session-scoped data on either platform, several are heavily OEM/generation-gated, and one
(ECG) is unavailable via any standard Wear OS API at all.

---

## Cardio implications

Recorded for awareness only — **no cardio architecture is designed here**, consistent with
[PD-003](../product/decisions.md#pd-003-workout-domain-scope--deferred), which leaves the first
implemented workout domain (strength, cardio, or both) as a deferred product/sequencing decision.

Cardio would introduce requirements strength does not have:

- GPS/routes as primary, not enrichment, data.
- Continuous time-series storage shape, fundamentally different from strength's sparse set/rep/load
  rows.
- Pace, distance, and elevation as core session data rather than optional context.
- Background execution becomes load-bearing rather than optional: Wear OS 5+ requires a foreground
  service with the correct declared service type(s) (`health`, and `location` if tracking GPS)
  backed by an Ongoing Activity notification for sampling to continue; Apple's workout-session
  background contract plays the equivalent role.
- The exercise-type taxonomy itself becomes an architectural input, since cadence/pace only exist
  as live data types for running/walking-classified sessions on Wear OS.

---

## Candidate architecture principles

`CANDIDATE — NOT YET ACCEPTED.` Each requires a future ADR (and, where it would change an existing
product decision, an update to `docs/product/decisions.md`) before it governs implementation.

1. **Runtime/device-aware capability discovery.** Capability must be checked at runtime, per
   device, per session — treated as effectively mandatory on Wear OS (no baseline exists beyond
   heart rate/step count) and as a best-effort, silently-degrading concern on Apple (no discovery
   API exists at all; generation inference is a workaround, not a guarantee).
2. **Prefer platform-derived health metrics over raw-sensor reimplementation.** Reserve raw
   accelerometer/gyroscope access for a clearly separate, explicitly opt-in future capability — not
   folded into the same abstraction as derived health metrics.
3. **Use platform workout-session frameworks for sampling and background lifecycle**, rather than
   hand-rolling sensor polling loops — required in practice on Wear OS given foreground-service-type
   rules, and the only App-Review-safe path on Apple.
4. **Separate workout-capture data from retrospective/health-context data** at the architecture
   level, not only conceptually, matching the physical split the platforms already enforce.
5. **Abstract at product-meaningful concepts carrying explicit semantic metadata** (e.g. source,
   acquisition method, live-vs-spot, unit/context) — not at bare platform types, and not at a
   generic numeric wrapper that would erase the distinctions recorded above.
6. **Represent capability/authorization state in a platform-honest way** rather than as a single
   collapsed boolean, given Apple's and Android's genuinely different read-permission semantics.

### Abstraction constraint

> **Abstract product meaning, not hardware/platform reality.**

Complementary constraint:

> **Cross-platform abstraction must not normalize away source, acquisition context, platform
> limitations, or measurement semantics.**

No Kotlin or Swift interfaces or types are defined by this document. That work is explicitly
deferred to a future design phase and ADR.

---

## Differentiation hypothesis

Recorded as a hypothesis only, not a fact or a decision:

> TBDFit may differentiate by combining serious structured workout logging with native watch
> execution and capability-transparent presentation of the best physiological data actually
> available from the user's device.

This must be read alongside an explicit limitation surfaced by the competitive-landscape research:

> Heart-rate graphs and calories during strength workouts alone are not a differentiator —
> competitors already provide this (most directly, Hevy already ships HR graph + average HR +
> calories attached to strength sessions from either watch platform).

The observation that no competitor researched (Hevy, Strong, Fitbod, Garmin Connect, WHOOP, Apple
Fitness, Fitbit/Pixel) was found to **publicly document** capability-aware/transparent-about-
missing-sensors presentation is based only on public documentation and marketing material reviewed
during this research — it has not been verified against actual competitor app behavior, screenshots,
or internal implementation, and must not be treated as a confirmed claim that no competitor does
this.

---

## Open product questions

Recorded, not answered, by this research:

- Should TBDFit's differentiation prioritize metric breadth (e.g. WHOOP-level physiological depth)
  or transparent/capability-aware presentation?
- How deeply should TBDFit invest in SpO2/temperature/ECG given the cross-platform asymmetry
  documented above (meaningfully more accessible on Apple than on Wear OS, where the richest
  sensors are typically OEM-partner-gated)?
- Which health-context metrics eventually belong near workout history versus a separate
  recovery/health area of the product?
- How much device/source information should ordinary users see, versus being reserved for an
  advanced/details view?

---

## Real-device verification backlog

Unresolved questions that documentation alone cannot answer — preserved as open questions, not
implementation tasks:

- Exact delivery reliability of `HKLiveWorkoutDataSource` types other than heart rate during a
  strength-training (`functionalStrengthTraining`) session specifically.
- Whether `oxygenSaturation` samples arrive at all during an active Apple Watch workout, versus
  only through the separate spot-check Blood Oxygen app.
- watchOS raw gyroscope/magnetometer reliability (only forum-level reports found; contradicts
  iPhone-side reliability expectations).
- Actual Watch→iPhone HealthKit reconciliation latency/behavior under poor connectivity, relevant
  to TBDFit's own phone/watch replication design.
- Whether HRV and VO2 max are deliverable as **live** Wear OS Health Services data types at all, or
  are historical-only via Health Connect.
- Real behavior of a Wear OS `ExerciseSession` when Bluetooth to the phone is fully severed
  mid-session, versus merely "phone app not running."
- The complete, current `DataTypeAvailability` enum on Wear OS — this research could confirm
  `AVAILABLE`, `ACQUIRING`, and `UNAVAILABLE` against secondary sources, but could not verify the
  full enum against the primary reference page directly.
- Which Wear OS OEMs besides Samsung actually populate `SkinTemperatureRecord` /
  `OxygenSaturationRecord` / respiratory-rate records in Health Connect in practice, and how sparse
  that data is (e.g. Pixel Watch, Fossil).
- Current status of Android's `BODY_SENSORS` → granular Health Connect permission migration, so any
  future design targets the current, not a superseded, permission model.

---

## Status

**STATUS: RESEARCH — FOR TEAM REVIEW**

Nothing in this document is an Accepted ADR. The candidate principles above are inputs to a future
architecture decision, not a decision themselves. This document does not change the status of
[PD-001, PD-002, or PD-003](../product/decisions.md).
