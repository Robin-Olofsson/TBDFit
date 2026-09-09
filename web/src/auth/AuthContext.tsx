import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from '../lib/supabaseClient'
import { mapAuthError } from './authErrors'

// The Web auth state model required by the review brief: RESTORING_SESSION / SIGNED_OUT /
// AWAITING_CONFIRMATION / SIGNED_IN / AUTH_ERROR. AUTH_ERROR is reserved for a genuinely
// unexpected failure while restoring the initial session (e.g. a network/storage error from
// supabase.auth.getSession() itself) — NOT for an ordinary wrong-password sign-in attempt, which
// is surfaced inline by the caller of signIn()/signUp() via their returned {ok:false, message}
// result instead, mirroring how Android's AuthGateway.signIn returns a Result<Unit> rather than
// mutating a global AuthState for a recoverable form-level failure (see
// SupabaseAuthGateway.kt/EmailAuthScreen.kt). Keeping that distinction is what stops "AUTH_ERROR"
// from becoming a catch-all for every failure.
export type AuthPhase = 'RESTORING_SESSION' | 'SIGNED_OUT' | 'AWAITING_CONFIRMATION' | 'SIGNED_IN' | 'AUTH_ERROR'

export interface AuthActionResult {
  ok: boolean
  message?: string
}

interface AuthContextValue {
  phase: AuthPhase
  session: Session | null
  pendingConfirmationEmail: string | null
  restoreErrorMessage: string | null
  signIn: (email: string, password: string) => Promise<AuthActionResult>
  signUp: (email: string, password: string) => Promise<AuthActionResult>
  signOut: () => Promise<void>
  returnToSignIn: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}

// React Context + local useState is sufficient here — no generic state-management library, per the
// review brief's explicit instruction to keep this small.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [phase, setPhase] = useState<AuthPhase>('RESTORING_SESSION')
  const [session, setSession] = useState<Session | null>(null)
  const [pendingConfirmationEmail, setPendingConfirmationEmail] = useState<string | null>(null)
  const [restoreErrorMessage, setRestoreErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    // Initial restore. With persistSession+detectSessionInUrl both true (see supabaseClient.ts),
    // this both (a) restores a session already in localStorage from a prior visit — so an ordinary
    // page refresh does not spuriously sign the user out — and (b) auto-exchanges a PKCE `code` if
    // one is present in the current URL (the email-confirmation callback case), with no dedicated
    // /auth/callback route required for that exchange itself to happen.
    supabase.auth.getSession().then(({ data, error }) => {
      if (!active) return
      if (error) {
        setRestoreErrorMessage(mapAuthError(error))
        setPhase('AUTH_ERROR')
        return
      }
      if (data.session) {
        setSession(data.session)
        setPhase('SIGNED_IN')
      } else {
        setPhase('SIGNED_OUT')
      }
    })

    const { data: subscription } = supabase.auth.onAuthStateChange((event, newSession) => {
      if (!active) return
      if (newSession) {
        setSession(newSession)
        setPendingConfirmationEmail(null)
        setPhase('SIGNED_IN')
      } else if (event === 'SIGNED_OUT') {
        setSession(null)
        setPhase('SIGNED_OUT')
      }
    })

    return () => {
      active = false
      subscription.subscription.unsubscribe()
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string): Promise<AuthActionResult> => {
    const { error } = await supabase.auth.signInWithPassword({ email, password })
    if (error) return { ok: false, message: mapAuthError(error) }
    // onAuthStateChange's SIGNED_IN branch (fired by this same call) sets phase/session.
    return { ok: true }
  }, [])

  const signUp = useCallback(async (email: string, password: string): Promise<AuthActionResult> => {
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      // Sends the confirmation link back to wherever this app is currently running (dev or a
      // future deployed origin) rather than a hardcoded URL — see web/README.md for the exact
      // origin(s) that must additionally be registered in the Supabase Dashboard's Redirect URLs
      // for this to actually work end-to-end (not done by this change — no dashboard access).
      options: { emailRedirectTo: window.location.origin },
    })
    if (error) return { ok: false, message: mapAuthError(error) }
    // Email confirmation is enabled on the real configured project (see
    // docs/development/supabase-setup-and-verification.md) — a successful signUp call does NOT
    // mean the account is usable yet. supabase-js only returns a session here if confirmation is
    // OFF; when it's required, data.session is null even though data.user exists. Never treat this
    // as SIGNED_IN.
    if (!data.session) {
      setPendingConfirmationEmail(email)
      setPhase('AWAITING_CONFIRMATION')
    }
    return { ok: true }
  }, [])

  const signOut = useCallback(async () => {
    await supabase.auth.signOut()
    // Also set directly (not just relying on the SIGNED_OUT auth-state-change event) so the UI
    // responds immediately even if that event were ever delayed. Affects only this Web session —
    // never touches Android's LocalAccount/Room or deletes any user data.
    setSession(null)
    setPendingConfirmationEmail(null)
    setPhase('SIGNED_OUT')
  }, [])

  const returnToSignIn = useCallback(() => {
    setPendingConfirmationEmail(null)
    setPhase('SIGNED_OUT')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ phase, session, pendingConfirmationEmail, restoreErrorMessage, signIn, signUp, signOut, returnToSignIn }),
    [phase, session, pendingConfirmationEmail, restoreErrorMessage, signIn, signUp, signOut, returnToSignIn],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
