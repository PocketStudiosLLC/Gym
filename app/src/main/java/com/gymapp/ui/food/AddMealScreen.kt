package com.gymapp.ui.food

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.gymapp.container
import com.gymapp.ui.common.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Add Meal: optional photo and name, calories, and fat/protein/carbs in grams. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun grams(s: String): Double? = if (s.isBlank()) 0.0 else s.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val cal = calories.toIntOrNull()?.takeIf { it >= 0 }
    val canSave = cal != null && grams(fat) != null && grams(protein) != null && grams(carbs) != null && !saving

    fun setPhoto(path: String?) {
        app.photos.delete(photo)
        photo = path
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
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) importUri(uri) }

    fun cancel() {
        app.photos.delete(photo)
        onDone()
    }
    BackHandler { cancel() }

    fun save() {
        saving = true
        scope.launch {
            app.write { app.food.addMeal(name, photo, cal!!, grams(fat)!!, grams(protein)!!, grams(carbs)!!) }
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Meal") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Photo
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(16.dp)
                if (photo != null && File(photo!!).exists()) {
                    AsyncImage(
                        model = File(photo!!),
                        contentDescription = "Meal photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(shape),
                    )
                } else {
                    Box(
                        Modifier.size(112.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
                        Text("Gallery")
                    }
                    if (photo != null) {
                        TextButton(onClick = { setPhoto(null) }) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Remove")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Meal name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Calories (kcal)", calories, { calories = it }, KeyboardType.Number, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Protein (g)", protein, { protein = it }, KeyboardType.Decimal, Modifier.weight(1f))
                NumberField("Carbs (g)", carbs, { carbs = it }, KeyboardType.Decimal, Modifier.weight(1f))
                NumberField("Fat (g)", fat, { fat = it }, KeyboardType.Decimal, Modifier.weight(1f))
            }
            Text(
                "Leave a macro blank to count it as 0 g.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = ::save, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, kb: KeyboardType, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 7 && v.all { it.isDigit() || it == '.' || it == ',' }) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        modifier = modifier,
    )
}
