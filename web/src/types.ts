// PROTOTYPE-ONLY types — see docs/product/frontend-prototype-notes.md. No backend/domain model
// exists for any of these on any TBDFit client yet (Routine/RoutineExercise, Workout history,
// Profile beyond auth). Deliberately using the SAME vocabulary as the Phone prototype
// (Workout / Routine / Exercise / History / Progress / Profile) per ADR-005's "same product,
// specialized clients" direction — the shapes below are NOT required to match Phone's Kotlin
// entities field-for-field; only the concepts must read as the same product.

export interface RoutineExercise {
  id: string
  name: string
  plannedSets: number
  plannedReps: number
}

export interface Routine {
  id: string
  name: string
  exercises: RoutineExercise[]
}

export interface HistoryEntry {
  id: string
  title: string
  dateLabel: string
  summary: string
  exerciseLines: string[]
}
