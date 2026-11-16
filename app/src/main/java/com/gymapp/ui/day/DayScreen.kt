package com.gymapp.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Tab
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.TabRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gymapp.container
import com.gymapp.data.DayEntry
import com.gymapp.data.WorkoutSet
import com.gymapp.timer.StopwatchController
import com.gymapp.timer.StopwatchState
import com.gymapp.timer.formatElapsed
import com.gymapp.ui.common.formatDay
import com.gymapp.ui.common.write
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Pages run in one line: Food (page 0) ← Gym today (page 1) → yesterday (2) → the day before (3) …
 * A right swipe from Gym today opens Food; left swipes step back one day at a time.
 * There are no future days.
 */
private const val FOOD_PAGE = 0
private const val TODAY_PAGE = 1
private const val PAGE_COUNT = 50_000 // ~137 years of history

private fun dateForPage(today: LocalDate, page: Int): LocalDate =
    if (page <= TODAY_PAGE) today else today.minusDays((page - TODAY_PAGE).toLong())
private fun pageForDate(today: LocalDate, date: LocalDate): Int =
    (TODAY_PAGE + ChronoUnit.DAYS.between(date, today).toInt()).coerceIn(TODAY_PAGE, PAGE_COUNT - 1)

private enum class HomeTab(val label: String) { GYM("Gym"), FOOD("Food") }

