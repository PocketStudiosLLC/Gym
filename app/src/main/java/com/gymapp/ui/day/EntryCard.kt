package com.gymapp.ui.day

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymapp.data.DayEntry
import com.gymapp.data.WorkoutSet
import com.gymapp.ui.common.ExerciseThumb
import com.gymapp.ui.common.MuscleChips
import com.gymapp.ui.common.formatWeight
import com.gymapp.ui.theme.supersetColor

data class EntryActions(
    val onToggle: () -> Unit,
    /** Logs one set with the given weight (lb) and reps. */
    val onAddSet: (weightLb: Double, reps: Int) -> Unit,
    val onEditSet: (WorkoutSet) -> Unit,
    val onEditExercise: () -> Unit,
    val onMoveUp: (() -> Unit)?,
    val onMoveDown: (() -> Unit)?,
    val onRemoveFromSuperset: () -> Unit,
    val onDelete: () -> Unit,
    val onDrop: (SetKey, SetKey) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCard(
    item: DayEntry,
    expanded: Boolean,
    drag: SetDragState,
    actions: EntryActions,
) {
    val ss = item.superset
    val ssColor = ss?.let { supersetColor(it.colorIndex) }
    var menuOpen by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val hoverHeader = drag.active && drag.hoverEntry == item.entry.id && !expanded

    DisposableEffect(item.entry.id) {
        onDispose { drag.entryTargets.remove(item.entry.id) }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hoverHeader) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape) else Modifier,
            ),
    ) {
        // Superset color bar down the whole left edge of the card.
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { if (ssColor != null) drawRect(ssColor, size = Size(6.dp.toPx(), size.height)) }
                .padding(start = if (ssColor != null) 6.dp else 0.dp),
        ) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { drag.entryTargets[item.entry.id] = it.boundsInRoot() }
                            .combinedClickable(
                                onClick = actions.onToggle,
                                onLongClick = { menuOpen = true },
                                onLongClickLabel = "Entry options",
                            )
                            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                item.exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MuscleChips(item.muscleGroups)
                            Text(
                                summary(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ss != null && ssColor != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, null, tint = ssColor, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Superset with ${ss.partnerNames.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ssColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevron),
                        )
                        Spacer(Modifier.width(8.dp))
                        ExerciseThumb(item.exercise.name, item.exercise.photoPath)
                    }
                    EntryMenu(
                        open = menuOpen,
                        inSuperset = ss != null,
                        actions = actions,
                        onClose = { menuOpen = false },
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        item.sets.forEach { set ->
                            SetRow(
                                entryName = item.exercise.name,
                                set = set,
                                inSuperset = item.isInSuperset(set),
                                ssColor = ssColor,
                                drag = drag,
                                onTap = { actions.onEditSet(set) },
                                onDrop = actions.onDrop,
                            )
                        }
                        QuickAddSet(
                            entryId = item.entry.id,
                            lastSet = item.sets.lastOrNull(),
                            onAdd = actions.onAddSet,
                        )
                    }
                }
            }
    }
}

/**
 * Weight and reps boxes beside the Add Set button. The values stay after adding,
 * so the same set can be logged several times with repeated taps.
 */
@Composable
private fun QuickAddSet(entryId: Long, lastSet: WorkoutSet?, onAdd: (Double, Int) -> Unit) {
    var weight by rememberSaveable(entryId) { mutableStateOf(lastSet?.let { formatWeight(it.weightLb) }.orEmpty()) }
    var reps by rememberSaveable(entryId) { mutableStateOf(lastSet?.reps?.toString().orEmpty()) }
    val w = weight.replace(',', '.').toDoubleOrNull()
    val r = reps.toIntOrNull()
    val valid = w != null && w >= 0 && r != null && r > 0
    val focus = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = weight,
            onValueChange = { v -> if (v.length <= 7 && v.all { it.isDigit() || it == '.' || it == ',' }) weight = v },
            label = { Text("lb") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = reps,
            onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) reps = v },
            label = { Text("Reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onAdd(w!!, r!!) },
            enabled = valid,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(56.dp).padding(top = 8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add Set")
        }
    }
}

private fun summary(item: DayEntry): String {
    if (item.sets.isEmpty()) return "No sets yet"
    val top = item.sets.maxWith(compareBy<WorkoutSet> { it.weightLb }.thenBy { it.reps })
    val n = item.sets.size
    return "$n set${if (n == 1) "" else "s"} · top ${formatWeight(top.weightLb)} lb × ${top.reps}"
}

@Composable
private fun EntryMenu(open: Boolean, inSuperset: Boolean, actions: EntryActions, onClose: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        DropdownMenuItem(
            text = { Text("Edit exercise") },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = { onClose(); actions.onEditExercise() },
        )
        actions.onMoveUp?.let { up ->
            DropdownMenuItem(
                text = { Text("Move up") },
                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                onClick = { onClose(); up() },
            )
        }
        actions.onMoveDown?.let { down ->
            DropdownMenuItem(
                text = { Text("Move down") },
                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                onClick = { onClose(); down() },
            )
        }
        if (inSuperset) {
            DropdownMenuItem(
                text = { Text("Remove from superset") },
                leadingIcon = { Icon(Icons.Default.LinkOff, null) },
                onClick = { onClose(); actions.onRemoveFromSuperset() },
            )
        }
        DropdownMenuItem(
            text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onClose(); actions.onDelete() },
        )
    }
}

@Composable
private fun SetRow(
    entryName: String,
    set: WorkoutSet,
    inSuperset: Boolean,
    ssColor: Color?,
    drag: SetDragState,
    onTap: () -> Unit,
    onDrop: (SetKey, SetKey) -> Unit,
) {
    val key = SetKey(set.entryId, set.setIndex)
    val haptic = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tap by rememberUpdatedState(onTap)
    val drop by rememberUpdatedState(onDrop)
    val label = "$entryName · Set ${set.setIndex}"
    val isTarget = drag.hoverTarget == key
    val isSource = drag.payload?.key == key

    DisposableEffect(key) { onDispose { drag.setTargets.remove(key) } }

    val tint = when {
        isTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        inSuperset && ssColor != null -> ssColor.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (isSource) 0.4f else 1f)
            .background(tint)
            .onGloballyPositioned {
                coords = it
                drag.setTargets[key] = it.boundsInRoot()
            }
            .pointerInput(key) {
                detectTapOrLongPressDrag(
                    coordinates = { coords },
                    onTap = { tap() },
                    onDragStart = { at ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag.start(DragPayload(key, label), at)
                    },
                    onDrag = { drag.move(it) },
                    onDragEnd = {
                        drag.end()?.let { (src, dst) ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            drop(src, dst)
                        }
                    },
                    onDragCancel = { drag.cancel() },
                )
            }
            .semantics { contentDescription = "Set ${set.setIndex}, ${formatWeight(set.weightLb)} pounds, ${set.reps} reps" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (inSuperset && ssColor != null) ssColor else Color.Transparent),
        )
        Text(
            "Set ${set.setIndex}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).width(64.dp),
        )
        Text(
            "${formatWeight(set.weightLb)} lb",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(96.dp),
        )
        Text("× ${set.reps}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (inSuperset && ssColor != null) {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .background(ssColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("SS", style = MaterialTheme.typography.labelSmall, color = ssColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
