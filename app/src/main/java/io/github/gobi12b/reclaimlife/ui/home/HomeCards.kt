package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.EARLIER_TODAY_KEY
import io.github.gobi12b.reclaimlife.data.HOURLY_BREAKS_WITHIN_LIMIT
import io.github.gobi12b.reclaimlife.data.HomeInsight
import io.github.gobi12b.reclaimlife.data.InsightAction
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.currentStreak
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.formatProjection
import io.github.gobi12b.reclaimlife.data.formatSaved
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.formatYearProjection
import io.github.gobi12b.reclaimlife.data.lastDays
import io.github.gobi12b.reclaimlife.service.Progress
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

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
 * The logo, a time-of-day greeting, Pause and Settings. Pause sits here so it's easy to find, but
 * as a small tonal button rather than a headline action — and it goes through the pause sheet's
 * friction. Hidden while paused, when the banner below offers Resume instead.
 */
@Composable
internal fun HomeHeader(name: String, nowMs: Long, showPause: Boolean, onPause: () -> Unit, onSettings: () -> Unit) {
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
        IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = "Settings" }) {
            GearIcon(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A drawn gear, so the app still needs no icon library. */
@Composable
private fun GearIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = 2.dp.toPx()
        drawCircle(color, radius = size.width * 0.28f, center = c, style = Stroke(stroke))
        drawCircle(color, radius = size.width * 0.1f, center = c, style = Stroke(stroke))
        repeat(8) { i ->
            val angle = Math.toRadians(i * 45.0)
            val inner = size.width * 0.3f
            val outer = size.width * 0.44f
            drawLine(
                color,
                Offset(c.x + inner * kotlin.math.cos(angle).toFloat(), c.y + inner * kotlin.math.sin(angle).toFloat()),
                Offset(c.x + outer * kotlin.math.cos(angle).toFloat(), c.y + outer * kotlin.math.sin(angle).toFloat()),
                strokeWidth = stroke * 1.6f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Gold, not grey: a pause is a state to notice, and "Resume now" is the one thing to do about it.
 * Rest of today shows its intention: "Enjoy: Movie night with friends · back at 00:00".
 */
@Composable
internal fun PausedBanner(remainingMs: Long, resumesAtMs: Long, intention: String?, onResume: () -> Unit) {
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
                    text = if (intention.isNullOrBlank()) {
                        "Reels aren't counted or blocked. Back on by itself at $resumesAt."
                    } else {
                        "Enjoy: $intention · back at $resumesAt"
                    },
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) { Text("Resume now") }
        }
    }
}

/** Rest of today's one-minute delayed start, cancellable until it runs out. */
@Composable
internal fun PendingPauseCard(startsInMs: Long, intention: String?, onCancel: () -> Unit) {
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
                    text = "Rest of today starts in ${formatPauseRemaining(startsInMs)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = if (intention.isNullOrBlank()) "Changed your mind? Cancel keeps tracking on." else "Enjoy: $intention",
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) { Text("Cancel") }
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
private fun PauseBars(color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width * 0.22f
        val h = size.height * 0.7f
        val top = (size.height - h) / 2f
        drawRoundRect(color, Offset(size.width * 0.2f, top), Size(w, h), CornerRadius(w / 2))
        drawRoundRect(color, Offset(size.width * 0.58f, top), Size(w, h), CornerRadius(w / 2))
    }
}

/** For installs from before the apps step: shown once, and dismissing it is permanent. */
@Composable
internal fun AppsCheckCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Text(
                text = "Check which apps ReclaimLife helps with ›",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 16.dp)
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** The number to act on, compact: "20 left · 30 of 50 reels today ›". Opens the Limits screen. */
@Composable
internal fun LimitsRow(left: String, detail: String, emphasised: Boolean, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (emphasised) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open limits", onClick = onOpen)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) { append(left) }
                    append(" · $detail")
                },
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

private fun heroBrush(start: Color, end: Color) = Brush.linearGradient(listOf(start, lerp(start, end, 0.55f)))

