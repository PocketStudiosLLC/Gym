package com.gymapp.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line chart over a numeric x axis (e.g. timestamps). Y axis is padded around the data
 * so small changes (like body weight) are visible.
 */
@Composable
fun LineChart(
    points: List<Pair<Double, Double>>,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    yFromZero: Boolean = false,
) {
    if (points.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val xs = points.map { it.first }
    val ys = points.map { it.second }
    val minX = xs.min()
    val maxX = xs.max()
    var minY = if (yFromZero) 0.0 else ys.min()
    var maxY = ys.max()
    val pad = ((maxY - minY) * 0.15).takeIf { it > 0 } ?: (maxOf(kotlin.math.abs(maxY), 1.0) * 0.05)
    if (!yFromZero) minY -= pad
    maxY += pad

    Column(modifier) {
        Row {
            Column(
                Modifier.height(height).width(52.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(yLabel(maxY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(yLabel(minY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                fun px(x: Double) = if (maxX == minX) size.width / 2 else ((x - minX) / (maxX - minX) * size.width).toFloat()
                fun py(y: Double) = (size.height - (y - minY) / (maxY - minY) * size.height).toFloat()
                // Grid: top, middle, bottom.
                listOf(0f, size.height / 2, size.height).forEach { y ->
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                if (points.size > 1) {
                    val path = Path()
                    points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
                    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                points.forEach { (x, y) -> drawCircle(color, radius = 4.dp.toPx(), center = Offset(px(x), py(y))) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 52.dp, top = 4.dp)) {
            Text(xLabel(minX), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (maxX != minX) {
                Text(xLabel(maxX), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Vertical bars with a value above and a label below each. */
@Composable
fun BarChart(
    bars: List<Pair<String, Double>>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (v > 0) valueLabel(v) else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
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
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** "1.2k", "980" */
fun compactNumber(v: Double): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
    v >= 10_000 -> "%.0fk".format(v / 1_000)
    v >= 1_000 -> "%.1fk".format(v / 1_000)
    else -> Math.round(v).toString()
}
