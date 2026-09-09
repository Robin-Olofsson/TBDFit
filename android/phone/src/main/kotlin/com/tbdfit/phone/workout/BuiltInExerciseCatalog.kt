package com.tbdfit.phone.workout

// The tiny, deliberately small built-in seed catalog for this slice — see the strength-workout
// design doc's Seed Catalog Lifecycle section: IDs are fixed, hand-assigned slugs in their own
// reserved namespace (never random UUIDs, never reassigned even if a name changes later). This is
// NOT a promoted product decision about catalog scope/content — Exercise identity/catalog strategy
// remains an open human decision in that design doc. This list exists only to make the exercise
// picker usable for this slice's manual/automated verification, not as a curated final catalog —
// deliberately far short of "dozens/hundreds."
//
// Chose runtime seeding (see seedBuiltInExercisesIfAbsent below) over a Room data-migration for
// this: Room's migration mechanism exists for schema changes, and a migration whose entire body is
// just seed-data INSERTs would require a version bump plus new schema JSON plus a migration test
// for zero schema change — more machinery than an idempotent insert-if-absent routine achieves for
// the same result, equally safely.
internal val BUILT_IN_EXERCISE_SEED: List<Pair<String, String>> = listOf(
    "builtin_bench_press" to "Bench Press",
    "builtin_back_squat" to "Back Squat",
    "builtin_deadlift" to "Deadlift",
    "builtin_overhead_press" to "Overhead Press",
    "builtin_barbell_row" to "Barbell Row",
    "builtin_pull_up" to "Pull-Up",
)

internal suspend fun seedBuiltInExercisesIfAbsent(exerciseDao: ExerciseDao, now: Long = System.currentTimeMillis()) {
    for ((id, name) in BUILT_IN_EXERCISE_SEED) {
        exerciseDao.insertBuiltInIfAbsent(
            ExerciseEntity(
                id = id,
                name = name,
                normalizedName = name.lowercase(),
                source = ExerciseSource.BUILT_IN,
                ownerId = null,
                createdAt = now,
            ),
        )
    }
}
