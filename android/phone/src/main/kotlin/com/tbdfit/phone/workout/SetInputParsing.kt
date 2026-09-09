package com.tbdfit.phone.workout

// Pure parsing for the weight/reps text fields in ActiveWorkoutScreen — kept separate from Compose
// so "user types → parse valid value → persist, but never silently convert malformed input to
// zero" is unit-testable without a Compose test harness (this codebase has none), the same pattern
// already used for isValidEmail/isEmailShaped in EmailAuthScreen.kt.
//
// Three, and only three, outcomes are possible for either field:
//  - blank input is a deliberate, intentional null (e.g. clearing a bodyweight exercise's weight)
//  - a valid non-negative number is persisted as-is
//  - anything else (malformed text, a negative number) is Invalid and must NOT be persisted —
//    the caller keeps showing whatever the user typed and simply does not write to Room until it
//    becomes valid or blank again.
internal sealed interface SetFieldInput<out T> {
    data class Persist<T>(val value: T?) : SetFieldInput<T>
    data object Invalid : SetFieldInput<Nothing>
}

internal fun parseWeightInput(text: String): SetFieldInput<Double> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return SetFieldInput.Persist(null)
    val value = trimmed.toDoubleOrNull() ?: return SetFieldInput.Invalid
    if (value < 0.0) return SetFieldInput.Invalid
    return SetFieldInput.Persist(value)
}

internal fun parseRepsInput(text: String): SetFieldInput<Int> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return SetFieldInput.Persist(null)
    val value = trimmed.toIntOrNull() ?: return SetFieldInput.Invalid
    if (value < 0) return SetFieldInput.Invalid
    return SetFieldInput.Persist(value)
}
