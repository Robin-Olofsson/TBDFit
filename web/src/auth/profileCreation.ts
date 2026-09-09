import { supabase } from '../lib/supabaseClient'

// Postgres SQLSTATE for unique_violation. `profiles` has two independent unique constraints (see
// supabase/migrations/20260906120000_create_profiles.sql): `profiles_pkey` (user_id) cannot fire
// here — this is always the FIRST insert attempt for this identity, made explicitly by the signed-in
// user themselves from ProfileSetupForm, never retried silently in the background the way the
// abandoned auto-bootstrap design did — so the only realistic 23505 is
// `profiles_normalized_username_key`: someone else already holds this username.
const UNIQUE_VIOLATION_CODE = '23505'

// Thrown specifically for the username-uniqueness conflict, so the caller (ProfileSetupForm) can
// show a precise, recoverable inline error rather than a generic failure — mirrors Android's
// UsernameUnavailableException (see android/phone/.../profile/ProfileGateway.kt) for the same
// product-level reason: the constraint name never leaks to the user, only "already taken."
export class UsernameUnavailableError extends Error {
  constructor() {
    super('That username is already taken.')
  }
}

// Pure detection of "this 23505 is specifically the username-uniqueness conflict" — split out from
// createOwnProfile below so it's unit-testable without mocking the Supabase client, the same
// pure-logic-split convention this codebase already uses throughout `auth/` (see
// usernameValidation.ts, profileState.ts's deriveProfileState). Postgres reports the violated
// constraint's name in the duplicate-key error's message/details — the same signal the abandoned
// auto-bootstrap design used to distinguish this case (see git history of useProfileBootstrap.ts),
// reused here at the boundary where it actually belongs: the profile-creation call itself, not a
// background retry.
export function isUsernameConflict(error: { code?: string; message?: string; details?: string | null }): boolean {
  if (error.code !== UNIQUE_VIOLATION_CODE) return false
  return Boolean(error.message?.includes('profiles_normalized_username_key') || error.details?.includes('normalized_username'))
}

// The explicit, user-initiated TBDFit profile creation call — the ONLY place `profiles` is ever
// inserted into from Web. Deliberately not automatic and not tied to auth confirmation (see
// web/README.md's "Identity model"): this runs only when the signed-in user presses Save on
// ProfileSetupForm. Relies on the existing `insert own profile` RLS policy
// (`(select auth.uid()) = user_id`) — no service-role key, no SECURITY DEFINER function, no trigger.
export async function createOwnProfile(username: string, displayName: string): Promise<{ username: string; displayName: string }> {
  const { data, error } = await supabase
    .from('profiles')
    .insert({ username, display_name: displayName })
    .select('username, display_name')
    .single()

  if (error) {
    if (isUsernameConflict(error)) throw new UsernameUnavailableError()
    throw error
  }

  return { username: data.username, displayName: data.display_name }
}
