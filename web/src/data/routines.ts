import { supabase } from '../lib/supabaseClient'
import type { ExerciseSummary, PlannedSetDraft, Routine, RoutineExercise, RoutineExerciseDraft, SetType } from '../types'

// The Web Routine data boundary — every real Supabase read/write for Routines goes through this
// module, not scattered inline `supabase.from(...)` calls in page components (see the review
// brief's explicit "do not let React components contain large raw Supabase query implementations"
// instruction). Small and domain-specific on purpose — not a generic repository framework.

const ROUTINE_SELECT =
  'id, name, created_at, updated_at, ' +
  'routine_exercises(id, position, exercise_id, note, rest_timer_seconds, exercises(name), ' +
  'routine_planned_sets(id, position, target_reps, target_weight, set_type))'

interface PlannedSetRow {
  id: string
  position: number
  target_reps: number | null
  target_weight: number | null
  set_type: SetType
}

interface RoutineExerciseRow {
  id: string
  position: number
  exercise_id: string
  note: string | null
  rest_timer_seconds: number | null
  // A single embedded-to-one relationship comes back as an object; supabase-js's generic typing
  // can't express that without generated types (none exist for this project yet), so this is
  // asserted at the mapping boundary below, not left as `any`.
  exercises: { name: string } | null
  routine_planned_sets: PlannedSetRow[]
}

interface RoutineRow {
  id: string
  name: string
  created_at: string
  updated_at: string
  routine_exercises: RoutineExerciseRow[]
}

// Pure, exported (and unit-tested — see routines.test.ts) specifically so the row→domain mapping
// is verifiable without a live Supabase connection.
export function mapRoutineRow(row: RoutineRow): Routine {
  return {
    id: row.id,
    name: row.name,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    exercises: [...row.routine_exercises]
      .sort((a, b) => a.position - b.position)
      .map((re) => ({
        id: re.id,
        exerciseId: re.exercise_id,
        exerciseName: re.exercises?.name ?? 'Unknown exercise',
        position: re.position,
        note: re.note,
        restTimerSeconds: re.rest_timer_seconds,
        plannedSets: [...re.routine_planned_sets]
          .sort((a, b) => a.position - b.position)
          .map((s) => ({
            id: s.id,
            position: s.position,
            targetReps: s.target_reps,
            targetWeight: s.target_weight,
            setType: s.set_type,
          })),
      })),
  }
}

export async function listMyRoutines(): Promise<Routine[]> {
  const { data, error } = await supabase
    .from('routines')
    .select(ROUTINE_SELECT)
    .order('created_at', { ascending: false })
    .order('position', { referencedTable: 'routine_exercises', ascending: true })
    .order('position', { referencedTable: 'routine_exercises.routine_planned_sets', ascending: true })
  if (error) throw error
  return (data as unknown as RoutineRow[]).map(mapRoutineRow)
}

export async function getRoutine(id: string): Promise<Routine | null> {
  const { data, error } = await supabase.from('routines').select(ROUTINE_SELECT).eq('id', id).maybeSingle()
  if (error) throw error
  return data ? mapRoutineRow(data as unknown as RoutineRow) : null
}

export async function deleteRoutine(id: string): Promise<void> {
  const { error } = await supabase.from('routines').delete().eq('id', id)
  if (error) throw error
}

// Strips local-only draft ids (React keys) and shapes the payload exactly as save_routine(...)
// expects — pure and unit-tested (see routines.test.ts) so the request shape is verifiable without
// a live network call.
export function toSaveRoutinePayload(exercises: RoutineExerciseDraft[]): {
  exerciseId: string
  note: string | null
  restTimerSeconds: number | null
  plannedSets: { targetReps: number | null; targetWeight: number | null; setType: SetType }[]
}[] {
  return exercises.map((exercise) => ({
    exerciseId: exercise.exerciseId,
    note: exercise.note,
    restTimerSeconds: exercise.restTimerSeconds,
    plannedSets: exercise.plannedSets.map((set: PlannedSetDraft) => ({
      targetReps: set.targetReps,
      targetWeight: set.targetWeight,
      setType: set.setType,
    })),
  }))
}

