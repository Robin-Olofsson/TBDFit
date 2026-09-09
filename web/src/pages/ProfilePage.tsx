import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { useOwnProfile } from '../auth/useOwnProfile'
import { useProfileState } from '../auth/profileState'
import { queryKeys } from '../queryKeys'
import ProfileSetupForm from '../auth/ProfileSetupForm'

// MIXED — identity card below is REAL (real Supabase session email + real `profiles.display_name`/
// `profiles.username` via useOwnProfile, same table Android reads; real Sign out). Settings rows
// and any future training/social stats remain PROTOTYPE-ONLY — see
// docs/product/frontend-prototype-notes.md.
//
// This is the ONLY place a missing `profiles` row has any UI consequence — see App.tsx's own doc
// comment: authentication alone grants access to the rest of the app, and a signed-in user with no
// profile yet sees Home/Plan/Programs exactly as normal. Here, and only here, `useProfileState()`
// (see profileState.ts) decides between the normal profile view and ProfileSetupForm.
export default function ProfilePage() {
  const { session, signOut } = useAuth()
  const profileState = useProfileState()
  const { username, displayName } = useOwnProfile()
  const queryClient = useQueryClient()
  const email = session?.user.email ?? ''

  if (profileState.status === 'LOADING') {
    return (
      <div className="page">
        <div className="page-header">
          <h1>Profile</h1>
        </div>
        <p className="page-subtitle">Loading…</p>
      </div>
    )
  }

  if (profileState.status === 'UNAVAILABLE') {
    return (
      <div className="page">
        <div className="page-header">
          <h1>Profile</h1>
        </div>
        <p className="form-error" role="alert">
          Couldn't load your profile. Check your connection and try again.
        </p>
        <button
          type="button"
          className="btn-secondary"
          onClick={() => void queryClient.invalidateQueries({ queryKey: queryKeys.profile.detail(session?.user.id ?? '') })}
        >
          Retry
        </button>
      </div>
    )
  }

  if (profileState.status === 'MISSING') {
    return (
      <div className="page">
        <div className="page-header">
          <h1>Profile</h1>
        </div>
        <ProfileSetupForm />
      </div>
    )
  }

  // profileState.status === 'COMPLETE' — display_name is the primary product-facing identity (see
  // supabase/migrations/20260912120000_add_profile_display_name.sql — always populated once a
  // profile row exists, initialized from username at creation); username, then email, are fallbacks
  // for the brief window before this query resolves — a render-time presentation choice only, never
  // written back to the row (see useOwnProfile.ts's own doc comment).
  const label = displayName ?? username ?? email
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
          {label !== email && <div className="profile-email">{email}</div>}
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
