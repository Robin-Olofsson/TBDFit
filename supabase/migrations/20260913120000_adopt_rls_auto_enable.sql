-- Adopts public.rls_auto_enable() and its ensure_rls event trigger into version control. Both
-- objects were created directly against the live project outside this migration history — no
-- CREATE FUNCTION/CREATE EVENT TRIGGER for either name exists anywhere before this file. That was
-- schema/security drift: a fresh database built from this repo's migrations alone did not have
-- this mechanism, even though the live project did (see
-- docs/development/supabase-setup-and-verification.md's "Database source-of-truth principle" for
-- why that is treated as a bug, not an acceptable second source of truth).
--
-- The function body and event trigger definition below are copied VERBATIM from the live project's
-- own catalogs (`pg_get_functiondef('public.rls_auto_enable()'::regprocedure)` and
-- `pg_event_trigger` for `ensure_rls`) — not reconstructed from the function's name, not
-- simplified, and not the synthetic stand-in used earlier in this repo's history purely to test
-- grant-revocation/event-trigger-dispatch mechanics in isolation. This supersedes this same
-- (never-applied) migration file's prior guarded-REVOKE-only version, which was accepted only as
-- temporary interim hardening against an unknown external object — see this file's own git history
-- for that version. This migration was never applied to the live project, so revising it in place
-- (rather than superseding it with a new file) is safe; every migration before it is untouched.
--
-- What it does, read from the real body: on CREATE TABLE / CREATE TABLE AS / SELECT INTO producing
-- a table (or partitioned table) in the `public` schema, force-enable row level security on it.
-- This is a defense-in-depth safety net for tables created without an explicit `ENABLE ROW LEVEL
-- SECURITY` in their own migration — every existing TBDFit table already enables RLS explicitly in
-- the migration that creates it, so adopting this here changes no existing table's behavior; it
-- only starts protecting future ones from this point in the chain onward, matching this project's
-- "do not require historical migrations to behave as if this had always existed" placement rule.
create or replace function public.rls_auto_enable()
 returns event_trigger
 language plpgsql
 security definer
 set search_path to 'pg_catalog'
as $function$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$function$;

-- Event triggers have no CREATE OR REPLACE / ALTER form for changing their event, tag list, or
-- target function (ALTER EVENT TRIGGER only supports ENABLE/DISABLE/OWNER TO/RENAME TO) — DROP +
-- CREATE is the only deterministic way to (re)establish an exact definition, and is safe here
-- specifically because the definition being (re)created is identical to what live already has
-- (verified against the live catalog dump, not assumed): this cannot change behavior for anything
-- that already relied on ensure_rls, on live or on a database that already carries this drift.
drop event trigger if exists ensure_rls;

create event trigger ensure_rls
  on ddl_command_end
  when tag in ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
  execute function public.rls_auto_enable();

-- Hardening (Security Advisor finding): PUBLIC — and therefore anon/authenticated/service_role,
-- which inherit every PUBLIC grant — held EXECUTE by default from function creation, with no
-- legitimate reason to call this internal DDL hook directly. `postgres` (the function's owner)
-- needs no explicit grant: owner privilege is implicit and untouched by any of the REVOKEs below.
-- Unlike this file's prior version, these REVOKEs are unconditional, not existence-guarded: this
-- migration now CREATEs the function immediately above, so it is guaranteed to exist by this point
-- in the chain on every database, fresh or live-drift alike.
--
-- Not a live, directly exploitable path today (verified locally): Postgres refuses to invoke any
-- function whose return type is event_trigger via ordinary SQL/RPC regardless of EXECUTE grants —
-- calling it directly fails with "trigger functions can only be called as triggers", a type-level
-- restriction, not a permission one. Revoking EXECUTE is still the correct hardening step (least
-- privilege, and it turns that failure into an explicit permission-denied error for any
-- still-unauthorized caller) — see docs/development/supabase-setup-and-verification.md for the full
-- verification record (fresh-database test, live-drift convergence test, event-trigger functional
-- test, and per-role has_function_privilege checks).
revoke execute on function public.rls_auto_enable() from public;
revoke execute on function public.rls_auto_enable() from anon;
revoke execute on function public.rls_auto_enable() from authenticated;
revoke execute on function public.rls_auto_enable() from service_role;
