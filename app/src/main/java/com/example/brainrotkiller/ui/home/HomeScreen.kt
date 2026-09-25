package com.example.brainrotkiller.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.data.DayOutcome
import com.example.brainrotkiller.data.MAX_DAILY_REEL_LIMIT
import com.example.brainrotkiller.data.Mood
import com.example.brainrotkiller.data.PauseDuration
import com.example.brainrotkiller.data.currentStreak
import com.example.brainrotkiller.data.formatPauseRemaining
import com.example.brainrotkiller.data.lastDays
import com.example.brainrotkiller.service.AccessibilityStatus
import com.example.brainrotkiller.ui.common.CompactBrandMark
import com.example.brainrotkiller.ui.common.LimitPicker
import com.example.brainrotkiller.ui.common.SproutBadge
import com.example.brainrotkiller.ui.common.rememberAccessibilityStatus
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Top-anchored dashboard: alerts first (they're the states that matter most), then today's
 * count, the limit, and the trend — with the deliberate low-emphasis "Pause tracking" last.
 */
@Composable
fun HomeScreen(
    dailyLimit: Int,
    todayCount: Int,
    extraAllowance: Int,
    nickname: String,
    daysWithinLimit: Int,
    daysExceededLimit: Int,
    pausedUntilMs: Long,
    dayHistory: Map<String, DayOutcome>,
    onLimitChange: (Int) -> Unit,
    onPause: (PauseDuration) -> Unit,
    onResume: () -> Unit
) {
    val name = nickname.trim()
    var showEditSheet by remember { mutableStateOf(false) }
    var showPauseConfirmDialog by remember { mutableStateOf(false) }
    val accessibilityStatus = rememberAccessibilityStatus()

    // Ticks once a second only while paused, so the countdown moves and the screen flips back to
    // normal on its own when the pause runs out — nothing has to write "resumed" to storage.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pausedUntilMs) {
        nowMs = System.currentTimeMillis()
        while (nowMs < pausedUntilMs) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    val isPaused = nowMs < pausedUntilMs

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CompactBrandMark(modifier = Modifier.padding(bottom = 4.dp))

            if (accessibilityStatus != AccessibilityStatus.ON) {
                AccessibilityBanner(status = accessibilityStatus)
            }

            if (isPaused) {
                PausedBanner(
                    remainingMs = pausedUntilMs - nowMs,
                    resumesAtMs = pausedUntilMs,
                    onResume = onResume
                )
            }

            // Still visible while paused so progress isn't hidden — just dimmed to read as "on hold".
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.alpha(if (isPaused) 0.45f else 1f)
            ) {
                TodayCard(
                    todayCount = todayCount,
                    dailyLimit = dailyLimit,
                    extraAllowance = extraAllowance,
                    name = name
                )
                LimitCard(
                    dailyLimit = dailyLimit,
                    onEdit = { showEditSheet = true },
                    onLowerToCap = { onLimitChange(MAX_DAILY_REEL_LIMIT) }
                )
                HistoryCard(
                    dayHistory = dayHistory,
                    daysWithinLimit = daysWithinLimit,
                    daysExceededLimit = daysExceededLimit,
                    todayMs = nowMs
                )
            }

            if (!isPaused) {
                TextButton(
                    onClick = { showPauseConfirmDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Pause tracking",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showEditSheet) {
            EditLimitSheet(
                dailyLimit = dailyLimit,
                todayCount = todayCount,
                isPaused = isPaused,
                onDismiss = { showEditSheet = false },
                onSave = {
                    onLimitChange(it)
                    showEditSheet = false
                }
            )
        }

        if (showPauseConfirmDialog) {
            PauseRecordDialog(
                onDismiss = { showPauseConfirmDialog = false },
                onConfirmPause = { duration ->
                    onPause(duration)
                    showPauseConfirmDialog = false
                }
            )
        }
    }
}

@Composable
private fun AccessibilityBanner(status: AccessibilityStatus) {
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    val notRunning = status == AccessibilityStatus.ENABLED_NOT_RUNNING
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlertGlyph()
                Text(
                    text = if (notRunning) "The reel counter stopped" else "Reels aren't being counted",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .semantics { heading() }
                )
            }
            Text(
                text = if (notRunning) {
                    "Accessibility is still switched on for ReclaimLife, but Android isn't running it " +
                        "(usually after a crash or a battery saver). Nothing is counted or blocked until " +
                        "you turn it off and back on."
                } else {
                    "Accessibility access for ReclaimLife is off, so nothing is counted or blocked."
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (notRunning) "Restart it in Accessibility settings" else "Turn on Accessibility access")
            }
            TextButton(onClick = { showHelp = !showHelp }) {
                Text(
                    (if (notRunning) "Keeps stopping?" else "Switch won't move?") + if (showHelp) " ▴" else " ▾",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            AnimatedVisibility(visible = showHelp) {
                Text(
                    text = if (notRunning) {
                        "Some phones kill background services to save battery. Settings → Apps → " +
                            "ReclaimLife → Battery → Unrestricted keeps the counter alive."
                    } else {
                        "In Accessibility, find ReclaimLife under Downloaded apps and turn it on. " +
                            "If the switch won't move: Settings → Apps → ReclaimLife → ⋮ menu → " +
                            "Allow restricted settings, then try again."
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** A drawn "!" badge, so warnings don't rely on red text alone. */
@Composable
private fun AlertGlyph() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
            .clearAndSetSemantics { contentDescription = "Warning" },
        contentAlignment = Alignment.Center
    ) {
        Text("!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
    }
}

@Composable
private fun PausedBanner(remainingMs: Long, resumesAtMs: Long, onResume: () -> Unit) {
    val resumesAt = remember(resumesAtMs) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(resumesAtMs)) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SproutBadge(size = 28.dp, modifier = Modifier.alpha(0.6f))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "Tracking paused · ${formatPauseRemaining(remainingMs)} left",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Reels aren't counted or blocked. It turns back on by itself at $resumesAt.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Resume now")
            }
        }
    }
}

@Composable
private fun TodayCard(todayCount: Int, dailyLimit: Int, extraAllowance: Int, name: String) {
    val effectiveLimit = dailyLimit + extraAllowance
    val reachedLimit = todayCount >= effectiveLimit
    val remaining = (effectiveLimit - todayCount).coerceAtLeast(0)
    val mood = remember(todayCount, effectiveLimit) { Mood.forProgress(todayCount, effectiveLimit) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = mood.emoji,
                fontSize = 40.sp,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Mood: ${mood.label}" }
            )
            Text(
                text = "Today · ${mood.label}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Remaining leads — "86 left" is the number you act on; used/total sits under it.
            Text(
                text = if (reachedLimit) "Limit reached" else "$remaining left today",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "$todayCount / $effectiveLimit reels watched",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LinearProgressIndicator(
                progress = { (todayCount.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            // The extra allowance is spelled out wherever the total appears, so "190" next to a
            // "186" limit reads as "186 + 4 you asked for", not as a bug.
            if (extraAllowance > 0) {
                Text(
                    text = "$dailyLimit limit + $extraAllowance extra today",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = when {
                    reachedLimit && name.isNotEmpty() ->
                        "You've hit today's limit, $name. That's the whole point — go live your life."
                    reachedLimit -> "You've hit today's limit. That's the whole point — go live your life."
                    name.isNotEmpty() -> "Be true to life, not the algorithm, $name. You're the one keeping score."
                    else -> "Be true to life, not the algorithm. You're the one keeping score."
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun LimitCard(dailyLimit: Int, onEdit: () -> Unit, onLowerToCap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily limit", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$dailyLimit reels", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
            // Limits saved above the cap (from the old 1–1000 slider) are kept, never forced
            // down — just a gentle, one-tap nudge toward the range the picker now offers.
            if (dailyLimit > MAX_DAILY_REEL_LIMIT) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Most people start at $MAX_DAILY_REEL_LIMIT or under. Ready to come down?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onLowerToCap) { Text("Lower to $MAX_DAILY_REEL_LIMIT") }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Last 7 days",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (streak) {
                        0 -> "No streak yet"
                        1 -> "1-day streak"
                        else -> "$streak-day streak"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
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
                            fontSize = 11.sp,
                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            if (daysWithinLimit > 0 || daysExceededLimit > 0) {
                Text(
                    text = "All time: $daysWithinLimit " + (if (daysWithinLimit == 1) "day" else "days") +
                        " within limit · $daysExceededLimit over",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Text(
                    text = "Today closes out at midnight — your first dot fills in tomorrow.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DayDot(outcome: DayOutcome?, isToday: Boolean) {
    val size = 18.dp
    when {
        isToday -> Box(
            Modifier
                .size(size)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
        outcome == DayOutcome.WITHIN -> Box(
            Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        outcome == DayOutcome.OVER -> Box(
            Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.error, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Shape cue as well as color, for anyone who can't tell green from red.
            Text("×", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
        }
        // No record (counter wasn't running): neutral grey, smaller, so it reads as "unknown"
        // rather than a result — it's skipped by the streak, not counted against it.
        else -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

/**
 * Editing happens in a sheet so today's count stays in view. There's no lecture on open anymore;
 * the pushback against raising the limit happens once, at Save, only if they actually raise it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLimitSheet(
    dailyLimit: Int,
    todayCount: Int,
    isPaused: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingLimit by remember { mutableIntStateOf(dailyLimit) }
    var confirmRaise by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Daily limit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "You've watched $todayCount today · current limit $dailyLimit",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            LimitPicker(
                value = pendingLimit,
                onValueChange = { pendingLimit = it },
                // While paused only lowering is allowed: a pause plus a raise is two escape
                // hatches at once.
                ceiling = if (isPaused) dailyLimit else maxOf(MAX_DAILY_REEL_LIMIT, dailyLimit)
            )
            if (isPaused) {
                Text(
                    text = "You can lower your limit while paused, not raise it.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Text(
                text = if (pendingLimit < dailyLimit) {
                    "That's the direction. Smaller numbers get easier by day three."
                } else {
                    "Improve day by day — lower it once the current number feels easy."
                },
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    if (pendingLimit > dailyLimit) confirmRaise = true else onSave(pendingLimit)
                }) {
                    Text("Save")
                }
            }
        }
    }

    if (confirmRaise) {
        AlertDialog(
            onDismissRequest = { confirmRaise = false },
            title = { Text("Raise it to $pendingLimit?") },
            text = {
                Text(
                    "Going up isn't progress — that's the algorithm talking, not you. " +
                        "You'll feel worse tonight, not better."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmRaise = false
                    onDismiss()
                }) {
                    Text("Keep $dailyLimit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmRaise = false
                    onSave(pendingLimit)
                }) {
                    Text("Raise anyway", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
