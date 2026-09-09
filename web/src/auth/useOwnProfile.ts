import { useQuery } from '@tanstack/react-query'
import { supabase } from '../lib/supabaseClient'
import { useAuth } from './AuthContext'
import { queryKeys } from '../queryKeys'

// Real, confirmed-accessible data — not invented for this task. `public.profiles` already exists
// (supabase/migrations/20260906120000_create_profiles.sql) with RLS scoping SELECT to the caller's
// own row, exactly the same table Android's SupabaseProfileGateway.loadOwnProfile() reads. A signed
// -in user may have no row at all: TBDFit profile creation is a separate, explicit, post-login step
// (see profileState.ts / profileCreation.ts / ProfileSetupForm) — account creation (email+password)
// never creates a profiles row itself. Once a row exists, both `username` and `display_name` are
// always populated (NOT NULL — see supabase/migrations/20260912120000_add_profile_display_name.sql):
// a row with one field present and the other missing is not a state this schema allows.
//
// Now TanStack Query-backed (see docs/architecture/web-server-state-cache.md). `enabled` gates the
// query so it never fires before a real session/user id exists, and the query key is scoped by that
// user id so a signed-in-again account never renders a stale profile cached under a different id
// (see queryIdentityGuard.ts for the identity-transition cache clear on top of this key-scoping).
//
// The queryFn deliberately THROWS on a real Postgrest error (network/RLS/backend failure) rather
// than swallowing it into `null` — `.maybeSingle()` already returns `{data: null, error: null}` for
// the ordinary "no row yet" case, so a non-null `error` here is always a genuine failure. This
// distinction is what lets profileState.ts's `useProfileState()` tell "no profile yet" (MISSING)
// apart from "couldn't find out" (UNAVAILABLE) — see that file. Callers that only want a
// presentation value (HomePage, ProfilePage, the account-menu initial in App.tsx) can keep ignoring
// `isError` exactly as they ignore any other query failure elsewhere in this app: `data` stays
// `undefined`/fields stay `null`, so they degrade to their existing email fallback.
//
// displayName is public.profiles.display_name verbatim — never a fallback value written back to
// the row. It starts out equal to username (both set together at profile-creation time — see
// profileCreation.ts) and is independently editable later; any presentation fallback (e.g. username,
// then email) is the caller's decision, made at render time.
export function useOwnProfile(): { username: string | null; displayName: string | null; loading: boolean; isError: boolean } {
  const { phase, session } = useAuth()
  const userId = session?.user.id

  const query = useQuery({
    queryKey: queryKeys.profile.detail(userId ?? 'unknown'),
    queryFn: async () => {
      const { data, error } = await supabase.from('profiles').select('username, display_name').maybeSingle()
      if (error) throw error
      return data ? { username: data.username, displayName: data.display_name } : null
    },
    enabled: phase === 'SIGNED_IN' && !!userId,
  })

  return {
    username: query.data?.username ?? null,
    displayName: query.data?.displayName ?? null,
    loading: query.isLoading,
    isError: query.isError,
  }
}
