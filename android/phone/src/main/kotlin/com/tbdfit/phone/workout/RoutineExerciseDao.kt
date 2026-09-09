package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// A read-only join projection for display — mirrors WorkoutExerciseDao's AttachedExercise exactly:
// exerciseId is identity, name is looked up fresh from the current Exercise row on every emission,
// never snapshotted. A rename is reflected automatically, with no migration or backfill.
data class RoutineAttachedExercise(
    val routineExerciseId: String,
    val exerciseId: String,
    val name: String,
    val position: Int,
)

@Dao
interface RoutineExerciseDao {
    @Insert
    suspend fun insert(routineExercise: RoutineExerciseEntity)

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position ASC")
    suspend fun getForRoutine(routineId: String): List<RoutineExerciseEntity>

    @Query(
        "SELECT re.id AS routineExerciseId, re.exerciseId AS exerciseId, e.name AS name, re.position AS position " +
            "FROM routine_exercises re JOIN exercises e ON e.id = re.exerciseId " +
            "WHERE re.routineId = :routineId ORDER BY re.position ASC",
    )
    fun observeAttachedExercises(routineId: String): Flow<List<RoutineAttachedExercise>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routine_exercises WHERE routineId = :routineId")
    suspend fun maxPosition(routineId: String): Int

    // Same atomic position-assignment pattern as WorkoutExerciseDao.appendExercise — computing the
    // next position and inserting happen in one transaction so two concurrent appends to the same
    // Routine cannot race to the same position.
    @Transaction
    suspend fun appendExercise(id: String, routineId: String, exerciseId: String): RoutineExerciseEntity {
        val nextPosition = maxPosition(routineId) + 1
        val entity = RoutineExerciseEntity(id = id, routineId = routineId, exerciseId = exerciseId, position = nextPosition)
        insert(entity)
        return entity
    }

    // Cascades to this exercise's own RoutinePlannedSet rows via their foreign key. Sparse
    // positions after delete, no compaction — same convention as WorkoutSetDao.
    @Query("DELETE FROM routine_exercises WHERE id = :id")
    suspend fun delete(id: String)
}
