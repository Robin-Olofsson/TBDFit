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

- Email + password sign-in/sign-up, matching the currently-configured Supabase project (see
  `docs/development/supabase-setup-and-verification.md`).
- Google sign-in is **not** implemented here: Android's Google sign-in is a native
  Credential-Manager ID-token flow with no browser-OAuth-redirect configuration behind it — there is
  nothing to port to a browser, and a *different* OAuth-redirect Google flow was not asked for.
- Email confirmation is required on the configured project — signing up shows an explicit "check
  your email" state rather than pretending the account is immediately usable.

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
