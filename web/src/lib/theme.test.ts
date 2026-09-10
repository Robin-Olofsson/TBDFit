import { describe, expect, it } from 'vitest'
import { resolveColorScheme } from './theme'

// Pure-logic test only (see theme.ts's own doc comment) — getAppColorScheme's DOM read
// (getComputedStyle) is not exercised here, per this project's no-jsdom convention; verified
// instead by direct code reading and a real build.
describe('resolveColorScheme', () => {
  it('resolves a plain "dark" computed value to dark', () => {
    expect(resolveColorScheme('dark')).toBe('dark')
  })

  it('resolves a plain "light" computed value to light', () => {
    expect(resolveColorScheme('light')).toBe('light')
  })

  it('resolves a dual-keyword value ("light dark") to light — the browser lists its own preferred scheme first', () => {
    expect(resolveColorScheme('light dark')).toBe('light')
  })

  it('defaults to dark for an unset/empty computed value, matching this app\'s actual unconditional-dark design', () => {
    expect(resolveColorScheme('')).toBe('dark')
  })
})
