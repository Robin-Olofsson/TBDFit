package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutinePlannedSetDao {
    @Insert
    suspend fun insert(plannedSet: RoutinePlannedSetEntity)

    @Query("SELECT * FROM routine_planned_sets WHERE routineExerciseId = :routineExerciseId ORDER BY position ASC")
    suspend fun getForRoutineExercise(routineExerciseId: String): List<RoutinePlannedSetEntity>

    @Query("SELECT * FROM routine_planned_sets WHERE routineExerciseId = :routineExerciseId ORDER BY position ASC")
    fun observeForRoutineExercise(routineExerciseId: String): Flow<List<RoutinePlannedSetEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routine_planned_sets WHERE routineExerciseId = :routineExerciseId")
    suspend fun maxPosition(routineExerciseId: String): Int

    @Query("UPDATE routine_planned_sets SET plannedReps = :plannedReps WHERE id = :id")
    suspend fun setPlannedRepsRaw(id: String, plannedReps: Int?)

    @Query("UPDATE routine_planned_sets SET plannedWeight = :plannedWeight WHERE id = :id")
    suspend fun setPlannedWeightRaw(id: String, plannedWeight: Double?)

    @Query("DELETE FROM routine_planned_sets WHERE id = :id")
    suspend fun delete(id: String)

    // Same non-negative enforcement + atomic position-assignment pattern as WorkoutSetDao.appendSet.
    @Transaction
    suspend fun appendPlannedSet(id: String, routineExerciseId: String, plannedReps: Int?, plannedWeight: Double?): RoutinePlannedSetEntity {
        require(plannedReps == null || plannedReps >= 0) { "plannedReps must not be negative" }
        require(plannedWeight == null || plannedWeight >= 0.0) { "plannedWeight must not be negative" }
        val nextPosition = maxPosition(routineExerciseId) + 1
        val entity = RoutinePlannedSetEntity(
            id = id,
            routineExerciseId = routineExerciseId,
            position = nextPosition,
            plannedReps = plannedReps,
            plannedWeight = plannedWeight,
        )
        insert(entity)
        return entity
    }

    suspend fun updatePlannedReps(id: String, plannedReps: Int?) {
        require(plannedReps == null || plannedReps >= 0) { "plannedReps must not be negative" }
        setPlannedRepsRaw(id, plannedReps)
    }

    suspend fun updatePlannedWeight(id: String, plannedWeight: Double?) {
        require(plannedWeight == null || plannedWeight >= 0.0) { "plannedWeight must not be negative" }
        setPlannedWeightRaw(id, plannedWeight)
    }
}
