import AuthForm from '../auth/AuthForm'
import AuthHero from '../auth/AuthHero'

// The TBDFit Web `/login` page — restyled to closely match an approved visual reference (a
// left auth panel / right marketing-hero split). Real Supabase auth logic lives entirely in
// AuthContext/AuthForm; this page only composes real, responsive components around it — never an
// embedded screenshot. See docs/product/frontend-prototype-notes.md for the production-backed vs.
// prototype-only classification: auth itself is real, the hero/product-preview content is not.
//
// This component is only ever mounted while phase is SIGNED_OUT/AWAITING_CONFIRMATION — see
// App.tsx's routing, which renders the authenticated shell (never this page) once a session
// exists, and separately redirects a signed-in visit to /login straight to /plan. No redundant
// "already signed in" check is needed here as a result.
export default function LoginPage() {
  return (
    <div className="login-page">
      <div className="login-body">
        <section className="login-panel">
          <AuthForm />
        </section>
        <AuthHero />
      </div>
      <footer className="login-footer">
        <span>© 2026 TBDFit. All rights reserved.</span>
        <span className="login-footer-links">
          <span className="footer-inert">Terms</span>
          <span className="footer-inert">Privacy</span>
          <span className="footer-inert">Contact</span>
        </span>
      </footer>
    </div>
  )
}
