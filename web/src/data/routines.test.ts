import { describe, expect, it } from 'vitest'
import { mapExerciseRow, mapRoutineRow, toRoutineExerciseDrafts, toSaveRoutinePayload } from './routines'
import { EQUIPMENT_OPTIONS, EXERCISE_TYPE_OPTIONS, equipmentLabel, exerciseTypeLabel } from '../lib/exerciseLabels'
import type { Routine, RoutineExerciseDraft } from '../types'

// Pure-logic tests only — no live Supabase connection, no component-rendering harness (this
// project's vitest setup has neither jsdom nor a component-testing library configured; adding one
// solely for this task would be exactly the "large new testing library" the review brief says not
// to introduce). What's genuinely unit-testable without either is the row<->payload mapping, which
// is also exactly where a silent bug (wrong nesting, wrong key name, lost ordering) would be most
// costly and least visible from casual manual testing.

describe('mapRoutineRow', () => {
  it('maps a nested Supabase row into the domain Routine shape, ordered by position', () => {
    const row = {
      id: 'routine-1',
      name: 'Push A',
      created_at: '2026-09-10T00:00:00Z',
      updated_at: '2026-09-10T00:00:00Z',
      routine_exercises: [
        {
          id: 're-2',
          position: 1,
          exercise_id: 'ex-overhead-press',
          note: null,
          rest_timer_seconds: null,
          exercises: { name: 'Overhead Press' },
          routine_planned_sets: [
            { id: 'set-b', position: 0, target_reps: 10, target_weight: null, set_type: 'NORMAL' as const },
          ],
        },
        {
          id: 're-1',
          position: 0,
          exercise_id: 'ex-bench-press',
          note: 'Keep elbows tucked',
          rest_timer_seconds: 90,
          exercises: { name: 'Bench Press' },
          routine_planned_sets: [
            { id: 'set-2', position: 1, target_reps: 8, target_weight: 80, set_type: 'NORMAL' as const },
            { id: 'set-1', position: 0, target_reps: 8, target_weight: 80, set_type: 'WARMUP' as const },
          ],
        },
      ],
    }

    const routine = mapRoutineRow(row)

    expect(routine.id).toBe('routine-1')
    expect(routine.name).toBe('Push A')
    // Exercises re-sorted by position even though the row arrived out of order.
    expect(routine.exercises.map((e) => e.exerciseName)).toEqual(['Bench Press', 'Overhead Press'])
    // Planned sets within an exercise re-sorted by position too.
    expect(routine.exercises[0].plannedSets.map((s) => s.id)).toEqual(['set-1', 'set-2'])
    expect(routine.exercises[0].plannedSets[0]).toMatchObject({ targetReps: 8, targetWeight: 80, setType: 'WARMUP' })
    // note/restTimerSeconds pass through verbatim per exercise, independently of each other.
    expect(routine.exercises[0]).toMatchObject({ note: 'Keep elbows tucked', restTimerSeconds: 90 })
    expect(routine.exercises[1]).toMatchObject({ note: null, restTimerSeconds: null })
    // set_type passes through per-set, independently of its sibling set's type.
    expect(routine.exercises[0].plannedSets.map((s) => s.setType)).toEqual(['WARMUP', 'NORMAL'])
  })

  it('falls back to a placeholder name if the embedded exercise is missing (should not happen under RLS, but must not crash)', () => {
    const row = {
      id: 'routine-1',
      name: 'Push A',
      created_at: '2026-09-10T00:00:00Z',
      updated_at: '2026-09-10T00:00:00Z',
      routine_exercises: [
        { id: 're-1', position: 0, exercise_id: 'ex-1', note: null, rest_timer_seconds: null, exercises: null, routine_planned_sets: [] },
      ],
    }

    const routine = mapRoutineRow(row)

    expect(routine.exercises[0].exerciseName).toBe('Unknown exercise')
  })

  it('handles a routine with no exercises', () => {
    const row = {
      id: 'routine-1',
      name: 'Empty Routine',
      created_at: '2026-09-10T00:00:00Z',
      updated_at: '2026-09-10T00:00:00Z',
      routine_exercises: [],
    }

    expect(mapRoutineRow(row).exercises).toEqual([])
  })
})

