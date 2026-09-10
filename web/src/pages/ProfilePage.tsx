import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { useOwnProfile } from '../auth/useOwnProfile'
import { useProfileState } from '../auth/profileState'
import { useMyProfileSummary } from '../auth/profileSummary'
import EditProfileDialog from '../auth/EditProfileDialog'
import { getInitials } from '../lib/profileDisplay'
import { queryKeys } from '../queryKeys'
import ProfileSetupForm from '../auth/ProfileSetupForm'
import ProfileStatistics from '../components/ProfileStatistics'
import ProfileCalendar from '../components/ProfileCalendar'

// REAL — the Profile header, Statistics, and Calendar below are backed by real Supabase data:
// `profiles`/`get_my_profile_summary()` (identity, bio, Workouts/Followers/Following counts),
// `daily_activity` (Movement), `workouts` (completed Workout History), and `scheduled_sessions`
// (Calendar planning). See docs/development/supabase-setup-and-verification.md's "Profile UI"
// section for the full read/write contract this page consumes. Sign-out lives only in
// `AccountMenu.tsx`'s dropdown now — removed from here to keep this a profile *view*, not an
// account-actions surface.
//
// This is the ONLY place a missing `profiles` row has any UI consequence — see App.tsx's own doc
// comment: authentication alone grants access to the rest of the app, and a signed-in user with no
// profile yet sees Home/Plan/Programs exactly as normal. Here, and only here, `useProfileState()`
// (see profileState.ts) decides between the normal profile view and ProfileSetupForm. This gating
// logic is unchanged by the Profile redesign below — only the COMPLETE branch's content changed.
export default function ProfilePage() {
  const { session } = useAuth()
  const profileState = useProfileState()
  const { username, displayName } = useOwnProfile()
  const { summary } = useMyProfileSummary()
  const queryClient = useQueryClient()
  const [editOpen, setEditOpen] = useState(false)
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
  // profile row exists, initialized from username at creation). username is always shown as the
  // secondary @handle, never more visually dominant than display_name (see index.css's
  // .profile-header-name/.profile-header-username rules).
  const label = displayName ?? username ?? email
  const initial = getInitials(label)
  const bio = summary?.bio ?? null

  return (
    <div className="page page-wide">
      <div className="page-header">
        <h1>Profile</h1>
      </div>

      <div className="profile-header-card">
        <div className="profile-avatar profile-header-avatar">{initial}</div>
        <div className="profile-header-identity">
          <div className="profile-header-top">
            <div>
              <div className="profile-header-name">{displayName ?? label}</div>
              {username && <div className="profile-header-username">@{username}</div>}
            </div>
            <button type="button" className="btn-secondary" onClick={() => setEditOpen(true)}>
              Edit Profile
            </button>
          </div>

          <div className="profile-header-stats">
            <div className="profile-header-stat">
              <span className="profile-header-stat-value">{summary?.workoutCount ?? 0}</span>
              <span className="profile-header-stat-label">Workouts</span>
            </div>
            <div className="profile-header-stat">
              <span className="profile-header-stat-value">{summary?.followerCount ?? 0}</span>
              <span className="profile-header-stat-label">Followers</span>
            </div>
            <div className="profile-header-stat">
              <span className="profile-header-stat-value">{summary?.followingCount ?? 0}</span>
              <span className="profile-header-stat-label">Following</span>
            </div>
          </div>

          {bio ? (
            <p className="profile-header-bio">{bio}</p>
          ) : (
            <button type="button" className="btn-link profile-header-bio-empty" onClick={() => setEditOpen(true)}>
              Add a bio
            </button>
          )}
        </div>
      </div>

      <div className="profile-main-columns">
        <ProfileStatistics />
        <ProfileCalendar />
      </div>

      {username && (
        <EditProfileDialog
          open={editOpen}
          username={username}
          displayName={displayName ?? ''}
          bio={bio}
          onClose={() => setEditOpen(false)}
        />
      )}
    </div>
  )
}
