import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
import PlannedExercisesEditor from '../components/PlannedExercisesEditor'
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
        <button type="button" className="btn-secondary" onClick={() => navigate('/programs')}>
          &larr; Programs
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
    if (!window.confirm('Delete this week and all its sessions? This cannot be undone.')) return
    deleteWeekMutation.mutate(weekId)
  }

  const handleDeleteSession = (sessionId: string) => {
    if (!window.confirm('Delete this session? This cannot be undone.')) return
    deleteSessionMutation.mutate(sessionId)
  }

  return (
    <div className="page-wide">
      <button type="button" className="btn-link" onClick={() => navigate('/programs')}>
        &larr; Programs
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
                    <button type="button" className="btn-icon" title="Delete session" onClick={() => handleDeleteSession(session.id)}>
                      ×
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
      plannedSets: e.plannedSets.map((s) => ({ id: s.id, targetReps: s.targetReps, targetWeight: s.targetWeight })),
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
      <PlannedExercisesEditor exercises={exercises} onChange={setExercises} />
      <div className="page-actions">
        <button type="button" className="btn-primary" disabled={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
          {saveMutation.isPending ? 'Saving…' : 'Save Session'}
        </button>
      </div>
    </div>
  )
}
