import { describe, expect, it } from 'vitest'
import { formatScheduledTime, instantToZonedLocalTime, scheduledLocalDate, zonedLocalTimeToInstant } from './scheduleTime'

// The correctness-critical case this whole module exists for: the SAME instant must render
// differently depending on the session's own intended timezone, never the viewer's browser zone.
describe('formatScheduledTime', () => {
  it('renders a different wall-clock time for the same instant in two different timezones', () => {
    const instant = '2026-01-15T23:30:00Z'
    const auckland = formatScheduledTime(instant, 'Pacific/Auckland')
    const losAngeles = formatScheduledTime(instant, 'America/Los_Angeles')
    expect(auckland).not.toBe(losAngeles)
  })
})

describe('scheduledLocalDate', () => {
  it('can land on a different calendar date depending on the intended timezone', () => {
    // 23:30 UTC on Jan 15 is already Jan 16 in Auckland (UTC+13 in southern-hemisphere summer) but
    // still Jan 15 in Los Angeles (UTC-8) — exactly the "near midnight" case this column exists for.
    const instant = '2026-01-15T23:30:00Z'
    expect(scheduledLocalDate(instant, 'Pacific/Auckland')).toBe('2026-01-16')
    expect(scheduledLocalDate(instant, 'America/Los_Angeles')).toBe('2026-01-15')
  })
})

describe('zonedLocalTimeToInstant', () => {
  it('converts a local wall-clock time to the correct UTC instant for a known summer offset', () => {
    // Stockholm is UTC+2 (CEST) in June.
    expect(zonedLocalTimeToInstant('2026-06-15T18:30', 'Europe/Stockholm')).toBe('2026-06-15T16:30:00.000Z')
  })

  it('converts correctly for a known winter offset too (no DST)', () => {
    // Stockholm is UTC+1 (CET) in January.
    expect(zonedLocalTimeToInstant('2026-01-15T18:30', 'Europe/Stockholm')).toBe('2026-01-15T17:30:00.000Z')
  })
})

describe('instantToZonedLocalTime / zonedLocalTimeToInstant round trip', () => {
  it('recovers the exact original local wall-clock value', () => {
    const local = '2026-06-15T18:30'
    const zone = 'Europe/Stockholm'
    const instant = zonedLocalTimeToInstant(local, zone)
    expect(instantToZonedLocalTime(instant, zone)).toBe(local)
  })

  it('round-trips correctly across a different, non-European zone', () => {
    const local = '2026-11-03T07:15'
    const zone = 'America/New_York'
    const instant = zonedLocalTimeToInstant(local, zone)
    expect(instantToZonedLocalTime(instant, zone)).toBe(local)
  })
})
