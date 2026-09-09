-- Exercise metadata: exercise_type + equipment — a narrow, Web-first product slice. Explicitly does
-- NOT add Primary Muscle Group, Secondary Muscles, an exercise image, or any Workout-execution
-- change (see docs/product/frontend-prototype-notes.md for the exact classification this migration
-- produces).
--
-- WHY A CHECK CONSTRAINT, NOT A POSTGRES ENUM TYPE: an enum type's value set can only be extended
-- (never removed/reordered without a full type rebuild), and adding a value to a Postgres enum
-- cannot run inside the same transaction as other DDL in older server versions — a real, avoidable
-- migration-ergonomics cost. A plain `text` column with a named CHECK constraint gets the same
-- "only these values are valid" guarantee, and extending it later (e.g. adding `DURATION` once
-- Workout execution ever supports it) is a trivial `ALTER TABLE ... DROP CONSTRAINT ...; ALTER
-- TABLE ... ADD CONSTRAINT ... CHECK (...)` — no identity migration, no table recreate, unlike the
-- exercises.id normalization this migration runs after.
--
-- exercise_type IS DELIBERATELY LIMITED TO (WEIGHT_REPS, BODYWEIGHT_REPS): verified directly against
-- android/phone/src/main/kotlin/com/tbdfit/phone/workout/WorkoutSetEntity.kt before writing this —
-- WorkoutSet has exactly `weight`/`reps` (plus target snapshots of the same two) and explicitly no
-- duration/distance/pace/time field, with its own doc comment stating "not a universal
-- representation for future cardio/duration/distance exercise types... do not add
-- setType/RPE/RIR/duration/distance columns to this entity without revisiting that design decision
-- first." Nothing on Web has any Workout execution model at all yet. Exposing a DURATION/DISTANCE
-- exercise_type value now would let a user pick a type with nowhere to log it — this constraint
-- exists specifically to prevent that, not merely to keep the list short.
--
-- NULLABLE, NOT DEFAULTED: existing custom exercises (created before this migration) get NULL for
-- both new columns, never a guessed value — the same "surfaced, not guessed" discipline already
-- used for Workout.ownerId's pre-ownership sentinel and Exercise's own identity normalization. A
-- NULL exercise_type/equipment on an old custom exercise is a correctly-honest "unknown", not a
-- silently-wrong "WEIGHT_REPS"/"OTHER" default that the user never actually chose. New custom
-- exercises (created through the updated Web form after this migration) always get a real,
-- user-chosen value for both — the form makes both fields required, not optional, for new rows;
-- the schema itself does not force this (it allows NULL) since forcing NOT NULL at the database
-- level would also block the pre-existing NULL rows this migration intentionally creates.
--
-- No RLS change: these are plain additional columns on rows already governed by exercises' existing
-- row-level policies (`select visible exercises`, `insert own custom exercise`) — RLS operates on
-- rows, not specific columns, so no policy needs to change for a new attribute on an already-covered
-- row. Verified explicitly in this task's own RLS re-run, not merely assumed.

alter table public.exercises
  add column exercise_type text,
  add column equipment text;

alter table public.exercises
  add constraint exercises_exercise_type_check
  check (exercise_type is null or exercise_type in ('WEIGHT_REPS', 'BODYWEIGHT_REPS'));

alter table public.exercises
  add constraint exercises_equipment_check
  check (equipment is null or equipment in (
    'BARBELL', 'DUMBBELL', 'MACHINE', 'CABLE', 'BODYWEIGHT', 'KETTLEBELL', 'BAND', 'OTHER'
  ));

-- Built-in metadata mapping — matched by canonical id (see BuiltInExerciseCatalog.kt), never by
-- name-guessing. Five of the six are straightforward barbell weight+reps lifts. Pull-Up is the one
-- judgment call: it is almost always performed as a bodyweight exercise in this catalog's context
-- (no attached-weight-belt concept exists anywhere in the product yet), so BODYWEIGHT_REPS +
-- BODYWEIGHT is the documented default — a user who wants to log a weighted pull-up as a barbell/
-- dumbbell-loaded custom variant remains free to create their own custom exercise for that; this
-- migration does not attempt to model "optionally weighted" as a built-in property.
update public.exercises set exercise_type = 'WEIGHT_REPS', equipment = 'BARBELL'
  where id in ('builtin_bench_press', 'builtin_back_squat', 'builtin_deadlift', 'builtin_overhead_press', 'builtin_barbell_row');

update public.exercises set exercise_type = 'BODYWEIGHT_REPS', equipment = 'BODYWEIGHT'
  where id = 'builtin_pull_up';
