package com.gymapp.ui.food

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gymapp.container
import com.gymapp.data.Meal
import com.gymapp.ui.common.formatWeight
import com.gymapp.ui.common.write
import com.gymapp.ui.day.ConfirmDialog
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

/** "32.5" → "32.5", "30.0" → "30" */
fun formatGrams(g: Double): String = formatWeight(g)

/** One row on the Food page; long-press to delete. */
private data class FoodRow(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val time: Long,
    val photo: String?,
    val describe: String,
    val delete: suspend () -> Unit,
)

/** Today's food, water and weight log. */
@Composable
fun FoodPage(date: LocalDate) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val meals by app.food.meals.collectAsState(initial = emptyList())
    val water by app.food.water.collectAsState(initial = emptyList())
    val weights by app.food.weights.collectAsState(initial = emptyList())
    val profile by app.settings.profile.collectAsState(initial = com.gymapp.data.Profile())
    var toDelete by remember { mutableStateOf<FoodRow?>(null) }

    val dayMeals = meals.filter { it.date == date }
    val dayWater = water.filter { it.date == date }
    val dayWeights = weights.filter { it.date == date }

    val rows = buildList {
        dayWeights.forEach { w ->
            add(FoodRow("w${w.id}", Icons.Default.MonitorWeight, "Weight", "${formatWeight(w.weightLb)} lb", w.loggedAt, null,
                "weigh-in of ${formatWeight(w.weightLb)} lb") { app.food.deleteWeight(w) })
        }
        dayMeals.forEach { m ->
            add(FoodRow("m${m.id}", Icons.Default.Restaurant, m.name.ifBlank { "Meal" }, mealDetail(m), m.loggedAt, m.photoPath,
                m.name.ifBlank { "this meal" }) { app.food.deleteMeal(m) })
        }
        dayWater.forEach { w ->
            add(FoodRow("h${w.id}", Icons.Default.WaterDrop, "Water", listOfNotNull(
                    "${w.ml} mL",
                    "${w.calories} kcal".takeIf { w.calories > 0 },
                    "${formatGrams(w.creatineG)} g creatine".takeIf { w.creatineG > 0 },
                ).joinToString(" · "),
                w.loggedAt, null, "${w.ml} mL of water") { app.food.deleteWater(w) })
        }
    }.sortedBy { it.time }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "summary") {
            Summary(
                calories = dayMeals.sumOf { it.calories } + dayWater.sumOf { it.calories },
                protein = dayMeals.sumOf { it.proteinG },
                carbs = dayMeals.sumOf { it.carbsG },
                fat = dayMeals.sumOf { it.fatG },
                waterMl = dayWater.sumOf { it.ml },
                weight = dayWeights.lastOrNull()?.weightLb ?: weights.lastOrNull()?.weightLb,
                calorieGoal = profile.calorieGoal,
                creatineG = dayWater.sumOf { it.creatineG },
            )
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing logged yet today.\nTap + to add weight, a meal or water.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
        items(rows, key = { it.key }) { row -> LogRow(row, onLongPress = { toDelete = row }) }
    }

    toDelete?.let { row ->
        ConfirmDialog(
            title = "Delete entry?",
            message = "Delete ${row.describe}?",
            confirmLabel = "Delete",
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                scope.launch { app.write { row.delete() } }
            },
        )
    }
}

private fun mealDetail(m: Meal) =
    "${m.calories} kcal · P ${formatGrams(m.proteinG)}g · C ${formatGrams(m.carbsG)}g · F ${formatGrams(m.fatG)}g"

@Composable
private fun Summary(
    calories: Int,
    protein: Double,
    carbs: Double,
    fat: Double,
    waterMl: Int,
    weight: Double?,
    calorieGoal: Int?,
    creatineG: Double,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (calorieGoal != null && calorieGoal > 0) {
                val left = calorieGoal - calories
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$calories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(" / $calorieGoal kcal", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 3.dp))
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (left >= 0) "$left left" else "${-left} over",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (left >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (calories.toFloat() / calorieGoal).coerceIn(0f, 1f) },
                        color = if (left >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        drawStopIndicator = {},
                    )
                }
            }
            Row {
                Stat("Calories", "$calories", "kcal", Modifier.weight(1f))
                Stat("Water", "$waterMl", "mL", Modifier.weight(1f))
                Stat("Weight", weight?.let(::formatWeight) ?: "–", if (weight != null) "lb" else "", Modifier.weight(1f))
            }
            Row {
                Stat("Protein", formatGrams(protein), "g", Modifier.weight(1f))
                Stat("Carbs", formatGrams(carbs), "g", Modifier.weight(1f))
                Stat("Fat", formatGrams(fat), "g", Modifier.weight(1f))
            }
            if (creatineG > 0) {
                Text(
                    "Creatine today: ${formatGrams(creatineG)} g",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(row: FoodRow, onLongPress: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Delete"),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(row.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(row.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.photo != null && File(row.photo).exists()) {
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = File(row.photo),
                    contentDescription = "Meal photo",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

/** A small dialog with numeric/text fields; [onSave] gets the raw strings. */
@Composable
private fun EntryDialog(
    title: String,
    fields: List<Triple<String, KeyboardType, Boolean>>, // label, keyboard, required
    valid: (List<String>) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var values by remember { mutableStateOf(List(fields.size) { "" }) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fields.forEachIndexed { i, (label, kb, required) ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { v -> values = values.toMutableList().also { it[i] = v } },
                        label = { Text(if (required) label else "$label (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = kb,
                            capitalization = if (kb == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                        ),
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = valid(values), onClick = { onSave(values) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()

@Composable
fun AddWeightDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    EntryDialog(
        title = "Add Weight",
        fields = listOf(Triple("Weight (lb)", KeyboardType.Decimal, true)),
        valid = { v -> (v[0].num() ?: 0.0) > 0 },
        onDismiss = onDismiss,
        onSave = { v ->
            onDismiss()
            scope.launch { app.write { app.food.addWeight(v[0].num()!!) } }
        },
    )
}

@Composable
fun AddWaterDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    EntryDialog(
        title = "Add Water",
        fields = listOf(
            Triple("Amount (mL)", KeyboardType.Number, true),
            Triple("Calories", KeyboardType.Number, false),
            Triple("Creatine (g)", KeyboardType.Decimal, false),
        ),
        valid = { v ->
            (v[0].toIntOrNull() ?: 0) > 0 &&
                (v[1].isBlank() || v[1].toIntOrNull() != null) &&
                (v[2].isBlank() || (v[2].num() ?: -1.0) >= 0)
        },
        onDismiss = onDismiss,
        onSave = { v ->
            onDismiss()
            scope.launch { app.write { app.food.addWater(v[0].toInt(), v[1].toIntOrNull() ?: 0, v[2].num() ?: 0.0) } }
        },
    )
}

