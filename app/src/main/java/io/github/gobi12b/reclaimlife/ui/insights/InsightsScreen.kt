package io.github.gobi12b.reclaimlife.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.DayUsage
import io.github.gobi12b.reclaimlife.data.INSIGHT_DAYS
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.USAGE_WINDOW_MS
import io.github.gobi12b.reclaimlife.data.UsageInsights
import io.github.gobi12b.reclaimlife.data.buildInsights
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.rememberTrackedApps
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import io.github.gobi12b.reclaimlife.ui.common.rememberUsage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * The last week in the chosen apps: headline numbers first, then time per day, then when in
 * the day it happens. Every chart has a text equivalent for screen readers, and tapping a bar
 * shows its exact numbers.
 */
@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as ReclaimLifeApp
    // Series follow the chosen apps in slot order (never by rank), so a colour always means the same app.
    val tracked = rememberTrackedApps()
    // One extra day so the week can start at local midnight six days ago.
    val usage = rememberUsage(days = INSIGHT_DAYS + 1, packages = remember(tracked) { tracked.map { it.packageName }.toSet() })
    val insights = remember(usage) { usage?.let { buildInsights(it.stretches, it.loadedAtMs, it.fromSystem) } }
    val decisions by app.reelUsageRepository.gateDecisions.collectAsState(initial = emptyList())
    val weekDecisions = remember(decisions, usage) {
        val since = (usage?.loadedAtMs ?: System.currentTimeMillis()) - INSIGHT_DAYS * USAGE_WINDOW_MS
        decisions.filter { it.timeMs >= since }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Insights", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(
                "Your ${tracked.size} apps over the last $INSIGHT_DAYS days",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (insights == null) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        if (!insights.fromSystem) {
            InsightCard {
                Text("Only part of the picture", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "These charts only include time since ReclaimLife started timing. Allow Usage access to read your full week from Android — it stays on this phone.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(usageAccessSettingsIntent()) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow Usage access") }
            }
        }

        StatGrid(insights = insights, skipped = weekDecisions.count { it.skipped }, gateShown = weekDecisions.size)
        DailyChartCard(insights.days, tracked)
        HourChartCard(insights)
        PatternsCard(insights)
    }
}

@Composable
private fun InsightCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) { content() }
    }
}

