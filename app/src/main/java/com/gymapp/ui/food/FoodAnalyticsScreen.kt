package com.gymapp.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymapp.container
import com.gymapp.data.Meal
import com.gymapp.data.WaterLog
import com.gymapp.ui.common.BarChart
import com.gymapp.ui.common.LineChart
import com.gymapp.ui.common.compactNumber
import com.gymapp.ui.common.formatWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class FoodTab(val label: String) { WEIGHT("Weight"), FOOD("Food"), WATER("Water") }
private enum class FoodRange(val label: String) { DAY("1 Day"), WEEK("1 Week"), TOTAL("Total") }

private val dayLetter = DateTimeFormatter.ofPattern("EEE")
private val shortDate = DateTimeFormatter.ofPattern("MMM d")
private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
private fun epochDay(d: LocalDate) = d.toEpochDay().toDouble()
private fun dayLabel(x: Double) = LocalDate.ofEpochDay(x.toLong()).format(shortDate)

/** Food analytics: Weight / Food / Water tabs, each with 1 Day / 1 Week / Total. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodAnalyticsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.container
    val weights by app.food.weights.collectAsState(initial = emptyList())
    val meals by app.food.meals.collectAsState(initial = emptyList())
    val water by app.food.water.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableStateOf(FoodTab.WEIGHT) }
    var range by rememberSaveable { mutableStateOf(FoodRange.WEEK) }
    val today = LocalDate.now()
    val weekStart = today.minusDays(6)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Analytics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                FoodTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FoodRange.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = range == r,
                            onClick = { range = r },
                            shape = SegmentedButtonDefaults.itemShape(i, FoodRange.entries.size),
                        ) { Text(r.label) }
                    }
                }
                fun inRange(d: LocalDate) = when (range) {
                    FoodRange.DAY -> d == today
                    FoodRange.WEEK -> !d.isBefore(weekStart) && !d.isAfter(today)
                    FoodRange.TOTAL -> true
                }
                when (tab) {
                    FoodTab.WEIGHT -> WeightTab(weights.filter { inRange(it.date) }.map { it.loggedAt to it.weightLb }, range)
                    FoodTab.FOOD -> FoodTabContent(meals.filter { inRange(it.date) }, water.filter { inRange(it.date) }, range, today)
                    FoodTab.WATER -> WaterTab(water.filter { inRange(it.date) }, range, today)
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Tiles(vararg tiles: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (label, value) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Empty(what: String) {
    Text(
        "No $what logged in this period.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    )
}

/** 7 day buckets ending today, labelled Mon/Tue/... */
private fun <T> weekBars(today: LocalDate, items: List<T>, date: (T) -> LocalDate, value: (List<T>) -> Double) =
    (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        d.format(dayLetter) to value(items.filter { date(it) == d })
    }

/** One point per day that has data. */
private fun <T> dailyPoints(items: List<T>, date: (T) -> LocalDate, value: (List<T>) -> Double) =
    items.groupBy(date).toSortedMap().map { (d, list) -> epochDay(d) to value(list) }

@Composable
private fun WeightTab(points: List<Pair<Long, Double>>, range: FoodRange) {
    if (points.isEmpty()) return Empty("weight")
    val sorted = points.sortedBy { it.first }
    val first = sorted.first().second
    val last = sorted.last().second
    val change = last - first
    Tiles(
        "Latest" to "${formatWeight(last)} lb",
        "Change" to (if (change > 0) "+" else "") + "${formatWeight(change)} lb",
        "Lowest" to "${formatWeight(sorted.minOf { it.second })} lb",
    )
    ChartCard("Body weight (lb)") {
        if (range == FoodRange.DAY) {
            LineChart(sorted.map { it.first.toDouble() to it.second }, xLabel = { formatTime(it.toLong()) }, yLabel = { formatWeight(it) })
        } else {
            // Average weigh-ins per day.
            val daily = sorted.groupBy { dateOf(it.first) }.toSortedMap().map { (d, l) -> epochDay(d) to l.map { it.second }.average() }
            LineChart(daily, xLabel = ::dayLabel, yLabel = { formatWeight(it) })
        }
    }
}

