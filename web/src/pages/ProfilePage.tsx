import { useAuth } from '../auth/AuthContext'
import { useOwnProfile } from '../auth/useOwnProfile'

// MIXED — identity card below is REAL (real Supabase session email + real `profiles.username` via
// useOwnProfile, same table Android reads; real Sign out). Settings rows and any future
// training/social stats remain PROTOTYPE-ONLY — see docs/product/frontend-prototype-notes.md.
// (Earlier versions of this page used PROTOTYPE_PROFILE hardcoded sample data — that predated the
// real Web auth implementation and is corrected here; auth/session integration is real now.)
export default function ProfilePage() {
  const { session, signOut } = useAuth()
  const { username } = useOwnProfile()
  const email = session?.user.email ?? ''
  const label = username ?? email
  const initial = (label || '?').charAt(0).toUpperCase()

  return (
    <div className="page">
      <div className="page-header">
        <h1>Profile</h1>
      </div>
      <p className="page-subtitle">Identity and sign-out below are real. Settings are prototype content.</p>

      <div className="profile-card">
        <div className="profile-avatar">{initial}</div>
        <div>
          <div className="profile-username">{label}</div>
          {username && <div className="profile-email">{email}</div>}
        </div>
        <button type="button" className="btn-secondary profile-signout" onClick={() => void signOut()}>
          Sign out
        </button>
      </div>

      <section className="settings-section">
        <h2>Settings</h2>
        <p className="prototype-inline-note">Prototype content — no real settings backend exists yet.</p>
        <div className="settings-row">
          <span>Units</span>
          <span className="settings-value-muted">Metric (kg)</span>
        </div>
        <div className="settings-row">
          <span>Feed / social activity</span>
          <span className="settings-value-muted">Coming soon</span>
        </div>
      </section>
    </div>
  )
}
