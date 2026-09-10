import { describe, expect, it } from 'vitest'
import { addMonths, getMonthGrid, monthGridInstantRange, monthLabel, todayDateString } from './calendarMonth'

describe('getMonthGrid', () => {
  it('always returns 6 Monday-start weeks of 7 days', () => {
    const grid = getMonthGrid(2026, 8) // September 2026 (month0 = 8)
    expect(grid).toHaveLength(6)
    for (const week of grid) expect(week).toHaveLength(7)
  })

  it('marks days outside the requested month as such', () => {
    const grid = getMonthGrid(2026, 8) // September 2026
    const flat = grid.flat()
    expect(flat.some((day) => day.inCurrentMonth)).toBe(true)
    // September 2026 has 30 days; the grid (42 cells) must include at least one lead/trail day.
    expect(flat.some((day) => !day.inCurrentMonth)).toBe(true)
  })

  it('starts every week on a Monday', () => {
    const grid = getMonthGrid(2026, 8)
    for (const week of grid) {
      const [y, m, d] = week[0].date.split('-').map(Number)
      expect(new Date(y, m - 1, d).getDay()).toBe(1) // 1 = Monday
    }
  })

  it('contains every real day of the requested month exactly once', () => {
    const grid = getMonthGrid(2026, 8)
    const daysInMonth = grid.flat().filter((day) => day.inCurrentMonth).map((day) => day.date)
    expect(daysInMonth).toHaveLength(30)
    expect(daysInMonth[0]).toBe('2026-09-01')
    expect(daysInMonth[29]).toBe('2026-09-30')
  })
})

describe('monthGridInstantRange', () => {
  it('spans from before the first visible day to after the last visible day', () => {
    const grid = getMonthGrid(2026, 8)
    const { startInstant, endInstant } = monthGridInstantRange(2026, 8)
    expect(new Date(startInstant).getTime()).toBeLessThan(new Date(`${grid[0][0].date}T12:00:00`).getTime())
    expect(new Date(endInstant).getTime()).toBeGreaterThan(new Date(`${grid[5][6].date}T12:00:00`).getTime())
  })
})

describe('monthLabel', () => {
  it('formats a human-readable month + year', () => {
    expect(monthLabel(2026, 8)).toMatch(/September/)
    expect(monthLabel(2026, 8)).toMatch(/2026/)
  })
})

describe('addMonths', () => {
  it('rolls over into the next year at December', () => {
    expect(addMonths(2026, 11, 1)).toEqual({ year: 2027, month0: 0 })
  })

  it('rolls back into the previous year at January', () => {
    expect(addMonths(2026, 0, -1)).toEqual({ year: 2025, month0: 11 })
  })
})

describe('todayDateString', () => {
  it('formats a given date as YYYY-MM-DD using local components', () => {
    expect(todayDateString(new Date(2026, 8, 5))).toBe('2026-09-05')
  })
})
