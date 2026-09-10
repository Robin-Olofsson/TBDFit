import type { ExerciseSummary, PlannedSetDraft, RoutineExerciseDraft, SetType } from '../types'

// Shared by PlannedExercisesEditor.tsx and ExerciseLibraryPanel.tsx's callers (RoutineEditorPage,
// ProgramBuilderPage's SessionEditor) — kept in its own pure-logic module rather than exported
// alongside a component so neither file trips oxlint's react-refresh/only-export-components rule.
export function newSetDraft(): PlannedSetDraft {
  return { id: crypto.randomUUID(), targetReps: null, targetWeight: null, setType: 'NORMAL' }
}

// The four Set Type options offered by the square selector in PlannedExercisesEditor.tsx's sets
// table, and reused by RoutineDetailPage.tsx's read-only letter badge — one source of truth for the
// code<->letter<->full-word mapping, matching the same code<->label pattern already established by
// exerciseLabels.ts for ExerciseType/Equipment. `letter` is the compact single-character UI display
// the developer asked for; `label` is the full word, used for the accessible name/screen readers.
export const SET_TYPE_OPTIONS: { code: SetType; letter: string; label: string }[] = [
  { code: 'NORMAL', letter: 'N', label: 'Normal' },
  { code: 'WARMUP', letter: 'W', label: 'Warm-up' },
  { code: 'FAILURE', letter: 'F', label: 'Failure' },
  { code: 'DROPSET', letter: 'D', label: 'Drop set' },
]

export function setTypeLetter(setType: SetType): string {
  return SET_TYPE_OPTIONS.find((option) => option.code === setType)?.letter ?? '?'
}

// Builds a fresh draft entry (one starter planned set) for an exercise chosen from any
// picker/library UI, so both the inline toggled picker and a page-level persistent library panel
// produce identically-shaped entries.
export function buildRoutineExerciseDraft(exercise: ExerciseSummary): RoutineExerciseDraft {
  return {
    id: crypto.randomUUID(),
    exerciseId: exercise.id,
    exerciseName: exercise.name,
    plannedSets: [newSetDraft()],
    note: null,
    restTimerSeconds: null,
  }
}

// mm:ss, zero-padded (e.g. 5 -> "00:05", 300 -> "05:00") — the single source of truth for how a
// rest-timer duration reads anywhere in the app, so the Rest Timer picker (PlannedExercisesEditor.tsx)
// and the read-only display (RoutineDetailPage.tsx) can never drift into showing the same stored
// value two different ways.
export function formatRestTimerLabel(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const remainderSeconds = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainderSeconds).padStart(2, '0')}`
}

const REST_TIMER_STEP_SECONDS = 5
const REST_TIMER_MAX_SECONDS = 5 * 60

// Off, then every 5 seconds up to 5 minutes (00:05, 00:10, ..., 05:00) — the developer's own spec
// for this exact range/step, chosen from a list (RestTimerPicker.tsx), not typed freely. `seconds:
// null` represents Off — the same "null means no value" convention `rest_timer_seconds` itself
// already uses. The database column has no opinion on which presets a client offers (any
// non-negative integer is valid), so this list is purely a UI concern, not a backend constraint.
export const REST_TIMER_OPTIONS: { label: string; seconds: number | null }[] = [
  { label: 'Off', seconds: null },
  ...Array.from({ length: REST_TIMER_MAX_SECONDS / REST_TIMER_STEP_SECONDS }, (_, index) => {
    const seconds = (index + 1) * REST_TIMER_STEP_SECONDS
    return { label: formatRestTimerLabel(seconds), seconds }
  }),
]
