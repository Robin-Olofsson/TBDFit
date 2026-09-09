# Web Server-State Cache Architecture

**Status: this describes an implemented mechanism, not a product/architecture decision requiring
team review** — it documents how the Web client's existing real Supabase-backed data (Routines,
Programs, Exercises, Profile) is cached client-side, and the rules any future server-backed Web
feature (History, Workout, Progress) should follow to stay consistent with it.

## Layering

```text
React UI
    ↓
TanStack Query (in-memory client cache)
    ↓
TBDFit data/domain functions (src/data/routines.ts, src/data/programs.ts, src/auth/useOwnProfile.ts)
    ↓
Supabase JS
    ↓
Row Level Security
    ↓
Postgres
```

**Postgres, behind RLS, remains the sole authority.** TanStack Query is a transient, in-memory
performance layer over it — never a second source of truth, never persisted, and never something a
UI screen can trust in place of a real read once its data is stale enough to warrant a refetch.

## Server state vs. local UI state

Only real Supabase reads go through TanStack Query: Routine list/detail, Program list/detail
(aggregate), the Exercise catalog, and the signed-in user's `profiles` row. Everything else stays
plain React `useState`, exactly as before this change:

- form drafts before Save (Routine/Program-session name, exercises, planned sets)
- `selectedWeekId`/`selectedSessionId` in the Program Builder
- picker/dialog open state (`pickerOpen`, `routinePickerOpen`)
- any filter/search input

A form draft is local UI state even while it represents server-backed data mid-edit — it becomes
part of the authoritative cache only at the moment a mutation actually persists it (see Mutation
Reconciliation below). No unsaved draft is ever written into a `useQuery` cache entry.

## Query key convention

`src/queryKeys.ts` is the single place every query key is constructed. Every private-data key's
second segment is the signed-in user's id:

```ts
queryKeys.routines.list(userId)
queryKeys.routines.detail(userId, routineId)
queryKeys.programs.list(userId)
queryKeys.programs.detail(userId, programId)
queryKeys.exercises.list(userId)
queryKeys.profile.detail(userId)
```

This is not just a naming convention — it is the **primary** account-isolation mechanism (see
Account Scoping below). No component ever passes an inline array literal as a query key.

## Account scoping and auth-transition safety

Two layers, deliberately redundant:

1. **Key scoping (primary).** A component rendering under User B's session can only ever read
   `queryKeys.routines.list('user-b')` — there is no shared key for two accounts' private data to
   collide under, so a stale or late-resolving response for User A's key can never become visible
   under User B's key, regardless of timing.
2. **Identity-transition cache clear (secondary, memory hygiene).** `src/auth/queryIdentityGuard.ts`
   exports a pure `createIdentityGuard(queryClient)` function; `src/auth/useQueryIdentitySync.ts`
   wires it to the real `AuthContext`, called once from `App.tsx`. On every observed identity change
   (sign-in, sign-out, or a different account signing in — anything other than the same user id seen
   twice in a row) it calls `queryClient.clear()`. This is a full clear, not a scoped removal — the
   simplest operation that cannot accidentally miss a private query, acceptable because there is no
   persistent cache to also need clearing and refetching everything after an account switch is cheap.

Private queries are additionally gated with `enabled: phase === 'SIGNED_IN' && !!session?.user.id`
so they never fire before an identity is actually resolved.

See `src/auth/queryIdentityGuard.test.ts` for the tests proving both layers hold, including the
specific "a request already in flight for User A resolves after User B has signed in" case.

## Defaults (`src/queryClient.ts`)

```ts
staleTime: 2 minutes   // cached data renders immediately on revisit within this window; no refetch
gcTime: 10 minutes     // how long an unobserved query stays in memory before eviction
retry: 1               // one silent retry, not infinite — RLS rejections/genuine errors must surface
refetchOnWindowFocus: true
refetchOnReconnect: true
```

Chosen for this app's actual data-change frequency (Routines/Programs are hand-edited by one
person, not high-churn) rather than tuned to an exact number — per-query overrides remain possible
(the Exercise catalog uses a longer 30-minute `staleTime`, see below).

## Stale-while-revalidate / loading vs. background fetch

- **No cached data at all** (`isLoading`): show the existing "Loading…" state — this is the only
  case that should block the screen.
- **Cached data exists, background refetch in progress**: keep rendering the cached content. No
  page in this app currently shows a distinct "refreshing" indicator on top of that — none was
  judged necessary yet; adding one later is a small, additive change to any given page, not an
  architectural one.
- **Background refetch fails**: the previous cached data stays visible (TanStack Query does not
  clear `data` on a failed background refetch) — no page has to do anything special for this.
