import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { createIdentityGuard } from './queryIdentityGuard'
import { queryKeys } from '../queryKeys'

// Real QueryClient, no rendering/DOM involved — see createIdentityGuard's own doc comment for why
// this is deliberately DOM-free. These tests exercise the single most safety-critical behavior in
// this task: an account transition must never leave a prior account's cached data reachable.
//
// Realistic ordering matters here: in production, `syncIdentity` is called the moment a phase/
// session is known (see useQueryIdentitySync.ts), BEFORE any query has had a chance to populate
// data — so the very first call (transitioning from the guard's initial `null`) always clears an
// already-empty cache, harmlessly. Data is only ever seeded into the cache AFTER that first sync,
// once real queries run under a resolved identity. Every test below seeds data in that same order.

describe('createIdentityGuard', () => {
  it('clears the cache when a different account signs in', () => {
    const client = new QueryClient()
    const sync = createIdentityGuard(client)
    sync('user-a') // resolves the initial identity — cache is empty at this point, nothing to clear

    client.setQueryData(queryKeys.routines.list('user-a'), [{ id: 'r1', name: "User A's Routine" }])
    expect(client.getQueryData(queryKeys.routines.list('user-a'))).toBeDefined()

    sync('user-b')

    expect(client.getQueryData(queryKeys.routines.list('user-a'))).toBeUndefined()
  })

  it('clears the cache on sign-out (identity becomes null)', () => {
    const client = new QueryClient()
    const sync = createIdentityGuard(client)
    sync('user-a')

    client.setQueryData(queryKeys.routines.list('user-a'), [{ id: 'r1' }])
    expect(client.getQueryData(queryKeys.routines.list('user-a'))).toBeDefined()

    sync(null)

    expect(client.getQueryData(queryKeys.routines.list('user-a'))).toBeUndefined()
  })

  it('does not clear the cache when the same identity is observed again (e.g. re-render, focus refetch)', () => {
    const client = new QueryClient()
    const sync = createIdentityGuard(client)
    sync('user-a')

    client.setQueryData(queryKeys.routines.list('user-a'), [{ id: 'r1' }])

    sync('user-a')
    sync('user-a')

    expect(client.getQueryData(queryKeys.routines.list('user-a'))).toBeDefined()
  })

  it('key scoping alone (not just clear()) stops a late-resolving User A response from becoming visible under User B', async () => {
    const client = new QueryClient()
    const sync = createIdentityGuard(client)
    sync('user-a')

    let resolveLate: (value: string[]) => void
    const latePromise = new Promise<string[]>((resolve) => {
      resolveLate = resolve
    })
    // Simulates a fetch for User A's list that was already in flight when B signs in.
    const fetchPromise = client.fetchQuery({ queryKey: queryKeys.routines.list('user-a'), queryFn: () => latePromise })

    sync('user-b') // identity transition happens while A's request is still pending

    resolveLate!(["User A's late data"])
    await fetchPromise.catch(() => undefined)

    // The late result can only ever land under user-a's own key, which nothing renders once the
    // app has moved to user-b — and the clear() above already evicted it as soon as it settles into
    // a cache the guard cleared. Confirms both layers: user-b's key was never touched...
    expect(client.getQueryData(queryKeys.routines.list('user-b'))).toBeUndefined()
    // ...and querying under user-a's own key from this point on is a fresh miss, not the stale value.
    expect(client.getQueryData(queryKeys.routines.list('user-a'))).toBeUndefined()
  })
})
