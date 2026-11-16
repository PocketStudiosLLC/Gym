package com.gymapp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun groupToString(g: MuscleGroup?): String? = g?.name
    @TypeConverter fun stringToGroup(s: String?): MuscleGroup? = s?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
}

@Dao
interface GymDao {
    // ---- Exercises ----
    @Query("SELECT * FROM exercise")
    fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise")
    suspend fun allExercises(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun exercise(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE LOWER(name) = LOWER(:name) AND archived = 0 LIMIT 1")
    suspend fun activeExerciseByName(name: String): Exercise?

    @Insert suspend fun insertExercise(e: Exercise): Long
    @Update suspend fun updateExercise(e: Exercise)

    @Query("SELECT * FROM exercise_muscle_group")
    fun observeMuscleGroups(): Flow<List<ExerciseMuscleGroup>>

    @Query("SELECT * FROM exercise_muscle_group")
    suspend fun allMuscleGroups(): List<ExerciseMuscleGroup>

    @Query("SELECT * FROM exercise_muscle_group WHERE exerciseId = :exerciseId")
    suspend fun muscleGroupsFor(exerciseId: Long): List<ExerciseMuscleGroup>

    @Query("DELETE FROM exercise_muscle_group WHERE exerciseId = :exerciseId")
    suspend fun clearMuscleGroups(exerciseId: Long)

    @Insert suspend fun insertMuscleGroups(rows: List<ExerciseMuscleGroup>)

    /** Most recent date each exercise was used, for "recent first" ordering. */
    @Query("SELECT exerciseId, MAX(date) AS lastDate FROM exercise_entry GROUP BY exerciseId")
    fun observeLastUsed(): Flow<List<LastUsed>>

    // ---- Entries ----
    @Query("SELECT * FROM exercise_entry WHERE date = :date ORDER BY position, id")
    fun observeEntries(date: LocalDate): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercise_entry WHERE date = :date ORDER BY position, id")
    suspend fun entries(date: LocalDate): List<ExerciseEntry>

    @Query("SELECT * FROM exercise_entry")
    suspend fun allEntries(): List<ExerciseEntry>

    @Query("SELECT * FROM exercise_entry WHERE id = :id")
    suspend fun entry(id: Long): ExerciseEntry?

    @Query("SELECT COALESCE(MAX(position), -1) FROM exercise_entry WHERE date = :date")
    suspend fun maxPosition(date: LocalDate): Int

    @Insert suspend fun insertEntry(e: ExerciseEntry): Long
    @Update suspend fun updateEntries(e: List<ExerciseEntry>)
    @Delete suspend fun deleteEntry(e: ExerciseEntry)

    // ---- Sets ----
    @Query(
        "SELECT s.* FROM workout_set s JOIN exercise_entry e ON s.entryId = e.id " +
            "WHERE e.date = :date ORDER BY s.entryId, s.setIndex",
    )
    fun observeSetsForDate(date: LocalDate): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_set WHERE entryId = :entryId ORDER BY setIndex")
    suspend fun sets(entryId: Long): List<WorkoutSet>

    @Query("SELECT * FROM workout_set")
    suspend fun allSets(): List<WorkoutSet>

    @Query(
        "SELECT COUNT(*) FROM workout_set s JOIN exercise_entry e ON s.entryId = e.id WHERE e.date = :date",
    )
    suspend fun setCountForDate(date: LocalDate): Int

    @Insert suspend fun insertSet(s: WorkoutSet): Long
    @Insert suspend fun insertSets(s: List<WorkoutSet>)
    @Update suspend fun updateSet(s: WorkoutSet)
    @Update suspend fun updateSets(s: List<WorkoutSet>)
    @Delete suspend fun deleteSet(s: WorkoutSet)

    // ---- Supersets ----
    @Query("SELECT * FROM superset_group WHERE date = :date")
    fun observeGroups(date: LocalDate): Flow<List<SupersetGroup>>

    @Query("SELECT * FROM superset_group WHERE date = :date")
    suspend fun groups(date: LocalDate): List<SupersetGroup>

    @Query("SELECT * FROM superset_group")
    suspend fun allGroups(): List<SupersetGroup>

    @Query(
        "SELECT m.* FROM superset_member m JOIN superset_group g ON m.groupId = g.id WHERE g.date = :date",
    )
    fun observeMembers(date: LocalDate): Flow<List<SupersetMember>>

    @Query(
        "SELECT m.* FROM superset_member m JOIN superset_group g ON m.groupId = g.id WHERE g.date = :date",
    )
    suspend fun members(date: LocalDate): List<SupersetMember>

    @Query("SELECT * FROM superset_member")
    suspend fun allMembers(): List<SupersetMember>

    @Query("SELECT * FROM superset_member WHERE entryId = :entryId")
    suspend fun memberFor(entryId: Long): SupersetMember?

    @Query("SELECT * FROM superset_member WHERE groupId = :groupId")
    suspend fun membersOf(groupId: Long): List<SupersetMember>

    @Insert suspend fun insertGroup(g: SupersetGroup): Long
    @Insert suspend fun insertMember(m: SupersetMember)
    @Update suspend fun updateMember(m: SupersetMember)
    @Delete suspend fun deleteMember(m: SupersetMember)

    @Query("DELETE FROM superset_group WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM superset_group WHERE date = :date")
    suspend fun deleteGroupsForDate(date: LocalDate)

    // ---- Analytics ----
    @Query(
        "SELECT e.id AS entryId, x.id AS exerciseId, x.name AS exerciseName, e.date AS date, " +
            "s.reps AS reps, s.weightLb AS weightLb " +
            "FROM workout_set s JOIN exercise_entry e ON s.entryId = e.id " +
            "JOIN exercise x ON e.exerciseId = x.id " +
            "WHERE e.date BETWEEN :from AND :to",
    )
    fun observeFacts(from: LocalDate, to: LocalDate): Flow<List<SetFact>>

    // ---- Routines ----
    @Query("SELECT * FROM routine ORDER BY name COLLATE NOCASE")
    fun observeRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routine_exercise ORDER BY routineId, position")
    fun observeRoutineExercises(): Flow<List<RoutineExercise>>

    @Query("SELECT * FROM routine") suspend fun allRoutines(): List<Routine>
    @Query("SELECT * FROM routine_exercise ORDER BY routineId, position") suspend fun allRoutineExercises(): List<RoutineExercise>
    @Query("SELECT * FROM routine WHERE id = :id") suspend fun routine(id: Long): Routine?

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY position")
    suspend fun routineExercises(routineId: Long): List<RoutineExercise>

    @Insert suspend fun insertRoutine(r: Routine): Long
    @Update suspend fun updateRoutine(r: Routine)
    @Insert suspend fun insertRoutineExercises(rows: List<RoutineExercise>)

    @Query("DELETE FROM routine_exercise WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: Long)

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    // ---- Wipe (import → replace) ----
    @Query("DELETE FROM superset_member") suspend fun wipeMembers()
    @Query("DELETE FROM superset_group") suspend fun wipeGroups()
    @Query("DELETE FROM workout_set") suspend fun wipeSets()
    @Query("DELETE FROM exercise_entry") suspend fun wipeEntries()
    @Query("DELETE FROM exercise_muscle_group") suspend fun wipeMuscleGroups()
    @Query("DELETE FROM exercise") suspend fun wipeExercises()
    @Query("DELETE FROM routine_exercise") suspend fun wipeRoutineExercises()
    @Query("DELETE FROM routine") suspend fun wipeRoutines()
}

data class LastUsed(val exerciseId: Long, val lastDate: LocalDate)

@Database(
    entities = [
        Exercise::class,
        ExerciseMuscleGroup::class,
        ExerciseEntry::class,
        WorkoutSet::class,
        SupersetGroup::class,
        SupersetMember::class,
        Routine::class,
        RoutineExercise::class,
        BodyWeight::class,
        Meal::class,
        WaterLog::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao
    abstract fun foodDao(): FoodDao

    companion object {
        fun create(context: Context): GymDatabase =
            Room.databaseBuilder(context, GymDatabase::class.java, "gym.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** v5 lets routines contain supersets. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `supersetGroup` INTEGER")
            }
        }

        /** v4 drops supplements and records creatine mixed into water. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `water` ADD COLUMN `creatineG` REAL NOT NULL DEFAULT 0")
                db.execSQL("DROP TABLE IF EXISTS `supplement`")
            }
        }

        /** v3 adds food tracking: body weight, meals, water and supplements. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `body_weight` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `date` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `weightLb` REAL NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weight_date` ON `body_weight` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_body_weight_uuid` ON `body_weight` (`uuid`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `meal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `date` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `name` TEXT NOT NULL, `photoPath` TEXT, `calories` INTEGER NOT NULL, `fatG` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_date` ON `meal` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_uuid` ON `meal` (`uuid`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `water` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `date` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `ml` INTEGER NOT NULL, `calories` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_date` ON `water` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_water_uuid` ON `water` (`uuid`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `supplement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `date` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplement_date` ON `supplement` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplement_uuid` ON `supplement` (`uuid`)")
            }
        }

        /** v2 adds routines. Existing workout data is untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_routine_uuid` ON `routine` (`uuid`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_exercise` (`routineId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, PRIMARY KEY(`routineId`, `position`), " +
                        "FOREIGN KEY(`routineId`) REFERENCES `routine`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_exercise_exerciseId` ON `routine_exercise` (`exerciseId`)")
            }
        }
    }
}
