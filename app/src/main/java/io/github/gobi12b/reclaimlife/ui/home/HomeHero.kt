package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.formatProjection
import io.github.gobi12b.reclaimlife.data.formatSaved
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.formatYearProjection
import io.github.gobi12b.reclaimlife.service.Progress
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.Motion
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import io.github.gobi12b.reclaimlife.ui.theme.DisplayFamily
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import io.github.gobi12b.reclaimlife.ui.theme.reclaim
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal enum class HeadlineKind { TODAY, YESTERDAY, SINCE_START, FRESH }

internal data class SavedHeadline(val kind: HeadlineKind, val ms: Long)

/**
 * What the hero leads with. First match wins, and no branch ever leads with "0 min": a zero day
 * falls back to the running total, and day one to "Just getting started".
 */
internal fun savedHeadline(todayMs: Long, beforeWaking: Boolean, yesterdayMs: Long?, sinceStartedMs: Long): SavedHeadline = when {
    beforeWaking && (yesterdayMs ?: 0L) >= 60_000L -> SavedHeadline(HeadlineKind.YESTERDAY, yesterdayMs ?: 0L)
    todayMs >= 60_000L -> SavedHeadline(HeadlineKind.TODAY, todayMs)
    sinceStartedMs >= 60_000L -> SavedHeadline(HeadlineKind.SINCE_START, sinceStartedMs)
    else -> SavedHeadline(HeadlineKind.FRESH, 0L)
}

internal fun savedHeadline(progress: Progress): SavedHeadline =
    savedHeadline(progress.today.totalMs, progress.today.beforeWaking, progress.history.yesterdayMs, progress.sinceStartedMs)

/** "42 min" → "42 minutes", "6 h 10 min" → "6 hours 10 minutes", "2 h 05 min" → "2 hours 5 minutes", for TalkBack. */
internal fun spoken(text: String): String =
    text.replace(Regex("(\\d+) h\\b")) { m -> m.groupValues[1].toInt().let { "$it " + if (it == 1) "hour" else "hours" } }
        .replace(Regex("(\\d+) min\\b")) { m -> m.groupValues[1].toInt().let { "$it " + if (it == 1) "minute" else "minutes" } }

/**
 * The one large, colourful surface on Home: a leaf-green gradient, flat, clickable only when there's
 * something to open. Content colour is onHero, so text on it needs no colour of its own.
 */
@Composable
internal fun HeroPanel(
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.reclaim
    val shape = RoundedCornerShape(Radii.hero)
    // The plain Surface plus clickable (not Surface(onClick)) so TalkBack gets "Show details".
    Surface(
        shape = shape,
        color = Color.Transparent,
        contentColor = colors.onHero,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom))),
            content = content
        )
    }
}

/**
 * A figure in two voices: serif digits at [digitStyle], unit words ("h", "min") a step smaller on
 * the same baseline, so the number reads first.
 */
@Composable
internal fun HeroNumberText(
    text: String,
    modifier: Modifier = Modifier,
    digitStyle: TextStyle = MaterialTheme.typography.displayMedium,
    unitSize: TextUnit = 24.sp
) {
    val unit = SpanStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Normal, fontSize = unitSize, letterSpacing = 0.sp)
    Text(
        text = buildAnnotatedString {
            Regex("(\\d+)|(\\D+)").findAll(text).forEach { m ->
                if (m.groups[1] != null) append(m.value) else withStyle(unit) { append(m.value) }
            }
        },
        style = digitStyle,
        modifier = modifier
    )
}

/** Keeps the plant at the bottom end, clear of the text; below it at large font. */
private fun Modifier.heroTextPadding(largeFont: Boolean): Modifier =
    if (largeFont) {
        padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 128.dp)
    } else {
        padding(start = 24.dp, top = 24.dp, end = 136.dp, bottom = 52.dp)
    }

private val HeroMinHeight = 196.dp

/** The ground band under the hill: notes, actions and lists that belong to the hero. */
@Composable
private fun GroundBand(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.reclaim.heroGround)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        content = content
    )
}

/** Banners, "By app" and help text expand in; with animations off they just appear. */
internal fun expandEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) EnterTransition.None else expandVertically(tween(Motion.expandMs, easing = FastOutSlowInEasing)) + fadeIn(tween(Motion.expandMs))

internal fun expandExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) ExitTransition.None else shrinkVertically(tween(Motion.expandMs, easing = FastOutSlowInEasing)) + fadeOut(tween(Motion.expandMs))

/**
 * Home's focal point: time won back, grown into a plant, plus the one month figure on Home. The
 * year lives in the detail sheet (from day 14). Tap for details.
 */
