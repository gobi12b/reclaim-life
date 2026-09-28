package io.github.gobi12b.reclaimlife.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.HomeInsight
import io.github.gobi12b.reclaimlife.data.InsightAction
import io.github.gobi12b.reclaimlife.data.LimitStatus
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.currentStreak
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.lastDays
import io.github.gobi12b.reclaimlife.service.Progress
import io.github.gobi12b.reclaimlife.ui.common.Motion
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * A warm hello and the way to Settings, with no numbers: the greeting in the serif voice, the date
 * under it. The logo is decorative here; the greeting is the heading.
 */
@Composable
internal fun HomeHeader(name: String, nowMs: Long, onSettings: () -> Unit, largeFont: Boolean) {
    val context = LocalContext.current
    val hourKey = nowMs / 3_600_000L
    val hour = remember(hourKey) { Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY) }
    val date = remember(hourKey) {
        DateUtils.formatDateTime(context, nowMs, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
    }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    Row(
        verticalAlignment = if (largeFont) Alignment.Top else Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        SproutBadge(modifier = Modifier.clearAndSetSemantics { }, size = 36.dp)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = if (name.isEmpty()) greeting else "$greeting, $name",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "Settings" }) {
            GearIcon(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Last on Home and deliberately quiet: pausing is allowed, not encouraged, and it still goes
 * through the pause sheet's friction. Hidden while paused, when the banner offers Resume instead.
 */
@Composable
internal fun PauseTrackingButton(onPause: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    TextButton(onClick = onPause, modifier = modifier.heightIn(min = 48.dp)) {
        PauseBars(color = color, modifier = Modifier.size(16.dp))
        Text("Pause tracking", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Direction and words for the last 24 hours against the 24 before. Never red, never a minus sign. */
internal fun trend(totalMs: Long, previousMs: Long): Pair<TrendDirection, String> {
    val diff = totalMs - previousMs
    return when {
        abs(diff) < 60_000L -> TrendDirection.SAME to "About the same as the day before"
        diff < 0 -> TrendDirection.DOWN to "${formatUsage(-diff)} less than the day before"
        else -> TrendDirection.UP to "${formatUsage(diff)} more than the day before"
    }
}

/** A drawn arrow plus words, read as one line. Shared by the Last 24 hours hero and section. */
@Composable
internal fun TrendLine(totalMs: Long, previousMs: Long, color: Color, modifier: Modifier = Modifier) {
    val (direction, words) = trend(totalMs, previousMs)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { }
    ) {
        TrendGlyph(direction, color, Modifier.size(16.dp))
        Text(words, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * One stacked bar of the last 24 hours, in tracked order. A 2dp [gapColor] gap keeps neighbouring
 * segments apart when their colours are close. Decorative: the legend carries the numbers.
 */
@Composable
internal fun UsageBar(perApp: List<Pair<TrackedApp, Long>>, gapColor: Color, modifier: Modifier = Modifier) {
    val total = perApp.sumOf { it.second }
    val segments = perApp.filter { it.second > 0 }.map { (app, ms) -> slotColor(app.slot) to ms }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(Radii.bar))
            .clearAndSetSemantics { }
    ) {
        if (total <= 0) return@Canvas
        var x = 0f
        segments.forEach { (color, ms) ->
            val w = size.width * ms / total
            drawRect(color, Offset(x, 0f), Size(w, size.height))
            x += w
            if (x < size.width - 1f) drawRect(gapColor, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
    }
}

/**
 * The per-app times, biggest first, so colour is never the only label. Apps with no time are left
 * out, as they are from the bar.
 */
@Composable
internal fun UsageLegend(
    perApp: List<Pair<TrackedApp, Long>>,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(modifier = modifier) {
        perApp.filter { it.second > 0 }.sortedByDescending { it.second }.forEach { (app, ms) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .semantics(mergeDescendants = true) { }
            ) {
                Box(Modifier.size(10.dp).background(slotColor(app.slot), CircleShape))
                Text(
                    rememberAppLabel(app.packageName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                Text(formatUsage(ms), style = MaterialTheme.typography.bodyMedium, color = valueColor)
            }
        }
    }
}

/**
 * The number to act on, compact: a ring gauge, "20 left" over "30 of 50 reels today", and a chevron
 * to the Limits screen. Near the limit it turns gold, but it also moves above the hero and the ring
 * runs near-empty, so colour is never the only signal.
 */
@Composable
internal fun LimitsPill(status: LimitStatus, nowMs: Long, emphasised: Boolean, onOpen: () -> Unit, reducedMotion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container = if (emphasised) scheme.tertiaryContainer else scheme.surfaceContainerHigh
    val leadColor = if (emphasised) scheme.onTertiaryContainer else scheme.onSurface
    val detailColor = if (emphasised) scheme.onTertiaryContainer.copy(alpha = 0.85f) else scheme.onSurfaceVariant
    val fill = if (emphasised) scheme.onTertiaryContainer else scheme.primary
    val track = if (emphasised) scheme.onTertiaryContainer.copy(alpha = 0.2f) else scheme.outlineVariant

    val unblockAt = status.hourlyUnblockAtMs
    val lead = when {
        unblockAt != null -> "Back in ${formatPauseRemaining(unblockAt - nowMs)}"
        status.hourlyOnly -> "${status.left} left this hour"
        else -> "${status.left} left"
    }
    val detail = if (status.hourlyOnly) "${status.used} of ${status.total} this hour" else "${status.used} of ${status.total} reels today"
    val spokenLead = when {
        unblockAt != null -> {
            val minutes = ((unblockAt - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L
            "Reels are back in $minutes " + if (minutes == 1L) "minute" else "minutes"
        }
        status.hourlyOnly -> lead
        else -> "${status.left} reels left"
    }
    val fraction = if (status.blocked) 0f else status.left.toFloat() / status.total.coerceAtLeast(1)
    val shape = RoundedCornerShape(Radii.pill)

    Surface(
        shape = shape,
        color = container,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .clickable(onClickLabel = "Open limits", role = Role.Button, onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                // Not a live region: the hourly countdown would be announced every second.
                .clearAndSetSemantics { contentDescription = "$spokenLead. $detail." }
        ) {
            LimitGauge(fraction = fraction, fill = fill, track = track, reducedMotion = reducedMotion)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(lead, style = MaterialTheme.typography.titleMedium, color = leadColor)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = detailColor)
            }
            Chevron(if (emphasised) scheme.onTertiaryContainer else scheme.onSurfaceVariant)
        }
    }
}

/** A ring of what's left, filling clockwise from the top. Decorative: the pill says it in words. */
@Composable
internal fun LimitGauge(fraction: Float, fill: Color, track: Color, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else tween(Motion.gaugeMs, easing = FastOutSlowInEasing),
        label = "gauge"
    )
    Canvas(modifier = modifier.size(32.dp).clearAndSetSemantics { }) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(track, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
        if (shown > 0f) {
            drawArc(fill, -90f, 360f * shown, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * One sentence on a soft note, a drawn glyph with its description, and at most one action — a text
 * button, so it never competes with a filled primary. Announced politely when it changes.
 */
@Composable
internal fun InsightNote(insight: HomeInsight, onAction: (HomeInsight) -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radii.panel),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            InsightGlyph(insight.kind)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(insight.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (insight.action != InsightAction.NONE && insight.actionLabel != null) {
                    // Pulled left so the label's text lines up with the sentence above it.
                    TextButton(
                        onClick = { onAction(insight) },
                        modifier = Modifier
                            .offset(x = (-12).dp)
                            .padding(top = 4.dp)
                    ) {
                        Text(insight.actionLabel, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** A section title on the background: no box, just type and space. */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/**
 * The last 24 hours, honestly and per app, once time won back is the hero. Reading content, so it
 * sits on the background with no card. Reel counts live on the Limits pill and screen, not here.
 */
@Composable
internal fun Last24hSection(progress: Progress, tracked: List<TrackedApp>, largeFont: Boolean) {
    val perApp = tracked.map { it to (progress.last24hByApp[it.packageName] ?: 0L) }
    val total = perApp.sumOf { it.second }
    val value = formatUsage(total)
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Last 24 hours")
        val valueModifier = Modifier
            .padding(top = 8.dp)
            .clearAndSetSemantics { contentDescription = "${spoken(value)} on your apps" }
        if (largeFont) {
            Column(modifier = valueModifier) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("on your apps", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom, modifier = valueModifier) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    " on your apps",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        if (progress.previous24hMs > 0 || progress.usageFromSystem) {
            TrendLine(total, progress.previous24hMs, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(top = 4.dp))
        }
        if (total > 0) {
            UsageBar(perApp, gapColor = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = 16.dp))
            UsageLegend(perApp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * The week at a glance. Days differ by shape, not just colour: a filled check within the limit, a
 * crossed ring over it (no red — the point is to notice, not to feel bad), a small dashed ring for
 * no data, and a ring for today.
 */
@Composable
internal fun WeekSection(
    dayHistory: Map<String, DayOutcome>,
    daysWithinLimit: Int,
    daysExceededLimit: Int,
    todayMs: Long,
    largeFont: Boolean
) {
    // Keyed per hour, not per ms tick, so the pause countdown doesn't recompute this every second
    // but the strip still rolls over to a new day within the hour after midnight.
    val hourKey = todayMs / (60 * 60 * 1000L)
    val cells = remember(dayHistory, hourKey) { lastDays(dayHistory, todayMs) }
    val streak = remember(dayHistory, hourKey) { currentStreak(dayHistory, todayMs) }
    val weekdays = remember(cells) {
        val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val name = SimpleDateFormat("EEEE", Locale.getDefault())
        cells.map { cell -> runCatching { parse.parse(cell.dateKey)?.let(name::format) }.getOrNull() ?: cell.dateKey }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (largeFont) {
            SectionHeading("This week")
            if (streak > 0) StreakChip(streak, Modifier.padding(top = 8.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("This week", Modifier.weight(1f))
                if (streak > 0) StreakChip(streak)
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            cells.forEachIndexed { i, cell ->
                val description = when {
                    cell.isToday -> "Today, in progress"
                    cell.outcome == DayOutcome.WITHIN -> "${weekdays[i]}, within limit"
                    cell.outcome == DayOutcome.OVER -> "${weekdays[i]}, over limit"
                    else -> "${weekdays[i]}, no data"
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(40.dp)
                        .clearAndSetSemantics { contentDescription = description }
                ) {
                    DayMark(outcome = cell.outcome, isToday = cell.isToday)
                    Text(
                        text = cell.weekdayInitial,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (cell.isToday) FontWeight.Bold else null,
                        color = if (cell.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        Text(
            text = if (daysWithinLimit > 0 || daysExceededLimit > 0) {
                "All time: $daysWithinLimit " + (if (daysWithinLimit == 1) "day" else "days") +
                    " within limit · $daysExceededLimit over"
            } else {
                "Today closes out at midnight — your first dot fills in tomorrow."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** onPrimaryContainer on primaryContainer: light primary text on a light surface is under 4.5:1. */
@Composable
internal fun StreakChip(streak: Int, modifier: Modifier = Modifier) {
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = RoundedCornerShape(Radii.chip),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$streak-day streak within your limit" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            LeafGlyph(onColor, Modifier.size(14.dp))
            Text(
                "$streak-day streak",
                style = MaterialTheme.typography.titleSmall,
                color = onColor,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** One day of the week strip, 36dp. Decorative: the cell carries the words. */
@Composable
internal fun DayMark(outcome: DayOutcome?, isToday: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(36.dp)) {
            when {
                isToday -> {
                    val stroke = 2.5.dp.toPx()
                    drawCircle(scheme.primary, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                }
                outcome == DayOutcome.WITHIN -> drawCircle(scheme.primary)
                outcome == DayOutcome.OVER -> {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(scheme.surfaceContainerHigh)
                    drawCircle(scheme.outline, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                }
                // No record (counter wasn't running): skipped by the streak, not counted against it.
                else -> {
                    val stroke = 1.5.dp.toPx()
                    val dash = 3.dp.toPx()
                    drawCircle(
                        scheme.outline,
                        radius = (12.dp.toPx() - stroke) / 2,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                    )
                }
            }
        }
        when {
            isToday -> Unit
            outcome == DayOutcome.WITHIN -> CheckGlyph(scheme.onPrimary, Modifier.size(18.dp), strokeWidth = 2.5.dp)
            outcome == DayOutcome.OVER -> CrossGlyph(scheme.onSurfaceVariant, Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
}
