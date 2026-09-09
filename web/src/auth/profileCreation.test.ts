import { describe, expect, it } from 'vitest'
import { isUsernameConflict } from './profileCreation'

// Pure-logic tests only (this project deliberately has neither jsdom nor a component-testing
// library — see routines.test.ts's own doc comment). The behavior this proves — ProfileSetupForm
// keeps the form open with an inline "Username is already taken" error on conflict — is exercised
// for real against a local Postgres instance (see
// docs/development/supabase-setup-and-verification.md's "Shared username + display name" section);
// this file proves only the pure classification `createOwnProfile` relies on to trigger that UI.
describe('isUsernameConflict', () => {
  it('is true for a 23505 violating profiles_normalized_username_key', () => {
    expect(
      isUsernameConflict({
        code: '23505',
        message: 'duplicate key value violates unique constraint "profiles_normalized_username_key"',
      }),
    ).toBe(true)
  })

  it('is true when the constraint name only appears in details, not message', () => {
    expect(
      isUsernameConflict({
        code: '23505',
        message: 'duplicate key value violates unique constraint',
        details: 'Key (normalized_username)=(robin) already exists.',
      }),
    ).toBe(true)
  })

  it('is false for a 23505 violating a different constraint (e.g. profiles_pkey)', () => {
    expect(
      isUsernameConflict({
        code: '23505',
        message: 'duplicate key value violates unique constraint "profiles_pkey"',
      }),
    ).toBe(false)
  })

  it('is false for a non-23505 error', () => {
    expect(isUsernameConflict({ code: '23514', message: 'violates check constraint "profiles_username_syntax"' })).toBe(false)
  })

  it('is false when code is missing entirely', () => {
    expect(isUsernameConflict({ message: 'network error' })).toBe(false)
  })
})
