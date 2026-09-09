import { describe, expect, it } from 'vitest'
import { USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH, usernameValidationError } from './usernameValidation'

// Pure-logic tests only, matching this codebase's existing convention (no jsdom/component-rendering
// harness — see routines.test.ts's own doc comment). This is the client-side half of
// profiles_username_syntax (supabase/migrations/20260906120000_create_profiles.sql) — the
// database-level equivalents of every case below (including cross-account uniqueness) are exercised
// for real against a local Postgres instance (see the Username + Display Name revision's own
// verification report; not repeated here as a mock).
describe('usernameValidationError', () => {
  it('accepts an ordinary alphanumeric username', () => {
    expect(usernameValidationError('robin_o')).toBeNull()
  })

  it('accepts a username with a period', () => {
    expect(usernameValidationError('robin.olofsson')).toBeNull()
  })

  it('rejects an empty string', () => {
    expect(usernameValidationError('')).toBe('Enter a username.')
  })

  it('rejects a whitespace-only string', () => {
    expect(usernameValidationError('   ')).toBe('Enter a username.')
  })

  it('rejects a username below the minimum length', () => {
    expect(usernameValidationError('a'.repeat(USERNAME_MIN_LENGTH - 1))).toBe(
      `Username must be at least ${USERNAME_MIN_LENGTH} characters.`,
    )
  })

  it('accepts a username exactly at the minimum length', () => {
    expect(usernameValidationError('a'.repeat(USERNAME_MIN_LENGTH))).toBeNull()
  })

  it('accepts a username exactly at the max length', () => {
    expect(usernameValidationError('a'.repeat(USERNAME_MAX_LENGTH))).toBeNull()
  })

  it('rejects a username one character over the max length', () => {
    const error = usernameValidationError('a'.repeat(USERNAME_MAX_LENGTH + 1))
    expect(error).toBe(`Username must be ${USERNAME_MAX_LENGTH} characters or fewer.`)
  })

  it('rejects characters outside letters/digits/underscore/period', () => {
    expect(usernameValidationError('robin olofsson')).toBe(
      'Usernames can only contain letters, numbers, underscores, and periods.',
    )
    expect(usernameValidationError('robin@o')).toBe(
      'Usernames can only contain letters, numbers, underscores, and periods.',
    )
  })

  it('trims surrounding whitespace before checking length/syntax', () => {
    expect(usernameValidationError('  robin_o  ')).toBeNull()
  })
})
