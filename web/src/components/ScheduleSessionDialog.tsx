import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Modal from './Modal'
import SelectField from './SelectField'
import { listMyRoutines } from '../data/routines'
import { listMyPrograms } from '../data/programs'
import {
  scheduleRoutine,
  scheduleProgramSession,
  rescheduleSession,
  type ScheduledSessionSummary,
} from '../data/scheduledSessions'
import {
  COMMON_TIME_ZONES,
  getBrowserTimeZone,
  instantToZonedLocalTime,
  zonedLocalTimeToInstant,
} from '../lib/scheduleTime'
import { useAuth } from '../auth/AuthContext'
import { notifyError, notifySessionRescheduled, notifySessionScheduled } from '../lib/toast'

type SourceType = 'routine' | 'program'

interface Props {
  open: boolean
  onClose: () => void
  // Provided => reschedule this existing session (source stays fixed, only time/timezone change).
  // Omitted => schedule a brand-new session (full source picker shown).
  existingSession?: ScheduledSessionSummary
  // Only used for a brand-new schedule — pre-fills the date part from the Calendar's selected day.
  initialDate?: string
}

function defaultDateTimeLocal(initialDate?: string): string {
  const date = initialDate ?? new Date().toISOString().slice(0, 10)
  return `${date}T08:00`
}

