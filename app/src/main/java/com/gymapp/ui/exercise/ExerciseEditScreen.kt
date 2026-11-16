package com.gymapp.ui.exercise

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gymapp.container
import com.gymapp.data.MuscleGroup
import com.gymapp.ui.common.ExerciseThumb
import com.gymapp.ui.common.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * New Exercise (when [exerciseId] is null) or Edit Exercise.
 * [onDone] receives true when the new exercise was also added to [date].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    exerciseId: Long?,
    date: LocalDate?,
    initialName: String,
    onDone: (addedToDay: Boolean) -> Unit,
    /** Called with the new exercise's id after it's created (not on edit). */
    onCreated: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val isNew = exerciseId == null

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var groups by rememberSaveable { mutableStateOf(setOf<MuscleGroup>()) }
    /** Photo the exercise had when the screen opened. */
    var originalPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    /** Photo currently shown in the form. */
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var addToDay by rememberSaveable { mutableStateOf(true) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(exerciseId) {
        if (exerciseId != null && !loaded) {
            val ex = app.repository.exercise(exerciseId)
            if (ex != null) {
                name = ex.name
                groups = app.repository.muscleGroupsFor(exerciseId).toSet()
                originalPhoto = ex.photoPath
                photo = ex.photoPath
            }
            loaded = true
        }
    }

    /** Replaces the form photo, deleting any unsaved photo it replaces. */
    fun setPhoto(newPath: String?) {
        if (photo != null && photo != originalPhoto) app.photos.delete(photo)
        photo = newPath
    }

    fun importUri(uri: Uri) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { app.photos.importPhoto(uri) }
            if (path != null) setPhoto(path)
            app.photos.clearCameraCache()
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (ok && uri != null) importUri(uri)
    }
    fun launchCamera() {
        val uri = app.photos.newCameraUri()
        pendingCameraUri = uri.toString()
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importUri(uri)
    }

    fun cancel() {
        if (photo != null && photo != originalPhoto) app.photos.delete(photo)
        onDone(false)
    }
    BackHandler { cancel() }

    val canSave = loaded && name.isNotBlank() && groups.isNotEmpty() && !saving

    fun save() {
        saving = true
        scope.launch {
            val trimmed = name.trim()
            if (app.repository.isNameTaken(trimmed, exerciseId)) {
                nameError = "You already have an exercise called \"$trimmed\""
                saving = false
                return@launch
            }
            var added = false
            app.write {
                if (exerciseId == null) {
                    val id = app.repository.createExercise(trimmed, groups, photo)
                    onCreated(id)
                    if (addToDay && date != null) {
                        val entryId = app.repository.addEntry(date, id)
                        app.expand(entryId)
                        added = true
                    }
                } else {
                    app.repository.updateExercise(exerciseId, trimmed, groups, photo)
                    if (originalPhoto != null && originalPhoto != photo) app.photos.delete(originalPhoto)
                }
            }
            onDone(added)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Exercise" else "Edit Exercise") },
                navigationIcon = { IconButton(onClick = ::cancel) { Icon(Icons.Default.Close, "Cancel") } },
                actions = { TextButton(enabled = canSave, onClick = ::save) { Text("Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Exercise name") },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Muscle groups", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Select every group this exercise targets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.entries.forEach { g ->
                        FilterChip(
                            selected = g in groups,
                            onClick = { groups = if (g in groups) groups - g else groups + g },
                            label = { Text(g.label) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Photo (optional)", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumb(name.ifBlank { "?" }, photo, size = 96.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                        }) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (photo == null) "Take Photo" else "Retake")
                        }
                        OutlinedButton(onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose from Gallery")
                        }
                        if (photo != null) {
                            TextButton(onClick = { setPhoto(null) }) {
                                Icon(Icons.Default.Delete, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Remove photo")
                            }
                        }
                    }
                }
            }

            if (isNew && date != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addToDay, onCheckedChange = { addToDay = it })
                    Text("Save & add to this day")
                }
            }

            Button(onClick = ::save, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
