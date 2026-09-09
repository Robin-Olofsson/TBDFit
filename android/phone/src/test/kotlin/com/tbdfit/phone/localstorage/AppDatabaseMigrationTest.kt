package com.tbdfit.phone.localstorage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Exercises the real MIGRATION_3_4 against a real, on-disk SQLite database constructed from the
// committed v3 schema (android/phone/schemas/.../3.json) — not the app's normal open path, and
// deliberately not going through fallbackToDestructiveMigration (there is none any more; see
// AppDatabase). This is the load-bearing test for the durable-data transition described in
// docs/architecture/strength-workout-first-slice-design.md's PERSISTENCE/MIGRATION POLICY: it
// proves the migration is real, preserves existing technical-proof rows, and produces a schema
// Room itself considers valid — not merely that the app "happens to work" on a fresh install.
//
// Runs under Robolectric (real SQLite on the JVM), consistent with every other Room test in this
// module — this is AUTOMATED/ROOM-TEST VERIFIED, not a substitute for real device/emulator
// verification.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFrom3To4PreservesExistingLocalRecordsAndCreatesWorkoutTables() {
        // Build a real version-3 database (the committed 3.json schema) and seed it with a
        // technical-proof row exactly as the pre-transition app would have — this is the state a
        // real installed app is in the moment before it ever sees version 4.
        val v3 = helper.createDatabase(testDbName, 3)
        v3.execSQL(
            "INSERT INTO local_records (id, createdAt, value, syncedAt) VALUES " +
                "('pre-existing-id', 1700000000000, 'pre-existing-value', NULL)",
        )
        v3.close()

        // Run the real, explicit MIGRATION_3_4 — not destructive fallback, since there is none.
        // runMigrationsAndValidate also asserts the resulting schema matches what Room expects
        // from the current entity annotations (the identity-hash check) — this is what would
        // otherwise surface as "Migration didn't properly handle..." at real app-open time.
        val v4 = helper.runMigrationsAndValidate(testDbName, 4, true, AppDatabase.MIGRATION_3_4)

        // The disposable technical-proof row survived a migration that also introduced new,
        // unrelated tables — the exact scenario the design doc's migration-review discipline
        // exists to guarantee.
        val cursor = v4.query("SELECT id, value, syncedAt FROM local_records WHERE id = 'pre-existing-id'")
        assertTrue(cursor.moveToFirst())
        assertEquals("pre-existing-value", cursor.getString(cursor.getColumnIndexOrThrow("value")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("syncedAt")))
        cursor.close()

        // The four new durable workout tables exist and are queryable (empty) post-migration.
        for (table in listOf("exercises", "workouts", "workout_exercises", "workout_sets")) {
            val countCursor = v4.query("SELECT COUNT(*) FROM $table")
            assertTrue(countCursor.moveToFirst())
            assertEquals(0, countCursor.getInt(0))
            countCursor.close()
        }

        v4.close()
    }

    // Load-bearing test for the local-workout-ownership audit: proves the v4->v5 migration adds
    // Workout.ownerId without destroying any pre-existing workout row, and that a row which
    // predates account scoping lands on the documented sentinel ('' — see WorkoutEntity's own doc
    // comment) rather than a guessed real account.
    @Test
    fun migrationFrom4To5AddsOwnerIdWithoutLosingExistingWorkoutRowsAndDefaultsToTheUnknownSentinel() {
        val v4 = helper.createDatabase(testDbName, 4)
        v4.execSQL(
            "INSERT INTO workouts (id, status, startedAt, completedAt, lastModifiedAt, createdAt) VALUES " +
                "('pre-ownership-workout', 'ACTIVE', 1700000000000, NULL, NULL, 1700000000000)",
        )
        v4.close()

        val v5 = helper.runMigrationsAndValidate(testDbName, 5, true, AppDatabase.MIGRATION_4_5)

        val cursor = v5.query("SELECT id, ownerId, status FROM workouts WHERE id = 'pre-ownership-workout'")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("ownerId")))
        assertEquals("ACTIVE", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        cursor.close()

        v5.close()
    }

    // Load-bearing test for the local-account-ownership correction: proves MIGRATION_5_6 (1) creates
    // local_accounts, (2) backfills a real LocalAccount row for a non-empty v5 ownerId that was
    // already a genuine in-use account id (a faithful migration, not a guess — see MIGRATION_5_6's
    // own doc comment), (3) converts the v5 '' sentinel to NULL rather than backfilling it into an
    // account, and (4) leaves a pre-existing exercise row with ownerId = NULL (no owner can be
    // recovered for it) — and that the resulting schema is one Room's own validator (runMigrationsAndValidate's
    // validate=true) considers to genuinely match the current entity set, i.e. the new foreign keys
    // are real, not merely columns that happen to have the right name.
    @Test
    fun migrationFrom5To6IntroducesLocalAccountsAndConvertsOwnerIdIntoARealForeignKey() {
        val v5 = helper.createDatabase(testDbName, 5)
        v5.execSQL(
            "INSERT INTO workouts (id, ownerId, status, startedAt, completedAt, lastModifiedAt, createdAt) VALUES " +
                "('real-owner-workout', 'user-a', 'ACTIVE', 1700000000000, NULL, NULL, 1700000000000)",
        )
        v5.execSQL(
            "INSERT INTO workouts (id, ownerId, status, startedAt, completedAt, lastModifiedAt, createdAt) VALUES " +
                "('orphaned-workout', '', 'COMPLETED', 1700000000000, 1700000005000, NULL, 1700000000000)",
        )
        v5.execSQL(
            "INSERT INTO exercises (id, name, normalizedName, source, createdAt) VALUES " +
                "('pre-existing-custom', 'My Move', 'my move', 'CUSTOM', 1700000000000)",
        )
        v5.close()

        val v6 = helper.runMigrationsAndValidate(testDbName, 6, true, AppDatabase.MIGRATION_5_6)

        // A real, already-in-use account id was backfilled into local_accounts...
        val accountCursor = v6.query("SELECT id FROM local_accounts WHERE id = 'user-a'")
        assertTrue(accountCursor.moveToFirst())
        accountCursor.close()
        // ...but the empty-string sentinel was never treated as an account.
        val noEmptyAccountCursor = v6.query("SELECT id FROM local_accounts WHERE id = ''")
        assertTrue(!noEmptyAccountCursor.moveToFirst())
        noEmptyAccountCursor.close()

        val realOwnerCursor = v6.query("SELECT ownerId FROM workouts WHERE id = 'real-owner-workout'")
        assertTrue(realOwnerCursor.moveToFirst())
        assertEquals("user-a", realOwnerCursor.getString(0))
        realOwnerCursor.close()

        val orphanCursor = v6.query("SELECT ownerId FROM workouts WHERE id = 'orphaned-workout'")
        assertTrue(orphanCursor.moveToFirst())
        assertTrue(orphanCursor.isNull(0))
        orphanCursor.close()

        // A pre-existing custom exercise has no recoverable owner — NULL, never guessed.
        val exerciseCursor = v6.query("SELECT ownerId FROM exercises WHERE id = 'pre-existing-custom'")
        assertTrue(exerciseCursor.moveToFirst())
        assertTrue(exerciseCursor.isNull(0))
        exerciseCursor.close()

        v6.close()
    }

    // Load-bearing test for Slice A of program-routine-first-slice-design.md: proves MIGRATION_6_7
    // (1) preserves every pre-existing entity type untouched, (2) gives existing workouts/
    // workout_sets rows correct NULL defaults for the new provenance/target columns (none of them
    // could ever have had a Routine origin or a source plan), and (3) the three new Routine tables
    // exist and are immediately usable — and that the resulting schema is one Room's own validator
    // considers a genuine match for the current entity set (i.e. the new foreign keys, including
    // originRoutineId's ON DELETE SET NULL, are real).
    @Test
    fun migrationFrom6To7AddsRoutineDomainAndPreservesExistingWorkoutData() {
        val v6 = helper.createDatabase(testDbName, 6)
        v6.execSQL(
            "INSERT INTO local_accounts (id, createdAt) VALUES ('owner-a', 1700000000000)",
        )
        v6.execSQL(
            "INSERT INTO exercises (id, name, normalizedName, source, ownerId, createdAt) VALUES " +
                "('builtin_bench_press', 'Bench Press', 'bench press', 'BUILT_IN', NULL, 1700000000000)",
        )
        v6.execSQL(
            "INSERT INTO workouts (id, ownerId, status, startedAt, completedAt, lastModifiedAt, createdAt) VALUES " +
                "('pre-existing-workout', 'owner-a', 'COMPLETED', 1700000000000, 1700000005000, NULL, 1700000000000)",
        )
        v6.execSQL(
            "INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES " +
                "('pre-existing-we', 'pre-existing-workout', 'builtin_bench_press', 0)",
        )
        v6.execSQL(
            "INSERT INTO workout_sets (id, workoutExerciseId, position, weight, reps, isCompleted, completedAt) VALUES " +
                "('pre-existing-set', 'pre-existing-we', 0, 80.0, 8, 1, 1700000005000)",
        )
        v6.close()

        val v7 = helper.runMigrationsAndValidate(testDbName, 7, true, AppDatabase.MIGRATION_6_7)

        // Pre-existing workout survives with the new provenance column NULL.
        val workoutCursor = v7.query("SELECT status, originRoutineId FROM workouts WHERE id = 'pre-existing-workout'")
        assertTrue(workoutCursor.moveToFirst())
        assertEquals("COMPLETED", workoutCursor.getString(0))
        assertTrue(workoutCursor.isNull(1))
        workoutCursor.close()

        // Pre-existing set survives with its real values untouched and both new target columns NULL.
        val setCursor = v7.query("SELECT weight, reps, isCompleted, targetReps, targetWeight FROM workout_sets WHERE id = 'pre-existing-set'")
        assertTrue(setCursor.moveToFirst())
        assertEquals(80.0, setCursor.getDouble(0), 0.0)
        assertEquals(8, setCursor.getInt(1))
        assertEquals(1, setCursor.getInt(2))
        assertTrue(setCursor.isNull(3))
        assertTrue(setCursor.isNull(4))
        setCursor.close()

        // The three new Routine tables exist, are empty, and are immediately writable.
        for (table in listOf("routines", "routine_exercises", "routine_planned_sets")) {
            val countCursor = v7.query("SELECT COUNT(*) FROM $table")
            assertTrue(countCursor.moveToFirst())
            assertEquals(0, countCursor.getInt(0))
            countCursor.close()
        }
        v7.execSQL(
            "INSERT INTO routines (id, ownerId, name, createdAt, lastModifiedAt) VALUES " +
                "('new-routine', 'owner-a', 'Push Day', 1700000010000, NULL)",
        )
        val newRoutineCursor = v7.query("SELECT name FROM routines WHERE id = 'new-routine'")
        assertTrue(newRoutineCursor.moveToFirst())
        assertEquals("Push Day", newRoutineCursor.getString(0))
        newRoutineCursor.close()

        v7.close()
    }
}