export default function ScheduleSessionDialog({ open, onClose, existingSession, initialDate }: Props) {
  const { session } = useAuth()
  const queryClient = useQueryClient()
  const userId = session?.user.id ?? ''
  const isReschedule = Boolean(existingSession)

  const [sourceType, setSourceType] = useState<SourceType>('routine')
  const [selectedRoutineId, setSelectedRoutineId] = useState<string | null>(null)
  const [selectedProgramSessionId, setSelectedProgramSessionId] = useState<string | null>(null)
  const [dateTimeLocal, setDateTimeLocal] = useState(() =>
    existingSession
      ? instantToZonedLocalTime(existingSession.scheduledAt, existingSession.scheduledTimezone)
      : defaultDateTimeLocal(initialDate),
  )
  const [timezone, setTimezone] = useState(() => existingSession?.scheduledTimezone ?? getBrowserTimeZone())

  const routinesQuery = useQuery({
    queryKey: ['scheduleDialog', userId, 'routines'],
    queryFn: listMyRoutines,
    enabled: open && !isReschedule,
  })
  const programsQuery = useQuery({
    queryKey: ['scheduleDialog', userId, 'programs'],
    queryFn: listMyPrograms,
    enabled: open && !isReschedule,
  })

  const timezoneOptions = useMemo(() => {
    const zones = new Set(COMMON_TIME_ZONES)
    zones.add(timezone)
    return Array.from(zones)
      .sort()
      .map((zone) => ({ value: zone, label: zone }))
  }, [timezone])

  const mutation = useMutation({
    mutationFn: async () => {
      const scheduledAt = zonedLocalTimeToInstant(dateTimeLocal, timezone)
      if (isReschedule && existingSession) {
        await rescheduleSession(existingSession.id, scheduledAt, timezone)
        return
      }
      if (sourceType === 'routine') {
        if (!selectedRoutineId) throw new Error('Choose a Routine to schedule.')
        await scheduleRoutine(selectedRoutineId, scheduledAt, timezone)
      } else {
        if (!selectedProgramSessionId) throw new Error('Choose a session to schedule.')
        await scheduleProgramSession(selectedProgramSessionId, scheduledAt, timezone)
      }
    },
    onSuccess: () => {
      // Partial-key invalidation (no trailing range segments) — matches every scheduledSessions
      // query regardless of which month/range it was fetched for, so the visible Calendar always
      // refetches without the dialog needing to know its exact instant-range key.
      void queryClient.invalidateQueries({ queryKey: ['scheduledSessions', userId] })
      onClose()
    },
  })

  if (!open) return null

  const canSubmit = isReschedule || (sourceType === 'routine' ? Boolean(selectedRoutineId) : Boolean(selectedProgramSessionId))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSubmit || mutation.isPending) return
    const notify = isReschedule ? notifySessionRescheduled : notifySessionScheduled
    notify(mutation.mutateAsync()).catch((error) => {
      notifyError(error instanceof Error ? error.message : 'Could not save schedule')
    })
  }

  return (
    <Modal
      title={isReschedule ? 'Reschedule Session' : 'Schedule Session'}
      onClose={onClose}
      closeOnEscape={!mutation.isPending}
      closeOnBackdropClick={!mutation.isPending}
    >
      <form onSubmit={handleSubmit}>
        {isReschedule ? (
          <p className="schedule-dialog-source-label">{existingSession?.sourceLabel}</p>
        ) : (
          <>
            <div className="schedule-dialog-source-toggle">
              <button
                type="button"
                className={sourceType === 'routine' ? 'btn-secondary schedule-dialog-toggle-active' : 'btn-secondary'}
                onClick={() => setSourceType('routine')}
              >
                Routine
              </button>
              <button
                type="button"
                className={sourceType === 'program' ? 'btn-secondary schedule-dialog-toggle-active' : 'btn-secondary'}
                onClick={() => setSourceType('program')}
              >
                Program Session
              </button>
            </div>

            {sourceType === 'routine' ? (
              <div className="schedule-dialog-source-list" role="listbox" aria-label="Choose a Routine">
                {routinesQuery.isLoading && <p className="page-subtitle">Loading Routines…</p>}
                {routinesQuery.data?.length === 0 && <p className="page-subtitle">No Routines yet.</p>}
                {routinesQuery.data?.map((routine) => (
                  <button
                    key={routine.id}
                    type="button"
                    role="option"
                    aria-selected={selectedRoutineId === routine.id}
                    className={
                      selectedRoutineId === routine.id ? 'dropdown-item dropdown-item-active' : 'dropdown-item'
                    }
                    onClick={() => setSelectedRoutineId(routine.id)}
                  >
                    {routine.name}
                  </button>
                ))}
              </div>
            ) : (
              <div className="schedule-dialog-source-list" role="listbox" aria-label="Choose a Program Session">
                {programsQuery.isLoading && <p className="page-subtitle">Loading Programs…</p>}
                {programsQuery.data?.length === 0 && <p className="page-subtitle">No Programs yet.</p>}
                {programsQuery.data?.map((program) => (
                  <div key={program.id} className="schedule-dialog-program-group">
                    <p className="schedule-dialog-program-name">{program.name}</p>
                    {program.weeks.map((week) => (
                      <div key={week.id} className="schedule-dialog-week-group">
                        <p className="schedule-dialog-week-name">Week {week.position + 1}</p>
                        {week.sessions.map((programSession) => (
                          <button
                            key={programSession.id}
                            type="button"
                            role="option"
                            aria-selected={selectedProgramSessionId === programSession.id}
                            className={
                              selectedProgramSessionId === programSession.id
                                ? 'dropdown-item dropdown-item-active'
                                : 'dropdown-item'
                            }
                            onClick={() => setSelectedProgramSessionId(programSession.id)}
                          >
                            {programSession.name ?? `Session ${programSession.position + 1}`}
                          </button>
                        ))}
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </>
        )}

        <label className="field-label" htmlFor="schedule-dialog-datetime">
          Date &amp; time
        </label>
        <input
          id="schedule-dialog-datetime"
          className="table-input modal-field-input"
          type="datetime-local"
          value={dateTimeLocal}
          onChange={(e) => setDateTimeLocal(e.target.value)}
          disabled={mutation.isPending}
          required
        />

        <label className="field-label" htmlFor="schedule-dialog-timezone">
          Timezone
        </label>
        <SelectField
          id="schedule-dialog-timezone"
          value={timezone}
          options={timezoneOptions}
          onChange={setTimezone}
        />

        <div className="modal-actions">
          <button type="button" className="btn-secondary" disabled={mutation.isPending} onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={!canSubmit || mutation.isPending}>
            {isReschedule ? 'Reschedule' : 'Schedule'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
