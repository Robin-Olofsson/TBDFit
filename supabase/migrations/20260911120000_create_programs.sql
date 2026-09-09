-- programs: the second real, centrally-persisted TBDFit product domain (after routines — see
-- 20260910120000_create_routines.sql). Program is an OPTIONAL, ADDITIVE structure built on top of
-- planned sessions; it must never become a requirement for ordinary Routine usage. Routine ->
-- Start -> Workout remains a complete, independent lifecycle with zero Program involvement — see
-- docs/architecture/program-routine-first-slice-design.md and
-- docs/product/web-routine-program-planning-research.md for the product reasoning this schema is
-- *informed by* (not mechanically copied from — see that reasoning restated at each divergence
-- point below).
--
-- Product invariants preserved from the existing domain research/design work:
--   - Program -> ProgramWeek -> ProgramSession -> ProgramSessionExercise -> ProgramSessionPlannedSet.
--   - ProgramWeek is a LOGICAL week (an ordered position), never a calendar week: no date, no
--     weekday, no startDate column exists anywhere in this migration. Calendar scheduling is a
--     separate, later, explicitly deferred concern (see the design doc's own reasoning, echoed by
--     TrainHeroic's official "author undated, assign to a calendar as a separate step" pattern
--     found during Web research).
--   - Routine -> ProgramSession is a COPY, never a live reference. This is the single
--     highest-confidence decision from all prior research/design work, and it is enforced
--     STRUCTURALLY here, not merely by discipline: there is no foreign key anywhere from
--     program_session_exercises/program_session_planned_sets back to
--     routine_exercises/routine_planned_sets. copy_routine_to_program_session() (below) reads a
--     routine's current content once and writes independent rows; nothing about a
--     program_session's content ever re-reads the source routine afterward. Editing or deleting
--     the source Routine after a copy has zero effect on the resulting ProgramSession — see that
--     function's own comment and the RLS/delete semantics below.
--   - ProgramInstance / Enrollment / "current program" / program progress are explicitly NOT part
--     of this slice (see the design doc's own deferral list) — this migration contains no such
--     table, and none of the functions below persist a progress counter anywhere. "Week 4 of 12" /
--     "11 of 36 sessions completed" is designed to be DERIVED later (once a central Workout/
--     execution model exists on Web, which it does not yet — see multi-client-product-vision.md)
--     by counting completed executions that reference a program_session, not stored here.
--   - No Program execution exists in this slice. There is no Workout table in this schema (Web
--     does not execute workouts at all yet — an explicitly open product question, not resolved by
--     this migration).
--
-- Ownership/security follows routines' exact established pattern (`(select auth.uid())`,
-- `grant ... to authenticated; revoke all ... from anon;`, RLS on every table, exists-based
-- ownership checks through parent chains for tables with no own owner_id column) — see that
-- migration's own top-of-file comment for the full reasoning, not repeated verbatim here except
-- where this schema's chain is one or two levels deeper (program_session_planned_sets is FOUR
-- joins from programs.owner_id: planned_set -> exercise -> session -> week -> program). The same
-- "FK integrity alone does not stop attaching another user's private custom exercise" gap that
-- routines' own migration explicitly closed is closed again here, identically, on
-- program_session_exercises' insert/update policies.
--
-- ORDERING DIVERGENCE FROM ROUTINES (deliberate, not an oversight): routine_exercises/
-- routine_planned_sets chose `unique(parent_id, position)` WITH an implicit expectation of always-
-- contiguous positions, because every routine write goes through save_routine()'s full-replace
-- strategy (delete everything, reinsert fresh 0..n-1 positions every save). Program's access
-- pattern is different and more incremental by design (add one week, delete one session, duplicate
-- one week — there is no "resave the entire 12-week program" operation anywhere in this slice).
-- The tables below therefore keep `unique(parent_id, position)` for real integrity (no two
-- siblings can silently collide) but do NOT require contiguity: each mutating function below
-- computes a fresh `coalesce(max(position), -1) + 1` for a new row and never renumbers siblings on
-- delete. This is the same sparse, never-compacted position discipline already established on the
-- Android side (WorkoutExercise/WorkoutSet) for the identical reason — granular deletes should not
-- require a renumbering transaction. The Web UI must derive any displayed "Week N" / "Set N"
-- number from array index after ordering by position, never from the raw position value itself
-- (mirroring RoutineDetailPage.tsx's own existing `Set {index + 1}` convention).

-- ============================================================================
-- programs
-- ============================================================================

create table if not exists public.programs (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null
    constraint programs_name_not_blank check (length(trim(name)) > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- CASCADE on owner_id, matching routines'/local_records'/profiles' established Postgres-specific
-- precedent (not a mirror of Android's RESTRICT-to-LocalAccount reasoning, which does not apply on
-- this backend — see routines' migration comment for the full justification, unchanged here).
create index if not exists programs_owner_id_idx on public.programs (owner_id);

grant select, insert, update, delete
on table public.programs
to authenticated;

revoke all
on table public.programs
from anon;

alter table public.programs enable row level security;

create policy "select own programs"
on public.programs
for select
to authenticated
using ((select auth.uid()) = owner_id);

create policy "insert own programs"
on public.programs
for insert
to authenticated
with check ((select auth.uid()) = owner_id);

create policy "update own programs"
on public.programs
for update
to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

create policy "delete own programs"
on public.programs
for delete
to authenticated
using ((select auth.uid()) = owner_id);

-- ============================================================================
-- program_weeks
-- ============================================================================

create table if not exists public.program_weeks (
  id uuid primary key default gen_random_uuid(),
  program_id uuid not null references public.programs(id) on delete cascade,
  position integer not null,
  constraint program_weeks_unique_position unique (program_id, position)
);

create index if not exists program_weeks_program_id_idx on public.program_weeks (program_id);

grant select, insert, update, delete
on table public.program_weeks
to authenticated;

revoke all
on table public.program_weeks
from anon;

alter table public.program_weeks enable row level security;

create policy "select own program_weeks"
on public.program_weeks
for select
to authenticated
using (
  exists (
    select 1 from public.programs p
    where p.id = program_weeks.program_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "insert own program_weeks"
on public.program_weeks
for insert
to authenticated
with check (
  exists (
    select 1 from public.programs p
    where p.id = program_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "update own program_weeks"
on public.program_weeks
for update
to authenticated
using (
  exists (
    select 1 from public.programs p
    where p.id = program_weeks.program_id
      and p.owner_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1 from public.programs p
    where p.id = program_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "delete own program_weeks"
on public.program_weeks
for delete
to authenticated
using (
  exists (
    select 1 from public.programs p
    where p.id = program_weeks.program_id
      and p.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- program_sessions
-- ============================================================================

create table if not exists public.program_sessions (
  id uuid primary key default gen_random_uuid(),
  program_week_id uuid not null references public.program_weeks(id) on delete cascade,
  position integer not null,
  -- Optional: ProgramSession.name is deliberately nullable (see the design doc's own decision,
  -- reconfirmed by Web research finding no real competitor problem with unnamed sessions). The UI
  -- falls back to a derived "Session N" (from array index, same as everything else) when null.
  name text,
  -- Pure traceability, never load-bearing for this row's own content (see top-of-file comment).
  -- SET NULL (not RESTRICT): deleting the source Routine must never be blocked by, or destroy, a
  -- ProgramSession that was once copied from it — this is the exact same reasoning the hardened
  -- Android design doc applied to Workout.originRoutineId, re-applied here one layer up.
  source_routine_id uuid references public.routines(id) on delete set null,
  constraint program_sessions_unique_position unique (program_week_id, position)
);

create index if not exists program_sessions_program_week_id_idx on public.program_sessions (program_week_id);
create index if not exists program_sessions_source_routine_id_idx on public.program_sessions (source_routine_id);

grant select, insert, update, delete
on table public.program_sessions
to authenticated;

revoke all
on table public.program_sessions
from anon;

alter table public.program_sessions enable row level security;

create policy "select own program_sessions"
on public.program_sessions
for select
to authenticated
using (
  exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = program_sessions.program_week_id
      and p.owner_id = (select auth.uid())
  )
);

-- The source_routine_id check here closes the same class of gap routine_exercises' exercise-
-- visibility check closes: without it, an authenticated user could set source_routine_id to
-- another user's private routine id (satisfying the FK, since FK checks bypass RLS on the
-- referenced table) purely as a breadcrumb — harmless to that routine's content, but still an
-- ownership-boundary leak (it would let User B assert "this session came from User A's routine
-- <uuid>", which is not User B's business to record). Requiring the caller to own the referenced
-- routine (when one is given) keeps this column truthful.
create policy "insert own program_sessions with valid source routine"
on public.program_sessions
for insert
to authenticated
with check (
  exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = program_week_id
      and p.owner_id = (select auth.uid())
  )
  and (
    source_routine_id is null
    or exists (
      select 1 from public.routines r
      where r.id = source_routine_id
        and r.owner_id = (select auth.uid())
    )
  )
);

create policy "update own program_sessions"
on public.program_sessions
for update
to authenticated
using (
  exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = program_sessions.program_week_id
      and p.owner_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = program_week_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "delete own program_sessions"
on public.program_sessions
for delete
to authenticated
using (
  exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = program_sessions.program_week_id
      and p.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- program_session_exercises
-- ============================================================================

create table if not exists public.program_session_exercises (
  id uuid primary key default gen_random_uuid(),
  program_session_id uuid not null references public.program_sessions(id) on delete cascade,
  -- RESTRICT, matching routine_exercises' exact precedent: an exercise still referenced by a
  -- program session's planned content cannot be deleted out from under it. Deliberately NO
  -- foreign key of any kind back to routine_exercises — see top-of-file comment: this is what
  -- makes the snapshot invariant a structural fact, not a convention.
  --
  -- `text`, not `uuid`: exercises.id was normalized to a stable string identity by
  -- 20260910180000_normalize_exercise_identity.sql (applied before this migration — see that
  -- file for the full reasoning) so built-in exercises carry the same canonical id
  -- (builtin_bench_press, etc.) Android already uses, ahead of any real cross-client sync. This
  -- schema references that canonical type from day one rather than shipping against `uuid` and
  -- requiring a second migration later.
  exercise_id text not null references public.exercises(id) on delete restrict,
  position integer not null,
  constraint program_session_exercises_unique_position unique (program_session_id, position)
);

create index if not exists program_session_exercises_program_session_id_idx
on public.program_session_exercises (program_session_id);
create index if not exists program_session_exercises_exercise_id_idx
on public.program_session_exercises (exercise_id);

grant select, insert, update, delete
on table public.program_session_exercises
to authenticated;

revoke all
on table public.program_session_exercises
from anon;

alter table public.program_session_exercises enable row level security;

create policy "select own program_session_exercises"
on public.program_session_exercises
for select
to authenticated
using (
  exists (
    select 1 from public.program_sessions ps
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where ps.id = program_session_exercises.program_session_id
      and p.owner_id = (select auth.uid())
  )
);

-- Identical reasoning to routine_exercises' own "insert ... with visible exercise" policy: a
-- foreign key alone does not stop this insert from succeeding against another user's private
-- custom exercise (FK checks bypass RLS on the referenced table). This with-check clause is the
-- actual enforcement point for "User A must never be able to attach User B's private custom
-- exercise to a ProgramSession" — exercised for real in this slice's RLS adversarial tests.
create policy "insert own program_session_exercises with visible exercise"
on public.program_session_exercises
for insert
to authenticated
with check (
  exists (
    select 1 from public.program_sessions ps
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where ps.id = program_session_id
      and p.owner_id = (select auth.uid())
  )
  and exists (
    select 1 from public.exercises e
    where e.id = exercise_id
      and (e.owner_id is null or e.owner_id = (select auth.uid()))
  )
);

create policy "update own program_session_exercises with visible exercise"
on public.program_session_exercises
for update
to authenticated
using (
  exists (
    select 1 from public.program_sessions ps
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where ps.id = program_session_exercises.program_session_id
      and p.owner_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1 from public.program_sessions ps
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where ps.id = program_session_id
      and p.owner_id = (select auth.uid())
  )
  and exists (
    select 1 from public.exercises e
    where e.id = exercise_id
      and (e.owner_id is null or e.owner_id = (select auth.uid()))
  )
);

create policy "delete own program_session_exercises"
on public.program_session_exercises
for delete
to authenticated
using (
  exists (
    select 1 from public.program_sessions ps
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where ps.id = program_session_exercises.program_session_id
      and p.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- program_session_planned_sets
-- ============================================================================

create table if not exists public.program_session_planned_sets (
  id uuid primary key default gen_random_uuid(),
  program_session_exercise_id uuid not null references public.program_session_exercises(id) on delete cascade,
  position integer not null,
  -- Planning TARGET values only — no execution/Workout model exists on Web in this slice at all.
  -- Deliberately NO foreign key back to routine_planned_sets (see top-of-file comment).
  target_reps integer
    constraint program_session_planned_sets_target_reps_non_negative check (target_reps is null or target_reps >= 0),
  target_weight numeric
    constraint program_session_planned_sets_target_weight_non_negative check (target_weight is null or target_weight >= 0),
  constraint program_session_planned_sets_unique_position unique (program_session_exercise_id, position)
);

create index if not exists program_session_planned_sets_pse_id_idx
on public.program_session_planned_sets (program_session_exercise_id);

grant select, insert, update, delete
on table public.program_session_planned_sets
to authenticated;

revoke all
on table public.program_session_planned_sets
from anon;

alter table public.program_session_planned_sets enable row level security;

-- Ownership here is FOUR levels removed from programs.owner_id (planned_set -> exercise ->
-- session -> week -> program) — the same "no redundant owner_id on a transitively-owned child"
-- decision the design document made for the Android side, re-applied at the Postgres/RLS layer via
-- a four-table join in each policy below. Kept deliberately explicit (not hidden behind a view or
-- security-definer helper) so each policy remains independently auditable.
create policy "select own program_session_planned_sets"
on public.program_session_planned_sets
for select
to authenticated
using (
  exists (
    select 1
    from public.program_session_exercises pse
    join public.program_sessions ps on ps.id = pse.program_session_id
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where pse.id = program_session_planned_sets.program_session_exercise_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "insert own program_session_planned_sets"
on public.program_session_planned_sets
for insert
to authenticated
with check (
  exists (
    select 1
    from public.program_session_exercises pse
    join public.program_sessions ps on ps.id = pse.program_session_id
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where pse.id = program_session_exercise_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "update own program_session_planned_sets"
on public.program_session_planned_sets
for update
to authenticated
using (
  exists (
    select 1
    from public.program_session_exercises pse
    join public.program_sessions ps on ps.id = pse.program_session_id
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where pse.id = program_session_planned_sets.program_session_exercise_id
      and p.owner_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1
    from public.program_session_exercises pse
    join public.program_sessions ps on ps.id = pse.program_session_id
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where pse.id = program_session_exercise_id
      and p.owner_id = (select auth.uid())
  )
);

create policy "delete own program_session_planned_sets"
on public.program_session_planned_sets
for delete
to authenticated
using (
  exists (
    select 1
    from public.program_session_exercises pse
    join public.program_sessions ps on ps.id = pse.program_session_id
    join public.program_weeks pw on pw.id = ps.program_week_id
    join public.programs p on p.id = pw.program_id
    where pse.id = program_session_planned_sets.program_session_exercise_id
      and p.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- add_program_week: append one empty week to a program
-- ============================================================================

create or replace function public.add_program_week(p_program_id uuid)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_position integer;
  v_week_id uuid;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  if not exists (
    select 1 from public.programs p
    where p.id = p_program_id and p.owner_id = v_uid
  ) then
    raise exception 'program not found or not owned by the current user';
  end if;

  select coalesce(max(position), -1) + 1 into v_position
  from public.program_weeks
  where program_id = p_program_id;

  insert into public.program_weeks (program_id, position)
  values (p_program_id, v_position)
  returning id into v_week_id;

  return v_week_id;
end;
$$;

revoke all on function public.add_program_week(uuid) from public;
grant execute on function public.add_program_week(uuid) to authenticated;

-- ============================================================================
-- add_program_session: append one empty (from-scratch) session to a week
-- ============================================================================

create or replace function public.add_program_session(p_program_week_id uuid, p_name text default null)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_position integer;
  v_session_id uuid;
  v_name text;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  if not exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = p_program_week_id and p.owner_id = v_uid
  ) then
    raise exception 'program week not found or not owned by the current user';
  end if;

  v_name := nullif(trim(coalesce(p_name, '')), '');

  select coalesce(max(position), -1) + 1 into v_position
  from public.program_sessions
  where program_week_id = p_program_week_id;

  insert into public.program_sessions (program_week_id, position, name)
  values (p_program_week_id, v_position, v_name)
  returning id into v_session_id;

  return v_session_id;
end;
$$;

revoke all on function public.add_program_session(uuid, text) from public;
grant execute on function public.add_program_session(uuid, text) to authenticated;

-- ============================================================================
-- copy_routine_to_program_session: the MUST-HAVE V1 copy operation
-- ============================================================================

-- Reads the caller's routine content ONCE and writes independent program_session_exercises/
-- program_session_planned_sets rows — this is the actual mechanism that makes the snapshot
-- invariant true (see top-of-file comment). SECURITY INVOKER (the default, stated explicitly):
-- every select/insert below is still checked against the RLS policies above, running as the
-- calling user via auth.uid() — including the exercise-visibility recheck on
-- program_session_exercises inserts. A malicious call against another user's routine or program
-- week fails the ownership checks below before any row is written, and the whole transaction
-- rolls back on any exception (implicit function-body transaction semantics).
create or replace function public.copy_routine_to_program_session(
  p_routine_id uuid,
  p_program_week_id uuid
)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_routine_name text;
  v_position integer;
  v_session_id uuid;
  v_routine_exercise record;
  v_new_pse_id uuid;
  v_planned_set record;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  select name into v_routine_name
  from public.routines
  where id = p_routine_id and owner_id = v_uid;

  if v_routine_name is null then
    raise exception 'routine not found or not owned by the current user';
  end if;

  if not exists (
    select 1 from public.program_weeks pw
    join public.programs p on p.id = pw.program_id
    where pw.id = p_program_week_id and p.owner_id = v_uid
  ) then
    raise exception 'program week not found or not owned by the current user';
  end if;

  select coalesce(max(position), -1) + 1 into v_position
  from public.program_sessions
  where program_week_id = p_program_week_id;

  -- The session's name is seeded from the routine's name at copy time (e.g. "Push A") — a
  -- one-time snapshot of the name too, exactly like its exercises/sets. This is still a copy, not
  -- a live reference: renaming the routine afterward never renames this session.
  insert into public.program_sessions (program_week_id, position, name, source_routine_id)
  values (p_program_week_id, v_position, v_routine_name, p_routine_id)
  returning id into v_session_id;

  for v_routine_exercise in
    select id, exercise_id, position
    from public.routine_exercises
    where routine_id = p_routine_id
    order by position
  loop
    insert into public.program_session_exercises (program_session_id, exercise_id, position)
    values (v_session_id, v_routine_exercise.exercise_id, v_routine_exercise.position)
    returning id into v_new_pse_id;

    for v_planned_set in
      select position, target_reps, target_weight
      from public.routine_planned_sets
      where routine_exercise_id = v_routine_exercise.id
      order by position
    loop
      insert into public.program_session_planned_sets (program_session_exercise_id, position, target_reps, target_weight)
      values (v_new_pse_id, v_planned_set.position, v_planned_set.target_reps, v_planned_set.target_weight);
    end loop;
  end loop;

  return v_session_id;
end;
$$;

revoke all on function public.copy_routine_to_program_session(uuid, uuid) from public;
grant execute on function public.copy_routine_to_program_session(uuid, uuid) to authenticated;

-- ============================================================================
-- duplicate_program_week: research-identified high-value V1 action
-- ============================================================================

-- Boostcamp and TrainHeroic (see docs/product/web-routine-program-planning-research.md) both treat
-- week duplication as a named, first-class feature specifically to solve "12 weeks of mostly-
-- repeated content" without painful manual re-entry. Produces a fully independent copy: new week,
-- new session rows, new exercise rows, new planned-set rows throughout — editing the duplicate
-- afterward has zero effect on the original, by the same "write independent rows once" mechanism
-- copy_routine_to_program_session uses above, not a shared/referenced substructure.
create or replace function public.duplicate_program_week(p_program_week_id uuid)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_program_id uuid;
  v_position integer;
  v_new_week_id uuid;
  v_session record;
  v_new_session_id uuid;
  v_pse record;
  v_new_pse_id uuid;
  v_set record;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  select p.id into v_program_id
  from public.program_weeks pw
  join public.programs p on p.id = pw.program_id
  where pw.id = p_program_week_id and p.owner_id = v_uid;

  if v_program_id is null then
    raise exception 'program week not found or not owned by the current user';
  end if;

  select coalesce(max(position), -1) + 1 into v_position
  from public.program_weeks
  where program_id = v_program_id;

  insert into public.program_weeks (program_id, position)
  values (v_program_id, v_position)
  returning id into v_new_week_id;

  for v_session in
    select id, position, name, source_routine_id
    from public.program_sessions
    where program_week_id = p_program_week_id
    order by position
  loop
    -- source_routine_id is carried through unchanged: the duplicate's sessions were, transitively,
    -- also originally copied from that same routine (if any) — this is still just a breadcrumb,
    -- re-validated by the same insert policy as any other program_sessions insert.
    insert into public.program_sessions (program_week_id, position, name, source_routine_id)
    values (v_new_week_id, v_session.position, v_session.name, v_session.source_routine_id)
    returning id into v_new_session_id;

    for v_pse in
      select id, exercise_id, position
      from public.program_session_exercises
      where program_session_id = v_session.id
      order by position
    loop
      insert into public.program_session_exercises (program_session_id, exercise_id, position)
      values (v_new_session_id, v_pse.exercise_id, v_pse.position)
      returning id into v_new_pse_id;

      for v_set in
        select position, target_reps, target_weight
        from public.program_session_planned_sets
        where program_session_exercise_id = v_pse.id
        order by position
      loop
        insert into public.program_session_planned_sets (program_session_exercise_id, position, target_reps, target_weight)
        values (v_new_pse_id, v_set.position, v_set.target_reps, v_set.target_weight);
      end loop;
    end loop;
  end loop;

  return v_new_week_id;
end;
$$;

revoke all on function public.duplicate_program_week(uuid) from public;
grant execute on function public.duplicate_program_week(uuid) to authenticated;

-- ============================================================================
-- save_program_session: the from-scratch/edit path for ONE session's content
-- ============================================================================

-- Full-replace strategy scoped to a single session, exactly mirroring save_routine()'s own
-- approach (delete the session's current exercises — cascades to their planned sets — then
-- reinsert the caller's complete current list with fresh, contiguous positions). This matches the
-- Web session editor's own UX: the user holds one session's full draft content in memory and saves
-- it in one action, the same pattern RoutineEditorPage.tsx already uses for a whole Routine. Unlike
-- routines' migration, positions elsewhere in this schema (weeks, sessions, and this function's own
-- exercises-within-a-session) are not required to be globally contiguous across the whole program —
-- only this function's own delete-then-reinsert-with-fresh-0..n-1 guarantees contiguity WITHIN the
-- one session it touches, which is all that's needed here.
create or replace function public.save_program_session(
  p_program_session_id uuid,
  p_name text,
  p_exercises jsonb
)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_session_id uuid;
  v_exercise jsonb;
  v_planned_set jsonb;
  v_pse_id uuid;
  v_exercise_position integer := 0;
  v_set_position integer;
  v_name text;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  select ps.id into v_session_id
  from public.program_sessions ps
  join public.program_weeks pw on pw.id = ps.program_week_id
  join public.programs p on p.id = pw.program_id
  where ps.id = p_program_session_id and p.owner_id = v_uid;

  if v_session_id is null then
    raise exception 'program session not found or not owned by the current user';
  end if;

  v_name := nullif(trim(coalesce(p_name, '')), '');

  update public.program_sessions
  set name = v_name
  where id = v_session_id;

  delete from public.program_session_exercises where program_session_id = v_session_id;

  for v_exercise in select * from jsonb_array_elements(coalesce(p_exercises, '[]'::jsonb))
  loop
    insert into public.program_session_exercises (program_session_id, exercise_id, position)
    values (v_session_id, (v_exercise ->> 'exerciseId'), v_exercise_position)
    returning id into v_pse_id;

    v_set_position := 0;
    for v_planned_set in select * from jsonb_array_elements(coalesce(v_exercise -> 'plannedSets', '[]'::jsonb))
    loop
      insert into public.program_session_planned_sets (program_session_exercise_id, position, target_reps, target_weight)
      values (
        v_pse_id,
        v_set_position,
        nullif(v_planned_set ->> 'targetReps', '')::integer,
        nullif(v_planned_set ->> 'targetWeight', '')::numeric
      );
      v_set_position := v_set_position + 1;
    end loop;

    v_exercise_position := v_exercise_position + 1;
  end loop;

  return v_session_id;
end;
$$;

revoke all on function public.save_program_session(uuid, text, jsonb) from public;
grant execute on function public.save_program_session(uuid, text, jsonb) to authenticated;
