import { supabase } from '../lib/supabaseClient'
import type { Program, RoutineExerciseDraft } from '../types'

// The Web Program data boundary — mirrors src/data/routines.ts's small, domain-specific style
// exactly (see that file's own doc comment). Program is OPTIONAL and ADDITIVE: nothing here is a
// dependency of routines.ts, and nothing in routines.ts depends on this file.
//
// Mutation strategy: most single-row operations (create/rename/delete Program, delete Week/Session)
// are plain RLS-protected supabase-js calls — a single-row Postgres DELETE with ON DELETE CASCADE
// is already atomic at the database level regardless of how it's invoked, so no RPC is needed for
// those. Multi-row operations that must not leave a half-written state (copying a Routine's full
// content into a session, duplicating a whole week's tree, replacing one session's full exercise
// list) go through the narrowly-scoped Postgres functions in
// supabase/migrations/20260911120000_create_programs.sql instead — see that migration's own
// comments for why each one exists.

const PROGRAM_SELECT =
  'id, name, created_at, updated_at, ' +
  'program_weeks(id, position, ' +
  'program_sessions(id, position, name, source_routine_id, ' +
  'program_session_exercises(id, position, exercise_id, exercises(name), ' +
  'program_session_planned_sets(id, position, target_reps, target_weight))))'

interface PlannedSetRow {
  id: string
  position: number
  target_reps: number | null
  target_weight: number | null
}

interface SessionExerciseRow {
  id: string
  position: number
  exercise_id: string
  exercises: { name: string } | null
  program_session_planned_sets: PlannedSetRow[]
}

interface SessionRow {
  id: string
  position: number
  name: string | null
  source_routine_id: string | null
  program_session_exercises: SessionExerciseRow[]
}

interface WeekRow {
  id: string
  position: number
  program_sessions: SessionRow[]
}

interface ProgramRow {
  id: string
  name: string
  created_at: string
  updated_at: string
  program_weeks: WeekRow[]
}

// Pure, exported (and unit-tested — see programs.test.ts) specifically so the row->domain mapping
// is verifiable without a live Supabase connection — same rationale as routines.ts's mapRoutineRow.
export function mapProgramRow(row: ProgramRow): Program {
  return {
    id: row.id,
    name: row.name,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    weeks: [...row.program_weeks]
      .sort((a, b) => a.position - b.position)
      .map((w) => ({
        id: w.id,
        position: w.position,
        sessions: [...w.program_sessions]
          .sort((a, b) => a.position - b.position)
          .map((s) => ({
            id: s.id,
            position: s.position,
            name: s.name,
            sourceRoutineId: s.source_routine_id,
            exercises: [...s.program_session_exercises]
              .sort((a, b) => a.position - b.position)
              .map((e) => ({
                id: e.id,
                exerciseId: e.exercise_id,
                exerciseName: e.exercises?.name ?? 'Unknown exercise',
                plannedSets: [...e.program_session_planned_sets]
                  .sort((a, b) => a.position - b.position)
                  .map((set) => ({ id: set.id, targetReps: set.target_reps, targetWeight: set.target_weight })),
              })),
          })),
      })),
  }
}

export async function listMyPrograms(): Promise<Program[]> {
  const { data, error } = await supabase
    .from('programs')
    .select(PROGRAM_SELECT)
    .order('created_at', { ascending: false })
    .order('position', { referencedTable: 'program_weeks', ascending: true })
    .order('position', { referencedTable: 'program_weeks.program_sessions', ascending: true })
    .order('position', { referencedTable: 'program_weeks.program_sessions.program_session_exercises', ascending: true })
    .order('position', {
      referencedTable: 'program_weeks.program_sessions.program_session_exercises.program_session_planned_sets',
      ascending: true,
    })
  if (error) throw error
  return (data as unknown as ProgramRow[]).map(mapProgramRow)
}

export async function getProgram(id: string): Promise<Program | null> {
  const { data, error } = await supabase.from('programs').select(PROGRAM_SELECT).eq('id', id).maybeSingle()
  if (error) throw error
  return data ? mapProgramRow(data as unknown as ProgramRow) : null
}

export async function createProgram(name: string): Promise<Program> {
  const trimmed = name.trim()
  if (!trimmed) throw new Error('Program name must not be empty')
  const {
    data: { session },
  } = await supabase.auth.getSession()
  const ownerId = session?.user.id
  if (!ownerId) throw new Error('Not signed in')
  const { data, error } = await supabase.from('programs').insert({ name: trimmed, owner_id: ownerId }).select(PROGRAM_SELECT).single()
  if (error) throw error
  return mapProgramRow(data as unknown as ProgramRow)
}

export async function renameProgram(id: string, name: string): Promise<void> {
  const trimmed = name.trim()
  if (!trimmed) throw new Error('Program name must not be empty')
  const { error } = await supabase.from('programs').update({ name: trimmed, updated_at: new Date().toISOString() }).eq('id', id)
  if (error) throw error
}

export async function deleteProgram(id: string): Promise<void> {
  const { error } = await supabase.from('programs').delete().eq('id', id)
  if (error) throw error
}

export async function addWeek(programId: string): Promise<string> {
  const { data, error } = await supabase.rpc('add_program_week', { p_program_id: programId })
  if (error) throw error
  return data as string
}

export async function deleteWeek(weekId: string): Promise<void> {
  const { error } = await supabase.from('program_weeks').delete().eq('id', weekId)
  if (error) throw error
}

export async function duplicateWeek(weekId: string): Promise<string> {
  const { data, error } = await supabase.rpc('duplicate_program_week', { p_program_week_id: weekId })
  if (error) throw error
  return data as string
}

export async function copyRoutineIntoWeek(routineId: string, weekId: string): Promise<string> {
  const { data, error } = await supabase.rpc('copy_routine_to_program_session', {
    p_routine_id: routineId,
    p_program_week_id: weekId,
  })
  if (error) throw error
  return data as string
}

export async function addSessionFromScratch(weekId: string, name?: string | null): Promise<string> {
  const { data, error } = await supabase.rpc('add_program_session', { p_program_week_id: weekId, p_name: name ?? null })
  if (error) throw error
  return data as string
}

export async function deleteSession(sessionId: string): Promise<void> {
  const { error } = await supabase.from('program_sessions').delete().eq('id', sessionId)
  if (error) throw error
}

// Strips local-only draft ids exactly like routines.ts's toSaveRoutinePayload — pure and
// unit-tested so the request shape is verifiable without a live network call.
export function toSaveProgramSessionPayload(exercises: RoutineExerciseDraft[]): {
  exerciseId: string
  plannedSets: { targetReps: number | null; targetWeight: number | null }[]
}[] {
  return exercises.map((exercise) => ({
    exerciseId: exercise.exerciseId,
    plannedSets: exercise.plannedSets.map((set) => ({ targetReps: set.targetReps, targetWeight: set.targetWeight })),
  }))
}

export async function saveProgramSession(sessionId: string, name: string | null, exercises: RoutineExerciseDraft[]): Promise<string> {
  const { data, error } = await supabase.rpc('save_program_session', {
    p_program_session_id: sessionId,
    p_name: name,
    p_exercises: toSaveProgramSessionPayload(exercises),
  })
  if (error) throw error
  return data as string
}
