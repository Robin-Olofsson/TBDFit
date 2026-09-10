// Pure presentation helpers for the Profile header — split out from ProfilePage.tsx so they're
// unit-testable without a component-rendering harness (this project deliberately has neither jsdom
// nor a component-testing library — see routines.test.ts's own doc comment).

// "Robin Olofsson" -> "RO" (first letter of the first word + first letter of the last word).
// A single-word name ("Robin") falls back to just its first letter ("R") rather than repeating it —
// there is no second word to take a letter from. Never throws on empty/whitespace-only input; the
// caller (ProfilePage) is expected to only reach this once a COMPLETE profile guarantees a non-blank
// displayName, but this stays defensive regardless since it has no other invariant to lean on.
export function getInitials(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return '?'
  if (words.length === 1) return words[0].charAt(0).toUpperCase()
  return (words[0].charAt(0) + words[words.length - 1].charAt(0)).toUpperCase()
}
