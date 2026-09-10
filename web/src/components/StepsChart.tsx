// A small, hand-rolled inline-SVG bar chart — no charting library added for this. This app has zero
// chart dependencies today and this feature (Steps/day, optionally Workouts/week) doesn't justify
// pulling in recharts/chart.js/d3 for two bar charts; a plain responsive SVG is lighter and fully
// themeable with the existing CSS custom properties. Reused for both the Movement (steps) and
// Workouts (frequency) tabs — genuinely generic, not steps-specific despite the file name.
export interface ChartBar {
  label: string
  // null = no backend row for this point (missing data) — rendered as a distinct muted marker, NEVER
  // a zero-height bar, which would be visually indistinguishable from a real measured zero. See
  // daily_activity's own schema comment: NO ROW != steps = 0.
  value: number | null
}

interface Props {
  bars: ChartBar[]
  valueLabel: string // e.g. "steps" or "workouts" — used in the per-bar tooltip and the a11y summary
  formatValue?: (value: number) => string
}

const CHART_WIDTH = 480
const CHART_HEIGHT = 160
const BAR_GAP_RATIO = 0.3

export default function StepsChart({ bars, valueLabel, formatValue = (v) => String(v) }: Props) {
  const maxValue = Math.max(1, ...bars.map((bar) => bar.value ?? 0))
  const slotWidth = CHART_WIDTH / Math.max(1, bars.length)
  const barWidth = slotWidth * (1 - BAR_GAP_RATIO)

  return (
    <div className="steps-chart">
      <svg
        className="steps-chart-svg"
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
        role="img"
        aria-label={`${valueLabel} over time chart — see the summary below for exact values`}
      >
        <line
          x1={0}
          y1={CHART_HEIGHT - 1}
          x2={CHART_WIDTH}
          y2={CHART_HEIGHT - 1}
          className="steps-chart-axis"
        />
        {bars.map((bar, index) => {
          const slotX = index * slotWidth + (slotWidth - barWidth) / 2
          if (bar.value === null) {
            return (
              <rect
                key={`${bar.label}-${index}`}
                x={slotX}
                y={CHART_HEIGHT - 6}
                width={barWidth}
                height={4}
                className="steps-chart-bar-missing"
              >
                <title>{`${bar.label}: no data`}</title>
              </rect>
            )
          }
          const barHeight = Math.max(2, (bar.value / maxValue) * (CHART_HEIGHT - 8))
          return (
            <rect
              key={`${bar.label}-${index}`}
              x={slotX}
              y={CHART_HEIGHT - barHeight}
              width={barWidth}
              height={barHeight}
              rx={2}
              className="steps-chart-bar"
            >
              <title>{`${bar.label}: ${formatValue(bar.value)} ${valueLabel}`}</title>
            </rect>
          )
        })}
      </svg>
      <div className="steps-chart-labels">
        {bars.map((bar, index) => (
          <span key={`${bar.label}-${index}`} className="steps-chart-label">
            {bar.label}
          </span>
        ))}
      </div>
      {/* Screen-reader-only summary — the chart's critical values must not be visual-only (see
          web/README.md's accessibility conventions and this task's own requirement). */}
      <table className="visually-hidden">
        <caption>{valueLabel} by period</caption>
        <tbody>
          {bars.map((bar, index) => (
            <tr key={`${bar.label}-${index}`}>
              <th scope="row">{bar.label}</th>
              <td>{bar.value === null ? 'No data' : `${formatValue(bar.value)} ${valueLabel}`}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
