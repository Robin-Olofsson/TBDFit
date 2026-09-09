import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from './AuthContext'
import { queryKeys } from '../queryKeys'
import { createOwnProfile, UsernameUnavailableError } from './profileCreation'
import { USERNAME_MAX_LENGTH, usernameValidationError } from './usernameValidation'
import { DISPLAY_NAME_MAX_LENGTH, displayNameValidationError } from './displayNameValidation'

// Shown inline inside /profile (see ProfilePage.tsx) when the signed-in user has no `profiles` row
// yet. This is a normal, optional Profile-area feature, NOT an app-wide onboarding gate — a user
// can use Home/Plan/Programs freely with no profile at all; only /profile itself cares whether one
// exists. See web/README.md's "Identity model" for why account creation (email+password, handled by
// AuthForm.tsx/AuthContext.tsx) and TBDFit profile creation (username+display name, here) are
// deliberately separate lifecycle steps, and why this component is embedded content rather than a
// full-screen interstitial (an earlier version of this app rendered this as a blocking top-level
// screen from App.tsx — that was a product-behavior mistake, since reverted).
export default function ProfileSetupForm() {
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  // Display Name mirrors Username as the user types, UNTIL they manually edit Display Name — from
  // that point on, username edits must never overwrite whatever they typed there. A ref (not state)
  // for the "has the user touched Display Name" flag would also work, but a boolean state is simpler
  // here and re-renders are irrelevant to correctness at this scale.
  const [displayNameTouched, setDisplayNameTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: () => createOwnProfile(username.trim(), displayName.trim()),
    onSuccess: () => {
      // Flips ProfilePage's profile query from "no row" to "row exists" — it re-renders into the
      // normal profile view automatically once this resolves; no manual navigation/callback needed.
      void queryClient.invalidateQueries({ queryKey: queryKeys.profile.detail(userId) })
    },
    onError: (e) => {
      setError(e instanceof UsernameUnavailableError ? e.message : 'Something went wrong. Please try again.')
    },
  })

  function handleUsernameChange(value: string) {
    setUsername(value)
    if (!displayNameTouched) setDisplayName(value)
  }

  function handleDisplayNameChange(value: string) {
    setDisplayNameTouched(true)
    setDisplayName(value)
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (createMutation.isPending) return

    // Client-side mirrors of the database's actual constraints (profiles_username_syntax /
    // profiles_display_name_not_blank+length) — the database remains the real authority regardless;
    // see usernameValidation.ts/displayNameValidation.ts's own doc comments.
    const usernameError = usernameValidationError(username)
    if (usernameError) {
      setError(usernameError)
      return
    }
    const displayNameError = displayNameValidationError(displayName)
    if (displayNameError) {
      setError(displayNameError)
      return
    }

    setError(null)
    createMutation.mutate()
  }

  return (
    <div className="auth-card">
      <h2 className="auth-title">Set up your profile</h2>
      <p className="page-subtitle">Choose a username and a display name to finish setting up your account.</p>
      <form className="login-form" onSubmit={handleSubmit} noValidate>
        <label className="auth-label" htmlFor="profile-setup-username">
          Username
        </label>
        <input
          id="profile-setup-username"
          className="auth-input"
          type="text"
          autoComplete="username"
          placeholder="robin_92"
          required
          maxLength={USERNAME_MAX_LENGTH}
          value={username}
          onChange={(e) => handleUsernameChange(e.target.value)}
          disabled={createMutation.isPending}
          autoFocus
        />
        <label className="auth-label" htmlFor="profile-setup-display-name">
          Display name
        </label>
        <input
          id="profile-setup-display-name"
          className="auth-input"
          type="text"
          autoComplete="name"
          placeholder="Robin Olofsson"
          required
          maxLength={DISPLAY_NAME_MAX_LENGTH}
          value={displayName}
          onChange={(e) => handleDisplayNameChange(e.target.value)}
          disabled={createMutation.isPending}
        />
        {error && (
          <p className="auth-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn-primary login-submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? 'Please wait…' : 'Save'}
        </button>
      </form>
    </div>
  )
}
