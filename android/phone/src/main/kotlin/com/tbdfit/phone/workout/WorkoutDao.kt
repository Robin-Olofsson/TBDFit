package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Deterministic outcome of an attempted startWorkoutIfNoneActive call — never a silently-created
// second ACTIVE workout. See WorkoutDao.startWorkoutIfNoneActive.
sealed interface StartWorkoutResult {
    data class Started(val workout: WorkoutEntity) : StartWorkoutResult
    data class AlreadyActive(val existing: WorkoutEntity) : StartWorkoutResult
}

@Dao
interface WorkoutDao {
    // NOT the supported way to start a workout — this exists for startWorkoutIfNoneActive's own
    // transaction body and for tests that deliberately need to construct an inconsistent state
    // (e.g. seeding more than one ACTIVE row to test restore behavior). Production/application
    // code must go through startWorkoutIfNoneActive (or WorkoutRepository.startWorkout(), which
    // does nothing but delegate to it) — calling this directly bypasses the single-active-workout
    // guarantee entirely, since there is no database-level constraint backing it (see
    // startWorkoutIfNoneActive's own doc comment for why). As of this audit pass, no production
    // code calls this directly (grep-verified) — keep it that way.
    @Insert
    suspend fun insert(workout: WorkoutEntity)

    // Recovery behavior, not enforcement — see the design doc's "Single active workout per
    // device" section. If more than one ACTIVE row for this owner ever exists (a future bug, a bad
    // migration), this deterministically resumes the most recently started one rather than an
    // arbitrary one. Preventing that state from occurring at all is startWorkoutIfNoneActive's job
    // below, not this query's.
    //
    // Scoped by ownerId (local-workout-ownership audit): there is deliberately no unscoped variant
    // of this query reachable from application code — every caller must supply the specific
    // account whose workout it wants, so one account can never observe another's. See
    // WorkoutEntity's own doc comment and WorkoutRepository for where ownerId actually comes from
    // (AuthSession.userId while SignedIn; a small local last-known-account cache while
    // SessionUnavailable — see com.tbdfit.phone.auth.LastSignedInAccountCache).
    @Query("SELECT * FROM workouts WHERE status = 'ACTIVE' AND ownerId = :ownerId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveWorkout(ownerId: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE status = 'ACTIVE' AND ownerId = :ownerId ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveWorkout(ownerId: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompletedWorkouts(): Flow<List<WorkoutEntity>>

    // The check-then-insert is one atomic database transaction — Room wraps this entire method
    // body in a single SQLite transaction — not app-level serialization tied to one repository
    // instance or a process-local Mutex. See the design doc's "Single active workout per device"
    // section for why that distinction is load-bearing: correctness holds even against a second
    // Room instance or coroutine racing this same call, because SQLite itself serializes writer
    // transactions against the same database file, and Room additionally serializes its own
    // @Transaction methods through one transaction executor per database instance.
    //
    // Precise scope of this guarantee (Part A audit): the SUPPORTED workout-start mutation path
    // atomically prevents multiple ACTIVE workouts. This is not the same claim as "the database is
    // structurally incapable of containing multiple ACTIVE rows" — there is no schema-level
    // constraint (see the partial-unique-index note below), so a hypothetical caller bypassing
    // this method via the raw `insert` above could still produce that state. The accurate claim is
    // scoped to this method, not to the schema.
    //
    // A SQLite-level partial unique index (`CREATE UNIQUE INDEX ... WHERE status = 'ACTIVE'`) was
    // evaluated as defense-in-depth per the design doc's instruction to do so, and deliberately not
    // added: Room's declarative @Index annotation cannot express a partial/conditional constraint
    // (only whole-column uniqueness, which would wrongly limit the table to one COMPLETED row ever,
    // not just one ACTIVE row), and adding such an index via raw migration SQL outside Room's own
    // schema declaration would break Room's built-in schema validation on every subsequent
    // migration (Room compares the live database structure against the identity hash it computes
    // from entity annotations, and an undeclared index is exactly the kind of mismatch that
    // validation is designed to catch). The atomic transaction below already closes the actual
    // race, which is the load-bearing property — the constraint would only add defense against a
    // bug in this method itself, at a real ongoing cost (every future migration must additionally
    // account for it). Not worth it for this slice.
    // The single-active-workout invariant is per-owner (local-workout-ownership audit), not
    // per-device: the existence check below is scoped to workout.ownerId, so a different account
    // starting their own workout is never blocked by (or able to see/resume) another account's
    // still-active one.
    @Transaction
    suspend fun startWorkoutIfNoneActive(workout: WorkoutEntity): StartWorkoutResult {
        // ownerId is nullable at the entity/column level only to represent pre-ownership legacy
        // rows (see WorkoutEntity's own doc comment) — the supported start path must always be
        // called with a real, currently-known account id. See WorkoutRepository.startWorkout.
        val ownerId = requireNotNull(workout.ownerId) {
            "startWorkoutIfNoneActive requires a real ownerId; a null ownerId is only ever produced by legacy-row migration, never by the supported start path"
        }
        val existing = getActiveWorkout(ownerId)
        if (existing != null) return StartWorkoutResult.AlreadyActive(existing)
        insert(workout)
        return StartWorkoutResult.Started(workout)
    }

    // Returns the number of rows updated (0 or 1) so callers can detect and reject an invalid
    // transition — e.g. completing an already-COMPLETED workout — rather than that silently
    // no-op-ing with no signal. Deliberately does not touch lastModifiedAt: ordinary completion is
    // not a "correction" (see WorkoutEntity's doc comment) — lastModifiedAt stays null here.
    @Query(
        "UPDATE workouts SET status = 'COMPLETED', completedAt = :completedAt " +
            "WHERE id = :id AND status = 'ACTIVE'",
    )
    suspend fun completeIfActive(id: String, completedAt: Long): Int

    // Discard is a transactional deletion, not a status transition — see WorkoutEntity's doc
    // comment. Cascades to WorkoutExercise/WorkoutSet children via their foreign keys (see
    // WorkoutExerciseEntity/WorkoutSetEntity). Scoped to ACTIVE only: only an in-progress workout
    // can be discarded through this path.
    @Query("DELETE FROM workouts WHERE id = :id AND status = 'ACTIVE'")
    suspend fun deleteIfActive(id: String): Int

    // Bumped only by an explicit historical correction — never by ordinary completion (see
    // completeIfActive above and WorkoutEntity's doc comment). Not called by anything in this
    // slice; retained so the persistence model does not block the future completed-workout-editing
    // capability, per the design doc.
    @Query("UPDATE workouts SET lastModifiedAt = :lastModifiedAt WHERE id = :id AND status = 'COMPLETED'")
    suspend fun touchLastModified(id: String, lastModifiedAt: Long): Int
}
