-- Pre-production Exercise identity normalization — see
-- docs/architecture/cross-client-identity-sync-research.md (RESEARCH / ARCHITECTURE
-- INVESTIGATION — NOT A DECISION) for the full reasoning this migration implements. Runs strictly
-- between routines (20260910120000_create_routines.sql, already applied) and programs
-- (20260911120000_create_programs.sql, NOT yet applied) — the Program migration is edited in place
-- to declare `program_session_exercises.exercise_id` as the canonical `text` identity established
-- here from day one, so Program never needs a second identity migration later.
--
-- THE MISMATCH THIS FIXES: 20260910120000_create_routines.sql seeded six built-in exercises with
-- fresh, random `gen_random_uuid()` values — a separate identity space from Android's own stable,
-- hand-assigned string IDs (see android/phone/src/main/kotlin/com/tbdfit/phone/workout/
-- BuiltInExerciseCatalog.kt's BUILT_IN_EXERCISE_SEED: builtin_bench_press, builtin_back_squat,
-- builtin_deadlift, builtin_overhead_press, builtin_barbell_row, builtin_pull_up). Before any
-- cross-client sync exists this is harmless; once sync is built, "the same Bench Press" must
-- resolve to the same id on both clients without a permanent runtime mapping table living forever.
-- This migration adopts Android's already-stable string IDs as the single canonical identity for
-- built-ins, pre-production, while doing so is still cheap (no real user has ever seen or stored
-- the old random values) — see that research document's CURRENT DEVELOPMENT-DATA MIGRATION
-- OPTIONS section for why "normalize now" beats "build a mapping layer to carry forward forever".
--
-- STRATEGY: `exercises.id` changes from `uuid` to `text`. Built-in rows are re-keyed to their
-- canonical `builtin_*` string, matched by name — safe because there are exactly six, explicitly
-- verified below (the migration aborts loudly rather than guessing if an unrecognized built-in row
-- is ever found, e.g. because the seed list changed since this file was written). Existing custom
-- exercises keep their existing UUID *value*, merely retyped to text — no data loss, no identity
-- change for any user-owned row. New custom exercises continue to get a fresh random UUID,
-- generated as text via the column's own default. `routine_exercises.exercise_id` is remapped in
-- lockstep so no foreign key or Routine content is lost: a row that referenced an old built-in-uuid
-- row now references the same exercise by its new canonical string id; a row that referenced a
-- custom exercise is untouched in meaning (same value, new column type only).
--
-- The name->canonical-id correlation used below lives in a temporary table that exists only for
-- the duration of this migration's own transaction (`on commit drop`) — this is explicitly NOT a
-- permanent mapping layer (the research document recommends against exactly that): it is scratch
-- space for a single one-time backfill, gone the moment this migration finishes.
--
-- Table-recreate technique (not a series of ALTER COLUMN TYPE statements on the live PK): a
-- primary key referenced by another table's foreign key, and referenced by other tables' RLS
-- policies via subqueries, cannot have its column dropped/retyped in place without first dropping
-- every dependent object (the FK, and — verified empirically against a real local Postgres 16
-- instance while authoring this migration — the two `routine_exercises` policies that reference
-- `exercises` in an EXISTS subquery; Postgres registers these as real dependencies, not just an
-- informal convention). This migration therefore: builds the new `exercises` table under a
-- temporary name, drops the two dependent `routine_exercises` policies and the old `exercises`
-- table (CASCADE, which also drops its own two policies and the FK — all recreated below), renames
-- the new table into place, and recreates every policy/FK/index that existed before. Every
-- recreated policy is byte-for-byte the same USING/WITH CHECK logic as
-- 20260910120000_create_routines.sql already established — only the column *type* changed, never
-- the ownership/visibility semantics.

begin;

create temporary table _exercise_id_backfill (
  old_id uuid primary key,
  new_id text not null
) on commit drop;

insert into _exercise_id_backfill (old_id, new_id)
select id, case name
  when 'Bench Press' then 'builtin_bench_press'
  when 'Back Squat' then 'builtin_back_squat'
  when 'Deadlift' then 'builtin_deadlift'
  when 'Overhead Press' then 'builtin_overhead_press'
  when 'Barbell Row' then 'builtin_barbell_row'
  when 'Pull-Up' then 'builtin_pull_up'
end
from public.exercises
where owner_id is null;

do $$
begin
  if exists (
    select 1 from public.exercises
    where owner_id is null
      and id not in (select old_id from _exercise_id_backfill)
  ) then
    raise exception 'unrecognized built-in exercise row found — update this migration''s name mapping before proceeding';
  end if;
end $$;

-- ============================================================================
-- exercises: uuid -> text, built-ins re-keyed to their canonical Android-matching string id
-- ============================================================================

