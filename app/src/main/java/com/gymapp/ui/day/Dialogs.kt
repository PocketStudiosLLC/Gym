package com.gymapp.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymapp.data.DayEntry
import com.gymapp.ui.common.formatWeight
import kotlinx.coroutines.delay

/** Adds or edits one set: weight (lb) and reps on numeric keypads. */
@Composable
fun SetDialog(
    title: String,
    initialWeight: Double?,
    initialReps: Int?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (weightLb: Double, reps: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onSupersetWith: (() -> Unit)? = null,
) {
    fun tfv(s: String) = TextFieldValue(s, TextRange(0, s.length))
    var weight by remember { mutableStateOf(tfv(initialWeight?.let(::formatWeight).orEmpty())) }
    var reps by remember { mutableStateOf(tfv(initialReps?.toString().orEmpty())) }
    val weightVal = weight.text.replace(',', '.').toDoubleOrNull()
    val repsVal = reps.text.toIntOrNull()
    val valid = weightVal != null && weightVal >= 0 && repsVal != null && repsVal > 0
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> if (v.text.length <= 7) weight = v },
                        label = { Text("Weight (lb)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f).focusRequester(focus).selectAllOnFocus { weight = it(weight) },
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { v -> if (v.text.length <= 4 && v.text.all(Char::isDigit)) reps = v },
                        label = { Text("Reps") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (valid) onSave(weightVal!!, repsVal!!) }),
                        modifier = Modifier.weight(1f).selectAllOnFocus { reps = it(reps) },
                    )
                }
                if (onSupersetWith != null) {
                    TextButton(onClick = onSupersetWith) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Text("  Superset with…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(weightVal!!, repsVal!!) }) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Accessible alternative to drag & drop: choose the set of another exercise to superset with. */
@Composable
fun SupersetPickerDialog(
    others: List<DayEntry>,
    onDismiss: () -> Unit,
    onPick: (entryId: Long, setIndex: Int) -> Unit,
) {
    val options = others.flatMap { e -> e.sets.map { s -> Triple(e, s.setIndex, s) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Superset with…") },
        text = {
            if (options.isEmpty()) {
                Text("Log a set on another exercise first.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { "${it.first.entry.id}-${it.second}" }) { (entry, index, set) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry.entry.id, index) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.exercise.name, modifier = Modifier.weight(1f))
                            Text(
                                "Set $index · ${formatWeight(set.weightLb)} lb × ${set.reps}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Selects the whole value when the field gains focus, so typing replaces the prefilled number.
 * The selection is applied just after the tap that focused the field places its cursor.
 */
@Composable
private fun Modifier.selectAllOnFocus(update: ((TextFieldValue) -> TextFieldValue) -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            delay(80)
            update { it.copy(selection = TextRange(0, it.text.length)) }
        }
    }
    return onFocusChanged { focused = it.isFocused }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
