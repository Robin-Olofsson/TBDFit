# Remote Live UX Testing (Cloudflare Quick Tunnel)

This is an operational runbook for one narrow workflow: Robin is actively developing TBDFit Web
locally, and a trusted friend/family member wants to click around on their own device while Robin
iterates on the UI. It is not an architecture document, and it is not a deployment guide — see
[ADR-006](../architecture/adr/0006-web-client-technology-stack.md) for the Web stack decision and
[web/README.md](../../web/README.md) for ordinary local development.

## Purpose

Let one trusted tester reach a **running local dev server** over the Internet, temporarily, with:

- no router port forwarding
- no inbound router configuration
- no deliberate LAN-wide Vite binding
- no new hosting infrastructure

This is **not** persistent hosting, a staging environment, or a production deployment. When the
tunnel process stops, the public URL stops working — that's the intended lifecycle, not a
limitation to fix.

## Security model

```text
Trusted tester
      │ HTTPS
      ▼
Cloudflare Quick Tunnel (trycloudflare.com)
      │
      ▼
cloudflared, running locally, outbound connection only
      │
      ▼
http://127.0.0.1:5173  (Vite dev server, loopback-only)
      │
      ▼
TBDFit React app  →  Supabase Auth + RLS
```

- **cloudflared makes outbound-only connections** to Cloudflare's edge — nothing is opened on your
  router, and no inbound Windows Firewall rule is needed for this to work.
- **Vite keeps its default `localhost`-only bind.** It is never started with `--host` / `0.0.0.0`
  for this workflow. Nothing on your LAN can reach it either way — cloudflared reaches it locally.
- **The generated `https://<random-words>.trycloudflare.com` URL is a temporary address, not a
  password.** While the tunnel is running, anyone who obtains that URL can reach the TBDFit
  `/login` screen. The actual protection for private data is unchanged from ordinary local
  development: **Supabase Auth + Row Level Security**, subject to the correctness of those
  policies — the same boundary that protects the app the rest of the time. Treat the URL as public
  for as long as the tunnel is up.
- This is development tooling. The Vite dev server itself is not hardened the way a production
  build would be — running it through a tunnel does not change that; it only removes the "your
  home IP is a standing scan target" problem that router port forwarding would introduce.

## Prerequisites

- TBDFit Web already set up per [web/README.md](../../web/README.md) (`web/.env` populated,
  `npm install` done).
