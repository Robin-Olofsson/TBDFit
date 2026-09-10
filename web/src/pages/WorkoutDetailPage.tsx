import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { PROTOTYPE_HISTORY } from '../data/prototypeData'

export default function WorkoutDetailPage() {
  const { entryId } = useParams()
  const navigate = useNavigate()
  const entry = PROTOTYPE_HISTORY.find((e) => e.id === entryId)

  if (!entry) {
    return (
      <div className="page">
        <p>Workout not found.</p>
        <button className="btn-secondary btn-back" onClick={() => navigate('/history')}>
          <ArrowLeft size={16} aria-hidden="true" /> History
        </button>
      </div>
    )
  }

  return (
    <div className="page">
      <button className="btn-link btn-back" onClick={() => navigate('/history')}>
        <ArrowLeft size={16} aria-hidden="true" /> History
      </button>
      <div className="page-header">
        <h1>{entry.title}</h1>
      </div>
      <p className="page-subtitle">{entry.dateLabel}</p>
      <ul className="detail-list">
        {entry.exerciseLines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
    </div>
  )
}
