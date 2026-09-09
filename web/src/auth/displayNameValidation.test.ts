import { describe, expect, it } from 'vitest'
import { DISPLAY_NAME_MAX_LENGTH, displayNameValidationError } from './displayNameValidation'

describe('displayNameValidationError', () => {
  it('accepts an ordinary name with spaces', () => {
    expect(displayNameValidationError('Robin Olofsson')).toBeNull()
  })

  it('accepts a name identical to a valid username', () => {
    expect(displayNameValidationError('robin_92')).toBeNull()
  })

  it('rejects an empty string', () => {
    expect(displayNameValidationError('')).toBe('Enter a display name.')
  })

  it('rejects a whitespace-only string', () => {
    expect(displayNameValidationError('   ')).toBe('Enter a display name.')
  })

  it(`accepts exactly ${DISPLAY_NAME_MAX_LENGTH} characters`, () => {
    expect(displayNameValidationError('a'.repeat(DISPLAY_NAME_MAX_LENGTH))).toBeNull()
  })

  it(`rejects ${DISPLAY_NAME_MAX_LENGTH + 1} characters`, () => {
    expect(displayNameValidationError('a'.repeat(DISPLAY_NAME_MAX_LENGTH + 1))).toBe(
      `Display name must be ${DISPLAY_NAME_MAX_LENGTH} characters or fewer.`,
    )
  })

  it('accepts names with punctuation the username regex would reject', () => {
    expect(displayNameValidationError("O'Brien-Smith")).toBeNull()
  })
})
