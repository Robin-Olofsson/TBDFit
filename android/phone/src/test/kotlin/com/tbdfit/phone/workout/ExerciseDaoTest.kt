package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseDaoTest {
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
    fun insertedExerciseIsReadableById() = runTest {
        val exercise = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.CUSTOM,
            ownerId = null,
            createdAt = 1_700_000_000_000L,
        )

        db.exerciseDao().insert(exercise)

        assertEquals(exercise, db.exerciseDao().getById(exercise.id))
    }

    // The load-bearing case from the design doc's Exercise Identity section: identity is the
    // stable id, not the name — similar/overlapping display names must be able to coexist as
    // distinct rows with no uniqueness constraint on normalizedName.
    @Test
    fun similarAndIdenticalNormalizedNamesCanCoexistAsDistinctExercises() = runTest {
        val benchPress = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.BUILT_IN,
            ownerId = null,
            createdAt = 1_700_000_000_000L,
        )
        val inclineBenchPress = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Incline Bench Press",
            normalizedName = "incline bench press",
            source = ExerciseSource.BUILT_IN,
            ownerId = null,
            createdAt = 1_700_000_000_001L,
        )
        // A second, independent exercise that happens to normalize to the exact same string as
        // the first — must not be rejected, and must remain a fully distinct row/id.
        val secondBenchPress = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.CUSTOM,
            ownerId = null,
            createdAt = 1_700_000_000_002L,
        )

        db.exerciseDao().insert(benchPress)
        db.exerciseDao().insert(inclineBenchPress)
        db.exerciseDao().insert(secondBenchPress)

        assertEquals(benchPress, db.exerciseDao().getById(benchPress.id))
        assertEquals(inclineBenchPress, db.exerciseDao().getById(inclineBenchPress.id))
        assertEquals(secondBenchPress, db.exerciseDao().getById(secondBenchPress.id))
        assertTrue(benchPress.id != secondBenchPress.id)
    }

    @Test
    fun renamePreservesIdAndIsVisibleImmediately() = runTest {
        val exercise = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.CUSTOM,
            ownerId = null,
            createdAt = 1_700_000_000_000L,
        )
        db.exerciseDao().insert(exercise)

        db.exerciseDao().rename(exercise.id, "Barbell Bench Press", "barbell bench press")

        val renamed = db.exerciseDao().getById(exercise.id)
        assertEquals(exercise.id, renamed?.id)
        assertEquals("Barbell Bench Press", renamed?.name)
        assertEquals("barbell bench press", renamed?.normalizedName)
    }

    // Local-account-ownership correction: the duplicate-hint query is scoped to CUSTOM rows AND to
    // the given owner — another account's identically-named custom exercise must never surface here.
    @Test
    fun findByNormalizedNameAndSourceIsScopedToCustomAndToTheGivenOwner() = runTest {
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = 1L))
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-b", createdAt = 1L))
        val builtIn = ExerciseEntity(
            id = "builtin_bench_press",
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.BUILT_IN,
            ownerId = null,
            createdAt = 1_700_000_000_000L,
        )
        val ownedByA = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.CUSTOM,
            ownerId = "owner-a",
            createdAt = 1_700_000_000_001L,
        )
        val ownedByB = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.CUSTOM,
            ownerId = "owner-b",
            createdAt = 1_700_000_000_002L,
        )
        db.exerciseDao().insert(builtIn)
        db.exerciseDao().insert(ownedByA)
        db.exerciseDao().insert(ownedByB)

        val hitsForA = db.exerciseDao().findByNormalizedNameAndSource("bench press", ownerId = "owner-a")

        assertEquals(listOf(ownedByA), hitsForA)
    }

    // Local-account-ownership correction — the core regression coverage the correction explicitly
    // asks for: BUILT_IN visible to both accounts; each account's own CUSTOM exercise visible only
    // to that account; a legacy CUSTOM row with no recorded owner (ownerId = null) invisible to both.
    @Test
    fun getVisibleToShowsBuiltInsToEveryoneAndCustomExercisesOnlyToTheirOwner() = runTest {
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = 1L))
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-b", createdAt = 1L))
        val builtIn = ExerciseEntity(
            id = "builtin_bench_press",
            name = "Bench Press",
            normalizedName = "bench press",
            source = ExerciseSource.BUILT_IN,
            ownerId = null,
            createdAt = 1_700_000_000_000L,
        )
        val ownedByA = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "A's Custom Move",
            normalizedName = "a's custom move",
            source = ExerciseSource.CUSTOM,
            ownerId = "owner-a",
            createdAt = 1_700_000_000_001L,
        )
        val ownedByB = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "B's Custom Move",
            normalizedName = "b's custom move",
            source = ExerciseSource.CUSTOM,
            ownerId = "owner-b",
            createdAt = 1_700_000_000_002L,
        )
        val orphanedCustom = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = "Pre-Ownership Custom Move",
            normalizedName = "pre-ownership custom move",
            source = ExerciseSource.CUSTOM,
            ownerId = null,
            createdAt = 1_700_000_000_003L,
        )
        db.exerciseDao().insert(builtIn)
        db.exerciseDao().insert(ownedByA)
        db.exerciseDao().insert(ownedByB)
        db.exerciseDao().insert(orphanedCustom)

        val visibleToA = db.exerciseDao().getVisibleTo("owner-a").first()
        val visibleToB = db.exerciseDao().getVisibleTo("owner-b").first()

        assertEquals(setOf(builtIn.id, ownedByA.id), visibleToA.map { it.id }.toSet())
        assertEquals(setOf(builtIn.id, ownedByB.id), visibleToB.map { it.id }.toSet())
        // Neither account ever sees the other's custom exercise, or the ownerless legacy row.
        assertTrue(visibleToA.none { it.id == ownedByB.id || it.id == orphanedCustom.id })
        assertTrue(visibleToB.none { it.id == ownedByA.id || it.id == orphanedCustom.id })
    }
}
