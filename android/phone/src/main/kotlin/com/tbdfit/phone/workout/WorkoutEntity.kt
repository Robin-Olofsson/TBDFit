package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tbdfit.phone.localaccount.LocalAccountEntity

// Discard is intentionally not a status value — see the design doc's Active-Workout State Model
// (hardening-pass revision): discarding an ACTIVE workout is a transactional row deletion, not a
// third status. Keeping status to exactly these two values means there is nothing to filter around
// or explain to a future contributor. See WorkoutDao.deleteIfActive.
enum class WorkoutStatus { ACTIVE, COMPLETED }

// `completedAt` (when the training session actually ended) and `lastModifiedAt` (when the recorded
// history was last corrected after the fact) are deliberately separate fields and must never be
// conflated — see the design doc's Completed-Workout Editing section. A future historical
// correction updates `lastModifiedAt` and must NEVER change `completedAt`. `lastModifiedAt` stays
// null until the first correction is ever made — ordinary completion does not set it (see
// WorkoutDao.completeIfActive).
//
// `ownerId` — local-account-ownership correction: a real foreign key to LocalAccountEntity.id, not
// a free-form string (see the earlier ownerId-as-plain-string audit fix and LocalAccountEntity's own
// doc comment for why a plain column alone was judged insufficient). Local-only, never requires a
// network call to read or write (the referenced LocalAccount row and the value itself are already
// resolved synchronously before any owner-scoped write — see WorkoutRepository.ensureLocalAccountExists)
// — this is account SCOPING, not remote sync or authorization. Every query that finds "the" active
// workout must filter by this column; there is no code path that returns an active workout across
// owners (see WorkoutDao). The single-active-workout invariant from Slice 1 is consequently
// per-owner, not per-device, now that a device can be used by more than one account over time — see
// WorkoutDao.startWorkoutIfNoneActive.
//
// Nullable, and ON DELETE RESTRICT rather than CASCADE: RESTRICT so a LocalAccount row can never be
// deleted out from under a workout it owns (there is no LocalAccount-delete path in the app today,
// but the constraint exists so accidentally adding one later can't silently destroy workout
// history). Nullable to correctly represent rows that predate account scoping entirely (migrated
// from v4/v5 with no recoverable owner — see MIGRATION_5_6): those rows get NULL, deliberately never
// a guessed real account, so they become correctly and permanently invisible to every owner-scoped
// query (NULL never equals a real ownerId string) rather than silently reassigned. What should
// happen to any such orphaned rows remains an open product decision, not resolved here.
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = LocalAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("ownerId")],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val ownerId: String?,
    val status: WorkoutStatus,
    val startedAt: Long,
    val completedAt: Long? = null,
    val lastModifiedAt: Long? = null,
    val createdAt: Long,
)