private data class FabAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private sealed interface DayDialog {
    data class EditSet(val entry: DayEntry, val set: WorkoutSet) : DayDialog
    data class SupersetPick(val entry: DayEntry, val set: WorkoutSet) : DayDialog
    data class DeleteEntry(val entry: DayEntry) : DayDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    onStartExercise: (LocalDate) -> Unit,
    onNewExercise: (LocalDate) -> Unit,
    onStartRoutine: (LocalDate) -> Unit,
    onEditExercise: (Long) -> Unit,
    onAnalytics: (LocalDate) -> Unit,
    onSettings: () -> Unit,
    onAddMeal: () -> Unit,
    onFoodAnalytics: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.container
    val today = remember { LocalDate.now() }
    val pager = rememberPagerState(initialPage = TODAY_PAGE) { PAGE_COUNT }
    val currentDate = dateForPage(today, pager.currentPage)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drag = remember { SetDragState() }
    var fabOpen by remember { mutableStateOf(false) }
    val tab = if (pager.currentPage == FOOD_PAGE) HomeTab.FOOD else HomeTab.GYM
    var datePickerOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DayDialog?>(null) }
    val stopwatch by app.stopwatch.state.collectAsState()
    val expanded by app.expandedEntries.collectAsState()

    fun showUndo(message: String, undo: suspend () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) app.write { undo() }
        }
    }

    val onDrop: (SetKey, SetKey) -> Unit = { src, dst ->
        scope.launch {
            val snap = app.write {
                app.repository.createSuperset(src.entryId, src.setIndex, dst.entryId, dst.setIndex)
            }
            if (snap != null) showUndo("Superset created") { app.repository.restore(snap) }
        }
    }

    BackHandler(enabled = fabOpen) { fabOpen = false }
    // Back from Food returns to Gym.
    BackHandler(enabled = !fabOpen && tab == HomeTab.FOOD) { scope.launch { pager.animateScrollToPage(TODAY_PAGE) } }

    var foodDialog by remember { mutableStateOf<String?>(null) }
    when (foodDialog) {
        "weight" -> com.gymapp.ui.food.AddWeightDialog { foodDialog = null }
        "water" -> com.gymapp.ui.food.AddWaterDialog { foodDialog = null }
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                    HomeTabs(
                        selected = tab,
                        onSelect = { t ->
                            fabOpen = false
                            scope.launch { pager.scrollToPage(if (t == HomeTab.FOOD) FOOD_PAGE else TODAY_PAGE) }
                        },
                        onSettings = { fabOpen = false; onSettings() },
                    )
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (currentDate == today) "Today · ${formatDay(currentDate)}" else formatDay(currentDate),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).clickable(enabled = tab == HomeTab.GYM) { datePickerOpen = true },
                        )
                        if (tab == HomeTab.GYM && currentDate != today) {
                            TextButton(onClick = { scope.launch { pager.animateScrollToPage(TODAY_PAGE) } }) {
                                Icon(Icons.Default.Today, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Today")
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                fun close(action: () -> Unit): () -> Unit = { fabOpen = false; action() }
                val actions = when (tab) {
                    HomeTab.GYM -> listOf(
                        FabAction("New Exercise", Icons.Default.AddCircle, close { onNewExercise(currentDate) }),
                        FabAction("Analytics", Icons.Default.Insights, close { onAnalytics(currentDate) }),
                        FabAction("Start Routine", Icons.Default.FormatListNumbered, close { onStartRoutine(currentDate) }),
                        FabAction("Start Exercise", Icons.Default.FitnessCenter, close { onStartExercise(currentDate) }),
                    )
                    HomeTab.FOOD -> listOf(
                        FabAction("Analytics", Icons.Default.Insights, close(onFoodAnalytics)),
                        FabAction("Add Water", Icons.Default.WaterDrop, close { foodDialog = "water" }),
                        FabAction("Add Meal", Icons.Default.Restaurant, close(onAddMeal)),
                        FabAction("Add Weight", Icons.Default.MonitorWeight, close { foodDialog = "weight" }),
                    )
                }
                FabMenu(open = fabOpen, onToggle = { fabOpen = !fabOpen }, actions = actions)
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
          Box(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !drag.active,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val date = dateForPage(today, page)
                if (page == FOOD_PAGE) {
                    com.gymapp.ui.food.FoodPage(today)
                    return@HorizontalPager
                }
                DayPage(
                    date = date,
                    isCurrent = page == pager.currentPage,
                    expanded = expanded,
                    drag = drag,
                    stopwatch = stopwatch,
                    controller = app.stopwatch,
                    onToggle = { id ->
                        app.expandedEntries.value = if (id in expanded) expanded - id else expanded + id
                    },
                    onExpand = { app.expand(it) },
                    onAddSet = { entryId, w, r -> scope.launch { app.write { app.repository.addSet(entryId, r, w) } } },
                    onEditSet = { e, s -> dialog = DayDialog.EditSet(e, s) },
                    onEditExercise = onEditExercise,
                    onMove = { id, delta -> scope.launch { app.write { app.repository.moveEntry(id, delta) } } },
                    onRemoveFromSuperset = { id ->
                        scope.launch {
                            val snap = app.write { app.repository.removeFromSuperset(id) }
                            if (snap != null) showUndo("Removed from superset") { app.repository.restore(snap) }
                        }
                    },
                    onDelete = { dialog = DayDialog.DeleteEntry(it) },
                    onDrop = onDrop,
                )
            }
            // Scrim behind the open FAB menu: tapping outside closes it.
            if (fabOpen) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fabOpen = false },
                )
            }
          }
        }

        DragOverlay(drag)
    }

    // ---- dialogs ----
    val entriesForDialogs by produceState<List<DayEntry>>(emptyList(), currentDate) {
        app.repository.observeDay(currentDate).collect { value = it }
    }
    when (val d = dialog) {
        is DayDialog.EditSet -> SetDialog(
            title = "${d.entry.exercise.name} · Set ${d.set.setIndex}",
            initialWeight = d.set.weightLb,
            initialReps = d.set.reps,
            confirmLabel = "Save",
            onDismiss = { dialog = null },
            onSave = { w, r ->
                dialog = null
                scope.launch { app.write { app.repository.updateSet(d.set, r, w) } }
            },
            onDelete = {
                dialog = null
                scope.launch {
                    val deleted = app.write { app.repository.deleteSet(d.set) }
                    if (deleted != null) showUndo("Set deleted") { app.repository.undoDeleteSet(deleted) }
                }
            },
            onSupersetWith = { dialog = DayDialog.SupersetPick(d.entry, d.set) },
        )
        is DayDialog.SupersetPick -> SupersetPickerDialog(
            others = entriesForDialogs.filter { it.entry.id != d.entry.entry.id },
            onDismiss = { dialog = null },
            onPick = { entryId, setIndex ->
                dialog = null
                onDrop(SetKey(d.entry.entry.id, d.set.setIndex), SetKey(entryId, setIndex))
            },
        )
        is DayDialog.DeleteEntry -> ConfirmDialog(
            title = "Delete ${d.entry.exercise.name}?",
            message = "This removes the exercise and its ${d.entry.sets.size} set(s) from this day. The exercise stays in your library.",
            confirmLabel = "Delete",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                scope.launch { app.write { app.repository.deleteEntry(d.entry.entry.id) } }
            },
        )
        null -> Unit
    }

    if (datePickerOpen) {
        val state = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int) = year <= today.year
            },
            initialSelectedDateMillis = currentDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerOpen = false
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        scope.launch { pager.scrollToPage(pageForDate(today, picked)) }
                    }
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    isCurrent: Boolean,
    expanded: Set<Long>,
    drag: SetDragState,
    stopwatch: StopwatchState?,
    controller: StopwatchController,
    onToggle: (Long) -> Unit,
    onExpand: (Long) -> Unit,
    onAddSet: (entryId: Long, weightLb: Double, reps: Int) -> Unit,
    onEditSet: (DayEntry, WorkoutSet) -> Unit,
    onEditExercise: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onRemoveFromSuperset: (Long) -> Unit,
    onDelete: (DayEntry) -> Unit,
    onDrop: (SetKey, SetKey) -> Unit,
) {
    val app = LocalContext.current.container
    val entries by produceState<List<DayEntry>?>(null, date) {
        app.repository.observeDay(date).collect { value = it }
    }
    val listState = rememberLazyListState()
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }

    // Auto-scroll while dragging a set near the top or bottom of the list.
    LaunchedEffect(isCurrent, drag.active) {
        if (!isCurrent || !drag.active) return@LaunchedEffect
        while (drag.active) {
            val y = drag.pointer.y
            val speed = when {
                y < listBounds.top + edge -> -((listBounds.top + edge - y) / edge).coerceIn(0f, 1f) * 24f
                y > listBounds.bottom - edge -> ((y - (listBounds.bottom - edge)) / edge).coerceIn(0f, 1f) * 24f
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed)
            delay(16)
        }
    }

    // Hovering a dragged set over a collapsed exercise for 600 ms expands it.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        snapshotFlow { drag.hoverEntry }.collect { id ->
            if (id != null && id !in app.expandedEntries.value) {
                delay(600)
                if (drag.hoverEntry == id) onExpand(id)
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { listBounds = it.boundsInRoot() },
    ) {
        if (stopwatch != null) {
            item(key = "timer") { StopwatchCard(stopwatch, controller) }
        }
        val list = entries
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No exercises logged for this day.\nTap + to add one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                )
            }
        }
        if (list != null) {
            items(list, key = { it.entry.id }) { item ->
                val index = list.indexOf(item)
                EntryCard(
                    item = item,
                    expanded = item.entry.id in expanded,
                    drag = drag,
                    actions = EntryActions(
                        onToggle = { onToggle(item.entry.id) },
                        onAddSet = { w, r -> onAddSet(item.entry.id, w, r) },
                        onEditSet = { onEditSet(item, it) },
                        onEditExercise = { onEditExercise(item.exercise.id) },
                        onMoveUp = if (index > 0) ({ onMove(item.entry.id, -1) }) else null,
                        onMoveDown = if (index < list.lastIndex) ({ onMove(item.entry.id, 1) }) else null,
                        onRemoveFromSuperset = { onRemoveFromSuperset(item.entry.id) },
                        onDelete = { onDelete(item) },
                        onDrop = onDrop,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StopwatchCard(state: StopwatchState, controller: StopwatchController) {
    val elapsed by produceState(state.elapsedMs(), state) {
        while (true) {
            value = state.elapsedMs()
            delay(250)
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Timer, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.paused) "Workout · paused" else "Workout",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    formatElapsed(elapsed),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FilledTonalIconButton(onClick = { if (state.paused) controller.resume() else controller.pause() }) {
                Icon(
                    if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.paused) "Resume stopwatch" else "Pause stopwatch",
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { controller.stop() }) {
                Icon(Icons.Default.Stop, contentDescription = "Stop stopwatch")
            }
        }
    }
}

@Composable
private fun FabMenu(open: Boolean, onToggle: () -> Unit, actions: List<FabAction>) {
    val rotation by animateFloatAsState(if (open) 45f else 0f, label = "fab")
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.forEach { FabItem(it.label, it.icon, it.onClick) }
            }
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                Icons.Default.Add,
                contentDescription = if (open) "Close menu" else "Open menu",
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

/** "Gym" / "Food" tabs centered at the top of the home screen. */
@Composable
private fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
        TabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = Color.Transparent,
            modifier = Modifier.width(220.dp),
        ) {
            HomeTab.entries.forEach { t ->
                Tab(
                    selected = selected == t,
                    onClick = { onSelect(t) },
                    text = { Text(t.label, style = MaterialTheme.typography.titleMedium) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FabItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 2.dp,
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        SmallFloatingActionButton(onClick = onClick, modifier = Modifier.padding(end = 4.dp)) {
            Icon(icon, contentDescription = label)
        }
    }
}

/** The set "lifted" under the finger while dragging. */
@Composable
private fun DragOverlay(drag: SetDragState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val p = drag.payload ?: return@Box
        val density = LocalDensity.current
        val half = with(density) { Offset(80.dp.toPx(), 28.dp.toPx()) }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .offset { (drag.pointer - origin - half).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .width(160.dp)
                .height(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(p.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
