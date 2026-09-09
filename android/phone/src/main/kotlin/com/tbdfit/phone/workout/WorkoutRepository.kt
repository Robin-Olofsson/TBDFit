package com.tbdfit.phone.workout

import com.tbdfit.phone.localaccount.LocalAccountDao
import com.tbdfit.phone.localaccount.LocalAccountEntity
import java.util.UUID

// The concrete, workout-specific composition point over the DAOs above — not a generic
// Repository<T> (see CLAUDE.md's guidance against introducing an abstraction without a real,
// current need: there is exactly one workout domain right now, so one concrete class is enough).
// Owns ID/timestamp generation so the DAOs themselves stay pure persistence, mirroring how
// MainActivity/LocalRecordSyncCoordinator already generate IDs and timestamps at the call site for
// LocalRecordEntity elsewhere in this codebase.
class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val workoutSetDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val localAccountDao: LocalAccountDao,
) {
    // The one place the "LocalAccount must exist before any owner-scoped write" invariant is
    // enforced structurally (local-account-ownership correction) — called at the top of every
    // mutation that inserts a row with a real ownerId foreign key (startWorkout,
    // createCustomExercise), so correctness never depends on a UI call site remembering to do this
    // first. Idempotent (LocalAccountDao.insertIfAbsent) — safe to call on every such write, not
    // just the first one for a given account.
    //
    // Also exposed publicly so MainActivity can call it directly and explicitly the moment
    // AuthState.SignedIn is observed, matching "On SignedIn(userId): ensure LocalAccount(userId)
    // exists" — that call and this internal one are both idempotent, so doing both is redundant but
    // not incorrect; this one is what actually guarantees the FK never fails regardless of UI timing.
    suspend fun ensureLocalAccountExists(ownerId: String) {
        localAccountDao.insertIfAbsent(LocalAccountEntity(id = ownerId, createdAt = System.currentTimeMillis()))
    }

    // ownerId (local-workout-ownership audit) must be a real, currently-known account id —
    // AuthSession.userId while SignedIn, or the cached last-known account while SessionUnavailable
    // (see com.tbdfit.phone.auth.LastSignedInAccountCache). There is no default/blank ownerId path.
    suspend fun startWorkout(ownerId: String): StartWorkoutResult {
        ensureLocalAccountExists(ownerId)
        val now = System.currentTimeMillis()
        val workout = WorkoutEntity(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            status = WorkoutStatus.ACTIVE,
            startedAt = now,
            createdAt = now,
        )
        return workoutDao.startWorkoutIfNoneActive(workout)
    }

    suspend fun getActiveWorkout(ownerId: String): WorkoutEntity? = workoutDao.getActiveWorkout(ownerId)

    fun observeActiveWorkout(ownerId: String) = workoutDao.observeActiveWorkout(ownerId)

    fun observeCompletedWorkouts() = workoutDao.observeCompletedWorkouts()

    suspend fun addExercise(workoutId: String, exerciseId: String): WorkoutExerciseEntity =
        workoutExerciseDao.appendExercise(
            id = UUID.randomUUID().toString(),
            workoutId = workoutId,
            exerciseId = exerciseId,
        )

    fun observeExercisesForWorkout(workoutId: String) = workoutExerciseDao.observeForWorkout(workoutId)

    fun observeAttachedExercises(workoutId: String) = workoutExerciseDao.observeAttachedExercises(workoutId)

    // Local-account-ownership correction: "all exercises" is not a real query any more — every
    // caller must supply the account whose view it wants, so a CUSTOM exercise never leaks across
    // accounts (BUILT_IN rows remain visible to everyone regardless). See ExerciseDao.getVisibleTo.
    fun observeAllExercises(ownerId: String) = exerciseDao.getVisibleTo(ownerId)

    suspend fun addSet(workoutExerciseId: String, weight: Double?, reps: Int?): WorkoutSetEntity =
        workoutSetDao.appendSet(
            id = UUID.randomUUID().toString(),
            workoutExerciseId = workoutExerciseId,
            weight = weight,
            reps = reps,
        )

    fun observeSetsForWorkoutExercise(workoutExerciseId: String) = workoutSetDao.observeForWorkoutExercise(workoutExerciseId)

    suspend fun updateSetWeight(setId: String, weight: Double?) = workoutSetDao.updateWeight(setId, weight)

    suspend fun updateSetReps(setId: String, reps: Int?) = workoutSetDao.updateReps(setId, reps)

    // Provisional first-slice semantics (C1): rejects completion if reps is absent, rather than
    // fabricating a value. See WorkoutSetDao.completeIfRepsPresent for the atomic check-then-write.
    suspend fun completeSet(setId: String): CompleteSetResult =
        workoutSetDao.completeIfRepsPresent(setId, System.currentTimeMillis())

    suspend fun uncompleteSet(setId: String) = workoutSetDao.markIncomplete(setId)

    suspend fun deleteSet(setId: String) = workoutSetDao.delete(setId)

    // True if the workout was actually ACTIVE and is now COMPLETED; false if it was not ACTIVE
    // (already completed, or does not exist) — an invalid double-completion is a detectable,
    // deterministic no-op, never a silent state mutation. See WorkoutDao.completeIfActive.
    suspend fun completeWorkout(workoutId: String): Boolean =
        workoutDao.completeIfActive(workoutId, System.currentTimeMillis()) > 0

    // Transactional deletion, not a status transition — see WorkoutEntity's doc comment. True if a
    // row was actually deleted (it was ACTIVE); false otherwise.
    suspend fun discardWorkout(workoutId: String): Boolean = workoutDao.deleteIfActive(workoutId) > 0

    suspend fun createCustomExercise(ownerId: String, name: String): ExerciseEntity {
        ensureLocalAccountExists(ownerId)
        val trimmed = name.trim()
        val exercise = ExerciseEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            normalizedName = trimmed.lowercase(),
            source = ExerciseSource.CUSTOM,
            ownerId = ownerId,
            createdAt = System.currentTimeMillis(),
        )
        exerciseDao.insert(exercise)
        return exercise
    }

    // Non-blocking duplicate hint only — callers decide what, if anything, to show the user. Scoped
    // to this owner's own custom exercises; see ExerciseDao.findByNormalizedNameAndSource.
    suspend fun findExistingCustomExercises(ownerId: String, name: String): List<ExerciseEntity> =
        exerciseDao.findByNormalizedNameAndSource(name.trim().lowercase(), ownerId)

    suspend fun renameExercise(exerciseId: String, newName: String) {
        val trimmed = newName.trim()
        exerciseDao.rename(exerciseId, trimmed, trimmed.lowercase())
    }
}
