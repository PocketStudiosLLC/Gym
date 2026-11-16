package com.gymapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.container
import com.gymapp.data.Profile
import com.gymapp.data.Sex
import com.gymapp.ui.common.formatWeight
import kotlinx.coroutines.launch

private fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()
private fun Double?.text() = this?.let(::formatWeight).orEmpty()

/** Settings → Data, top: daily calorie goal, then height/weight/age/sex and the resulting BMR. */
@Composable
fun ProfileSection() {
    val app = LocalContext.current.container
    val loaded by app.settings.profile.collectAsState(initial = null)
    val profile = loaded ?: return
    // Key the editor on "loaded once" so typing isn't overwritten by each save round-trip.
    ProfileEditor(profile)
}

@Composable
private fun ProfileEditor(initial: Profile) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState(initial = initial)
    val weights by app.food.weights.collectAsState(initial = emptyList())
    val latestWeighIn = weights.lastOrNull()?.weightLb

    var goal by remember { mutableStateOf(initial.calorieGoal?.toString().orEmpty()) }
    var ft by remember { mutableStateOf(initial.heightFt?.toString().orEmpty()) }
    var inches by remember { mutableStateOf(initial.heightIn.text()) }
    var weight by remember { mutableStateOf(initial.weightLb.text()) }
    var age by remember { mutableStateOf(initial.age?.toString().orEmpty()) }

    fun digits(v: String, max: Int, decimal: Boolean = false) =
        v.length <= max && v.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Daily calories", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = goal,
            onValueChange = { v ->
                if (digits(v, 5)) {
                    goal = v
                    scope.launch { app.settings.setCalorieGoal(v.toIntOrNull()?.takeIf { it > 0 }) }
                }
            },
            label = { Text("Daily calorie goal (kcal)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("Shown on the Food tab against what you've eaten") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Body", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ft,
                onValueChange = { v ->
                    if (digits(v, 1)) {
                        ft = v
                        scope.launch { app.settings.setHeight(v.toIntOrNull(), inches.num()) }
                    }
                },
                label = { Text("Height (ft)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = inches,
                onValueChange = { v ->
                    if (digits(v, 4, decimal = true)) {
                        inches = v
                        scope.launch { app.settings.setHeight(ft.toIntOrNull(), v.num()) }
                    }
                },
                label = { Text("Height (in)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = weight,
            onValueChange = { v ->
                if (digits(v, 6, decimal = true)) {
                    weight = v
                    scope.launch { app.settings.setProfileWeight(v.num()?.takeIf { it > 0 }) }
                }
            },
            label = { Text("Weight (lb)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (latestWeighIn != null && weight.num() != latestWeighIn) {
            TextButton(onClick = {
                weight = formatWeight(latestWeighIn)
                scope.launch { app.settings.setProfileWeight(latestWeighIn) }
            }) { Text("Use latest weigh-in (${formatWeight(latestWeighIn)} lb)") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = age,
                onValueChange = { v ->
                    if (digits(v, 3)) {
                        age = v
                        scope.launch { app.settings.setAge(v.toIntOrNull()?.takeIf { it > 0 }) }
                    }
                },
                label = { Text("Age") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            SingleChoiceSegmentedButtonRow(Modifier.weight(1.4f).padding(top = 8.dp)) {
                Sex.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = profile.sex == s,
                        onClick = { scope.launch { app.settings.setSex(s) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                    ) { Text(s.label) }
                }
            }
        }

        BmrCard(profile)
    }
}

@Composable
private fun BmrCard(profile: Profile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Basal metabolic rate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bmr = profile.bmr
            if (bmr == null) {
                Text(
                    "Enter your height, weight, age and sex to see it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("%,d kcal/day".format(bmr), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Calories your body burns at complete rest (Mifflin-St Jeor). Activity adds to this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