create table public.exercises_new (
  id text primary key default (gen_random_uuid())::text,
  name text not null
    constraint exercises_name_not_blank check (length(trim(name)) > 0),
  owner_id uuid references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

insert into public.exercises_new (id, name, owner_id, created_at)
select coalesce(b.new_id, e.id::text), e.name, e.owner_id, e.created_at
from public.exercises e
left join _exercise_id_backfill b on b.old_id = e.id;

-- ============================================================================
-- routine_exercises.exercise_id: uuid -> text, remapped in lockstep with the above
-- ============================================================================

alter table public.routine_exercises add column exercise_id_new text;

update public.routine_exercises re
set exercise_id_new = coalesce(
  (select b.new_id from _exercise_id_backfill b where b.old_id = re.exercise_id),
  re.exercise_id::text
);

alter table public.routine_exercises alter column exercise_id_new set not null;

-- These two policies reference `exercises` (via EXISTS) and are real, tracked dependents of the
-- old exercise_id column (confirmed empirically — see top-of-file comment) — drop them before the
-- column swap, recreate them verbatim afterward against the new text-typed columns on both sides.
drop policy "insert own routine_exercises with visible exercise" on public.routine_exercises;
drop policy "update own routine_exercises with visible exercise" on public.routine_exercises;

alter table public.routine_exercises drop column exercise_id;
alter table public.routine_exercises rename column exercise_id_new to exercise_id;

-- Drops the old exercises table, its own two policies, and the routine_exercises_exercise_id_fkey
-- FK constraint (all recreated below against the new table/column types).
drop table public.exercises cascade;

alter table public.exercises_new rename to exercises;
alter table public.exercises rename constraint exercises_new_pkey to exercises_pkey;
alter table public.exercises rename constraint exercises_new_owner_id_fkey to exercises_owner_id_fkey;

create index exercises_owner_id_idx on public.exercises (owner_id);

grant select, insert
on table public.exercises
to authenticated;

revoke all
on table public.exercises
from anon;

alter table public.exercises enable row level security;

create policy "select visible exercises"
on public.exercises
for select
to authenticated
using (owner_id is null or owner_id = (select auth.uid()));

create policy "insert own custom exercise"
on public.exercises
for insert
to authenticated
with check (owner_id = (select auth.uid()));

alter table public.routine_exercises
  add constraint routine_exercises_exercise_id_fkey
  foreign key (exercise_id) references public.exercises(id) on delete restrict;

create index if not exists routine_exercises_exercise_id_idx on public.routine_exercises (exercise_id);

create policy "insert own routine_exercises with visible exercise"
on public.routine_exercises
for insert
to authenticated
with check (
  exists (
    select 1 from public.routines r
    where r.id = routine_id
      and r.owner_id = (select auth.uid())
  )
  and exists (
    select 1 from public.exercises e
    where e.id = exercise_id
      and (e.owner_id is null or e.owner_id = (select auth.uid()))
  )
);

create policy "update own routine_exercises with visible exercise"
on public.routine_exercises
for update
to authenticated
using (
  exists (
    select 1 from public.routines r
    where r.id = routine_exercises.routine_id
      and r.owner_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1 from public.routines r
    where r.id = routine_id
      and r.owner_id = (select auth.uid())
  )
  and exists (
    select 1 from public.exercises e
    where e.id = exercise_id
      and (e.owner_id is null or e.owner_id = (select auth.uid()))
  )
);

-- ============================================================================
-- save_routine: re-declared only because the exercise_id payload is no longer cast to ::uuid
-- ============================================================================

-- Identical to 20260910120000_create_routines.sql's version in every respect except one line: the
-- exerciseId payload value is used directly as text (no `::uuid` cast) since exercises.id is now
-- `text`, not `uuid`. See that migration's own comment for the full atomicity/security-invoker
-- reasoning, unchanged here.
create or replace function public.save_routine(
  p_routine_id uuid,
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
  v_routine_id uuid;
  v_exercise jsonb;
  v_planned_set jsonb;
  v_routine_exercise_id uuid;
  v_exercise_position integer := 0;
  v_set_position integer;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  if p_name is null or length(trim(p_name)) = 0 then
    raise exception 'routine name must not be empty';
  end if;

  if p_routine_id is null then
    insert into public.routines (owner_id, name)
    values (v_uid, trim(p_name))
    returning id into v_routine_id;
  else
    update public.routines
    set name = trim(p_name), updated_at = now()
    where id = p_routine_id and owner_id = v_uid
    returning id into v_routine_id;

    if v_routine_id is null then
      raise exception 'routine not found or not owned by the current user';
    end if;

    delete from public.routine_exercises where routine_id = v_routine_id;
  end if;

  for v_exercise in select * from jsonb_array_elements(coalesce(p_exercises, '[]'::jsonb))
  loop
    insert into public.routine_exercises (routine_id, exercise_id, position)
    values (v_routine_id, (v_exercise ->> 'exerciseId'), v_exercise_position)
    returning id into v_routine_exercise_id;

    v_set_position := 0;
    for v_planned_set in select * from jsonb_array_elements(coalesce(v_exercise -> 'plannedSets', '[]'::jsonb))
    loop
      insert into public.routine_planned_sets (routine_exercise_id, position, target_reps, target_weight)
      values (
        v_routine_exercise_id,
        v_set_position,
        nullif(v_planned_set ->> 'targetReps', '')::integer,
        nullif(v_planned_set ->> 'targetWeight', '')::numeric
      );
      v_set_position := v_set_position + 1;
    end loop;

    v_exercise_position := v_exercise_position + 1;
  end loop;

  return v_routine_id;
end;
$$;

revoke all on function public.save_routine(uuid, text, jsonb) from public;
grant execute on function public.save_routine(uuid, text, jsonb) to authenticated;

commit;
