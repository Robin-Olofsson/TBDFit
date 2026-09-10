import { supabase } from '../lib/supabaseClient'

// Read-only Web access to completed Workout History (supabase/migrations/20260917120000 +
// 20260920130000_add_workout_schedule_provenance.sql) — same domain-module convention as
// data/dailyActivity.ts. RLS scopes every row to its own account; Web has no write path here at
// all (record_completed_workout is a native-execution-only RPC — see that migration's own header
// comment on the client-agnostic-by-design boundary). This module exists for the Profile
// Statistics/Calendar reads only.
export interface CompletedWorkoutSummary {
  id: string
  name: string
  workoutDate: string
  startedAt: string
  completedAt: string
  originScheduledSessionId: string | null
}

interface CompletedWorkoutRow {
  id: string
  name: string
  workout_date: string
  started_at: string
  completed_at: string
  origin_scheduled_session_id: string | null
}

// Inclusive workout_date range, both bounds "YYYY-MM-DD". Used both for the Calendar's visible
// month and, with a wider fixed range computed by the caller, for streak/frequency statistics —
// there is deliberately no second query function for that; the shape is identical either way.
export async function getCompletedWorkouts(startDate: string, endDate: string): Promise<CompletedWorkoutSummary[]> {
  const { data, error } = await supabase
    .from('workouts')
    .select('id, name, workout_date, started_at, completed_at, origin_scheduled_session_id')
    .gte('workout_date', startDate)
    .lte('workout_date', endDate)
    .order('workout_date', { ascending: true })
  if (error) throw error

  return (data as CompletedWorkoutRow[]).map((row) => ({
    id: row.id,
    name: row.name,
    workoutDate: row.workout_date,
    startedAt: row.started_at,
    completedAt: row.completed_at,
    originScheduledSessionId: row.origin_scheduled_session_id,
  }))
}
