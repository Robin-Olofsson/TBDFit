import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { createProgram, deleteProgram, listMyPrograms } from '../data/programs'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { notifyError, notifyProgramDeleted } from '../lib/toast'
import ConfirmDialog from '../components/ConfirmDialog'
import PromptDialog from '../components/PromptDialog'
import type { Program } from '../types'

// REAL, Supabase-backed "My Programs" — see supabase/migrations/20260911120000_create_programs.sql
// and docs/product/frontend-prototype-notes.md. Program is OPTIONAL: this page is reached via its
// own top-level nav item (see TopNav.tsx), never nested inside /plan (Routines) — Option C from
// docs/product/web-routine-program-planning-research.md's Routine Information Architecture section,
// chosen specifically so Program never reads as a requirement for, or a parent of, Routine.
//
// TanStack Query-backed: revisiting this list after opening a Program (and back) renders the cached
// list immediately — see docs/architecture/web-server-state-cache.md.
export default function ProgramsListPage() {
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [promptOpen, setPromptOpen] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<{ id: string; name: string } | null>(null)

  const { data: programs, isLoading, isError, error } = useQuery({
    queryKey: queryKeys.programs.list(userId),
    queryFn: listMyPrograms,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteProgram,
    onSuccess: (_result, deletedId) => {
      queryClient.setQueryData(queryKeys.programs.list(userId), (prev: Program[] | undefined) => prev?.filter((p) => p.id !== deletedId))
      queryClient.removeQueries({ queryKey: queryKeys.programs.detail(userId, deletedId) })
      setPendingDelete(null)
    },
  })

  const handleConfirmDelete = () => {
    if (!pendingDelete) return
    void notifyProgramDeleted(deleteMutation.mutateAsync(pendingDelete.id)).catch(() => {})
  }

  // Replaces the earlier window.prompt() — a themed, accessible PromptDialog instead (see that
  // component's own doc comment for why it's not ConfirmDialog: this collects a name, it doesn't
  // confirm a destructive action). The dialog stays open on failure (toast surfaces the error) and
  // closes only once the server has actually returned the created Program — never optimistically.
  const handleCreate = async (name: string) => {
    setCreating(true)
    try {
      const created = await createProgram(name)
      // The RPC/insert already returns the full created aggregate — seed both caches directly
      // rather than invalidating, since we already have the authoritative shape in hand.
      queryClient.setQueryData(queryKeys.programs.list(userId), (prev: Program[] | undefined) => (prev ? [created, ...prev] : [created]))
      queryClient.setQueryData(queryKeys.programs.detail(userId, created.id), created)
      setPromptOpen(false)
      navigate(`/programs/${created.id}`)
    } catch (err) {
      notifyError(err instanceof Error ? err.message : 'Failed to create program.')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Programs</h1>
          <p className="page-subtitle">
            Optional multi-week training plans, built from your Routines — stored in Supabase for your account.
          </p>
        </div>
        <button type="button" className="btn-primary" disabled={creating} onClick={() => setPromptOpen(true)}>
          + Create Program
        </button>
      </div>

      {isError && <p className="form-error">{error instanceof Error ? error.message : 'Failed to load programs.'}</p>}
      {deleteMutation.isError && (
        <p className="form-error">{deleteMutation.error instanceof Error ? deleteMutation.error.message : 'Failed to delete program.'}</p>
      )}

      {isLoading ? (
        <p className="page-subtitle">Loading…</p>
      ) : !programs || programs.length === 0 ? (
        <div className="empty-state">
          <p>No programs yet.</p>
          <p className="page-subtitle">
            Programs are optional — your Routines already work on their own. Create a Program when you want to organize
            sessions across multiple weeks.
          </p>
          <button type="button" className="btn-primary" disabled={creating} onClick={() => setPromptOpen(true)}>
            + Create Program
          </button>
        </div>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Program</th>
              <th>Weeks</th>
              <th>Sessions</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {programs.map((program) => {
              const sessionCount = program.weeks.reduce((sum, w) => sum + w.sessions.length, 0)
              return (
                <tr key={program.id} className="data-row">
                  <td onClick={() => navigate(`/programs/${program.id}`)}>{program.name}</td>
                  <td onClick={() => navigate(`/programs/${program.id}`)}>{program.weeks.length}</td>
                  <td onClick={() => navigate(`/programs/${program.id}`)}>{sessionCount}</td>
                  <td className="data-row-action" onClick={() => navigate(`/programs/${program.id}`)}>
                    Open <ChevronRight size={14} aria-hidden="true" />
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn-link btn-link-danger"
                      onClick={(e) => {
                        e.stopPropagation()
                        setPendingDelete({ id: program.id, name: program.name })
                      }}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete program?"
        description={`This will permanently delete "${pendingDelete?.name}".`}
        confirmLabel="Delete"
        destructive
        pending={deleteMutation.isPending}
        onConfirm={handleConfirmDelete}
        onCancel={() => setPendingDelete(null)}
      />

      <PromptDialog
        open={promptOpen}
        title="Create Program"
        label="Program name"
        placeholder='e.g. "12 Week Strength"'
        confirmLabel="Create"
        pending={creating}
        onConfirm={(name) => void handleCreate(name)}
        onCancel={() => setPromptOpen(false)}
      />
    </div>
  )
}
