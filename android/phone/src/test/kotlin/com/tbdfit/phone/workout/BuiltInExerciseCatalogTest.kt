package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuiltInExerciseCatalogTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun seedingInsertsExactlyTheDeclaredSeedRowsWithTheirStableIds() = runTest {
        seedBuiltInExercisesIfAbsent(db.exerciseDao())

        for ((id, name) in BUILT_IN_EXERCISE_SEED) {
            val row = db.exerciseDao().getById(id)
            assertEquals(name, row?.name)
            assertEquals(ExerciseSource.BUILT_IN, row?.source)
        }
    }

    @Test
    fun seedingTwiceDoesNotDuplicateRowsOrChangeIds() = runTest {
        seedBuiltInExercisesIfAbsent(db.exerciseDao())
        seedBuiltInExercisesIfAbsent(db.exerciseDao())

        // Counted via a direct query rather than the Flow, to avoid any dependency on emission
        // timing in this assertion.
        val count = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM exercises WHERE source = 'BUILT_IN'",
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

        assertEquals(BUILT_IN_EXERCISE_SEED.size, count)
    }

    @Test
    fun reSeedingDoesNotOverwriteARenamedBuiltInExercise() = runTest {
        seedBuiltInExercisesIfAbsent(db.exerciseDao())
        val (firstId, _) = BUILT_IN_EXERCISE_SEED.first()
        db.exerciseDao().rename(firstId, "My Renamed Bench", "my renamed bench")

        seedBuiltInExercisesIfAbsent(db.exerciseDao())

        val row = db.exerciseDao().getById(firstId)
        assertEquals("My Renamed Bench", row?.name)
    }

    // Independent drift-detection assertion (not derived from BUILT_IN_EXERCISE_SEED itself) — a
    // separately hand-typed literal of the exact 30-entry V1 catalog documented in
    // supabase/migrations/20260916120000_expand_builtin_exercise_catalog.sql. If either list is
    // ever edited without the other (a renamed/removed id, a typo'd name), this test fails instead
    // of the two files silently drifting apart. Deliberately does not compare exercise_type/
    // equipment: Android's ExerciseEntity has no such fields — that metadata is Web+Supabase only.
    @Test
    fun matchesTheDocumentedThirtyExerciseV1Catalog() {
        val expected = listOf(
            "builtin_bench_press" to "Bench Press",
            "builtin_back_squat" to "Back Squat",
            "builtin_deadlift" to "Deadlift",
            "builtin_overhead_press" to "Overhead Press",
            "builtin_barbell_row" to "Barbell Row",
            "builtin_pull_up" to "Pull-Up",
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

        assertEquals(30, BUILT_IN_EXERCISE_SEED.size)
        assertEquals(expected, BUILT_IN_EXERCISE_SEED)
        assertEquals("no duplicate canonical ids", expected.size, expected.map { it.first }.distinct().size)
    }

    @Test
    fun reSeedingDoesNotTouchExistingCustomExercises() = runTest {
        val customId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = customId, name = "My Custom Move", normalizedName = "my custom move", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 1L),
        )

        seedBuiltInExercisesIfAbsent(db.exerciseDao())
        seedBuiltInExercisesIfAbsent(db.exerciseDao())

        val row = db.exerciseDao().getById(customId)
        assertEquals("My Custom Move", row?.name)
        assertEquals(ExerciseSource.CUSTOM, row?.source)
    }
}
