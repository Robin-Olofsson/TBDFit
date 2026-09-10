// Pure month/calendar-grid arithmetic for the Profile Calendar — split out for unit testing, same
// convention as workoutStats.ts. Grids are Monday-start, matching the ISO-week convention already
// used for the workout streak.
export interface CalendarDay {
  date: string // YYYY-MM-DD, local calendar date
  inCurrentMonth: boolean
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function toDateString(year: number, month0: number, day: number): string {
  const date = new Date(year, month0, day)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

// A 6x7 grid (always 6 weeks, so month-to-month navigation never resizes the grid) covering the
// given month plus enough leading/trailing adjacent-month days to fill whole Monday-start weeks.
export function getMonthGrid(year: number, month0: number): CalendarDay[][] {
  const firstOfMonth = new Date(year, month0, 1)
  const firstWeekdayMondayZero = (firstOfMonth.getDay() + 6) % 7
  const gridStart = new Date(year, month0, 1 - firstWeekdayMondayZero)

  const weeks: CalendarDay[][] = []
  const cursor = new Date(gridStart)
  for (let week = 0; week < 6; week++) {
    const days: CalendarDay[] = []
    for (let day = 0; day < 7; day++) {
      days.push({
        date: toDateString(cursor.getFullYear(), cursor.getMonth(), cursor.getDate()),
        inCurrentMonth: cursor.getMonth() === month0,
      })
      cursor.setDate(cursor.getDate() + 1)
    }
    weeks.push(days)
  }
  return weeks
}

// The instant range spanning the ENTIRE visible grid (including adjacent-month lead/trail days),
// so a scheduled session or completed Workout on a visible-but-different-month cell still loads in
// one query — local midnight of the first visible day through local end-of-day of the last.
export function monthGridInstantRange(year: number, month0: number): { startInstant: string; endInstant: string } {
  const grid = getMonthGrid(year, month0)
  const [fy, fm, fd] = grid[0][0].date.split('-').map(Number)
  const [ly, lm, ld] = grid[grid.length - 1][6].date.split('-').map(Number)
  const start = new Date(fy, fm - 1, fd, 0, 0, 0, 0)
  const end = new Date(ly, lm - 1, ld, 23, 59, 59, 999)
  return { startInstant: start.toISOString(), endInstant: end.toISOString() }
}

export function monthLabel(year: number, month0: number): string {
  return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(year, month0, 1))
}

export function addMonths(year: number, month0: number, delta: number): { year: number; month0: number } {
  const d = new Date(year, month0 + delta, 1)
  return { year: d.getFullYear(), month0: d.getMonth() }
}

export function todayDateString(today: Date = new Date()): string {
  return toDateString(today.getFullYear(), today.getMonth(), today.getDate())
}
