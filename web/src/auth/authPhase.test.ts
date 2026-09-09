import { describe, expect, it } from 'vitest'
import { isAuthenticatedPhase, requiresAuthScreen } from './authPhase'

describe('isAuthenticatedPhase', () => {
  it('is true only for SIGNED_IN — the private shell must not render otherwise', () => {
    expect(isAuthenticatedPhase('SIGNED_IN')).toBe(true)
    expect(isAuthenticatedPhase('SIGNED_OUT')).toBe(false)
    expect(isAuthenticatedPhase('RESTORING_SESSION')).toBe(false)
    expect(isAuthenticatedPhase('AWAITING_CONFIRMATION')).toBe(false)
    // Auth failure must never be treated as authenticated.
    expect(isAuthenticatedPhase('AUTH_ERROR')).toBe(false)
  })
})

describe('requiresAuthScreen', () => {
  it('is true for SIGNED_OUT and AWAITING_CONFIRMATION only', () => {
    expect(requiresAuthScreen('SIGNED_OUT')).toBe(true)
    expect(requiresAuthScreen('AWAITING_CONFIRMATION')).toBe(true)
    expect(requiresAuthScreen('SIGNED_IN')).toBe(false)
    expect(requiresAuthScreen('RESTORING_SESSION')).toBe(false)
    // AUTH_ERROR shows its own distinct screen (see App.tsx), not the sign-in form.
    expect(requiresAuthScreen('AUTH_ERROR')).toBe(false)
  })
})