// The one atomic write path for Routine content (create AND edit) — see the migration's own doc
// comment for why this is an RPC rather than several independent .insert()/.update() calls.
// `routineId: null` creates a new Routine; a real id updates (and fully replaces the planned
// content of) an existing one the caller owns.
export async function saveRoutine(
  routineId: string | null,
  name: string,
  exercises: RoutineExerciseDraft[],
): Promise<string> {
  const { data, error } = await supabase.rpc('save_routine', {
    p_routine_id: routineId,
    p_name: name,
    p_exercises: toSaveRoutinePayload(exercises),
  })
  if (error) throw error
  return data as string
}

// Converts an already-fetched Routine's read shape (real row ids/positions) into the editor's draft
// shape (RoutineEditorPage.tsx's own local-edit state, also what duplicateRoutine below feeds into
// saveRoutine). Reusing the original routine_exercise/planned_set ids as draft ids is safe: they're
// local-only React-list-identity values here, never sent to the server — toSaveRoutinePayload
// strips them before the RPC call, same as a freshly-generated crypto.randomUUID() would be.
export function toRoutineExerciseDrafts(exercises: RoutineExercise[]): RoutineExerciseDraft[] {
  return exercises.map((e) => ({
    id: e.id,
    exerciseId: e.exerciseId,
    exerciseName: e.exerciseName,
    note: e.note,
    restTimerSeconds: e.restTimerSeconds,
    plannedSets: e.plannedSets.map((s) => ({ id: s.id, targetReps: s.targetReps, targetWeight: s.targetWeight, setType: s.setType })),
  }))
}

// A real, independent copy — not a reference. Goes through the exact same save_routine(routineId:
// null, ...) path a brand-new Create Routine save does, so the result is a fully separate row from
// the moment it's created: editing/deleting the original afterward never touches the duplicate, and
// vice versa (the same "copy, not live reference" invariant already established for
// copy_routine_to_program_session). No new RPC or migration needed — duplicating is just "create a
// routine whose starting content happens to match an existing one."
export async function duplicateRoutine(routine: Routine): Promise<string> {
  return saveRoutine(null, `${routine.name} (copy)`, toRoutineExerciseDrafts(routine.exercises))
}

const EXERCISE_SELECT = 'id, name, owner_id, exercise_type, equipment'

interface ExerciseRow {
  id: string
  name: string
  owner_id: string | null
  exercise_type: ExerciseSummary['exerciseType']
  equipment: ExerciseSummary['equipment']
}

export function mapExerciseRow(row: ExerciseRow): ExerciseSummary {
  return { id: row.id, name: row.name, ownerId: row.owner_id, exerciseType: row.exercise_type, equipment: row.equipment }
}

export async function listVisibleExercises(): Promise<ExerciseSummary[]> {
  const { data, error } = await supabase.from('exercises').select(EXERCISE_SELECT).order('name')
  if (error) throw error
  return (data ?? []).map(mapExerciseRow)
}

// exerciseType/equipment are required for a NEW custom exercise created through this function (the
// Web form always collects both — see ExerciseLibraryPanel.tsx/PlannedExercisesEditor.tsx) even
// though the database column itself remains nullable, to correctly represent exercises created
// before this metadata existed (see the migration's own comment) without forcing that same
// nullability onto every future caller of this function.
export async function createCustomExercise(
  name: string,
  exerciseType: NonNullable<ExerciseSummary['exerciseType']>,
  equipment: NonNullable<ExerciseSummary['equipment']>,
): Promise<ExerciseSummary> {
  const trimmed = name.trim()
  if (!trimmed) throw new Error('Exercise name must not be empty')
  const {
    data: { session },
  } = await supabase.auth.getSession()
  const ownerId = session?.user.id
  if (!ownerId) throw new Error('Not signed in')
  const { data, error } = await supabase
    .from('exercises')
    .insert({ name: trimmed, owner_id: ownerId, exercise_type: exerciseType, equipment })
    .select(EXERCISE_SELECT)
    .single()
  if (error) throw error
  return mapExerciseRow(data)
}
