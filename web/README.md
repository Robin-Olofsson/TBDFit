# TBDFit Web

A clickable UX prototype for the TBDFit Web client (see `docs/product/web-information-architecture.md`
for the information architecture this implements, and `docs/product/frontend-prototype-notes.md`
for the current production-backed vs. prototype-only classification of every screen).

Stack: Vite + React 19 + TypeScript + React Router, plain CSS — no component/state library, no SSR
framework. This is the smallest reversible choice for a client-only UX prototype with no backend of
its own (see the design docs above for why).

## Authentication — real, production-backed

Auth uses the **same Supabase project and the same account system as Android** — there is no
separate Web user system and no second user identifier. A TBDFit account created on Android (or
vice versa) works here too, because both clients authenticate against `auth.users` in the same
Supabase project via the same client-safe (anon/publishable) key.

- Sign in is email + password. Sign up is **email + password only** — no username or display name is
  collected or persisted at signup time (see "Identity model" below for why).
- Google sign-in is **not** implemented here: Android's Google sign-in is a native
  Credential-Manager ID-token flow with no browser-OAuth-redirect configuration behind it — there is
  nothing to port to a browser, and a *different* OAuth-redirect Google flow was not asked for.
- Email confirmation is required on the configured project — signing up shows an explicit "check
  your email" state rather than pretending the account is immediately usable.

### Identity model: account creation vs. profile creation

**Account creation and TBDFit profile creation are two separate lifecycle steps.** This is a
**shared, cross-client product decision** (Android/Web/Wear/future Apple), not something specific to
this Web client:

- **Supabase Auth account** (`auth.users`: `email`, `password`, `id`) — created by `signUp()`. Owned
  entirely by Supabase Auth. Carries no TBDFit product data at all — no username, no display name,
  nothing transported through `raw_user_meta_data`.
- **TBDFit profile** (`public.profiles`: `username`, `display_name`) — created explicitly, by the
  signed-in user, after email confirmation. A signed-in account with no `profiles` row is a normal,
  expected, named application state (`ProfileState.MISSING` — see `src/auth/profileState.ts`), not an
  error and not something represented by nullable `username`/`display_name` fields: **a profile row
  is either valid and complete, or it does not exist yet.**
  - **Username** (`public.profiles.username`) — a **required**, unique, syntax-restricted product
    handle (`^[A-Za-z0-9_.]{3,30}$`, case-insensitive uniqueness via the generated
    `normalized_username` column — see `supabase/migrations/20260906120000_create_profiles.sql`). The
    schema is deliberately kept **ready** for a future username-based login (a unique, unambiguous
    mapping to exactly one `auth.users` row, with no dependency on display_name) — but username-based
    login **is not implemented** by this codebase. Resolving a username to an email/user id for
    sign-in would need a server-side lookup that never hands the mapping to the browser directly;
    doing that lookup client-side would leak the username→email/account mapping, which is exactly the
    enumeration/exposure hazard `create_profiles.sql`'s own security invariants (no anon SELECT, no
    public existence check) guard against. That capability is reserved for a separate, later,
    security-reviewed slice.
  - **Display name** (`public.profiles.display_name`) — a non-unique, human-readable, independently
    editable presentation name. **Required** (NOT NULL) — never used as an identifier, ownership key,
    or auth credential.

**TBDFit profile creation is an optional, in-app feature — not an onboarding gate.** Authentication
is the *only* thing that controls access to the authenticated application: a signed-in user with no
`profiles` row yet gets the full app immediately (`/`, `/plan`, `/programs`, ... all work normally —
see `App.tsx`, which checks nothing but auth phase). Profile existence is a concern local to exactly
one route:

- `/profile` + a `profiles` row exists → the normal Profile page.
- `/profile` + no `profiles` row → `src/auth/ProfileSetupForm.tsx`, rendered inline by
  `src/pages/ProfilePage.tsx`.

`src/auth/profileState.ts` derives `LOADING`/`MISSING`/`COMPLETE`/`UNAVAILABLE` from the current
`profiles` query — `ProfilePage.tsx` is its only consumer, deciding locally what to render. Nothing
in `App.tsx` reads this state. Web's Profile Setup form is the only place `public.profiles` is ever
inserted into from Web — an explicit, user-initiated `INSERT` triggered by pressing Save, using the
browser's own authenticated session (no service-role key, no `SECURITY DEFINER` bootstrap, no auth
metadata transport). `src/auth/profileCreation.ts` does the actual insert.

An earlier version of this app used `profileState.ts` as a second, app-wide access gate in
`App.tsx` — signed-in users with no profile were blocked behind a full-screen Profile Setup
interstitial before reaching `/plan`, `/programs`, etc. That was a product-behavior mistake (it
turned an optional feature into mandatory onboarding) and has been removed; the paragraph above is
the corrected, current behavior.

