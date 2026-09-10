import { supabase } from '../lib/supabaseClient'

// Web read/write access to Scheduling (supabase/migrations/20260920120000_create_scheduled_sessions.sql).
// Unlike dailyActivity.ts/workoutHistory.ts, this module is NOT read-only — Web scheduling
// (schedule/reschedule/unschedule a Routine or ProgramSession) is an explicitly valid Web product
// responsibility (Web plans; only native execution is off-limits). All four operations rely
// entirely on that migration's own RLS policies, which already validate ownership through the full
// Routine/ProgramSession ownership chain — there is no client-side ownership pre-check here, since
// one would be redundant with (and could silently drift from) the database's own enforcement.
// owner_id is never sent by the client; it defaults server-side from auth.uid(), exactly like
// workouts.owner_id.
export interface ScheduledSessionSummary {
  id: string
  scheduledAt: string
  scheduledTimezone: string
  routineId: string | null
  programSessionId: string | null
  sourceLabel: string
}

interface ScheduledSessionRow {
  id: string
  scheduled_at: string
  scheduled_timezone: string
  routine_id: string | null
  program_session_id: string | null
  routines: { name: string } | null
  program_sessions: {
    name: string | null
    position: number
    program_weeks: { position: number; programs: { name: string } | null } | null
  } | null
}

function sourceLabelFor(row: ScheduledSessionRow): string {
  if (row.routines) return row.routines.name
  const session = row.program_sessions
  if (!session) return 'Unknown session'
  // Mirrors ProgramBuilderPage.tsx's own `session.name ?? \`Session ${index + 1}\`` fallback
  // convention exactly — position is 0-based, displayed 1-based, same as that file.
  const sessionLabel = session.name ?? `Session ${session.position + 1}`
  const week = session.program_weeks
  if (!week) return sessionLabel
  const programName = week.programs?.name ?? 'Program'
  return `${programName} · Week ${week.position + 1} · ${sessionLabel}`
}

// Queried by INSTANT range (scheduled_at is a timestamptz, not a date) — pass the visible calendar
// window's start/end instants, not calendar-date strings. Uses PostgREST's embedded-resource select
// (real FKs exist for both routine_id and program_session_id) so the display label is available in
// one round trip instead of N+1 follow-up fetches per row.
export async function getScheduledSessions(startInstant: string, endInstant: string): Promise<ScheduledSessionSummary[]> {
  const { data, error } = await supabase
    .from('scheduled_sessions')
    .select(
      `
      id, scheduled_at, scheduled_timezone, routine_id, program_session_id,
      routines ( name ),
      program_sessions ( name, position, program_weeks ( position, programs ( name ) ) )
    `,
    )
    .gte('scheduled_at', startInstant)
    .lte('scheduled_at', endInstant)
    .order('scheduled_at', { ascending: true })
  if (error) throw error

  return (data as unknown as ScheduledSessionRow[]).map((row) => ({
    id: row.id,
    scheduledAt: row.scheduled_at,
    scheduledTimezone: row.scheduled_timezone,
    routineId: row.routine_id,
    programSessionId: row.program_session_id,
    sourceLabel: sourceLabelFor(row),
  }))
}

export async function scheduleRoutine(routineId: string, scheduledAt: string, scheduledTimezone: string): Promise<void> {
  const { error } = await supabase
    .from('scheduled_sessions')
    .insert({ routine_id: routineId, scheduled_at: scheduledAt, scheduled_timezone: scheduledTimezone })
  if (error) throw error
}

export async function scheduleProgramSession(
  programSessionId: string,
  scheduledAt: string,
  scheduledTimezone: string,
): Promise<void> {
  const { error } = await supabase
    .from('scheduled_sessions')
    .insert({ program_session_id: programSessionId, scheduled_at: scheduledAt, scheduled_timezone: scheduledTimezone })
  if (error) throw error
}

export async function rescheduleSession(id: string, scheduledAt: string, scheduledTimezone: string): Promise<void> {
  const { error } = await supabase
    .from('scheduled_sessions')
    .update({ scheduled_at: scheduledAt, scheduled_timezone: scheduledTimezone, updated_at: new Date().toISOString() })
    .eq('id', id)
  if (error) throw error
}

export async function unscheduleSession(id: string): Promise<void> {
  const { error } = await supabase.from('scheduled_sessions').delete().eq('id', id)
  if (error) throw error
}
