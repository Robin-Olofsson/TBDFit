// Centralized, typed query-key factories — the single place every TanStack Query hook in this app
// gets its key from, instead of scattering inline array literals across components (see
// docs/architecture/web-server-state-cache.md's Query Key Convention section).
//
// Every PRIVATE key is scoped by `userId` as its second segment, always. This is the primary
// account-isolation mechanism, not merely a convention: a component reading
// queryKeys.routines.list('user-b') can structurally never read data cached under
// queryKeys.routines.list('user-a') — there is no shared key for two different accounts' private
// data to collide under, regardless of cache-clear timing. See useAuthQuerySync.ts for the
// secondary (memory-hygiene) safety net on top of this.
//
// Exercises are visible to everyone for built-ins but account-scoped for customs (see
// data/routines.ts's listVisibleExercises, which already applies this via RLS) — the query key is
// still scoped by userId because the *result set* differs per account even though part of it is
// shared, exactly like routines/programs.
export const queryKeys = {
  routines: {
    all: (userId: string) => ['routines', userId] as const,
    list: (userId: string) => ['routines', userId, 'list'] as const,
    detail: (userId: string, routineId: string) => ['routines', userId, 'detail', routineId] as const,
  },
  programs: {
    all: (userId: string) => ['programs', userId] as const,
    list: (userId: string) => ['programs', userId, 'list'] as const,
    detail: (userId: string, programId: string) => ['programs', userId, 'detail', programId] as const,
  },
  exercises: {
    list: (userId: string) => ['exercises', userId, 'list'] as const,
  },
  profile: {
    detail: (userId: string) => ['profile', userId, 'detail'] as const,
  },
} as const