@Composable
private fun FoodTabContent(meals: List<Meal>, water: List<WaterLog>, range: FoodRange, today: LocalDate) {
    if (meals.isEmpty() && water.none { it.calories > 0 }) return Empty("food")
    // Average over days that have something logged, so a missed day doesn't drag the average down.
    val days = (meals.map { it.date } + water.filter { it.calories > 0 }.map { it.date }).distinct().size.coerceAtLeast(1)
    val calories = meals.sumOf { it.calories } + water.sumOf { it.calories }
    Tiles(
        (if (range == FoodRange.DAY) "Calories" else "Avg / logged day") to compactNumber(calories.toDouble() / days),
        "Meals" to "${meals.size}",
    )
    ChartCard("Calories (kcal)") {
        when (range) {
            FoodRange.DAY -> if (meals.isEmpty()) Empty("meals") else
                BarChart(meals.sortedBy { it.loggedAt }.map { formatTime(it.loggedAt) to it.calories.toDouble() }, valueLabel = ::compactNumber)
            FoodRange.WEEK -> BarChart(
                (6 downTo 0).map { back ->
                    val d = today.minusDays(back.toLong())
                    d.format(dayLetter) to (meals.filter { it.date == d }.sumOf { it.calories } + water.filter { it.date == d }.sumOf { it.calories }).toDouble()
                },
                valueLabel = ::compactNumber,
            )
            FoodRange.TOTAL -> {
                val byDay = (meals.map { it.date to it.calories } + water.map { it.date to it.calories })
                    .groupBy({ it.first }, { it.second }).toSortedMap().map { (d, c) -> epochDay(d) to c.sum().toDouble() }
                LineChart(byDay, xLabel = ::dayLabel, yLabel = ::compactNumber, yFromZero = true)
            }
        }
    }
    val protein = meals.sumOf { it.proteinG }
    val carbs = meals.sumOf { it.carbsG }
    val fat = meals.sumOf { it.fatG }
    ChartCard(if (range == FoodRange.DAY) "Macros (g)" else "Macros, average per logged day (g)") {
        BarChart(
            listOf("Protein" to protein / days, "Carbs" to carbs / days, "Fat" to fat / days),
            valueLabel = { formatGrams(Math.round(it * 10) / 10.0) },
            height = 120.dp,
        )
    }
}

@Composable
private fun WaterTab(water: List<WaterLog>, range: FoodRange, today: LocalDate) {
    if (water.isEmpty()) return Empty("water")
    val days = water.map { it.date }.distinct().size.coerceAtLeast(1)
    val total = water.sumOf { it.ml }
    Tiles(
        "Total" to "${compactNumber(total.toDouble())} mL",
        (if (range == FoodRange.DAY) "Drinks" else "Avg / logged day") to
            if (range == FoodRange.DAY) "${water.size}" else "${compactNumber(total.toDouble() / days)} mL",
    )
    ChartCard("Water (mL)") {
        when (range) {
            FoodRange.DAY -> {
                // Running total through the day.
                var sum = 0.0
                val pts = water.sortedBy { it.loggedAt }.map { sum += it.ml; it.loggedAt.toDouble() to sum }
                LineChart(pts, xLabel = { formatTime(it.toLong()) }, yLabel = ::compactNumber, yFromZero = true)
            }
            FoodRange.WEEK -> BarChart(weekBars(today, water, { it.date }) { l -> l.sumOf { it.ml }.toDouble() }, valueLabel = ::compactNumber)
            FoodRange.TOTAL -> LineChart(
                dailyPoints(water, { it.date }) { l -> l.sumOf { it.ml }.toDouble() },
                xLabel = ::dayLabel, yLabel = ::compactNumber, yFromZero = true,
            )
        }
    }
}
