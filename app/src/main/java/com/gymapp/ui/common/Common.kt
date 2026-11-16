package com.gymapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gymapp.AppContainer
import com.gymapp.data.MuscleGroup
import kotlinx.coroutines.async
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Runs a database write in the app-wide scope so it finishes even if the calling
 * screen goes away, while still returning its result to the caller.
 */
suspend fun <T> AppContainer.write(block: suspend () -> T): T = appScope.async { block() }.await()

/** "225", "22.5" — never "225.0". */
fun formatWeight(lb: Double): String =
    if (lb % 1.0 == 0.0) lb.toLong().toString() else "%.1f".format(lb).trimEnd('0').trimEnd('.')

fun formatVolume(lb: Double): String = "%,d".format(Math.round(lb))

/** Up to two letters from the first two words: "Barbell Bench Press" → "BB". */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dayYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

fun formatDay(date: LocalDate): String =
    if (date.year == LocalDate.now().year) date.format(dayFormat) else date.format(dayYearFormat)

/** Exercise photo, or the exercise's initials on a theme-colored tile when there's no photo. */
@Composable
fun ExerciseThumb(name: String, photoPath: String?, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    if (photoPath != null && File(photoPath).exists()) {
        AsyncImage(
            model = File(photoPath),
            contentDescription = "$name photo",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(name),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChips(groups: List<MuscleGroup>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { g ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    g.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
