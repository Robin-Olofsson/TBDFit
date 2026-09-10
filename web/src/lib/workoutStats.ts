// Pure Workout-statistics logic (streak, weekly frequency, date grouping) — split out from any
// component so it's unit-testable without a rendering harness, the same convention this codebase
// already uses throughout (see profileState.ts's deriveProfileState, plannedExerciseDrafts.ts).
// Everything here operates on workout_date ("YYYY-MM-DD") strings, never started_at/completed_at
// instants — see workouts' own schema comment on why workout_date is the local-day grouping column.
import type { CompletedWorkoutSummary } from '../data/workoutHistory'

// ISO-8601 week key ("2026-W38") for a plain calendar date, computed from (year, month0, day)
// components only — never from a timezone-sensitive Date parse. The standard "move to this week's
// Thursday, then count Thursdays since week 1" algorithm; Date.UTC here is only a calculation
// scratchpad for calendar arithmetic, not a representation of any real instant/timezone.
function isoWeekKeyFromParts(year: number, month0: number, day: number): string {
  const d = new Date(Date.UTC(year, month0, day))
  const isoDayNum = (d.getUTCDay() + 6) % 7 // Monday=0 ... Sunday=6
  d.setUTCDate(d.getUTCDate() - isoDayNum + 3) // Thursday of this ISO week
  const isoYear = d.getUTCFullYear()
  const jan4 = new Date(Date.UTC(isoYear, 0, 4))
  const jan4DayNum = (jan4.getUTCDay() + 6) % 7
  const week1Monday = new Date(Date.UTC(isoYear, 0, 4 - jan4DayNum))
  const weekNum = Math.round((d.getTime() - week1Monday.getTime()) / (7 * 86400000)) + 1
  return `${isoYear}-W${String(weekNum).padStart(2, '0')}`
}

function isoWeekKeyFromDateString(dateString: string): string {
  const [year, month, day] = dateString.split('-').map(Number)
  return isoWeekKeyFromParts(year, month - 1, day)
}

function isoWeekKeyFromDate(date: Date): string {
  return isoWeekKeyFromParts(date.getFullYear(), date.getMonth(), date.getDate())
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  d.setDate(d.getDate() + days)
  return d
}

function mondayOf(date: Date): Date {
  const isoDayNum = (date.getDay() + 6) % 7
  return addDays(date, -isoDayNum)
}

function formatDateOnly(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// STREAK DEFINITION (deliberate, documented decision — not consecutive Workout *days*):
// a streak is the count of consecutive ISO-8601 weeks (Monday-start), walking backward from the
// week containing `today`, that each contain at least one completed Workout.
//
// Current-week handling: if the week containing `today` already has a workout, counting starts
// there. If it has none *yet* (the week isn't over), that alone must not zero out a real ongoing
// streak — the check falls back to the immediately preceding week instead. Only if that preceding
// week is also empty does the streak read as 0. This means a user who trained every week through
// last week, but hasn't yet trained this week, still sees their real streak up until they let a
// FULL week pass with nothing in it.
export function calculateWorkoutStreak(workoutDates: string[], today: Date): number {
  if (workoutDates.length === 0) return 0
  const weeksWithWorkout = new Set(workoutDates.map(isoWeekKeyFromDateString))

  const currentWeekKey = isoWeekKeyFromDate(today)
  let cursor: Date
  if (weeksWithWorkout.has(currentWeekKey)) {
    cursor = today
  } else {
    const lastWeek = addDays(today, -7)
    if (!weeksWithWorkout.has(isoWeekKeyFromDate(lastWeek))) return 0
    cursor = lastWeek
  }

  let streak = 0
  while (weeksWithWorkout.has(isoWeekKeyFromDate(cursor))) {
    streak += 1
    cursor = addDays(cursor, -7)
  }
  return streak
}

export interface WeeklyWorkoutCount {
  weekStart: string // YYYY-MM-DD, the Monday of that ISO week
  count: number
}

// Workouts-per-ISO-week for the `weekCount` weeks ending with the week containing `today`, oldest
// first — the data shape the (optional) frequency chart renders directly.
export function workoutsPerWeek(workoutDates: string[], weekCount: number, today: Date): WeeklyWorkoutCount[] {
  const counts = new Map<string, number>()
  for (const dateString of workoutDates) {
    const key = isoWeekKeyFromDateString(dateString)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }

  const buckets: { key: string; monday: Date }[] = []
  let cursor = today
  for (let i = 0; i < weekCount; i++) {
    buckets.unshift({ key: isoWeekKeyFromDate(cursor), monday: mondayOf(cursor) })
    cursor = addDays(cursor, -7)
  }

  return buckets.map((bucket) => ({ weekStart: formatDateOnly(bucket.monday), count: counts.get(bucket.key) ?? 0 }))
}

// Groups completed Workouts by their local workout_date — the Calendar's per-day agenda reads
// directly from this rather than re-deriving grouping logic inline.
export function groupCompletedWorkoutsByDate(workouts: CompletedWorkoutSummary[]): Map<string, CompletedWorkoutSummary[]> {
  const map = new Map<string, CompletedWorkoutSummary[]>()
  for (const workout of workouts) {
    const existing = map.get(workout.workoutDate)
    if (existing) existing.push(workout)
    else map.set(workout.workoutDate, [workout])
  }
  return map
}
