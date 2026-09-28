package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.HOURLY_BREAKS_WITHIN_LIMIT
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.MAX_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.Mood
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.currentStreak
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.formatProjection
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.lastDays
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.LoadedUsage
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Section label between groups of cards: small caps-style, quiet. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 4.dp, top = 8.dp)
            .semantics { heading() }
    )
}

/**
 * The logo, a time-of-day greeting, and Pause. Pause sits here so it's easy to find, but as a
 * small tonal button rather than a headline action — and it still goes through the recording
 * confirm. Hidden while paused, when the banner below offers Resume instead.
 */
@Composable
internal fun HomeHeader(name: String, nowMs: Long, showPause: Boolean, onPause: () -> Unit) {
    val hour = remember(nowMs / 3_600_000L) { Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        SproutBadge(size = 44.dp)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = if (name.isEmpty()) greeting else "$greeting, $name",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text("ReclaimLife", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showPause) {
            FilledTonalButton(
                onClick = onPause,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                PauseBars(color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                Text("Pause", fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Gold, not grey: a pause is a state to notice, and "Resume" is the one thing to do about it. */
@Composable
internal fun PausedBanner(remainingMs: Long, resumesAtMs: Long, onResume: () -> Unit) {
    val resumesAt = remember(resumesAtMs) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(resumesAtMs)) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            PauseGlyph()
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = "Paused · ${formatPauseRemaining(remainingMs)} left",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Reels aren't counted or blocked. Back on by itself at $resumesAt.",
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) { Text("Resume") }
        }
    }
}

/** Two bars — the pause symbol, drawn so the state isn't carried by colour alone. */
@Composable
private fun PauseGlyph() {
    PauseBars(
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.size(28.dp).clearAndSetSemantics { contentDescription = "Paused" }
    )
}

@Composable
private fun PauseBars(color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width * 0.22f
        val h = size.height * 0.7f
        val top = (size.height - h) / 2f
        drawRoundRect(color, Offset(size.width * 0.2f, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
        drawRoundRect(color, Offset(size.width * 0.58f, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
    }
}

/**
 * The one number to act on, as a ring: what's left leads (the ring fills as reels are used), with
 * used/limit, the mood and any extras beside it. Dimmed when paused — the rest of the page isn't.
 */
@Composable
internal fun HeroCard(
    title: String,
    used: Int,
    limit: Int,
    headline: String,
    detail: String,
    message: String,
    extraNote: String?,
    isPaused: Boolean
) {
    val mood = remember(used, limit) { Mood.forProgress(used, limit) }
    val start = MaterialTheme.colorScheme.primaryContainer
    val end = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer, 0.55f)
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    val progress = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(start, end)))
            .alpha(if (isPaused) 0.6f else 1f)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = progress,
                    track = onColor.copy(alpha = 0.14f),
                    fill = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(124.dp)
                        .clearAndSetSemantics { contentDescription = "$used of $limit reels used" }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = mood.emoji,
                            fontSize = 30.sp,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = "Mood: ${mood.label}" }
                        )
                        Text("$used / $limit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = onColor)
                    }
                }
                Column(modifier = Modifier.padding(start = 18.dp)) {
                    Text(
                        text = "$title · ${mood.label}",
                        fontSize = 13.sp,
                        color = onColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = headline,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = onColor
                    )
                    Text(detail, fontSize = 13.sp, color = onColor.copy(alpha = 0.8f))
                    // The extra allowance is spelled out wherever the total appears, so a total
                    // above the limit reads as "limit + extra you asked for", not as a bug.
                    if (extraNote != null) {
                        Text(
                            text = extraNote,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = message,
                fontSize = 14.sp,
                color = onColor,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    track: androidx.compose.ui.graphics.Color,
    fill: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0f) {
                drawArc(fill, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * The last 24 hours in each chosen app (most-used first), and what that pace adds up to — the
 * same figures the open-pause screen shows. Without Usage access it says the numbers are partial
 * and offers it.
 */
@Composable
internal fun ScreenTimeCard(usage: LoadedUsage?, tracked: List<TrackedApp>, hasUsageAccess: Boolean) {
    val context = LocalContext.current
    val perApp = tracked.map { it to (usage?.last24hMs(it.packageName) ?: 0L) }.sortedByDescending { it.second }
    val total = perApp.sumOf { it.second }
    val maxMs = (perApp.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Last 24 hours", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (usage == null) "…" else formatUsage(total),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (tracked.size == 1) "in ${rememberAppLabel(tracked[0].packageName)}" else "across your ${tracked.size} apps",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            perApp.forEach { (app, ms) ->
                AppBarRow(label = rememberAppLabel(app.packageName), ms = ms, fraction = ms.toFloat() / maxMs, color = slotColor(app.slot))
            }
            if (total >= 60_000L) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                    ProjectionChip("≈ ${formatProjection(total * 30)}", "a month at this pace", Modifier.weight(1f))
                    ProjectionChip("≈ ${formatProjection(total * 365)}", "a year at this pace", Modifier.weight(1f))
                }
            }
            if (!hasUsageAccess) {
                Text(
                    text = "Only counts time since ReclaimLife started timing. Allow Usage access to read the full picture from Android — it stays on this phone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(usageAccessSettingsIntent()) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow Usage access") }
            }
        }
    }
}

