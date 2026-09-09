import type { HistoryEntry } from '../types'

// PROTOTYPE-ONLY sample data — see docs/product/frontend-prototype-notes.md. Not persisted, not
// fetched from any backend. Reset on every reload.
//
// Routine/RoutineExercise sample data used to live here. As of the Supabase Routine vertical
// slice (supabase/migrations/20260910120000_create_routines.sql, src/data/routines.ts), Routines
// are REAL, per-account, Supabase-backed data — there is no hardcoded sample-routine fixture any
// more, on purpose (see PlanPage.tsx's real empty state for a brand-new account instead of a fake
// seeded list). History/Progress below remain prototype-only; that backend does not exist yet.

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
