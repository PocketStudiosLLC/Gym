package com.gymapp.ui.routine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymapp.container
import com.gymapp.data.LibraryExercise
import com.gymapp.data.RoutineWithExercises
import com.gymapp.ui.common.ExerciseThumb
import com.gymapp.ui.common.write
import com.gymapp.ui.day.ConfirmDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Start Routine: pick a routine to add all of its exercises to [date], in order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartRoutineScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (Long) -> Unit,
) {
    val app = LocalContext.current.container
    val routines by remember { app.repository.observeRoutines() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Routine") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val list = routines
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        "You don't have any routines yet.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.routine.id }) { r ->
                RoutineCard(
                    routine = r,
                    onClick = {
                        scope.launch {
                            val ids = app.write { app.repository.startRoutine(date, r.routine.id) }
                            ids.firstOrNull()?.let(app::expand)
                            onStarted()
                        }
                    },
                    onEdit = { onEditRoutine(r.routine.id) },
                    onDelete = { toDelete = r },
                )
            }
            item {
                OutlinedButton(onClick = onNewRoutine, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Routine")
                }
            }
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = "Delete ${r.routine.name}?",
            message = "The routine is removed. Workouts you already logged with it are kept.",
            confirmLabel = "Delete",
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                scope.launch { app.write { app.repository.deleteRoutine(r.routine.id) } }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineCard(routine: RoutineWithExercises, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "Routine options"),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(routine.routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${routine.exercises.size} exercise${if (routine.exercises.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                routine.exercises.forEachIndexed { i, ex ->
                    Text(
                        "${i + 1}. ${ex.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { menu = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** Settings → Routines: build and manage routines. Tap a routine to edit it. */
@Composable
fun ManageRoutines(onNewRoutine: () -> Unit, onEditRoutine: (Long) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val routines by remember { app.repository.observeRoutines() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<RoutineWithExercises?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "A routine is a list of exercises in the order you do them. Start one from the + menu with Start Routine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = onNewRoutine, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New Routine")
            }
        }
        if (routines?.isEmpty() == true) {
            item {
                Text(
                    "No routines yet.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        items(routines.orEmpty(), key = { it.routine.id }) { r ->
            RoutineCard(
                routine = r,
                onClick = { onEditRoutine(r.routine.id) },
                onEdit = { onEditRoutine(r.routine.id) },
                onDelete = { toDelete = r },
            )
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            title = "Delete ${r.routine.name}?",
            message = "The routine is removed. Workouts you already logged with it are kept.",
            confirmLabel = "Delete",
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                scope.launch { app.write { app.repository.deleteRoutine(r.routine.id) } }
            },
        )
    }
}

/** New Routine ([routineId] null) or Edit Routine: a name plus an ordered list of exercises. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(routineId: Long?, onDone: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val library by remember { app.repository.observeLibrary() }.collectAsState(initial = emptyList())

    var loaded by rememberSaveable { mutableStateOf(routineId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var exerciseIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        if (routineId != null && !loaded) {
            app.repository.routineWithExercises(routineId)?.let { r ->
                name = r.routine.name
                exerciseIds = r.exercises.map { it.id }
            }
            loaded = true
        }
    }

    val byId = library.associateBy { it.exercise.id }
    val canSave = loaded && name.isNotBlank() && exerciseIds.isNotEmpty() && !saving

    fun save() {
        saving = true
        scope.launch {
            app.write { app.repository.saveRoutine(routineId, name, exerciseIds) }
            onDone()
        }
    }

    fun move(from: Int, to: Int) {
        if (to !in exerciseIds.indices) return
        exerciseIds = exerciseIds.toMutableList().apply { add(to, removeAt(from)) }
    }

    BackHandler { onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == null) "New Routine" else "Edit Routine") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Cancel") } },
                actions = { TextButton(enabled = canSave, onClick = ::save) { Text("Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    placeholder = { Text("e.g. Push Day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Exercises, in the order you'll do them",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (exerciseIds.isEmpty()) {
                item {
                    Text(
                        "No exercises yet. Add some below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            itemsIndexed(exerciseIds) { i, id ->
                val ex = byId[id]?.exercise
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                    if (ex != null) ExerciseThumb(ex.name, ex.photoPath, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        ex?.name ?: "(deleted exercise)",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { move(i, i - 1) }, enabled = i > 0) {
                        Icon(Icons.Default.ArrowUpward, "Move up")
                    }
                    IconButton(onClick = { move(i, i + 1) }, enabled = i < exerciseIds.lastIndex) {
                        Icon(Icons.Default.ArrowDownward, "Move down")
                    }
                    IconButton(onClick = { exerciseIds = exerciseIds.toMutableList().apply { removeAt(i) } }) {
                        Icon(Icons.Default.Close, "Remove")
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
            }
        }
    }

    if (picking) {
        ExercisePickDialog(
            library = library,
            onDismiss = { picking = false },
            onPick = { exerciseIds = exerciseIds + it },
        )
    }
}

/** Searchable library list. Each tap appends one exercise; the dialog stays open to add more. */
@Composable
private fun ExercisePickDialog(library: List<LibraryExercise>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(0) }
    val shown = library.filter { query.isBlank() || it.exercise.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (added == 0) "Add exercises" else "Added $added") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (library.isEmpty()) {
                    Text(
                        "Your library is empty. Create exercises with New Exercise first.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.exercise.id }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item.exercise.id); added++ }
                                .padding(vertical = 8.dp),
                        ) {
                            ExerciseThumb(item.exercise.name, item.exercise.photoPath, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(item.exercise.name, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
