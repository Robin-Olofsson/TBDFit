import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useOwnProfile } from '../auth/useOwnProfile'
import { PROTOTYPE_DASHBOARD_STATS, PROTOTYPE_HISTORY } from '../data/prototypeData'
import { listMyRoutines } from '../data/routines'
import { queryKeys } from '../queryKeys'
import StatCard from '../components/StatCard'
import PlanCard from '../components/PlanCard'
import ActivityRow from '../components/ActivityRow'

// MIXED — Home dashboard. Added as a PROTOTYPE HYPOTHESIS per direct human UX feedback (the
// approved /login reference's laptop mockup implied an authenticated dashboard). This is NOT part
// of the accepted web-information-architecture.md nav map, which currently recommends Plan as the
// landing destination — see docs/product/frontend-prototype-notes.md for the explicit real vs.
// prototype breakdown and the note that this deviates from that doc pending human evaluation.
//
// The greeting uses REAL identity (profiles.display_name — always populated once a profile row
// exists, initialized from username at creation — then profiles.username, via useOwnProfile,
// falling back to the real session email — never invented). "Your Routines" below is
// now REAL data too (see supabase/migrations/20260910120000_create_routines.sql) — a brand-new
// account correctly shows no routines here rather than a fake seeded list. "Recent Activity"
// remains PROTOTYPE_HISTORY — History has no backend yet, out of scope for the Routine vertical
// slice.
export default function HomePage() {
  const { session } = useAuth()
  const { username, displayName } = useOwnProfile()
  const navigate = useNavigate()
  const label = displayName ?? username ?? session?.user.email ?? 'there'
  const userId = session?.user.id ?? ''

  // Deliberately the SAME query key PlanPage.tsx uses — this is one cached list shared by both
  // screens, not a second independent fetch. Visiting Home right after Plan (or vice versa) never
  // re-requests the list while it's still fresh (see docs/architecture/web-server-state-cache.md).
  const { data: routines } = useQuery({
    queryKey: queryKeys.routines.list(userId),
    queryFn: listMyRoutines,
    // Home's dashboard is a convenience surface, not the source of truth for Routines — a fetch
    // failure here degrades to an empty section rather than surfacing an error; PlanPage.tsx is
    // where a real error is shown for the same underlying query.
    throwOnError: false,
  })

  return (
    <div className="page page-wide">
      <div className="home-header">
        <div>
          <h1>
            {timeOfDayGreeting()}, {label}
          </h1>
          <p className="page-subtitle">Consistency builds results.</p>
        </div>
        {/* Navigates to Routine selection — a planning shortcut, not workout execution. Web is
            explicitly NOT a workout-execution client (product decision): it plans, schedules, and
            reads completed history/statistics/calendar; starting/logging/finishing a workout is
            Phone/Watch-only, later. The label must never imply this button starts anything. */}
        <button type="button" className="btn-primary" onClick={() => navigate('/plan')}>
          Browse Routines
        </button>
      </div>

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
            {routines === undefined ? (
              <p className="page-subtitle">Loading…</p>
            ) : routines.length === 0 ? (
              <p className="page-subtitle">No routines yet — create your first one.</p>
            ) : (
              routines.slice(0, 3).map((routine) => (
                <PlanCard
                  key={routine.id}
                  title={routine.name}
                  subtitle={routine.exercises.map((exercise) => exercise.exerciseName).join(' · ') || 'No exercises yet'}
                  to={`/plan/${routine.id}`}
                />
              ))
            )}
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
