import type { QueryClient } from '@tanstack/react-query'

// The hard invariant from the review brief: User A's cached data must never render for User B, and
// a User-A request that resolves late (after B has signed in) must never become B's visible state.
//
// Query-key account-scoping (see queryKeys.ts) is the PRIMARY mechanism — a component reading
// queryKeys.routines.list('user-b') structurally cannot read a cache entry stored under
// queryKeys.routines.list('user-a'), no matter when a stale request resolves, since nothing ever
// subscribes to the old key again once every component has remounted/rerendered under the new
// userId. This guard is the SECONDARY safety net: a full `queryClient.clear()` on any identity
// change (including sign-out, where the "new identity" is null) is memory hygiene, not the only
// thing preventing cross-account visibility — it just means the app doesn't keep a departed
// account's private rows sitting in memory indefinitely.
//
// A full clear (not a scoped `removeQueries`) is the accepted V1 approach per the review brief:
// it's the one operation that cannot accidentally miss a private query, and there is no persistent
// cache to also need clearing (see docs/architecture/web-server-state-cache.md).
//
// Kept as a plain, DOM-free function specifically so the safety-critical behavior is unit-testable
// against a real QueryClient without a component-rendering harness (see queryIdentityGuard.test.ts)
// — this codebase has deliberately avoided adding jsdom/@testing-library for exactly this reason
// elsewhere (see routines.test.ts's own doc comment).
export function createIdentityGuard(queryClient: QueryClient) {
  let lastUserId: string | null = null

  return function syncIdentity(currentUserId: string | null): void {
    if (currentUserId === lastUserId) return
    queryClient.clear()
    lastUserId = currentUserId
  }
}
