import { describe, expect, it } from 'vitest'
import { REST_TIMER_OPTIONS, SET_TYPE_OPTIONS, formatRestTimerLabel, newSetDraft, setTypeLetter } from './plannedExerciseDrafts'

describe('formatRestTimerLabel', () => {
  it('formats a sub-minute value as 00:ss', () => {
    expect(formatRestTimerLabel(5)).toBe('00:05')
  })

  it('formats an exact-minute value as mm:00', () => {
    expect(formatRestTimerLabel(300)).toBe('05:00')
  })

  it('formats a mixed minutes+seconds value', () => {
    expect(formatRestTimerLabel(125)).toBe('02:05')
  })
})

describe('REST_TIMER_OPTIONS', () => {
  it('starts with Off (null seconds)', () => {
    expect(REST_TIMER_OPTIONS[0]).toEqual({ label: 'Off', seconds: null })
  })

  it('has 00:05 as the first timed option', () => {
    expect(REST_TIMER_OPTIONS[1]).toEqual({ label: '00:05', seconds: 5 })
  })

  it('has 05:00 as the last option', () => {
    expect(REST_TIMER_OPTIONS[REST_TIMER_OPTIONS.length - 1]).toEqual({ label: '05:00', seconds: 300 })
  })

  it('steps by exactly 5 seconds throughout', () => {
    for (let i = 2; i < REST_TIMER_OPTIONS.length; i++) {
      expect(REST_TIMER_OPTIONS[i].seconds! - REST_TIMER_OPTIONS[i - 1].seconds!).toBe(5)
    }
  })

  it('has exactly 61 options (Off + 5s..300s in 5s steps)', () => {
    expect(REST_TIMER_OPTIONS).toHaveLength(61)
  })
})

describe('newSetDraft', () => {
  it('defaults setType to NORMAL', () => {
    expect(newSetDraft().setType).toBe('NORMAL')
  })
})

describe('SET_TYPE_OPTIONS', () => {
  it('offers exactly the four expected codes, in the developer-specified order', () => {
    expect(SET_TYPE_OPTIONS.map((o) => o.code)).toEqual(['NORMAL', 'WARMUP', 'FAILURE', 'DROPSET'])
  })

  it('maps each code to a single-character letter for the compact square selector', () => {
    expect(SET_TYPE_OPTIONS.map((o) => o.letter)).toEqual(['N', 'W', 'F', 'D'])
  })
})

describe('setTypeLetter', () => {
  it('returns the matching letter for each known set type', () => {
    expect(setTypeLetter('NORMAL')).toBe('N')
    expect(setTypeLetter('WARMUP')).toBe('W')
    expect(setTypeLetter('FAILURE')).toBe('F')
    expect(setTypeLetter('DROPSET')).toBe('D')
  })
})
