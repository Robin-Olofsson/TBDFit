import { describe, expect, it } from 'vitest'
import { deriveProfileState } from './profileState'

const loadingSnapshot = { username: null, displayName: null, loading: true, isError: false }
const missingSnapshot = { username: null, displayName: null, loading: false, isError: false }
const completeSnapshot = { username: 'robin_92', displayName: 'Robin Olofsson', loading: false, isError: false }
const errorSnapshot = { username: null, displayName: null, loading: false, isError: true }

describe('deriveProfileState', () => {
  it('is LOADING while not yet SIGNED_IN, regardless of the profile query snapshot', () => {
    expect(deriveProfileState('RESTORING_SESSION', completeSnapshot)).toEqual({ status: 'LOADING' })
    expect(deriveProfileState('SIGNED_OUT', completeSnapshot)).toEqual({ status: 'LOADING' })
    expect(deriveProfileState('AWAITING_CONFIRMATION', completeSnapshot)).toEqual({ status: 'LOADING' })
    expect(deriveProfileState('AUTH_ERROR', completeSnapshot)).toEqual({ status: 'LOADING' })
  })

  it('is LOADING while SIGNED_IN and the profile query is still in flight', () => {
    expect(deriveProfileState('SIGNED_IN', loadingSnapshot)).toEqual({ status: 'LOADING' })
  })

  it('is MISSING when SIGNED_IN and no profiles row exists — not an error', () => {
    expect(deriveProfileState('SIGNED_IN', missingSnapshot)).toEqual({ status: 'MISSING' })
  })

  it('is COMPLETE when SIGNED_IN and a full profile row exists', () => {
    expect(deriveProfileState('SIGNED_IN', completeSnapshot)).toEqual({
      status: 'COMPLETE',
      username: 'robin_92',
      displayName: 'Robin Olofsson',
    })
  })

  it('is UNAVAILABLE when SIGNED_IN and the profile query genuinely failed', () => {
    expect(deriveProfileState('SIGNED_IN', errorSnapshot)).toEqual({ status: 'UNAVAILABLE' })
  })

  it('prefers UNAVAILABLE over MISSING when a real error occurred', () => {
    // isError=true alongside null username/displayName must never be read as "no profile yet".
    expect(deriveProfileState('SIGNED_IN', { username: null, displayName: null, loading: false, isError: true })).toEqual({
      status: 'UNAVAILABLE',
    })
  })
})
