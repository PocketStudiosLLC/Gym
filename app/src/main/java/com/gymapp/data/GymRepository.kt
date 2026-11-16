package com.gymapp.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID

const val SUPERSET_COLOR_COUNT = 6

data class SupersetInfo(
    val groupId: Long,
    val colorIndex: Int,
    val startSetIndex: Int,
    val partnerNames: List<String>,
)

data class DayEntry(
    val entry: ExerciseEntry,
    val exercise: Exercise,
    val muscleGroups: List<MuscleGroup>,
    val sets: List<WorkoutSet>,
    val superset: SupersetInfo?,
) {
    fun isInSuperset(set: WorkoutSet): Boolean =
        superset != null && set.setIndex >= superset.startSetIndex
}

data class RoutineWithExercises(val routine: Routine, val exercises: List<Exercise>)

data class LibraryExercise(
    val exercise: Exercise,
    val muscleGroups: List<MuscleGroup>,
    val lastUsed: LocalDate?,
)

/** Superset state of one day, captured so a change can be undone. */
data class SupersetSnapshot(
    val date: LocalDate,
    val groups: List<SupersetGroup>,
    val members: List<SupersetMember>,
)

/** What is needed to undo a set deletion. */
data class DeletedSet(val set: WorkoutSet, val supersets: SupersetSnapshot)

