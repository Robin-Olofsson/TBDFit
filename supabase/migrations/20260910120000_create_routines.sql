-- routines: the first REAL, centrally-persisted TBDFit product domain table (not a disposable
-- technical proof like local_records, and not mirrored mechanically from Android's Room schema —
-- see docs/architecture/program-routine-first-slice-design.md for the domain reasoning this
-- schema is *informed by*, and the strategic pivot notes in this migration's own commit for why
-- the Postgres shape below is not a 1:1 translation of the Room entities).
--
-- Product invariants preserved from the existing domain research/design work:
--   - Routine is standalone: it never requires a Program (Program does not exist in this schema
--     at all yet — that is a deliberately separate, later vertical slice).
--   - Routine -> RoutineExercise -> RoutinePlannedSet is planning/intent data, never execution
--     history. There is no Workout table here — Web does not execute workouts in this slice (see
--     multi-client-product-vision.md: whether Web ever executes at all remains an explicitly open
--     product question, not resolved by this migration).
--   - One owner per Routine. No Folder/Collection concept.
--
-- Exercise identity (see docs/architecture/program-routine-first-slice-design.md's Exercise
-- Identity reasoning, adapted here rather than copied): a RoutineExercise references a stable
-- `exercises.id`, never a bare exercise name string, so a rename never breaks history and identity
-- never depends on display text. Built-in exercises have `owner_id = NULL` and are visible to every
-- authenticated user; a custom exercise has a real `owner_id` and is visible only to its owner.
-- This mirrors Android's BUILT_IN/CUSTOM + nullable-ownerId pattern conceptually, but uses fresh
-- Postgres-native UUIDs — these are a SEPARATE identity space from Android's `builtin_bench_press`
-- -style string IDs for now. There is no cross-client sync yet (Android's Room database and this
-- Postgres schema are two independent, unconnected persistence stores as of this migration) so
-- forcing the ID spaces to match would be a false consistency with no actual behavior behind it.
--
-- Ordering: RoutineExercise.position and RoutinePlannedSet.position are real, explicit ordering
-- columns (never insertion order). Unlike Android's Room design (which chose sparse,
-- never-compacted positions specifically to avoid a renumbering transaction on every single
-- granular delete), this schema enforces `unique (parent_id, position)` and expects positions to
-- always be contiguous 0..n-1. This is the correct, simpler choice here because every write in this
-- slice goes through save_routine(...) below, which replaces a Routine's entire exercise/set content
-- in one transaction and therefore always writes fresh, contiguous positions — there is no granular
-- single-row position-shifting operation in this design that sparse positions exist to make cheap.
-- If a future granular (non-full-replace) mutation path is added, this constraint should be
-- revisited then, not preemptively relaxed now.
--
-- Security invariant enforced below (mirrors local_records'/profiles' established pattern, extended
-- for a two-level-deep owned child hierarchy that has no owner_id column of its own):
--   - unauthenticated/public requests must not expose any row in these four tables (except the
--     built-in, globally-visible exercises rows, which are visible to authenticated users only —
--     there is deliberately no anon/public SELECT policy at all, same as profiles).
--   - authenticated users may only read/write their own routines and the planning children owned
--     transitively through them.
--   - CRITICAL, easy-to-miss gap this migration explicitly closes: Postgres foreign key constraints
--     are checked against the underlying table, not through the referencing role's RLS view of it —
--     an INSERT into routine_exercises with a fabricated exercise_id UUID belonging to another
--     user's private custom exercise would otherwise succeed structurally (the FK is satisfied)
--     even though the inserting user could never SELECT that exercise. The INSERT/UPDATE policies
--     on routine_exercises below explicitly re-check exercise visibility inside their `with check`
--     clause for exactly this reason — FK integrity alone is not ownership isolation.

-- ============================================================================
-- exercises
-- ============================================================================

