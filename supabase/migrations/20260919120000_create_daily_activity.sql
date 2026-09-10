-- daily_activity: the first Supabase table for TBDFit's Movement domain — a normalized, private,
-- device-independent daily aggregate. See docs/development/supabase-setup-and-verification.md's
-- "Daily Movement (Steps)" section for the full producer/consumer picture this establishes:
--   many future device/source observations (Phone, Watch, HealthKit, Health Connect, ...)
--     -> a future ingestion/deduplication layer (NOT built here)
--     -> one normalized daily total per (account, local date)
--     -> daily_activity (this table)
--     -> Web Statistics (read-only, later)
--
-- This is NOT a raw sensor/health-event warehouse. There is deliberately no step_samples,
-- health_events, device_samples, or per-source table — V1 central data is exactly one normalized
-- daily total per account per day, full stop. Reconciling multiple simultaneous device sources
-- (e.g. a phone and a watch both reporting steps for today) is explicitly deferred to a future
-- ingestion layer that decides the single value written here; this table has no opinion on how that
-- value was produced.
--
-- CLIENT-AGNOSTIC BY DESIGN, same precedent as workouts (20260917120000_create_workout_history.sql):
-- nothing below distinguishes Web from Phone/Watch. Web currently has no product requirement to
-- submit step data (no ingestion source exists on Web), so no Web mutation UI is built against this
-- table in this slice — but that is a Web product decision enforced by what Web's own code simply
-- never calls, not a database-level role restriction. A future native client authenticates as the
-- exact same `authenticated` role and is expected to write here directly.
--
-- NO SOURCE/PROVENANCE COLUMNS (source, device_id, health_platform, ...): deliberately deferred.
-- Multiple real ingestion sources don't exist yet, so a provenance column would be speculative
-- schema for a problem this project doesn't have yet — see CLAUDE.md's "avoid architecture
-- astronautics" / "don't pay today's complexity cost for hypothetical future requirements."
create table if not exists public.daily_activity (
  -- Same client-omittable-owner pattern as workouts.owner_id: a writing client's upsert payload
  -- never needs to state its own identity — auth.uid() supplies it, so there is nothing here for a
  -- caller to spoof.
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  -- The LOCAL calendar day the account experienced this activity on — supplied by the recording
  -- client, deliberately NOT derived by truncating a UTC timestamp server-side (identical reasoning
  -- to workouts.workout_date's own comment: a timestamptz::date truncation is wrong for activity
  -- near midnight or after a timezone change). A future ingestion layer is responsible for
  -- determining the correct local date before writing here.
  activity_date date not null,
  -- The account's current normalized total for this date — a replaceable snapshot, not an
  -- accumulating counter. A client performs an idempotent upsert of its complete daily total
  -- (see the ON CONFLICT form documented below); this table never adds submitted values to an
  -- existing total, because a future phone/watch integration may resubmit a complete corrected
  -- total for the same day (health platforms can legitimately revise a total downward during their
  -- own source deduplication) — blind accumulation would double-count or diverge from truth.
  -- Arbitrating between multiple devices submitting different totals for the same day (e.g. a stale
  -- device later overwriting a fresher one) is explicitly out of scope for this V1 table: it is a
  -- future ingestion/sync-layer concern, not something this schema attempts to resolve via
  -- MAX()-wins, revisions, or vector clocks.
  steps integer not null
    constraint daily_activity_steps_non_negative check (steps >= 0),
  -- The one minimal fact this table keeps about *when* the current total was last replaced, without
  -- keeping *which* device/source replaced it (provenance is deferred, see above). This matters once
  -- multiple real device sources exist and a future ingestion layer needs to reason about a stale
  -- resubmission — knowing "this row was last touched at T" is a prerequisite for that reasoning,
  -- even though this table performs none of it itself. No separate created_at: every write to a row
  -- (the first insert and every later upsert) is "the current total as of now," so first-ever-written
  -- and last-updated collapse to the same fact for this table's purpose.
  updated_at timestamptz not null default now(),
  -- One authoritative row per account per local calendar day. NO ROW for a date means "TBDFit has
  -- no movement data for that day" — a row with steps = 0 means "TBDFit has data indicating zero
  -- steps." These are deliberately different states; nothing in this schema ever fabricates a zero
  -- row for a missing day (that stays a presentation-layer decision for a future chart).
  constraint daily_activity_pkey primary key (user_id, activity_date)
);

-- No additional index: the composite primary key already serves both expected query shapes —
-- the point-upsert (user_id = ? and activity_date = ?, an exact PK lookup) and the range-read
-- (user_id = ? and activity_date between ? and ?, ordered by activity_date — activity_date is the
-- PK's trailing column for a fixed leading user_id, so this is a single efficient index range scan).
-- No redundant index is added without a demonstrated query need beyond these two shapes.

-- Delete is granted/policied the same way workouts grants owner-delete — not required by this
-- slice's spec, but a deliberate, harmless extension of the same "owner may remove their own row"
-- precedent already established for completed Workouts, rather than an oversight.
grant select, insert, update, delete
on table public.daily_activity
to authenticated;

revoke all
on table public.daily_activity
from anon;

alter table public.daily_activity enable row level security;

-- Movement is private personal activity data, same security domain as workouts — completely
-- separate from the Profile + Social read boundary (20260918130000_create_follows.sql /
-- 20260918140000_create_profile_summary.sql). A `follows` edge between two accounts grants zero
-- extra visibility here: following someone is a Profile/Social-graph concept, not an activity-data
-- grant, and this table's policies never reference `follows` at all.
create policy "select own daily activity"
on public.daily_activity
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "insert own daily activity"
on public.daily_activity
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "update own daily activity"
on public.daily_activity
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create policy "delete own daily activity"
on public.daily_activity
for delete
to authenticated
using ((select auth.uid()) = user_id);

-- WRITE CONTRACT: plain RLS-protected upsert, no RPC, no SECURITY DEFINER function. This is a
-- single-row write to a single table with no cross-table atomicity requirement (unlike
-- record_completed_workout's multi-table aggregate write) — RLS alone already fully expresses the
-- required invariant. A client performs:
--
--   insert into public.daily_activity (activity_date, steps)
--   values ($1, $2)
--   on conflict (user_id, activity_date) do update
--     set steps = excluded.steps, updated_at = now();
--
-- (user_id is never supplied by the client — it defaults to auth.uid(), same as workouts.owner_id.)
-- A forged `user_id = <someone else>` fails the INSERT's WITH CHECK before ON CONFLICT resolution is
-- ever reached; an UPDATE/DELETE targeting a row the caller does not own matches zero rows under the
-- USING policy. There is nothing an RPC would add here that RLS does not already guarantee.
--
-- READ CONTRACT: plain RLS-protected SELECT, no RPC/view — unlike get_my_profile_summary() (which
-- exists specifically to avoid an N+1 fan-out across three unrelated tables: profiles, workouts,
-- follows), a single-table date-range SELECT has no such problem to solve:
--
--   select activity_date, steps
--   from public.daily_activity
--   where user_id = auth.uid() and activity_date between $1 and $2
--   order by activity_date;
--
-- A date with no row is simply absent from the result set — this table/policy never fabricates a
-- zero-step row for a missing day; that remains a future chart's presentation decision, not this
-- backend's.