class GymRepository(
    private val db: GymDatabase,
    /** Called after a set is logged, with the date it was logged on. */
    private val onSetLogged: suspend (LocalDate) -> Unit,
) {
    private val dao = db.dao()

    // ------------------------------------------------------------------ reads

    fun observeDay(date: LocalDate): Flow<List<DayEntry>> {
        val base = combine(
            dao.observeEntries(date),
            dao.observeExercises(),
            dao.observeMuscleGroups(),
            dao.observeSetsForDate(date),
        ) { entries, exercises, mgs, sets -> Base(entries, exercises, mgs, sets) }

        return combine(base, dao.observeGroups(date), dao.observeMembers(date)) { b, groups, members ->
            val exById = b.exercises.associateBy { it.id }
            val mgByEx = b.muscleGroups.groupBy({ it.exerciseId }, { it.muscleGroup })
            val setsByEntry = b.sets.groupBy { it.entryId }
            val memberByEntry = members.associateBy { it.entryId }
            val groupById = groups.associateBy { it.id }
            val entryById = b.entries.associateBy { it.id }

            b.entries.mapNotNull { entry ->
                val ex = exById[entry.exerciseId] ?: return@mapNotNull null
                val member = memberByEntry[entry.id]
                val info = member?.let { m ->
                    val group = groupById[m.groupId] ?: return@let null
                    val partners = members
                        .filter { it.groupId == m.groupId && it.entryId != entry.id }
                        .mapNotNull { p -> entryById[p.entryId]?.let { exById[it.exerciseId]?.name } }
                    SupersetInfo(group.id, group.colorIndex, m.startSetIndex, partners)
                }
                DayEntry(
                    entry = entry,
                    exercise = ex,
                    muscleGroups = sortGroups(mgByEx[ex.id].orEmpty()),
                    sets = setsByEntry[entry.id].orEmpty().sortedBy { it.setIndex },
                    superset = info,
                )
            }
        }
    }

    private data class Base(
        val entries: List<ExerciseEntry>,
        val exercises: List<Exercise>,
        val muscleGroups: List<ExerciseMuscleGroup>,
        val sets: List<WorkoutSet>,
    )

    fun observeLibrary(): Flow<List<LibraryExercise>> =
        combine(dao.observeExercises(), dao.observeMuscleGroups(), dao.observeLastUsed()) { ex, mgs, last ->
            val mgByEx = mgs.groupBy({ it.exerciseId }, { it.muscleGroup })
            val lastByEx = last.associate { it.exerciseId to it.lastDate }
            ex.filter { !it.archived }
                .map { LibraryExercise(it, sortGroups(mgByEx[it.id].orEmpty()), lastByEx[it.id]) }
                .sortedWith(
                    compareByDescending<LibraryExercise> { it.lastUsed ?: LocalDate.MIN }
                        .thenBy { it.exercise.name.lowercase() },
                )
        }

    fun observeFacts(from: LocalDate, to: LocalDate): Flow<List<SetFact>> = dao.observeFacts(from, to)

    fun observeMuscleGroupMap(): Flow<Map<Long, List<MuscleGroup>>> =
        combine(dao.observeMuscleGroups(), dao.observeExercises()) { mgs, _ ->
            mgs.groupBy({ it.exerciseId }, { it.muscleGroup })
        }

    fun observeRoutines(): Flow<List<RoutineWithExercises>> =
        combine(dao.observeRoutines(), dao.observeRoutineExercises(), dao.observeExercises()) { routines, rows, exercises ->
            val exById = exercises.associateBy { it.id }
            val byRoutine = rows.groupBy { it.routineId }
            routines.map { r ->
                RoutineWithExercises(r, byRoutine[r.id].orEmpty().sortedBy { it.position }.mapNotNull { exById[it.exerciseId] })
            }
        }

    suspend fun routineWithExercises(id: Long): RoutineWithExercises? {
        val r = dao.routine(id) ?: return null
        val exById = dao.allExercises().associateBy { it.id }
        return RoutineWithExercises(r, dao.routineExercises(id).mapNotNull { exById[it.exerciseId] })
    }

    /** Creates ([id] null) or replaces a routine with [exerciseIds] in order. Returns its id. */
    suspend fun saveRoutine(id: Long?, name: String, exerciseIds: List<Long>): Long = db.withTransaction {
        val routineId = if (id == null) {
            dao.insertRoutine(Routine(uuid = UUID.randomUUID().toString(), name = name.trim(), createdAt = System.currentTimeMillis()))
        } else {
            val r = dao.routine(id) ?: return@withTransaction id
            dao.updateRoutine(r.copy(name = name.trim()))
            dao.clearRoutineExercises(id)
            id
        }
        dao.insertRoutineExercises(exerciseIds.mapIndexed { i, ex -> RoutineExercise(routineId, i, ex) })
        routineId
    }

    suspend fun deleteRoutine(id: Long) = dao.deleteRoutine(id)

    /** Adds every exercise of the routine to [date], in the routine's order. Returns the new entry ids. */
    suspend fun startRoutine(date: LocalDate, routineId: Long): List<Long> = db.withTransaction {
        val rows = dao.routineExercises(routineId)
        var position = dao.maxPosition(date)
        rows.map { row ->
            position += 1
            dao.insertEntry(
                ExerciseEntry(uuid = UUID.randomUUID().toString(), exerciseId = row.exerciseId, date = date, position = position),
            )
        }
    }

    suspend fun exercise(id: Long): Exercise? = dao.exercise(id)
    suspend fun muscleGroupsFor(id: Long): List<MuscleGroup> = sortGroups(dao.muscleGroupsFor(id).map { it.muscleGroup })

    /** True when another active exercise already uses [name] (case-insensitive). */
    suspend fun isNameTaken(name: String, exceptId: Long?): Boolean {
        val hit = dao.activeExerciseByName(name.trim()) ?: return false
        return hit.id != exceptId
    }

    // -------------------------------------------------------------- exercises

    suspend fun createExercise(name: String, groups: Set<MuscleGroup>, photoPath: String?): Long =
        db.withTransaction {
            val id = dao.insertExercise(
                Exercise(
                    uuid = UUID.randomUUID().toString(),
                    name = name.trim(),
                    photoPath = photoPath,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            dao.insertMuscleGroups(groups.map { ExerciseMuscleGroup(id, it) })
            id
        }

    suspend fun updateExercise(id: Long, name: String, groups: Set<MuscleGroup>, photoPath: String?) =
        db.withTransaction {
            val ex = dao.exercise(id) ?: return@withTransaction
            dao.updateExercise(ex.copy(name = name.trim(), photoPath = photoPath))
            dao.clearMuscleGroups(id)
            dao.insertMuscleGroups(groups.map { ExerciseMuscleGroup(id, it) })
        }

    /** Soft delete: removes it from the library but keeps its history. */
    suspend fun archiveExercise(id: Long) {
        val ex = dao.exercise(id) ?: return
        dao.updateExercise(ex.copy(archived = true))
    }

    // ---------------------------------------------------------------- entries

    suspend fun addEntry(date: LocalDate, exerciseId: Long): Long = db.withTransaction {
        dao.insertEntry(
            ExerciseEntry(
                uuid = UUID.randomUUID().toString(),
                exerciseId = exerciseId,
                date = date,
                position = dao.maxPosition(date) + 1,
            ),
        )
    }

    suspend fun deleteEntry(entryId: Long) = db.withTransaction {
        val entry = dao.entry(entryId) ?: return@withTransaction
        dao.deleteEntry(entry)
        cleanupGroups(entry.date)
    }

    /** Moves an entry up (delta = -1) or down (delta = +1) in its day's list. */
    suspend fun moveEntry(entryId: Long, delta: Int) = db.withTransaction {
        val entry = dao.entry(entryId) ?: return@withTransaction
        val list = dao.entries(entry.date).toMutableList()
        val from = list.indexOfFirst { it.id == entryId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@withTransaction
        list.add(to, list.removeAt(from))
        dao.updateEntries(list.mapIndexed { i, e -> e.copy(position = i) })
    }

    // ------------------------------------------------------------------- sets

    suspend fun addSet(entryId: Long, reps: Int, weightLb: Double) {
        val date = db.withTransaction {
            val entry = dao.entry(entryId) ?: return@withTransaction null
            val next = (dao.sets(entryId).maxOfOrNull { it.setIndex } ?: 0) + 1
            dao.insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = next,
                    reps = reps,
                    weightLb = weightLb,
                    completedAt = System.currentTimeMillis(),
                ),
            )
            entry.date
        } ?: return
        onSetLogged(date)
    }

    suspend fun updateSet(set: WorkoutSet, reps: Int, weightLb: Double) =
        dao.updateSet(set.copy(reps = reps, weightLb = weightLb))

    suspend fun deleteSet(set: WorkoutSet): DeletedSet? = db.withTransaction {
        val entry = dao.entry(set.entryId) ?: return@withTransaction null
        val snapshot = snapshot(entry.date)
        dao.deleteSet(set)
        val later = dao.sets(set.entryId).filter { it.setIndex > set.setIndex }
        if (later.isNotEmpty()) dao.updateSets(later.map { it.copy(setIndex = it.setIndex - 1) })

        dao.memberFor(entry.id)?.let { m ->
            val newStart = if (set.setIndex < m.startSetIndex) m.startSetIndex - 1 else m.startSetIndex
            val remaining = dao.sets(entry.id)
            if (remaining.none { it.setIndex >= newStart }) {
                dao.deleteMember(m)
            } else if (newStart != m.startSetIndex) {
                dao.updateMember(m.copy(startSetIndex = newStart))
            }
        }
        cleanupGroups(entry.date)
        DeletedSet(set, snapshot)
    }

    suspend fun undoDeleteSet(deleted: DeletedSet) = db.withTransaction {
        val s = deleted.set
        val later = dao.sets(s.entryId).filter { it.setIndex >= s.setIndex }
        if (later.isNotEmpty()) dao.updateSets(later.map { it.copy(setIndex = it.setIndex + 1) })
        dao.insertSet(s)
        restore(deleted.supersets)
    }

    // -------------------------------------------------------------- supersets

    /**
     * Links set [sourceSetIndex] of [sourceEntryId] with set [targetSetIndex] of [targetEntryId].
     * Every set from those points on (including sets added later) belongs to the superset.
     * Returns a snapshot for undo, or null if nothing changed.
     */
    suspend fun createSuperset(
        sourceEntryId: Long,
        sourceSetIndex: Int,
        targetEntryId: Long,
        targetSetIndex: Int,
    ): SupersetSnapshot? = db.withTransaction {
        if (sourceEntryId == targetEntryId) return@withTransaction null
        val src = dao.entry(sourceEntryId) ?: return@withTransaction null
        val tgt = dao.entry(targetEntryId) ?: return@withTransaction null
        if (src.date != tgt.date) return@withTransaction null
        val date = src.date
        val snapshot = snapshot(date)

        val srcMember = dao.memberFor(src.id)
        val tgtMember = dao.memberFor(tgt.id)

        when {
            srcMember != null && tgtMember != null && srcMember.groupId == tgtMember.groupId -> {
                dao.updateMember(srcMember.copy(startSetIndex = sourceSetIndex))
                dao.updateMember(tgtMember.copy(startSetIndex = targetSetIndex))
            }
            tgtMember != null -> {
                // Join the target's group (moving out of any other group).
                srcMember?.let { dao.deleteMember(it) }
                dao.insertMember(SupersetMember(tgtMember.groupId, src.id, sourceSetIndex))
                if (targetSetIndex < tgtMember.startSetIndex) {
                    dao.updateMember(tgtMember.copy(startSetIndex = targetSetIndex))
                }
            }
            srcMember != null -> {
                dao.insertMember(SupersetMember(srcMember.groupId, tgt.id, targetSetIndex))
                if (sourceSetIndex < srcMember.startSetIndex) {
                    dao.updateMember(srcMember.copy(startSetIndex = sourceSetIndex))
                }
            }
            else -> {
                val groupId = dao.insertGroup(
                    SupersetGroup(
                        uuid = UUID.randomUUID().toString(),
                        date = date,
                        colorIndex = nextColor(date),
                    ),
                )
                dao.insertMember(SupersetMember(groupId, src.id, sourceSetIndex))
                dao.insertMember(SupersetMember(groupId, tgt.id, targetSetIndex))
            }
        }
        cleanupGroups(date)
        snapshot
    }

    suspend fun removeFromSuperset(entryId: Long): SupersetSnapshot? = db.withTransaction {
        val entry = dao.entry(entryId) ?: return@withTransaction null
        val member = dao.memberFor(entryId) ?: return@withTransaction null
        val snapshot = snapshot(entry.date)
        dao.deleteMember(member)
        cleanupGroups(entry.date)
        snapshot
    }

    suspend fun restore(snapshot: SupersetSnapshot) = db.withTransaction {
        dao.deleteGroupsForDate(snapshot.date)
        snapshot.groups.forEach { dao.insertGroup(it) }
        val existing = dao.entries(snapshot.date).map { it.id }.toSet()
        snapshot.members.filter { it.entryId in existing }.forEach { dao.insertMember(it) }
        cleanupGroups(snapshot.date)
    }

    private suspend fun snapshot(date: LocalDate) =
        SupersetSnapshot(date, dao.groups(date), dao.members(date))

    private suspend fun nextColor(date: LocalDate): Int {
        val groups = dao.groups(date)
        val used = groups.map { it.colorIndex }.toSet()
        return (0 until SUPERSET_COLOR_COUNT).firstOrNull { it !in used } ?: (groups.size % SUPERSET_COLOR_COUNT)
    }

    /** Deletes any group on [date] that has fewer than 2 members. */
    private suspend fun cleanupGroups(date: LocalDate) {
        for (g in dao.groups(date)) {
            if (dao.membersOf(g.id).size < 2) dao.deleteGroup(g.id)
        }
    }

    private fun sortGroups(groups: List<MuscleGroup>) = groups.distinct().sortedBy { it.ordinal }
}
