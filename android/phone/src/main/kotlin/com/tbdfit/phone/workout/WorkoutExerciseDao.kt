package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// A read-only join projection for display — exerciseId is still carried through (identity), name
// is looked up fresh from the current Exercise row on every emission, never snapshotted into
// workout_exercises itself. A rename is therefore reflected automatically the next time this Flow
// emits, with no migration or backfill involved.
data class AttachedExercise(
    val workoutExerciseId: String,
    val exerciseId: String,
    val name: String,
    val position: Int,
)

@Dao
interface WorkoutExerciseDao {
    @Insert
    suspend fun insert(workoutExercise: WorkoutExerciseEntity)

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position ASC")
    suspend fun getForWorkout(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position ASC")
    fun observeForWorkout(workoutId: String): Flow<List<WorkoutExerciseEntity>>

    // Duplicate attachment semantics (open, provisional — see the strength-workout design doc,
    // which does not specify whether the same Exercise may appear twice in one Workout): the
    // smallest, most reversible choice was made here, which is to add no constraint at all. The
    // same exerciseId may be attached to one workout more than once; nothing below de-duplicates.
    // Revisit if the team decides duplicates should be blocked or need distinct handling (e.g. for
    // a future superset feature).
    @Query(
        "SELECT we.id AS workoutExerciseId, we.exerciseId AS exerciseId, e.name AS name, we.position AS position " +
            "FROM workout_exercises we JOIN exercises e ON e.id = we.exerciseId " +
            "WHERE we.workoutId = :workoutId ORDER BY we.position ASC",
    )
    fun observeAttachedExercises(workoutId: String): Flow<List<AttachedExercise>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun maxPosition(workoutId: String): Int

    // Computing the next position and inserting are wrapped in one transaction so two concurrent
    // appends to the same workout cannot race to the same position — see the design doc's
    // Persistence Model (deterministic order).
    @Transaction
    suspend fun appendExercise(id: String, workoutId: String, exerciseId: String): WorkoutExerciseEntity {
        val nextPosition = maxPosition(workoutId) + 1
        val entity = WorkoutExerciseEntity(id = id, workoutId = workoutId, exerciseId = exerciseId, position = nextPosition)
        insert(entity)
        return entity
    }
}
