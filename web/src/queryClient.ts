import { QueryClient } from '@tanstack/react-query'

// The single, app-lifetime QueryClient — created once here (a module-level singleton, not inside a
// component) so React Router page unmount/remount never recreates it. This is what makes cached
// data survive ordinary SPA navigation (Plan -> Profile -> Plan) instead of every remount starting
// from an empty cache. See docs/architecture/web-server-state-cache.md for the full reasoning
// behind every default below.
//
// staleTime/gcTime are deliberately generous for this app's actual data-change frequency: Routines
// and Programs are hand-edited by one person, not high-churn data, so treating a fetch as "fresh"
// for a few minutes trades a small staleness window for eliminating the "Loading..." flash on every
// route revisit — the whole point of this task. Per-query overrides remain possible later (e.g. a
// future Exercise-catalog query using a much longer staleTime, since built-ins essentially never
// change — see queryKeys.ts/useExercises.ts).
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 2 * 60 * 1000, // 2 minutes: cached data renders immediately; a revisit within this
      // window never refetches at all, and one just past it triggers a silent background refetch
      // (see requirement 5/12 — stale-while-revalidate, never a blocking reload of already-cached
      // content).
      gcTime: 10 * 60 * 1000, // 10 minutes: how long an unobserved query (e.g. a Routine detail you
      // navigated away from) stays in memory before eviction — long enough that "back" a minute
      // later is still instant, short enough that this isn't a de facto permanent cache (there is
      // no persistence to localStorage/IndexedDB at all — see the architecture doc's explicit
      // "no persistent cache" section).
      retry: 1, // one silent retry on transient failure, not infinite — a genuine failure should
      // reach the UI's existing error state, not retry forever against a real problem (e.g. RLS
      // rejecting an unauthorized read, which retrying can never fix).
      refetchOnWindowFocus: true, // the cheapest available "did something change while I was away"
      // signal now that no Phone sync/Realtime exists yet — see requirement 17.
      refetchOnReconnect: true,
    },
  },
})
