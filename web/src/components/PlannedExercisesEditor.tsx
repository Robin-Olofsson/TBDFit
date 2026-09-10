import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Dumbbell } from 'lucide-react'
import { createCustomExercise, listVisibleExercises } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { SET_TYPE_OPTIONS, buildRoutineExerciseDraft, newSetDraft, setTypeLetter } from '../lib/plannedExerciseDrafts'
import RestTimerPicker from './RestTimerPicker'
import SelectField from './SelectField'
import { EQUIPMENT_OPTIONS, EXERCISE_TYPE_OPTIONS, equipmentLabel, exerciseTypeLabel } from '../lib/exerciseLabels'
import type { Equipment, ExerciseSummary, ExerciseType, RoutineExerciseDraft, SetType } from '../types'

// Shared planned-prescription editor: the exercise/set editing UI extracted from
// RoutineEditorPage.tsx so it can serve BOTH a standalone Routine's editor and a Program's
// per-session editor (see ProgramBuilderPage.tsx) without duplicating this logic. `RoutineExerciseDraft`/
// `PlannedSetDraft` are reused as-is for Program sessions too — they are the same "planned
// prescription" shape in both cases (see types.ts's own comment on ProgramSession) even though the
// two save paths (saveRoutine vs. saveProgramSession) write to entirely separate Postgres tables.
// This component owns only in-memory draft state via controlled props; it never calls a save
// function itself — the caller decides when/how to persist (one atomic RPC call either way).

interface Props {
  exercises: RoutineExerciseDraft[]
  onChange: (exercises: RoutineExerciseDraft[]) => void
  // Default true (Program's SessionEditor keeps its exact existing behavior unchanged). RoutineEditorPage
  // sets this to false and renders a persistent ExerciseLibraryPanel alongside instead — see that
  // page's own comment for why a toggled inline picker and an always-visible side panel shouldn't
  // both be shown at once.
  showPicker?: boolean
  // Default true (Routine's own editor). Program's SessionEditor sets this to false: neither
  // program_session_exercises nor program_session_planned_sets has a note/rest_timer_seconds/
  // set_type column at all (see supabase/migrations/20260914120000_add_routine_exercise_note_and_rest_timer.sql
  // and 20260915120000_add_planned_set_type.sql — both slices were deliberately scoped to Routine
  // only), and neither field is read by toSaveProgramSessionPayload — showing these controls there
  // would silently discard whatever the user picked/typed into them, which is worse than not
  // offering them at all. Covers the Note field, Rest Timer picker, AND the per-set Set Type
  // selector — all three are Routine-only, not just the first two despite the prop's name predating
  // Set Type's addition.
  showNoteAndRestTimer?: boolean
}

