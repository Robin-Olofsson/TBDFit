import { useAuth } from './AuthContext'
import { useOwnProfile } from './useOwnProfile'

// Web's equivalent of Android's ProfileState sealed interface
// (android/phone/src/main/kotlin/com/tbdfit/phone/profile/ProfileState.kt) — Loading/Missing/
// Complete/Unavailable. A TS discriminated union (tagged by `status`) rather than a 1:1 port of the
// Kotlin sealed-class shape, since that's the idiomatic form for this codebase (see AuthPhase in
// AuthContext.tsx for the same string-literal-union convention).
//
// Unlike Android — where ProfileState.Missing blocks the entire authenticated app behind
// ProfileCompletionScreen — this type is scoped to the Profile *feature* only: ProfilePage.tsx is
// currently its one consumer, deciding locally whether to show ProfileSetupForm or the normal
// profile view. It is deliberately NOT read anywhere in App.tsx — a signed-in user with no
// `profiles` row still gets the full AuthenticatedShell (Home/Plan/Programs work normally); an
// earlier version of this app used this type as a second, app-wide access gate in App.tsx, which
// was a product-behavior mistake (profile creation is an optional, in-app feature, not onboarding)
// and has since been removed. See web/README.md's "Identity model" for the corrected contract, and
// section 18 (ANDROID IMPACT) of that revision's own report for why Android's UX is intentionally
// different and untouched.
//
// Missing carries no `suggestedUsername` the way Android's does: Android's hint comes from a
// signup-time `desired_username` metadata value Android's OWN signup flow still collects. Web no
// longer collects a username at signup at all (see AuthContext.signUp/web/README.md's "Identity
// model") — there is nothing to suggest, and ProfileSetupForm's username field always starts empty.
export type ProfileState =
  | { status: 'LOADING' }
  | { status: 'MISSING' }
  | { status: 'COMPLETE'; username: string; displayName: string }
  | { status: 'UNAVAILABLE' }

interface OwnProfileSnapshot {
  username: string | null
  displayName: string | null
  loading: boolean
  isError: boolean
}

// Pure decision logic, split out from the hook below specifically so it's unit-testable without a
// component-rendering harness (this project deliberately has neither jsdom nor a component-testing
// library — see routines.test.ts's own doc comment), the same reasoning as
// createIdentityGuard.ts's own split. `isError` here means a genuine
// Postgrest failure, never "no row yet" — see useOwnProfile.ts's own doc comment for why its query
// throws on error instead of swallowing it into null — that's what makes MISSING and UNAVAILABLE
// distinguishable at all.
//
// Only meaningful once `phase === 'SIGNED_IN'`: `useProfileState()` below is called unconditionally
// from App.tsx (Rules of Hooks), including during
// RESTORING_SESSION/SIGNED_OUT/AWAITING_CONFIRMATION/AUTH_ERROR — LOADING is returned for all of
// those rather than a state that doesn't apply yet, so a component gating on this value never has to
// also re-check `phase` itself.
export function deriveProfileState(phase: string, snapshot: OwnProfileSnapshot): ProfileState {
  if (phase !== 'SIGNED_IN') return { status: 'LOADING' }
  if (snapshot.loading) return { status: 'LOADING' }
  if (snapshot.isError) return { status: 'UNAVAILABLE' }
  if (snapshot.username && snapshot.displayName) {
    return { status: 'COMPLETE', username: snapshot.username, displayName: snapshot.displayName }
  }
  return { status: 'MISSING' }
}

// Built entirely on the existing useOwnProfile/TanStack Query plumbing — no second data-fetching
// path, no new cache, no client-side state-management library.
export function useProfileState(): ProfileState {
  const { phase } = useAuth()
  const ownProfile = useOwnProfile()
  return deriveProfileState(phase, ownProfile)
}
