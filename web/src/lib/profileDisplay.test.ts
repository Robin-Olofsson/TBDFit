import { describe, expect, it } from 'vitest'
import { getInitials } from './profileDisplay'

describe('getInitials', () => {
  it('takes the first letter of the first and last word for a two-word name', () => {
    expect(getInitials('Robin Olofsson')).toBe('RO')
  })

  it('uses the first and last word for a three-word name, ignoring the middle', () => {
    expect(getInitials('Robin Anders Olofsson')).toBe('RO')
  })

  it('falls back to a single letter for a single-word name', () => {
    expect(getInitials('Robin')).toBe('R')
  })

  it('collapses repeated whitespace between words', () => {
    expect(getInitials('Robin   Olofsson')).toBe('RO')
  })

  it('uppercases lowercase input', () => {
    expect(getInitials('robin olofsson')).toBe('RO')
  })

  it('returns a placeholder for blank input rather than throwing', () => {
    expect(getInitials('   ')).toBe('?')
  })
})
