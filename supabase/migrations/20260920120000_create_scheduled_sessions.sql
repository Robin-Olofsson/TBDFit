-- scheduled_sessions: the first Supabase table for TBDFit's Scheduling/Calendar domain — a
-- user-owned occurrence of an existing training plan (Routine or ProgramSession) at a specific
-- future instant. See docs/development/supabase-setup-and-verification.md's "Scheduling / Calendar
-- Planning" section for the full picture this establishes, including the intentionally-deferred
-- future direction (reminders/notifications, start-confirmation, missed/cancelled/aborted
-- semantics, recurrence).
--
-- WHAT THIS IS: a PLAN REFERENCE, not a plan snapshot.
--   Routine or ProgramSession
--     -> Schedule (this table)
--     -> ScheduledSession, an exact future date/time
--     -> Calendar
--     -> (later) native client may start that planned session
--     -> execution snapshot -> completed Workout (see 20260920130000, which links a Workout back to
--        the ScheduledSession it fulfilled, if any)
-- A ScheduledSession does NOT copy Routine/ProgramSession exercises/sets at scheduling time — it
-- just points at the current plan by id. If the source plan is edited after scheduling but before
-- execution, the ScheduledSession naturally reflects the new content (e.g. a renamed Routine shows
-- its new name in a future Calendar read) — this is accepted V1 behavior, not a bug: snapshotting
-- happens exactly once, at native execution start, which is what turns a plan reference into a
-- Workout (the actual execution/result authority). This preserves the same
-- PLAN/INTENT -> START SNAPSHOT -> EXECUTION AUTHORITY -> RECORDED RESULT invariant already
-- established for Routine -> ProgramSession copying and for completed Workout History.
--
-- WHAT THIS IS NOT (deliberately, this slice):
--   - not a Workout, not a Workout snapshot, not an execution state of any kind
--   - not a reminder/notification itself — no push tokens, no delivery log, no scheduled job
--   - not a state machine (no PLANNED/STARTED/COMPLETED/MISSED/ABORTED/CANCELLED status column) —
--     "row exists" IS "currently scheduled"; "row deleted" IS "unscheduled/removed from planning."
--     Whether a completed Workout resulted from this occurrence is answered by
--     workouts.origin_scheduled_session_id (20260920130000), never inferred from this table alone.
--     See that migration's own comment, and the docs section, for why collapsing planning
--     cancellation / missed-scheduled-intent / aborted-execution into one field here would be
--     premature — those are three different future concepts this slice explicitly does not decide.
--   - not recurring — one row is one explicit occurrence; no RRULE/cron/repeat_interval today.
--   - client-agnostic, same precedent as workouts/daily_activity: nothing below distinguishes Web
--     from Phone/Watch. Unlike Workout execution, though, Web scheduling IS a valid, intended Web
--     product responsibility (Web may schedule/reschedule/unschedule/read Calendar; it still may
--     never execute, log sets, or otherwise touch Workout state) — see the docs section for the
--     full boundary restatement.

-- ============================================================================
-- IANA timezone validation
-- ============================================================================

-- Validates against Postgres's own tzdata-backed pg_timezone_names view rather than a hand-written
-- whitelist, which would silently drift from whatever timezone database Postgres actually ships.
-- STABLE, not IMMUTABLE, in the strictest sense (a Postgres/tzdata upgrade could in principle change
-- pg_timezone_names' contents) — this is atypical for a function backing a CHECK constraint, but is
-- an established real-world pattern for exactly this problem, and is dramatically more correct than
-- a maintained list of "CET"/"EST"-style abbreviations (which this schema deliberately never stores
-- as the primary identifier — only real IANA identifiers like 'Europe/Stockholm' are accepted).
create or replace function public.is_valid_iana_timezone(tz text)
returns boolean
language sql
stable
as $$
  select exists (select 1 from pg_timezone_names where name = tz)
$$;

-- No function keeps its default PUBLIC execute grant in this repo, regardless of how low-risk it is
-- — this is a pure read-only lookup against a public system view, not a privilege boundary, but the
-- explicit revoke/grant is this codebase's own hygiene convention (see record_completed_workout's
-- own precedent).
revoke all on function public.is_valid_iana_timezone(text) from public;
revoke all on function public.is_valid_iana_timezone(text) from anon;
grant execute on function public.is_valid_iana_timezone(text) to authenticated;