/** "Working out your starting point…" while the baseline is read — usually under 2 seconds. */
@Composable
internal fun SavedSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroBrush(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
            .padding(20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text("Working out your starting point…", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** "42 min" → "42 minutes", "6 h 10 min" → "6 hours 10 minutes", for TalkBack. */
internal fun spoken(text: String): String =
    text.replace(Regex("(\\d+) h\\b"), "$1 hours").replace(Regex("(\\d+) min\\b"), "$1 minutes")

/**
 * Home's hero: time won back. It never leads with "0 min saved" — on a zero day it leads with the
 * running total since the start. Before the waking window it shows yesterday's final figure. One
 * month figure only; the year lives in the detail sheet (from day 14). Tap for details.
 */
@Composable
internal fun SavedCard(
    progress: Progress,
    tracked: List<TrackedApp>,
    hasUsageAccess: Boolean,
    onOpenDetails: () -> Unit
) {
    val context = LocalContext.current
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    val today = progress.today
    var showApps by remember { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    val zeroDay = today.totalMs < 60_000L && progress.sinceStartedMs >= 60_000L
    val headline: String
    val secondary: String?
    when {
        today.beforeWaking && progress.history.yesterdayMs != null -> {
            headline = "Yesterday you saved ${formatSaved(progress.history.yesterdayMs)}"
            secondary = null
        }
        zeroDay -> {
            headline = "Since you started: ${formatSaved(progress.sinceStartedMs)}"
            secondary = "Nothing saved yet today"
        }
        else -> {
            headline = "${formatSaved(today.totalMs)} saved today"
            secondary = null
        }
    }
    val monthLine = progress.monthMs?.let { "≈ ${formatProjection(it)} a month at this pace" } ?: "Your monthly estimate shows up on day 3"
    val spokenSummary = buildString {
        append(spoken(headline)).append(". ")
        secondary?.let { append(it).append(". ") }
        progress.monthMs?.let { append("About ${spoken(formatProjection(it))} this month. ") }
        progress.yearMs?.let { append("About ${spoken(formatProjection(it))} this year.") }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroBrush(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
            .clickable(onClickLabel = "Show details", onClick = onOpenDetails)
            .padding(20.dp)
    ) {
        Column {
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spokenSummary }) {
                Text(
                    text = if (zeroDay) "Time won back" else "Time won back today",
                    fontSize = 13.sp,
                    color = onColor.copy(alpha = 0.8f)
                )
                Text(headline, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = onColor)
                secondary?.let { Text(it, fontSize = 14.sp, color = onColor) }
                Text(monthLine, fontSize = 15.sp, color = onColor, modifier = Modifier.padding(top = 6.dp))
            }
            progress.provisionalUntilMs?.let {
                Text("Estimate · settles on ${dateFormat.format(Date(it))}", fontSize = 12.sp, color = onColor.copy(alpha = 0.8f))
            }
            val notes = buildList {
                if (progress.trackingOffTodayMs >= 60_000L) add("Tracking was off for ${formatSaved(progress.trackingOffTodayMs)}")
                if (progress.pausedToday) add("Not tracked while paused")
            }
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), fontSize = 12.sp, color = onColor.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
            }
            if (!hasUsageAccess) {
                Text(
                    text = "Using ReclaimLife's timing only · Allow Usage access ›",
                    fontSize = 12.sp,
                    color = onColor,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(role = Role.Button) { runCatching { context.startActivity(usageAccessSettingsIntent()) } }
                        .padding(vertical = 6.dp)
                )
            }
            // Per app, collapsed by default; no minus signs, which read as loss.
            if (today.byApp.size > 1) {
                TextButton(
                    onClick = { showApps = !showApps },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics { stateDescription = if (showApps) "Expanded" else "Collapsed" }
                ) {
                    Text("By app " + if (showApps) "▴" else "▾", color = onColor)
                }
                AnimatedVisibility(visible = showApps) {
                    Column {
                        tracked.filter { it.packageName in today.byApp }.forEach { app ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Box(Modifier.size(10.dp).background(slotColor(app.slot), CircleShape))
                                Text(
                                    "${rememberAppLabel(app.packageName)} ${formatSaved(today.byApp[app.packageName] ?: 0L)} saved",
                                    fontSize = 14.sp,
                                    color = onColor,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Since you started, and — from day 14 — the year. Real totals first, projections marked "≈". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedDetailSheet(progress: Progress, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Time won back", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            DetailRow("Since you started", formatSaved(progress.sinceStartedMs))
            DetailRow("Today", formatSaved(progress.today.totalMs))
            DetailRow("This month", progress.monthMs?.let { "≈ ${formatProjection(it)}" } ?: "Shows up on day 3")
            DetailRow("This year", progress.yearMs?.let { "≈ ${formatYearProjection(it)}" } ?: "Shows up on day 14")
            flavourLine(progress)?.let {
                Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Measured against your usual daily time before ReclaimLife. Days when tracking was off or " +
                    "paused don't count either way, and a heavier day counts as 0 — never less.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** "That's about 40 books" or "about 320 long walks" — rotated weekly, only with a year of ≥ 1 day. */
private fun flavourLine(progress: Progress): String? {
    val year = progress.yearMs ?: return null
    if (year < 24 * 60 * 60_000L) return null
    val week = Calendar.getInstance().apply { timeInMillis = progress.nowMs }.get(Calendar.WEEK_OF_YEAR)
    return if (week % 2 == 0) {
        "That's about ${year / (6 * 60 * 60_000L)} books"
    } else {
        "That's about ${year / (45 * 60_000L)} long walks"
    }
}

/** One sentence, an icon with a description, and at most one action. Announced politely when it changes. */
@Composable
internal fun InsightCard(insight: HomeInsight, onAction: (HomeInsight) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(modifier = Modifier.padding(20.dp)) {
            Text(
                insight.kind.emoji,
                fontSize = 24.sp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = insight.kind.iconDescription }
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(insight.text, fontSize = 15.sp)
                if (insight.action != InsightAction.NONE && insight.actionLabel != null) {
                    FilledTonalButton(onClick = { onAction(insight) }, modifier = Modifier.padding(top = 10.dp)) {
                        Text(insight.actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * The last 24 hours, honestly and per app: a labelled stacked bar, then a row per app with its
 * minutes and (where counted) reels today. [hero] is for when there are no saved figures yet.
 */
@Composable
internal fun Last24hCard(
    progress: Progress?,
    tracked: List<TrackedApp>,
    reelsByApp: Map<String, Int>,
    hero: Boolean,
    hasUsageAccess: Boolean
) {
    val context = LocalContext.current
    val perApp = tracked.map { it to (progress?.last24hByApp?.get(it.packageName) ?: 0L) }
    val total = perApp.sumOf { it.second }
    val colors = tracked.associate { it.packageName to slotColor(it.slot) }
    val gap = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hero) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (hero) 30.sp else 24.sp)) {
                        append(if (progress == null) "…" else formatUsage(total))
                    }
                    append(" on your apps in the last 24 hours")
                },
                fontSize = 15.sp,
                lineHeight = if (hero) 34.sp else 28.sp
            )
            if (progress != null && (progress.previous24hMs > 0 || progress.usageFromSystem)) {
                val diff = total - progress.previous24hMs
                val (arrow, words) = when {
                    abs(diff) < 60_000L -> "→" to "About the same as the day before"
                    diff < 0 -> "↓" to "${formatUsage(-diff)} less than the day before"
                    else -> "↑" to "${formatUsage(diff)} more than the day before"
                }
                Row(modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { }) {
                    Text(arrow, fontSize = 14.sp, modifier = Modifier.clearAndSetSemantics { })
                    Text(words, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (total > 0) {
                Canvas(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clearAndSetSemantics { }
                ) {
                    var x = 0f
                    perApp.filter { it.second > 0 }.forEach { (app, ms) ->
                        val w = size.width * ms / total
                        drawRect(colors.getValue(app.packageName), Offset(x, 0f), Size(w, size.height))
                        x += w
                        // A 2dp gap keeps neighbouring segments apart when their colours are close.
                        if (x < size.width - 1f) drawRect(gap, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                    }
                }
            }
            // The legend doubles as the per-app numbers, so colour is never the only label.
            perApp.sortedByDescending { it.second }.forEach { (app, ms) ->
                val reels = reelsByApp[app.packageName]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) { }
                ) {
                    Box(Modifier.size(10.dp).background(colors.getValue(app.packageName), CircleShape))
                    Text(rememberAppLabel(app.packageName), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(
                        formatUsage(ms) + if (reels != null) " · $reels reels" else "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            reelsByApp[EARLIER_TODAY_KEY]?.let { earlier ->
                Row(modifier = Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) { }) {
                    Spacer(Modifier.width(18.dp))
                    Text("Earlier today", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("$earlier reels", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hero) {
                Text(
                    text = if (!hasUsageAccess) {
                        "Allow Usage access to see how much time you win back ›"
                    } else {
                        "Time won back shows up once your phone has 3 days of history."
                    },
                    fontSize = 13.sp,
                    fontWeight = if (!hasUsageAccess) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .then(
                            if (!hasUsageAccess) {
                                Modifier.clickable(role = Role.Button) { runCatching { context.startActivity(usageAccessSettingsIntent()) } }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 6.dp)
                )
            }
        }
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
        // No record (counter wasn't running): a small hollow ring, so it reads as "unknown" by
        // shape as well as colour — it's skipped by the streak, not counted against it.
        else -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(10.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}
