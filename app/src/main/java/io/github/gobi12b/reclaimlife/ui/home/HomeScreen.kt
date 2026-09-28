package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gobi12b.reclaimlife.MainViewModel
import io.github.gobi12b.reclaimlife.data.HomeInsight
import io.github.gobi12b.reclaimlife.data.InsightAction
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.formatSaved
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.limitStatus
import io.github.gobi12b.reclaimlife.data.pausesStartedToday
import io.github.gobi12b.reclaimlife.data.restOfTodayAvailableAt
import io.github.gobi12b.reclaimlife.service.AccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.AccessibilityConsentDialog
import io.github.gobi12b.reclaimlife.ui.common.PrivacyPolicyLink
import io.github.gobi12b.reclaimlife.ui.common.isInstalled
import io.github.gobi12b.reclaimlife.ui.common.rememberAccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.rememberHasUsageAccess
import io.github.gobi12b.reclaimlife.ui.common.rememberProgress
import io.github.gobi12b.reclaimlife.ui.limits.EditLimitSheet
import io.github.gobi12b.reclaimlife.ui.limits.SwapDialog
import io.github.gobi12b.reclaimlife.ui.pause.PauseSheet
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-anchored and progress-first: alerts, then time won back, the limits row (moved right under
 * the alerts when it's nearly out), one insight, the last 24 hours, and the week. Settings and the
 * Limits screen are a tap away; reels left leads there, not here.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val dailyLimit by viewModel.dailyReelLimit.collectAsStateWithLifecycle()
    val hourlyLimit by viewModel.hourlyReelLimit.collectAsStateWithLifecycle()
    val limitMode by viewModel.limitMode.collectAsStateWithLifecycle()
    val recentReelTimes by viewModel.recentReelTimes.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayReelCount.collectAsStateWithLifecycle()
    val countsByApp by viewModel.todayCountsByApp.collectAsStateWithLifecycle()
    val extraAllowance by viewModel.todayExtraAllowance.collectAsStateWithLifecycle()
    val swapActivity by viewModel.replacementActivity.collectAsStateWithLifecycle()
    val flashcardDeck by viewModel.flashcardDeck.collectAsStateWithLifecycle()
    val daysWithinLimit by viewModel.daysWithinLimit.collectAsStateWithLifecycle()
    val daysExceededLimit by viewModel.daysExceededLimit.collectAsStateWithLifecycle()
    val pausedFromMs by viewModel.pausedFromMs.collectAsStateWithLifecycle()
    val pausedUntilMs by viewModel.pausedUntilMs.collectAsStateWithLifecycle()
    val pauseLog by viewModel.pauseLog.collectAsStateWithLifecycle()
    val intention by viewModel.pauseIntention.collectAsStateWithLifecycle()
    val dayHistory by viewModel.dayHistory.collectAsStateWithLifecycle()
    val trackedApps by viewModel.trackedApps.collectAsStateWithLifecycle()
    val showAppsCard by viewModel.showAppsCard.collectAsStateWithLifecycle()
    val name = nickname.trim()

    val accessibilityStatus = rememberAccessibilityStatus()
    val hasUsageAccess = rememberHasUsageAccess()
    val progress = rememberProgress(todayCount, pausedFromMs, pausedUntilMs, trackedApps, hasUsageAccess)
    // An uninstalled tracked app is hidden here; its history is kept and Settings says "Not installed".
    val installedApps = remember(trackedApps) { trackedApps.filter { isInstalled(context, it.packageName) } }

    var showPauseSheet by remember { mutableStateOf(false) }
    var showSavedDetails by remember { mutableStateOf(false) }
    var swapOnDemand by remember { mutableStateOf(false) }
    var lowerLimitPrefill by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ticks once a second while a pause is running or about to start, so countdowns move and the
    // screen flips back on its own — nothing has to write "resumed" to storage. The hourly window
    // also rolls with time, not just with new reels.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pausedFromMs, pausedUntilMs, limitMode.usesHourly) {
        nowMs = System.currentTimeMillis()
        while (nowMs < pausedUntilMs || limitMode.usesHourly) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    val pendingStart = nowMs < pausedFromMs && pausedFromMs < pausedUntilMs
    val isPaused = !pendingStart && nowMs < pausedUntilMs

    val status = limitStatus(limitMode, dailyLimit, extraAllowance, todayCount, hourlyLimit, recentReelTimes, nowMs)
    val limitsRow: @Composable () -> Unit = {
        LimitsRow(
            left = when {
                status.hourlyUnblockAtMs != null -> "Back in ${formatPauseRemaining(status.hourlyUnblockAtMs - nowMs)}"
                status.hourlyOnly -> "${status.left} left this hour"
                else -> "${status.left} left"
            },
            detail = if (status.hourlyOnly) "${status.used} of ${status.total} this hour" else "${status.used} of ${status.total} reels today",
            emphasised = status.nearLimit,
            onOpen = onOpenLimits
        )
    }

    fun onInsightAction(insight: HomeInsight) {
        when (insight.action) {
            InsightAction.TURN_ON_GATE -> insight.actionApp?.let { pkg ->
                viewModel.turnOnGateFor(pkg) { undo ->
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Pause before opening is on for ${io.github.gobi12b.reclaimlife.ui.common.appLabel(context, pkg)}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) undo()
                    }
                }
            }
            InsightAction.LOWER_LIMIT -> lowerLimitPrefill = (dailyLimit - 10).coerceAtLeast(1)
            InsightAction.START_SWAP -> swapOnDemand = true
            InsightAction.NONE -> Unit
        }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeHeader(
                    name = name,
                    nowMs = nowMs,
                    showPause = !isPaused && !pendingStart,
                    onPause = { showPauseSheet = true },
                    onSettings = onOpenSettings
                )

                // 1. Alerts.
                if (accessibilityStatus != AccessibilityStatus.ON) AccessibilityBanner(status = accessibilityStatus)
                if (pendingStart) {
                    PendingPauseCard(startsInMs = pausedFromMs - nowMs, intention = intention, onCancel = viewModel::cancelPendingPause)
                }
                if (isPaused) {
                    PausedBanner(
                        remainingMs = pausedUntilMs - nowMs,
                        resumesAtMs = pausedUntilMs,
                        intention = intention,
                        onResume = viewModel::resumeTracking
                    )
                }
                if (showAppsCard) {
                    AppsCheckCard(
                        onOpen = {
                            viewModel.dismissAppsCard()
                            onOpenSettings()
                        },
                        onDismiss = viewModel::dismissAppsCard
                    )
                }
                // Nearly out (or blocked): the number to act on moves up, right under the alerts.
                if (status.nearLimit) limitsRow()

                // 2. The hero: time won back, or the last 24 hours before there's a baseline.
                val loaded = progress
                when {
                    loaded == null && hasUsageAccess -> SavedSkeleton()
                    loaded != null && loaded.hasBaseline -> SavedCard(
                        progress = loaded,
                        tracked = installedApps,
                        hasUsageAccess = hasUsageAccess,
                        onOpenDetails = { showSavedDetails = true }
                    )
                    else -> Last24hCard(loaded, installedApps, countsByApp, hero = true, hasUsageAccess = hasUsageAccess)
                }

                // 3. Limits row, above the fold.
                if (!status.nearLimit) limitsRow()

                // 4. One insight.
                loaded?.let { InsightCard(it.insight, ::onInsightAction) }

                // 5. The last 24 hours, when it isn't already the hero.
                if (loaded != null && loaded.hasBaseline) {
                    Last24hCard(loaded, installedApps, countsByApp, hero = false, hasUsageAccess = hasUsageAccess)
                }

                // 6. The week and streak.
                WeekCard(
                    hourlyOnly = !limitMode.usesDaily,
                    dayHistory = dayHistory,
                    daysWithinLimit = daysWithinLimit,
                    daysExceededLimit = daysExceededLimit,
                    todayMs = nowMs
                )
                PrivacyPolicyLink(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }

        if (showPauseSheet) {
            val calmLine = when {
                progress?.hasBaseline == true -> "You've won back ${formatSaved(progress.today.totalMs)} today."
                progress != null -> "${formatUsage(progress.last24hMs).replaceFirstChar { it.uppercase() }} on your apps in the last 24 hours."
                else -> "Take a breath."
            }
            PauseSheet(
                pausesToday = pausesStartedToday(pauseLog, nowMs),
                restOfTodayAvailableAtMs = restOfTodayAvailableAt(pauseLog, nowMs),
                calmLine = calmLine,
                onDismiss = { showPauseSheet = false },
                onStart = { duration, reason ->
                    viewModel.startPause(duration, reason)
                    showPauseSheet = false
                },
                onScheduleRestOfToday = { reason, text ->
                    viewModel.scheduleRestOfToday(reason, text)
                    showPauseSheet = false
                }
            )
        }

        if (showSavedDetails) progress?.let { SavedDetailSheet(it, onDismiss = { showSavedDetails = false }) }

        if (swapOnDemand) {
            SwapDialog(
                activity = swapActivity,
                deck = flashcardDeck,
                headline = "Your 2-minute swap",
                subtitle = "A small reset, whenever you want one.",
                onClose = { swapOnDemand = false }
            )
        }

        lowerLimitPrefill?.let { prefill ->
            EditLimitSheet(
                dailyLimit = dailyLimit,
                todayCount = todayCount,
                isPaused = isPaused,
                prefill = prefill,
                onDismiss = { lowerLimitPrefill = null },
                onSave = {
                    viewModel.updateDailyLimit(it)
                    lowerLimitPrefill = null
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
                        "you turn it off and back on. Time saved leaves these hours out."
                } else {
                    "Accessibility access for ReclaimLife is off, so nothing is counted or blocked. " +
                        "Time saved leaves these hours out."
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
