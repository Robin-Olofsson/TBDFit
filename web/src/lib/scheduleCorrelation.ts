// Pure grouping/correlation logic for the Profile Calendar — split out for unit testing, same
// convention as workoutStats.ts/calendarMonth.ts.
import type { ScheduledSessionSummary } from '../data/scheduledSessions'
import type { CompletedWorkoutSummary } from '../data/workoutHistory'
import { scheduledLocalDate } from './scheduleTime'

// Groups scheduled sessions onto the local calendar day each falls on IN ITS OWN intended timezone
// (see scheduleTime.ts's scheduledLocalDate) — never a naive UTC-date read of scheduled_at.
export function groupScheduledSessionsByLocalDate(
  sessions: ScheduledSessionSummary[],
): Map<string, ScheduledSessionSummary[]> {
  const map = new Map<string, ScheduledSessionSummary[]>()
  for (const session of sessions) {
    const date = scheduledLocalDate(session.scheduledAt, session.scheduledTimezone)
    const existing = map.get(date)
    if (existing) existing.push(session)
    else map.set(date, [session])
  }
  return map
}

export interface DayAgendaEntry {
  scheduled: ScheduledSessionSummary
  completedWorkout: CompletedWorkoutSummary | null
}

export interface DayAgenda {
  // Scheduled sessions for the day, ordered by time, each correlated to its completed Workout if
  // one exists.
  entries: DayAgendaEntry[]
  // Completed Workouts with no correlated scheduled entry above — either genuinely ad-hoc
  // (originScheduledSessionId is null) or whose origin session isn't part of this day's scheduled
  // list (e.g. it was later unscheduled). Both cases must still show as completed history for the
  // day, just without a "Scheduled" counterpart.
  adHocCompleted: CompletedWorkoutSummary[]
}

// Correlates one day's scheduled sessions with its completed Workouts SOLELY via
// origin_scheduled_session_id — never by inferring from "same date" or "same Routine" (see
// workouts.origin_scheduled_session_id's own migration comment on why that inference is wrong: two
// same-Routine sessions on the same day must stay independently correlated).
export function buildDayAgenda(scheduled: ScheduledSessionSummary[], completed: CompletedWorkoutSummary[]): DayAgenda {
  const completedBySessionId = new Map<string, CompletedWorkoutSummary>()
  for (const workout of completed) {
    if (workout.originScheduledSessionId) completedBySessionId.set(workout.originScheduledSessionId, workout)
  }

  const entries: DayAgendaEntry[] = [...scheduled]
    .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
    .map((session) => ({ scheduled: session, completedWorkout: completedBySessionId.get(session.id) ?? null }))

  const correlatedWorkoutIds = new Set(
    entries.map((entry) => entry.completedWorkout?.id).filter((id): id is string => Boolean(id)),
  )
  const adHocCompleted = completed.filter((workout) => !correlatedWorkoutIds.has(workout.id))

  return { entries, adHocCompleted }
}