describe('toRoutineExerciseDrafts', () => {
  it('converts a Routine read shape into editor drafts, preserving values and reusing existing ids', () => {
    const exercises: Routine['exercises'] = [
      {
        id: 're-1',
        exerciseId: 'ex-bench-press',
        exerciseName: 'Bench Press',
        position: 0,
        note: 'Keep elbows tucked',
        restTimerSeconds: 90,
        plannedSets: [{ id: 'set-1', position: 0, targetReps: 8, targetWeight: 80, setType: 'WARMUP' }],
      },
    ]

    const drafts = toRoutineExerciseDrafts(exercises)

    expect(drafts).toEqual([
      {
        id: 're-1',
        exerciseId: 'ex-bench-press',
        exerciseName: 'Bench Press',
        note: 'Keep elbows tucked',
        restTimerSeconds: 90,
        plannedSets: [{ id: 'set-1', targetReps: 8, targetWeight: 80, setType: 'WARMUP' }],
      },
    ])
    // No `position` field leaks into the draft shape — array order is the position source once
    // fed into toSaveRoutinePayload/save_routine, per that function's own doc comment.
    expect(drafts[0]).not.toHaveProperty('position')
    expect(drafts[0].plannedSets[0]).not.toHaveProperty('position')
  })
})

describe('toSaveRoutinePayload', () => {
  it('strips local-only draft ids and preserves array order as the save_routine position source', () => {
    const drafts: RoutineExerciseDraft[] = [
      {
        id: 'local-draft-1',
        exerciseId: 'ex-bench-press',
        exerciseName: 'Bench Press',
        note: 'Keep elbows tucked',
        restTimerSeconds: 90,
        plannedSets: [
          { id: 'local-set-1', targetReps: 8, targetWeight: 80, setType: 'WARMUP' },
          { id: 'local-set-2', targetReps: null, targetWeight: null, setType: 'NORMAL' },
        ],
      },
    ]

    const payload = toSaveRoutinePayload(drafts)

    expect(payload).toEqual([
      {
        exerciseId: 'ex-bench-press',
        note: 'Keep elbows tucked',
        restTimerSeconds: 90,
        plannedSets: [
          { targetReps: 8, targetWeight: 80, setType: 'WARMUP' },
          { targetReps: null, targetWeight: null, setType: 'NORMAL' },
        ],
      },
    ])
    // No draft-only `id`/`exerciseName` field leaked into the network payload.
    expect(Object.keys(payload[0])).toEqual(['exerciseId', 'note', 'restTimerSeconds', 'plannedSets'])
  })

  it('passes through a freshly-added exercise\'s null note/restTimerSeconds explicitly, never omitting the keys', () => {
    const drafts: RoutineExerciseDraft[] = [
      { id: 'local-draft-1', exerciseId: 'ex-bench-press', exerciseName: 'Bench Press', note: null, restTimerSeconds: null, plannedSets: [] },
    ]

    expect(toSaveRoutinePayload(drafts)[0]).toMatchObject({ note: null, restTimerSeconds: null })
  })

  it('produces an empty array for a routine with no exercises (a valid, saveable state)', () => {
    expect(toSaveRoutinePayload([])).toEqual([])
  })
})

describe('mapExerciseRow', () => {
  it('maps a fully-populated exercise row (both metadata fields set)', () => {
    const row = { id: 'builtin_bench_press', name: 'Bench Press', owner_id: null, exercise_type: 'WEIGHT_REPS' as const, equipment: 'BARBELL' as const }

    expect(mapExerciseRow(row)).toEqual({
      id: 'builtin_bench_press',
      name: 'Bench Press',
      ownerId: null,
      exerciseType: 'WEIGHT_REPS',
      equipment: 'BARBELL',
    })
  })

  it('preserves null metadata for a pre-metadata exercise row rather than guessing a value', () => {
    const row = { id: 'ex-old-custom', name: 'Old Custom Move', owner_id: 'user-a', exercise_type: null, equipment: null }

    const mapped = mapExerciseRow(row)

    expect(mapped.exerciseType).toBeNull()
    expect(mapped.equipment).toBeNull()
  })
})

describe('exercise metadata labels', () => {
  it('maps every stable code to a human-readable label, never the raw code itself', () => {
    for (const type of EXERCISE_TYPE_OPTIONS) {
      expect(exerciseTypeLabel(type)).not.toBe(type)
    }
    for (const equipment of EQUIPMENT_OPTIONS) {
      expect(equipmentLabel(equipment)).not.toBe(equipment)
    }
  })

  it('renders an explicit "Unspecified" label for a null value rather than blank/undefined text', () => {
    expect(exerciseTypeLabel(null)).toBe('Unspecified')
    expect(equipmentLabel(null)).toBe('Unspecified')
  })

  it('exposes exactly the two currently-supported exercise types (no DURATION/DISTANCE) — see the migration\'s own comment for why', () => {
    expect(EXERCISE_TYPE_OPTIONS).toEqual(['WEIGHT_REPS', 'BODYWEIGHT_REPS'])
  })
})
