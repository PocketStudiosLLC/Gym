package com.gymapp.ui.exercise

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymapp.container
import com.gymapp.data.LibraryExercise
import com.gymapp.data.MuscleGroup
import com.gymapp.ui.common.ExerciseThumb
import com.gymapp.ui.common.MuscleChips
import com.gymapp.ui.common.write
import com.gymapp.ui.day.ConfirmDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    onCreateNew: (String) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val app = LocalContext.current.container
    val library by remember { app.repository.observeLibrary() }.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var toDelete by remember { mutableStateOf<LibraryExercise?>(null) }
    val scope = rememberCoroutineScope()

    val shown = library.orEmpty().filter { item ->
        (filter == null || filter in item.muscleGroups) &&
            (query.isBlank() || item.exercise.name.contains(query.trim(), ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                }
                items(MuscleGroup.entries) { g ->
                    FilterChip(
                        selected = filter == g,
                        onClick = { filter = if (filter == g) null else g },
                        label = { Text(g.label) },
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (library != null && shown.isEmpty()) {
                    item {
                        Text(
                            if (library!!.isEmpty()) "Your library is empty. Create your first exercise below."
                            else "No exercises match.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(shown, key = { it.exercise.id }) { item ->
                    PickerRow(
                        item = item,
                        onClick = {
                            scope.launch {
                                val id = app.write { app.repository.addEntry(date, item.exercise.id) }
                                app.expand(id)
                                onAdded()
                            }
                        },
                        onEdit = { onEdit(item.exercise.id) },
                        onDelete = { toDelete = item },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { onCreateNew(query.trim()) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (query.isBlank()) "Create new…" else "Create \"${query.trim()}\"…")
                    }
                }
            }
        }
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.exercise.name}?",
            message = "It will be removed from your library. Workouts you already logged with it are kept.",
            confirmLabel = "Delete",
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                scope.launch { app.write { app.repository.archiveExercise(item.exercise.id) } }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerRow(item: LibraryExercise, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "Exercise options")
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseThumb(item.exercise.name, item.exercise.photoPath, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                MuscleChips(item.muscleGroups)
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
