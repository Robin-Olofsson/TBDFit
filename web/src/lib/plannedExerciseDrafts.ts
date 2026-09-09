import type { ExerciseSummary, PlannedSetDraft, RoutineExerciseDraft } from '../types'

// Shared by PlannedExercisesEditor.tsx and ExerciseLibraryPanel.tsx's callers (RoutineEditorPage,
// ProgramBuilderPage's SessionEditor) — kept in its own pure-logic module rather than exported
// alongside a component so neither file trips oxlint's react-refresh/only-export-components rule.
export function newSetDraft(): PlannedSetDraft {
  return { id: crypto.randomUUID(), targetReps: null, targetWeight: null }
}

// Builds a fresh draft entry (one starter planned set) for an exercise chosen from any
// picker/library UI, so both the inline toggled picker and a page-level persistent library panel
// produce identically-shaped entries.
export function buildRoutineExerciseDraft(exercise: ExerciseSummary): RoutineExerciseDraft {
  return { id: crypto.randomUUID(), exerciseId: exercise.id, exerciseName: exercise.name, plannedSets: [newSetDraft()] }
}
