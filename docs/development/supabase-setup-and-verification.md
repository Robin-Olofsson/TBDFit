# Supabase Setup and Verification

This is an operational runbook: how to stand up a Supabase project this codebase can talk to, how
to configure a client against it, and how to verify it actually works. It is not an architecture
document — for the backend strategy, migration model, and exit-path reasoning, see
[ADR-004](../architecture/adr/0004-initial-backend-platform-and-migration-strategy.md).

## Architecture context

- Supabase is the current shared backend platform for this project (ADR-004) — not the permanent
  application architecture.
- `supabase/migrations/` at the repository root is the source of truth for the versioned remote
  schema. It is not owned by any one client.
- Each native client has its own Supabase implementation, confined below a purpose-scoped
  capability boundary (see `CLAUDE.md`'s Supabase boundary rule and ADR-004). Android's
  implementation is documented here as the current example; a future Apple implementation would
  follow the same setup against the same backend, with its own native Supabase SDK usage.
- This document covers *setup and verification*, not *why Supabase* or *how migration to a custom
  API would work* — that's ADR-004's job.

## Fresh Supabase project setup

Steps to stand up a new Supabase project this codebase can use, in the order we actually used them:

1. Create or open a Supabase project at [supabase.com](https://supabase.com).
2. Row Level Security may already be enabled by default on new projects as a safe default —
   harmless either way, since the migration below enables it explicitly if it isn't already.
3. **Authentication → Sign In / Providers → Email** should already be enabled (Supabase's
   default). Do **not** enable the **Anonymous** provider — the application no longer uses it (see
   [Anonymous authentication — retired](#anonymous-authentication--retired) below); a fresh project
   needs no anonymous-auth configuration at all.
4. Apply the migration in the SQL editor:
   [`supabase/migrations/20260905120000_create_local_records.sql`](../../supabase/migrations/20260905120000_create_local_records.sql).
5. Confirm table-level privileges match the intent: `authenticated` can `select`/`insert`/`update`
   on `public.local_records`; `anon` has no access. The migration file includes the explicit
   `grant`/`revoke` statements for this — applying the migration as written already does it.
6. Confirm RLS is enabled on `public.local_records` and that the `select own local_records`,
   `insert own local_records`, and `update own local_records` policies exist, each scoped
   `to authenticated` (no delete policy — that's deliberate, not missing).
7. Confirm the table has exactly the columns the migration defines: `id`, `user_id`, `created_at`,
   `value`.

**`local_records` and everything in it is disposable technical-proof data** — it exists only to
prove the local → Supabase sync mechanism end to end. It is not, and must not be treated as, the
workout domain model (that remains deferred per PD-003).

## Android configuration

The Android phone client reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` from
`android/local.properties`, which is gitignored and never committed. Add these two lines yourself
(placeholders only — never commit or paste real values into this repo or its docs):

```properties
sdk.dir=<local Android SDK path>
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=sb_publishable_<...>
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
```

**Naming note:** the variable is still called `SUPABASE_ANON_KEY` for historical reasons, but the
value that belongs there is the current **publishable client key** from your project's API
settings — Supabase's newer terminology for the same client-safe key. This key is safe to ship in
a client app by design (Supabase's security boundary is RLS, not secrecy of this key), but it's
still kept out of git for environment hygiene, not because it's a true secret.

**`GOOGLE_WEB_CLIENT_ID`** is the OAuth 2.0 **Web application** Client ID from Google Cloud
Console — not the Android Client ID (see "Google authentication" below). It's client-safe
configuration, not a secret, kept out of git the same way as the values above.

**Never** put any of the following in `local.properties`, anywhere in this app, or in any
document: the service-role key, any other secret/API key, the database password, any access or
refresh token, or a Google OAuth **client secret**. `local.properties` is confirmed covered by the
root `.gitignore`.

## Email verification

Email/password account creation requires email ownership verification before the account becomes
usable — verify this is actually enabled on the live project (**Authentication → Sign In /
Providers → Email → Confirm email**); do not assume a prior technical-proof-era setting is still
what's wanted.

The confirmation link uses a deep link back into the app:

- Redirect URL `tbdfit://auth-callback` must be added under **Authentication → URL Configuration →
  Redirect URLs** on the Supabase Dashboard.
- `scheme`/`host` are configured to match in `SupabaseClientProvider`'s `install(Auth) { ... }`,
  and the matching intent-filter is declared on `MainActivity` in `AndroidManifest.xml`
  (`android:launchMode="singleTask"` avoids stacking a duplicate activity instance when the link is
  tapped from an email app while TBDFit is already running).
- The Auth plugin is configured with `flowType = FlowType.PKCE` rather than the default
  `FlowType.IMPLICIT`: Supabase's own docs note that some email clients/scanners strip URL
  fragments (which implicit-flow tokens are carried in) before the link ever reaches the app; PKCE
  carries a `code` query parameter instead, which survives that.
- `MainActivity.onCreate`/`onNewIntent` call `SupabaseClientProvider.client.handleDeeplinks(intent)`
  — this parses the link and establishes the session itself; no further wiring is needed since
  `AuthState` is already derived from the same session state.

`AuthState` never claims `SignedIn` merely because a signup request was accepted — see
`requiresEmailVerificationPrompt` in `EmailAuthScreen.kt`. Until the link is followed, the app shows
a "Check your email" screen with a resend option (`AuthGateway.resendEmailVerification`).

## Google authentication

Native ID-token flow via Android Credential Manager — not the deprecated `GoogleSignIn` API, and
not an OAuth browser-redirect flow.

**Google Cloud Console** — two separate OAuth 2.0 Client IDs are required:

- A **Web application** Client ID. Its value is what goes in `GOOGLE_WEB_CLIENT_ID` above, and is
  also what gets registered in the Supabase Dashboard (next section) — **not** the Android Client
  ID.
- An **Android** Client ID, configured with package name `com.tbdfit.app` and the SHA-1 signing
  certificate fingerprint of the app (register both the debug and release fingerprints — they
  differ). This authorizes the app itself to request ID tokens; its value is not pasted anywhere in
  app code or in the Supabase Dashboard.

**Supabase Dashboard** — **Authentication → Sign In / Providers → Google**: enable the provider and
add the **Web** Client ID under **Client IDs**. No client secret is required for this native
ID-token flow (unlike the OAuth browser-redirect flow, which this app does not use) — confirm this
directly in the Dashboard UI for your project version.

**Wear note:** `:phone` and `:wear` share `applicationId = com.tbdfit.app`, but Google auth is
Phone-only in this slice — the Android Client ID/SHA-1 registration above is unaffected by Wear,
since Wear has no Google auth code at all here.

**Identity linking:** Supabase Auth automatically links a new Google identity to an existing
email/password user when the emails match **and** the existing identity is already verified (no
separate dashboard toggle needed for this — the "Manual Linking" toggle that exists is a different,
unrelated feature for linking *different*-email identities, which this app does not use). This
means a user who verifies `alice@example.com` via email/password and later taps "Continue with
Google" with the same address converges to the same account automatically.

## Android application identity (Phone ↔ Wear)

Not Supabase-specific, but recorded here as the closest existing Android operational runbook:

- `:phone` and `:wear` require the **same installed application/package identity (`applicationId`)
  and the same signing certificate** — this is a Google Play services requirement for Wear Data
  Layer (DataClient/MessageClient) communication to work at all, not a project preference.
- The shared `applicationId` (`com.tbdfit.app`) is deliberately **product-neutral** — it identifies
  the whole TBDFit Android product across both form factors, not one of them.
- Each module's Kotlin **namespace** (`com.tbdfit.phone`, `com.tbdfit.wear`) is unaffected and
  remains platform/module-specific source organization — only the installed app identity is shared.
- Release signing configuration is still deferred (only debug builds exist so far), but future
  Phone and Wear release artifacts must use a compatible (matching) signing identity for the same
  Data Layer reason.

## Project URL: which value goes where

Use the project's base URL as `SUPABASE_URL`:

```
https://<project-ref>.supabase.co
```

**Do not** use a service-specific path like:

```
https://<project-ref>.supabase.co/rest/v1/
```

The Supabase client library constructs each service's specific endpoint (`/rest/v1/`, `/auth/v1/`,
etc.) itself from the base URL — passing a service path as the base URL breaks every service the
client talks to, not just the one whose path you happened to paste.

## Anonymous authentication — retired

**Current application: anonymous auth is no longer used or required.** Anonymous Supabase
authentication was introduced early on only to exercise real, authenticated Supabase sync for the
disposable `LocalRecord` technical proof, before any real login existed. Now that Phone
email/password authentication exists, anonymous sign-in has been removed from the application:

- `SupabaseLocalRecordRemoteStore` no longer calls `signInAnonymously()`. It checks for an
  existing session (`client.auth.currentSessionOrNull()`) and, if none exists, fails explicitly
  with `NoAuthenticatedSessionException` — it never creates a session of its own. Establishing/
  restoring a session is the auth capability's (`com.tbdfit.phone.auth`) responsibility alone.
- A `LocalRecord` sync attempt with no authenticated session now fails safely: the local record is
  left pending (never marked synced, never deleted), and the failure is logged distinctly from a
  network/Postgres failure.
- **The application can now run with the Supabase Anonymous provider disabled.** Email/password
  auth is unaffected and remains the only provider the app uses.
- Existing remote `local_records`/`wear_replicas` rows owned by old anonymous users are unaffected
  by this change — they remain disposable technical-proof data (see above) and are not migrated to
  any real account. They may be left in place or cleaned up manually; there is no code-level
  migration path for them, deliberately.

The **Verification status** and **Troubleshooting** material immediately below predates this
change and describes the anonymous-auth-based technical proof as it was tested on 2026-09-05 —
those bullets are a historical record of what was actually run at the time, not current guidance,
and are left as originally written rather than rewritten for consistency. Do not read them as
describing current application behavior.

## Historical troubleshooting note: anonymous sign-in disabled (technical-proof era, retired)

This section describes a failure mode that could only occur while the application still called
`signInAnonymously()`. It no longer applies to the current codebase (see
[Anonymous authentication — retired](#anonymous-authentication--retired) above) and is kept only as
a historical record of what was diagnosed and fixed at the time.

**Observed error** (from the `TBDFit.sync` diagnostic log, see below):

```
anonymous sign-in failed status=422 error=anonymous_provider_disabled description=Anonymous sign-ins are disabled: anonymous_provider_disabled
```

**Root cause:** anonymous sign-in had not actually been enabled on the live Supabase project —
enabling RLS or applying the migration does not enable it; it's a separate, explicit toggle.

**Resolution:** Supabase Dashboard → **Authentication** → **Sign In / Providers** → enable
**"Allow anonymous sign-ins"**, and confirm it actually saves (some dashboard versions need an
explicit save action, not just flipping the toggle).

**No Android rebuild was required** after fixing this server-side — retrying "Sync now" in the
already-running app succeeded immediately once anonymous sign-in was enabled.

## Diagnostic logging

The Android client logs sync progress under a single tag: `TBDFit.sync`.

```bash
adb logcat -s TBDFit.sync:* AndroidRuntime:E
```

What gets logged, and why it's safe:

- Configuration presence (`SUPABASE_URL configured=true/false`, host if present) — never the key
  value itself.
- Sync orchestration: `sync requested`, `pendingCount=<n>`, `syncing id=<id>`, remote
  success/failure, local acknowledgement (`markSynced ...`).
- The authenticated user's UID (`uid=<uid>`) — a Supabase user id is not sensitive.
- Remote write start/success/failure, including structured PostgREST/Postgres error fields
  (status, error code, hint, details) when available — this is what made the anonymous-auth
  failure above immediately diagnosable instead of an opaque failure.

**Never logged, under any circumstance:** access tokens, refresh tokens, database passwords,
secret/service-role keys, or any other bearer credential. UID-only logging is intended as a
temporary verification aid, not a permanent logging policy — revisit whether it's still needed
once verification is complete.

## Verification status

The dated section below predates anonymous auth's retirement (see
[Anonymous authentication — retired](#anonymous-authentication--retired)) and is left exactly as
recorded at the time, including its references to anonymous auth — it is a historical record of
what was actually tested on that date, not current guidance.

### VERIFIED (as of 2026-09-05)

- Android phone can create a `LocalRecord` locally.
- Local creation does not require Supabase or network availability.
- The record is stored in Room as `pending`.
- Android successfully performs anonymous Supabase authentication against the live project.
- The authenticated remote write reaches `public.local_records`.
- The remote write succeeds against the real, live Supabase project (not a fake/test double).
- Local acknowledgement is written, and the record transitions `pending` → `synced`.
- The existing JVM/Robolectric test suite (`LocalRecordDaoTest`, `LocalRecordSyncCoordinatorTest`,
  the `FoundationTest`s) remained green before this live verification.
- `local_records.user_id` matches the authenticated Supabase Auth user identity.
- Offline local creation succeeds without backend/network availability.
- Reconnect + retry successfully synchronizes the pending record.
- Repeated synchronization produces exactly one logical remote row for the same LocalRecord ID.
- Room record data and pending/synced state survive Android process force-stop/restart.
- The anonymous Supabase Auth session survives a normal Android process restart.
- User A can create/read A-owned data.
- User B cannot read User A's row.
- User B cannot INSERT a row claiming User A's `user_id` — live test returned HTTP 403.
- User B cannot UPDATE User A's row.
- Unauthenticated/public access cannot expose `local_records` — live test returned HTTP 401.
- Concurrent calls to the current LocalRecord sync operation are serialized within the coordinator
  instance, verified by a deterministic concurrent-sync test showing exactly one effective remote
  upsert attempt for the same pending record.
- Credential-bearing Supabase exception messages are not logged by the coordinator; structured,
  safe diagnostics (status, error code, description) are used instead.

### STILL TO VERIFY

Not yet checked — do not assume this passes until actually tested and reported:

- GitHub Android CI is green after the latest Supabase changes.
- Live, on-device verification of anonymous-auth retirement: with a fresh install and the
  Supabase Anonymous provider disabled on the dashboard, confirm login/create-account still work,
  and that triggering "Sync now" while signed out fails safely (record stays `pending`, no crash,
  no anonymous session appears under **Authentication → Users**). See the exact steps in
  [Anonymous authentication — retired](#anonymous-authentication--retired)'s live verification
  runbook entry below (section F).

## Remaining verification runbook

### A. Ownership

Compare **Authentication → Users** (the signed-in user's ID) against **Table Editor →
local_records → user_id** for a row that user created. They must match exactly.

### B. Offline → retry

1. Disable network on the device/emulator.
2. Create a record — confirm it shows `pending` and the app does not crash or hang.
3. Restore network.
4. Press "Sync now" — confirm the record becomes `synced`.
5. Confirm exactly one matching row exists in `local_records` (not zero, not two).

### C. Process restart

1. With at least one record present, force-stop the app: `adb shell am force-stop com.tbdfit.app`.
   `:phone` and `:wear` now share this applicationId (required for Wear Data Layer communication —
   see below), so when both are installed on different devices/emulators, target the right one
   explicitly, e.g. `adb -s <phone-emulator-serial> shell am force-stop com.tbdfit.app`.
2. Relaunch manually from the launcher (not from Android Studio's run button).
3. Confirm the Room data and each record's pending/synced status are exactly as before.

### D. Session persistence

1. Note the UID logged before the restart.
2. Force-stop and relaunch (as in C).
3. Perform another sync and note the UID logged this time.
4. Compare the two UIDs and record what was actually observed — do not assume persistence either
   way without checking.

### E. RLS / user isolation

Goal, without instructing anyone to log or paste tokens into anything:

- A second user (user B) must not be able to read user A's row.
- User B must not be able to insert a row claiming user A's `user_id`.
- Unauthenticated/public access must not expose any `local_records` rows.

If a safe method for testing this (e.g. a scripted API check using two distinct authenticated
sessions) is chosen later, document the exact method here at that time — none is prescribed yet.

### F. Anonymous auth retirement

To be run once the Supabase Anonymous provider is disabled on the dashboard (**Authentication →
Sign In / Providers → Anonymous → off**):

1. Confirm **Authentication → Sign In / Providers → Email** is still on.
2. Fresh install the Phone app (or clear its data) so no session is persisted yet.
3. Launch → confirm the login/create-account screen appears (`AuthState.SignedOut`), not a crash
   and not an automatically-created anonymous session.
4. Create a real account and log in → confirm the profile screen appears normally.
5. Log out → back at the login/create-account screen → confirm no new row appears under
   **Authentication → Users** as a result of being signed out (no anonymous fallback fires).
6. While signed out, use `adb` or a debug affordance to trigger the LocalRecord sync path (or
   simply note that the current UI makes this unreachable, since sync is only available while
   signed in) → confirm: no crash, no new row under **Authentication → Users**, and any
   already-pending `LocalRecord` stays `pending` rather than being marked `synced` or deleted.
7. Log back in with the same real account → confirm "Sync now" succeeds normally using that
   session, exactly as before this change.

### G. Email landing + verification flow

```
fresh SignedOut app (landing screen: "Continue with Google" / "Continue with Email")
→ tap "Continue with Email" → confirm the slide-in transition, and that back navigation
  (system back gesture/button) slides back to the landing screen
→ tap "Create account", enter a real email + a valid password → submit
→ confirm the "Check your email" screen appears (NOT the profile screen) — this is the point that
  proves signup-accepted is not being confused with authenticated
→ open the verification email, tap the link → confirm it opens TBDFit (not a browser dead end)
→ back in the app (may need to bring it to foreground), confirm AuthState became SignedIn without
  any further action
→ force-stop the app, relaunch → confirm still SignedIn (no re-login prompt)
→ logout → confirm the landing screen reappears
→ tap "Continue with Email" → "Log in" with the same credentials → confirm SignedIn again
```

Also verify: tapping "Resend email" on the "Check your email" screen results in a second email
arriving; and in **Supabase Dashboard → Authentication → Users**, the new user's row shows a
confirmed email timestamp only after the link was followed, not at signup time.

### H. Google sign-in

```
SignedOut (landing screen)
→ tap "Continue with Google"
→ Google account chooser appears → select an account (or cancel, and confirm the app returns
  cleanly to the landing screen with no error message shown for a plain cancellation)
→ confirm AuthState becomes SignedIn immediately, no email/password step, no verification screen
→ force-stop the app, relaunch → confirm still SignedIn
→ logout → confirm the landing screen reappears
```

Also verify in **Supabase Dashboard → Authentication → Users**: the signed-in row's provider shows
Google; and, separately, sign up with email/password using the same address as a Google account,
verify it, then "Continue with Google" with that same address on a fresh sign-out — confirm both
methods resolve to the **same** user row (same id), not two separate rows.

### I. Profiles: authenticated RLS verification (Data API, not SQL Editor)

**Do not use the SQL Editor as evidence that RLS works.** The SQL Editor runs as the database
owner/service role — it can bypass RLS entirely or behave materially differently from a normal
`authenticated`-role request, so a query succeeding there proves nothing about what a real signed-in
user's app request can or cannot do. The only valid proof is exercising the actual Data API with
real per-user JWTs, exactly as the Android app does.

Use two disposable throwaway accounts (e.g. created via the app's own signup + verification, or any
already-available test accounts) — never real user accounts. **Never print, log, or paste an access
token anywhere, including into this document or any AI/chat tool.** Keep tokens only in local shell
variables for the duration of the test, in a terminal you control.

```bash
export SUPABASE_URL="https://<project-ref>.supabase.co"
export ANON_KEY="<publishable/anon key>"          # safe, client-facing — see "Android configuration" above

# --- Sign in as user A; the token never leaves this shell variable ---
A_TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d '{"email":"<user-a-email>","password":"<user-a-password>"}' | jq -r '.access_token')

# A inserts their own profile — also doubles as the "generated column" check (D.4 below): the
# response body's normalized_username must read back lowercase.
curl -s -X POST "$SUPABASE_URL/rest/v1/profiles" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $A_TOKEN" \
  -H "Content-Type: application/json" -H "Prefer: return=representation" \
  -d '{"username":"Testusera"}'
# Expect: 201, one row, user_id = A's own auth uid, normalized_username = "testusera".

A_USER_ID="<paste A's user_id from the response above — not a secret, just an id>"

# A reads their own profile
curl -s "$SUPABASE_URL/rest/v1/profiles?select=*" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $A_TOKEN"
# Expect: exactly A's row, nothing else.

# --- Sign in as user B ---
B_TOKEN=$(curl -s -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON_KEY" -H "Content-Type: application/json" \
  -d '{"email":"<user-b-email>","password":"<user-b-password>"}' | jq -r '.access_token')

# B cannot read A's row
curl -s "$SUPABASE_URL/rest/v1/profiles?select=*" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $B_TOKEN"
# Expect: empty array — RLS silently excludes A's row, not an error response.

# B cannot update A's row
curl -s -X PATCH "$SUPABASE_URL/rest/v1/profiles?user_id=eq.$A_USER_ID" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $B_TOKEN" \
  -H "Content-Type: application/json" -H "Prefer: return=representation" \
  -d '{"username":"hijacked"}'
# Expect: empty array / 0 rows affected. Re-check with $A_TOKEN that A's row is unchanged.

# B cannot insert a profile claiming A's user_id
curl -s -X POST "$SUPABASE_URL/rest/v1/profiles" \
  -H "apikey: $ANON_KEY" -H "Authorization: Bearer $B_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"$A_USER_ID\",\"username\":\"hijacked2\"}"
# Expect: rejected by the insert policy's with-check. Confirm via A_TOKEN that no such row exists.

# --- Unauthenticated / public ---
curl -s "$SUPABASE_URL/rest/v1/profiles?select=*" -H "apikey: $ANON_KEY"
# Expect: empty array / no rows — no Authorization header at all, anon-role request.
```

Record the actual HTTP status/body observed for each step here once run — do not assume the
expected outcome without checking, the same discipline already applied to `local_records`' own
verification above.

### J. Profiles: database constraint verification

Schema/constraint behavior (as opposed to *whose* request it is) may be checked in the SQL Editor,
since RLS identity isn't what's being tested — but case-insensitive uniqueness specifically must
still use separate real authenticated users (continuing directly from Section I), since the point
is that *different accounts* collide, not just that a constraint exists.

**Case-insensitive uniqueness** (continuing Section I's `$A_TOKEN`/`$B_TOKEN`, plus a third user C):
have A, B, and C each attempt to create a profile with `Robin`, `robin`, and `ROBIN` respectively
(one per user, via the same `POST .../rest/v1/profiles` call as above). Expect exactly one `201`
across all three attempts; the other two must fail with a PostgREST error whose underlying Postgres
code is `23505` (unique_violation) on `profiles_normalized_username_key`. Confirm via any one
token's own `select=*` that only one such row's `normalized_username` is `robin`.

**Invalid usernames** (schema-only — SQL Editor is fine here, or reuse `$A_TOKEN` for realism):
confirm each of `ab` (too short), a 31-character string (too long), `röbin` (non-ASCII), `rob in`
(internal whitespace), and `robin!` (disallowed punctuation) is rejected with SQLSTATE `23514`
(check_violation) on `profiles_username_syntax` — independent of whatever Android's own validation
would have allowed through, proving the database is the actual authority.

**Existing user, no prior profile:** confirm a user who already has a Supabase Auth session but no
`profiles` row reaches `ProfileState.Missing` in the app and can successfully complete profile
creation — no manual data migration or fabricated username required.
