import { useNavigate } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { PROTOTYPE_HISTORY, PROTOTYPE_PROGRESS_STATS } from '../data/prototypeData'
import StatCard from '../components/StatCard'

// PROTOTYPE-ONLY — History (Journey W2). The real blocker for this journey isn't web navigation at
// all: no backend replication of phone-originated Workout data exists yet, and no Workout Summary/
// completion screen exists on Phone to produce reviewable history in the first place (see
// web-information-architecture.md Journey W2's own note). Representative sample content stands in.
//
// The small Progress panel at the top is folded into History rather than given its own top-level
// nav item — the corrected Web IA's nav map does not list Progress as a destination (unlike the
// earlier, incorrect version, which sourced a Progress/analytics emphasis primarily from
// supplementary-tier TrainingPeaks/TrainHeroic evidence). Layout/IA only, no real analytics.
export default function HistoryPage() {
  const navigate = useNavigate()

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>History</h1>
          <p className="page-subtitle">Your training history.</p>
        </div>
      </div>

      <div className="stat-row">
        {PROTOTYPE_PROGRESS_STATS.map((stat) => (
          <StatCard key={stat.label} label={stat.label} value={stat.value} sublabel={stat.sublabel} />
        ))}
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th>Workout</th>
            <th>Summary</th>
            <th>Date</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {PROTOTYPE_HISTORY.map((entry) => (
            <tr key={entry.id} className="data-row" onClick={() => navigate(`/history/${entry.id}`)}>
              <td>{entry.title}</td>
              <td>{entry.summary}</td>
              <td>{entry.dateLabel}</td>
              <td className="data-row-action">
                Open <ChevronRight size={14} aria-hidden="true" />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
