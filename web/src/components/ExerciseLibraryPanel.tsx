import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { createCustomExercise, listVisibleExercises } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { EQUIPMENT_OPTIONS, EXERCISE_TYPE_OPTIONS, equipmentLabel, exerciseTypeLabel } from '../lib/exerciseLabels'
import Modal from './Modal'
import type { Equipment, ExerciseSummary, ExerciseType } from '../types'

// A persistent (always-visible, never toggled) exercise picker — see the design reference at the
// project root (newroutinepage.png, a Hevy Create-Routine screenshot the developer shared for
// layout inspiration) for the "Library" panel this is modeled after. Deliberately NOT a 1:1 copy:
// TBDFit has no exercise images or muscle-group metadata to populate the reference's
// thumbnail/muscle-filter UI, and per docs/product/web-routine-program-planning-research.md's
// Exercise Library UX findings, muscle filters remain an explicit DEFER (no data to filter by).
// Equipment/Exercise Type filters ARE included below, per the follow-up Exercise metadata slice —
// unlike muscle group, this repository now has real data behind both.
//
// Distinct from PlannedExercisesEditor's own (still-used-by-Program) inline toggled picker — see
// that component's `showPicker` prop doc comment for why both exist rather than one replacing the
// other everywhere.
interface Props {
  onSelect: (exercise: ExerciseSummary) => void
}

const ANY = 'ANY' as const

export default function ExerciseLibraryPanel({ onSelect }: Props) {
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<ExerciseType | typeof ANY>(ANY)
  const [equipmentFilter, setEquipmentFilter] = useState<Equipment | typeof ANY>(ANY)
  const [creating, setCreating] = useState(false)
  const [newExerciseName, setNewExerciseName] = useState('')
  const [newExerciseType, setNewExerciseType] = useState<ExerciseType>('WEIGHT_REPS')
  const [newEquipment, setNewEquipment] = useState<Equipment>('BARBELL')

  const { data: visibleExercises, isLoading, error: queryError } = useQuery({
    queryKey: queryKeys.exercises.list(userId),
    queryFn: listVisibleExercises,
    staleTime: 30 * 60 * 1000,
  })

  const createExerciseMutation = useMutation({
    mutationFn: () => createCustomExercise(newExerciseName, newExerciseType, newEquipment),
    onSuccess: (created) => {
      queryClient.setQueryData(queryKeys.exercises.list(userId), (prev: ExerciseSummary[] | undefined) =>
        prev ? [...prev, created] : [created],
      )
      setNewExerciseName('')
      setCreating(false)
      onSelect(created)
    },
  })

  const queryErrorMessage = queryError instanceof Error ? queryError.message : null
  const createErrorMessage = createExerciseMutation.error instanceof Error ? createExerciseMutation.error.message : null

  const filtered = useMemo(() => {
    const all = visibleExercises ?? []
    const trimmedSearch = search.trim().toLowerCase()
    return all.filter((exercise) => {
      if (trimmedSearch && !exercise.name.toLowerCase().includes(trimmedSearch)) return false
      if (typeFilter !== ANY && exercise.exerciseType !== typeFilter) return false
      if (equipmentFilter !== ANY && exercise.equipment !== equipmentFilter) return false
      return true
    })
  }, [visibleExercises, search, typeFilter, equipmentFilter])

  const handleCreate = () => {
    const trimmed = newExerciseName.trim()
    if (!trimmed) return
    createExerciseMutation.mutate()
  }

  return (
    <div className="exercise-library">
      <div className="exercise-library-header">
        <h2>Library</h2>
        <button type="button" className="btn-link exercise-library-custom-toggle" onClick={() => setCreating((prev) => !prev)}>
          <Plus size={14} aria-hidden="true" />
          Custom Exercise
        </button>
      </div>

      {creating && (
        <Modal title="Create Custom Exercise" onClose={() => setCreating(false)}>
          {createErrorMessage && <p className="form-error">{createErrorMessage}</p>}

          <div>
            <label className="field-label" htmlFor="new-exercise-name">
              Exercise Name
            </label>
            <input
              id="new-exercise-name"
              className="table-input modal-field-input"
              type="text"
              placeholder="Enter exercise name…"
              autoFocus
              value={newExerciseName}
              onChange={(e) => setNewExerciseName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
            />
          </div>

          <div>
            <label className="field-label" htmlFor="new-exercise-type">
              Exercise Type
            </label>
            <select
              id="new-exercise-type"
              className="table-input modal-field-input"
              value={newExerciseType}
              onChange={(e) => setNewExerciseType(e.target.value as ExerciseType)}
            >
              {EXERCISE_TYPE_OPTIONS.map((type) => (
                <option key={type} value={type}>
                  {exerciseTypeLabel(type)}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="field-label" htmlFor="new-exercise-equipment">
              Equipment
            </label>
            <select
              id="new-exercise-equipment"
              className="table-input modal-field-input"
              value={newEquipment}
              onChange={(e) => setNewEquipment(e.target.value as Equipment)}
            >
              {EQUIPMENT_OPTIONS.map((equipment) => (
                <option key={equipment} value={equipment}>
                  {equipmentLabel(equipment)}
                </option>
              ))}
            </select>
          </div>

          <div className="modal-actions">
            <button
              type="button"
              className="btn-primary"
              disabled={createExerciseMutation.isPending || !newExerciseName.trim()}
              onClick={handleCreate}
            >
              {createExerciseMutation.isPending ? 'Creating…' : 'Create Exercise'}
            </button>
          </div>
        </Modal>
      )}

      {queryErrorMessage && <p className="form-error">{queryErrorMessage}</p>}

      <div className="exercise-library-search">
        <Search size={16} aria-hidden="true" className="exercise-library-search-icon" />
        <input
          className="table-input"
          type="text"
          placeholder="Search exercises"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="exercise-library-filters">
        <select className="table-input" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as ExerciseType | typeof ANY)}>
          <option value={ANY}>All Types</option>
          {EXERCISE_TYPE_OPTIONS.map((type) => (
            <option key={type} value={type}>
              {exerciseTypeLabel(type)}
            </option>
          ))}
        </select>
        <select
          className="table-input"
          value={equipmentFilter}
          onChange={(e) => setEquipmentFilter(e.target.value as Equipment | typeof ANY)}
        >
          <option value={ANY}>All Equipment</option>
          {EQUIPMENT_OPTIONS.map((equipment) => (
            <option key={equipment} value={equipment}>
              {equipmentLabel(equipment)}
            </option>
          ))}
        </select>
      </div>

      <div className="exercise-library-list">
        {isLoading ? (
          <p className="page-subtitle">Loading…</p>
        ) : filtered.length === 0 ? (
          <p className="page-subtitle">No exercises found.</p>
        ) : (
          filtered.map((exercise) => (
            <button key={exercise.id} type="button" className="exercise-library-item" onClick={() => onSelect(exercise)}>
              <span className="exercise-library-item-add" aria-hidden="true">
                <Plus size={14} />
              </span>
              <span className="exercise-library-item-text">
                {exercise.name}
                <span className="exercise-library-item-meta">
                  {exerciseTypeLabel(exercise.exerciseType)} · {equipmentLabel(exercise.equipment)}
                </span>
              </span>
            </button>
          ))
        )}
      </div>
    </div>
  )
}
