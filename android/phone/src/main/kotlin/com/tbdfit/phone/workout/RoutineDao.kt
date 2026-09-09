package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert
    suspend fun insert(routine: RoutineEntity)

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineEntity?

    // Owner-scoped only — there is deliberately no unscoped "get all routines" query reachable from
    // application code, mirroring ExerciseDao.getVisibleTo/WorkoutDao.getActiveWorkout. See
    // program-routine-first-slice-design.md's Cross-account isolation section.
    @Query("SELECT * FROM routines WHERE ownerId = :ownerId ORDER BY name ASC")
    fun getVisibleTo(ownerId: String): Flow<List<RoutineEntity>>

    @Query("UPDATE routines SET name = :name, lastModifiedAt = :lastModifiedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, lastModifiedAt: Long)

    // Scoped by ownerId directly in the query (not just id) as a second, structural layer of
    // cross-account protection beyond the repository's own ownership check — returns 0 rather than
    // deleting anything if the caller does not actually own this Routine. Cascades to
    // RoutineExercise/RoutinePlannedSet via their own foreign keys; never touches any Workout that
    // was started from this Routine (see WorkoutEntity.originRoutineId, ON DELETE SET NULL).
    @Query("DELETE FROM routines WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteOwnedBy(id: String, ownerId: String): Int
}
