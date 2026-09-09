package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Deterministic, atomically-checked outcome of an attempted completion — see
// WorkoutSetDao.completeIfRepsPresent. First-slice provisional semantics (see the design doc's
// Minimum Set Model / this slice's own C1 semantics): a completed set requires reps to be present;
// weight may remain null (bodyweight exercises). This rule is provisional product/domain semantics
// pending team review, not an Accepted decision.
sealed interface CompleteSetResult {
    data object Completed : CompleteSetResult
    data object RejectedMissingReps : CompleteSetResult
    data object NotFound : CompleteSetResult
}

@Dao
interface WorkoutSetDao {
    @Insert
    suspend fun insert(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position ASC")
    suspend fun getForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getById(id: String): WorkoutSetEntity?

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position ASC")
    fun observeForWorkoutExercise(workoutExerciseId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun maxPosition(workoutExerciseId: String): Int

    @Query("UPDATE workout_sets SET weight = :weight WHERE id = :id")
    suspend fun setWeightRaw(id: String, weight: Double?)

    @Query("UPDATE workout_sets SET reps = :reps WHERE id = :id")
    suspend fun setRepsRaw(id: String, reps: Int?)

    // NOT the supported way to complete a set from application code — see completeIfRepsPresent
    // below, which is the only caller that should reach this. Kept as its own @Query so the
    // isCompleted/completedAt pairing stays a single atomic statement, but calling this directly
    // would skip the "reps must be present" check entirely.
    @Query("UPDATE workout_sets SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long)

    // Un-completing is supported because the UI presents completion as a checkbox (see
    // ActiveWorkoutScreen), which inherently implies a two-way toggle — leaving it one-way would
    // make an accidental tap permanent short of deleting and re-adding the entire set. Keeps
    // isCompleted/completedAt paired, mirroring markCompleted exactly.
    @Query("UPDATE workout_sets SET isCompleted = 0, completedAt = NULL WHERE id = :id")
    suspend fun markIncomplete(id: String)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun delete(id: String)

    // First-slice completion semantics (C1, provisional): isCompleted=true requires reps to already
    // be present on the row; weight may remain null. Reading the current row and writing the
    // completion state happen in one transaction so no externally-visible half-completed state (a
    // reader observing isCompleted=true with completedAt still null, or vice versa) can ever be
    // produced, and so the reps check can never be raced by a concurrent edit.
    @Transaction
    suspend fun completeIfRepsPresent(id: String, completedAt: Long): CompleteSetResult {
        val set = getById(id) ?: return CompleteSetResult.NotFound
        if (set.reps == null) return CompleteSetResult.RejectedMissingReps
        markCompleted(id, completedAt)
        return CompleteSetResult.Completed
    }

    // Enforcement layer for rejecting impossible persisted values (see the design doc's Minimum
    // Set Model): Room has no declarative CHECK-constraint mechanism used elsewhere in this
    // codebase, so validation happens here, at the DAO boundary, before anything reaches the
    // database. Position assignment is wrapped in the same transaction as the insert so two
    // concurrent appends to the same exercise cannot race to the same position.
    @Transaction
    suspend fun appendSet(id: String, workoutExerciseId: String, weight: Double?, reps: Int?): WorkoutSetEntity {
        require(weight == null || weight >= 0.0) { "weight must not be negative" }
        require(reps == null || reps >= 0) { "reps must not be negative" }
        val nextPosition = maxPosition(workoutExerciseId) + 1
        val entity = WorkoutSetEntity(
            id = id,
            workoutExerciseId = workoutExerciseId,
            position = nextPosition,
            weight = weight,
            reps = reps,
        )
        insert(entity)
        return entity
    }

    suspend fun updateWeight(id: String, weight: Double?) {
        require(weight == null || weight >= 0.0) { "weight must not be negative" }
        setWeightRaw(id, weight)
    }

    suspend fun updateReps(id: String, reps: Int?) {
        require(reps == null || reps >= 0) { "reps must not be negative" }
        setRepsRaw(id, reps)
    }

    // Slice A execution target snapshot (program-routine-first-slice-design.md): creates an
    // unperformed WorkoutSet carrying a frozen target — reps/weight/isCompleted/completedAt all
    // stay at their ordinary unperformed defaults, exactly like appendSet above. The ONLY difference
    // from appendSet is that targetReps/targetWeight are populated here — this is the actual
    // mechanism of "START creates an execution snapshot": once this row exists, nothing ever reads
    // the source RoutinePlannedSet/ProgramSessionPlannedSet again to learn what the target was. See
    // WorkoutRepository.startRoutine for the only caller.
    @Transaction
    suspend fun appendPlannedSet(id: String, workoutExerciseId: String, targetReps: Int?, targetWeight: Double?): WorkoutSetEntity {
        require(targetWeight == null || targetWeight >= 0.0) { "targetWeight must not be negative" }
        require(targetReps == null || targetReps >= 0) { "targetReps must not be negative" }
        val nextPosition = maxPosition(workoutExerciseId) + 1
        val entity = WorkoutSetEntity(
            id = id,
            workoutExerciseId = workoutExerciseId,
            position = nextPosition,
            targetReps = targetReps,
            targetWeight = targetWeight,
        )
        insert(entity)
        return entity
    }
}
