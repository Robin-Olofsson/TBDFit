-- Completed Workout History — the first Supabase table family for TBDFit's execution domain.
-- Represents ONLY finished historical execution records. There is no ACTIVE/in-progress remote
-- state here, no scheduling, and no execution UI is built on top of this in Web — see
-- docs/development/supabase-setup-and-verification.md's "Completed Workout History" section (added
-- alongside this migration) for the full client-responsibility split this establishes:
--   Web            = planning (Routine/Program) + reads of this history (statistics/calendar/detail)
--   Native Phone/Watch (later) = workout execution, produces the rows this migration defines
--   Supabase       = durable, client-agnostic completed-history storage
--
-- CLIENT-AGNOSTIC BY DESIGN: nothing below distinguishes "Web" from "Phone" — Supabase's
-- `authenticated` role means "a signed-in TBDFit account of any client," never "the Web app"
-- specifically. The boundary that Web must not create/execute workouts is a Web PRODUCT decision
-- (no mutation hook, no UI, enforced by what Web's own code simply never calls), not a database
-- authorization rule — a future native client authenticates as the exact same role and is expected
-- to use the write RPC below. Do not ever encode "if caller is Web" logic here.
--
-- PLAN -> SNAPSHOT -> RESULT invariant (already established for Routine -> ProgramSession copying
-- via copy_routine_to_program_session): a completed Workout is a permanent historical fact and must
-- remain fully renderable even after the planning data it came from changes or disappears. This is
-- why `workout_exercises` snapshots exercise_name/exercise_type/equipment/note/rest_timer_seconds
-- instead of only holding a live FK the way `routine_exercises` does — Android's own local Room
-- schema deliberately does NOT snapshot the exercise name (renaming an Exercise there transparently
-- updates past workouts, by explicit local design), but that convenience tradeoff does not transfer
-- to Supabase: this is the durable, potentially-cross-client system of record, and it must survive
-- Exercise catalog mutations (rename, metadata change, deletion) independently. `origin_routine_id`
-- and `exercise_id` remain as nullable, ON DELETE SET NULL provenance/traceability columns exactly
-- like Android's own `originRoutineId` — informational only, never read back to reconstruct a
-- Workout's own content.

-- ============================================================================
-- workouts
-- ============================================================================

create table if not exists public.workouts (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  -- Snapshot, not a live join to routines.name — a completed Workout's title must survive the
  -- source Routine being renamed or deleted afterward (see the migration header comment).
  name text not null
    constraint workouts_name_not_blank check (length(btrim(name)) > 0),
  -- Traceability only (see header comment) — ON DELETE SET NULL, never CASCADE: deleting the
  -- source Routine must never delete, block, or otherwise affect completed history that came from
  -- it. Never read back to reconstruct this Workout's exercises/sets.
  origin_routine_id uuid references public.routines(id) on delete set null,
  -- The LOCAL calendar day the user experienced this workout on, supplied by the recording client
  -- (which knows its own local timezone at the moment of completion) — deliberately NOT derived by
  -- truncating started_at/completed_at server-side, since a `timestamptz::date` truncation is
  -- wrong for a workout finished near midnight or after a timezone change. This is the column
  -- Profile Calendar/history-grouping/streak calculation should group by; started_at/completed_at
  -- remain precise instants for ordering/duration, not for "which day" bucketing.
  workout_date date not null,
  started_at timestamptz not null,
  completed_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint workouts_completed_not_before_started check (completed_at >= started_at)
);

create index if not exists workouts_owner_id_idx on public.workouts (owner_id);
create index if not exists workouts_owner_id_workout_date_idx on public.workouts (owner_id, workout_date);

-- No INSERT/UPDATE grant to `authenticated` at all — see "RPC-only write boundary" below for why:
-- the only path that can ever create a row here is record_completed_workout(...), a SECURITY
-- DEFINER function that holds its own (the function owner's) table privileges regardless of the
-- calling role's grants. SELECT (for reads) and DELETE (for the correction path in section 24 of
-- the implementation brief — a full-aggregate delete-and-re-record, no partial edit) are granted
-- directly since those have no cross-table atomicity requirement a bare RLS-protected statement
-- can't already satisfy on its own.
grant select, delete
on table public.workouts
to authenticated;

revoke all
on table public.workouts
from anon;

alter table public.workouts enable row level security;

create policy "select own workouts"
on public.workouts
for select
to authenticated
using ((select auth.uid()) = owner_id);

create policy "delete own workouts"
on public.workouts
for delete
to authenticated
using ((select auth.uid()) = owner_id);

-- No insert/update policy: with no INSERT/UPDATE grant on the table at all (above), a policy here
-- would be unreachable anyway — writes exist solely through the SECURITY DEFINER RPC below.

-- ============================================================================
-- workout_exercises
-- ============================================================================

create table if not exists public.workout_exercises (
  id uuid primary key,
  workout_id uuid not null references public.workouts(id) on delete cascade,
  -- Traceability only, ON DELETE SET NULL — see the migration header comment on why this table
  -- snapshots display fields separately instead of relying on this FK to render history. Nullable
  -- so a later Exercise deletion (no delete policy exists on `exercises` today, but the schema
  -- should not assume that never changes) can never touch completed history.
  exercise_id text references public.exercises(id) on delete set null,
  exercise_name text not null
    constraint workout_exercises_name_not_blank check (length(btrim(exercise_name)) > 0),
  -- Snapshotted from exercises.exercise_type/equipment at record time (same CHECK values — see
  -- 20260911180000_add_exercise_metadata.sql) — nullable because the source Exercise's own metadata
  -- is nullable (a pre-metadata custom exercise), not because this is optional history data.
  exercise_type text
    constraint workout_exercises_exercise_type_valid check (exercise_type is null or exercise_type in ('WEIGHT_REPS', 'BODYWEIGHT_REPS')),
  equipment text
    constraint workout_exercises_equipment_valid check (
      equipment is null or equipment in ('BARBELL', 'DUMBBELL', 'MACHINE', 'CABLE', 'BODYWEIGHT', 'KETTLEBELL', 'BAND', 'OTHER')
    ),
  -- Snapshot of the Routine-planning note/rest-timer that were in effect when this Workout was
  -- recorded, if any (see the implementation brief's "NOTE / REST TIMER DECISION": completed
  -- history is private owner data — the Routine-sharing decision to exclude Notes from PUBLIC
  -- shares does not apply here, nothing on this table is ever publicly readable). Same bounds as
  -- routine_exercises' own columns (20260914120000_add_routine_exercise_note_and_rest_timer.sql).
  note text
    constraint workout_exercises_note_length check (note is null or length(note) <= 1000),
  rest_timer_seconds integer
    constraint workout_exercises_rest_timer_seconds_non_negative check (rest_timer_seconds is null or rest_timer_seconds >= 0),
  -- Assigned purely from JSONB array order inside record_completed_workout(...), the same
  -- convention save_routine(...) already established — never caller-supplied, so there is nothing
  -- to validate for correctness/contiguity.
  position integer not null,
  constraint workout_exercises_unique_position unique (workout_id, position)
);

create index if not exists workout_exercises_workout_id_idx on public.workout_exercises (workout_id);
create index if not exists workout_exercises_exercise_id_idx on public.workout_exercises (exercise_id);

grant select
on table public.workout_exercises
to authenticated;

revoke all
on table public.workout_exercises
from anon;

alter table public.workout_exercises enable row level security;

-- Deleting a workout cascades (FK) through this table regardless of this SELECT-only grant — a
-- cascade delete is performed by the system, not subject to the deleting role's own table grants
-- on the child table.
create policy "select own workout_exercises"
on public.workout_exercises
for select
to authenticated
using (
  exists (
    select 1 from public.workouts w
    where w.id = workout_exercises.workout_id
      and w.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- workout_sets
-- ============================================================================

create table if not exists public.workout_sets (
  id uuid primary key,
  workout_exercise_id uuid not null references public.workout_exercises(id) on delete cascade,
  position integer not null,
  -- Snapshot of the set's classification at record time — reuses routine_planned_sets' exact CHECK
  -- values (20260915120000_add_planned_set_type.sql). Every performed set genuinely has some type
  -- (same reasoning as the planning-side column), so this stays NOT NULL with the same constant
  -- default rather than nullable.
  set_type text not null default 'NORMAL'
    constraint workout_sets_set_type_valid check (set_type in ('NORMAL', 'WARMUP', 'FAILURE', 'DROPSET')),
  -- PLAN vs RESULT, kept structurally independent (see the migration header comment and Android's
  -- own WorkoutSetEntity, whose targetReps/targetWeight are "copied ONCE, at START... never re-read
  -- from the source afterward" — the exact same copy-once-at-start semantic applies here): planned
  -- values must never be overwritten by, or derived from, performed values or vice versa.
  target_reps integer
    constraint workout_sets_target_reps_non_negative check (target_reps is null or target_reps >= 0),
  target_weight numeric
    constraint workout_sets_target_weight_non_negative check (target_weight is null or target_weight >= 0),
  reps integer
    constraint workout_sets_reps_non_negative check (reps is null or reps >= 0),
  weight numeric
    constraint workout_sets_weight_non_negative check (weight is null or weight >= 0),
  constraint workout_sets_unique_position unique (workout_exercise_id, position)
);

create index if not exists workout_sets_workout_exercise_id_idx on public.workout_sets (workout_exercise_id);

grant select
on table public.workout_sets
to authenticated;

revoke all
on table public.workout_sets
from anon;

alter table public.workout_sets enable row level security;

create policy "select own workout_sets"
on public.workout_sets
for select
to authenticated
using (
  exists (
    select 1
    from public.workout_exercises we
    join public.workouts w on w.id = we.workout_id
    where we.id = workout_sets.workout_exercise_id
      and w.owner_id = (select auth.uid())
  )
);

-- ============================================================================
-- record_completed_workout: the sole write path for this domain
-- ============================================================================

-- SECURITY DEFINER, deliberately NOT security invoker like save_routine(...) — the reasoning
-- differs from Routine's full-replace pattern in one important way: a Routine mid-save is just a
-- draft, so save_routine's reliance on ordinary RLS-protected multi-statement inserts is harmless
-- even though it isn't literally atomic at the privilege layer (RLS still lets an authenticated
-- caller INSERT into routine_exercises/routine_planned_sets directly). A completed Workout is
-- meant to represent a permanent, immutable historical fact — a partially-written aggregate (a
-- workout row with only some of its exercises/sets, left behind by an interrupted multi-call
-- sequence) is a materially worse failure mode than an interrupted Routine draft. This function is
-- therefore the ONLY way to create a row in any of the three tables above: no INSERT grant exists
-- for `authenticated` on any of them (see each table's own grants above), so even a well-intentioned
-- direct multi-statement write is structurally impossible, not merely discouraged by convention.
--
-- Hardening applied (same rules 20260913120000_adopt_rls_auto_enable.sql already established for
-- this repo's one other SECURITY DEFINER function):
--   - `set search_path = public, pg_temp` — fixed, cannot be redirected by a session-level change.
--   - every reference is schema-qualified (public.workouts, public.exercises, ...).
--   - EXECUTE revoked from PUBLIC and anon explicitly; granted only to `authenticated`.
--   - owner identity is derived exclusively from auth.uid() inside the function body — the payload
--     has no owner_id field at all, so there is nothing for a caller to spoof.
--   - anon cannot call this at all (no EXECUTE grant), verified below.
--
-- IMPORTANT PRODUCT BOUNDARY (see the implementation brief section 18): this RPC existing does not
-- mean Web uses it. Web's own code (web/src/data/*.ts) contains no call to this function, no
-- mutation hook, and no UI that could trigger one — Web's relationship to this table family is
-- read-only at the product layer, enforced by what Web's code simply never does, not by revoking
-- EXECUTE from `authenticated` (a future native Phone/Watch client authenticates as the same role
-- and MUST be able to call this).
--
-- IDEMPOTENCY: the caller supplies the Workout's own id (and its exercises'/sets' ids) rather than
-- letting Postgres generate them — a native client can generate a stable id once, locally, before
-- ever attempting the network call, so a lost-response retry can safely resubmit the identical
-- payload. If a workout with the given id already exists AND is owned by the same caller, this
-- function does NOT accept that as success on id+owner alone — a completed Workout is historical
-- execution data and must not silently accept conflicting content under a reused identity. Instead
-- it reconstructs the canonically-persisted aggregate from the actual stored rows (as typed values,
-- not raw request text — see below) and compares it to the incoming payload, parsed through the
-- exact same casts this function uses to write in the first place:
--   - identical (same typed values, in the same order)  -> idempotent no-op, existing id returned,
--     nothing re-written. This is the "lost-response retry resubmits the same payload" case.
--   - different                                          -> rejected with an exception; the
--     persisted historical aggregate is left completely untouched (no latest-wins overwrite, no
--     merge). This is the "different logical workout / stale write reusing an id" case.
-- Comparing through typed columns/variables on both sides (rather than comparing raw JSON text) is
-- deliberate: it makes the check semantic, not textual — e.g. a resubmitted timestamp with different
-- ISO-8601 offset formatting, or "100" vs "100.0" for a numeric weight, still compares equal once
-- both sides pass through the same timestamptz/numeric casts, exactly as they would on first write.
-- If the id already exists owned by a DIFFERENT account, this is rejected as a cross-account id
-- collision attempt, not silently ignored or overwritten — unchanged from the original design.
create or replace function public.record_completed_workout(p_workout jsonb)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_workout_id uuid;
  v_existing_owner_id uuid;
  v_row_exists boolean;
  v_name text;
  v_origin_routine_id uuid;
  v_workout_date date;
  v_started_at timestamptz;
  v_completed_at timestamptz;
  v_exercise jsonb;
  v_set jsonb;
  v_exercise_id text;
  v_exercise_name text;
  v_exercise_type text;
  v_equipment text;
  v_note text;
  v_rest_timer_seconds integer;
  v_workout_exercise_id uuid;
  v_exercise_position integer := 0;
  v_set_id uuid;
  v_set_type text;
  v_target_reps integer;
  v_target_weight numeric;
  v_reps integer;
  v_weight numeric;
  v_set_position integer;
  v_new_exercises jsonb := '[]'::jsonb;
  v_new_sets jsonb;
  v_new_snapshot jsonb;
  v_existing_snapshot jsonb;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  v_workout_id := nullif(p_workout ->> 'id', '')::uuid;
  if v_workout_id is null then
    raise exception 'workout id is required';
  end if;

  -- Cross-account collision check happens up front, before any parsing/comparison work — an id
  -- collision from a different owner is always rejected regardless of payload content.
  select owner_id into v_existing_owner_id from public.workouts where id = v_workout_id;
  v_row_exists := found;
  if v_row_exists and v_existing_owner_id <> v_uid then
    raise exception 'a workout with this id already exists';
  end if;

  v_name := p_workout ->> 'name';
  v_origin_routine_id := nullif(p_workout ->> 'originRoutineId', '')::uuid;

  v_started_at := nullif(p_workout ->> 'startedAt', '')::timestamptz;
  v_completed_at := nullif(p_workout ->> 'completedAt', '')::timestamptz;
  if v_started_at is null or v_completed_at is null then
    raise exception 'startedAt and completedAt are required';
  end if;
  if v_completed_at < v_started_at then
    raise exception 'completedAt must not be before startedAt';
  end if;

  v_workout_date := nullif(p_workout ->> 'workoutDate', '')::date;
  if v_workout_date is null then
    raise exception 'workoutDate is required';
  end if;

  -- First-time write only — if a row with this id already exists (same owner, checked above), no
  -- insert happens here at all; we fall through to building the comparison snapshot below instead.
  if not v_row_exists then
    insert into public.workouts (id, owner_id, name, origin_routine_id, workout_date, started_at, completed_at)
    values (v_workout_id, v_uid, v_name, v_origin_routine_id, v_workout_date, v_started_at, v_completed_at);
  end if;

  for v_exercise in select * from jsonb_array_elements(coalesce(p_workout -> 'exercises', '[]'::jsonb))
  loop
    v_exercise_id := nullif(v_exercise ->> 'exerciseId', '');
    v_exercise_name := v_exercise ->> 'exerciseName';
    v_exercise_type := nullif(v_exercise ->> 'exerciseType', '');
    v_equipment := nullif(v_exercise ->> 'equipment', '');
    v_note := nullif(v_exercise ->> 'note', '');
    v_rest_timer_seconds := nullif(v_exercise ->> 'restTimerSeconds', '')::integer;
    v_workout_exercise_id := nullif(v_exercise ->> 'id', '')::uuid;

    if v_exercise_id is null then
      raise exception 'exerciseId is required for every workout exercise';
    end if;
    if v_exercise_name is null or length(btrim(v_exercise_name)) = 0 then
      raise exception 'exerciseName is required for every workout exercise';
    end if;

    if not v_row_exists then
      -- Exercise visibility re-check — the exact FK-bypasses-RLS fix already established for
      -- routine_exercises (20260910120000_create_routines.sql): a bare FK only proves the row
      -- exists, never that this caller may see/use it. A snapshot exercise_name supplied alongside
      -- another account's private exercise_id must not legitimize the reference. Only needed on the
      -- first-time write path — a comparison-only retry never touches this table.
      if not exists (
        select 1 from public.exercises e
        where e.id = v_exercise_id
          and (e.owner_id is null or e.owner_id = v_uid)
      ) then
        raise exception 'exercise % is not visible to the current user', v_exercise_id;
      end if;

      insert into public.workout_exercises (
        id, workout_id, exercise_id, exercise_name, exercise_type, equipment, note, rest_timer_seconds, position
      )
      values (
        v_workout_exercise_id, v_workout_id, v_exercise_id, v_exercise_name, v_exercise_type,
        v_equipment, v_note, v_rest_timer_seconds, v_exercise_position
      );
    end if;

    v_new_sets := '[]'::jsonb;
    v_set_position := 0;
    for v_set in select * from jsonb_array_elements(coalesce(v_exercise -> 'sets', '[]'::jsonb))
    loop
      v_set_id := nullif(v_set ->> 'id', '')::uuid;
      v_set_type := coalesce(nullif(v_set ->> 'setType', ''), 'NORMAL');
      v_target_reps := nullif(v_set ->> 'targetReps', '')::integer;
      v_target_weight := nullif(v_set ->> 'targetWeight', '')::numeric;
      v_reps := nullif(v_set ->> 'reps', '')::integer;
      v_weight := nullif(v_set ->> 'weight', '')::numeric;

      if not v_row_exists then
        insert into public.workout_sets (
          id, workout_exercise_id, position, set_type, target_reps, target_weight, reps, weight
        )
        values (
          v_set_id, v_workout_exercise_id, v_set_position, v_set_type, v_target_reps, v_target_weight,
          v_reps, v_weight
        );
      end if;

      -- Built from the same typed variables just inserted (or that would have been inserted) above
      -- — this is the "compare through typed values, not raw text" contract described in the header
      -- comment, applied down to individual set fields.
      v_new_sets := v_new_sets || jsonb_build_array(jsonb_build_object(
        'id', v_set_id,
        'position', v_set_position,
        'setType', v_set_type,
        'targetReps', v_target_reps,
        'targetWeight', v_target_weight,
        'reps', v_reps,
        'weight', v_weight
      ));
      v_set_position := v_set_position + 1;
    end loop;

    v_new_exercises := v_new_exercises || jsonb_build_array(jsonb_build_object(
      'id', v_workout_exercise_id,
      'position', v_exercise_position,
      'exerciseId', v_exercise_id,
      'exerciseName', v_exercise_name,
      'exerciseType', v_exercise_type,
      'equipment', v_equipment,
      'note', v_note,
      'restTimerSeconds', v_rest_timer_seconds,
      'sets', v_new_sets
    ));

    v_exercise_position := v_exercise_position + 1;
  end loop;

  if not v_row_exists then
    return v_workout_id;
  end if;

  -- Comparison-only path: nothing was written above (every insert was guarded by `not v_row_exists`).
  -- Reconstruct the canonically-persisted aggregate from the actual stored rows, through the same
  -- column types the original write used, and compare it to what this call would have written.
  v_new_snapshot := jsonb_build_object(
    'name', v_name,
    'originRoutineId', v_origin_routine_id,
    'workoutDate', v_workout_date,
    'startedAt', v_started_at,
    'completedAt', v_completed_at,
    'exercises', v_new_exercises
  );

  select jsonb_build_object(
    'name', w.name,
    'originRoutineId', w.origin_routine_id,
    'workoutDate', w.workout_date,
    'startedAt', w.started_at,
    'completedAt', w.completed_at,
    'exercises', coalesce(ex_agg.exercises, '[]'::jsonb)
  )
  into v_existing_snapshot
  from public.workouts w
  left join (
    select we.workout_id,
      jsonb_agg(
        jsonb_build_object(
          'id', we.id,
          'position', we.position,
          'exerciseId', we.exercise_id,
          'exerciseName', we.exercise_name,
          'exerciseType', we.exercise_type,
          'equipment', we.equipment,
          'note', we.note,
          'restTimerSeconds', we.rest_timer_seconds,
          'sets', coalesce(set_agg.sets, '[]'::jsonb)
        ) order by we.position
      ) as exercises
    from public.workout_exercises we
    left join (
      select ws.workout_exercise_id,
        jsonb_agg(
          jsonb_build_object(
            'id', ws.id,
            'position', ws.position,
            'setType', ws.set_type,
            'targetReps', ws.target_reps,
            'targetWeight', ws.target_weight,
            'reps', ws.reps,
            'weight', ws.weight
          ) order by ws.position
        ) as sets
      from public.workout_sets ws
      group by ws.workout_exercise_id
    ) set_agg on set_agg.workout_exercise_id = we.id
    group by we.workout_id
  ) ex_agg on ex_agg.workout_id = w.id
  where w.id = v_workout_id;

  if v_existing_snapshot = v_new_snapshot then
    return v_workout_id;
  else
    raise exception 'a workout with id % already exists with different content — conflicting retries are not accepted', v_workout_id;
  end if;
end;
$$;

revoke all on function public.record_completed_workout(jsonb) from public;
revoke all on function public.record_completed_workout(jsonb) from anon;
grant execute on function public.record_completed_workout(jsonb) to authenticated;