@Composable
private fun AppBarRow(label: String, ms: Long, fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Column(modifier = Modifier.padding(top = 14.dp).semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(formatUsage(ms), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun ProjectionChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The week at a glance: a filled dot for a day within limit, a crossed one for over, a ring for today. */
@Composable
internal fun WeekCard(
    hourlyOnly: Boolean,
    dayHistory: Map<String, DayOutcome>,
    daysWithinLimit: Int,
    daysExceededLimit: Int,
    todayMs: Long
) {
    // Keyed per hour, not per ms tick, so the pause countdown doesn't recompute this every second
    // but the strip still rolls over to a new day within the hour after midnight.
    val hourKey = todayMs / (60 * 60 * 1000L)
    val cells = remember(dayHistory, hourKey) { lastDays(dayHistory, todayMs) }
    val streak = remember(dayHistory, hourKey) { currentStreak(dayHistory, todayMs) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "This week",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (streak) {
                        0 -> "No streak yet"
                        1 -> "🔥 1-day streak"
                        else -> "🔥 $streak-day streak"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (streak > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (streak > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                cells.forEach { cell ->
                    val description = when {
                        cell.isToday -> "Today, in progress"
                        cell.outcome == DayOutcome.WITHIN -> "${cell.dateKey}, within limit"
                        cell.outcome == DayOutcome.OVER -> "${cell.dateKey}, over limit"
                        else -> "${cell.dateKey}, no data"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
                    ) {
                        DayDot(outcome = cell.outcome, isToday = cell.isToday)
                        Text(
                            text = cell.weekdayInitial,
                            fontSize = 12.sp,
                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            Text(
                text = when {
                    daysWithinLimit > 0 || daysExceededLimit > 0 ->
                        "All time: $daysWithinLimit " + (if (daysWithinLimit == 1) "day" else "days") +
                            " within limit · $daysExceededLimit over"
                    else -> "Today closes out at midnight — your first dot fills in tomorrow."
                } + if (hourlyOnly) ". Hourly mode: a day is within with $HOURLY_BREAKS_WITHIN_LIMIT or fewer breaks that reopened reels." else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun DayDot(outcome: DayOutcome?, isToday: Boolean) {
    val size = 30.dp
    when {
        isToday -> Box(
            Modifier
                .size(size)
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
        outcome == DayOutcome.WITHIN -> Box(
            Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
        outcome == DayOutcome.OVER -> Box(
            Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.error, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Shape cue as well as colour, for anyone who can't tell green from red.
            Text("×", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
        }
        // No record (counter wasn't running): small and neutral, so it reads as "unknown" rather
        // than a result — it's skipped by the streak, not counted against it.
        else -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

/**
 * Everything you set up, in one card: how to limit, the numbers, and the swap. Dropping a limit
 * that's enforced now is a loosening, so it's confirmed (with "Keep" as the main button) and not
 * allowed while paused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupCard(
    limitMode: LimitMode,
    isPaused: Boolean,
    dailyLimit: Int,
    hourlyLimit: Int,
    swapActivity: ReplacementActivity,
    flashcardDeck: FlashcardDeck,
    tracked: List<TrackedApp>,
    onEditApps: () -> Unit,
    onLimitModeChange: (LimitMode) -> Unit,
    onEditDaily: () -> Unit,
    onLowerDailyToCap: () -> Unit,
    onEditHourly: () -> Unit,
    onChangeSwap: () -> Unit,
    onTrySwap: () -> Unit
) {
    var confirmMode by remember { mutableStateOf<LimitMode?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            val labels = tracked.map { rememberAppLabel(it.packageName) }
            SettingRow(
                emoji = "📱",
                title = "Apps to watch",
                value = when {
                    labels.isEmpty() -> "None"
                    labels.size <= 2 -> labels.joinToString(" & ")
                    else -> "${labels.take(2).joinToString(", ")} +${labels.size - 2}"
                },
                onClick = onEditApps
            )
            SetupDivider()
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Reel limit by", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    LimitMode.entries.forEachIndexed { index, mode ->
                        val loosens = limitMode.loosensTo(mode)
                        SegmentedButton(
                            selected = mode == limitMode,
                            onClick = {
                                when {
                                    mode == limitMode -> Unit
                                    loosens -> confirmMode = mode
                                    else -> onLimitModeChange(mode)
                                }
                            },
                            enabled = mode == limitMode || !(isPaused && loosens),
                            shape = SegmentedButtonDefaults.itemShape(index, LimitMode.entries.size)
                        ) { Text(mode.label) }
                    }
                }
                Text(
                    text = when (limitMode) {
                        LimitMode.DAILY -> "One cap for the whole day."
                        LimitMode.HOURLY -> "A 2-minute break whenever an hour's reels run out; no daily cap."
                        LimitMode.BOTH -> "A daily cap, plus a 2-minute break whenever an hour's reels run out."
                    } + if (isPaused && limitMode != LimitMode.BOTH) " While paused you can only add a limit." else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (limitMode.usesDaily) {
                SetupDivider()
                SettingRow(emoji = "☀️", title = "Daily limit", value = "$dailyLimit reels", onClick = onEditDaily)
                // Limits saved above the cap (from the old 1–1000 slider) are kept, never forced
                // down — just a gentle, one-tap nudge toward the range the picker now offers.
                if (dailyLimit > MAX_DAILY_REEL_LIMIT) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 56.dp, end = 8.dp)) {
                        Text(
                            text = "Most people start at $MAX_DAILY_REEL_LIMIT or under.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onLowerDailyToCap) { Text("Lower to $MAX_DAILY_REEL_LIMIT") }
                    }
                }
            }
            if (limitMode.usesHourly) {
                SetupDivider()
                SettingRow(emoji = "⏱️", title = "Hourly limit", value = "$hourlyLimit reels / hour", onClick = onEditHourly)
            }
            SetupDivider()
            SettingRow(
                emoji = swapActivity.emoji,
                title = "2-minute swap",
                value = swapActivity.label + if (swapActivity == ReplacementActivity.FLASHCARDS) " · ${flashcardDeck.label}" else "",
                onClick = onChangeSwap,
                trailing = { TextButton(onClick = onTrySwap) { Text("Try it") } }
            )
        }
    }

    confirmMode?.let { target ->
        val dropped = if (limitMode.usesDaily && !target.usesDaily) "daily" else "hourly"
        AlertDialog(
            onDismissRequest = { confirmMode = null },
            title = { Text("Drop the $dropped limit?") },
            text = {
                Text(
                    if (dropped == "daily") {
                        "Without a daily limit, the hours can quietly add up. You can switch back anytime."
                    } else {
                        "The hourly limit helps keep one sitting from running long. You can switch back anytime."
                    }
                )
            },
            confirmButton = {
                Button(onClick = { confirmMode = null }) { Text("Keep ${limitMode.label.lowercase()}") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmMode = null
                    onLimitModeChange(target)
                }) {
                    Text("Switch")
                }
            }
        )
    }
}

@Composable
private fun SetupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

/** A tappable settings row: emoji tile, title over value, and a chevron (or [trailing]). */
@Composable
private fun SettingRow(
    emoji: String,
    title: String,
    value: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Change $title", onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
        ) {
            Text(emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (trailing != null) {
            trailing()
        } else {
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        }
    }
}
