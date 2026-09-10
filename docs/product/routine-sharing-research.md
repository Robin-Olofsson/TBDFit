# TBDFit — Routine Sharing via Public Link

**RESEARCH ONLY — NOT AN ACCEPTED PRODUCT DECISION.** This document informs a future implementation
prompt; nothing here has been built, and no migration/RLS/routing change has been made. See
`docs/product/web-routine-program-planning-research.md` / `docs/architecture/cross-client-identity-sync-research.md`
for this repo's established convention for this kind of pre-decision artifact.

## 1. Current Routine aggregate (repository truth, verified directly against migrations)

A complete Routine today is `routines` (id, owner_id, name, created_at, updated_at) →
`routine_exercises` (id, routine_id, **exercise_id: text**, position, **note**: text≤1000 nullable,
**rest_timer_seconds**: int≥0 nullable, "Off"=null) → `routine_planned_sets` (id,
routine_exercise_id, position, target_reps: int≥0 nullable, target_weight: numeric≥0 nullable,
**set_type**: text NOT NULL default `'NORMAL'`, CHECK IN NORMAL/WARMUP/FAILURE/DROPSET).
`exercises` (id text pk — canonical `builtin_*` slugs or a client-generated custom id, name,
owner_id nullable uuid, exercise_type nullable CHECK WEIGHT_REPS/BODYWEIGHT_REPS, equipment
nullable CHECK 8 values) is the identity/metadata authority everything else references by id, never
by name. `save_routine(routine_id, name, exercises jsonb)` is the sole write path: full
replace-by-delete-then-reinsert, `security invoker` (never bypasses RLS), pinned `search_path`. Every
table: RLS on, `revoke all from anon`, ownership scoped to `auth.uid()`. `routine_exercises`'
insert/update policies explicitly re-check the referenced exercise's visibility inside `with check`
— FK integrity alone does not stop attaching another account's private custom exercise; this is the
load-bearing precedent for section 8 below.

`programs`→`program_weeks`→`program_sessions`→`program_session_exercises`→
`program_session_planned_sets` is a fully separate family with **no FK back to any `routine_*`
table**. `copy_routine_to_program_session(routine_id, program_week_id)` reads the caller's own
Routine once and writes independent rows; `program_sessions.source_routine_id` is a real FK but
`ON DELETE SET NULL` — pure traceability, never load-bearing, deleting the original Routine cannot
break or block the copy. **This is the exact "copy, not live reference" pattern the product already
committed to for an analogous cross-boundary copy** — directly reusable for sharing, not merely an
analogy.

`web/src/App.tsx` has no public/unauthenticated route today besides `/login` itself:
`RESTORING_SESSION`/`AUTH_ERROR` early returns → `requiresAuthScreen(phase)` (only `/login`, else
redirect) → `isAuthenticatedPhase(phase) ? <AuthenticatedShell/> : null`, and `AuthenticatedShell`
owns the entire real route table inside its own auth-gated `<Routes>`. `web/src/queryKeys.ts`'s
factories are all `userId`-scoped by design (the primary account-isolation mechanism) — an
anonymous share view has no `userId` and needs its own, differently-scoped key.

## 2. Live link vs. immutable snapshot

**Model A (live link)** would require the public read path to reach the *actual* mutable
`routines`/`routine_exercises`/`routine_planned_sets` rows — either a new anon-facing SELECT policy
chain across all three tables (permanently mixing "private CRUD table" and "public read surface" in
the same rows, and repeating the join-based ownership-check complexity three times over, forever)
or a SECURITY DEFINER RPC that bypasses RLS on every read (a standing, repeated bypass, not a
one-time write). Either way, the live, owner-editable aggregate becomes reachable by non-owners on
every single view.

**Model B (immutable snapshot)** needs exactly one owner-authenticated write (share creation) to
reason about; every subsequent read touches only the snapshot, never the live private tables at all.