export default function PlannedExercisesEditor({ exercises, onChange, showPicker = true, showNoteAndRestTimer = true }: Props) {
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()
  const [pickerOpen, setPickerOpen] = useState(false)
  const [newExerciseName, setNewExerciseName] = useState('')
  const [newExerciseType, setNewExerciseType] = useState<ExerciseType>('WEIGHT_REPS')
  const [newEquipment, setNewEquipment] = useState<Equipment>('BARBELL')

  // Built-ins essentially never change and customs are only added through this same component's
  // own mutation below (which updates this exact cache entry directly) — a longer staleTime than
  // Routine/Program data is justified here specifically (see docs/architecture/web-server-state-cache.md's
  // Exercise Catalog Caching section). The query only runs once the picker is actually opened.
  const { data: visibleExercises, isLoading, error: queryError } = useQuery({
    queryKey: queryKeys.exercises.list(userId),
    queryFn: listVisibleExercises,
    staleTime: 30 * 60 * 1000,
    enabled: pickerOpen,
  })

  const createExerciseMutation = useMutation({
    mutationFn: () => createCustomExercise(newExerciseName, newExerciseType, newEquipment),
    onSuccess: (created) => {
      queryClient.setQueryData(queryKeys.exercises.list(userId), (prev: ExerciseSummary[] | undefined) =>
        prev ? [...prev, created] : [created],
      )
      setNewExerciseName('')
      addExercise(created)
    },
  })

  const error = queryError instanceof Error ? queryError.message : createExerciseMutation.error instanceof Error ? createExerciseMutation.error.message : null

  const addExercise = (exercise: ExerciseSummary) => {
    onChange([...exercises, buildRoutineExerciseDraft(exercise)])
    setPickerOpen(false)
  }

  const handleCreateCustomExercise = () => {
    const trimmed = newExerciseName.trim()
    if (!trimmed) return
    createExerciseMutation.mutate()
  }

  const removeExercise = (exerciseDraftId: string) => {
    onChange(exercises.filter((e) => e.id !== exerciseDraftId))
  }

  const addSet = (exerciseDraftId: string) => {
    onChange(exercises.map((e) => (e.id === exerciseDraftId ? { ...e, plannedSets: [...e.plannedSets, newSetDraft()] } : e)))
  }

  const removeSet = (exerciseDraftId: string, setId: string) => {
    onChange(exercises.map((e) => (e.id === exerciseDraftId ? { ...e, plannedSets: e.plannedSets.filter((s) => s.id !== setId) } : e)))
  }

  const updateSetType = (exerciseDraftId: string, setId: string, setType: SetType) => {
    onChange(
      exercises.map((e) =>
        e.id === exerciseDraftId ? { ...e, plannedSets: e.plannedSets.map((s) => (s.id === setId ? { ...s, setType } : s)) } : e,
      ),
    )
  }

  // Blank means "no note" (persisted as null), same convention as updateSetField below — never an
  // empty-string row value.
  const updateExerciseNote = (exerciseDraftId: string, rawValue: string) => {
    onChange(exercises.map((e) => (e.id === exerciseDraftId ? { ...e, note: rawValue.trim() === '' ? null : rawValue } : e)))
  }

  // Picked from RestTimerPicker's fixed Off/00:05/…/05:00 list — always a definite, already-valid
  // value (or null for Off), so no parsing/rejection is needed here the way updateSetField's
  // open-ended number inputs require.
  const updateExerciseRestTimer = (exerciseDraftId: string, seconds: number | null) => {
    onChange(exercises.map((e) => (e.id === exerciseDraftId ? { ...e, restTimerSeconds: seconds } : e)))
  }

  // Blank means "no target" (persisted as null) — never silently coerced to zero. An unparseable,
  // non-blank value is ignored (the input keeps showing what the user typed; the draft value is
  // simply not updated until it becomes blank or a real number) — same contract
  // Android's SetInputParsing.kt establishes for the same concept.
  const updateSetField = (exerciseDraftId: string, setId: string, field: 'targetReps' | 'targetWeight', rawValue: string) => {
    if (rawValue.trim() === '') {
      onChange(
        exercises.map((e) =>
          e.id === exerciseDraftId ? { ...e, plannedSets: e.plannedSets.map((s) => (s.id === setId ? { ...s, [field]: null } : s)) } : e,
        ),
      )
      return
    }
    const parsed = Number(rawValue)
    if (Number.isNaN(parsed) || parsed < 0) return
    onChange(
      exercises.map((e) =>
        e.id === exerciseDraftId ? { ...e, plannedSets: e.plannedSets.map((s) => (s.id === setId ? { ...s, [field]: parsed } : s)) } : e,
      ),
    )
  }

  return (
    <div>
      {error && <p className="form-error">{error}</p>}

      {exercises.length === 0 && (
        <div className="exercise-empty-state">
          <Dumbbell size={32} aria-hidden="true" />
          <p className="exercise-empty-state-title">No Exercises</p>
          <p className="page-subtitle">So far, you haven&apos;t added any exercises to this routine.</p>
        </div>
      )}

      {exercises.map((exercise) => (
        <div key={exercise.id} className="routine-editor-exercise">
          <div className="routine-editor-exercise-header">
            <h2>{exercise.exerciseName}</h2>
            <button type="button" className="btn-link btn-link-danger" onClick={() => removeExercise(exercise.id)}>
              Remove exercise
            </button>
          </div>

          {showNoteAndRestTimer && (
            <>
              <div className="routine-editor-exercise-note-field">
                <label className="field-label" htmlFor={`note-${exercise.id}`}>
                  Note
                </label>
                <textarea
                  id={`note-${exercise.id}`}
                  className="table-input modal-field-input"
                  rows={2}
                  placeholder="Add pinned note"
                  defaultValue={exercise.note ?? ''}
                  onChange={(e) => updateExerciseNote(exercise.id, e.target.value)}
                />
              </div>

              <div className="routine-editor-exercise-rest-timer-field">
                <label className="field-label" htmlFor={`rest-timer-${exercise.id}`}>
                  Rest Timer
                </label>
                <RestTimerPicker
                  id={`rest-timer-${exercise.id}`}
                  value={exercise.restTimerSeconds}
                  onChange={(seconds) => updateExerciseRestTimer(exercise.id, seconds)}
                />
              </div>
            </>
          )}

          <table className="data-table">
            <thead>
              <tr>
                <th>Set</th>
                <th>Target weight (kg)</th>
                <th>Target reps</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {exercise.plannedSets.map((set, index) => (
                <tr key={set.id}>
                  <td>
                    {showNoteAndRestTimer && (
                      <SelectField
                        className="set-type-select"
                        ariaLabel={`Set ${index + 1} type`}
                        value={set.setType}
                        triggerLabel={setTypeLetter(set.setType)}
                        options={SET_TYPE_OPTIONS.map((option) => ({ value: option.code, label: option.label }))}
                        onChange={(setType) => updateSetType(exercise.id, set.id, setType)}
                      />
                    )}
                  </td>
                  <td>
                    <input
                      className="table-input"
                      type="number"
                      min={0}
                      step="0.5"
                      defaultValue={set.targetWeight ?? ''}
                      onChange={(e) => updateSetField(exercise.id, set.id, 'targetWeight', e.target.value)}
                    />
                  </td>
                  <td>
                    <input
                      className="table-input"
                      type="number"
                      min={0}
                      defaultValue={set.targetReps ?? ''}
                      onChange={(e) => updateSetField(exercise.id, set.id, 'targetReps', e.target.value)}
                    />
                  </td>
                  <td>
                    {exercise.plannedSets.length > 1 && (
                      <button type="button" className="btn-link btn-link-danger" onClick={() => removeSet(exercise.id, set.id)}>
                        Remove set
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button type="button" className="btn-secondary" onClick={() => addSet(exercise.id)}>
            + Add Set
          </button>
        </div>
      ))}

      {showPicker &&
        (pickerOpen ? (
          <div className="exercise-picker">
            <p className="page-subtitle">Add an exercise</p>
            <div className="exercise-picker-list">
              {isLoading ? (
                <p className="page-subtitle">Loading…</p>
              ) : (
                (visibleExercises ?? []).map((exercise) => (
                  <button key={exercise.id} className="exercise-picker-item" onClick={() => addExercise(exercise)}>
                    {exercise.name}
                    <span className="exercise-library-item-meta">
                      {' '}
                      · {exerciseTypeLabel(exercise.exerciseType)} · {equipmentLabel(exercise.equipment)}
                    </span>
                  </button>
                ))
              )}
            </div>
            <div className="exercise-picker-create">
              <input
                className="table-input"
                type="text"
                placeholder="New exercise name"
                value={newExerciseName}
                onChange={(e) => setNewExerciseName(e.target.value)}
              />
              <SelectField
                ariaLabel="New exercise type"
                value={newExerciseType}
                options={EXERCISE_TYPE_OPTIONS.map((type) => ({ value: type, label: exerciseTypeLabel(type) }))}
                onChange={setNewExerciseType}
              />
              <SelectField
                ariaLabel="New exercise equipment"
                value={newEquipment}
                options={EQUIPMENT_OPTIONS.map((equipment) => ({ value: equipment, label: equipmentLabel(equipment) }))}
                onChange={setNewEquipment}
              />
              <button type="button" className="btn-secondary" disabled={createExerciseMutation.isPending} onClick={handleCreateCustomExercise}>
                + Create &amp; add
              </button>
            </div>
            <button type="button" className="btn-secondary" onClick={() => setPickerOpen(false)}>
              Cancel
            </button>
          </div>
        ) : (
          <button type="button" className="btn-secondary" onClick={() => setPickerOpen(true)}>
            + Add Exercise
          </button>
        ))}
    </div>
  )
}