@Composable
internal fun SavedHero(
    progress: Progress,
    tracked: List<TrackedApp>,
    hasUsageAccess: Boolean,
    largeFont: Boolean,
    reducedMotion: Boolean,
    onOpenDetails: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.reclaim
    val today = progress.today
    var showApps by rememberSaveable { mutableStateOf(false) }
    val headline = savedHeadline(progress)
    val provisional = progress.provisionalUntilMs != null
    val monthLine = progress.monthMs?.let { "≈ ${formatProjection(it)} a month at this pace" + if (provisional) " · estimate" else "" }
        ?: "Your monthly estimate shows up on day 3"
    val summary = buildString {
        val figure = spoken(formatSaved(headline.ms))
        append(
            when (headline.kind) {
                HeadlineKind.TODAY -> "$figure saved today. "
                HeadlineKind.YESTERDAY -> "Yesterday you saved $figure. "
                HeadlineKind.SINCE_START -> "Since you started: $figure. Nothing saved yet today. "
                HeadlineKind.FRESH -> "Just getting started. Time you win back today shows up here. "
            }
        )
        val month = progress.monthMs
        if (month != null) {
            append("About ${spoken(formatProjection(month))} this month").append(if (provisional) ", an estimate. " else ". ")
        } else {
            append("Your monthly estimate shows up on day 3. ")
        }
        progress.yearMs?.let { append("About ${spoken(formatProjection(it))} this year.") }
    }.trim()
    val notes = buildList {
        if (progress.trackingOffTodayMs >= 60_000L) add("Tracking was off for ${formatSaved(progress.trackingOffTodayMs)}")
        if (progress.pausedToday) add("Not tracked while paused")
        if (!hasUsageAccess) add("Using ReclaimLife's timing only")
    }
    val showByApp = today.byApp.size > 1

    HeroPanel(onClick = onOpenDetails, onClickLabel = "Show details") {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = HeroMinHeight)) {
            HeroBackdrop(Modifier.matchParentSize())
            GrowthPlant(
                stage = growthStage(progress.sinceStartedMs),
                reducedMotion = reducedMotion,
                compact = largeFont,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)
            )
            Column(
                modifier = Modifier
                    .heroTextPadding(largeFont)
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = summary }
            ) {
                AnimatedContent(
                    targetState = headline,
                    contentKey = { it.kind to formatSaved(it.ms) },
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(Motion.fadeMs, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(tween(Motion.fadeMs, easing = LinearOutSlowInEasing))
                        }
                    },
                    label = "headline"
                ) { shown ->
                    Column {
                        if (shown.kind == HeadlineKind.FRESH) {
                            Text(
                                "Just getting started",
                                style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 38.sp)
                            )
                        } else {
                            HeroNumberText(formatSaved(shown.ms))
                        }
                        Text(
                            text = when (shown.kind) {
                                HeadlineKind.TODAY -> "saved today"
                                HeadlineKind.YESTERDAY -> "saved yesterday"
                                HeadlineKind.SINCE_START -> "saved since you started"
                                HeadlineKind.FRESH -> "Time you win back today shows up here."
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (shown.kind == HeadlineKind.SINCE_START) {
                            Text(
                                "Nothing saved yet today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onHeroMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    monthLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroMuted,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        if (notes.isNotEmpty() || !hasUsageAccess || showByApp) {
            GroundBand {
                if (notes.isNotEmpty()) {
                    Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onHeroMuted)
                }
                if (!hasUsageAccess || showByApp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val buttonColors = ButtonDefaults.textButtonColors(contentColor = colors.onHero)
                        if (!hasUsageAccess) {
                            TextButton(
                                onClick = { runCatching { context.startActivity(usageAccessSettingsIntent()) } },
                                colors = buttonColors
                            ) { Text("Allow Usage access") }
                        }
                        // Per app, collapsed by default; no minus signs, which read as loss.
                        if (showByApp) {
                            TextButton(
                                onClick = { showApps = !showApps },
                                colors = buttonColors,
                                modifier = Modifier.semantics { stateDescription = if (showApps) "Expanded" else "Collapsed" }
                            ) {
                                Text("By app")
                                Chevron(colors.onHero, rotation = if (showApps) -90f else 90f)
                            }
                        }
                    }
                }
                if (showByApp) {
                    AnimatedVisibility(visible = showApps, enter = expandEnter(reducedMotion), exit = expandExit(reducedMotion)) {
                        Column {
                            tracked.filter { it.packageName in today.byApp }.forEach { app ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .semantics(mergeDescendants = true) { }
                                ) {
                                    Box(Modifier.size(10.dp).background(slotColor(app.slot), CircleShape))
                                    Text(
                                        "${rememberAppLabel(app.packageName)} ${formatSaved(today.byApp[app.packageName] ?: 0L)} saved",
                                        style = MaterialTheme.typography.bodyMedium,
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
}

/**
 * The hero before there's a baseline: the last 24 hours, honestly and per app, with a seed that
 * says growth is coming. Not clickable — there are no saved figures to detail yet.
 */
@Composable
internal fun Last24hHero(
    progress: Progress,
    tracked: List<TrackedApp>,
    hasUsageAccess: Boolean,
    largeFont: Boolean,
    reducedMotion: Boolean
) {
    val context = LocalContext.current
    val colors = MaterialTheme.reclaim
    val perApp = tracked.map { it to (progress.last24hByApp[it.packageName] ?: 0L) }
    val total = perApp.sumOf { it.second }
    val showTrend = progress.previous24hMs > 0 || progress.usageFromSystem
    val summary = buildString {
        append(spoken(formatUsage(total)).replaceFirstChar { it.uppercase() }).append(" on your apps in the last 24 hours.")
        if (showTrend) append(" ").append(trend(total, progress.previous24hMs).second).append(".")
    }

    HeroPanel(onClick = null, onClickLabel = null) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = HeroMinHeight)) {
            HeroBackdrop(Modifier.matchParentSize())
            GrowthPlant(
                stage = GrowthStage.SEED,
                reducedMotion = reducedMotion,
                compact = largeFont,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)
            )
            Column(
                modifier = Modifier
                    .heroTextPadding(largeFont)
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = summary }
            ) {
                // Usage is information, not the reward, so it sits a step below the saved figure.
                if (total < 60_000L) {
                    Text("Under a minute", style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 38.sp))
                } else {
                    HeroNumberText(formatUsage(total), digitStyle = MaterialTheme.typography.displaySmall, unitSize = 22.sp)
                }
                Text(
                    "on your apps in the last 24 hours",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (showTrend) {
                    TrendLine(total, progress.previous24hMs, colors.onHeroMuted, Modifier.padding(top = 8.dp))
                }
            }
        }
        GroundBand {
            if (total > 0) {
                UsageBar(perApp, gapColor = colors.heroGround, modifier = Modifier.padding(top = 12.dp))
                UsageLegend(perApp, labelColor = colors.onHero, valueColor = colors.onHeroMuted, modifier = Modifier.padding(top = 8.dp))
            }
            if (!hasUsageAccess) {
                TextButton(
                    onClick = { runCatching { context.startActivity(usageAccessSettingsIntent()) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onHero),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Allow Usage access to see how much time you win back", modifier = Modifier.weight(1f, fill = false))
                    Chevron(colors.onHero)
                }
            } else {
                Text(
                    "Time won back shows up once your phone has 3 days of history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onHeroMuted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * While progress loads: the hero's shape with placeholder bars, the same height as the saved
 * hero so nothing jumps when the numbers arrive. Pulses gently unless animations are off.
 */
@Composable
internal fun HeroSkeleton(message: String, reducedMotion: Boolean) {
    val colors = MaterialTheme.reclaim
    val alpha = if (reducedMotion) {
        Motion.skeletonAlphaStatic
    } else {
        val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = Motion.skeletonAlphaLow,
            targetValue = Motion.skeletonAlphaHigh,
            animationSpec = infiniteRepeatable(tween(Motion.skeletonPulseMs), RepeatMode.Reverse),
            label = "skeletonAlpha"
        )
        pulse
    }
    HeroPanel(onClick = null, onClickLabel = null) {
        Box(modifier = Modifier.fillMaxWidth().height(HeroMinHeight)) {
            HeroBackdrop(Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = message
                    }
            ) {
                Column(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                    val bar = colors.onHero.copy(alpha = 0.10f)
                    val shape = RoundedCornerShape(12.dp)
                    Box(Modifier.size(148.dp, 48.dp).background(bar, shape))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(184.dp, 16.dp).background(bar, shape))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(120.dp, 14.dp).background(bar, shape))
                }
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroMuted,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

/** Since you started, and — from day 14 — the year. Real totals first, projections marked "≈". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedDetailSheet(progress: Progress, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Time won back",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            DetailRow("Since you started", formatSaved(progress.sinceStartedMs))
            DetailRow("Today", formatSaved(progress.today.totalMs))
            DetailRow("This month", progress.monthMs?.let { "≈ ${formatProjection(it)}" } ?: "Shows up on day 3")
            DetailRow("This year", progress.yearMs?.let { "≈ ${formatYearProjection(it)}" } ?: "Shows up on day 14")
            flavourLine(progress)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(plantLine(progress.sinceStartedMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The hero only says "estimate"; the date it settles on lives here.
            progress.provisionalUntilMs?.let {
                Text(
                    "Today's figure is an estimate · settles on ${dateFormat.format(Date(it))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Measured against your usual daily time before ReclaimLife. Days when tracking was off or " +
                    "paused don't count either way, and a heavier day counts as 0 — never less.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** What the hero's plant means and when it next grows. */
internal fun plantLine(sinceStartedMs: Long): String {
    val next = nextStage(growthStage(sinceStartedMs))
        ?: return "Your plant is in full bloom. Every minute from here is a bonus."
    return "Your plant grows as your time adds up. It reaches its next stage at ${formatSaved(next.thresholdMs)} since you started."
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
