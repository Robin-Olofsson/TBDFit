import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { queryKeys } from './queryKeys'

// Verifies the cache-layer half of the snapshot invariant documented in
// docs/architecture/web-server-state-cache.md's Mutation Reconciliation table: a Routine
// delete/edit must reconcile the Routine's own cache entries and never touch any Program's cached
// aggregate, even one containing a session that was originally copied from that Routine. Exercises
// the real QueryClient API with the real key structure from queryKeys.ts (no component-rendering
// harness — see queryIdentityGuard.test.ts's own doc comment for why this codebase keeps such tests
// DOM-free).

const USER = 'user-a'

describe('Routine mutation cache reconciliation', () => {
  it('deleting a Routine removes its own cache entries but leaves an unrelated Program aggregate untouched', () => {
    const client = new QueryClient()
    const routineId = 'routine-1'
    const programId = 'program-1'

    client.setQueryData(queryKeys.routines.list(USER), [{ id: routineId, name: 'Push A' }])
    client.setQueryData(queryKeys.routines.detail(USER, routineId), { id: routineId, name: 'Push A', exercises: [] })
    const programBeforeDelete = {
      id: programId,
      name: '12 Week Strength',
      weeks: [{ id: 'week-1', sessions: [{ id: 'session-1', sourceRoutineId: routineId, name: 'Push A' }] }],
    }
    client.setQueryData(queryKeys.programs.detail(USER, programId), programBeforeDelete)

    // The exact pattern PlanPage.tsx / RoutineDetailPage.tsx use on a successful delete mutation.
    client.removeQueries({ queryKey: queryKeys.routines.detail(USER, routineId) })
    client.setQueryData(queryKeys.routines.list(USER), (prev: { id: string }[] | undefined) => prev?.filter((r) => r.id !== routineId))

    expect(client.getQueryData(queryKeys.routines.detail(USER, routineId))).toBeUndefined()
    expect(client.getQueryData(queryKeys.routines.list(USER))).toEqual([])
    // The Program's cached aggregate — including the session that still carries sourceRoutineId
    // pointing at the now-deleted Routine — is byte-for-byte untouched by the Routine's own
    // reconciliation. The snapshot already happened in Postgres; the cache must not "helpfully"
    // re-couple the two.
    expect(client.getQueryData(queryKeys.programs.detail(USER, programId))).toEqual(programBeforeDelete)
  })

  it('editing/invalidating a Routine detail query does not affect a Program detail query for a different id', () => {
    const client = new QueryClient()
    const routineId = 'routine-1'
    const programId = 'program-1'
    client.setQueryData(queryKeys.routines.detail(USER, routineId), { id: routineId, name: 'Push A (old)' })
    client.setQueryData(queryKeys.programs.detail(USER, programId), { id: programId, name: 'Untouched Program' })

    void client.invalidateQueries({ queryKey: queryKeys.routines.detail(USER, routineId) })

    // Invalidation marks the Routine query stale (triggering a background refetch for any active
    // observer) but never removes or mutates a differently-keyed Program query's data.
    expect(client.getQueryData(queryKeys.programs.detail(USER, programId))).toEqual({ id: programId, name: 'Untouched Program' })
  })
})
