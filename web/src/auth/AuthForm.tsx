import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from './AuthContext'

// The real, production-backed sign-in/sign-up form for /login. Logic is unchanged from the prior
// AuthScreen.tsx (now removed) — this is a restyle to match an approved visual reference, not a
// rewrite of AuthContext's behavior. Only email/password is offered: see web/README.md for why
// Google was not ported to Web (Android's is a native Credential-Manager ID-token flow with no
// browser-OAuth-redirect configuration behind it — there was nothing to port, and a *different*
// browser-OAuth Google flow was out of scope for this task). "Forgot password?" is intentionally
// omitted: no password-recovery method exists anywhere in AuthContext — showing that link would
// point nowhere.
export default function AuthForm() {
  const { phase, pendingConfirmationEmail, returnToSignIn } = useAuth()

  return (
    <div className="login-panel-inner">
      <div className="login-logo">
        <span className="login-logo-mark">B</span>
        <span>
          TBD<span className="login-logo-accent">Fit</span>
        </span>
      </div>
      {phase === 'AWAITING_CONFIRMATION' ? (
        <ConfirmationPending email={pendingConfirmationEmail} onBack={returnToSignIn} />
      ) : (
        <SignInOrUpForm />
      )}
    </div>
  )
}

function ConfirmationPending({ email, onBack }: { email: string | null; onBack: () => void }) {
  return (
    <div>
      <p className="login-eyebrow">Almost there</p>
      <h1 className="login-heading">Check your email</h1>
      <p className="login-subtitle">
        We sent a confirmation link to <strong>{email}</strong>. Follow it to finish creating your
        account, then come back and sign in.
      </p>
      <button type="button" className="btn-link" onClick={onBack}>
        Back to sign in
      </button>
    </div>
  )
}

function SignInOrUpForm() {
  const { signIn, signUp } = useAuth()
  const [mode, setMode] = useState<'sign_in' | 'sign_up'>('sign_in')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isSignIn = mode === 'sign_in'

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (submitting) return // guards against double-submit (e.g. a fast repeated Enter/click)

    setSubmitting(true)
    setError(null)
    // Account creation is Email + Password only — no username/display name field here. TBDFit
    // profile data is collected separately, after SIGNED_IN, by ProfileSetupForm (see
    // profileState.ts): account creation and profile creation are deliberately separate lifecycle
    // steps (see web/README.md's "Identity model").
    const result = isSignIn ? await signIn(email, password) : await signUp({ email, password })
    setSubmitting(false)
    if (!result.ok) setError(result.message ?? 'Something went wrong. Please try again.')
  }

  function switchMode(next: 'sign_in' | 'sign_up') {
    setMode(next)
    setError(null)
  }

  return (
    <>
      <p className="login-eyebrow">{isSignIn ? 'Welcome back' : 'Welcome to the club'}</p>
      <h1 className="login-heading">{isSignIn ? 'Log In' : 'Create your account'}</h1>
      <p className="login-subtitle">
        {isSignIn
          ? 'Track your progress. Build better habits. Be a stronger you.'
          : ''}
      </p>
      <form className="login-form" onSubmit={handleSubmit} noValidate>
        <label className="auth-label" htmlFor="auth-email">
          Email
        </label>
        <input
          id="auth-email"
          className="auth-input"
          type="email"
          autoComplete="email"
          placeholder="you@domain.com"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={submitting}
        />
        <label className="auth-label" htmlFor="auth-password">
          Password
        </label>
        <div className="login-password-field">
          <input
            id="auth-password"
            className="auth-input"
            type={showPassword ? 'text' : 'password'}
            autoComplete={isSignIn ? 'current-password' : 'new-password'}
            placeholder="Enter your password"
            required
            minLength={6}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={submitting}
          />
          <button
            type="button"
            className="login-password-toggle"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            aria-pressed={showPassword}
          >
            {showPassword ? 'Hide' : 'Show'}
          </button>
        </div>
        {error && (
          <p className="auth-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn-primary login-submit" disabled={submitting}>
          {submitting ? 'Please wait…' : isSignIn ? 'Log In' : 'Create account'}
        </button>
      </form>
      <p className="login-switch">
        {isSignIn ? (
          <>
            New to TBDFit?{' '}
            <button type="button" className="btn-link login-switch-link" onClick={() => switchMode('sign_up')}>
              Sign Up
            </button>
          </>
        ) : (
          <button type="button" className="btn-link login-switch-link" onClick={() => switchMode('sign_in')}>
            Already have an account? Log In
          </button>
        )}
      </p>
    </>
  )
}
