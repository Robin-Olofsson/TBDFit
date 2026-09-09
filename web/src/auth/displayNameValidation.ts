// Client-side mirror of the database's display_name CHECK constraints (see
// supabase/migrations/20260912120000_add_profile_display_name.sql:
// profiles_display_name_not_blank / profiles_display_name_length) — a small pure function, the same
// pattern as usernameValidation.ts, so it is unit-testable without a component-rendering harness
// (this project deliberately has neither jsdom nor a component-testing library — see
// routines.test.ts's own doc comment). Deliberately does NOT reuse usernameValidation's restrictive
// `[A-Za-z0-9_.]{3,30}` regex — display_name is a free-form human-readable name ("Robin Olofsson"),
// not a syntax-restricted handle; only a non-blank/length bound applies.
export const DISPLAY_NAME_MAX_LENGTH = 80

export function displayNameValidationError(rawDisplayName: string): string | null {
  const trimmed = rawDisplayName.trim()
  if (trimmed.length === 0) return 'Enter a display name.'
  if (trimmed.length > DISPLAY_NAME_MAX_LENGTH) return `Display name must be ${DISPLAY_NAME_MAX_LENGTH} characters or fewer.`
  return null
}
