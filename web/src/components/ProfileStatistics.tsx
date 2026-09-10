import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getDailyActivity } from '../data/dailyActivity'
import { getCompletedWorkouts } from '../data/workoutHistory'
import { calculateWorkoutStreak, workoutsPerWeek } from '../lib/workoutStats'
import { useAuth } from '../auth/AuthContext'
import { queryKeys } from '../queryKeys'
import StepsChart from './StepsChart'
import type { ChartBar } from './StepsChart'

type Tab = 'movement' | 'workouts'
type MovementPeriod = 7 | 28 | 84

const MOVEMENT_PERIODS: { value: MovementPeriod; label: string }[] = [
  { value: 7, label: '7 days' },
  { value: 28, label: '4 weeks' },
  { value: 84, label: '12 weeks' },
]

const STREAK_LOOKBACK_WEEKS = 26

function isoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function daysAgo(days: number, from: Date): Date {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  d.setDate(d.getDate() - days)
  return d
}

function weekdayLabel(dateString: string): string {
  const [y, m, d] = dateString.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { weekday: 'short' }).format(new Date(y, m - 1, d))
}

// TEMPORARY DEV PREVIEW — added at explicit user request, only because no real Movement ingestion
// source (Phone/Watch) exists yet, so every account currently has zero real `daily_activity` rows
// and the Steps chart can never otherwise be eyeballed. This is NOT real product behavior: it never
// touches `daily_activity` and never runs if the query returned even one real row (see
// `usingFakeMovementPreview` below — it's the *entire* empty-result case, not a per-day gap-filler).
// It's always paired with a visible "Preview data" label so it can never be mistaken for a real
// account's numbers.
// TO REMOVE once real Movement data exists anywhere: delete this function, the
// `usingFakeMovementPreview` constant, its use in `movementBars`, and the preview-badge JSX below —
// the `!activityQuery.isLoading && !activityQuery.isError && averageSteps === null` empty-state
// branch already handles the real "no data" case correctly and needs no other change.
function buildFakeMovementPreview(period: MovementPeriod, today: Date): ChartBar[] {
  const bars: ChartBar[] = []
  for (let i = period - 1; i >= 0; i--) {
    const date = isoDate(daysAgo(i, today))
    const steps = Math.round(6500 + 4000 * Math.sin(i * 0.6) + ((i * 733) % 1500))
    bars.push({ label: period === 7 ? weekdayLabel(date) : date.slice(5), value: steps })
  }
  return bars
}

