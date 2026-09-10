import { describe, expect, it } from 'vitest'
import { calculateWorkoutStreak, groupCompletedWorkoutsByDate, workoutsPerWeek } from './workoutStats'
import type { CompletedWorkoutSummary } from '../data/workoutHistory'

const TODAY = new Date(2026, 8, 16) // a fixed Wednesday — deterministic regardless of when tests run

function isoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

// Subtracting an exact multiple of 7 days always lands in the week exactly that many ISO weeks
// earlier, regardless of where in the week `TODAY` itself falls — so tests can build fixtures
// without reimplementing ISO-week math.
function dateWeeksAgo(weeks: number): string {
  const d = new Date(TODAY)
  d.setDate(d.getDate() - weeks * 7)
  return isoDate(d)
}

describe('calculateWorkoutStreak', () => {
  it('returns 0 for no workout history at all', () => {
    expect(calculateWorkoutStreak([], TODAY)).toBe(0)
  })

  it('counts an unbroken run of weeks including the current week', () => {
    const dates = [dateWeeksAgo(0), dateWeeksAgo(1), dateWeeksAgo(2), dateWeeksAgo(3)]
    expect(calculateWorkoutStreak(dates, TODAY)).toBe(4)
  })

  it('stops at the first gap when walking backward', () => {
    // weeks 0,1,2 have workouts, week 3 does not, week 4 does — the streak must stop at the gap.
    const dates = [dateWeeksAgo(0), dateWeeksAgo(1), dateWeeksAgo(2), dateWeeksAgo(4)]
    expect(calculateWorkoutStreak(dates, TODAY)).toBe(3)
  })

  it('does not zero out a real streak just because the current in-progress week has no workout yet', () => {
    // No workout this week (week 0), but last week (1) and the week before (2) both have one.
    const dates = [dateWeeksAgo(1), dateWeeksAgo(2)]
    expect(calculateWorkoutStreak(dates, TODAY)).toBe(2)
  })

  it('reads as 0 once a full past week has also gone by with nothing in it', () => {
    // Neither this week nor last week has a workout — even though older weeks do, the streak is
    // over.
    const dates = [dateWeeksAgo(2), dateWeeksAgo(3)]
    expect(calculateWorkoutStreak(dates, TODAY)).toBe(0)
  })

  it('extends the streak when the current week already has a workout', () => {
    const dates = [dateWeeksAgo(0)]
    expect(calculateWorkoutStreak(dates, TODAY)).toBe(1)
  })

  it('counts multiple workouts in the same week as one streak week, not extra length', () => {
    const dates = [dateWeeksAgo(0), isoDate(new Date(TODAY.getFullYear(), TODAY.getMonth(), TODAY.getDate() - 1))]
    expect(calculateWorkoutStreak(dates, TODAY)).toBe(1)
  })
})

describe('workoutsPerWeek', () => {
  it('buckets workouts into the correct week and reports 0 for weeks with none, oldest first', () => {
    const dates = [dateWeeksAgo(0), dateWeeksAgo(0), dateWeeksAgo(2)]
    const weeks = workoutsPerWeek(dates, 3, TODAY)
    expect(weeks).toHaveLength(3)
    expect(weeks[2].count).toBe(2) // this week (last entry, oldest-first order)
    expect(weeks[1].count).toBe(0) // last week
    expect(weeks[0].count).toBe(1) // two weeks ago (first entry)
  })
})

describe('groupCompletedWorkoutsByDate', () => {
  const workout = (id: string, workoutDate: string): CompletedWorkoutSummary => ({
    id,
    name: 'W',
    workoutDate,
    startedAt: `${workoutDate}T10:00:00Z`,
    completedAt: `${workoutDate}T11:00:00Z`,
    originScheduledSessionId: null,
  })

  it('groups multiple workouts on the same date together', () => {
    const grouped = groupCompletedWorkoutsByDate([workout('a', '2026-09-10'), workout('b', '2026-09-10'), workout('c', '2026-09-11')])
    expect(grouped.get('2026-09-10')).toHaveLength(2)
    expect(grouped.get('2026-09-11')).toHaveLength(1)
    expect(grouped.get('2026-09-12')).toBeUndefined()
  })
})
