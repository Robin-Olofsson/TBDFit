import type { AuthPhase } from './AuthContext'

// Pure predicates extracted specifically so the authenticated-boundary logic used in App.tsx is
// unit-testable without mounting React or mocking the Supabase client — see authPhase.test.ts for
// the exact assertions the review brief asked for (signed-out → no private shell; signed-in →
// shell; auth failure → no private shell). "Reload restores the session" and "logout removes the
// shell" are consequences of the SAME predicates applied to whatever phase AuthContext transitions
// to — verified by code inspection of AuthContext's persistSession/signOut handling, not re-tested
// here as separate assertions.
export function isAuthenticatedPhase(phase: AuthPhase): boolean {
  return phase === 'SIGNED_IN'
}

export function requiresAuthScreen(phase: AuthPhase): boolean {
  return phase === 'SIGNED_OUT' || phase === 'AWAITING_CONFIRMATION'
}
