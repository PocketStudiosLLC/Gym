package com.gymapp.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gymapp.BuildConfig
import com.gymapp.data.Exercise
import com.gymapp.data.ExerciseEntry
import com.gymapp.data.ExerciseMuscleGroup
import com.gymapp.data.GymDatabase
import com.gymapp.data.MuscleGroup
import com.gymapp.data.BodyWeight
import com.gymapp.data.Meal
import com.gymapp.data.PhotoStore
import com.gymapp.data.normalizeSupersets
import com.gymapp.data.Profile
import com.gymapp.data.Sex
import com.gymapp.data.WaterLog
import com.gymapp.data.Routine
import com.gymapp.data.RoutineExercise
import com.gymapp.data.SettingsRepository
import com.gymapp.data.SupersetGroup
import com.gymapp.data.SupersetMember
import com.gymapp.data.ThemeColor
import com.gymapp.data.ThemeMode
import com.gymapp.data.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class Manifest(
    val app: String = "Gym App",
    val appVersion: String,
    val schemaVersion: Int,
    val exportedAt: Long,
)

@Serializable
data class BackupData(
    val exercises: List<BExercise>,
    val entries: List<BEntry>,
    val supersets: List<BSuperset>,
    val preferences: BPreferences,
    /** Added in app 0.2; older backups have none. */
    val routines: List<BRoutine> = emptyList(),
    /** Food tracking, added in app 0.3. */
    val weights: List<BWeight> = emptyList(),
    val meals: List<BMeal> = emptyList(),
    val water: List<BWater> = emptyList(),
)

@Serializable
data class BWeight(val uuid: String, val date: String, val loggedAt: Long, val weightLb: Double)

@Serializable
data class BMeal(
    val uuid: String, val date: String, val loggedAt: Long, val name: String, val hasPhoto: Boolean,
    val calories: Int, val fatG: Double, val proteinG: Double, val carbsG: Double,
)

@Serializable
data class BWater(val uuid: String, val date: String, val loggedAt: Long, val ml: Int, val calories: Int, val creatineG: Double = 0.0)



@Serializable
data class BRoutine(
    val uuid: String,
    val name: String,
    val createdAt: Long,
    val exerciseUuids: List<String>,
    /** Superset marker per exercise (same order as [exerciseUuids]); empty in older backups. */
    val supersetGroups: List<Int?> = emptyList(),
)

@Serializable
data class BExercise(
    val uuid: String,
    val name: String,
    val muscleGroups: List<String>,
    val createdAt: Long,
    val archived: Boolean,
    val hasPhoto: Boolean,
)

@Serializable
data class BEntry(
    val uuid: String,
    val exerciseUuid: String,
    /** ISO date, e.g. 2026-11-16 */
    val date: String,
    val position: Int,
    val sets: List<BSet>,
)

@Serializable
data class BSet(val setIndex: Int, val reps: Int, val weightLb: Double, val completedAt: Long)

@Serializable
data class BSuperset(val uuid: String, val date: String, val colorIndex: Int, val members: List<BMember>)

@Serializable
data class BMember(val entryUuid: String, val startSetIndex: Int)

@Serializable
data class BPreferences(
    val themeMode: String,
    val themeColor: String,
    val calorieGoal: Int? = null,
    val heightFt: Int? = null,
    val heightIn: Double? = null,
    val weightLb: Double? = null,
    val age: Int? = null,
    val sex: String? = null,
)

/** A backup file that has been read and checked, ready to import. */
class ParsedBackup(
    val manifest: Manifest,
    val data: BackupData,
    /** Photo files extracted to cache, keyed by exercise uuid. */
    val photos: Map<String, File>,
) {
    val workoutDays: Int get() = data.entries.map { it.date }.distinct().size
    val firstDate: String? get() = data.entries.minOfOrNull { it.date }
    val lastDate: String? get() = data.entries.maxOfOrNull { it.date }
    val exerciseCount: Int get() = data.exercises.count { !it.archived }
    val setCount: Int get() = data.entries.sumOf { it.sets.size }
    val foodCount: Int get() = data.weights.size + data.meals.size + data.water.size
}

enum class ImportMode { REPLACE, MERGE }