/** Headline numbers are stat tiles, not charts — one number each needs no axis. */
@Composable
private fun StatGrid(insights: UsageInsights, skipped: Int, gateShown: Int) {
    val perDayOpens = insights.opens.toFloat() / INSIGHT_DAYS
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Daily average", formatUsage(insights.averageDayMs), "across your apps", Modifier.weight(1f))
            StatTile("Opens", insights.opens.toString(), "≈ %.0f a day".format(perDayOpens), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Average visit", formatUsage(insights.averageVisitMs), "per open", Modifier.weight(1f))
            StatTile(
                "Skipped at the pause",
                if (gateShown == 0) "—" else "$skipped of $gateShown",
                if (gateShown == 0) "counting starts now" else "times you chose not to open",
                Modifier.weight(1f),
                highlight = skipped > 0
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
            .semantics(mergeDescendants = true) { }
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(caption, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val dayLetter = SimpleDateFormat("EEEEE", Locale.getDefault())
private val dayName = SimpleDateFormat("EEEE", Locale.getDefault())

/** Minutes per day, stacked by app. Tap a bar for that day's numbers; today is selected at first. */
@Composable
private fun DailyChartCard(days: List<DayUsage>, tracked: List<TrackedApp>) {
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val colors = tracked.map { slotColor(it.slot) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val maxMs = days.maxOfOrNull { it.totalMs }?.coerceAtLeast(60_000L) ?: 60_000L
    val summary = days.joinToString("; ") { "${dayName.format(Date(it.dayStartMs))} ${formatUsage(it.totalMs)}" }

    InsightCard {
        Text("Time per day", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Legend(tracked)
        Row(modifier = Modifier.padding(top = 12.dp)) {
            // Recessive y-axis: just the top value, aligned with the top gridline.
            Box(modifier = Modifier.height(160.dp).padding(end = 6.dp)) {
                Text(formatUsage(maxMs), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp)
                    .semantics { contentDescription = "Time per day chart. $summary" }
                    .pointerInput(days) {
                        detectTapGestures { offset ->
                            selected = (offset.x / (size.width / days.size)).toInt().coerceIn(0, days.lastIndex)
                        }
                    }
            ) {
                val slot = size.width / days.size
                val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = size.height * (1f - f)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                days.forEachIndexed { i, day ->
                    val left = i * slot + (slot - barWidth) / 2f
                    if (i == selected) drawRect(highlight, Offset(i * slot, 0f), Size(slot, size.height))
                    var top = size.height
                    val segments = tracked.mapIndexed { s, app -> colors[s] to (day.msByPackage[app.packageName] ?: 0L) }.filter { it.second > 0 }
                    segments.forEachIndexed { s, (color, ms) ->
                        val h = size.height * ms / maxMs
                        val isTop = s == segments.lastIndex
                        drawBarSegment(color, left, top - h, barWidth, h, roundTop = isTop)
                        top -= h
                        // 2dp surface gap between stacked segments.
                        if (!isTop) drawRect(surface, Offset(left, top - 1.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp)) {
            days.forEachIndexed { i, day ->
                Text(
                    text = dayLetter.format(Date(day.dayStartMs)),
                    fontSize = 12.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val day = days[selected]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .semantics(mergeDescendants = true) { }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected == days.lastIndex) "Today" else dayName.format(Date(day.dayStartMs)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(formatUsage(day.totalMs), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            tracked.sortedByDescending { day.msByPackage[it.packageName] ?: 0L }.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.size(8.dp).background(slotColor(app.slot), CircleShape))
                    Text(rememberAppLabel(app.packageName), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(formatUsage(day.msByPackage[app.packageName] ?: 0L), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Always shown for the stacked chart, and wraps onto more lines as apps are added. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(tracked: List<TrackedApp>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        tracked.forEach { app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(slotColor(app.slot), RoundedCornerShape(3.dp)))
                Text(rememberAppLabel(app.packageName), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** A bar segment with 4dp rounded top corners when it's the top of its stack. */
private fun DrawScope.drawBarSegment(color: Color, left: Float, top: Float, width: Float, height: Float, roundTop: Boolean) {
    if (height <= 0f) return
    val r = minOf(4.dp.toPx(), height, width / 2f)
    if (!roundTop || r <= 0f) {
        drawRect(color, Offset(left, top), Size(width, height))
        return
    }
    val path = Path().apply {
        moveTo(left, top + height)
        lineTo(left, top + r)
        quadraticTo(left, top, left + r, top)
        lineTo(left + width - r, top)
        quadraticTo(left + width, top, left + width, top + r)
        lineTo(left + width, top + height)
        close()
    }
    drawPath(path, color)
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

/** When in the day the time goes: one series, so one colour; the busiest hour is called out. */
@Composable
private fun HourChartCard(insights: UsageInsights) {
    val hours = insights.msByHour
    var selected by remember(hours) { mutableIntStateOf(insights.peakHour ?: -1) }
    val bar = MaterialTheme.colorScheme.primary
    val barMuted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maxMs = hours.max().coerceAtLeast(1L)
    val summary = hours.withIndex().filter { it.value > 0 }.joinToString("; ") { "${hourLabel(it.index)} ${formatUsage(it.value)}" }

    InsightCard {
        Text("When you scroll", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text(
            "Time in each hour of the day, added up over the week",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(120.dp)
                .semantics { contentDescription = "Time by hour of day. ${summary.ifEmpty { "No time recorded" }}" }
                .pointerInput(hours) {
                    detectTapGestures { offset -> selected = (offset.x / (size.width / 24f)).toInt().coerceIn(0, 23) }
                }
        ) {
            val slot = size.width / 24f
            val w = slot - 2.dp.toPx()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            hours.forEachIndexed { h, ms ->
                val height = size.height * ms / maxMs
                // The selected hour is full strength; the rest step back so it reads as the answer.
                drawBarSegment(if (selected < 0 || h == selected) bar else barMuted, h * slot + 1.dp.toPx(), size.height - height, w, height, roundTop = true)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf(0, 6, 12, 18).forEach { h ->
                Text(hourLabel(h), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        if (selected in 0..23) {
            Text(
                text = "${hourLabel(selected)}–${hourLabel((selected + 1) % 24)}: ${formatUsage(hours[selected])} this week" +
                    if (selected == insights.peakHour) " · your busiest hour" else "",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** Plain-language takeaways, framed as what to try — not as a score. */
@Composable
private fun PatternsCard(insights: UsageInsights) {
    val busiest = insights.days.maxByOrNull { it.totalMs }?.takeIf { it.totalMs > 0 }
    val peak = insights.peakHour
    val lines = buildList {
        if (busiest != null) add("📅" to "Your biggest day was ${dayName.format(Date(busiest.dayStartMs))}, with ${formatUsage(busiest.totalMs)}.")
        if (peak != null) {
            add("🕘" to "Most of your scrolling happens around ${hourLabel(peak)}. A plan for that hour — a walk, a call, a book — makes skipping easier.")
            if (peak >= 21 || peak < 5) add("🌙" to "Late-night scrolling cuts into sleep. Try charging your phone outside the bedroom.")
        }
        if (insights.averageVisitMs >= 10 * 60_000L) {
            add("⏳" to "Visits average ${formatUsage(insights.averageVisitMs)}. Deciding what you came for before opening helps you leave sooner.")
        }
        if (isEmpty()) add("🌱" to "Not much to show yet — check back after a few days of use.")
    }
    InsightCard {
        Text("Patterns", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        lines.forEach { (emoji, text) ->
            Row(modifier = Modifier.padding(top = 12.dp)) {
                Text(emoji, fontSize = 18.sp, modifier = Modifier.semantics { contentDescription = "" })
                Text(text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
