-- follows: the minimal one-way social graph edge. No blocking, no private accounts, no follow
-- requests, no denormalized counters — see docs/development/supabase-setup-and-verification.md's
-- Profile Core + Social Graph section for the full V1 boundary. Follower/following counts are
-- always derived from this table at read time (see get_my_profile_summary() in
-- 20260918140000_create_profile_summary.sql), never stored redundantly here or on `profiles` — a
-- stored counter is one more place for truth to drift from the actual edge set, with no benefit at
-- this table's expected size.
--
-- Identity: both sides reference auth.users.id directly, NOT public.profiles.user_id. A follow edge
-- is an account-level relationship, and — per the existing accepted invariant (see
-- create_profiles.sql / web/README.md's Identity model) — an authenticated account may legitimately
-- have no `profiles` row at all. Requiring a `profiles` row to be followable (or to follow someone)
-- would silently introduce a new profile-completeness gate this project has explicitly rejected
-- elsewhere; referencing auth.users.id keeps this table correct for that state.
--
-- The composite primary key (follower_id, following_id) is the single mechanism enforcing "one user
-- cannot follow the same user twice" — a second identical INSERT fails with a plain 23505
-- unique_violation, no separate uniqueness constraint or pre-check needed. Its leading column
-- (follower_id) also directly serves "accounts followed by user X" lookups/counts; the additional
-- index below on following_id exists specifically for the other direction ("followers of user X"),
-- which the primary key alone does not serve efficiently. `on delete cascade` on both foreign keys
-- means deleting an auth.users row (account deletion) removes every follow edge that account was a
-- part of, on either side, automatically — no trigger or application-level cleanup step required.
create table if not exists public.follows (
  follower_id uuid not null references auth.users(id) on delete cascade,
  following_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint follows_pkey primary key (follower_id, following_id),
  -- Single source of truth for "cannot follow yourself" — deliberately not duplicated as a
  -- redundant RLS check; see the insert policy below.
  constraint follows_no_self_follow check (follower_id <> following_id)
);

create index if not exists follows_following_id_idx on public.follows (following_id);

-- No UPDATE grant/policy: an edge is created or removed, never modified in place — there is no
-- meaningful "change" to a follow relationship short of unfollow-then-refollow.
grant select, insert, delete
on table public.follows
to authenticated;

revoke all
on table public.follows
from anon;

alter table public.follows enable row level security;

-- FOLLOW READ PRIVACY: deliberately NOT a broad "authenticated can select all follows" policy —
-- that would hand every signed-in account the entire social graph, including edges neither party to
-- this session is involved in. Instead this scopes SELECT to rows the caller is personally a party
-- to: their own following list and their own followers. This is the same per-row-ownership pattern
-- every other RLS-protected table in this schema already uses (profiles, routines, workouts, ...),
-- it keeps the privileged surface no larger than a single OR'd auth.uid() comparison (smaller than
-- introducing a counts-only SECURITY DEFINER function would require), and it already supports a
-- future followers/following *list* UI for one's own account with zero further schema or policy
-- change — while still giving get_my_profile_summary() (SECURITY INVOKER) everything it needs to
-- compute the caller's own follower/following counts. It does NOT expose a third party's followers
-- or following list to anyone but that party — that remains a deliberately unsolved cross-account
-- read boundary (see the profile-summary migration's header comment and this slice's own product
-- decision notes).
create policy "select own follow edges"
on public.follows
for select
to authenticated
using ((select auth.uid()) = follower_id or (select auth.uid()) = following_id);

-- FOLLOW WRITE CONTRACT: no follow()/unfollow() RPC — a plain RLS-protected INSERT/DELETE is the
-- full, sufficient contract here. There is exactly one row being written per call, no cross-table
-- atomicity requirement (unlike record_completed_workout's multi-table aggregate), and this project
-- already prefers SECURITY INVOKER + RLS over a DEFINER function whenever RLS alone expresses the
-- rule correctly (see record_completed_workout's own header comment on when DEFINER is actually
-- warranted). follower_id must equal the caller's own auth.uid() — the client can never assert
-- `follower_id = <someone else>` and have it accepted, so it is structurally impossible to create a
-- follow "on behalf of" another account.
create policy "insert own follow edges"
on public.follows
for insert
to authenticated
with check ((select auth.uid()) = follower_id);

-- A caller may only ever delete edges where THEY are the follower — i.e. unfollow, never force
-- another account to unfollow someone, and never remove an edge they are merely the target of.
create policy "delete own follow edges"
on public.follows
for delete
to authenticated
using ((select auth.uid()) = follower_id);