class BackupException(message: String) : Exception(message)

class BackupManager(
    private val context: Context,
    private val db: GymDatabase,
    private val photos: PhotoStore,
    private val settings: SettingsRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val dao = db.dao()
    private val foodDao = db.foodDao()

    // --------------------------------------------------------------- export

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val exercises = dao.allExercises()
        val mgs = dao.allMuscleGroups().groupBy({ it.exerciseId }, { it.muscleGroup.name })
        val entries = dao.allEntries()
        val sets = dao.allSets().groupBy { it.entryId }
        val groups = dao.allGroups()
        val members = dao.allMembers().groupBy { it.groupId }
        val prefs = settings.current()
        val routines = dao.allRoutines()
        val routineRows = dao.allRoutineExercises().groupBy { it.routineId }
        val meals = foodDao.allMeals()

        val exUuid = exercises.associate { it.id to it.uuid }
        val entryUuid = entries.associate { it.id to it.uuid }

        val data = BackupData(
            exercises = exercises.map {
                BExercise(
                    uuid = it.uuid,
                    name = it.name,
                    muscleGroups = mgs[it.id].orEmpty(),
                    createdAt = it.createdAt,
                    archived = it.archived,
                    hasPhoto = it.photoPath?.let(::File)?.exists() == true,
                )
            },
            entries = entries.mapNotNull { e ->
                BEntry(
                    uuid = e.uuid,
                    exerciseUuid = exUuid[e.exerciseId] ?: return@mapNotNull null,
                    date = e.date.toString(),
                    position = e.position,
                    sets = sets[e.id].orEmpty().sortedBy { it.setIndex }
                        .map { BSet(it.setIndex, it.reps, it.weightLb, it.completedAt) },
                )
            },
            supersets = groups.map { g ->
                BSuperset(
                    uuid = g.uuid,
                    date = g.date.toString(),
                    colorIndex = g.colorIndex,
                    members = members[g.id].orEmpty().mapNotNull { m ->
                        BMember(entryUuid[m.entryId] ?: return@mapNotNull null, m.startSetIndex)
                    },
                )
            },
            preferences = settings.currentProfile().let { pr ->
                BPreferences(
                    prefs.mode.name, prefs.color.name,
                    calorieGoal = pr.calorieGoal, heightFt = pr.heightFt, heightIn = pr.heightIn,
                    weightLb = pr.weightLb, age = pr.age, sex = pr.sex?.name,
                )
            },
            routines = routines.map { r ->
                BRoutine(
                    uuid = r.uuid,
                    name = r.name,
                    createdAt = r.createdAt,
                    exerciseUuids = routineRows[r.id].orEmpty().sortedBy { it.position }.mapNotNull { exUuid[it.exerciseId] },
                    supersetGroups = routineRows[r.id].orEmpty().sortedBy { it.position }
                        .filter { exUuid[it.exerciseId] != null }.map { it.supersetGroup },
                )
            },
            weights = foodDao.allWeights().map { BWeight(it.uuid, it.date.toString(), it.loggedAt, it.weightLb) },
            meals = meals.map {
                BMeal(
                    it.uuid, it.date.toString(), it.loggedAt, it.name, it.photoPath?.let(::File)?.exists() == true,
                    it.calories, it.fatG, it.proteinG, it.carbsG,
                )
            },
            water = foodDao.allWater().map { BWater(it.uuid, it.date.toString(), it.loggedAt, it.ml, it.calories, it.creatineG) },
        )
        val manifest = Manifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
        )

        val out = context.contentResolver.openOutputStream(target, "wt")
            ?: throw BackupException("Couldn't open the file for writing.")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
            zip.closeEntry()
            val photoOwners = exercises.map { it.uuid to it.photoPath } + meals.map { it.uuid to it.photoPath }
            for ((uuid, path) in photoOwners) {
                val f = path?.let(::File)?.takeIf { it.exists() } ?: continue
                zip.putNextEntry(ZipEntry("photos/$uuid.jpg"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // --------------------------------------------------------------- import

    /** Reads and validates a backup without changing anything. */
    suspend fun read(source: Uri): ParsedBackup = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        var manifestText: String? = null
        var dataText: String? = null
        val photoFiles = mutableMapOf<String, File>()

        val input = context.contentResolver.openInputStream(source)
            ?: throw BackupException("Couldn't open the file.")
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.isDirectory -> Unit
                        e.name == "manifest.json" -> manifestText = zip.readBytes().decodeToString()
                        e.name == "data.json" -> dataText = zip.readBytes().decodeToString()
                        e.name.startsWith("photos/") && e.name.endsWith(".jpg") -> {
                            val uuid = e.name.removePrefix("photos/").removeSuffix(".jpg")
                            // Guard against path traversal: only accept plain uuid-like names.
                            if (uuid.matches(Regex("[A-Za-z0-9-]+"))) {
                                val f = File(tmp, "$uuid.jpg")
                                f.outputStream().use { zip.copyTo(it) }
                                photoFiles[uuid] = f
                            }
                        }
                    }
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw BackupException("This isn't a Gym App backup file.")
        }

        val manifest = manifestText?.let { runCatching { json.decodeFromString(Manifest.serializer(), it) }.getOrNull() }
            ?: throw BackupException("This isn't a Gym App backup file.")
        if (manifest.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw BackupException("This backup was made by a newer version of Gym App. Update the app and try again.")
        }
        val data = dataText?.let { runCatching { json.decodeFromString(BackupData.serializer(), it) }.getOrNull() }
            ?: throw BackupException("The backup file is damaged.")
        ParsedBackup(manifest, data, photoFiles)
    }

    suspend fun import(backup: ParsedBackup, mode: ImportMode) = withContext(Dispatchers.IO) {
        val d = backup.data
        val newPhotoPaths = mutableListOf<String>()
        try {
            db.withTransaction {
                if (mode == ImportMode.REPLACE) {
                    dao.wipeRoutineExercises(); dao.wipeRoutines()
                    foodDao.wipeWeights(); foodDao.wipeMeals(); foodDao.wipeWater()
                    dao.wipeMembers(); dao.wipeGroups(); dao.wipeSets()
                    dao.wipeEntries(); dao.wipeMuscleGroups(); dao.wipeExercises()
                }

                // Exercises: map backup uuid → local id.
                val existing = dao.allExercises()
                val byUuid = existing.associateBy { it.uuid }
                val byName = existing.filter { !it.archived }.associateBy { it.name.lowercase() }
                val exId = mutableMapOf<String, Long>()
                for (bx in d.exercises) {
                    val match = byUuid[bx.uuid] ?: if (!bx.archived) byName[bx.name.lowercase()] else null
                    if (match != null) {
                        exId[bx.uuid] = match.id
                        continue
                    }
                    val photo = backup.photos[bx.uuid]?.takeIf { it.exists() }?.let {
                        photos.saveBytes(it.readBytes()).also(newPhotoPaths::add)
                    }
                    val id = dao.insertExercise(
                        Exercise(
                            uuid = bx.uuid,
                            name = bx.name,
                            photoPath = photo,
                            createdAt = bx.createdAt,
                            archived = bx.archived,
                        ),
                    )
                    dao.insertMuscleGroups(
                        bx.muscleGroups.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }
                            .distinct().map { ExerciseMuscleGroup(id, it) },
                    )
                    exId[bx.uuid] = id
                }

                // Entries and sets.
                val existingEntries = dao.allEntries().map { it.uuid }.toSet()
                val entryId = mutableMapOf<String, Long>()
                for (be in d.entries) {
                    if (be.uuid in existingEntries) continue
                    val exerciseId = exId[be.exerciseUuid] ?: continue
                    val date = runCatching { LocalDate.parse(be.date) }.getOrNull() ?: continue
                    val position = if (mode == ImportMode.MERGE) dao.maxPosition(date) + 1 + be.position else be.position
                    val id = dao.insertEntry(ExerciseEntry(uuid = be.uuid, exerciseId = exerciseId, date = date, position = position))
                    dao.insertSets(
                        be.sets.map { WorkoutSet(entryId = id, setIndex = it.setIndex, reps = it.reps, weightLb = it.weightLb, completedAt = it.completedAt) },
                    )
                    entryId[be.uuid] = id
                }

                // Supersets (only for entries this import added).
                val existingGroups = dao.allGroups().map { it.uuid }.toSet()
                for (bs in d.supersets) {
                    if (bs.uuid in existingGroups) continue
                    val members = bs.members.mapNotNull { m -> entryId[m.entryUuid]?.let { it to m.startSetIndex } }
                    if (members.size < 2) continue
                    val date = runCatching { LocalDate.parse(bs.date) }.getOrNull() ?: continue
                    val gid = dao.insertGroup(SupersetGroup(uuid = bs.uuid, date = date, colorIndex = bs.colorIndex))
                    members.forEach { (eid, start) -> dao.insertMember(SupersetMember(gid, eid, start)) }
                }

                // Food tracking.
                fun day(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
                val haveWeights = foodDao.allWeights().map { it.uuid }.toSet()
                d.weights.filter { it.uuid !in haveWeights }.forEach { w ->
                    val date = day(w.date) ?: return@forEach
                    foodDao.insertWeight(BodyWeight(uuid = w.uuid, date = date, loggedAt = w.loggedAt, weightLb = w.weightLb))
                }
                val haveMeals = foodDao.allMeals().map { it.uuid }.toSet()
                d.meals.filter { it.uuid !in haveMeals }.forEach { m ->
                    val date = day(m.date) ?: return@forEach
                    val photo = backup.photos[m.uuid]?.takeIf { it.exists() }?.let {
                        photos.saveBytes(it.readBytes()).also(newPhotoPaths::add)
                    }
                    foodDao.insertMeal(
                        Meal(
                            uuid = m.uuid, date = date, loggedAt = m.loggedAt, name = m.name, photoPath = photo,
                            calories = m.calories, fatG = m.fatG, proteinG = m.proteinG, carbsG = m.carbsG,
                        ),
                    )
                }
                val haveWater = foodDao.allWater().map { it.uuid }.toSet()
                d.water.filter { it.uuid !in haveWater }.forEach { w ->
                    val date = day(w.date) ?: return@forEach
                    foodDao.insertWater(WaterLog(uuid = w.uuid, date = date, loggedAt = w.loggedAt, ml = w.ml, calories = w.calories, creatineG = w.creatineG))
                }

                // Routines.
                val existingRoutines = dao.allRoutines().map { it.uuid }.toSet()
                for (br in d.routines) {
                    if (br.uuid in existingRoutines) continue
                    val rid = dao.insertRoutine(Routine(uuid = br.uuid, name = br.name, createdAt = br.createdAt))
                    dao.insertRoutineExercises(
                        br.exerciseUuids.mapIndexedNotNull { i, u -> exId[u]?.let { it to br.supersetGroups.getOrNull(i) } }
                            .let { kept -> kept.zip(normalizeSupersets(kept.map { it.second })) }
                            .mapIndexed { i, (pair, group) -> RoutineExercise(rid, i, pair.first, group) },
                    )
                }
            }
        } catch (t: Throwable) {
            newPhotoPaths.forEach { photos.delete(it) }
            throw t
        }

        if (mode == ImportMode.REPLACE) {
            // Remove photos that no longer belong to any exercise.
            val keep = (dao.allExercises().mapNotNull { it.photoPath } + foodDao.allMeals().mapNotNull { it.photoPath }).toSet()
            photos.photoDir.listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
            runCatching { settings.setMode(ThemeMode.valueOf(d.preferences.themeMode)) }
            runCatching { settings.setColor(ThemeColor.valueOf(d.preferences.themeColor)) }
            d.preferences.let { bp ->
                // Older backups have no profile; keep what's on the phone in that case.
                val hasProfile = listOf(bp.calorieGoal, bp.heightFt, bp.heightIn, bp.weightLb, bp.age, bp.sex).any { it != null }
                if (hasProfile) {
                    settings.setProfile(
                        Profile(
                            bp.calorieGoal, bp.heightFt, bp.heightIn, bp.weightLb, bp.age,
                            bp.sex?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
                        ),
                    )
                }
            }
        }
        File(context.cacheDir, "import").deleteRecursively()
    }
}
