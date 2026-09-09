import type { Equipment, ExerciseType } from '../types'

// Centralized display-label mapping for Exercise metadata — see
// supabase/migrations/20260911180000_add_exercise_metadata.sql for why the stored values are stable
// uppercase codes (a database contract, not UI copy) while the labels below are what a user actually
// sees. Kept in one place so no component hand-rolls its own string conversion.

const EXERCISE_TYPE_LABELS: Record<ExerciseType, string> = {
  WEIGHT_REPS: 'Weight & Reps',
  BODYWEIGHT_REPS: 'Bodyweight & Reps',
}

const EQUIPMENT_LABELS: Record<Equipment, string> = {
  BARBELL: 'Barbell',
  DUMBBELL: 'Dumbbell',
  MACHINE: 'Machine',
  CABLE: 'Cable',
  BODYWEIGHT: 'Bodyweight',
  KETTLEBELL: 'Kettlebell',
  BAND: 'Band',
  OTHER: 'Other',
}

export const EXERCISE_TYPE_OPTIONS: ExerciseType[] = ['WEIGHT_REPS', 'BODYWEIGHT_REPS']
export const EQUIPMENT_OPTIONS: Equipment[] = ['BARBELL', 'DUMBBELL', 'MACHINE', 'CABLE', 'BODYWEIGHT', 'KETTLEBELL', 'BAND', 'OTHER']

export function exerciseTypeLabel(value: ExerciseType | null): string {
  return value ? EXERCISE_TYPE_LABELS[value] : 'Unspecified'
}

export function equipmentLabel(value: Equipment | null): string {
  return value ? EQUIPMENT_LABELS[value] : 'Unspecified'
}