TBDFit's own `Routine → COPY → ProgramSession` precedent is not just consistent with B, it is direct
evidence FOR it: the team already rejected "children reference the live Routine" for a
same-account copy, specifically to avoid entangling child lifecycle with source lifecycle. A
cross-account public share has strictly more reasons to make that same choice (privacy,
revocation, and reproducibility all resolve for free under a snapshot; none of them do under a live
link).

**Recommendation: immutable snapshot.** Repository truth reinforces the stated preference; no
concrete reason found to choose live-link.

## 3. Separate `RoutineShare` representation vs. `is_public` flag

A flag/public-policy on `routines` directly would need new anon SELECT policies on `routines` AND
`routine_exercises` AND `routine_planned_sets` (three join-based predicates to get exactly right,
not one), permanently mixing private-CRUD and public-read concerns on rows currently protected by
zero anon access. A separate representation needs **zero changes to any existing private table's
RLS** — the biggest practical win: nothing about the already-verified private-data guarantees needs
touching or re-testing. **Recommendation: separate representation (`routine_shares`, see §16),
not a flag on `routines`.**

## 4. Snapshot contents — canonical reference vs. copied value

- **Name**: copy. Immutable by definition; a rename after sharing must not retroactively change
  what was shared.
- **Exercise identity**: copy display fields (`name`, `exercise_type`, `equipment`) into the
  snapshot, **for both built-ins and customs**, rather than a live reference. This is not just the
  only option for customs (anon/other accounts can never read a private exercise row, full stop) —
  it is also the *simpler, more consistent* choice for built-ins, because **anon has zero SELECT
  access to `exercises` today**, and adding one just to resolve display names for shares would be a
  new, narrow, easy-to-get-wrong anon-readable surface on a live table for no real benefit. Retain
  the canonical `exercise_id` too, *only* when it's a built-in, purely so import can match "same
  canonical exercise" without a name-lookup (see §7). If canonical Exercise metadata changes after
  a share exists: **nothing happens to the share** — it shows what was true at share time, which is
  the entire point of an immutable snapshot, applied consistently down to the leaf fields.
- **Exercise ordering, rest_timer_seconds, planned sets (target_weight/target_reps/set_type,
  ordering)**: copy — see §6.
- **note**: excluded by default — see §5.

## 5. Notes — privacy decision

Note can carry coaching cues, personal reminders, or injury/private information. Comparing A
(always included) vs. B (never) vs. C (opt-in): **C requires new UI, a new per-share or per-field
toggle, and a real chance the toggle is left on by accident** — real complexity and real risk for a
V1 whose own brief asks to "keep the design simple." **B (never share notes) is simplest and
safest, and a strict subset of what C could ever expose** — nothing to redact, because nothing was
ever copied into the snapshot. Adding an opt-in later needs zero migration cost against a JSONB
snapshot (§17) — just a new optional key going forward. **Recommendation: B — do not include Notes
in V1**, matching the stated product preference, with the reasoning for B over C made explicit.

## 6. Rest timer and set type

Neither is personal/private data — both are structural attributes of the planned prescription
itself, the same category as target_reps/target_weight/ordering, not an annotation like `note`.
**Recommendation: include both**, confirming the stated intuition against the domain model.

One tension worth surfacing explicitly rather than assuming away: **Hevy's actual, documented
behavior is the opposite for target_reps/target_weight** — "the exercises will share, but the reps
and weights will not" (only a rep *range*, if set, propagates). TBDFit's spec's own phrasing assumed
targets would be shared; Hevy's real product does not share them. This is flagged as an open
decision (see §26), not silently resolved either way.

## 7. Built-in exercises

Confirmed: a shared built-in's canonical `exercise_id` (e.g. `builtin_romanian_deadlift`) already
means the same thing on every account — import should map it directly to the recipient's own view
of that same globally-visible row. No duplicate, no remapping needed. This is the easy case,
assuming the snapshot retained the canonical id per §4.

## 8. Custom exercises — the primary research question

