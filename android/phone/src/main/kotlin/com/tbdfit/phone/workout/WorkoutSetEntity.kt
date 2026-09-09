package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Deliberately a strength-set (reps x load) model for this slice only — not a universal
// representation for future cardio/duration/distance exercise types. See the design doc's Minimum
// Set Model section: a future cardio domain gets its own new table referencing WorkoutExercise the
// same way this one does, rather than generic columns bolted on here. Do not add setType/RPE/RIR/
// duration/distance columns to this entity without revisiting that design decision first.
//
// `weight`/`reps` are nullable (a bodyweight-only set may have no logged weight) and enforced
// non-negative at the DAO boundary (see WorkoutSetDao) — Room has no declarative CHECK-constraint
// mechanism used elsewhere in this codebase, so the DAO is the chosen enforcement layer.
//
// `targetReps`/`targetWeight` — Slice A of program-routine-first-slice-design.md's execution target
// snapshot (hardening pass): copied ONCE, at START, from the source RoutinePlannedSet (see
// WorkoutRepository.startRoutine), and never re-read from the source afterward. Structurally
// independent from `reps`/`weight` (the actual performed result) and from `isCompleted`/
// `completedAt` — a set awaiting execution has a populated target and a null actual, exactly as
// before this slice, just with a target now visible alongside it. Null for a plain manually-added
// set (no source plan) — the same nullable columns, no special case.
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val position: Int,
    val weight: Double? = null,
    val reps: Int? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val targetReps: Int? = null,
    val targetWeight: Double? = null,
)