Display Name defaults to mirroring whatever the user types into Username, until they manually edit
Display Name — after that, username edits never overwrite it. If the chosen username was already
claimed by a different account, the `INSERT` fails with `profiles_normalized_username_key`;
`profileCreation.ts` detects this specifically (a real, user-facing conflict) and ProfileSetupForm
shows an inline "Username is already taken" error with the form still open and both fields intact —
there is no dead-end and no redirect away.

An earlier design collected the username at signup time and transported it through
`auth.users.raw_user_meta_data` (`options.data.desired_username`) for an automatic post-confirmation
bootstrap insert. That has been removed: it conflated Supabase Auth account creation with TBDFit
profile creation, and made a signup-time username conflict an asynchronous, unrecoverable dead-end
(discovered only after email confirmation, with no form left open to retry in) instead of an ordinary
recoverable form validation. Android still collects a username during its own onboarding
(`ProfileCompletionScreen`) and defaults `display_name` to the same value — that is Android's own,
unrelated, already-established flow, unaffected by this change.

### Environment setup

Copy the example file and fill in the same project values Android already uses:

```sh
cp .env.example .env
```

```env
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<the same value as android/local.properties' SUPABASE_ANON_KEY>
```

`.env` is gitignored — never commit real values. Both variables must use the `VITE_` prefix for
Vite to expose them to client code (see `src/vite-env.d.ts`).

### Required Supabase Dashboard configuration (not done by this change)

Android's confirmation-email deep link (`tbdfit://auth-callback`) is already registered in the
Supabase Dashboard. **Web needs its own redirect URL added separately** — this was *not* done as
part of building this prototype (no dashboard access from this environment):

- **Authentication → URL Configuration → Redirect URLs**: add your local dev origin, e.g.
  `http://localhost:5173` (Vite's default dev port), and later whatever origin Web is actually
  deployed to.

Without this, a real confirmation-link click will redirect to a URL Supabase rejects. Sign-in for an
already-confirmed account is unaffected by this — it's specifically the sign-up confirmation flow.

## Development

```sh
npm install
npm run dev       # http://localhost:5173
npm run build     # tsc -b && vite build
npm run lint      # oxlint
npm run test      # vitest run
```

## Icons

```text
Standard UI icons (create/edit/delete/copy/search/chevron/settings/user/calendar/...)
→ lucide-react

TBDFit-specific / fitness-domain concepts with no good Lucide match, or unique brand/decorative
artwork (e.g. DecorativeCurve.tsx)
→ custom SVG component
```

Use the same icon for the same action everywhere (`Plus` = create, `Pencil` = edit, `Trash2` =
delete, `Copy` = duplicate — never swap these per screen). Icons inherit color via `currentColor`
(no hardcoded fill/stroke) so they follow the surrounding text/button color automatically. For a
primary or ambiguous action, pair the icon with a text label (`<Plus /> New Routine`) rather than
shipping an icon-only button — icon-only is for low-ambiguity controls (close, expand/collapse,
overflow menu). Every icon-only button needs `aria-label`; a purely decorative icon needs
`aria-hidden="true"`. Do not install a second icon library (react-icons, Font Awesome, Heroicons,
Material Icons) — lucide-react plus custom SVG covers both cases.

## Remote live UX testing

Want a trusted friend/family member to click around on their own device while you iterate locally?
See [`docs/development/remote-web-testing.md`](../docs/development/remote-web-testing.md) — a
Cloudflare Quick Tunnel workflow, no router changes, no new infrastructure, existing-account sign-in
only.

## Screens

`Home` (`/`) · `Routine` library and detail/builder (`/plan`, `/plan/:routineId` — route path
predates the "Routine" nav rename and is intentionally unchanged) · `History` and workout detail
(`/history`, `/history/:entryId`) · `Profile` (`/profile`). Navigation is a top header bar (brand +
Home/Routine/History + an account-icon menu with Profile/Sign out), not a sidebar.

## Prototype scope

Auth/session/current-user-identity/sign-out are the only production-backed pieces. Everything else
— Home's dashboard content, the Routine library/builder, History, Progress, and profile
activity/settings — uses representative hardcoded data and local component state, not a real
backend; see `docs/product/frontend-prototype-notes.md` for the exact classification per screen.
Home in particular is a **prototype hypothesis** (an authenticated landing dashboard) layered on top
of `docs/product/web-information-architecture.md`'s actual recommendation (Routine as the landing
page) after direct human UX feedback — it is not yet a revision of that document's decision. Signing
in unlocks the same prototype content for every account; it does not yet scope any of that content
per user.
