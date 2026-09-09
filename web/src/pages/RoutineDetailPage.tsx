import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteRoutine, getRoutine } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'

// REAL, Supabase-backed Routine Detail — read-only per-exercise, per-set display fetched from
// Supabase (see supabase/migrations/20260910120000_create_routines.sql, src/data/routines.ts). No
// local edit state here any more; editing happens on RoutineEditorPage.tsx (/plan/:routineId/edit).
//
// TanStack Query-backed: navigating away (e.g. into Edit) and back to this exact detail renders the
// cached aggregate immediately rather than re-fetching — see docs/architecture/web-server-state-cache.md.
export default function RoutineDetailPage() {
  const { routineId } = useParams()
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()

  const { data: routine, isLoading, isError, error } = useQuery({
    queryKey: queryKeys.routines.detail(userId, routineId ?? ''),
    queryFn: () => getRoutine(routineId!),
    enabled: !!routineId,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRoutine,
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.routines.detail(userId, routineId ?? '') })
      queryClient.setQueryData(
        queryKeys.routines.list(userId),
        (prev: { id: string }[] | undefined) => prev?.filter((r) => r.id !== routineId),
      )
      navigate('/plan')
    },
  })

  const handleDelete = () => {
    if (!routine) return
    if (!window.confirm(`Delete "${routine.name}"? This cannot be undone.`)) return
    deleteMutation.mutate(routine.id)
  }

  if (isError) {
    return (
      <div className="page">
        <p className="form-error">{error instanceof Error ? error.message : 'Failed to load routine.'}</p>
        <button type="button" className="btn-secondary" onClick={() => navigate('/plan')}>
          &larr; Routine
        </button>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="page">
        <p className="page-subtitle">Loading…</p>
      </div>
    )
  }

  if (!routine) {
    return (
      <div className="page">
        <p>Routine not found.</p>
        <button type="button" className="btn-secondary" onClick={() => navigate('/plan')}>
          &larr; Routine
        </button>
      </div>
    )
  }

  return (
    <div className="page">
      <button type="button" className="btn-link" onClick={() => navigate('/plan')}>
        &larr; Routine
      </button>
      <div className="page-header">
        <h1>{routine.name}</h1>
      </div>

      {deleteMutation.isError && (
        <p className="form-error">
          {deleteMutation.error instanceof Error ? deleteMutation.error.message : 'Failed to delete routine.'}
        </p>
      )}

      {routine.exercises.length === 0 ? (
        <p className="page-subtitle">No exercises yet — edit this routine to add some.</p>
      ) : (
        routine.exercises.map((exercise) => (
          <div key={exercise.id} className="routine-detail-exercise">
            <h2 className="routine-detail-exercise-name">{exercise.exerciseName}</h2>
            {exercise.plannedSets.length === 0 ? (
              <p className="page-subtitle">No planned sets.</p>
            ) : (
              <ul className="routine-detail-set-list">
                {exercise.plannedSets.map((set, index) => (
                  <li key={set.id}>
                    <span className="routine-detail-set-index">Set {index + 1}</span>
                    <span>{set.targetReps !== null ? `${set.targetReps} reps` : 'no rep target'}</span>
                    <span>{set.targetWeight !== null ? `${set.targetWeight} kg` : 'bodyweight / no load target'}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))
      )}

      <div className="page-actions">
        <button
          type="button"
          className="btn-primary"
          onClick={() =>
            window.alert(
              'Prototype only — Web does not execute workouts in this pass (see multi-client-product-vision.md: execution on Web remains an open question, not a settled "no").',
            )
          }
        >
          Start Routine
        </button>{' '}
        <button type="button" className="btn-secondary" onClick={() => navigate(`/plan/${routine.id}/edit`)}>
          Edit Routine
        </button>{' '}
        <button type="button" className="btn-link btn-link-danger" onClick={handleDelete}>
          Delete
        </button>
      </div>
    </div>
  )
}
