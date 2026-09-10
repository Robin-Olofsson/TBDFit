import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { getRoutine } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { formatRestTimerLabel, setTypeLetter } from '../lib/plannedExerciseDrafts'

// REAL, Supabase-backed Routine Detail — read-only per-exercise, per-set display fetched from
// Supabase (see supabase/migrations/20260910120000_create_routines.sql, src/data/routines.ts). No
// local edit state here any more; editing happens on RoutineEditorPage.tsx (/plan/:routineId/edit).
// Deliberately no Start/Delete here (developer decision): Start-on-Web remains unimplemented and
// unadvertised on this page, and Delete now lives only on the Routine list (PlanPage.tsx), which
// already has its own ConfirmDialog-based delete flow — this page has nothing destructive left to
// confirm.
//
// TanStack Query-backed: navigating away (e.g. into Edit) and back to this exact detail renders the
// cached aggregate immediately rather than re-fetching — see docs/architecture/web-server-state-cache.md.
export default function RoutineDetailPage() {
  const { routineId } = useParams()
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user.id ?? ''

  const { data: routine, isLoading, isError, error } = useQuery({
    queryKey: queryKeys.routines.detail(userId, routineId ?? ''),
    queryFn: () => getRoutine(routineId!),
    enabled: !!routineId,
  })

  if (isError) {
    return (
      <div className="page">
        <p className="form-error">{error instanceof Error ? error.message : 'Failed to load routine.'}</p>
        <button type="button" className="btn-secondary btn-back" onClick={() => navigate('/plan')}>
          <ArrowLeft size={16} aria-hidden="true" /> Routine
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
        <button type="button" className="btn-secondary btn-back" onClick={() => navigate('/plan')}>
          <ArrowLeft size={16} aria-hidden="true" /> Routine
        </button>
      </div>
    )
  }

  return (
    <div className="page">
      <button type="button" className="btn-link btn-back" onClick={() => navigate('/plan')}>
        <ArrowLeft size={16} aria-hidden="true" /> Routine
      </button>
      <div className="page-header">
        <h1>{routine.name}</h1>
      </div>

      {routine.exercises.length === 0 ? (
        <p className="page-subtitle">No exercises yet — edit this routine to add some.</p>
      ) : (
        routine.exercises.map((exercise) => (
          <div key={exercise.id} className="routine-detail-exercise">
            <h2 className="routine-detail-exercise-name">{exercise.exerciseName}</h2>
            {exercise.note && <p className="routine-detail-exercise-note">{exercise.note}</p>}
            {exercise.restTimerSeconds !== null && (
              <p className="page-subtitle">Rest timer: {formatRestTimerLabel(exercise.restTimerSeconds)}</p>
            )}
            {exercise.plannedSets.length === 0 ? (
              <p className="page-subtitle">No planned sets.</p>
            ) : (
              <ul className="routine-detail-set-list">
                {exercise.plannedSets.map((set, index) => (
                  <li key={set.id}>
                    <span className="routine-detail-set-index">
                      Set {index + 1}
                      {set.setType !== 'NORMAL' && (
                        <span className="set-type-badge" title={set.setType}>
                          {setTypeLetter(set.setType)}
                        </span>
                      )}
                    </span>
                    <span>{set.targetWeight !== null ? `${set.targetWeight} kg` : 'bodyweight / no load target'}</span>
                    <span>{set.targetReps !== null ? `${set.targetReps} reps` : 'no rep target'}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))
      )}

      <div className="page-actions">
        <button type="button" className="btn-secondary" onClick={() => navigate(`/plan/${routine.id}/edit`)}>
          Edit Routine
        </button>
      </div>
    </div>
  )
}
