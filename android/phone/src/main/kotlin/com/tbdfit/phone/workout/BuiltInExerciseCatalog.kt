package com.tbdfit.phone.workout

// A curated V1 general-purpose built-in seed catalog (Push/Pull/Legs, Upper/Lower, general
// strength/hypertrophy — see supabase/migrations/20260916120000_expand_builtin_exercise_catalog.sql)
// — see the strength-workout design doc's Seed Catalog Lifecycle section: IDs are fixed,
// hand-assigned slugs in their own reserved namespace (never random UUIDs, never reassigned even if
// a name changes later). This is NOT a promoted product decision about final catalog scope/content —
// Exercise identity/catalog strategy remains an open human decision in that design doc. Broad
// canonical exercises only, no grip/angle/variant explosion — a user wanting a specific variant
// remains free to create it as a custom exercise.
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
    // Expanded V1 catalog (see supabase/migrations/20260916120000_expand_builtin_exercise_catalog.sql)
    // — same canonical ids/names as the Supabase side, matched by hand, never generated. Android has
    // no exercise_type/equipment concept, so parity here is id+name only.
    "builtin_incline_dumbbell_press" to "Incline Dumbbell Press",
    "builtin_dumbbell_bench_press" to "Dumbbell Bench Press",
    "builtin_push_up" to "Push-Up",
    "builtin_dip" to "Dip",
    "builtin_lat_pulldown" to "Lat Pulldown",
    "builtin_seated_cable_row" to "Seated Cable Row",
    "builtin_dumbbell_row" to "Dumbbell Row",
    "builtin_face_pull" to "Face Pull",
    "builtin_dumbbell_shoulder_press" to "Dumbbell Shoulder Press",
    "builtin_dumbbell_lateral_raise" to "Dumbbell Lateral Raise",
    "builtin_romanian_deadlift" to "Romanian Deadlift",
    "builtin_leg_press" to "Leg Press",
    "builtin_leg_extension" to "Leg Extension",
    "builtin_leg_curl" to "Leg Curl",
    "builtin_bulgarian_split_squat" to "Bulgarian Split Squat",
    "builtin_barbell_hip_thrust" to "Barbell Hip Thrust",
    "builtin_barbell_curl" to "Barbell Curl",
    "builtin_dumbbell_curl" to "Dumbbell Curl",
    "builtin_hammer_curl" to "Hammer Curl",
    "builtin_triceps_pushdown" to "Triceps Pushdown",
    "builtin_cable_crunch" to "Cable Crunch",
    "builtin_hanging_leg_raise" to "Hanging Leg Raise",
    "builtin_standing_calf_raise" to "Standing Calf Raise",
    "builtin_seated_calf_raise" to "Seated Calf Raise",
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
