import { Link } from 'react-router-dom'

// Compact list row for Home's "Recent Activity" — reuses PROTOTYPE_HISTORY, not a second data set.
export interface ActivityRowProps {
  title: string
  meta: string
  to: string
}

export default function ActivityRow({ title, meta, to }: ActivityRowProps) {
  return (
    <Link to={to} className="activity-row">
      <span className="activity-row-title">{title}</span>
      <span className="activity-row-meta">{meta}</span>
    </Link>
  )
}
