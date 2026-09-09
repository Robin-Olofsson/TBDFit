import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useAuth } from './AuthContext'
import { createIdentityGuard } from './queryIdentityGuard'

// Wires the pure createIdentityGuard (see its own doc comment for the actual safety reasoning) into
// the real AuthContext/QueryClient. Call once, near the app root (see App.tsx) — not per-page,
// since the guard's `lastUserId` closure state must persist across the whole session, not reset on
// every route change.
//
// Skips syncing while RESTORING_SESSION: at that point `session` is still null pending the initial
// `supabase.auth.getSession()` resolution, and syncing against that transient null would be a
// meaningless clear on an already-empty cache — waiting for a resolved phase avoids that no-op
// noise without changing the guard's actual behavior once a real phase is known.
export function useQueryIdentitySync(): void {
  const queryClient = useQueryClient()
  const { phase, session } = useAuth()
  const guardRef = useRef(createIdentityGuard(queryClient))

  useEffect(() => {
    if (phase === 'RESTORING_SESSION') return
    guardRef.current(session?.user.id ?? null)
  }, [phase, session?.user.id])
}
