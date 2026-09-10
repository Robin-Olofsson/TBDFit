-- Adds optional Workout <-> ScheduledSession provenance, and extends record_completed_workout(...)
-- to validate and canonically compare it. This is a NEW FORWARD migration, not an edit to
-- 20260917120000_create_workout_history.sql — that migration is already committed to this repo's
-- git history (and, per this project's standing rule, is treated as immutable the moment that's
-- true, independent of whether it has also reached a live remote project). Every change below is
-- CREATE OR REPLACE / ALTER TABLE ADD COLUMN, exactly this repo's established pattern for extending
-- an already-committed migration's function without rewriting history (see
-- 20260914120000_add_routine_exercise_note_and_rest_timer.sql's own precedent of replacing
-- save_routine() in a forward migration rather than editing create_routines.sql).
--
-- SEMANTICS: workouts.origin_scheduled_session_id answers "did this completed Workout fulfill a
-- planned ScheduledSession, and if so, which one" — the one piece of provenance a future Calendar
-- needs to distinguish (see docs/development/supabase-setup-and-verification.md's Scheduling section
-- for the full writeup):
--   A. a ScheduledSession with no completed Workout yet (planned, not yet executed)
--   B. a ScheduledSession whose native execution produced this exact Workout (origin_scheduled_
--      session_id = that session's id)
--   C. an ad-hoc completed Workout that was never scheduled (origin_scheduled_session_id = NULL,
--      fully valid)
-- This must never be inferred from "same date + same Routine" — two ScheduledSessions for the same
-- Routine on the same day must remain independently correlatable, and an ad-hoc Workout coincidentally
-- on the same day as a planned session must never be mistaken for fulfilling it.
--
-- ON DELETE SET NULL, not CASCADE — the deliberate opposite of scheduled_sessions' own CASCADE from
-- its Routine/ProgramSession source (20260920120000_create_scheduled_sessions.sql). A completed
-- Workout is permanent historical truth (see 20260917120000's own header comment); unscheduling or
-- otherwise deleting the ScheduledSession that a Workout fulfilled must never delete, block, or
-- otherwise affect that Workout — only the provenance link itself is cleared. This mirrors
-- origin_routine_id's exact existing precedent on the same table.
alter table public.workouts
  add column origin_scheduled_session_id uuid references public.scheduled_sessions(id) on delete set null;

-- IDEMPOTENCY IMPACT: origin_scheduled_session_id is now part of the canonical persisted aggregate
-- that a same-id/same-owner retry is compared against (see record_completed_workout's own existing
-- comparison-by-typed-value discipline, unchanged by this migration). A retry that resubmits the
-- exact same workout id/owner but a DIFFERENT origin_scheduled_session_id is therefore now correctly
-- rejected as a conflicting retry, the same as a changed performed reps/weight already was — it is
-- semantically meaningful data (which planned occurrence this execution fulfilled, if any), not
-- incidental metadata safe to silently overwrite. Nested Exercise/Set reconstruction ordering is
-- untouched (still `order by position` on both sides, already deterministic — no natural-row-order
-- assumption existed before this change and none is introduced now).
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
  v_origin_scheduled_session_id uuid;
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
  -- NULL is valid here (ad-hoc Workout, never scheduled) — no required-ness check, unlike
  -- startedAt/completedAt/workoutDate below. Ownership is verified below, only on the first-time
  -- write path (see the exercise-visibility check's own comment for why the comparison-only retry
  -- path needs no re-validation).
  v_origin_scheduled_session_id := nullif(p_workout ->> 'originScheduledSessionId', '')::uuid;

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
    if v_origin_scheduled_session_id is not null then
      -- ScheduledSession visibility/ownership re-check — the same FK-bypasses-RLS fix pattern
      -- already established for Exercise ownership just below: a bare FK only proves the row
      -- exists, never that this caller owns it. Unlike Exercise (which may be a shared builtin,
      -- owner_id is null), a ScheduledSession has no such shared case — it must belong to this
      -- exact caller or be rejected outright.
      if not exists (
        select 1 from public.scheduled_sessions ss
        where ss.id = v_origin_scheduled_session_id and ss.owner_id = v_uid
      ) then
        raise exception 'scheduled session % is not visible to the current user', v_origin_scheduled_session_id;
      end if;
    end if;

    insert into public.workouts (
      id, owner_id, name, origin_routine_id, origin_scheduled_session_id, workout_date, started_at, completed_at
    )
    values (
      v_workout_id, v_uid, v_name, v_origin_routine_id, v_origin_scheduled_session_id, v_workout_date,
      v_started_at, v_completed_at
    );
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
  -- originScheduledSessionId is now part of this comparison (see this migration's own header
  -- comment) — a retry that changes only which ScheduledSession a Workout is attributed to is a
  -- semantically different payload, not a safe no-op.
  v_new_snapshot := jsonb_build_object(
    'name', v_name,
    'originRoutineId', v_origin_routine_id,
    'originScheduledSessionId', v_origin_scheduled_session_id,
    'workoutDate', v_workout_date,
    'startedAt', v_started_at,
    'completedAt', v_completed_at,
    'exercises', v_new_exercises
  );

  select jsonb_build_object(
    'name', w.name,
    'originRoutineId', w.origin_routine_id,
    'originScheduledSessionId', w.origin_scheduled_session_id,
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
