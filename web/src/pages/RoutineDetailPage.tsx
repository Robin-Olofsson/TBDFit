import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { PROTOTYPE_EXERCISE_LIBRARY, PROTOTYPE_ROUTINES } from '../data/prototypeData'
import type { RoutineExercise } from '../types'

// PROTOTYPE-ONLY — Routine Detail / Builder concept. Interactive enough to judge the shape (reorder,
// planned sets/reps, add/remove exercises) per the review brief's section 7 requirement, but LOCAL
// COMPONENT STATE ONLY — edits here are never persisted; reloading the page resets to the original
// sample routine. No Routine backend exists on any TBDFit client (see
// product-information-architecture.md §5, web-information-architecture.md Journey W1).
let nextRowId = 1000

export default function RoutineDetailPage() {
  const { routineId } = useParams()
  const navigate = useNavigate()
  const original = useMemo(() => PROTOTYPE_ROUTINES.find((r) => r.id === routineId), [routineId])
  const [exercises, setExercises] = useState<RoutineExercise[]>(original?.exercises ?? [])
  const [pickerOpen, setPickerOpen] = useState(false)

  if (!original) {
    return (
      <div className="page">
        <p>Routine not found.</p>
        <button className="btn-secondary" onClick={() => navigate('/plan')}>
          &larr; Routine
        </button>
      </div>
    )
  }

  const moveExercise = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= exercises.length) return
    const next = [...exercises]
    ;[next[index], next[target]] = [next[target], next[index]]
    setExercises(next)
  }

  const updateField = (index: number, field: 'plannedSets' | 'plannedReps', value: number) => {
    const next = [...exercises]
    next[index] = { ...next[index], [field]: value }
    setExercises(next)
  }

  const removeExercise = (index: number) => {
    setExercises(exercises.filter((_, i) => i !== index))
  }

  const addExercise = (name: string) => {
    setExercises([...exercises, { id: `proto-added-${nextRowId++}`, name, plannedSets: 3, plannedReps: 8 }])
    setPickerOpen(false)
  }

  return (
    <div className="page">
      <button className="btn-link" onClick={() => navigate('/plan')}>
        &larr; Routine
      </button>
      <div className="page-header">
        <h1>{original.name}</h1>
      </div>
      <p className="page-subtitle">
        Prototype builder — reordering and edits here are local to this page view only, not saved.
      </p>

      <table className="data-table">
        <thead>
          <tr>
            <th style={{ width: 40 }}></th>
            <th>Exercise</th>
            <th style={{ width: 120 }}>Sets</th>
            <th style={{ width: 120 }}>Reps</th>
            <th style={{ width: 160 }}></th>
          </tr>
        </thead>
        <tbody>
          {exercises.map((exercise, index) => (
            <tr key={exercise.id}>
              <td className="reorder-cell">
                <button className="btn-icon" onClick={() => moveExercise(index, -1)} aria-label="Move up" disabled={index === 0}>
                  ↑
                </button>
                <button
                  className="btn-icon"
                  onClick={() => moveExercise(index, 1)}
                  aria-label="Move down"
                  disabled={index === exercises.length - 1}
                >
                  ↓
                </button>
              </td>
              <td>{exercise.name}</td>
              <td>
                <input
                  className="table-input"
                  type="number"
                  min={1}
                  value={exercise.plannedSets}
                  onChange={(e) => updateField(index, 'plannedSets', Number(e.target.value))}
                />
              </td>
              <td>
                <input
                  className="table-input"
                  type="number"
                  min={1}
                  value={exercise.plannedReps}
                  onChange={(e) => updateField(index, 'plannedReps', Number(e.target.value))}
                />
              </td>
              <td>
                <button className="btn-link btn-link-danger" onClick={() => removeExercise(index)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {pickerOpen ? (
        <div className="exercise-picker">
          <p className="page-subtitle">Add an exercise — representative library, not a real search/backend.</p>
          <div className="exercise-picker-list">
            {PROTOTYPE_EXERCISE_LIBRARY.map((name) => (
              <button key={name} className="exercise-picker-item" onClick={() => addExercise(name)}>
                {name}
              </button>
            ))}
          </div>
          <button className="btn-secondary" onClick={() => setPickerOpen(false)}>
            Cancel
          </button>
        </div>
      ) : (
        <button className="btn-secondary" onClick={() => setPickerOpen(true)}>
          + Add Exercise
        </button>
      )}

      <div className="page-actions">
        <button
          className="btn-primary"
          onClick={() => alert('Prototype only — Web does not execute workouts in this pass (see multi-client-product-vision.md: execution on Web remains an open question, not a settled "no").')}
        >
          Start Routine
        </button>
      </div>
    </div>
  )
}