export default function ProfileStatistics() {
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const [tab, setTab] = useState<Tab>('movement')
  const [period, setPeriod] = useState<MovementPeriod>(7)

  const today = useMemo(() => new Date(), [])
  const movementStart = useMemo(() => isoDate(daysAgo(period - 1, today)), [period, today])
  const movementEnd = useMemo(() => isoDate(today), [today])

  const activityQuery = useQuery({
    queryKey: queryKeys.dailyActivity.range(userId, movementStart, movementEnd),
    queryFn: () => getDailyActivity(movementStart, movementEnd),
    enabled: Boolean(userId) && tab === 'movement',
  })

  const streakRangeStart = useMemo(() => isoDate(daysAgo(STREAK_LOOKBACK_WEEKS * 7, today)), [today])
  const workoutHistoryQuery = useQuery({
    queryKey: queryKeys.workoutHistory.range(userId, streakRangeStart, movementEnd),
    queryFn: () => getCompletedWorkouts(streakRangeStart, movementEnd),
    enabled: Boolean(userId) && tab === 'workouts',
  })

  // TEMPORARY, see buildFakeMovementPreview's own comment: true only when the real query has
  // resolved (not loading, not errored) and came back completely empty — never when any real row
  // exists for the period.
  const usingFakeMovementPreview = !activityQuery.isLoading && !activityQuery.isError && activityQuery.data?.length === 0

  const movementBars: ChartBar[] = useMemo(() => {
    if (usingFakeMovementPreview) return buildFakeMovementPreview(period, today)
    if (!activityQuery.data) return []
    const byDate = new Map(activityQuery.data.map((point) => [point.date, point.steps]))
    const bars: ChartBar[] = []
    for (let i = period - 1; i >= 0; i--) {
      const date = isoDate(daysAgo(i, today))
      const steps = byDate.has(date) ? byDate.get(date)! : null
      bars.push({ label: period === 7 ? weekdayLabel(date) : date.slice(5), value: steps })
    }
    return bars
  }, [usingFakeMovementPreview, activityQuery.data, period, today])

  // Average is computed only over days that actually have a row — never divided by the full period
  // length, since a missing day is absent data, not a measured zero (see daily_activity's own
  // schema comment). A period with zero rows has no average to show at all.
  const averageSteps = useMemo(() => {
    const measured = movementBars.filter((bar) => bar.value !== null).map((bar) => bar.value as number)
    if (measured.length === 0) return null
    return Math.round(measured.reduce((sum, v) => sum + v, 0) / measured.length)
  }, [movementBars])

  const streak = useMemo(() => {
    if (!workoutHistoryQuery.data) return 0
    return calculateWorkoutStreak(
      workoutHistoryQuery.data.map((w) => w.workoutDate),
      today,
    )
  }, [workoutHistoryQuery.data, today])

  const frequencyBars: ChartBar[] = useMemo(() => {
    if (!workoutHistoryQuery.data) return []
    const weeks = workoutsPerWeek(
      workoutHistoryQuery.data.map((w) => w.workoutDate),
      12,
      today,
    )
    return weeks.map((week) => ({ label: week.weekStart.slice(5), value: week.count }))
  }, [workoutHistoryQuery.data, today])

  return (
    <div className="card profile-statistics-card">
      <div className="section-header">
        <h2>Statistics</h2>
      </div>

      <div className="profile-statistics-tabs" role="tablist" aria-label="Statistics">
        <button
          type="button"
          role="tab"
          id="statistics-tab-movement"
          aria-selected={tab === 'movement'}
          aria-controls="statistics-panel-movement"
          className={tab === 'movement' ? 'profile-statistics-tab profile-statistics-tab-active' : 'profile-statistics-tab'}
          onClick={() => setTab('movement')}
        >
          Movement
        </button>
        <button
          type="button"
          role="tab"
          id="statistics-tab-workouts"
          aria-selected={tab === 'workouts'}
          aria-controls="statistics-panel-workouts"
          className={tab === 'workouts' ? 'profile-statistics-tab profile-statistics-tab-active' : 'profile-statistics-tab'}
          onClick={() => setTab('workouts')}
        >
          Workouts
        </button>
      </div>

      {tab === 'movement' && (
        <div role="tabpanel" id="statistics-panel-movement" aria-labelledby="statistics-tab-movement">
          <div className="profile-statistics-period-row">
            {MOVEMENT_PERIODS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={period === option.value ? 'btn-secondary schedule-dialog-toggle-active' : 'btn-secondary'}
                onClick={() => setPeriod(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>

          {activityQuery.isLoading && <p className="page-subtitle">Loading…</p>}
          {activityQuery.isError && <p className="form-error" role="alert">Couldn't load Movement data.</p>}

          {!activityQuery.isLoading && !activityQuery.isError && averageSteps === null && !usingFakeMovementPreview && (
            <p className="page-subtitle profile-statistics-empty">No movement data yet.</p>
          )}

          {/* TEMPORARY, see buildFakeMovementPreview's own comment — remove this badge together with
              that function once real Movement data exists anywhere. */}
          {usingFakeMovementPreview && (
            <p className="profile-statistics-preview-badge">Preview data — no real Movement data yet</p>
          )}

          {averageSteps !== null && (
            <>
              <div className="profile-statistics-headline">
                <span className="profile-statistics-headline-value">{averageSteps.toLocaleString()}</span>
                <span className="profile-statistics-headline-label">avg steps/day</span>
              </div>
              <StepsChart bars={movementBars} valueLabel="steps" formatValue={(v) => v.toLocaleString()} />
            </>
          )}
        </div>
      )}

      {tab === 'workouts' && (
        <div role="tabpanel" id="statistics-panel-workouts" aria-labelledby="statistics-tab-workouts">
          {workoutHistoryQuery.isLoading && <p className="page-subtitle">Loading…</p>}
          {workoutHistoryQuery.isError && <p className="form-error" role="alert">Couldn't load Workout history.</p>}

          {!workoutHistoryQuery.isLoading && !workoutHistoryQuery.isError && (
            <>
              <div className="profile-statistics-headline">
                <span className="profile-statistics-headline-value">{streak}</span>
                <span className="profile-statistics-headline-label">week streak</span>
              </div>
              {frequencyBars.some((bar) => (bar.value ?? 0) > 0) ? (
                <StepsChart bars={frequencyBars} valueLabel="workouts" />
              ) : (
                <p className="page-subtitle profile-statistics-empty">No completed Workouts yet.</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}
