-- Adds an optional profile biography. Purely additive: does not touch username/display_name's
-- existing NOT NULL contracts (see create_profiles.sql / add_profile_display_name.sql), does not
-- change RLS (bio is just another column on the same owner-only row, already fully covered by the
-- existing select/insert/update-own-profile policies), and does not require a value at profile
-- setup time (ProfileSetupForm.tsx collects only username + display name; bio stays NULL for every
-- profile created through it today, and this migration adds no backfill for it because there is
-- nothing to backfill — bio never existed before this column).
--
-- NULL means "no bio configured" — there is no separate "bio not set" flag or empty-string
-- convention. 280 is a deliberate short-form bound (Hevy/social-profile scale, not a long-form
-- about-page), enforced by a database CHECK rather than left to client-side trust alone.
--
-- No write path exists in Web yet as of this migration — there is no Edit Profile UI to call one
-- from (see docs/development/supabase-setup-and-verification.md's Profile Core + Social Graph
-- section). The existing "update own profile" policy already covers a future bio-write call with
-- zero further schema/policy change once that UI exists.
alter table public.profiles
  add column bio text
    constraint profiles_bio_length check (bio is null or length(bio) <= 280);
