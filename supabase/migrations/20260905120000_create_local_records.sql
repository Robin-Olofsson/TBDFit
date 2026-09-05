-- local_records: the disposable technical entity proving the local (Room) -> Supabase sync slice
-- (see ADR-004). This is NOT a workout-domain table — it exists only to prove durable local
-- creation, authenticated sync, and remote idempotency end to end. The real workout domain schema
-- is deliberately deferred (PD-003) and will get its own migration(s) when it's designed.
--
-- Security invariant enforced below:
--   - unauthenticated/public requests must not expose local_records
--   - authenticated users may only access rows where user_id = auth.uid()
--   - INSERT must not allow an authenticated user to claim another user's ownership
--
-- Table-level GRANTs and RLS policies are separate mechanisms: scoping these policies
-- `to authenticated` does not itself revoke the `anon` role's table grants — it means RLS has no
-- applicable policy for `anon` on this table, so RLS's default-deny is what actually blocks it.

create table if not exists public.local_records (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at bigint not null,
  value text not null
);

create index if not exists local_records_user_id_idx
on public.local_records (user_id);

grant select, insert, update
on table public.local_records
to authenticated;

revoke all
on table public.local_records
from anon;

alter table public.local_records enable row level security;

create policy "select own local_records"
on public.local_records
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "insert own local_records"
on public.local_records
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "update own local_records"
on public.local_records
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- No delete policy: this proof never deletes remote rows, and with RLS enabled, an operation with
-- no applicable policy is denied by default — that default-deny is exactly what's wanted here, not
-- an oversight.
