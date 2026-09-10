import { useQuery } from '@tanstack/react-query'
import { supabase } from '../lib/supabaseClient'
import { useAuth } from './AuthContext'
import { queryKeys } from '../queryKeys'

// The Web-side shape of get_my_profile_summary()'s returned jsonb
// (supabase/migrations/20260918140000_create_profile_summary.sql). `hasProfile: false` + null
// identity fields is the valid "signed-in account, no `profiles` row yet" state — see that
// migration's own header comment — never an error. Counts are always real numbers (never null),
// including for an account with no profile row: they key off auth.uid() directly via
// workouts.owner_id / follows.follower_id / follows.following_id, not profiles.user_id.
export interface ProfileSummary {
  hasProfile: boolean
  username: string | null
  displayName: string | null
  bio: string | null
  workoutCount: number
  followerCount: number
  followingCount: number
}

// Raw RPC shape as returned by Postgres (jsonb keys exactly as the migration builds them) before
// being handed back as ProfileSummary — kept as a separate internal type only so a shape mismatch
// between the migration and this file is a type error here, not a silent `any`.
interface ProfileSummaryRow {
  hasProfile: boolean
  username: string | null
  displayName: string | null
  bio: string | null
  workoutCount: number
  followerCount: number
  followingCount: number
}

// The one place Web calls get_my_profile_summary(). No arguments — the RPC derives the caller
// exclusively from auth.uid() server-side, so there is nothing here for a caller to forge (see the
// migration's own "cross-account forgery" note: the function takes zero parameters).
export async function getMyProfileSummary(): Promise<ProfileSummary> {
  const { data, error } = await supabase.rpc('get_my_profile_summary')
  if (error) throw error
  const row = data as ProfileSummaryRow
  return {
    hasProfile: row.hasProfile,
    username: row.username,
    displayName: row.displayName,
    bio: row.bio,
    workoutCount: row.workoutCount,
    followerCount: row.followerCount,
    followingCount: row.followingCount,
  }
}

// No consumers yet this slice (see the Web Profile frontend slice, not built here) — provided now,
// following useOwnProfile.ts's exact pattern (throw-on-real-error via TanStack Query, `enabled`
// gated on a real session), so the eventual Profile header can adopt it with zero new plumbing.
// Deliberately NOT merged into useOwnProfile: that hook backs the existing MISSING/COMPLETE profile
// gate (profileState.ts) and must keep throwing on a bare Postgrest error to preserve that
// distinction; a summary-RPC failure is a separate concern with its own error shape.
export function useMyProfileSummary(): { summary: ProfileSummary | undefined; loading: boolean; isError: boolean } {
  const { phase, session } = useAuth()
  const userId = session?.user.id

  const query = useQuery({
    queryKey: queryKeys.profile.summary(userId ?? 'unknown'),
    queryFn: getMyProfileSummary,
    enabled: phase === 'SIGNED_IN' && !!userId,
  })

  return {
    summary: query.data,
    loading: query.isLoading,
    isError: query.isError,
  }
}
