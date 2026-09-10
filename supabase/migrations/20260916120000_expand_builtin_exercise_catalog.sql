-- Expands the built-in Exercise catalog from 6 to 30 — a curated V1 general-purpose starting
-- library (Push/Pull/Legs, Upper/Lower, general strength/hypertrophy), not an exhaustive exercise
-- database. See docs/product/frontend-prototype-notes.md's "Exercise metadata" section for the
-- product-level framing and docs/development/supabase-setup-and-verification.md for the identity
-- contract this respects.
--
-- Does NOT touch the existing six built-ins in any way (no delete/reinsert, no id change) — those
-- canonical ids (builtin_bench_press, builtin_back_squat, builtin_deadlift, builtin_overhead_press,
-- builtin_barbell_row, builtin_pull_up) are an immutable product contract: existing
-- routine_exercises/program_session_exercises rows may already reference them by id.
--
-- Same identity rule as those six (see 20260910180000_normalize_exercise_identity.sql): every new
-- built-in gets a fixed, hand-assigned `builtin_*` string id, matched by
-- Android's android/phone/src/main/kotlin/com/tbdfit/phone/workout/BuiltInExerciseCatalog.kt
-- BUILT_IN_EXERCISE_SEED entry of the same name (updated in the same change) — never a generated
-- UUID, never a display-name lookup, never a separate Android/Supabase id space. Android has no
-- exercise_type/equipment concept at all (that metadata slice, 20260911180000, was Web+Supabase
-- only), so parity for these 24 is id+name only, exactly like the original six.
--
-- No schema/RLS change: this is a pure data insert into the exact same `exercises` shape
-- 20260911180000_add_exercise_metadata.sql already established (id, name, owner_id, created_at,
-- exercise_type, equipment) — every new row is owner_id NULL (built-in, globally visible under the
-- existing "select visible exercises" policy) with both metadata columns populated immediately
-- (unlike a pre-metadata custom exercise, there is no historical NULL state to preserve here, since
-- these rows never existed before this migration).
--
-- Curation choices worth recording explicitly (see also the task's own KNOWN CATALOG TRADEOFFS):
--   - Broad canonical exercises only, no grip/angle/cable-height variants (e.g. one Lat Pulldown,
--     not wide/close/neutral/reverse-grip variants) — a user wanting a specific variant remains
--     free to create it as a custom exercise.
--   - Pull-Up/Push-Up/Dip/Hanging Leg Raise follow the existing Pull-Up precedent exactly:
--     BODYWEIGHT_REPS + BODYWEIGHT, no optional-external-load modeling.
--   - Leg Curl and Standing/Seated Calf Raise are recorded as MACHINE without a seated/lying or
--     standing/plate-loaded distinction — the broad canonical form, not a specific machine variant.
insert into public.exercises (id, name, owner_id, exercise_type, equipment) values
  ('builtin_incline_dumbbell_press', 'Incline Dumbbell Press', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_dumbbell_bench_press', 'Dumbbell Bench Press', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_push_up', 'Push-Up', null, 'BODYWEIGHT_REPS', 'BODYWEIGHT'),
  ('builtin_dip', 'Dip', null, 'BODYWEIGHT_REPS', 'BODYWEIGHT'),
  ('builtin_lat_pulldown', 'Lat Pulldown', null, 'WEIGHT_REPS', 'CABLE'),
  ('builtin_seated_cable_row', 'Seated Cable Row', null, 'WEIGHT_REPS', 'CABLE'),
  ('builtin_dumbbell_row', 'Dumbbell Row', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_face_pull', 'Face Pull', null, 'WEIGHT_REPS', 'CABLE'),
  ('builtin_dumbbell_shoulder_press', 'Dumbbell Shoulder Press', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_dumbbell_lateral_raise', 'Dumbbell Lateral Raise', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_romanian_deadlift', 'Romanian Deadlift', null, 'WEIGHT_REPS', 'BARBELL'),
  ('builtin_leg_press', 'Leg Press', null, 'WEIGHT_REPS', 'MACHINE'),
  ('builtin_leg_extension', 'Leg Extension', null, 'WEIGHT_REPS', 'MACHINE'),
  ('builtin_leg_curl', 'Leg Curl', null, 'WEIGHT_REPS', 'MACHINE'),
  ('builtin_bulgarian_split_squat', 'Bulgarian Split Squat', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_barbell_hip_thrust', 'Barbell Hip Thrust', null, 'WEIGHT_REPS', 'BARBELL'),
  ('builtin_barbell_curl', 'Barbell Curl', null, 'WEIGHT_REPS', 'BARBELL'),
  ('builtin_dumbbell_curl', 'Dumbbell Curl', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_hammer_curl', 'Hammer Curl', null, 'WEIGHT_REPS', 'DUMBBELL'),
  ('builtin_triceps_pushdown', 'Triceps Pushdown', null, 'WEIGHT_REPS', 'CABLE'),
  ('builtin_cable_crunch', 'Cable Crunch', null, 'WEIGHT_REPS', 'CABLE'),
  ('builtin_hanging_leg_raise', 'Hanging Leg Raise', null, 'BODYWEIGHT_REPS', 'BODYWEIGHT'),
  ('builtin_standing_calf_raise', 'Standing Calf Raise', null, 'WEIGHT_REPS', 'MACHINE'),
  ('builtin_seated_calf_raise', 'Seated Calf Raise', null, 'WEIGHT_REPS', 'MACHINE');
