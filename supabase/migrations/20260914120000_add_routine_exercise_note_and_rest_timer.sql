-- Adds a per-exercise pinned Note and a Rest Timer to public.routine_exercises — a routine-editor
-- UX reference the developer shared showed both fields directly on the exercise card (a "Note"
-- textarea and a "Rest Timer" dropdown), and this migration is the backend half of that Web slice.
-- Both fields are nullable and OPTIONAL, not required: unlike profiles.display_name (where a
-- missing value means an incomplete record), the overwhelming common case for a planned exercise is
-- no note and no rest timer at all — NULL is the normal, expected state here, not a signal of
-- incompleteness. No not-blank constraint is added for `note` for the same reason.
--
-- `note`: free-text, pinned to this specific routine-exercise slot (not the exercise definition
-- itself — the same Bench Press in two different Routines can carry two different notes). Bound at
-- 1000 characters — comfortably larger than any realistic coaching cue/reminder, still a real,
-- deliberate limit rather than unbounded text.
--
-- `rest_timer_seconds`: an integer count of seconds, NULL meaning "Off" (no rest timer configured)
-- — the same "blank/absent means no value, never coerced to 0" convention already established by
-- `routine_planned_sets.target_reps`/`target_weight` in 20260910120000_create_routines.sql. The Web
-- UI offers a fixed set of presets (Off/30s/60s/90s/2min/3min/5min) but the column itself is a plain
-- unconstrained-above integer — this table has no opinion on which presets a client offers, only
-- that a stored value must be a non-negative number of seconds.
--
-- No RLS change: two more columns on an already-RLS-covered table, same as
-- 20260911180000_add_exercise_metadata.sql adding exercise_type/equipment to public.exercises.
alter table public.routine_exercises
  add column note text
    constraint routine_exercises_note_length check (note is null or length(note) <= 1000);

alter table public.routine_exercises
  add column rest_timer_seconds integer
    constraint routine_exercises_rest_timer_seconds_non_negative check (
      rest_timer_seconds is null or rest_timer_seconds >= 0
    );

-- ============================================================================
-- save_routine: re-declared only to read/write the two new fields
-- ============================================================================

-- 20260910120000_create_routines.sql has already been applied to the live project (confirmed via
-- the developer's own migration-status audit this session), so it cannot be edited in place — this
-- is this repo's normal append-only migration rule, not an exception. Identical to
-- 20260910180000_normalize_exercise_identity.sql's own re-declaration of this function in every
-- respect except the two new columns below; see that migration and the original's own comment for
-- the full atomicity/security-invoker/search_path reasoning, unchanged here. Blank/absent
-- `note`/`restTimerSeconds` in the payload become NULL, following the exact same
-- `nullif(x ->> 'key', '')` pattern already used for `targetReps`/`targetWeight` — never coerced to
-- `''`/`0`.
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
    insert into public.routine_exercises (routine_id, exercise_id, position, note, rest_timer_seconds)
    values (
      v_routine_id,
      (v_exercise ->> 'exerciseId'),
      v_exercise_position,
      nullif(v_exercise ->> 'note', ''),
      nullif(v_exercise ->> 'restTimerSeconds', '')::integer
    )
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
