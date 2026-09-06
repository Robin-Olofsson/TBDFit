-- profiles: TBDFit's product-owned username profile — one row per Supabase Auth user. Unlike
-- local_records, this IS real product data from the moment it exists; do not treat future schema
-- changes to this table as disposable, and do not apply destructive changes to it.
--
-- Identity model (see the profile/username research report for the full comparison):
--   - auth.users.id remains the durable account identity.
--   - Supabase Auth (auth.users) owns credentials/identity/session.
--   - This table owns exactly one thing: the product-facing username.
--
-- Username V1 semantics (deliberately restrictive; Unicode/confusable handling is out of scope
-- for V1, not forgotten):
--   - ASCII letters, digits, underscore, period only — no whitespace anywhere.
--   - 3-30 characters.
--   - Case-insensitive identity: Robin / robin / ROBIN are the same username. `username` preserves
--     the casing the user entered (display); `normalized_username` is the database-derived,
--     database-authoritative lowercase form used for uniqueness. The Android client mirrors this
--     same character/length rule for UX only (see EmailAuthScreen.kt / profile package's
--     usernameValidationError) — this migration remains the actual authority regardless of what
--     the client allows through.
--   - normalized_username is a GENERATED ALWAYS ... STORED column (supported since PostgreSQL 12;
--     Supabase projects run well past that version) specifically so the client is never trusted to
--     supply the authoritative normalized form itself.
--   - No citext: a plain generated column + explicit unique constraint needs no extra Postgres
--     extension, which keeps this fully portable to a future non-Supabase Postgres deployment
--     (ADR-004) with zero dependency risk. citext's availability on hosted Supabase could not be
--     confirmed as reliable, and isn't needed here regardless.
--
-- Security invariant enforced below (mirrors local_records' established pattern exactly):
--   - unauthenticated/public requests must not expose profiles, including username existence —
--     there is deliberately no anon/public SELECT policy of any kind. A public "does this username
--     exist" check would itself be an enumeration primitive; it does not belong on this table at
--     all. Username-or-email login resolution is a separate, later, security-reviewed capability.
--   - authenticated users may only access their own row (auth.uid() = user_id).
--   - INSERT must not allow an authenticated user to claim another user's ownership, or to insert
--     a syntactically invalid username — both are enforced by the database, not merely by the
--     Android client.
--
-- Concurrency: uniqueness on normalized_username is a plain PostgreSQL UNIQUE constraint. Two
-- concurrent signups both attempting "robin"/"Robin"/"ROBIN" will race at the index; exactly one
-- INSERT commits, the other fails with a unique_violation (SQLSTATE 23505) that the application
-- maps to a safe, user-facing "username no longer available" result — see
-- ProfileGateway.createOwnProfile / UsernameUnavailableException. No distributed locking or
-- availability-check step is required or attempted; the INSERT itself is the race resolution.

create table if not exists public.profiles (
  user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  username text not null
    constraint profiles_username_syntax check (
      username ~ '^[A-Za-z0-9_.]{3,30}$'
    ),
  normalized_username text generated always as (lower(username)) stored,
  created_at timestamptz not null default now(),
  constraint profiles_normalized_username_key unique (normalized_username)
);

grant select, insert, update
on table public.profiles
to authenticated;

revoke all
on table public.profiles
from anon;

alter table public.profiles enable row level security;

create policy "select own profile"
on public.profiles
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "insert own profile"
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "update own profile"
on public.profiles
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- No delete policy: deliberate default-deny, same precedent as local_records — not needed yet,
-- not an oversight.