- **Failed mutation**: every mutation surfaces its own error state (`mutation.isError` /
  `mutation.error`) next to the relevant action; nothing in this codebase treats a failed
  create/edit/delete as if it had succeeded.

## Mutation reconciliation

Correctness-first for this pass — no optimistic updates. On success, each mutation does one of two
things, chosen per case:

- **Direct cache write** (`setQueryData`) when the created/returned shape is already fully known
  client-side without a refetch — e.g. `ProgramsListPage`'s create-Program handler seeds both the
  list and the new detail entry directly from what `createProgram` already returned.
- **Targeted invalidate** (`invalidateQueries` on the specific affected key(s), never the whole
  cache) when the RPC's return value is just an id and the real saved shape should come from
  Postgres — e.g. every Routine/Program-session save, every Program week/session
  add/duplicate/delete/copy.

Explicit rules, matching the existing snapshot invariant at the cache layer, not just the database:

| Action | Cache effect |
| --- | --- |
| Routine edit/save | Invalidate that Routine's detail + the Routine list. **Never** touches any Program's cached aggregate, even one containing a session originally copied from this Routine — the snapshot already happened in Postgres; the cache layer must not "helpfully" re-couple them. |
| Routine delete | Remove the Routine from the cached list + detail. Does not touch any Program aggregate that references it via `source_routine_id` (Postgres already handles that column becoming `NULL`; the cache simply reflects whatever the Program aggregate next fetches). |
| Program create | Seed list + new detail directly (see above). |
| Program add/duplicate/delete Week, add/copy-from-Routine/delete Session, save Session | Invalidate that one Program's detail aggregate only (`queryKeys.programs.detail(userId, programId)`) — never the Program list or any other Program's cache. The list's week/session counts go stale until the list is next visited/refetched, which is an acceptable, minor staleness window for this V1 (see Known Limitations in the implementation report). |
| Program delete | Remove from the cached list + detail. |

## Query granularity

- **Routine detail** = one aggregate query per routine id (name + all exercises + all planned
  sets), matching `getRoutine`'s single nested Supabase select.
- **Program detail** = one aggregate query per program id (name + all weeks + all sessions + all
  exercises + all planned sets), matching `getProgram`'s single nested select. Switching between
  weeks/sessions inside the Program Builder reads from this one cached tree — it does not fan out
  into per-week or per-session queries just because the underlying tables are nested.

This mirrors how `save_routine`/`save_program_session` already treat their respective trees as one
atomic unit to write; treating them as one atomic unit to cache and invalidate is the same
reasoning applied to reads.

## Exercise catalog caching

`queryKeys.exercises.list(userId)` uses a much longer `staleTime` (30 minutes) than Routine/Program
data — built-in exercises essentially never change, and this list is reused by both the Routine
editor and every Program session editor via the shared `PlannedExercisesEditor` component, so it is
fetched at most once per 30-minute window regardless of how many editors are opened in that time.
Custom exercises remain correctly account-scoped: the key still includes `userId`, and the
underlying `listVisibleExercises()` call still only ever returns built-ins plus the caller's own
customs (enforced by RLS, unchanged by this task) — caching never widens what a query can see.
Creating a new custom exercise appends it directly into this cache via `setQueryData` rather than
waiting out the 30-minute staleness window.

## Explicitly out of scope

- **No persistent cache.** Nothing is written to `localStorage`/`IndexedDB`/a Service Worker. A full
  browser refresh legitimately refetches everything from Postgres — this is accepted, not a gap.
- **No Supabase Realtime.** `refetchOnWindowFocus`/`refetchOnReconnect` are the only "something may
  have changed elsewhere" signals this pass adds. Realtime's eventual role, if ever added, is
  invalidating a query or waking a background check — it must never become the reconciliation
  protocol itself (see `docs/architecture/cross-client-identity-sync-research.md`'s own distinction
  between Realtime-as-notification and sync-as-reconciliation).
- **No cross-client sync.** This is Web-only; Android/Room is untouched and has no relationship to
  this cache.
- **No Redux/Zustand.** TanStack Query is the only state-management dependency this task adds.

## Rule for future server-backed Web features

History, Workout, and Progress do not have a real backend yet. Whenever one is added, it must follow
this same pattern from day one: a `queryKeys.<feature>.*` entry in `queryKeys.ts`, a small
domain-specific module under `src/data/` (not raw `supabase.from(...)` calls inline in a page
component), `useQuery`/`useMutation` in the page, and an explicit invalidation rule added to the
Mutation Reconciliation table above — not a bespoke `useEffect`/`useState` fetch loop reintroducing
the exact page-remount loading flash this task exists to eliminate.
