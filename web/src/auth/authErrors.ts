// Pure, dependency-free mapping from a Supabase auth error to a plain, safe, user-facing message —
// mirrors messageFor(AuthErrorCode) in
// android/phone/src/main/kotlin/com/tbdfit/phone/backend/auth/SupabaseAuthGateway.kt, adapted to
// @supabase/supabase-js's actual error shape (an AuthApiError/AuthError with a string `.code`, not
// Android's typed AuthErrorCode enum — confirmed against the installed
// node_modules/@supabase/auth-js source rather than assumed). Kept as a standalone pure function
// specifically so it's unit-testable without a real Supabase client (see authErrors.test.ts) — same
// reasoning as toAuthState in Android's SupabaseAuthGateway.kt.
//
// Never surfaces a raw Supabase error message/object to the UI — every branch below returns a
// fixed, safe string.
export function mapAuthError(error: unknown): string {
  const code = extractCode(error)
  switch (code) {
    case 'invalid_credentials':
      return 'Incorrect email or password.'
    case 'weak_password':
      return 'Password is too weak.'
    case 'email_not_confirmed':
      return 'Please confirm your email before signing in.'
    case 'over_email_send_rate_limit':
    case 'over_request_rate_limit':
      return 'Too many attempts. Please wait and try again.'
    case 'validation_failed':
    case 'bad_json':
    case 'bad_jwt':
      return 'Please check your email and password and try again.'
    case 'user_already_exists':
      return 'An account with this email may already exist. Try signing in instead.'
    case 'email_address_invalid':
      return 'Please enter a valid email address.'
    default:
      break
  }

  // No structured code available — distinguish "we got a response the server rejected" from
  // "we never reached the server at all" the same way SupabaseAuthGateway.runAuthCall's three-tier
  // catch does, without assuming a specific error class (the JS SDK throws plain AuthApiError /
  // AuthRetryableFetchError instances, not a rich exception hierarchy to pattern-match on here).
  if (isNetworkLikeError(error)) {
    return "Couldn't reach the server. Check your connection and try again."
  }
  return 'Something went wrong. Please try again.'
}

function extractCode(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = (error as { code?: unknown }).code
    return typeof code === 'string' ? code : undefined
  }
  return undefined
}

function isNetworkLikeError(error: unknown): boolean {
  if (!(error instanceof Error)) return false
  // AuthRetryableFetchError's name; also catches a raw fetch TypeError ("Failed to fetch").
  return error.name === 'AuthRetryableFetchError' || /fetch/i.test(error.message)
}
