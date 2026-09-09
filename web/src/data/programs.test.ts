import { describe, expect, it } from 'vitest'
import { mapProgramRow, toSaveProgramSessionPayload } from './programs'
import type { RoutineExerciseDraft } from '../types'

// Pure-logic tests only — mirrors routines.test.ts's own reasoning: the row<->payload mapping is
// exactly where a silent bug (wrong nesting, wrong key name, lost ordering across four levels of
// nesting) would be most costly and least visible from casual manual testing.

describe('mapProgramRow', () => {
  it('maps a deeply nested Supabase row into the domain Program shape, ordered by position at every level', () => {
    const row = {
      id: 'program-1',
      name: '12 Week Strength',
      created_at: '2026-09-11T00:00:00Z',
      updated_at: '2026-09-11T00:00:00Z',
      program_weeks: [
        {
          id: 'week-2',
          position: 1,
          program_sessions: [
            { id: 'session-2a', position: 0, name: null, source_routine_id: null, program_session_exercises: [] },
          ],
        },
        {
          id: 'week-1',
          position: 0,
          program_sessions: [
            {
              id: 'session-1b',
              position: 1,
              name: 'Pull A',
              source_routine_id: 'routine-pull',
              program_session_exercises: [],
            },
            {
              id: 'session-1a',
              position: 0,
              name: 'Push A',
              source_routine_id: 'routine-push',
              program_session_exercises: [
                {
                  id: 'pse-1',
                  position: 0,
                  exercise_id: 'ex-bench-press',
                  exercises: { name: 'Bench Press' },
                  program_session_planned_sets: [
                    { id: 'set-2', position: 1, target_reps: 8, target_weight: 80 },
                    { id: 'set-1', position: 0, target_reps: 8, target_weight: 80 },
                  ],
                },
              ],
            },
          ],
        },
      ],
    }

    const program = mapProgramRow(row)

    expect(program.id).toBe('program-1')
    // Weeks re-sorted by position even though the row arrived out of order.
    expect(program.weeks.map((w) => w.id)).toEqual(['week-1', 'week-2'])
    // Sessions within a week re-sorted by position too.
    expect(program.weeks[0].sessions.map((s) => s.name)).toEqual(['Push A', 'Pull A'])
    expect(program.weeks[0].sessions[0].sourceRoutineId).toBe('routine-push')
    // Exercises and their planned sets re-sorted by position, four levels deep.
    expect(program.weeks[0].sessions[0].exercises[0].plannedSets.map((s) => s.id)).toEqual(['set-1', 'set-2'])
    expect(program.weeks[0].sessions[0].exercises[0].plannedSets[0]).toMatchObject({ targetReps: 8, targetWeight: 80 })
  })

  it('preserves a null session name (the UI derives "Session N" from array index, not stored here)', () => {
    const row = {
      id: 'program-1',
      name: 'Empty Program',
      created_at: '2026-09-11T00:00:00Z',
      updated_at: '2026-09-11T00:00:00Z',
      program_weeks: [
        { id: 'week-1', position: 0, program_sessions: [{ id: 's1', position: 0, name: null, source_routine_id: null, program_session_exercises: [] }] },
      ],
    }

    const program = mapProgramRow(row)

    expect(program.weeks[0].sessions[0].name).toBeNull()
  })

  it('handles a program with no weeks', () => {
    const row = {
      id: 'program-1',
      name: 'Blank Program',
      created_at: '2026-09-11T00:00:00Z',
      updated_at: '2026-09-11T00:00:00Z',
      program_weeks: [],
    }

    expect(mapProgramRow(row).weeks).toEqual([])
  })

  it('falls back to a placeholder exercise name if the embedded exercise is missing (should not happen under RLS, but must not crash)', () => {
    const row = {
      id: 'program-1',
      name: 'Program',
      created_at: '2026-09-11T00:00:00Z',
      updated_at: '2026-09-11T00:00:00Z',
      program_weeks: [
        {
          id: 'week-1',
          position: 0,
          program_sessions: [
            {
              id: 's1',
              position: 0,
              name: null,
              source_routine_id: null,
              program_session_exercises: [{ id: 'pse-1', position: 0, exercise_id: 'ex-1', exercises: null, program_session_planned_sets: [] }],
            },
          ],
        },
      ],
    }

    const program = mapProgramRow(row)

    expect(program.weeks[0].sessions[0].exercises[0].exerciseName).toBe('Unknown exercise')
  })
})

describe('toSaveProgramSessionPayload', () => {
  it('strips local-only draft ids and preserves array order as the save_program_session position source', () => {
    const drafts: RoutineExerciseDraft[] = [
      {
        id: 'local-draft-1',
        exerciseId: 'ex-bench-press',
        exerciseName: 'Bench Press',
        plannedSets: [
          { id: 'local-set-1', targetReps: 5, targetWeight: 100 },
          { id: 'local-set-2', targetReps: null, targetWeight: null },
        ],
      },
    ]

    const payload = toSaveProgramSessionPayload(drafts)

    expect(payload).toEqual([
      {
        exerciseId: 'ex-bench-press',
        plannedSets: [
          { targetReps: 5, targetWeight: 100 },
          { targetReps: null, targetWeight: null },
        ],
      },
    ])
    expect(Object.keys(payload[0])).toEqual(['exerciseId', 'plannedSets'])
  })

  it('produces an empty array for a session with no exercises (a valid, saveable state)', () => {
    expect(toSaveProgramSessionPayload([])).toEqual([])
  })
})
