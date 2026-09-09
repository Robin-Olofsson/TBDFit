import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Dumbbell } from 'lucide-react'
import { createCustomExercise, listVisibleExercises } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { newSetDraft } from '../lib/plannedExerciseDrafts'
import { EQUIPMENT_OPTIONS, EXERCISE_TYPE_OPTIONS, equipmentLabel, exerciseTypeLabel } from '../lib/exerciseLabels'
import type { Equipment, ExerciseSummary, ExerciseType, RoutineExerciseDraft } from '../types'

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
}

export default function PlannedExercisesEditor({ exercises, onChange, showPicker = true }: Props) {
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
    onChange([...exercises, { id: crypto.randomUUID(), exerciseId: exercise.id, exerciseName: exercise.name, plannedSets: [newSetDraft()] }])
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
          <table className="data-table">
            <thead>
              <tr>
                <th></th>
                <th>Target reps</th>
                <th>Target weight (kg)</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {exercise.plannedSets.map((set, index) => (
                <tr key={set.id}>
                  <td>Set {index + 1}</td>
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
                    <button type="button" className="btn-link btn-link-danger" onClick={() => removeSet(exercise.id, set.id)}>
                      Remove set
                    </button>
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
              <select className="table-input" value={newExerciseType} onChange={(e) => setNewExerciseType(e.target.value as ExerciseType)}>
                {EXERCISE_TYPE_OPTIONS.map((type) => (
                  <option key={type} value={type}>
                    {exerciseTypeLabel(type)}
                  </option>
                ))}
              </select>
              <select className="table-input" value={newEquipment} onChange={(e) => setNewEquipment(e.target.value as Equipment)}>
                {EQUIPMENT_OPTIONS.map((equipment) => (
                  <option key={equipment} value={equipment}>
                    {equipmentLabel(equipment)}
                  </option>
                ))}
              </select>
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
