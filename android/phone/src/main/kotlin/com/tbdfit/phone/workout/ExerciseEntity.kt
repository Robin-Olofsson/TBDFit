package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tbdfit.phone.localaccount.LocalAccountEntity

// Distinguishes seeded catalog rows from user-created ones — descriptive metadata only (used for
// exercise-picker grouping and to scope a future catalog-update migration to the rows it's allowed
// to touch). Must never be used to branch identity, previous-performance, or replication logic —
// see the design doc's Exercise Identity section for why BUILT_IN and CUSTOM are deliberately the
// same kind of row in every way that matters otherwise.
enum class ExerciseSource { BUILT_IN, CUSTOM }

// Exercise identity is the stable `id`, not `name` — see
// docs/architecture/strength-workout-first-slice-design.md's Exercise Identity section (and its
// hardening-pass correction retracting an earlier username-normalization analogy). Renaming this
// exercise, or two exercises sharing an identical/similar name, are both safe: nothing outside this
// entity ever looks up an Exercise by name, only by `id` — see WorkoutExerciseEntity.
//
// `normalizedName` is a plain app-computed field (lower-cased/trimmed at write time — not a
// database-generated column), used only as a search key and a non-blocking duplicate-creation
// hint. It deliberately carries no uniqueness constraint: "Bench Press", "Incline Bench Press", and
// "Smith Machine Bench Press" must coexist, and even an exact normalizedName match between two rows
// is not itself invalid.
//
// `ownerId` — local-account-ownership correction: null for every BUILT_IN row, always (the catalog
// is global — see BuiltInExerciseCatalog.kt — and visible to every account regardless of ownerId).
// For a CUSTOM row it is the owning LocalAccountEntity's id, enforced by a real foreign key rather
// than a free-form string, with ON DELETE RESTRICT so a LocalAccount row can never be removed out
// from under an owned custom exercise.
//
// CUSTOM rows created before this column existed also have ownerId = null, but that is deliberately
// NOT treated as "visible to everyone the way BUILT_IN is": ExerciseDao's owner-scoped query
// distinguishes by `source`, never by ownerId nullness alone (see ExerciseDao.getVisibleTo) — a
// CUSTOM row with no recorded owner is correctly invisible to every account, the same
// surfaced-not-guessed treatment already used for Workout's pre-ownership orphans (see
// WorkoutEntity's own doc comment).
@Entity(
    tableName = "exercises",
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
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val source: ExerciseSource,
    val ownerId: String?,
    val createdAt: Long,
)
