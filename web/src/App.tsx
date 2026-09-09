import { Navigate, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage'
import PlanPage from './pages/PlanPage'
import RoutineDetailPage from './pages/RoutineDetailPage'
import HistoryPage from './pages/HistoryPage'
import WorkoutDetailPage from './pages/WorkoutDetailPage'
import ProfilePage from './pages/ProfilePage'
import LoginPage from './pages/LoginPage'
import TopNav from './components/TopNav'
import AccountMenu from './components/AccountMenu'
import { useAuth } from './auth/AuthContext'
import { useOwnProfile } from './auth/useOwnProfile'
import { isAuthenticatedPhase, requiresAuthScreen } from './auth/authPhase'

// Authenticated boundary (real, production-backed — see docs/product/frontend-prototype-notes.md):
// the product shell below only ever renders once a real Supabase session exists. This is a decision
// about the CURRENT private prototype shell only, not a permanent rule that every future TBDFit Web
// route requires auth — a public athlete profile / published workout / share link is an explicitly
// open product question (see web-information-architecture.md), unaffected by this gate.
export default function App() {
  const { phase } = useAuth()

  if (phase === 'RESTORING_SESSION') {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <div className="auth-brand">TBDFit</div>
          <p className="page-subtitle">Restoring your session…</p>
        </div>
      </div>
    )
  }

  if (phase === 'AUTH_ERROR') {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <div className="auth-brand">TBDFit</div>
          <h1 className="auth-title">Couldn't restore your session</h1>
          <p className="page-subtitle">Reload the page to try again.</p>
        </div>
      </div>
    )
  }

  // /login is a real route (not an implicit fallback): while signed out/awaiting confirmation,
  // every other path redirects there. This is what lets App.tsx's own "redirect an authenticated
  // visit to /login → /" rule (in AuthenticatedShell's routes below) and this rule compose into
  // a real address a developer can navigate to directly, per the review brief's routing requirement.
  if (requiresAuthScreen(phase)) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  // Only remaining phase is SIGNED_IN, but the explicit check (rather than a bare else) keeps this
  // gate honest if a phase is ever added later without updating this function.
  return isAuthenticatedPhase(phase) ? <AuthenticatedShell /> : null
}

// Nav shape: a compact top header bar, not the sidebar this prototype used previously — direct
// human UX feedback found the sidebar read as "generic admin dashboard" next to the approved
// /login reference's laptop mockup, which uses a top bar. See frontend-prototype-notes.md for the
// full before/after. Nav items are Home/Plan/History (see TopNav.tsx for the sliding active-item
// indicator, also direct human UX feedback) — Profile is deliberately NOT a nav item here any more;
// it lives behind the account icon's menu instead (see AccountMenu.tsx), along with Sign out. Feed
// stays deferred (Open Decision #6 in web-information-architecture.md) and is intentionally not
// shown at all (it was a disabled item in the old sidebar; dropping it from a compact top bar reads
// cleaner than a disabled pill, and nothing about its own deferred status changes). The former
// "UX Prototype" pill was removed from the bar entirely, per the same feedback — the prototype
// framing lives in docs/product/frontend-prototype-notes.md, not as in-app chrome.
//
// No search control: the reference image shows one, but nothing in this prototype (three sample
// routines, three sample history entries) is large enough to need searching — adding it would be a
// control with nothing behind it, which the review brief explicitly warns against.
function AuthenticatedShell() {
  const { session, signOut } = useAuth()
  const { username } = useOwnProfile()
  const email = session?.user.email ?? ''
  // Real identity data only: username if a real `profiles` row exists for this account (same table
  // Android reads — see useOwnProfile.ts), otherwise the account's email — never invented content.
  const label = username ?? email
  const initial = (label || '?').charAt(0).toUpperCase()

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-left">
          <div className="topbar-brand">
            <span className="login-logo-mark" aria-hidden="true">
              B
            </span>
            TBD<span className="login-logo-accent">Fit</span>
          </div>
          <TopNav />
        </div>
        <div className="topbar-right">
          <AccountMenu initial={initial} onSignOut={() => void signOut()} />
        </div>
      </header>
      <main className="content-area">
        <Routes>
          <Route path="/" element={<HomePage />} />
          {/* An authenticated visit to /login (a stale bookmark, the back button) never shows the
              login page "on top of" a real session — it goes straight to the authenticated entry
              route instead, per the review brief's routing requirement. Home (not /plan) is now
              that entry route — see HomePage's own doc comment for why. */}
          <Route path="/login" element={<Navigate to="/" replace />} />
          <Route path="/plan" element={<PlanPage />} />
          <Route path="/plan/:routineId" element={<RoutineDetailPage />} />
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/history/:entryId" element={<WorkoutDetailPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Routes>
      </main>
    </div>
  )
}
