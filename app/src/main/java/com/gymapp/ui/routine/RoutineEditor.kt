package com.gymapp.ui.routine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gymapp.container
import com.gymapp.data.normalizeSupersets
import com.gymapp.ui.common.ExerciseThumb
import com.gymapp.ui.common.write
import com.gymapp.ui.theme.supersetColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long an exercise must be held over another before releasing supersets them. */
private const val SUPERSET_HOLD_MS = 550L

/**
 * New Routine ([routineId] null) or Edit Routine: a name plus an ordered list of exercises.
 *
 * Hold an exercise and drag it to reorder. Holding it over another exercise for a moment arms a
 * superset; releasing then puts the two together. Dropping between two exercises of a superset
 * joins it; dropping anywhere else takes the exercise out of its superset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(
    routineId: Long?,
    onDone: () -> Unit,
    onNewExercise: () -> Unit,
    /** An exercise just created from [onNewExercise], to append to the list; cleared via [onNewExerciseAdded]. */
    newExerciseId: Long? = null,
    onNewExerciseAdded: () -> Unit = {},
) {
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val library by remember { app.repository.observeLibrary() }.collectAsState(initial = emptyList())

    var loaded by rememberSaveable { mutableStateOf(routineId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    // Parallel lists: exercise ids in order, and each one's superset marker (see normalizeSupersets).
    var exerciseIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var groups by rememberSaveable { mutableStateOf(listOf<Int?>()) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun setList(ids: List<Long>, gs: List<Int?>) {
        exerciseIds = ids
        groups = normalizeSupersets(gs)
    }

    LaunchedEffect(routineId) {
        if (routineId != null && !loaded) {
            app.repository.routineWithExercises(routineId)?.let { r ->
                name = r.routine.name
                setList(r.items.map { it.exercise.id }, r.items.map { it.supersetGroup })
            }
            loaded = true
        }
    }
    LaunchedEffect(newExerciseId, loaded) {
        if (newExerciseId != null && loaded) {
            setList(exerciseIds + newExerciseId, groups + null)
            onNewExerciseAdded()
        }
    }

    val byId = library.associateBy { it.exercise.id }
    val canSave = loaded && name.isNotBlank() && exerciseIds.isNotEmpty() && !saving

    fun save() {
        saving = true
        scope.launch {
            app.write { app.repository.saveRoutine(routineId, name, exerciseIds, groups) }
            onDone()
        }
    }

    BackHandler { onDone() }

    // ------------------------------------------------------------ drag state
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val rowBounds = remember { mutableStateMapOf<Int, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragStartCenter by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragStartScroll by remember { mutableIntStateOf(0) }
    var armedTarget by remember { mutableStateOf<Int?>(null) }

    // These read state directly, so they're current both while composing and when a drop lands.

    /** The row the dragged exercise is centred over (middle half of that row), if any. */
    fun currentHover(): Int? {
        val d = dragIndex ?: return null
        val y = dragStartCenter + dragDelta
        return rowBounds.entries.firstOrNull { (i, r) ->
            i != d && i < exerciseIds.size && y in (r.top + r.height * 0.25f)..(r.bottom - r.height * 0.25f)
        }?.key
    }

    /** Where the exercise would go if dropped now, as an index into the list without it. */
    fun currentInsert(): Int? {
        val d = dragIndex ?: return null
        if (currentHover() != null) return null
        val y = dragStartCenter + dragDelta
        return exerciseIds.indices.count { i -> i != d && (rowBounds[i]?.center?.y ?: Float.MAX_VALUE) < y }
    }

    val drag = dragIndex
    val hoverTarget = currentHover()
    val insertIndex = currentInsert()

    LaunchedEffect(hoverTarget) {
        armedTarget = null
        if (hoverTarget != null) {
            delay(SUPERSET_HOLD_MS)
            armedTarget = hoverTarget
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Scroll while an exercise is held near the top or bottom edge.
    LaunchedEffect(drag) {
        if (drag == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val speed = with(density) { 14.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val y = dragStartCenter + dragDelta
            val by = when {
                y < viewport.top + edge -> -speed * ((viewport.top + edge - y) / edge).coerceIn(0f, 1f)
                y > viewport.bottom - edge -> speed * ((y - (viewport.bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (by != 0f) scroll.scrollBy(by)
        }
    }

    fun endDrag(commit: Boolean) {
        val d = dragIndex ?: return
        if (commit) {
            val ids = exerciseIds.toMutableList()
            val gs = groups.toMutableList()
            val id = ids.removeAt(d)
            val oldGroup = gs.removeAt(d)
            // Only superset if still over the row that was armed.
            val target = armedTarget?.takeIf { it == currentHover() }
            val insert = currentInsert()
            if (target != null) {
                // Superset with the target: sit right after it, in its group.
                val t = if (target > d) target - 1 else target
                val g = gs[t] ?: ((gs.filterNotNull().maxOrNull() ?: -1) + 1).also { gs[t] = it }
                ids.add(t + 1, id)
                gs.add(t + 1, g)
            } else {
                val k = (insert ?: d).coerceIn(0, ids.size)
                val above = gs.getOrNull(k - 1)
                val below = gs.getOrNull(k)
                // Between two members of one superset → join it; otherwise stand alone.
                val g = if (above != null && above == below) above else if (k == d) oldGroup.takeIf { above == it || below == it } else null
                ids.add(k, id)
                gs.add(k, g)
            }
            setList(ids, gs)
        }
        dragIndex = null
        dragDelta = 0f
        armedTarget = null
    }

    // Each superset run gets its own colour, in order of appearance.
    val runColorIndex: List<Int?> = run {
        var run = -1
        groups.mapIndexed { i, g ->
            if (g == null) null else {
                if (i == 0 || groups[i - 1] != g) run++
                run
            }
        }
    }

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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .onGloballyPositioned { viewport = it.boundsInRoot() }
                .verticalScroll(scroll)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine name") },
                placeholder = { Text("e.g. Push Day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Exercises, in the order you'll do them",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                if (exerciseIds.isEmpty()) "No exercises yet. Add some below."
                else "Hold and drag to reorder. Hold one over another to superset them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )

            exerciseIds.forEachIndexed { i, id ->
                val ex = byId[id]?.exercise
                val inGroupWithPrev = i > 0 && groups[i] != null && groups[i] == groups[i - 1]
                // Gap above this row; shows the drop line when the exercise would land here.
                val showLine = drag != null && insertIndex != null && insertIndex != drag &&
                    (if (insertIndex < drag) i == insertIndex else i == insertIndex + 1)
                DropGap(showLine, joined = inGroupWithPrev && drag != i && drag != i - 1, color = runColorIndex[i]?.let { supersetColor(it) })

                val dragging = drag == i
                val hovered = hoverTarget == i
                val armed = armedTarget == i
                val color = runColorIndex[i]?.let { supersetColor(it) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        armed -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .onGloballyPositioned { rowBounds[i] = it.boundsInRoot() }
                        // Keep this modifier chain the same shape whatever the state, or Compose
                        // recreates the gesture handler mid-drag and the drop is lost.
                        .pointerInput(i, exerciseIds.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragIndex = i
                                    dragStartCenter = rowBounds[i]?.center?.y ?: 0f
                                    dragStartScroll = scroll.value
                                    dragDelta = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDelta += amount.y
                                },
                                onDragEnd = { endDrag(true) },
                                onDragCancel = { endDrag(false) },
                            )
                        }
                        .graphicsLayer {
                            translationY = if (dragging) dragDelta + (scroll.value - dragStartScroll) else 0f
                            val scale = if (dragging) 1.03f else 1f
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                            shape = RoundedCornerShape(12.dp)
                            clip = false
                        }
                        .border(
                            width = 2.dp,
                            color = if (hovered) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .drawBehind {
                                if (color != null) {
                                    drawRoundRect(
                                        color,
                                        topLeft = Offset.Zero,
                                        size = Size(5.dp.toPx(), size.height),
                                        cornerRadius = CornerRadius(3.dp.toPx()),
                                    )
                                }
                            }
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
                        if (ex != null) ExerciseThumb(ex.name, ex.photoPath, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ex?.name ?: "(deleted exercise)",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            when {
                                armed -> Text(
                                    "Release to superset",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                color != null -> Text("Superset", style = MaterialTheme.typography.labelMedium, color = color)
                            }
                        }
                        IconButton(onClick = {
                            setList(
                                exerciseIds.toMutableList().apply { removeAt(i) },
                                groups.toMutableList().apply { removeAt(i) },
                            )
                        }) {
                            Icon(Icons.Default.Close, "Remove")
                        }
                    }
                }
            }
            if (exerciseIds.isNotEmpty()) {
                val showLine = drag != null && insertIndex != null && insertIndex != drag && insertIndex == exerciseIds.size - 1 && drag != exerciseIds.lastIndex
                DropGap(showLine, joined = false, color = null)
            }

            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
                OutlinedButton(onClick = onNewExercise, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add new exercise")
                }
            }
        }
    }

    if (picking) {
        ExercisePickDialog(
            library = library,
            onDismiss = { picking = false },
            onPick = { setList(exerciseIds + it, groups + null) },
        )
    }
}

/**
 * The space between two rows: a drop indicator while dragging, and a coloured link when the rows
 * on either side are in the same superset.
 */
@Composable
private fun DropGap(showLine: Boolean, joined: Boolean, color: Color?) {
    val line = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.CenterStart) {
        when {
            showLine -> Box(Modifier.fillMaxWidth().height(3.dp).background(line, RoundedCornerShape(2.dp)))
            joined && color != null -> Box(Modifier.width(5.dp).height(10.dp).background(color))
        }
    }
}
