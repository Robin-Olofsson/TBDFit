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
