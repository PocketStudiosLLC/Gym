package com.gymapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class MuscleGroup(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    CORE("Core/Abs"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    FULL_BODY("Full Body"),
    CARDIO("Cardio"),
}

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    /** Absolute path of the photo in app-private storage, or null. */
    val photoPath: String?,
    val createdAt: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "exercise_muscle_group",
    primaryKeys = ["exerciseId", "muscleGroup"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseMuscleGroup(
    val exerciseId: Long,
    val muscleGroup: MuscleGroup,
)

@Entity(
    tableName = "exercise_entry",
    indices = [Index("date"), Index("exerciseId"), Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val exerciseId: Long,
    val date: LocalDate,
    val position: Int,
)

@Entity(
    tableName = "workout_set",
    indices = [Index("entryId")],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /** 1-based order within the entry. */
    val setIndex: Int,
    val reps: Int,
    val weightLb: Double,
    val completedAt: Long,
)

@Entity(
    tableName = "superset_group",
    indices = [Index("date"), Index(value = ["uuid"], unique = true)],
)
data class SupersetGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val date: LocalDate,
    val colorIndex: Int,
)

@Entity(
    tableName = "superset_member",
    primaryKeys = ["groupId", "entryId"],
    indices = [Index(value = ["entryId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SupersetGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SupersetMember(
    val groupId: Long,
    val entryId: Long,
    /** The set index at which this entry joined the superset; it covers every set from here on. */
    val startSetIndex: Int,
)

/** Flat row used by analytics. */
data class SetFact(
    val entryId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val date: LocalDate,
    val reps: Int,
    val weightLb: Double,
)

/** A named, ordered list of exercises the user can start in one tap. */
@Entity(
    tableName = "routine",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "routine_exercise",
    primaryKeys = ["routineId", "position"],
    indices = [Index("exerciseId")],
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutineExercise(
    val routineId: Long,
    /** 0-based order within the routine. */
    val position: Int,
    val exerciseId: Long,
    /**
     * Superset marker: consecutive rows with the same non-null value are done as a superset
     * when the routine is started. Null = not in a superset.
     */
    val supersetGroup: Int? = null,
)
