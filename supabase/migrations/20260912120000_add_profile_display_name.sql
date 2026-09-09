-- Adds display_name to public.profiles and keeps username exactly as create_profiles.sql defined
-- it: REQUIRED, syntax-checked, and uniquely normalized. This is a cross-client product decision
-- (Web/Android/Wear/future Apple all share one account model), not a Web-specific choice — see
-- web/README.md's "Identity model" section and docs/development/supabase-setup-and-verification.md's
-- "Shared username + display name" section for the full rationale.
--
-- Why username stays required (this migration does NOT relax profiles_username_syntax,
-- profiles_normalized_username_key, or the NOT NULL constraint on username in any way): username is
-- the one field this schema deliberately keeps ready for a future username-based login — a unique,
-- unambiguous, case-insensitive-via-normalized_username mapping to exactly one auth.users row, with
-- no dependency on display_name and no public exposure of email. Username-based login ITSELF is
-- explicitly NOT implemented by this migration or by any client change alongside it: resolving a
-- username to an email/user id for sign-in would require a server-side lookup (e.g. a narrowly
-- scoped RPC or edge function) that never hands the mapping to the browser directly — inventing that
-- lookup here, or worse, having a client enumerate/guess it, is exactly the kind of
-- username-enumeration/email-exposure hazard this table's own security invariants (see
-- create_profiles.sql's header comment — no anon SELECT, no public existence check) already guard
-- against. That capability is reserved for a separate, later, security-reviewed slice.
--
-- display_name is the independently-editable, human-facing name. It is NOT NULL — a profile row is
-- either valid and complete or does not exist at all; there is no "profile with a missing
-- display_name" onboarding state. Account creation (Supabase Auth: email/password) and TBDFit
-- profile creation are separate lifecycle steps for every client: Android collects a username via
-- ProfileCompletionScreen and defaults display_name to the same value
-- (SupabaseProfileGateway.createOwnProfile/NewProfileRow); Web collects both explicitly, after
-- SIGNED_IN, via a dedicated Profile Setup step (web/src/auth/ProfileSetupForm.tsx,
-- profileCreation.ts) — never during signup, and never transported through auth user metadata. In
-- both cases display_name starts out equal to username only as an application-level default, never
-- a database invariant — editing one never touches the other afterward.
--
-- This migration was never applied to the live Supabase project (confirmed before revising it), so
-- editing it in place — rather than stacking a new migration on top, this repo's normal rule — is a
-- deliberate, one-time exception: the original (unapplied) version of this file assumed a
-- Display-Name-only Web signup with a nullable username, a design superseded before it ever reached
-- production. Every migration before this one remains untouched.

alter table public.profiles
  add column display_name text;

-- Backfill before adding NOT NULL below, so the constraint can never fail against a row that
-- predates this column. Defensive rather than load-bearing here (no row exists yet — this migration
-- was never applied), but this is the correct, safe order for any add-NOT-NULL-column migration —
-- and username is the only sane backfill value for a row that was created before display_name
-- existed at all (every new row going forward supplies a real, possibly different, display_name
-- explicitly at insert time — see this file's header comment).
update public.profiles
  set display_name = username
  where display_name is null;

alter table public.profiles
  alter column display_name set not null;

-- Bound chosen to comfortably fit real human names while remaining a deliberate limit, not an
-- arbitrary one. No "is null or" clause is needed any more: display_name is NOT NULL, so a NULL
-- value can no longer reach this check.
alter table public.profiles
  add constraint profiles_display_name_not_blank
    check (length(btrim(display_name)) > 0);

alter table public.profiles
  add constraint profiles_display_name_length
    check (length(display_name) <= 80);

-- No RLS change: display_name is just another column on the same existing row, already fully
-- covered by the current select/insert/update-own-profile policies (see create_profiles.sql). No
-- new policy, no SECURITY DEFINER function, no trigger — this is a purely additive-column change.
