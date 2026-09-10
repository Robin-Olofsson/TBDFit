import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Plus, Calendar as CalendarIcon, Check } from 'lucide-react'
import { getScheduledSessions, unscheduleSession, type ScheduledSessionSummary } from '../data/scheduledSessions'
import { getCompletedWorkouts } from '../data/workoutHistory'
import { getMonthGrid, monthGridInstantRange, monthLabel, addMonths, todayDateString } from '../lib/calendarMonth'
import { groupScheduledSessionsByLocalDate, buildDayAgenda } from '../lib/scheduleCorrelation'
import { groupCompletedWorkoutsByDate } from '../lib/workoutStats'
import { formatScheduledTime } from '../lib/scheduleTime'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import { notifySessionUnscheduled, notifyError } from '../lib/toast'
import ScheduleSessionDialog from './ScheduleSessionDialog'
import ConfirmDialog from './ConfirmDialog'

function workoutDurationLabel(startedAt: string, completedAt: string): string {
  const minutes = Math.max(0, Math.round((new Date(completedAt).getTime() - new Date(startedAt).getTime()) / 60000))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const remaining = minutes % 60
  return remaining === 0 ? `${hours} hr` : `${hours} hr ${remaining} min`
}

// The Profile Calendar: a real month grid backed by scheduled_sessions + completed workouts (never
// a decorative placeholder). See scheduleCorrelation.ts for why "completed" is decided solely by
// origin_scheduled_session_id, never by same-date/same-source inference.
export default function ProfileCalendar() {
  const { session } = useAuth()
  const queryClient = useQueryClient()
  const userId = session?.user.id ?? ''

  const today = useMemo(() => new Date(), [])
  const [year, setYear] = useState(today.getFullYear())
  const [month0, setMonth0] = useState(today.getMonth())
  const [selectedDate, setSelectedDate] = useState(todayDateString(today))
  const [scheduleDialogOpen, setScheduleDialogOpen] = useState(false)
  const [rescheduleTarget, setRescheduleTarget] = useState<ScheduledSessionSummary | null>(null)
  const [unscheduleTarget, setUnscheduleTarget] = useState<ScheduledSessionSummary | null>(null)

  const grid = useMemo(() => getMonthGrid(year, month0), [year, month0])
  const { startInstant, endInstant } = useMemo(() => monthGridInstantRange(year, month0), [year, month0])
  const startDate = grid[0][0].date
  const endDate = grid[grid.length - 1][6].date

  const scheduledQuery = useQuery({
    queryKey: queryKeys.scheduledSessions.range(userId, startInstant, endInstant),
    queryFn: () => getScheduledSessions(startInstant, endInstant),
    enabled: Boolean(userId),
  })
  const completedQuery = useQuery({
    queryKey: queryKeys.workoutHistory.range(userId, startDate, endDate),
    queryFn: () => getCompletedWorkouts(startDate, endDate),
    enabled: Boolean(userId),
  })

  const scheduledByDate = useMemo(
    () => groupScheduledSessionsByLocalDate(scheduledQuery.data ?? []),
    [scheduledQuery.data],
  )
  const completedByDate = useMemo(() => groupCompletedWorkoutsByDate(completedQuery.data ?? []), [completedQuery.data])

  const selectedAgenda = useMemo(
    () => buildDayAgenda(scheduledByDate.get(selectedDate) ?? [], completedByDate.get(selectedDate) ?? []),
    [scheduledByDate, completedByDate, selectedDate],
  )

  const unscheduleMutation = useMutation({
    mutationFn: (id: string) => unscheduleSession(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['scheduledSessions', userId] })
      setUnscheduleTarget(null)
    },
  })

  const goToMonth = (delta: number) => {
    const next = addMonths(year, month0, delta)
    setYear(next.year)
    setMonth0(next.month0)
  }

  const isLoading = scheduledQuery.isLoading || completedQuery.isLoading
  const hasError = scheduledQuery.isError || completedQuery.isError

  return (
    <div className="card profile-calendar-card">
      <div className="section-header">
        <h2>Calendar</h2>
      </div>

      <div className="profile-calendar-nav">
        <button type="button" className="btn-icon" aria-label="Previous month" onClick={() => goToMonth(-1)}>
          <ChevronLeft size={16} aria-hidden="true" />
        </button>
        <span className="profile-calendar-month-label">{monthLabel(year, month0)}</span>
        <button type="button" className="btn-icon" aria-label="Next month" onClick={() => goToMonth(1)}>
          <ChevronRight size={16} aria-hidden="true" />
        </button>
      </div>

      {hasError && <p className="form-error" role="alert">Couldn't load your Calendar. Try again shortly.</p>}

      <div className="profile-calendar-weekday-row">
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((weekday) => (
          <span key={weekday} className="profile-calendar-weekday">
            {weekday}
          </span>
        ))}
      </div>

      <div className="profile-calendar-grid">
        {grid.flat().map((day) => {
          const hasScheduled = (scheduledByDate.get(day.date)?.length ?? 0) > 0
          const hasCompleted = (completedByDate.get(day.date)?.length ?? 0) > 0
          const isSelected = day.date === selectedDate
          const isToday = day.date === todayDateString(today)
          return (
            <button
              key={day.date}
              type="button"
              className={[
                'profile-calendar-day',
                day.inCurrentMonth ? '' : 'profile-calendar-day-outside',
                isSelected ? 'profile-calendar-day-selected' : '',
                isToday ? 'profile-calendar-day-today' : '',
              ]
                .filter(Boolean)
                .join(' ')}
              aria-pressed={isSelected}
              aria-label={`${day.date}${hasScheduled ? ', has scheduled sessions' : ''}${hasCompleted ? ', has completed workouts' : ''}`}
              onClick={() => setSelectedDate(day.date)}
            >
              <span className="profile-calendar-day-number">{Number(day.date.slice(8, 10))}</span>
              <span className="profile-calendar-day-markers">
                {hasScheduled && <CalendarIcon size={10} aria-hidden="true" className="profile-calendar-marker-scheduled" />}
                {hasCompleted && <Check size={10} aria-hidden="true" className="profile-calendar-marker-completed" />}
              </span>
            </button>
          )
        })}
      </div>

      <div className="profile-calendar-agenda">
        <div className="section-header">
          <h3>{selectedDate}</h3>
          <button type="button" className="btn-link" onClick={() => setScheduleDialogOpen(true)}>
            <Plus size={14} aria-hidden="true" /> Schedule session
          </button>
        </div>

        {isLoading && <p className="page-subtitle">Loading…</p>}

        {!isLoading && selectedAgenda.entries.length === 0 && selectedAgenda.adHocCompleted.length === 0 && (
          <p className="page-subtitle">No sessions scheduled.</p>
        )}

        <ul className="profile-calendar-agenda-list">
          {selectedAgenda.entries.map((entry) => (
            <li key={entry.scheduled.id} className="profile-calendar-agenda-item">
              <div>
                <span className="profile-calendar-agenda-time">
                  {formatScheduledTime(entry.scheduled.scheduledAt, entry.scheduled.scheduledTimezone)}
                </span>
                <span className="profile-calendar-agenda-title">{entry.scheduled.sourceLabel}</span>
                <span className="profile-calendar-agenda-status">
                  {entry.completedWorkout ? 'Completed' : 'Scheduled'}
                </span>
              </div>
              {!entry.completedWorkout && (
                <div className="profile-calendar-agenda-actions">
                  <button type="button" className="btn-link" onClick={() => setRescheduleTarget(entry.scheduled)}>
                    Reschedule
                  </button>
                  <button type="button" className="btn-link-danger" onClick={() => setUnscheduleTarget(entry.scheduled)}>
                    Unschedule
                  </button>
                </div>
              )}
            </li>
          ))}
          {selectedAgenda.adHocCompleted.map((workout) => (
            <li key={workout.id} className="profile-calendar-agenda-item">
              <div>
                <span className="profile-calendar-agenda-time">
                  {new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(
                    new Date(workout.startedAt),
                  )}
                </span>
                <span className="profile-calendar-agenda-title">{workout.name}</span>
                <span className="profile-calendar-agenda-status">
                  {workoutDurationLabel(workout.startedAt, workout.completedAt)} · Completed
                </span>
              </div>
            </li>
          ))}
        </ul>
      </div>

      {scheduleDialogOpen && (
        <ScheduleSessionDialog open={scheduleDialogOpen} initialDate={selectedDate} onClose={() => setScheduleDialogOpen(false)} />
      )}
      {rescheduleTarget && (
        <ScheduleSessionDialog
          open={Boolean(rescheduleTarget)}
          existingSession={rescheduleTarget}
          onClose={() => setRescheduleTarget(null)}
        />
      )}
      {unscheduleTarget && (
        <ConfirmDialog
          open={Boolean(unscheduleTarget)}
          title="Unschedule session?"
          description={`This removes "${unscheduleTarget.sourceLabel}" from your schedule. It does not delete the Routine or Program.`}
          confirmLabel="Unschedule"
          destructive
          pending={unscheduleMutation.isPending}
          onConfirm={() => {
            notifySessionUnscheduled(unscheduleMutation.mutateAsync(unscheduleTarget.id)).catch(() =>
              notifyError('Could not unschedule session'),
            )
          }}
          onCancel={() => setUnscheduleTarget(null)}
        />
      )}
    </div>
  )
}