- [`cloudflared`](https://github.com/cloudflare/cloudflared) installed on the machine running the
  dev server. On Windows:

  ```powershell
  winget install --id Cloudflare.cloudflared
  ```

  Verify:

  ```powershell
  cloudflared --version
  ```

  No Cloudflare account is required for a Quick Tunnel. Nothing about this workflow needs to be
  committed to the repository — `cloudflared` is a machine-local tool, not a project dependency (it
  is not, and should not become, an npm package or a checked-in binary).

## Start TBDFit

```sh
cd web
npm run dev
```

Leave this running. It behaves exactly as it does for ordinary solo development — nothing about
this workflow changes what `npm run dev` does.

## Start the Quick Tunnel

In a second terminal:

```sh
cloudflared tunnel --url http://localhost:5173 --http-host-header "localhost:5173"
```

`cloudflared` will print a line like:

```text
https://random-two-words.trycloudflare.com
```

Send that URL to your tester. That's the whole setup — two terminals, no repository changes
required for the common case.

**Why `--http-host-header` is here:** Vite's dev server checks the incoming request's `Host` header
against an allow list (`server.allowedHosts`) and rejects unfamiliar hosts by default — a real
protection against DNS-rebinding attacks, not something to disable. `cloudflared`'s
`--http-host-header` flag rewrites the `Host` header it sends to your local origin, so Vite always
sees `localhost:5173` regardless of the public `trycloudflare.com` hostname the tester's browser
used. This means **no change to `vite.config.ts` is needed** — Vite's host protection stays fully
intact. (Confirmed as a real `cloudflared` proxy flag via Cloudflare's own
[Origin parameters](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/cloudflared-parameters/origin-parameters/)
reference; not exercised end-to-end against a live public tunnel from this environment — verify the
first time you use it, per the checklist below.)

If you ever see Vite log `Blocked request. This host is not allowed`, see
[Troubleshooting](#troubleshooting) below for the fallback approach — do not reach for
`allowedHosts: true` or a wildcard.

## Share the URL

The hostname is random and temporary — generated fresh by Cloudflare each time you start a Quick
Tunnel, and only valid while that `cloudflared` process keeps running. Don't bookmark it for later,
don't put it in a commit, and don't rely on it still working tomorrow.

## Live HMR

The goal of using a live tunnel (instead of, say, deploying a preview) is that your tester sees UI
changes as you make them:

```text
you edit a component or CSS file
→ Vite HMR rebuilds
→ the change reaches the tester's browser over the same tunnel connection
```

Cloudflare Tunnel proxies WebSocket traffic by default, with no extra configuration on Cloudflare's
side — HMR's update channel is a WebSocket, so it should traverse the tunnel the same way an
ordinary page load does. **This has not been exercised against a real public tunnel and a real
remote browser in this environment** (no outbound network access to Cloudflare's edge from this
sandbox, and no second machine to act as the tester) — treat it as expected-per-Cloudflare's-own
architecture, not confirmed. The first live session is the actual verification; see the manual
checklist below. If HMR ever proves unreliable in practice, a manual browser refresh on the
tester's end is an acceptable fallback — that's a real, working experience, just not an automatic
one.

## Authentication

This workflow supports **signing in with an existing, already-confirmed TBDFit account only.**
Do not hand out real credentials in chat, source control, or any document — that's outside this
guide's scope; use whatever account-sharing approach you already trust.

No Supabase configuration change is needed for this: `signInWithPassword` (what an ordinary
email/password sign-in calls) does not redirect anywhere, so there is no redirect URL for the
tunnel's temporary hostname to break. Session restore, navigation, and sign-out all work through
the tunnel exactly as they do on `localhost`, because the app itself doesn't know it's being
accessed through a tunnel — it's the same page.

## Known limitation — signup / email confirmation

**Do not use this workflow for testing new-account signup.** Quick Tunnel hostnames are random and
temporary — a fresh one is generated on every run. Supabase's confirmation-email link redirects
back to whatever origin was active when `signUp` was called; if the tunnel is restarted (or the
session ends) before the tester clicks that link, the confirmation redirect lands on a hostname
that no longer routes anywhere.

This is **accepted**, not a bug to fix: the whole point of Quick Tunnel is zero setup, and a stable
hostname is exactly what a Named Tunnel or a real deployment would provide instead — deliberately
out of scope for this narrow workflow. If full remote signup/confirmation testing becomes a real
need later, that's a reason to revisit [the deployment research](../product/) that led here, not to
patch around Quick Tunnel.

## End the session

<kbd>Ctrl</kbd>+<kbd>C</kbd> in the `cloudflared` terminal. The public URL stops responding
immediately. The Vite dev server can keep running locally, or be stopped too — that's independent.

## Security checklist

- [ ] No router port forwarding, no inbound router rule of any kind
- [ ] Vite bound to its default `localhost` — never started with `--host` / `0.0.0.0` for this
- [ ] `server.allowedHosts` never set to `true`; no wildcard host added
- [ ] `server.cors` untouched (not set to `true`)
- [ ] `server.fs.strict` untouched (stays enabled — no filesystem exposure beyond the project)
- [ ] Only the browser-safe `VITE_SUPABASE_ANON_KEY` reaches the app — no service-role key, no DB
      password, ever, in `web/`
- [ ] Supabase Auth + RLS remain the actual data boundary — the tunnel URL is not a credential
- [ ] Only existing, already-confirmed test accounts are used over the tunnel
- [ ] The tunnel is stopped when the session ends
- [ ] No tunnel credentials, hostnames, or logs are committed to the repository (a Quick Tunnel
      creates none locally — no `cloudflared` config file is written for this command form)

## Troubleshooting

**`Blocked request. This host is not allowed` in the Vite terminal.** Means the `Host` header Vite
received didn't match its allow list — check that `--http-host-header "localhost:5173"` is present
on the `cloudflared` command exactly as shown above. If it's present and this still happens,
Vite has an official escape hatch that avoids editing `vite.config.ts` or hardcoding the random
hostname into source: set the environment variable
[`__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS`](https://github.com/vitejs/vite/commit/4d88f6c9391f96275b1359f1343ee2ec3e1adb7b)
to the tunnel's printed hostname before starting `npm run dev`, e.g. (PowerShell):

```powershell
$env:__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS = "random-two-words.trycloudflare.com"
npm run dev
```

This only needs to be set for that one session (a new hostname means a new value next time) — it
is never written into a committed file.

**`cloudflared: command not found` / not recognized.** Reinstall with
`winget install --id Cloudflare.cloudflared`, then open a new terminal so the updated `PATH` is
picked up.

**Tester's page can't connect / HMR seems disconnected.** Confirm `npm run dev` is still running in
its terminal (Vite must be up before `cloudflared` has anything to proxy to) and that the
`cloudflared` terminal hasn't errored out or been closed — a Quick Tunnel dies with its process.
Reconnects after a network blip are the tunnel's job, not something to configure; a stale connection
is usually fixed by the tester reloading the page.

**Sign-in fails for the tester.** Confirm they're using a real, already-confirmed account — this
workflow does not support new signups (see [Known limitation](#known-limitation--signup--email-confirmation)
above). A generic "invalid credentials" message covers both a wrong password and an unconfirmed
account; there's nothing tunnel-specific to check here.
