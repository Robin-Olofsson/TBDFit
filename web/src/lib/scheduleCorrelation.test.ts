import { describe, expect, it } from 'vitest'
import { buildDayAgenda, groupScheduledSessionsByLocalDate } from './scheduleCorrelation'
import type { ScheduledSessionSummary } from '../data/scheduledSessions'
import type { CompletedWorkoutSummary } from '../data/workoutHistory'

function session(id: string, scheduledAt: string, scheduledTimezone = 'UTC'): ScheduledSessionSummary {
  return { id, scheduledAt, scheduledTimezone, routineId: 'r1', programSessionId: null, sourceLabel: `Session ${id}` }
}

function workout(id: string, workoutDate: string, originScheduledSessionId: string | null): CompletedWorkoutSummary {
  return {
    id,
    name: `Workout ${id}`,
    workoutDate,
    startedAt: `${workoutDate}T10:00:00Z`,
    completedAt: `${workoutDate}T11:00:00Z`,
    originScheduledSessionId,
  }
}

describe('groupScheduledSessionsByLocalDate', () => {
  it('groups by the sessions own local date, not a naive UTC read', () => {
    // 23:30 UTC on the 15th is already the 16th in a UTC+13 zone.
    const sessions = [session('a', '2026-01-15T23:30:00Z', 'Pacific/Auckland')]
    const grouped = groupScheduledSessionsByLocalDate(sessions)
    expect(grouped.get('2026-01-16')).toHaveLength(1)
    expect(grouped.has('2026-01-15')).toBe(false)
  })
})

describe('buildDayAgenda', () => {
  it('correlates a completed workout to its scheduled session via originScheduledSessionId only', () => {
    const scheduled = [session('s1', '2026-09-21T08:00:00Z')]
    const completed = [workout('w1', '2026-09-21', 's1')]
    const agenda = buildDayAgenda(scheduled, completed)
    expect(agenda.entries).toHaveLength(1)
    expect(agenda.entries[0].completedWorkout?.id).toBe('w1')
    expect(agenda.adHocCompleted).toHaveLength(0)
  })

  it('keeps two same-source sessions on the same day independently correlated', () => {
    const scheduled = [session('s1', '2026-09-21T08:00:00Z'), session('s2', '2026-09-21T18:00:00Z')]
    const completed = [workout('w2', '2026-09-21', 's2')]
    const agenda = buildDayAgenda(scheduled, completed)
    const morning = agenda.entries.find((e) => e.scheduled.id === 's1')
    const evening = agenda.entries.find((e) => e.scheduled.id === 's2')
    expect(morning?.completedWorkout).toBeNull()
    expect(evening?.completedWorkout?.id).toBe('w2')
  })

  it('surfaces an ad-hoc completed workout (no origin session) separately', () => {
    const scheduled: ScheduledSessionSummary[] = []
    const completed = [workout('w3', '2026-09-21', null)]
    const agenda = buildDayAgenda(scheduled, completed)
    expect(agenda.entries).toHaveLength(0)
    expect(agenda.adHocCompleted).toHaveLength(1)
  })

  it('orders scheduled entries by time', () => {
    const scheduled = [session('late', '2026-09-21T18:00:00Z'), session('early', '2026-09-21T08:00:00Z')]
    const agenda = buildDayAgenda(scheduled, [])
    expect(agenda.entries.map((e) => e.scheduled.id)).toEqual(['early', 'late'])
  })

  it('treats a scheduled session with no matching completed workout as still pending', () => {
    const scheduled = [session('s1', '2026-09-21T08:00:00Z')]
    const agenda = buildDayAgenda(scheduled, [])
    expect(agenda.entries[0].completedWorkout).toBeNull()
  })
})
