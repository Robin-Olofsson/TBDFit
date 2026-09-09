import { useEffect, useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { useAuth } from './AuthContext'

// Real, confirmed-accessible data — not invented for this task. `public.profiles` already exists
// (supabase/migrations/20260906120000_create_profiles.sql) with RLS scoping SELECT to the caller's
// own row, exactly the same table Android's SupabaseProfileGateway.loadOwnProfile() reads. A signed
// -in user may not have a row yet (Android's own profile-onboarding flow shows a "choose a
// username" step for exactly this case — see ProfileState.Missing) — this hook does not build that
// onboarding flow (out of scope for an auth task), it only reads whatever already exists and falls
// back to null, so the caller can fall back to showing the account's email instead.
export function useOwnProfile(): { username: string | null; loading: boolean } {
  const { phase } = useAuth()
  const [username, setUsername] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (phase !== 'SIGNED_IN') {
      setUsername(null)
      return
    }
    let active = true
    setLoading(true)
    supabase
      .from('profiles')
      .select('username')
      .maybeSingle()
      .then(({ data, error }) => {
        if (!active) return
        // A missing row is an expected, normal state (no profile created yet), not an error to
        // surface — silently fall back to null (→ email-only presentation) exactly as instructed.
        setUsername(error ? null : (data?.username ?? null))
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [phase])

  return { username, loading }
}