A: shared snapshot carries enough metadata to render/import; on import, a NEW custom exercise is
created **owned by the recipient**, with a **new id**, and the recipient's new `routine_exercises`
row points at that new id. B (temporarily expose the original): rejected — a "temporary" anon-read
exception on a specific private row is a classic RLS anti-pattern (hard to bound correctly, easy to
leave open, and still exposes the owner's real object even briefly) for no benefit over A. C (cannot
share customs): rejected — breaks the feature for the exact routines most worth sharing (ones with a
lifter's own custom movements).

**A is correct, and repository truth confirms it's fully sufficient**: a custom exercise's only
fields worth copying are `name`/`exercise_type`/`equipment` — everything the snapshot already needs
to carry per §4. Consequences:
- **No cross-account FK is ever created** — the recipient's new exercise row has no reference back
  to the original owner's row, satisfying the "recipient ownership stays isolated" goal structurally,
  not by convention.
- **Deduplication**: none in V1. Repeated imports (of the same share, or of two shares whose
  originals both contained "the same" custom exercise by name) each create their own new row — this
  matches how customs already behave today (name carries no uniqueness constraint; "Bench Press" /
  "Incline Bench Press" / "Smith Machine Bench Press" already coexist by design per
  `ExerciseEntity.kt`'s own doc comment on Android, mirrored in Postgres). Flag as an explicit,
  deliberate V1 non-goal, not an oversight.
- **ID remapping** happens inside one atomic, `security invoker` import RPC, exactly mirroring
  `save_routine`'s own pattern: for each snapshot exercise entry, if it names a built-in canonical id
  that still exists → use it; otherwise → insert a new custom exercise owned by the importer from
  the copied `name`/`exercise_type`/`equipment`, then use *that* new id when building the recipient's
  `routine_exercises` rows. The whole import remains one transaction, RLS-enforced identically to an
  ordinary Routine save.
- **Future image**: flagged, not solved, in §19.

## 9. Import/copy semantics

Reuse the exact Routine→ProgramSession invariant: copy once, independently mutable forever after.
One nuance worth being explicit about: the imported Routine's traceability FK (if one is added)
should point at the **share/snapshot row**, never at the original private `routines` row — the
recipient has no legitimate reason to hold any reference, even a dangling nullable one, into another
account's private schema. `on delete set null` against `routine_shares`, matching
`program_sessions.source_routine_id`'s exact precedent. Verified against all three stated
invariants: original owner edits → no effect (both snapshot and import are already-copied,
independent data); share revoked → recipient's Routine, now a fully independent row, is unaffected;
original account deleted → recipient's Routine remains (it was never owned by, or dependent on,
that account in the first place).

## 10. Anonymous access — the central security finding

**A direct `anon` SELECT policy on `routine_shares`, gated only on something like
`revoked_at is null`, is not sufficient and would be a real vulnerability**: RLS filters which rows
a query is allowed to see, but it cannot distinguish "the client already knows the exact token" from
"the client ran `select * from routine_shares` with no filter at all" — a permissive
"not-revoked" policy would let `anon` **enumerate every active share on the table**, not just look
up ones whose token it already possesses. Token unguessability alone does not fix this; the access
path itself must require the token as an input, not merely allow filtering by it.

**The correct pattern: a `SECURITY DEFINER` RPC** (e.g. `get_shared_routine(p_token text)`) that
performs the token-equality lookup server-side and returns the snapshot only on an exact match
against a still-active row. `anon` gets `EXECUTE` on this function and **no SELECT grant on
`routine_shares` at all** — there is then no query shape through which a table scan or enumeration
is even expressible. This mirrors why `save_routine` and `copy_routine_to_program_session` are RPCs
rather than raw `.select()`/`.insert()` calls, applied to the read side for the first time in this
app.

Other requirements: token generated server-side with real entropy (≥128 bits, e.g. 22+ char
base62/base64url) at share-creation time — never client-supplied or derived from anything guessable
(sequential id, timestamp, routine name); unique index on the token column for O(1) lookup; a
revoked or non-existent token must return the **same generic "not found" response** (never
distinguish "revoked" from "never existed" — a smaller, easy-to-miss information leak); the RPC's
return shape must be hand-picked to exclude `owner_id`, the original `routine_id`, and any
`auth.users` join — see §18's "minimum public data" principle. **Explicitly reject** the idea that an
unguessable token is itself sufficient authorization for anything beyond read of that one snapshot
— it must never be treated as implicit authorization to import, revoke, or modify anything.

## 11. Public route

`web/src/App.tsx` currently has exactly one variable driving its top-level branch: `phase`. A
`/r/:shareToken`-style route needs to be reachable **regardless of** `phase` — including during
`RESTORING_SESSION`, which today blocks all UI on an async `getSession()` call a share-link visitor
may have no session to restore at all. This means the share route cannot be added as "one more
`<Route>`" inside `AuthenticatedShell`'s existing auth-gated `<Routes>`, nor inside the unauthenticated
branch's `<Routes>` (which currently redirects everything but `/login`) — it needs to be checked
**before or independent of** the `phase`-based dispatch, e.g. by matching `location.pathname` (or a
sibling top-level `<Routes>` with the share route ahead of a catch-all that defers into today's
`renderForPhase(phase)` logic). This is a real, structural addition to `App.tsx`'s current shape —
correctly recommend the placement, but treat it as non-trivial, not a one-line route add.
`queryKeys.ts`'s `userId`-scoped factories don't apply here — a new, unscoped key family is needed
for anonymous share data.

## 12. Save-to-my-routines / login-return flow

Anonymous viewer: "Save to my routines" → redirect to `/login`, preserving the share token
client-side (e.g. `sessionStorage`, not a Supabase-level concern) across the login/signup round
trip → on `SIGNED_IN`, check for a pending token and complete the import automatically, then
navigate to the new Routine. Already-authenticated viewer: import immediately, no redirect. This is
a client-side "remember what I was doing" pattern, not special auth-flow architecture — kept
conceptual per the brief, no redirect code reviewed/designed here.

## 13. Share revocation

`revoked_at timestamptz null` (NULL = active) is recommended over a bare `active boolean`: exactly
as simple to query (`revoked_at is null` vs. `active = true`) but strictly more informative
(preserves *when* revocation happened, useful for support/audit) at zero extra cost — and over hard
delete, which would need `on delete set null` handling anyway for any recipient traceability FK and
throws away the historical record for no benefit. **Recommendation: `revoked_at timestamptz null`.**

## 14. Original Routine deletion — open decision, not silently resolved

Two real options: (a) share snapshot survives the original's deletion (public link keeps working;
matches the "snapshot is independent" philosophy taken to its logical end), or (b) deleting the
original auto-revokes any shares of it (matches an owner's likely intuition that deleting something
also stops it being public; requires either a DB trigger on `routines` DELETE or turning
`deleteRoutine` into an RPC that also revokes, since today it's a plain RLS-scoped `.delete()` with
no side effects).

