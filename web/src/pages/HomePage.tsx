import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useOwnProfile } from '../auth/useOwnProfile'
import { PROTOTYPE_DASHBOARD_STATS, PROTOTYPE_HISTORY, PROTOTYPE_ROUTINES } from '../data/prototypeData'
import StatCard from '../components/StatCard'
import PlanCard from '../components/PlanCard'
import ActivityRow from '../components/ActivityRow'

// MIXED — Home dashboard. Added as a PROTOTYPE HYPOTHESIS per direct human UX feedback (the
// approved /login reference's laptop mockup implied an authenticated dashboard). This is NOT part
// of the accepted web-information-architecture.md nav map, which currently recommends Plan as the
// landing destination — see docs/product/frontend-prototype-notes.md for the explicit real vs.
// prototype breakdown and the note that this deviates from that doc pending human evaluation.
//
// The greeting uses REAL identity (profiles.username via useOwnProfile, falling back to the real
// session email — never invented). Everything else on this page reuses the SAME sample data as
// Plan/History (PROTOTYPE_ROUTINES/PROTOTYPE_HISTORY) rather than a second, disconnected data set —
// see PlanPage.tsx/HistoryPage.tsx for where the same routines/history are shown in full.
export default function HomePage() {
  const { session } = useAuth()
  const { username } = useOwnProfile()
  const navigate = useNavigate()
  const label = username ?? session?.user.email ?? 'there'

  return (
    <div className="page page-wide">
      <div className="home-header">
        <div>
          <h1>
            {timeOfDayGreeting()}, {label}
          </h1>
          <p className="page-subtitle">Consistency builds results.</p>
        </div>
        {/* Navigates to routine selection — deliberately does NOT execute a workout. Whether Web
            ever executes a workout at all remains an open product question (see
            adr/0005-multi-client-responsibility-strategy.md) — this button must not silently
            resolve that question by pretending to start one. */}
        <button type="button" className="btn-primary" onClick={() => navigate('/plan')}>
          Start Workout
        </button>
      </div>

      <p className="prototype-inline-note">
        The stats below are representative prototype values — no analytics backend exists yet.
      </p>

      <div className="stat-row">
        {PROTOTYPE_DASHBOARD_STATS.map((stat) => (
          <StatCard key={stat.label} label={stat.label} value={stat.value} sublabel={stat.sublabel} />
        ))}
      </div>

      <div className="home-columns">
        <section>
          <div className="section-header">
            <h2>Your Routines</h2>
            <Link to="/plan" className="view-all-link">
              View All
            </Link>
          </div>
          <div className="plan-card-list">
            {PROTOTYPE_ROUTINES.map((routine) => (
              <PlanCard
                key={routine.id}
                title={routine.name}
                subtitle={routine.exercises.map((exercise) => exercise.name).join(' · ')}
                to={`/plan/${routine.id}`}
              />
            ))}
          </div>
        </section>

        <section>
          <div className="section-header">
            <h2>Recent Activity</h2>
            <Link to="/history" className="view-all-link">
              View All
            </Link>
          </div>
          <div className="activity-list">
            {PROTOTYPE_HISTORY.map((entry) => (
              <ActivityRow
                key={entry.id}
                title={entry.title}
                meta={`${entry.dateLabel} · ${entry.summary}`}
                to={`/history/${entry.id}`}
              />
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}

function timeOfDayGreeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}
