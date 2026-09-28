package io.github.gobi12b.reclaimlife.ui.home

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.HOURLY_BREAKS_WITHIN_LIMIT
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.MAX_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.MAX_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.Mood
import io.github.gobi12b.reclaimlife.data.PauseDuration
import io.github.gobi12b.reclaimlife.data.currentStreak
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.hourlyUnblockAt
import io.github.gobi12b.reclaimlife.data.lastDays
import io.github.gobi12b.reclaimlife.data.reelsInWindow
import io.github.gobi12b.reclaimlife.service.AccessibilityStatus
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.OutlinedButton
import io.github.gobi12b.reclaimlife.service.hasUsageAccess
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.AccessibilityConsentDialog
import io.github.gobi12b.reclaimlife.ui.common.CompactBrandMark
import io.github.gobi12b.reclaimlife.ui.common.HOURLY_LIMIT_PRESETS
import io.github.gobi12b.reclaimlife.ui.common.LimitPicker
import io.github.gobi12b.reclaimlife.ui.common.PrivacyPolicyLink
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.common.SwapPicker
import io.github.gobi12b.reclaimlife.ui.replacement.SwapSession
import io.github.gobi12b.reclaimlife.ui.common.rememberAccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.rememberHasUsageAccess
import io.github.gobi12b.reclaimlife.ui.common.rememberUsage
import io.github.gobi12b.reclaimlife.ui.common.rememberTrackedApps
import io.github.gobi12b.reclaimlife.ui.common.AppPickerSheet
import io.github.gobi12b.reclaimlife.data.TrackedApp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Top-anchored dashboard: a greeting with Pause, alerts (the states that matter most), then the
 * one number to act on, screen time, the week — and, set apart below, everything you configured.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    dailyLimit: Int,
    hourlyLimit: Int,
    limitMode: LimitMode,
    recentReelTimes: List<Long>,
    todayCount: Int,
    extraAllowance: Int,
    nickname: String,
    swapActivity: ReplacementActivity,
    flashcardDeck: FlashcardDeck,
    daysWithinLimit: Int,
    daysExceededLimit: Int,
    pausedUntilMs: Long,
    dayHistory: Map<String, DayOutcome>,
    onLimitChange: (Int) -> Unit,
    onHourlyLimitChange: (Int) -> Unit,
    onLimitModeChange: (LimitMode) -> Unit,
    onSwapChange: (ReplacementActivity, FlashcardDeck) -> Unit,
    onTrackedAppsChange: (List<TrackedApp>) -> Unit,
    onPause: (PauseDuration) -> Unit,
    onResume: () -> Unit
) {
    val name = nickname.trim()
    var showEditSheet by remember { mutableStateOf(false) }
    var showHourlySheet by remember { mutableStateOf(false) }
    var showSwapSheet by remember { mutableStateOf(false) }
    var tryingSwap by remember { mutableStateOf(false) }
    var showPauseConfirmDialog by remember { mutableStateOf(false) }
    val accessibilityStatus = rememberAccessibilityStatus()
    val trackedApps = rememberTrackedApps()
    val usage = rememberUsage(days = 1, packages = remember(trackedApps) { trackedApps.map { it.packageName }.toSet() })
    var showAppsSheet by remember { mutableStateOf(false) }
    val hasUsageAccess = rememberHasUsageAccess()

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

    // The hourly window rolls with time, not just with new reels, so it needs its own clock.
    var hourlyNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(limitMode.usesHourly) {
        while (limitMode.usesHourly) {
            hourlyNowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val thisHour = reelsInWindow(recentReelTimes, hourlyNowMs).size
    val hourlyUnblockAtMs = if (limitMode.usesHourly) hourlyUnblockAt(recentReelTimes, hourlyLimit, hourlyNowMs) else null

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeHeader(name = name, nowMs = nowMs, showPause = !isPaused, onPause = { showPauseConfirmDialog = true })

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

            // Remaining leads — "7 left" is the number you act on; used/limit sits beside it.
            if (limitMode.usesDaily) {
                val effectiveLimit = dailyLimit + extraAllowance
                val reached = todayCount >= effectiveLimit
                HeroCard(
                    title = "Today",
                    used = todayCount,
                    limit = effectiveLimit,
                    headline = if (reached) "Limit reached" else "${effectiveLimit - todayCount} left",
                    detail = "$todayCount of $effectiveLimit reels today",
                    message = when {
                        reached && name.isNotEmpty() -> "That's today's reels, $name. The rest of the day is yours."
                        reached -> "That's today's reels. The rest of the day is yours."
                        name.isNotEmpty() -> "Every reel you skip is a little time back for you, $name."
                        else -> "Every reel you skip is a little time back for you."
                    },
                    extraNote = if (extraAllowance > 0) "$dailyLimit limit + $extraAllowance extra today" else null,
                    isPaused = isPaused
                )
            } else {
                val unblockInMs = hourlyUnblockAtMs?.let { it - hourlyNowMs }
                HeroCard(
                    title = "This hour",
                    used = thisHour,
                    limit = hourlyLimit,
                    headline = if (unblockInMs != null) "Back in ${formatPauseRemaining(unblockInMs)}" else "${(hourlyLimit - thisHour).coerceAtLeast(0)} left",
                    detail = "$thisHour of $hourlyLimit in the last 60 min · $todayCount today",
                    message = when {
                        unblockInMs != null -> "Reels are taking a short break. They're back soon, or right after a 2-minute break."
                        name.isNotEmpty() -> "Every reel you skip is a little time back for you, $name."
                        else -> "Every reel you skip is a little time back for you."
                    },
                    extraNote = null,
                    isPaused = isPaused
                )
            }
            // In Both mode the hourly window is the other live number — say where it stands.
            if (limitMode == LimitMode.BOTH) {
                val unblockInMs = hourlyUnblockAtMs?.let { it - hourlyNowMs }
                Text(
                    text = if (unblockInMs != null) {
                        "Hourly limit reached · back in ${formatPauseRemaining(unblockInMs)}, or after a 2-minute break"
                    } else {
                        "This hour: $thisHour of $hourlyLimit reels"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            ScreenTimeCard(usage = usage, tracked = trackedApps, hasUsageAccess = hasUsageAccess)

            WeekCard(
                hourlyOnly = !limitMode.usesDaily,
                dayHistory = dayHistory,
                daysWithinLimit = daysWithinLimit,
                daysExceededLimit = daysExceededLimit,
                todayMs = nowMs
            )

            SectionLabel("Your setup")
            SetupCard(
                limitMode = limitMode,
                isPaused = isPaused,
                dailyLimit = dailyLimit,
                hourlyLimit = hourlyLimit,
                swapActivity = swapActivity,
                flashcardDeck = flashcardDeck,
                tracked = trackedApps,
                onEditApps = { showAppsSheet = true },
                onLimitModeChange = onLimitModeChange,
                onEditDaily = { showEditSheet = true },
                onLowerDailyToCap = { onLimitChange(MAX_DAILY_REEL_LIMIT) },
                onEditHourly = { showHourlySheet = true },
                onChangeSwap = { showSwapSheet = true },
                onTrySwap = { tryingSwap = true }
            )
            PrivacyPolicyLink(modifier = Modifier.align(Alignment.CenterHorizontally))
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

        if (showHourlySheet) {
            EditHourlyLimitSheet(
                hourlyLimit = hourlyLimit,
                isPaused = isPaused,
                onDismiss = { showHourlySheet = false },
                onSave = {
                    onHourlyLimitChange(it)
                    showHourlySheet = false
                }
            )
        }

        if (showAppsSheet) {
            AppPickerSheet(
                current = trackedApps,
                onDismiss = { showAppsSheet = false },
                onSave = {
                    onTrackedAppsChange(it)
                    showAppsSheet = false
                }
            )
        }

        if (showSwapSheet) {
            EditSwapSheet(
                activity = swapActivity,
                deck = flashcardDeck,
                onDismiss = { showSwapSheet = false },
                onSave = { activity, deck ->
                    onSwapChange(activity, deck)
                    showSwapSheet = false
                }
            )
        }

        if (tryingSwap) {
            Dialog(
                onDismissRequest = { tryingSwap = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    SwapSession(
                        activity = swapActivity,
                        deck = flashcardDeck,
                        headline = "Try your swap",
                        subtitle = "This is what you'll get when you reach your limit.",
                        finishLabel = "Done",
                        onFinish = { tryingSwap = false },
                        onSkip = { tryingSwap = false },
                        skipLabel = "Close"
                    )
                }
            }
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
    var showHelp by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
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
            // Same disclosure + explicit consent as onboarding before Settings opens (Play's
            // prominent-disclosure rule covers every path, including "Not now" users and restarts).
            Button(
                onClick = { showConsent = true },
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

    if (showConsent) {
        AccessibilityConsentDialog(onDismiss = { showConsent = false })
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
                    "Nice — smaller numbers get easier after a few days."
                } else {
                    "Go at your own pace — lower it once the current number feels easy."
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
                    "That's okay if today needs it. Smaller numbers usually get easier after a few days, " +
                        "so you can always bring it back down."
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
                    Text("Raise it")
                }
            }
        )
    }
}

/** Same rules as the daily sheet: only lowering while paused, and raising asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHourlyLimitSheet(
    hourlyLimit: Int,
    isPaused: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingLimit by remember { mutableIntStateOf(hourlyLimit) }
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
            Text("Hourly limit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Counts reels in any rolling 60 minutes. Hit it and reels pause until the " +
                    "hour frees up — or right away after a 2-minute break.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            LimitPicker(
                value = pendingLimit,
                onValueChange = { pendingLimit = it },
                ceiling = if (isPaused) hourlyLimit else maxOf(MAX_HOURLY_REEL_LIMIT, hourlyLimit),
                presets = HOURLY_LIMIT_PRESETS,
                unitLabel = "reels / hour",
                fieldLabel = "Hourly limit"
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
            Row(modifier = Modifier.padding(top = 16.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    if (pendingLimit > hourlyLimit) confirmRaise = true else onSave(pendingLimit)
                }) {
                    Text("Save")
                }
            }
        }
    }

    if (confirmRaise) {
        AlertDialog(
            onDismissRequest = { confirmRaise = false },
            title = { Text("Raise it to $pendingLimit an hour?") },
            text = {
                Text(
                    "That's okay if it's needed. The hourly limit helps keep one sitting from running long, " +
                        "and you can always bring it back down."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmRaise = false
                    onDismiss()
                }) {
                    Text("Keep $hourlyLimit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmRaise = false
                    onSave(pendingLimit)
                }) {
                    Text("Raise it")
                }
            }
        )
    }
}

/** Changing the swap isn't loosening anything, so it saves without a confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSwapSheet(
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    onDismiss: () -> Unit,
    onSave: (ReplacementActivity, FlashcardDeck) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingActivity by remember { mutableStateOf(activity) }
    var pendingDeck by remember { mutableStateOf(deck) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Your 2-minute swap", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "What you'll get instead of more reels when you reach a limit.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            SwapPicker(
                activity = pendingActivity,
                deck = pendingDeck,
                onActivityChange = { pendingActivity = it },
                onDeckChange = { pendingDeck = it },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.padding(top = 16.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onSave(pendingActivity, pendingDeck) }) { Text("Save") }
            }
        }
    }
}