TBDFit's own ProgramSession precedent ("deleting the source Routine has zero effect on the copy")
argues for (a) — but that precedent is same-owner-only; a *public*, cross-account artifact carries
real consent/reputational weight that a private same-account copy doesn't. **Leaning recommendation:
(b), auto-revoke on delete** — but this is flagged explicitly as one of the real decisions for the
developer, not chosen silently.

## 15. Reshare / version semantics

Confirmed: new share click → new immutable snapshot → new token, matching the stated preference.
UX implication worth surfacing: this also means old links do **not** auto-update or auto-die — a
routine can accumulate several simultaneously-live share links pointing at different historical
snapshots, unless the owner explicitly revokes old ones (no "my shares" management UI designed here).
**Recommendation: allow multiple to coexist in V1** (simpler, and a re-share for an unrelated reason
shouldn't silently kill a link someone already has) — flagged as open, not blocking.

## 16. Data model options

**Option 1 — normalized**: `routine_shares` (id, share_token unique, routine_id → set null,
owner_id, name, created_at, revoked_at) + `routine_share_exercises` (share_id → cascade,
exercise_id nullable canonical ref, name, exercise_type, equipment, position,
rest_timer_seconds) + `routine_share_sets` (share_exercise_id → cascade, position, target_reps,
target_weight, set_type).

**Option 2 — JSONB**: `routine_shares` (id, share_token unique, routine_id → set null, owner_id,
created_at, revoked_at, `snapshot jsonb`).

| | Normalized | JSONB |
|---|---|---|
| Simplicity | 3 tables, 3 RLS surfaces | 1 table |
| Public read | RPC must assemble a nested object from 3 joined tables anyway (Web needs one object regardless) | RPC returns the stored object directly |
| Import | Loop over exercises/sets (same either way) | Identical loop, `jsonb_array_elements` — literally `save_routine`'s existing style, reused |
| Validation | Column CHECKs enforce shape at write time | App/RPC-level only — mitigated: the only writer is the owner's own already-validated live Routine data, not arbitrary input |
| Schema evolution | New field = new migration + column, every time (this repo has already done this 3 times for note/rest_timer/set_type) | New field = new JSON key, no migration, ever |
| Snapshots staying historically readable | Depends on never dropping/renaming old columns | Immune by construction — an old snapshot's shape is exactly what it was written as, forever |
| Analytics/querying into contents | Native SQL joins/aggregates | Possible via GIN + containment queries, but not native |

**Recommendation: JSONB (Option 2) for V1.**

## 17. The JSON snapshot question — explicit verdict

This is one of the genuinely rare cases where JSONB fits better than the relational-everywhere
default this schema otherwise correctly follows: the data is write-once/immutable (no in-place
update semantics needed), always consumed as one whole nested object by exactly two first-party
readers (the share-view page, the import RPC — never ad-hoc analytics SQL), and the shape is
*identical* to what `save_routine` already accepts and validates today, meaning share-creation can
directly reuse that existing serialization logic rather than inventing a second one. Not chosen
because it's easy — chosen because the immutability + single-consumer-shape + historical-stability
properties are exactly what JSONB is good at and relational duplication is not.

## 18. Security threat model

- **Token guessing/enumeration**: high-entropy token + SECURITY DEFINER RPC-only read path (§10) —
  no table scan is expressible at all.
- **Reading the owner's private Routine, or unrelated private Exercises, through share joins**: the
  RPC returns only stored snapshot content, never a live join back into `routines`/
  `routine_exercises`/`exercises` — nothing to leak even if a snapshot happens to retain a built-in's
  canonical id (built-ins are already globally readable to any *authenticated* user; anon still
  never touches the live table directly).
- **Modifying a share as another user**: `routine_shares` INSERT is owner-authenticated
  (`security invoker`, `owner_id = auth.uid()`); **no UPDATE policy on snapshot content at all**
  (immutable, matching `exercises`' own established "no update/delete = deliberate default-deny"
  precedent) — only a narrow revoke path (setting `revoked_at`) is writable, owner-only.
- **Importing another user's private custom Exercise by id**: structurally prevented — import never
  writes a `routine_exercises.exercise_id` pointing at a live private exercise it didn't itself just
  create for the recipient in the same transaction (§8/§9).
- **Forged snapshot contents**: the only writer is a `security invoker` `create_routine_share`
  RPC that computes the payload server-side from the caller's own currently-RLS-visible Routine —
  a client cannot pass arbitrary snapshot JSON directly, mirroring `save_routine`'s own
  never-trust-a-client-supplied-ownership-pairing discipline.
- **Anonymous mutation**: zero anon INSERT/UPDATE/DELETE grants anywhere in this feature; anon's
  only capability is `EXECUTE` on the one read-only RPC.
- **Revoked-token reuse**: the RPC's own `revoked_at is null` check runs on every call — no caching
  window.
- **Accidental exposure of owner email/user_id**: the RPC's return shape must be hand-picked
  (never `select *`, never a join into `auth.users`) to exclude `owner_id` and the original
  `routine_id`. Minimum public data = Routine name + exercise/set structure + created_at only,
  unless a future product decision explicitly wants "shared by @username" attribution.
- **Notes leaking private information**: solved upstream by §5 — never copied into the snapshot in
  the first place, so there is no code path that could leak them via this surface.

## 19. Future exercise images — flag only, non-blocking

If images are added later, a custom exercise's future private image asset would need its own
explicit "copy to a public-safe location at share time" step (mirroring the copy-not-reference
principle established here) — a live-private-bucket reference could not simply be embedded in a
public snapshot. Built-in exercise images (permanently public, same for everyone) would need no
special handling. **Does not affect today's schema choice** — a JSONB snapshot can gain an optional
`imagePath` key later with zero migration cost.

## 20. Future Program sharing — flag only, no design done

The same pattern (JSONB snapshot, SECURITY DEFINER RPC read, owner-authenticated write,
revoked_at) generalizes directly to a future, separate `program_shares` table serializing a
Program's full nested structure — nothing about today's Routine-share design forecloses that path.
Recommend a second, near-identical table later rather than a premature generic polymorphic "shares"
framework now, consistent with this codebase's existing deliberate Routine/Program separation.

## 21. External product research

Sourced from official help-center/support articles where fetchable; two Zendesk-hosted articles
(Hevy, Strong) returned HTTP 403 to direct fetch and are cited via their own search-result summaries
instead — flagged, not silently treated as verified primary-source reading.

**Hevy** (help.hevyapp.com — "How to Share Workouts and Routines Step-by-Step",
hevyapp.com/features/share-folders-routines): explicitly supports **anonymous, no-account viewing**
— a shared link opens the routine on **hevy.com in a browser**, usable by people who don't have the
app at all. **Exercises share; reps and weights explicitly do not** (only a rep *range*, if one was
set, propagates) — a deliberate product choice to share structure, not specific numeric
prescriptions. No official documentation of a revocation/"unshare" feature was found in this
research pass (search specifically for it returned nothing — flagged as "not found," not "doesn't
exist").
[How to Share Workouts and Routines Step-by-Step](https://help.hevyapp.com/hc/en-us/articles/34953501503895-How-to-Share-Workouts-and-Routines-Step-by-Step) ·
[Learn How to Share Folders & Workout Routines](https://www.hevyapp.com/features/share-folders-routines/)

**Strong** (help.strongapp.io — "Share Link Updates (Strong 6.X)", "How do I share a workout or
template?"): sharing is via a link, but **the recipient must have the Strong app installed to
import it** — not browser/anonymous-viewable the way Hevy is. Link format is versioned and
breaking (6.0 links incompatible with pre-6.0 clients), consistent with an app-parsed
encoded-payload link rather than a persistent public web page.
[Share Link Updates](https://help.strongapp.io/article/255-share-links) ·
[How do I share a workout or template?](https://help.strongapp.io/article/109-share-workout-or-template)

**StrengthLog** (help.strengthlog.com — "Share a Workout or Program with Other Users"): also
app-gated, **and paywalled** — sharing requires the sender to have Premium, and the recipient must
open the link on a phone with StrengthLog 6.0+ installed; explicitly **cannot** be opened on a
desktop browser.
[Share a Workout or Program with Other Users](https://help.strengthlog.com/help-article/share-workouts/)

**Boostcamp** (boostcamp.app/share): only marketing copy found ("send the link, they save it,
start training") — no technical/support documentation surfaced describing anonymous-view,
revocation, or exact data included. Treated as unverified beyond that marketing claim.

**TrainHeroic** (support.trainheroic.com): official docs describe **coach-athlete roster
connection** (access codes, group programming) and ad-hoc content sharing via external links (e.g.
a Google Doc pasted into a session) — no evidence found of a Hevy-like standalone "public web page
for one routine" feature. Different sharing paradigm (coach-to-roster), not directly comparable.

**TBDFIT RECOMMENDATION vs. external behavior**: Hevy is the only one of the five with confirmed
official-documentation evidence of true anonymous, no-app-required, browser-viewable public routine
links — which is exactly the model the developer described wanting. Strong/StrengthLog require the
app (StrengthLog additionally requires a paid tier) and are not directly comparable to the desired
UX. This is not a reason to copy Hevy uncritically, but it is a reason to trust that the "anonymous
browser view, save requires an account" shape is a proven, real product pattern, not a novel risk.
Hevy's reps/weights-excluded-by-default behavior is noted as a genuine open question for TBDFit
(§6), not silently adopted.

## 22–25. See §9–§20 above (import/copy, revocation, deletion, reshare, data model, JSON,
threat model, images, Program sharing are each covered in their own numbered section above,
matching the order requested).

## Open product decisions

1. **Data model**: JSONB snapshot (recommended) vs. normalized relational — a real judgment call
   worth explicit sign-off since it's the first non-relational storage choice in this schema.
2. **Original Routine deletion**: auto-revoke shares (recommended) vs. shares survive.
3. **Reshare behavior**: allow multiple coexisting share links per routine (recommended) vs.
   auto-revoke prior shares on reshare.
4. **Owner attribution on the public view**: none by default (recommended, minimum public data) vs.
   showing "shared by @username" — not asked directly by the brief but a natural real question.
5. **Target reps/weight inclusion**: the brief's own phrasing assumed inclusion, but Hevy's actual
   product explicitly excludes them — decide deliberately rather than by default.

## Risks

- Treating an unguessable token as sufficient authorization by itself (rather than requiring the
  SECURITY DEFINER RPC pattern) would reintroduce the enumeration vulnerability described in §10 —
  the single highest-severity risk in this whole feature if built carelessly.
- A direct anon SELECT policy on any table "for convenience" (even one that seems narrowly scoped)
  is the most likely way this feature accidentally regresses the "private routines stay private"
  invariant — every existing table currently has zero anon access; this feature is the first
  exception, and it should stay maximally narrow (one RPC, one table).
- `App.tsx`'s routing change is more structurally invasive than it first looks (§11) — worth
  scoping as its own step in the eventual implementation prompt, not a one-line addition.
- Skipping the "auto-revoke on original deletion" decision (§14) risks shipping surprising behavior
  either direction — this should be an explicit product answer, not an implementation-time default.

## Recommendation

Immutable JSONB snapshot in a new, separate `routine_shares` table; public read exclusively via a
`SECURITY DEFINER` token-lookup RPC (never a direct anon SELECT policy); owner-authenticated
`security invoker` RPCs for create/revoke; import as an owner-authenticated `security invoker` RPC
that creates new custom-exercise rows for any non-built-in reference (never reusing another
account's id); Notes excluded from V1; rest_timer/set_type included; `/r/:shareToken` added as a
route reachable independent of `phase` in `App.tsx`, not nested inside the authenticated shell.

## ROUTINE SHARE V1 (proposed contract)

- **Share creates**: one immutable JSONB snapshot (name, ordered exercises with copied
  name/exercise_type/equipment + retained canonical id for built-ins, rest_timer_seconds, ordered
  planned sets with target_weight/target_reps/set_type) under a new high-entropy token, owned by the
  sharer.
- **Link visibility**: anonymous, browser-viewable, via a `SECURITY DEFINER` RPC keyed on the exact
  token — no direct table SELECT for `anon`, ever.
- **Notes**: never included.
- **Custom exercises**: fully embedded in the snapshot (name/type/equipment); on import, always
  become a brand-new exercise row owned by the recipient — never a reference to the original.
- **Import**: "Save to my routines" → one atomic, owner(recipient)-authenticated RPC → a fresh,
  independently-mutable Routine; anonymous viewers are sent to login/signup first, with the pending
  token preserved client-side and completed automatically after sign-in.
- **Revocation**: `revoked_at timestamptz` on the share row; a revoked or nonexistent token returns
  the same generic "not found," never distinguishing the two.
- **Original edits**: never affect an already-created share (immutable by construction) or any
  already-imported recipient Routine.

ROUTINE SHARING RESEARCH STATUS: READY FOR PRODUCT DECISION
