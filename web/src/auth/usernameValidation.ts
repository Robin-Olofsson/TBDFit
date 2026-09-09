// UX-only mirror of the database's authoritative CHECK constraint
// (supabase/migrations/20260906120000_create_profiles.sql, profiles_username_syntax) — kept in sync
// by hand; PostgreSQL remains the actual authority regardless of what this function allows. Mirrors
// Android's own usernameValidationError
// (android/phone/src/main/kotlin/com/tbdfit/phone/profile/UsernameValidation.kt) exactly — same
// regex, same bounds, same error copy — rather than inventing a second, possibly-divergent set of
// rules for the same product-wide field. Case-insensitivity is a login/uniqueness concern (handled
// by the database's generated normalized_username column), not something this function needs to
// enforce. Unicode/confusable handling is deliberately out of scope for V1 in both places — see the
// profile/username research report.
const USERNAME_PATTERN = /^[A-Za-z0-9_.]+$/

export const USERNAME_MIN_LENGTH = 3
export const USERNAME_MAX_LENGTH = 30

export function usernameValidationError(rawUsername: string): string | null {
  const trimmed = rawUsername.trim()
  if (trimmed.length === 0) return 'Enter a username.'
  if (trimmed.length < USERNAME_MIN_LENGTH) return `Username must be at least ${USERNAME_MIN_LENGTH} characters.`
  if (trimmed.length > USERNAME_MAX_LENGTH) return `Username must be ${USERNAME_MAX_LENGTH} characters or fewer.`
  if (!USERNAME_PATTERN.test(trimmed)) return 'Usernames can only contain letters, numbers, underscores, and periods.'
  return null
}