create table if not exists public.exercises (
  id uuid primary key default gen_random_uuid(),
  name text not null
    constraint exercises_name_not_blank check (length(trim(name)) > 0),
  owner_id uuid references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

-- ON DELETE CASCADE on owner_id (not RESTRICT, unlike Android's Exercise.ownerId -> LocalAccount):
-- this is a deliberate Postgres-specific choice, not a mechanical mirror of the Room design. Every
-- existing Supabase migration in this repository (local_records, profiles) already uses
-- `... references auth.users(id) on delete cascade` for a user-owned row — when a Supabase Auth
-- user is deleted, their owned product data disappearing with them is the established, correct
-- behavior for this backend, and there is no equivalent to Android's "LocalAccount can never
-- practically be deleted through the app" reasoning here (auth.users deletion is a real, supported
-- Supabase operation, e.g. via account deletion requests).
create index if not exists exercises_owner_id_idx on public.exercises (owner_id);

grant select, insert
on table public.exercises
to authenticated;

revoke all
on table public.exercises
from anon;

alter table public.exercises enable row level security;

-- A built-in exercise (owner_id is null) is visible to everyone; a custom exercise is visible only
-- to the account that owns it.
create policy "select visible exercises"
on public.exercises
for select
to authenticated
using (owner_id is null or owner_id = (select auth.uid()));

-- A normal authenticated user may only ever create a CUSTOM exercise owned by themselves — they can
-- never insert a new built-in (owner_id null) row through this policy. Built-ins are seeded once,
-- below, by this migration itself.
create policy "insert own custom exercise"
on public.exercises
for insert
to authenticated
with check (owner_id = (select auth.uid()));

-- No update/delete policy on exercises in this slice: renaming/deleting a custom exercise is not a
-- required V1 capability (see the design document's "do not overbuild" guidance) — deliberate
-- default-deny, not an oversight, same precedent as local_records'/profiles' missing delete policy.

insert into public.exercises (name, owner_id) values
  ('Bench Press', null),
  ('Back Squat', null),
  ('Deadlift', null),
  ('Overhead Press', null),
  ('Barbell Row', null),
  ('Pull-Up', null);

-- ============================================================================
-- routines
-- ============================================================================

create table if not exists public.routines (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null
    constraint routines_name_not_blank check (length(trim(name)) > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists routines_owner_id_idx on public.routines (owner_id);

grant select, insert, update, delete
on table public.routines
to authenticated;

revoke all
on table public.routines
from anon;

alter table public.routines enable row level security;

create policy "select own routines"
on public.routines
for select
to authenticated
using ((select auth.uid()) = owner_id);

create policy "insert own routines"
on public.routines
for insert
to authenticated
with check ((select auth.uid()) = owner_id);

create policy "update own routines"
on public.routines
for update
to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

-- A real delete policy (unlike local_records/profiles, which deliberately have none yet): deleting
-- a Routine the user owns is an explicit, required V1 capability (see the product goal's
-- "[ Delete ]" affordance).
create policy "delete own routines"
on public.routines
for delete
to authenticated
using ((select auth.uid()) = owner_id);

-- ============================================================================
-- routine_exercises
-- ============================================================================

create table if not exists public.routine_exercises (
  id uuid primary key default gen_random_uuid(),
  routine_id uuid not null references public.routines(id) on delete cascade,
  exercise_id uuid not null references public.exercises(id) on delete restrict,
  position integer not null,
  constraint routine_exercises_unique_position unique (routine_id, position)
);

-- CASCADE from routines: a genuinely owned child, no historical Workout dependency exists on Web
-- yet (Program/execution are out of this slice's scope entirely). RESTRICT on exercise_id: an
-- exercise still referenced by a Routine's planned content cannot be deleted out from under it —
-- moot for built-ins today (no delete policy exists on exercises at all yet), but load-bearing the
-- moment custom-exercise deletion is ever added.
create index if not exists routine_exercises_routine_id_idx on public.routine_exercises (routine_id);
create index if not exists routine_exercises_exercise_id_idx on public.routine_exercises (exercise_id);

grant select, insert, update, delete
on table public.routine_exercises
to authenticated;

revoke all
on table public.routine_exercises
from anon;

alter table public.routine_exercises enable row level security;

create policy "select own routine_exercises"
on public.routine_exercises
for select
to authenticated
using (
  exists (
    select 1 from public.routines r
    where r.id = routine_exercises.routine_id
      and r.owner_id = (select auth.uid())
  )
);

-- See this migration's top-of-file comment: the exercise-visibility re-check here is not
-- redundant with exercises' own RLS — a foreign key constraint alone would let this INSERT succeed
-- against another user's private custom exercise, since FK checks bypass RLS on the referenced
-- table. This `with check` clause is the actual enforcement point for "User A must never be able to
-- attach User B's custom exercise to a Routine."
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

create policy "delete own routine_exercises"
on public.routine_exercises
for delete
to authenticated
using (
  exists (
    select 1 from public.routines r
    where r.id = routine_exercises.routine_id
      and r.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- routine_planned_sets
-- ============================================================================

create table if not exists public.routine_planned_sets (
  id uuid primary key default gen_random_uuid(),
  routine_exercise_id uuid not null references public.routine_exercises(id) on delete cascade,
  position integer not null,
  -- Planning TARGET values only, never actual performed results (no Workout/execution model
  -- exists in this slice at all) — nullable because a set may specify only a rep target, only a
  -- load target, or neither (e.g. a bodyweight movement with no target reps recorded yet).
  target_reps integer
    constraint routine_planned_sets_target_reps_non_negative check (target_reps is null or target_reps >= 0),
  target_weight numeric
    constraint routine_planned_sets_target_weight_non_negative check (target_weight is null or target_weight >= 0),
  constraint routine_planned_sets_unique_position unique (routine_exercise_id, position)
);

create index if not exists routine_planned_sets_routine_exercise_id_idx
on public.routine_planned_sets (routine_exercise_id);

grant select, insert, update, delete
on table public.routine_planned_sets
to authenticated;

revoke all
on table public.routine_planned_sets
from anon;

alter table public.routine_planned_sets enable row level security;

-- Ownership here is two levels removed from routines (routine_planned_sets -> routine_exercises ->
-- routines) — the same "no redundant owner_id on a transitively-owned child" decision the Android
-- design document made, re-applied at the Postgres/RLS layer via a two-table join in each policy.
create policy "select own routine_planned_sets"
on public.routine_planned_sets
for select
to authenticated
using (
  exists (
    select 1
    from public.routine_exercises re
    join public.routines r on r.id = re.routine_id
    where re.id = routine_planned_sets.routine_exercise_id
      and r.owner_id = (select auth.uid())
  )
);

create policy "insert own routine_planned_sets"
on public.routine_planned_sets
for insert
to authenticated
with check (
  exists (
    select 1
    from public.routine_exercises re
    join public.routines r on r.id = re.routine_id
    where re.id = routine_exercise_id
      and r.owner_id = (select auth.uid())
  )
);

create policy "update own routine_planned_sets"
on public.routine_planned_sets
for update
to authenticated
using (
  exists (
    select 1
    from public.routine_exercises re
    join public.routines r on r.id = re.routine_id
    where re.id = routine_planned_sets.routine_exercise_id
      and r.owner_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1
    from public.routine_exercises re
    join public.routines r on r.id = re.routine_id
    where re.id = routine_exercise_id
      and r.owner_id = (select auth.uid())
  )
);

create policy "delete own routine_planned_sets"
on public.routine_planned_sets
for delete
to authenticated
using (
  exists (
    select 1
    from public.routine_exercises re
    join public.routines r on r.id = re.routine_id
    where re.id = routine_planned_sets.routine_exercise_id
      and r.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- save_routine: the atomic full-content save operation
-- ============================================================================

-- Why an RPC instead of several independent browser writes: PostgREST/supabase-js has no general
-- multi-table transaction primitive callable from the browser, so "create/edit a Routine with its
-- exercises and sets" cannot be made atomic as a sequence of separate .insert()/.update() calls —
-- a network failure partway through would leave a half-written prescription. A Postgres function
-- runs as one implicit transaction, giving real atomicity for free. This function is intentionally
-- narrow and domain-specific (it knows exactly the Routine/RoutineExercise/RoutinePlannedSet shape),
-- not a generic "apply this JSON mutation" engine.
--
-- Strategy: full replace, not incremental patch. Every save deletes the routine's existing
-- routine_exercises (which cascades to routine_planned_sets) and reinserts the caller's complete
-- current exercise/set list with fresh, contiguous positions. This is what makes the
-- unique(parent_id, position) constraints above trivially satisfiable on every save, and it exactly
-- matches how the Web editor UI holds a routine's full content as in-memory draft state before one
-- Save action — there is no granular "move this one set" network call in this slice's UI to support
-- a more surgical (and more complex) incremental update strategy.
--
-- SECURITY INVOKER (the default — stated explicitly for clarity): this function does NOT bypass
-- RLS. Every insert/update it performs is still checked against the policies above, running as the
-- calling user via auth.uid() — including the exercise-visibility check on routine_exercises
-- inserts. A malicious payload referencing another user's private exercise_id fails the RLS check
-- inside this function exactly as it would from a direct insert, and the whole transaction rolls
-- back. `set search_path` pins name resolution to `public` (and the safe, empty `pg_temp`) so this
-- function cannot be tricked by a session-level search_path change into resolving an
-- attacker-controlled object instead of the real public.routines etc.
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
      -- Either the routine does not exist or the caller does not own it. RLS would have blocked
      -- the update anyway; this NULL check turns that into a clear application-level error rather
      -- than a silent no-op that looks like a successful save.
      raise exception 'routine not found or not owned by the current user';
    end if;

    -- Full-replace strategy: children are recreated fresh below. CASCADE removes the planned sets
    -- along with their parent routine_exercises rows.
    delete from public.routine_exercises where routine_id = v_routine_id;
  end if;

  for v_exercise in select * from jsonb_array_elements(coalesce(p_exercises, '[]'::jsonb))
  loop
    insert into public.routine_exercises (routine_id, exercise_id, position)
    values (v_routine_id, (v_exercise ->> 'exerciseId')::uuid, v_exercise_position)
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
