package com.gymapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.saveable.rememberSaveable
import com.gymapp.ui.routine.ManageRoutines
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gymapp.backup.BackupException
import com.gymapp.backup.ImportMode
import com.gymapp.backup.ParsedBackup
import com.gymapp.container
import com.gymapp.data.AppSettings
import com.gymapp.data.ThemeColor
import com.gymapp.data.ThemeMode
import com.gymapp.ui.common.write
import com.gymapp.ui.theme.swatchColor
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNewRoutine: () -> Unit, onEditRoutine: (Long) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.COLORS) }
    val app = LocalContext.current.container
    val settings by app.settings.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ParsedBackup?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }

    fun errorText(t: Throwable) = (t as? BackupException)?.message ?: "Something went wrong: ${t.message ?: t.javaClass.simpleName}"

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Exporting…"
        scope.launch {
            val result = runCatching { app.write { app.backup.export(uri) } }
            busy = null
            snackbar.showSnackbar(result.fold({ "Backup saved" }, ::errorText))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Reading backup…"
        scope.launch {
            val result = runCatching { app.backup.read(uri) }
            busy = null
            result.onSuccess { pendingImport = it }.onFailure { snackbar.showSnackbar(errorText(it)) }
        }
    }

    fun runImport(backup: ParsedBackup, mode: ImportMode) {
        pendingImport = null
        confirmReplace = false
        busy = "Importing…"
        scope.launch {
            val result = runCatching { app.write { app.backup.import(backup, mode) } }
            busy = null
            snackbar.showSnackbar(result.fold({ "Import complete" }, ::errorText))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            SettingsTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (tab) {
        SettingsTab.ROUTINES -> ManageRoutines(onNewRoutine = onNewRoutine, onEditRoutine = onEditRoutine)
        SettingsTab.COLORS -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Mode", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light").forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.mode == mode,
                            onClick = { scope.launch { app.settings.setMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                Text("Theme color", style = MaterialTheme.typography.titleSmall)
                ThemeColor.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            Swatch(
                                color = c,
                                selected = settings.color == c,
                                onClick = { scope.launch { app.settings.setColor(c) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

        }
        SettingsTab.DATA -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            ProfileSection()
            Spacer(Modifier.padding(8.dp))
            HorizontalDivider()
            SectionTitle("Backup")
            ListItem(
                headlineContent = { Text("Export workout data") },
                supportingContent = { Text("Save everything (workouts, routines, food logs, photos) to a backup file") },
                leadingContent = { Icon(Icons.Default.Upload, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = busy == null) {
                    exportLauncher.launch("gymapp-backup-${LocalDate.now()}.zip")
                },
            )
            ListItem(
                headlineContent = { Text("Import workout data") },
                supportingContent = { Text("Restore from a backup file, e.g. after a reset or on a new phone") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = busy == null) {
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
            )
        }
        }
      }
    }

    busy?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(message)
                }
            },
        )
    }

    pendingImport?.let { backup ->
        if (!confirmReplace) {
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("Import backup?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            buildString {
                                append("${backup.workoutDays} workout day(s), ${backup.exerciseCount} exercise(s), ${backup.setCount} set(s)")
                                if (backup.foodCount > 0) append("\n${backup.foodCount} food, water and weight log(s)")
                                if (backup.firstDate != null) append("\n${backup.firstDate} → ${backup.lastDate}")
                            },
                        )
                        Text(
                            "Merge adds anything not already on this phone. Replace deletes everything on this phone first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { confirmReplace = true }) {
                            Text("Replace", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { runImport(backup, ImportMode.MERGE) }) { Text("Merge") }
                    }
                },
                dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { confirmReplace = false },
                title = { Text("Replace all data?") },
                text = { Text("Every workout, exercise and photo on this phone will be deleted and replaced by the backup. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { runImport(backup, ImportMode.REPLACE) }) {
                        Text("Replace", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Back") } },
            )
        }
    }
}

private enum class SettingsTab(val label: String) { COLORS("Colors"), ROUTINES("Routines"), DATA("Data") }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun Swatch(color: ThemeColor, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(4.dp)
                .background(swatchColor(color), CircleShape)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                )
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                    contentDescription = "${color.label} theme"
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
        }
        Text(color.label, style = MaterialTheme.typography.labelSmall)
    }
}
