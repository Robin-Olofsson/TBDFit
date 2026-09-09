import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ListChecks } from 'lucide-react'
import { deleteRoutine, listMyRoutines } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'

// REAL, Supabase-backed — see supabase/migrations/20260910120000_create_routines.sql and
// docs/product/frontend-prototype-notes.md. Route stays /plan (predates the "Routine" nav rename,
// intentionally unchanged); the visible heading/nav label is "Routine".
//
// TanStack Query-backed (see docs/architecture/web-server-state-cache.md): the same
// queryKeys.routines.list(userId) query HomePage.tsx and ProgramBuilderPage.tsx's Routine picker
// also use — visiting Plan after either of those (or navigating away and back) renders the already
// -cached list immediately, with a background refetch only once it's gone stale, not a blocking
// "Loading..." on every remount.
export default function PlanPage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  // Safe: this page only ever renders inside AuthenticatedShell (phase === 'SIGNED_IN'), so a real
  // session always exists — no non-null assertion needed, just a defensive fallback.
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()

  const { data: routines, isLoading, isError, error } = useQuery({
    queryKey: queryKeys.routines.list(userId),
    queryFn: listMyRoutines,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRoutine,
    onSuccess: (_result, deletedId) => {
      // Targeted cache update, not a full invalidate-everything: only this list (and the deleted
      // routine's own now-gone detail query) are affected. Any Program session previously copied
      // from this routine is a completely separate cached query (queryKeys.programs.*) and is
      // deliberately never touched here — the snapshot invariant applies to the cache layer exactly
      // as it does to the database (see Program Mutation Reconciliation in the architecture doc).
      queryClient.setQueryData(queryKeys.routines.list(userId), (prev: typeof routines) => prev?.filter((r) => r.id !== deletedId))
      queryClient.removeQueries({ queryKey: queryKeys.routines.detail(userId, deletedId) })
    },
  })

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Routine</h1>
          <p className="page-subtitle">Your reusable workout plans — stored in Supabase for your account.</p>
        </div>
        <button type="button" className="btn-primary btn-primary-lg" onClick={() => navigate('/plan/new')}>
          <ListChecks size={18} aria-hidden="true" />
          New Routine
        </button>
      </div>

      {isError && <p className="form-error">{error instanceof Error ? error.message : 'Failed to load routines.'}</p>}
      {deleteMutation.isError && (
        <p className="form-error">
          {deleteMutation.error instanceof Error ? deleteMutation.error.message : 'Failed to delete routine.'}
        </p>
      )}

      {isLoading ? (
        // Only shown when there is genuinely no cached data yet (first-ever load) — TanStack
        // Query's isLoading (not isFetching) is specifically "no data, currently fetching," which is
        // exactly the loading-vs-background-refetch distinction this task requires.
        <p className="page-subtitle">Loading…</p>
      ) : !routines || routines.length === 0 ? (
        <div className="empty-state">
          <p>No routines yet.</p>
          <p className="page-subtitle">Create your first reusable workout plan using the New Routine button above.</p>
        </div>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Routine</th>
              <th>Exercises</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {routines.map((routine) => (
              <tr key={routine.id} className="data-row">
                <td onClick={() => navigate(`/plan/${routine.id}`)}>{routine.name}</td>
                <td onClick={() => navigate(`/plan/${routine.id}`)}>
                  {routine.exercises.map((e) => e.exerciseName).join(', ') || '—'}
                </td>
                <td className="data-row-action" onClick={() => navigate(`/plan/${routine.id}`)}>
                  Open →
                </td>
                <td>
                  <button
                    type="button"
                    className="btn-link btn-link-danger"
                    onClick={(e) => {
                      e.stopPropagation()
                      if (!window.confirm(`Delete "${routine.name}"? This cannot be undone.`)) return
                      deleteMutation.mutate(routine.id)
                    }}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
