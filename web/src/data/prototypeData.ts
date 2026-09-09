import type { HistoryEntry, Routine } from '../types'

// PROTOTYPE-ONLY sample data — see docs/product/frontend-prototype-notes.md. Not persisted, not
// fetched from any backend. Reset on every reload. Chosen to mirror the Phone prototype's sample
// content conceptually (same exercise names, same routine names) so a reviewer comparing both
// clients side by side sees the same product, not two unrelated demos.

export const PROTOTYPE_ROUTINES: Routine[] = [
  {
    id: 'proto-routine-push',
    name: 'Push Day',
    exercises: [
      { id: 'ex-bench-press', name: 'Bench Press', plannedSets: 4, plannedReps: 8 },
      { id: 'ex-overhead-press', name: 'Overhead Press', plannedSets: 3, plannedReps: 10 },
    ],
  },
  {
    id: 'proto-routine-pull',
    name: 'Pull Day',
    exercises: [
      { id: 'ex-deadlift', name: 'Deadlift', plannedSets: 3, plannedReps: 5 },
      { id: 'ex-barbell-row', name: 'Barbell Row', plannedSets: 4, plannedReps: 8 },
      { id: 'ex-pull-up', name: 'Pull-Up', plannedSets: 3, plannedReps: 6 },
    ],
  },
  {
    id: 'proto-routine-legs',
    name: 'Leg Day',
    exercises: [{ id: 'ex-back-squat', name: 'Back Squat', plannedSets: 5, plannedReps: 5 }],
  },
]

// Representative exercise search results for the Routine Builder's "add exercise" flow — a small,
// fixed list standing in for a real exercise-library query (no Exercise backend on web; see
// product-information-architecture.md's Exercise Identity direction, not re-litigated here).
export const PROTOTYPE_EXERCISE_LIBRARY: string[] = [
  'Bench Press',
  'Back Squat',
  'Deadlift',
  'Overhead Press',
  'Barbell Row',
  'Pull-Up',
  'Incline Bench Press',
  'Romanian Deadlift',
  'Front Squat',
  'Lat Pulldown',
]

export const PROTOTYPE_HISTORY: HistoryEntry[] = [
  {
    id: 'proto-history-1',
    title: 'Push Day',
    dateLabel: 'Yesterday',
    summary: '2 exercises · 7 sets',
    exerciseLines: ['Bench Press — 4×8 @ 80kg', 'Overhead Press — 3×10 @ 40kg'],
  },
  {
    id: 'proto-history-2',
    title: 'Pull Day',
    dateLabel: '3 days ago',
    summary: '3 exercises · 10 sets',
    exerciseLines: ['Deadlift — 3×5 @ 140kg', 'Barbell Row — 4×8 @ 60kg', 'Pull-Up — 3×6 (bodyweight)'],
  },
  {
    id: 'proto-history-3',
    title: 'Leg Day',
    dateLabel: '1 week ago',
    summary: '1 exercise · 5 sets',
    exerciseLines: ['Back Squat — 5×5 @ 100kg'],
  },
]

export const PROTOTYPE_PROGRESS_STATS: { label: string; value: string; sublabel?: string }[] = [
  { label: 'Workouts this week', value: '3' },
  { label: 'Estimated Bench Press 1RM', value: '~95 kg' },
  { label: 'Estimated Back Squat 1RM', value: '~125 kg' },
]

// Home dashboard's three headline stat cards — representative prototype values only, matching the
// approved visual reference's "Workouts / Total Volume / Current Streak" concept. No analytics
// backend/computation exists; these are hand-picked sample numbers, not derived from
// PROTOTYPE_HISTORY (there are only 3 sample history entries, not 24 workouts) — do not read them
// as consistent with each other or as real user data.
export const PROTOTYPE_DASHBOARD_STATS: { label: string; value: string; sublabel?: string }[] = [
  { label: 'Workouts', value: '24', sublabel: 'This month' },
  { label: 'Total Volume', value: '12,450 kg', sublabel: '+12% vs last month' },
  { label: 'Current Streak', value: '8 days', sublabel: 'Keep it going' },
]
