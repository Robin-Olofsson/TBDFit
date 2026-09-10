-- Adds a Set Type classification (Normal/Warm-up/Failure/Drop set) to public.routine_planned_sets
-- — a routine-editor UX request from the developer: each planned set gets a small square control to
-- pick one of four letters (N/W/F/D). Unlike `note`/`rest_timer_seconds`
-- (20260914120000_add_routine_exercise_note_and_rest_timer.sql), every set genuinely has SOME type
-- — "no type" is not a meaningful state the way "no note" is — so this column is NOT NULL with a
-- constant default, not nullable.
--
-- `set_type` is a plain CHECK-constrained text column, not a Postgres enum type, matching the exact
-- convention already established by `exercises.exercise_type`/`exercises.equipment`
-- (20260911180000_add_exercise_metadata.sql) — a future fifth set type is then a trivial
-- constraint-alteration migration, never an enum-type migration.
--
-- `not null default 'NORMAL'` in one statement is safe and sufficient here (no separate backfill
-- UPDATE needed the way `profiles.display_name` required one): the default is a constant, not
-- derived from another column per row, so Postgres can apply it to every existing row (there are
-- none yet — this table's creating migration, 20260910120000_create_routines.sql, is already
-- applied live, so this is a genuine ALTER against a populated table on the real project) as a fast,
-- safe, metadata-only operation.
alter table public.routine_planned_sets
  add column set_type text not null default 'NORMAL'
    constraint routine_planned_sets_set_type_valid
      check (set_type in ('NORMAL', 'WARMUP', 'FAILURE', 'DROPSET'));

-- ============================================================================
-- save_routine: re-declared only to read/write the new field
-- ============================================================================

-- Same append-only reasoning as 20260914120000's own re-declaration of this function:
-- 20260910120000_create_routines.sql (which originally defined save_routine) is already applied
-- live, so it cannot be edited in place. This is a full, verbatim carry-forward of
-- 20260914120000's version of the function, plus set_type on the planned-set insert — nothing else
-- changes (same signature, same security invoker, same search_path). A blank/absent `setType` in
-- the payload defaults to 'NORMAL' via `coalesce(nullif(x, ''), 'NORMAL')`, rather than the
-- null-means-null convention used for targetReps/targetWeight/note/restTimerSeconds — because
-- 'NORMAL' is a real, valid value here, not an absence signal.
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
      insert into public.routine_planned_sets (routine_exercise_id, position, target_reps, target_weight, set_type)
      values (
        v_routine_exercise_id,
        v_set_position,
        nullif(v_planned_set ->> 'targetReps', '')::integer,
        nullif(v_planned_set ->> 'targetWeight', '')::numeric,
        coalesce(nullif(v_planned_set ->> 'setType', ''), 'NORMAL')
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
