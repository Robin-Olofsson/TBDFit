package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// A single planned set within a RoutineExercise — intent ("3 sets of 8 at 80 kg"), never a
// performed result. Deliberately a separate entity family from WorkoutSet, full stop — see
// program-routine-first-slice-design.md's Planning vs. Execution section. No isCompleted/
// completedAt here: completion is an execution-time concept that does not apply to a plan.
//
// `plannedReps`/`plannedWeight` are both nullable — a set may specify only load, only reps, or
// neither (mirrors WorkoutSet.weight's own nullability for bodyweight movements).
@Entity(
    tableName = "routine_planned_sets",
    foreignKeys = [
        ForeignKey(
            entity = RoutineExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineExerciseId")],
)
data class RoutinePlannedSetEntity(
    @PrimaryKey val id: String,
    val routineExerciseId: String,
    val position: Int,
    val plannedReps: Int? = null,
    val plannedWeight: Double? = null,
)
