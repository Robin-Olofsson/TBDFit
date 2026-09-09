import { describe, expect, it } from 'vitest'
import { mapAuthError } from './authErrors'

describe('mapAuthError', () => {
  it('maps known Supabase error codes to safe, plain messages', () => {
    expect(mapAuthError({ code: 'invalid_credentials' })).toBe('Incorrect email or password.')
    expect(mapAuthError({ code: 'email_not_confirmed' })).toBe('Please confirm your email before signing in.')
    expect(mapAuthError({ code: 'user_already_exists' })).toBe(
      'An account with this email may already exist. Try signing in instead.',
    )
    expect(mapAuthError({ code: 'weak_password' })).toBe('Password is too weak.')
  })

  it('never leaks a raw Supabase error message for an unrecognized code', () => {
    const raw = { code: 'some_future_error_code', message: 'a very Supabase-specific internal detail' }
    const result = mapAuthError(raw)
    expect(result).not.toContain('Supabase-specific')
    expect(result).toBe('Something went wrong. Please try again.')
  })

  it('distinguishes a network-like failure from a generic one', () => {
    const networkError = new Error('Failed to fetch')
    expect(mapAuthError(networkError)).toBe("Couldn't reach the server. Check your connection and try again.")

    class AuthRetryableFetchError extends Error {
      constructor() {
        super('retryable')
        this.name = 'AuthRetryableFetchError'
      }
    }
    expect(mapAuthError(new AuthRetryableFetchError())).toBe(
      "Couldn't reach the server. Check your connection and try again.",
    )
  })

  it('falls back to a generic message for a non-error, code-less value', () => {
    expect(mapAuthError(null)).toBe('Something went wrong. Please try again.')
    expect(mapAuthError('a plain string')).toBe('Something went wrong. Please try again.')
  })
})
