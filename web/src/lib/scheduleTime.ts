// Timezone-correct helpers for scheduled_sessions.scheduled_at (a UTC instant) +
// scheduled_timezone (the IANA zone the user intended) — see
// supabase/migrations/20260920120000_create_scheduled_sessions.sql's own header comment for why
// both are stored. This is the one correctness-critical detail in the Calendar/Scheduling UI: a
// plain `new Date(x).toLocaleTimeString()` renders in the *viewer's own* browser zone, which is only
// correct by coincidence. No date library is added for this (Moment.js etc. would be a large
// dependency for two small functions) — native `Intl`/`Date` are sufficient.

// Formats the exact wall-clock time the session was scheduled for, in ITS OWN intended timezone,
// regardless of what timezone the current viewer's browser is in.
export function formatScheduledTime(scheduledAt: string, scheduledTimezone: string): string {
  return new Intl.DateTimeFormat(undefined, {
    timeZone: scheduledTimezone,
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(scheduledAt))
}

// The local calendar date (YYYY-MM-DD) the session falls on IN ITS OWN intended timezone — this can
// differ from the date a naive UTC read would suggest for a session scheduled near midnight. Used to
// group scheduled sessions onto the correct Calendar day cell.
export function scheduledLocalDate(scheduledAt: string, scheduledTimezone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: scheduledTimezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(scheduledAt))
  const map: Record<string, string> = {}
  for (const part of parts) map[part.type] = part.value
  return `${map.year}-${map.month}-${map.day}`
}

// The browser's own IANA zone — used only as a sensible default for the Schedule dialog's timezone
// field, never assumed correct without letting the user see/change it.
export function getBrowserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

// How far `timeZone` is ahead of UTC, in minutes, at the given instant (positive = ahead of UTC).
// Computed by asking Intl how `date` reads when formatted in `timeZone`, then comparing that wall
// clock (misinterpreted as UTC) against the real instant — the standard no-library technique for
// this, since Intl has no direct "offset for this zone at this instant" API.
function timeZoneOffsetMinutesAt(date: Date, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(date)
  const map: Record<string, string> = {}
  for (const part of parts) map[part.type] = part.value
  const asUtcMs = Date.UTC(
    Number(map.year),
    Number(map.month) - 1,
    Number(map.day),
    Number(map.hour),
    Number(map.minute),
    Number(map.second),
  )
  return (asUtcMs - date.getTime()) / 60000
}

// Converts a wall-clock local date+time (as typed into an <input type="datetime-local">,
// "YYYY-MM-DDTHH:mm") plus the IANA zone the user means it in, into the real UTC instant to send as
// scheduled_at. A single offset lookup can be wrong right at a DST transition (the offset at the
// naive guess may not match the offset at the real instant); a second lookup using the corrected
// instant resolves that in every realistic case without pulling in a full timezone-arithmetic
// library.
export function zonedLocalTimeToInstant(dateTimeLocal: string, timeZone: string): string {
  const [datePart, timePart] = dateTimeLocal.split('T')
  const [year, month, day] = datePart.split('-').map(Number)
  const [hour, minute] = timePart.split(':').map(Number)
  const naiveUtcMs = Date.UTC(year, month - 1, day, hour, minute, 0)

  const firstPassOffset = timeZoneOffsetMinutesAt(new Date(naiveUtcMs), timeZone)
  const candidateMs = naiveUtcMs - firstPassOffset * 60000

  const secondPassOffset = timeZoneOffsetMinutesAt(new Date(candidateMs), timeZone)
  const instantMs = naiveUtcMs - secondPassOffset * 60000

  return new Date(instantMs).toISOString()
}

// The inverse of the above, for pre-filling a reschedule dialog's <input type="datetime-local">
// with the session's current wall-clock value in its own intended timezone.
export function instantToZonedLocalTime(scheduledAt: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).formatToParts(new Date(scheduledAt))
  const map: Record<string, string> = {}
  for (const part of parts) map[part.type] = part.value
  return `${map.year}-${map.month}-${map.day}T${map.hour}:${map.minute}`
}

// A reasonable curated list for the Schedule dialog's timezone picker — `Intl.supportedValuesOf`
// exists in modern browsers but is not reliably typed under this project's current TS lib target,
// and enumerating every IANA zone (~400) would make a cramped dropdown worse, not better. The
// backend's own is_valid_iana_timezone() (pg_timezone_names-backed) remains the actual source of
// truth; an unlisted zone is not reachable through this picker, but the dialog always defaults to
// the browser's own zone (getBrowserTimeZone), so the common case never requires this list at all.
export const COMMON_TIME_ZONES: readonly string[] = [
  'UTC',
  'Europe/Stockholm',
  'Europe/Helsinki',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'Europe/Madrid',
  'Europe/Rome',
  'Europe/Oslo',
  'Europe/Copenhagen',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Sao_Paulo',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Asia/Kolkata',
  'Asia/Dubai',
  'Australia/Sydney',
  'Pacific/Auckland',
]
