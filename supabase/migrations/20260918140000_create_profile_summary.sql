-- get_my_profile_summary(): a single narrow read contract for the currently authenticated user's
-- own Profile header — identity, bio, and the three header counts (Workouts/Followers/Following) —
-- in one round trip instead of forcing the Web client into N separate queries for values that always
-- render together. See docs/development/supabase-setup-and-verification.md's Profile Core + Social
-- Graph section for the full contract writeup.
--
-- SECURITY INVOKER, not DEFINER — deliberately the opposite choice from record_completed_workout.
-- Every read this function performs is already something the calling user is independently allowed
-- to do under existing RLS: their own `profiles` row (select-own-profile policy), their own
-- `workouts` rows (select-own-workouts policy), and their own `follows` edges on either side
-- (select-own-follow-edges policy, 20260918130000_create_follows.sql). There is no privilege gap to
-- bridge and no multi-table write atomicity concern (this function performs zero writes) — the two
-- reasons record_completed_workout needed DEFINER. Running this as INVOKER means Postgres enforces
-- every one of those RLS checks exactly as if the caller had issued the underlying SELECTs directly;
-- there is no privileged code path here for a bug to accidentally widen.
--
-- Even so, this repository does not leave a newly created function's default PUBLIC execute grant in
-- place regardless of DEFINER/INVOKER — see the explicit revoke/grant below, matching
-- record_completed_workout's own precedent. The explicit `auth.uid() is null` check is likewise
-- kept for defense-in-depth/stylistic consistency with that function, even though anon already has no
-- EXECUTE grant on this function at all and could not reach this code path regardless.
--
-- `search_path` is still fixed (`public, pg_temp`) and every reference is schema-qualified — not
-- because INVOKER needs it for privilege-escalation safety the way DEFINER does (an invoker function
-- runs with the caller's own privileges either way), but so a session-level search_path change can
-- never redirect this function to an unexpected relation of the same short name.
--
-- MISSING PROFILE stays a valid, non-error result: `hasProfile: false` with null
-- username/displayName/bio, but still-correct (possibly zero) counts. This works because the counts
-- key off auth.users.id directly via workouts.owner_id / follows.follower_id / follows.following_id
-- — never public.profiles.user_id — so an authenticated account with no `profiles` row yet still gets
-- a fully coherent summary instead of a join failure or a thrown error. This is the same "account
-- exists without a profile row" invariant already established for /profile (profileState.ts) applied
-- to this new read contract; the LEFT JOIN below is what preserves it.
create or replace function public.get_my_profile_summary()
returns jsonb
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_uid uuid := auth.uid();
  v_result jsonb;
begin
  if v_uid is null then
    raise exception 'not authenticated';
  end if;

  select jsonb_build_object(
    'hasProfile', p.user_id is not null,
    'username', p.username,
    'displayName', p.display_name,
    'bio', p.bio,
    'workoutCount', (select count(*) from public.workouts w where w.owner_id = v_uid),
    'followerCount', (select count(*) from public.follows f where f.following_id = v_uid),
    'followingCount', (select count(*) from public.follows f where f.follower_id = v_uid)
  )
  into v_result
  from (select v_uid as uid) u
  left join public.profiles p on p.user_id = u.uid;

  return v_result;
end;
$$;

revoke all on function public.get_my_profile_summary() from public;
revoke all on function public.get_my_profile_summary() from anon;
grant execute on function public.get_my_profile_summary() to authenticated;
