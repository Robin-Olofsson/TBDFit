// Routine / RoutineExercise / PlannedSet below are REAL, Supabase-backed product domain types —
// see supabase/migrations/20260910120000_create_routines.sql and docs/product/frontend-prototype-notes.md
// for the production-backed classification. They are NOT required to match Android's Room entities
// field-for-field (see that migration's own header comment for why) — only the concepts (Routine
// standalone, RoutineExercise references stable Exercise identity, PlannedSet is intent not
// execution) must read as the same product, per ADR-005's "same product, specialized clients".
//
// HistoryEntry remains PROTOTYPE-ONLY — no History/Workout backend exists on Web yet (out of scope
// for the Routine vertical slice).

// Exercise metadata (exercise_type/equipment) — see
// supabase/migrations/20260911180000_add_exercise_metadata.sql. Both are plain CHECK-constrained
// text columns (not a Postgres enum) specifically so a future value can be added via a trivial
// constraint-alteration migration rather than an enum-type migration. `ExerciseType` is
// deliberately limited to values the current strength-first Workout execution model can actually
// log (reps + optional weight, nothing else) — see that migration's own comment and
// WorkoutSetEntity.kt (Android) for why DURATION/DISTANCE are not exposed anywhere yet.
export type ExerciseType = 'WEIGHT_REPS' | 'BODYWEIGHT_REPS'
export type Equipment = 'BARBELL' | 'DUMBBELL' | 'MACHINE' | 'CABLE' | 'BODYWEIGHT' | 'KETTLEBELL' | 'BAND' | 'OTHER'

// A planned set's classification — see
// supabase/migrations/20260915120000_add_planned_set_type.sql. Unlike ExerciseType/Equipment (which
// can be null for a pre-metadata row), every set genuinely has one of these four values — the
// database column is NOT NULL with a 'NORMAL' default, never null here.
export type SetType = 'NORMAL' | 'WARMUP' | 'FAILURE' | 'DROPSET'

export interface ExerciseSummary {
  id: string
  name: string
  /** null = built-in, visible to everyone. A real user id = a custom exercise, visible only to its owner. */
  ownerId: string | null
  /** Null for exercises created before this metadata existed — never guessed/backfilled. */
  exerciseType: ExerciseType | null
  equipment: Equipment | null
}

export interface PlannedSet {
  id: string
  position: number
  setType: SetType
  targetReps: number | null
  targetWeight: number | null
}

export interface RoutineExercise {
  id: string
  exerciseId: string
  exerciseName: string
  position: number
  plannedSets: PlannedSet[]
  /** Pinned to this routine-exercise slot, not the exercise definition — see
   * supabase/migrations/20260914120000_add_routine_exercise_note_and_rest_timer.sql. Null is the
   * normal, common case (most exercises carry no note), never a signal of an incomplete record. */
  note: string | null
  /** Seconds. Null means "Off" (no rest timer configured) — same blank-means-null convention as
   * PlannedSet's targetReps/targetWeight, never coerced to 0. */
  restTimerSeconds: number | null
}

export interface Routine {
  id: string
  name: string
  createdAt: string
  updatedAt: string
  exercises: RoutineExercise[]
}

// Local-only draft shapes used by the Create/Edit Routine editor before a save. `id` on a draft is
// a client-generated key (crypto.randomUUID()) for React list identity only — it is never sent to
// the database; save_routine() assigns real row ids and positions from array order.
export interface PlannedSetDraft {
  id: string
  targetReps: number | null
  targetWeight: number | null
  setType: SetType
}

export interface RoutineExerciseDraft {
  id: string
  exerciseId: string
  exerciseName: string
  plannedSets: PlannedSetDraft[]
  note: string | null
  restTimerSeconds: number | null
}

export interface HistoryEntry {
  id: string
  title: string
  dateLabel: string
  summary: string
  exerciseLines: string[]
}

// Program / ProgramWeek / ProgramSession below are REAL, Supabase-backed — see
// supabase/migrations/20260911120000_create_programs.sql and
// docs/product/frontend-prototype-notes.md. Program is OPTIONAL and ADDITIVE: nothing here is
// required to create/edit/use a standalone Routine (see the Routine types above, unchanged).
//
// A ProgramSession's exercises/planned sets reuse the exact same draft shapes as Routine
// (RoutineExerciseDraft/PlannedSetDraft) — the two are structurally identical "planned
// prescription" concepts (see PlannedExercisesEditor.tsx), even though they are backed by
// completely separate Postgres tables (program_session_exercises/program_session_planned_sets),
// never routine_exercises/routine_planned_sets. Reusing the TS draft shape is not the same claim as
// reusing the database rows — the snapshot invariant lives in the schema, not in these types.

export interface ProgramSessionPlannedSet {
  id: string
  targetReps: number | null
  targetWeight: number | null
}

export interface ProgramSessionExercise {
  id: string
  exerciseId: string
  exerciseName: string
  plannedSets: ProgramSessionPlannedSet[]
}

export interface ProgramSession {
  id: string
  position: number
  /** null = no explicit name; the UI derives "Session N" from position/array index instead. */
  name: string | null
  /** Provenance only — never re-read to determine this session's current content. */
  sourceRoutineId: string | null
  exercises: ProgramSessionExercise[]
}

export interface ProgramWeek {
  id: string
  position: number
  sessions: ProgramSession[]
}

export interface Program {
  id: string
  name: string
  createdAt: string
  updatedAt: string
  weeks: ProgramWeek[]
}