-- ============================================================================
-- scheduled_sessions
-- ============================================================================

create table if not exists public.scheduled_sessions (
  -- Server-generated, unlike workouts.id: there is no idempotent-retry requirement here (a plain
  -- RLS-protected single-row INSERT/UPDATE/DELETE, no multi-table aggregate RPC), so this follows
  -- routines.id/programs.id's own convention instead of workouts.id's client-generated one.
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  -- Exactly one of routine_id / program_session_id must be set — see the CHECK constraint below.
  -- CASCADE (not SET NULL, unlike workouts' provenance columns): a ScheduledSession is future
  -- planning intent tied to a source plan, not historical truth — deleting the source plan means
  -- there is nothing left to schedule, so the occurrence disappears with it. This is the deliberate
  -- opposite of completed Workout History, where the historical snapshot must survive source
  -- deletion (see 20260917120000_create_workout_history.sql's own header comment on why).
  routine_id uuid references public.routines(id) on delete cascade,
  program_session_id uuid references public.program_sessions(id) on delete cascade,
  -- The actual instant, always resolvable/comparable/sortable regardless of timezone.
  scheduled_at timestamptz not null,
  -- The user's INTENDED timezone context at scheduling time, preserved alongside the instant rather
  -- than discarded — required for later displaying "the same wall-clock time the user chose" even
  -- across a DST transition, and is a prerequisite for a future reminder/notification subsystem
  -- (see the docs section's "PLANNED / FUTURE DIRECTION" writeup) without a schema redesign then.
  -- A real IANA identifier (e.g. 'Europe/Stockholm'), never a fixed-offset abbreviation.
  scheduled_timezone text not null
    constraint scheduled_sessions_timezone_valid check (public.is_valid_iana_timezone(scheduled_timezone)),
  -- Both created_at and updated_at are justified here (unlike daily_activity, which needed only
  -- one) — a scheduled_sessions row is a mutable planning object, edited via reschedule potentially
  -- many times after creation. "When was this originally planned" and "when was it last changed"
  -- are two different, both genuinely useful facts for a future Calendar UI (e.g. "rescheduled 2
  -- hours ago"). No trigger: a reschedule's own UPDATE statement sets updated_at = now() explicitly,
  -- the same convention daily_activity's upsert already established — this repo avoids
  -- trigger-based magic for a fact the mutating statement can just state directly.
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- Exactly one plan source, enforced at the database level via boolean XOR (<>): both non-null and
  -- both null are equally invalid. Explicit FKs to the two real source tables, not a generic
  -- polymorphic (source_type, source_id) pair — there are exactly two schedulable plan kinds today
  -- (Routine, ProgramSession; see the migration's own header comment on why a Running/cardio domain
  -- or other future source kind is not pre-emptively modeled here), and a generic pair would trade a
  -- real, database-checkable FK for a text discriminator plus manual polymorphic-integrity
  -- discipline — strictly worse for two known cases.
  constraint scheduled_sessions_exactly_one_source check (
    (routine_id is not null) <> (program_session_id is not null)
  )
);

-- owner_id alone: "my full schedule." owner_id+scheduled_at: the actual expected Calendar query
-- shape (an account's occurrences within a date/time range, ordered by time) — see the docs
-- section's "Calendar read contract" for the exact query this serves. routine_id/program_session_id
-- indexes exist so the ON DELETE CASCADE lookup when a Routine/ProgramSession is deleted isn't a
-- sequential scan, the same reasoning workout_exercises_workout_id_idx already established.
create index if not exists scheduled_sessions_owner_id_idx on public.scheduled_sessions (owner_id);
create index if not exists scheduled_sessions_owner_id_scheduled_at_idx on public.scheduled_sessions (owner_id, scheduled_at);
create index if not exists scheduled_sessions_routine_id_idx on public.scheduled_sessions (routine_id);
create index if not exists scheduled_sessions_program_session_id_idx on public.scheduled_sessions (program_session_id);

-- Explicitly NOT unique on (owner_id, date-of(scheduled_at)) or (owner_id, routine_id, date) —
-- multiple ScheduledSessions per day, including multiple occurrences of the exact same source plan
-- in one day, are valid V1 behavior (e.g. an AM and PM session). Each row is its own independent
-- occurrence.

grant select, insert, update, delete
on table public.scheduled_sessions
to authenticated;

revoke all
on table public.scheduled_sessions
from anon;

alter table public.scheduled_sessions enable row level security;

-- WRITE ARCHITECTURE: plain RLS-protected INSERT/UPDATE/DELETE — no schedule_session()/
-- reschedule_session()/unschedule_session() RPC, no SECURITY DEFINER function. The apparent
-- complication ("ordinary FK constraints alone do not enforce that the referenced Routine/
-- ProgramSession actually belongs to the caller") does not actually require a privileged function
-- to solve: unlike a plain table CHECK constraint, a Postgres RLS USING/WITH CHECK clause CAN
-- contain arbitrary subqueries and joins, so the exact ownership-chain verification is fully
-- expressible declaratively, evaluated under the calling user's own privileges. This keeps the same
-- "prefer SECURITY INVOKER + RLS when it already expresses the rule correctly" principle already
-- established for follows (20260918130000) and daily_activity (20260919120000) in this schema —
-- there is no multi-table write-atomicity requirement here (unlike record_completed_workout's
-- aggregate write) to justify a DEFINER function. A reschedule is just an UPDATE of scheduled_at/
-- scheduled_timezone (with updated_at set explicitly by the caller's statement); an unschedule is
-- just a DELETE. Physical deletion is the accepted V1 unschedule semantic — no revision history for
-- schedule edits is kept.
create policy "select own scheduled sessions"
on public.scheduled_sessions
for select
to authenticated
using ((select auth.uid()) = owner_id);

-- Routine ownership: routines.owner_id directly (20260910120000_create_routines.sql). ProgramSession
-- ownership: program_sessions -> program_weeks -> programs -> owner_id, the EXACT join chain every
-- existing program_sessions/program_session_exercises RLS policy already uses
-- (20260911120000_create_programs.sql) — reused verbatim here, not reinvented, so a caller knowing
-- another account's ProgramSession id (via UUID guess or leak) is rejected at the same depth the
-- rest of this schema already relies on.
create policy "insert own scheduled sessions"
on public.scheduled_sessions
for insert
to authenticated
with check (
  (select auth.uid()) = owner_id
  and (
    routine_id is null
    or exists (select 1 from public.routines r where r.id = routine_id and r.owner_id = (select auth.uid()))
  )
  and (
    program_session_id is null
    or exists (
      select 1
      from public.program_sessions ps
      join public.program_weeks pw on pw.id = ps.program_week_id
      join public.programs p on p.id = pw.program_id
      where ps.id = program_session_id and p.owner_id = (select auth.uid())
    )
  )
);

-- Same ownership-chain check as insert, re-evaluated on UPDATE's WITH CHECK — a reschedule that also
-- changes which plan it points at (not required by V1 UX, but not prohibited either) is held to the
-- identical ownership bar as the original schedule call.
create policy "update own scheduled sessions"
on public.scheduled_sessions
for update
to authenticated
using ((select auth.uid()) = owner_id)
with check (
  (select auth.uid()) = owner_id
  and (
    routine_id is null
    or exists (select 1 from public.routines r where r.id = routine_id and r.owner_id = (select auth.uid()))
  )
  and (
    program_session_id is null
    or exists (
      select 1
      from public.program_sessions ps
      join public.program_weeks pw on pw.id = ps.program_week_id
      join public.programs p on p.id = pw.program_id
      where ps.id = program_session_id and p.owner_id = (select auth.uid())
    )
  )
);

create policy "delete own scheduled sessions"
on public.scheduled_sessions
for delete
to authenticated
using ((select auth.uid()) = owner_id);

-- PRIVACY: ScheduledSession is private planning data, the same security domain as workouts/
-- daily_activity — completely separate from the Profile + Social read boundary
-- (20260918130000_create_follows.sql / 20260918140000_create_profile_summary.sql). A `follows` edge
-- between two accounts grants zero visibility here: following someone is a Profile/Social-graph
-- concept, not a schedule/calendar-data grant, and none of this table's policies reference `follows`
-- at all. get_my_profile_summary() is untouched by this migration and returns no schedule/calendar
-- field of any kind.
