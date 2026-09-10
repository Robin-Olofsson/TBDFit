import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Trash2 } from 'lucide-react'
import {
  addSessionFromScratch,
  addWeek,
  copyRoutineIntoWeek,
  deleteSession,
  deleteWeek,
  duplicateWeek,
  getProgram,
  saveProgramSession,
} from '../data/programs'
import { listMyRoutines } from '../data/routines'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { notifyProgramSessionDeleted, notifyProgramWeekDeleted } from '../lib/toast'
import PlannedExercisesEditor from '../components/PlannedExercisesEditor'
import ConfirmDialog from '../components/ConfirmDialog'
import type { ProgramSession, RoutineExerciseDraft } from '../types'

// REAL, Supabase-backed Program Builder — see supabase/migrations/20260911120000_create_programs.sql.
// Three-pane desktop layout (Weeks | Sessions in selected week | Session editor), per the
// recommendation in docs/product/web-routine-program-planning-research.md's Program Builder UX
// Patterns section. Program never gates or wraps Routine — "Copy from Routine" is one of two ways
// to add a session, never the only way, and this page is reached only via its own top-level
// `Programs` nav item (see TopNav.tsx), independent of `/plan` (Routines).
//
// TanStack Query-backed: the whole Program (weeks/sessions/exercises/sets) is cached as ONE
// aggregate query per program id — matching how getProgram already fetches it as one nested
// Supabase select, and matching how every mutation below reasons about "the current state of this
// program" as a single tree, not independent rows (see docs/architecture/web-server-state-cache.md's
// Query Granularity Decisions). Every mutation invalidates that one query rather than manually
// re-deriving the next state client-side; switching between already-fetched weeks/sessions reads
// from the same cached aggregate and triggers no new network request.
export default function ProgramBuilderPage() {
  const { programId } = useParams()
  const navigate = useNavigate()
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()

  const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null)
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [routinePickerOpen, setRoutinePickerOpen] = useState(false)
  const [mutationError, setMutationError] = useState<string | null>(null)
  // Which delete is pending confirmation, if any — a discriminated union rather than two separate
  // booleans/ids, since exactly one shared ConfirmDialog below serves both the week-delete and
  // session-delete flows (they differ only in copy and which mutation runs).
  const [pendingDelete, setPendingDelete] = useState<{ kind: 'week' | 'session'; id: string } | null>(null)

  const programQueryKey = queryKeys.programs.detail(userId, programId ?? '')
  const { data: program, isLoading, isError } = useQuery({
    queryKey: programQueryKey,
    queryFn: () => getProgram(programId!),
    enabled: !!programId,
  })

  // Deliberately the SAME query key PlanPage.tsx/HomePage.tsx use for "my routines" — opening this
  // picker never issues a second network request if that list is already cached and fresh.
  const { data: myRoutines, isLoading: routinesLoading } = useQuery({
    queryKey: queryKeys.routines.list(userId),
    queryFn: listMyRoutines,
    enabled: routinePickerOpen,
  })

  const invalidateProgram = () => queryClient.invalidateQueries({ queryKey: programQueryKey })
  const onMutationError = (err: unknown) => setMutationError(err instanceof Error ? err.message : 'Something went wrong.')

  const addWeekMutation = useMutation({
    mutationFn: () => addWeek(program!.id),
    onSuccess: (newWeekId) => {
      setMutationError(null)
      setSelectedWeekId(newWeekId)
      setSelectedSessionId(null)
      void invalidateProgram()
    },
    onError: onMutationError,
  })

  const duplicateWeekMutation = useMutation({
    mutationFn: (weekId: string) => duplicateWeek(weekId),
    onSuccess: (newWeekId) => {
      setMutationError(null)
      setSelectedWeekId(newWeekId)
      setSelectedSessionId(null)
      void invalidateProgram()
    },
    onError: onMutationError,
  })

  const deleteWeekMutation = useMutation({
    mutationFn: (weekId: string) => deleteWeek(weekId),
    onSuccess: () => {
      setMutationError(null)
      setSelectedWeekId(null)
      setSelectedSessionId(null)
      setPendingDelete(null)
      void invalidateProgram()
    },
    onError: onMutationError,
  })

  const addSessionMutation = useMutation({
    mutationFn: (weekId: string) => addSessionFromScratch(weekId),
    onSuccess: (newSessionId) => {
      setMutationError(null)
      setSelectedSessionId(newSessionId)
      void invalidateProgram()
    },
    onError: onMutationError,
  })

  const copyRoutineMutation = useMutation({
    mutationFn: ({ routineId, weekId }: { routineId: string; weekId: string }) => copyRoutineIntoWeek(routineId, weekId),
    onSuccess: (newSessionId) => {
      setMutationError(null)
      setSelectedSessionId(newSessionId)
      setRoutinePickerOpen(false)
      void invalidateProgram()
    },
    onError: onMutationError,
  })

  const deleteSessionMutation = useMutation({
    mutationFn: (sessionId: string) => deleteSession(sessionId),
    onSuccess: () => {
      setMutationError(null)
      setSelectedSessionId(null)
      setPendingDelete(null)
      void invalidateProgram()
    },
    onError: onMutationError,
  })

  const busy =
    addWeekMutation.isPending ||
    duplicateWeekMutation.isPending ||
    deleteWeekMutation.isPending ||
    addSessionMutation.isPending ||
    copyRoutineMutation.isPending ||
    deleteSessionMutation.isPending

  if (isError) {
    return (
      <div className="page-wide">
        <p className="form-error">Failed to load program.</p>
        <button type="button" className="btn-secondary btn-back" onClick={() => navigate('/programs')}>
          <ArrowLeft size={16} aria-hidden="true" /> Programs
        </button>
      </div>
    )
  }

  if (isLoading || !program) {
    return (
      <div className="page-wide">
        <p className="page-subtitle">Loading…</p>
      </div>
    )
  }

  // Falls back to the first available week/session, computed fresh on every render, whenever the
  // stored id doesn't (or no longer does) match anything in the current aggregate — e.g. right
  // after a delete removes the previously-selected week/session, or on first load before the user
  // has clicked anything. No effect needed for this: it is a pure derivation from already-available
  // data, not a synchronization with an external system.
  const selectedWeek = program.weeks.find((w) => w.id === selectedWeekId) ?? program.weeks[0] ?? null
  const selectedSession = selectedWeek?.sessions.find((s) => s.id === selectedSessionId) ?? selectedWeek?.sessions[0] ?? null

  const handleDeleteWeek = (weekId: string) => {
    setPendingDelete({ kind: 'week', id: weekId })
  }

  const handleDeleteSession = (sessionId: string) => {
    setPendingDelete({ kind: 'session', id: sessionId })
  }

  const handleConfirmDelete = () => {
    if (!pendingDelete) return
    if (pendingDelete.kind === 'week') {
      void notifyProgramWeekDeleted(deleteWeekMutation.mutateAsync(pendingDelete.id)).catch(() => {})
    } else {
      void notifyProgramSessionDeleted(deleteSessionMutation.mutateAsync(pendingDelete.id)).catch(() => {})
    }
  }

  return (
    <div className="page-wide">
      <button type="button" className="btn-link btn-back" onClick={() => navigate('/programs')}>
        <ArrowLeft size={16} aria-hidden="true" /> Programs
      </button>
      <div className="page-header">
        <h1>{program.name}</h1>
      </div>
      {mutationError && <p className="form-error">{mutationError}</p>}

      <div className="program-builder">
        <div className="program-builder-weeks">
          <div className="program-builder-pane-header">
            <span>Weeks</span>
          </div>
          <ul className="program-week-list">
            {program.weeks.map((week, index) => (
              <li key={week.id}>
                <button
                  type="button"
                  className={week.id === selectedWeekId ? 'program-week-item program-week-item-active' : 'program-week-item'}
                  onClick={() => {
                    setSelectedWeekId(week.id)
                    setSelectedSessionId(week.sessions[0]?.id ?? null)
                  }}
                >
                  Week {index + 1}
                  <span className="program-week-item-count">{week.sessions.length} session{week.sessions.length === 1 ? '' : 's'}</span>
                </button>
              </li>
            ))}
          </ul>
          <div className="program-builder-pane-actions">
            <button type="button" className="btn-secondary" disabled={busy} onClick={() => addWeekMutation.mutate()}>
              + Add Week
            </button>
            {selectedWeek && (
              <>
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={busy}
                  onClick={() => duplicateWeekMutation.mutate(selectedWeek.id)}
                >
                  Duplicate Week
                </button>
                <button
                  type="button"
                  className="btn-link btn-link-danger"
                  disabled={busy}
                  onClick={() => handleDeleteWeek(selectedWeek.id)}
                >
                  Delete Week
                </button>
              </>
            )}
          </div>
        </div>

        <div className="program-builder-sessions">
          <div className="program-builder-pane-header">
            <span>{selectedWeek ? `Week ${program.weeks.findIndex((w) => w.id === selectedWeek.id) + 1} — Sessions` : 'Sessions'}</span>
          </div>
          {!selectedWeek ? (
            <p className="page-subtitle">Add a week to get started.</p>
          ) : (
            <>
              <ul className="program-session-list">
                {selectedWeek.sessions.map((session, index) => (
                  <li key={session.id}>
                    <button
                      type="button"
                      className={
                        session.id === selectedSessionId ? 'program-session-item program-session-item-active' : 'program-session-item'
                      }
                      onClick={() => setSelectedSessionId(session.id)}
                    >
                      {sessionDisplayName(session, index)}
                    </button>
                    <button
                      type="button"
                      className="btn-icon"
                      aria-label="Delete session"
                      title="Delete session"
                      onClick={() => handleDeleteSession(session.id)}
                    >
                      <Trash2 size={14} aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
              <div className="program-builder-pane-actions">
                <button type="button" className="btn-secondary" disabled={busy} onClick={() => addSessionMutation.mutate(selectedWeek.id)}>
                  + Add Session
                </button>
                <button type="button" className="btn-secondary" disabled={busy} onClick={() => setRoutinePickerOpen(true)}>
                  Copy from Routine
                </button>
              </div>
              {routinePickerOpen && (
                <div className="exercise-picker">
                  <p className="page-subtitle">Choose a Routine to copy</p>
                  {routinesLoading ? (
                    <p className="page-subtitle">Loading…</p>
                  ) : !myRoutines || myRoutines.length === 0 ? (
                    <p className="page-subtitle">You don't have any Routines yet — create one under Routine first.</p>
                  ) : (
                    <div className="exercise-picker-list">
                      {myRoutines.map((routine) => (
                        <button
                          key={routine.id}
                          className="exercise-picker-item"
                          onClick={() => copyRoutineMutation.mutate({ routineId: routine.id, weekId: selectedWeek.id })}
                        >
                          {routine.name}
                        </button>
                      ))}
                    </div>
                  )}
                  <button type="button" className="btn-secondary" onClick={() => setRoutinePickerOpen(false)}>
                    Cancel
                  </button>
                </div>
              )}
            </>
          )}
        </div>

        <div className="program-builder-editor">
          <div className="program-builder-pane-header">
            <span>Session editor</span>
          </div>
          {selectedSession ? (
            <SessionEditor key={selectedSession.id} session={selectedSession} onSaved={invalidateProgram} />
          ) : (
            <p className="page-subtitle">Select or add a session to edit its exercises and sets.</p>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={pendingDelete !== null}
        title={pendingDelete?.kind === 'week' ? 'Delete week?' : 'Delete session?'}
        description={
          pendingDelete?.kind === 'week'
            ? 'This will permanently delete this week and all its sessions.'
            : 'This will permanently delete this session.'
        }
        confirmLabel="Delete"
        destructive
        pending={pendingDelete?.kind === 'week' ? deleteWeekMutation.isPending : deleteSessionMutation.isPending}
        onConfirm={handleConfirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  )
}

function sessionDisplayName(session: ProgramSession, index: number): string {
  return session.name ?? `Session ${index + 1}`
}

interface SessionEditorProps {
  session: ProgramSession
  onSaved: () => void
}

// One session's name + planned exercises/sets, saved atomically via save_program_session (a
// full-replace of this session's content only — see that function's own comment). Keyed by
// session.id in the parent so switching sessions re-seeds this component's local draft state fresh
// each time. `onSaved` invalidates the whole Program aggregate query (see the granularity note at
// the top of this file) — there is no separate per-session query to reconcile.
function SessionEditor({ session, onSaved }: SessionEditorProps) {
  const [name, setName] = useState(session.name ?? '')
  const [exercises, setExercises] = useState<RoutineExerciseDraft[]>(
    session.exercises.map((e) => ({
      id: e.id,
      exerciseId: e.exerciseId,
      exerciseName: e.exerciseName,
      // Program sessions have no note/rest-timer concept of their own (see
      // supabase/migrations/20260914120000_add_routine_exercise_note_and_rest_timer.sql's own
      // comment — that slice was scoped to Routine only) — RoutineExerciseDraft is a shared TS shape
      // reused here as-is, but these two fields simply never populate for a Program session. The
      // editor below is told not to render their inputs at all (showNoteAndRestTimer={false}), so
      // there's no field a user could type into that would then be silently discarded on save.
      note: null,
      restTimerSeconds: null,
      // set_type is likewise a Routine-only column (20260915120000_add_planned_set_type.sql) — the
      // shared editor is told not to render its selector for Program sessions
      // (showNoteAndRestTimer={false}), so 'NORMAL' here is an inert default never surfaced to the
      // user, never read by toSaveProgramSessionPayload.
      plannedSets: e.plannedSets.map((s) => ({ id: s.id, targetReps: s.targetReps, targetWeight: s.targetWeight, setType: 'NORMAL' as const })),
    })),
  )

  const saveMutation = useMutation({
    mutationFn: () => saveProgramSession(session.id, name.trim() || null, exercises),
    onSuccess: onSaved,
  })

  return (
    <div>
      {saveMutation.isError && (
        <p className="form-error">{saveMutation.error instanceof Error ? saveMutation.error.message : 'Failed to save session.'}</p>
      )}
      <label className="field-label" htmlFor="session-name">
        Session name (optional)
      </label>
      <input
        id="session-name"
        className="table-input routine-name-input"
        type="text"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="e.g. Upper A"
      />
      <PlannedExercisesEditor exercises={exercises} onChange={setExercises} showNoteAndRestTimer={false} />
      <div className="page-actions">
        <button type="button" className="btn-primary" disabled={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
          {saveMutation.isPending ? 'Saving…' : 'Save Session'}
        </button>
      </div>
    </div>
  )
}
