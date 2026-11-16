package com.gymapp.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymapp.container
import com.gymapp.data.MuscleGroup
import com.gymapp.data.SetFact
import com.gymapp.ui.common.formatDay
import com.gymapp.ui.common.formatVolume
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Range(val label: String) { DAY("1 Day"), WEEK("1 Week") }

private data class Stats(
    val totalSets: Int,
    val totalReps: Int,
    val totalVolume: Double,
    val exerciseCount: Int,
    val setsByGroup: List<Pair<MuscleGroup, Int>>,
    val volumeByGroup: List<Pair<MuscleGroup, Double>>,
    val volumeByDay: List<Pair<LocalDate, Double>>,
    /** For each muscle group: exercise name → set count. */
    val exercisesByGroup: Map<MuscleGroup, List<Pair<String, Int>>>,
)

/**
 * A set counts toward every muscle group its exercise targets, so per-group
 * numbers can add up to more than the totals (which count each set once).
 */
private fun computeStats(
    facts: List<SetFact>,
    groupsOf: Map<Long, List<MuscleGroup>>,
    from: LocalDate,
    to: LocalDate,
): Stats {
    val sets = mutableMapOf<MuscleGroup, Int>()
    val volume = mutableMapOf<MuscleGroup, Double>()
    val perGroupExercise = mutableMapOf<MuscleGroup, MutableMap<String, Int>>()
    for (f in facts) {
        for (g in groupsOf[f.exerciseId].orEmpty()) {
            sets[g] = (sets[g] ?: 0) + 1
            volume[g] = (volume[g] ?: 0.0) + f.reps * f.weightLb
            val m = perGroupExercise.getOrPut(g) { mutableMapOf() }
            m[f.exerciseName] = (m[f.exerciseName] ?: 0) + 1
        }
    }
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    val byDay = facts.groupBy { it.date }
    return Stats(
        totalSets = facts.size,
        totalReps = facts.sumOf { it.reps },
        totalVolume = facts.sumOf { it.reps * it.weightLb },
        exerciseCount = facts.map { it.exerciseId }.distinct().size,
        setsByGroup = sets.entries.sortedByDescending { it.value }.map { it.key to it.value },
        volumeByGroup = volume.entries.sortedByDescending { it.value }.map { it.key to it.value },
        volumeByDay = days.map { d -> d to byDay[d].orEmpty().sumOf { it.reps * it.weightLb } },
        exercisesByGroup = perGroupExercise.mapValues { (_, m) -> m.entries.sortedByDescending { it.value }.map { it.key to it.value } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(initialDate: LocalDate, onBack: () -> Unit) {
    val app = LocalContext.current.container
    var range by rememberSaveable { mutableStateOf(Range.DAY) }
    var endDate by rememberSaveable { mutableStateOf(initialDate) }
    var selectedGroup by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    val from = if (range == Range.DAY) endDate else endDate.minusDays(6)

    val facts by remember(from, endDate) { app.repository.observeFacts(from, endDate) }.collectAsState(initial = null)
    val groupsOf by remember { app.repository.observeMuscleGroupMap() }.collectAsState(initial = emptyMap())
    val stats = facts?.let { computeStats(it, groupsOf, from, endDate) }
    val step = if (range == Range.DAY) 1L else 7L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Range.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = range == r,
                        onClick = { range = r; selectedGroup = null },
                        shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size),
                    ) { Text(r.label) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { endDate = endDate.minusDays(step) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier")
                }
                Text(
                    if (range == Range.DAY) formatDay(endDate) else "${formatDay(from)} – ${formatDay(endDate)}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { endDate = endDate.plusDays(step) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later")
                }
            }

            if (stats == null) return@Column
            if (stats.totalSets == 0) {
                Text(
                    "No workouts logged in this range.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Sets", stats.totalSets.toString(), Modifier.weight(1f))
                StatTile("Reps", stats.totalReps.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Volume (lb)", formatVolume(stats.totalVolume), Modifier.weight(1f))
                StatTile("Exercises", stats.exerciseCount.toString(), Modifier.weight(1f))
            }

            ChartCard(
                title = "Sets per muscle group",
                note = "Tap a bar to see its exercises. A set counts toward every group its exercise targets.",
            ) {
                HorizontalBars(
                    rows = stats.setsByGroup.map { (g, n) -> g to n.toDouble() },
                    format = { it.toInt().toString() },
                    selected = selectedGroup,
                    onSelect = { selectedGroup = if (selectedGroup == it) null else it },
                )
                selectedGroup?.let { g ->
                    AnimatedVisibility(visible = true) {
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(g.label, style = MaterialTheme.typography.titleSmall)
                            stats.exercisesByGroup[g].orEmpty().forEach { (name, n) ->
                                Row {
                                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("$n set${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            if (range == Range.WEEK) {
                ChartCard(title = "Weight lifted per day (lb)") {
                    DayColumns(stats.volumeByDay)
                }
            }
            ChartCard(title = "Weight lifted by muscle group (lb)") {
                HorizontalBars(
                    rows = stats.volumeByGroup,
                    format = ::formatVolume,
                    selected = null,
                    onSelect = null,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartCard(title: String, note: String? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun HorizontalBars(
    rows: List<Pair<MuscleGroup, Double>>,
    format: (Double) -> String,
    selected: MuscleGroup?,
    onSelect: ((MuscleGroup) -> Unit)?,
) {
    val max = rows.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (g, v) ->
            val dim = selected != null && selected != g
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onSelect != null) Modifier.clickable { onSelect(g) } else Modifier)
                    .padding(vertical = 2.dp),
            ) {
                Text(g.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
                Box(Modifier.weight(1f).height(18.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (dim) 0.35f else 1f),
                                RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                            ),
                    )
                }
                Text(
                    format(v),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}

private val weekdayFormat = DateTimeFormatter.ofPattern("EEE")

@Composable
private fun DayColumns(days: List<Pair<LocalDate, Double>>) {
    val max = days.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val chartHeight = 140.dp
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { (d, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (v > 0) compact(v) else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Box(Modifier.height(chartHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (v > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((v / max).toFloat().coerceIn(0.02f, 1f))
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(d.format(weekdayFormat), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun compact(v: Double): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
    v >= 1_000 -> "%.1fk".format(v / 1_000)
    else -> v.toInt().toString()
}
