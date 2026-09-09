import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { getRoutine, saveRoutine } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import PlannedExercisesEditor from '../components/PlannedExercisesEditor'
import ExerciseLibraryPanel from '../components/ExerciseLibraryPanel'
import { buildRoutineExerciseDraft } from '../lib/plannedExerciseDrafts'
import type { RoutineExerciseDraft } from '../types'

// REAL Create/Edit Routine. Local component state holds the full draft (name + exercises + planned
// sets) — a form draft is local UI state, never authoritative server cache, even while editing
// server-backed data (see docs/architecture/web-server-state-cache.md's Server State vs. Local UI
// State section). Nothing is persisted until Save, which calls the atomic save_routine RPC once
// with the complete content (see src/data/routines.ts's saveRoutine / the migration's own doc
// comment for why a single atomic write, not incremental per-field network calls). Exercise/set
// editing itself is PlannedExercisesEditor.tsx, shared with ProgramBuilderPage.tsx's per-session
// editor. Handles both routes:
//   /plan/new            — routineId is undefined, Save creates a new Routine
//   /plan/:routineId/edit — Save replaces the existing Routine's content
export default function RoutineEditorPage() {
  const { routineId } = useParams()
  const isEditing = routineId !== undefined
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [exercises, setExercises] = useState<RoutineExerciseDraft[]>([])
  const [seeded, setSeeded] = useState(!isEditing)
  const [error, setError] = useState<string | null>(null)

  const { data: existingRoutine, isLoading, isError } = useQuery({
    queryKey: queryKeys.routines.detail(userId, routineId ?? ''),
    queryFn: () => getRoutine(routineId!),
    enabled: isEditing && !!routineId,
  })

  // Seed the local draft from the fetched routine exactly once — a cache hit can resolve
  // near-instantly, but the draft must still only be initialized once, never re-clobbered by a
  // later background refetch while the user is mid-edit. Adjusted directly during render (React's
  // documented pattern for "reset/derive state when an external value changes") rather than in a
  // useEffect, which would otherwise cause an extra render pass for no benefit here.
  if (isEditing && !seeded && existingRoutine) {
    setName(existingRoutine.name)
    setExercises(
      existingRoutine.exercises.map((e) => ({
        id: e.id,
        exerciseId: e.exerciseId,
        exerciseName: e.exerciseName,
        plannedSets: e.plannedSets.map((s) => ({ id: s.id, targetReps: s.targetReps, targetWeight: s.targetWeight })),
      })),
    )
    setSeeded(true)
  }

  const saveMutation = useMutation({
    mutationFn: () => saveRoutine(isEditing ? (routineId ?? null) : null, name, exercises),
    onSuccess: (savedId) => {
      // Correctness-first reconciliation (no optimistic writes): the RPC is the authority on the
      // saved shape, so invalidate rather than guess at it — the next visit to the list/detail
      // refetches the real persisted content.
      queryClient.invalidateQueries({ queryKey: queryKeys.routines.list(userId) })
      queryClient.invalidateQueries({ queryKey: queryKeys.routines.detail(userId, savedId) })
      navigate(`/plan/${savedId}`)
    },
    onError: (err) => setError(err instanceof Error ? err.message : 'Failed to save routine.'),
  })

  const handleSave = () => {
    if (!name.trim()) {
      setError('Routine name must not be empty.')
      return
    }
    setError(null)
    saveMutation.mutate()
  }

  if (isEditing && (isLoading || !seeded)) {
    return (
      <div className="page">
        <p className="page-subtitle">Loading…</p>
      </div>
    )
  }

  if (isEditing && isError) {
    return (
      <div className="page">
        <p className="form-error">Failed to load routine.</p>
      </div>
    )
  }

  return (
    <div className="page routine-editor-page">
      <div className="editor-toolbar">
        <button
          type="button"
          className="btn-link btn-back"
          aria-label="Back"
          onClick={() => navigate(isEditing && routineId ? `/plan/${routineId}` : '/plan')}
        >
          <ArrowLeft size={20} aria-hidden="true" />
        </button>
        <h1 className="editor-toolbar-title">{isEditing ? 'Edit Routine' : 'Create Routine'}</h1>
        <button type="button" className="btn-primary" disabled={saveMutation.isPending} onClick={handleSave}>
          {saveMutation.isPending ? 'Saving…' : 'Save Routine'}
        </button>
      </div>

      {(error || saveMutation.isError) && (
        <p className="form-error">
          {error ?? (saveMutation.error instanceof Error ? saveMutation.error.message : 'Failed to save routine.')}
        </p>
      )}

      <div className="editor-body">
        <div className="editor-main">
          <label className="field-label" htmlFor="routine-name">
            Routine Title
          </label>
          <input
            id="routine-name"
            className="table-input routine-name-input"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Workout Routine Title"
          />

          <PlannedExercisesEditor exercises={exercises} onChange={setExercises} showPicker={false} />
        </div>

        <aside className="editor-library-pane">
          <ExerciseLibraryPanel onSelect={(exercise) => setExercises((prev) => [...prev, buildRoutineExerciseDraft(exercise)])} />
        </aside>
      </div>
    </div>
  )
}
