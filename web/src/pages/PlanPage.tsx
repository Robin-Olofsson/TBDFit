import { useNavigate } from 'react-router-dom'
import { PROTOTYPE_ROUTINES } from '../data/prototypeData'

// PROTOTYPE-ONLY — Routine Library. No Routine/RoutineExercise backend exists on any client (see
// product-information-architecture.md §5). This is Web's evidenced-primary job per the corrected
// research (Option B, Hevy-sourced) — this is deliberately the landing page for that reason. Route
// stays /plan (internal, not user-visible); the visible heading/nav label is "Routine" per direct
// human UX feedback.
export default function PlanPage() {
  const navigate = useNavigate()

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Routine</h1>
          <p className="page-subtitle">Prototype content — no real Routine model exists yet.</p>
        </div>
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th>Routine</th>
            <th>Exercises</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {PROTOTYPE_ROUTINES.map((routine) => (
            <tr key={routine.id} className="data-row" onClick={() => navigate(`/plan/${routine.id}`)}>
              <td>{routine.name}</td>
              <td>{routine.exercises.map((e) => e.name).join(', ')}</td>
              <td className="data-row-action">Open →</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
