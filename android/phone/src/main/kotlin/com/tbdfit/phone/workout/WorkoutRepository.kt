package com.tbdfit.phone.workout

import androidx.room.withTransaction
import com.tbdfit.phone.localaccount.LocalAccountDao
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.localstorage.AppDatabase
import java.util.UUID

// The concrete, workout-specific composition point over the DAOs above — not a generic
// Repository<T> (see CLAUDE.md's guidance against introducing an abstraction without a real,
// current need: there is exactly one workout domain right now, so one concrete class is enough).
// Owns ID/timestamp generation so the DAOs themselves stay pure persistence, mirroring how
// MainActivity/LocalRecordSyncCoordinator already generate IDs and timestamps at the call site for
// LocalRecordEntity elsewhere in this codebase.
//
// Also the home of the real Routine domain (Slice A of program-routine-first-slice-design.md) —
// Routine is part of the same workout domain, not a separate one, so it lives here rather than in a
// new repository class (see that design doc's Repository API Direction section). `appDatabase` is
// needed only for `startRoutine`'s cross-DAO transaction (`androidx.room.withTransaction`) — every
// other existing atomic operation in this codebase stays within one DAO's own `@Transaction`
// default method, but starting a Routine genuinely spans WorkoutDao/WorkoutExerciseDao/
// WorkoutSetDao/RoutineExerciseDao/RoutinePlannedSetDao, which Room cannot express as a single
// DAO-level `@Transaction`.
class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val workoutSetDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val localAccountDao: LocalAccountDao,
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val routinePlannedSetDao: RoutinePlannedSetDao,
    private val appDatabase: AppDatabase,
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

    // --- Routine (Slice A of program-routine-first-slice-design.md) -----------------------------

    suspend fun createRoutine(ownerId: String, name: String): RoutineEntity {
        ensureLocalAccountExists(ownerId)
        val routine = RoutineEntity(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
        )
        routineDao.insert(routine)
        return routine
    }

    // Owner-scoped only — see RoutineDao.getVisibleTo's own doc comment.
    fun observeRoutinesFor(ownerId: String) = routineDao.getVisibleTo(ownerId)

    suspend fun addExerciseToRoutine(routineId: String, exerciseId: String): RoutineExerciseEntity =
        routineExerciseDao.appendExercise(id = UUID.randomUUID().toString(), routineId = routineId, exerciseId = exerciseId)

    fun observeRoutineExercises(routineId: String) = routineExerciseDao.observeAttachedExercises(routineId)

    suspend fun addPlannedSetToRoutineExercise(routineExerciseId: String, plannedReps: Int?, plannedWeight: Double?): RoutinePlannedSetEntity =
        routinePlannedSetDao.appendPlannedSet(
            id = UUID.randomUUID().toString(),
            routineExerciseId = routineExerciseId,
            plannedReps = plannedReps,
            plannedWeight = plannedWeight,
        )

    fun observePlannedSets(routineExerciseId: String) = routinePlannedSetDao.observeForRoutineExercise(routineExerciseId)

    // Routine UX completion slice — thin wrappers over already-existing DAO operations (no new
    // ordering/atomicity scheme introduced). Cascades to this exercise's own RoutinePlannedSet rows
    // via FK; never touches any Workout already started from this Routine (WorkoutSet's own
    // targetReps/targetWeight were copied at start time and do not read this row again).
    suspend fun removeExerciseFromRoutine(routineExerciseId: String) = routineExerciseDao.delete(routineExerciseId)

    suspend fun removePlannedSet(plannedSetId: String) = routinePlannedSetDao.delete(plannedSetId)

    suspend fun updatePlannedSetReps(plannedSetId: String, reps: Int?) = routinePlannedSetDao.updatePlannedReps(plannedSetId, reps)

    suspend fun updatePlannedSetWeight(plannedSetId: String, weight: Double?) = routinePlannedSetDao.updatePlannedWeight(plannedSetId, weight)

    suspend fun renameRoutine(routineId: String, newName: String) {
        routineDao.rename(routineId, newName.trim(), System.currentTimeMillis())
    }

    // True if a row was actually deleted (it existed and belonged to ownerId); false otherwise —
    // the same deterministic-no-op-signal pattern as discardWorkout above. Cascades to this
    // Routine's own RoutineExercise/RoutinePlannedSet rows; never touches any Workout that was ever
    // started from it (see WorkoutEntity.originRoutineId, ON DELETE SET NULL).
    suspend fun deleteRoutine(routineId: String, ownerId: String): Boolean =
        routineDao.deleteOwnedBy(routineId, ownerId) > 0

    // The atomic Routine → Workout start transaction (design doc's Execution Lifecycles section).
    // ownerId must be a real, currently-known account id — same contract as startWorkout above.
    //
    // Ownership is checked with `require` (throws), not a new result-type variant: per the design
    // doc, `startRoutine` reuses StartWorkoutResult unchanged (Started/AlreadyActive) rather than
    // inventing a new outcome for "not your routine" — a mismatch here means the caller is not going
    // through the supported, owner-scoped Routine list (observeRoutinesFor), the same "should be
    // structurally impossible via the supported UI path" class of assertion already used by
    // WorkoutDao.startWorkoutIfNoneActive's own requireNotNull.
    //
    // If a workout is already active for this owner, planned content is NOT attached to it (mirrors
    // startPrototypeRoutine's existing chosen behavior exactly) — the caller is routed to the
    // existing active workout, untouched.
    suspend fun startRoutine(ownerId: String, routineId: String): StartWorkoutResult {
        ensureLocalAccountExists(ownerId)
        val routine = routineDao.getById(routineId)
        require(routine != null && routine.ownerId == ownerId) {
            "Routine $routineId does not belong to owner $ownerId"
        }
        return appDatabase.withTransaction {
            val now = System.currentTimeMillis()
            val workout = WorkoutEntity(
                id = UUID.randomUUID().toString(),
                ownerId = ownerId,
                status = WorkoutStatus.ACTIVE,
                startedAt = now,
                createdAt = now,
                originRoutineId = routineId,
            )
            val result = workoutDao.startWorkoutIfNoneActive(workout)
            if (result is StartWorkoutResult.Started) {
                for (routineExercise in routineExerciseDao.getForRoutine(routineId)) {
                    val workoutExercise = workoutExerciseDao.appendExercise(
                        id = UUID.randomUUID().toString(),
                        workoutId = workout.id,
                        exerciseId = routineExercise.exerciseId,
                    )
                    for (plannedSet in routinePlannedSetDao.getForRoutineExercise(routineExercise.id)) {
                        // The execution target snapshot itself: this is the one and only moment
                        // targetReps/targetWeight are ever copied from planning data — see
                        // WorkoutSetDao.appendPlannedSet's own doc comment.
                        workoutSetDao.appendPlannedSet(
                            id = UUID.randomUUID().toString(),
                            workoutExerciseId = workoutExercise.id,
                            targetReps = plannedSet.plannedReps,
                            targetWeight = plannedSet.plannedWeight,
                        )
                    }
                }
            }
            result
        }
    }
}
