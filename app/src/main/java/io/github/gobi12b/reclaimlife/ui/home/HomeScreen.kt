package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gobi12b.reclaimlife.MainViewModel
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.HomeInsight
import io.github.gobi12b.reclaimlife.data.localDayStart
import io.github.gobi12b.reclaimlife.data.pausedMsOn
import io.github.gobi12b.reclaimlife.data.raiseTreeLine
import io.github.gobi12b.reclaimlife.data.InsightAction
import io.github.gobi12b.reclaimlife.data.formatSaved
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.limitStatus
import io.github.gobi12b.reclaimlife.data.pausesStartedToday
import io.github.gobi12b.reclaimlife.data.restOfTodayAvailableAt
import io.github.gobi12b.reclaimlife.service.AccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.appLabel
import io.github.gobi12b.reclaimlife.ui.common.isInstalled
import io.github.gobi12b.reclaimlife.ui.common.rememberAccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.rememberHasUsageAccess
import io.github.gobi12b.reclaimlife.ui.common.rememberProgress
import io.github.gobi12b.reclaimlife.ui.common.rememberReducedMotion
import io.github.gobi12b.reclaimlife.ui.limits.EditLimitSheet
import io.github.gobi12b.reclaimlife.ui.limits.SwapDialog
import io.github.gobi12b.reclaimlife.ui.pause.PauseSheet
import io.github.gobi12b.reclaimlife.ui.theme.HomeLayout
import io.github.gobi12b.reclaimlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One focal point: a greeting, any alerts, then the hero (time won back, and your tree), the
 * Limits pill (moved above the hero when it's nearly out), one insight, the last 24 hours and the
 * week on the background, and a quiet Pause at the end. State, ordering and sheets live here; the
 * pieces live in HomeHero, HomeSections and HomeBanners.
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
    val treeName by viewModel.treeName.collectAsStateWithLifecycle()
    val name = nickname.trim()

    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val reducedMotion = rememberReducedMotion()
    val accessibilityStatus = rememberAccessibilityStatus()
    val hasUsageAccess = rememberHasUsageAccess()
    // Limits and history are keys too, so a raise or a put-back updates the tree's line straight away.
    val progress = rememberProgress(
        todayCount, pausedFromMs, pausedUntilMs, trackedApps, hasUsageAccess,
        dailyLimit, hourlyLimit, limitMode, dayHistory, treeName, accessibilityStatus
    )
    // Quiet days (counter on, no reels, never closed out) count as within, as they do for the tree.
    val weekHistory = remember(dayHistory, progress?.quietDays) {
        progress?.quietDays.orEmpty().associateWith { DayOutcome.WITHIN } + dayHistory
    }
    // An uninstalled tracked app is hidden here; its history is kept and Settings says "Not installed".
    val installedApps = remember(trackedApps) { trackedApps.filter { isInstalled(context, it.packageName) } }

    var showPauseSheet by remember { mutableStateOf(false) }
    var showSavedDetails by remember { mutableStateOf(false) }
    var showTreeSheet by remember { mutableStateOf(false) }
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
        LimitsPill(status, nowMs, emphasised = status.nearLimit, onOpen = onOpenLimits, reducedMotion = reducedMotion)
    }

    fun onInsightAction(insight: HomeInsight) {
        when (insight.action) {
            InsightAction.TURN_ON_GATE -> insight.actionApp?.let { pkg ->
                viewModel.turnOnGateFor(pkg) { undo ->
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Pause before opening is on for ${appLabel(context, pkg)}",
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
            // No spacedBy: explicit spacers keep the groups visible in code — 12dp inside a group,
            // 32dp between groups.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HomeLayout.gutter)
                    .padding(top = Spacing.xs, bottom = Spacing.xxl)
            ) {
                HomeHeader(
                    name = name,
                    nowMs = nowMs,
                    onSettings = onOpenSettings,
                    largeFont = largeFont,
                    onPause = if (!isPaused && !pendingStart) ({ showPauseSheet = true }) else null
                )

                // 1. Alerts, each only when it applies. The spacer rides inside the animation so
                // the gap opens and closes with the banner.
                val alerts = listOf(accessibilityStatus != AccessibilityStatus.ON, pendingStart, isPaused, showAppsCard)
                val anyAlert = alerts.any { it }
                @Composable
                fun Alert(index: Int, content: @Composable () -> Unit) {
                    AnimatedVisibility(alerts[index], enter = expandEnter(reducedMotion), exit = expandExit(reducedMotion)) {
                        Column {
                            Spacer(Modifier.height(if (alerts.take(index).none { it }) Spacing.l else Spacing.s))
                            content()
                        }
                    }
                }
                Alert(0) { AccessibilityBanner(status = accessibilityStatus, reducedMotion = reducedMotion) }
                Alert(1) {
                    PendingPauseBanner(startsInMs = pausedFromMs - nowMs, intention = intention, onCancel = viewModel::cancelPendingPause)
                }
                Alert(2) {
                    PausedBanner(
                        remainingMs = pausedUntilMs - nowMs,
                        resumesAtMs = pausedUntilMs,
                        intention = intention,
                        onResume = viewModel::resumeTracking
                    )
                }
                Alert(3) {
                    AppsCheckBanner(
                        onOpen = {
                            viewModel.dismissAppsCard()
                            onOpenSettings()
                        },
                        onDismiss = viewModel::dismissAppsCard
                    )
                }

                // Nearly out (or blocked): the number to act on moves up, right under the alerts.
                if (status.nearLimit) {
                    Spacer(Modifier.height(if (anyAlert) Spacing.s else Spacing.l))
                    limitsRow()
                }

                // 2. The hero: time won back, or the last 24 hours before there's a baseline.
                Spacer(Modifier.height(if (anyAlert || status.nearLimit) Spacing.s else Spacing.l))
                val loaded = progress
                when {
                    loaded == null -> HeroSkeleton(
                        message = if (hasUsageAccess) "Getting your baseline…" else "Loading…",
                        reducedMotion = reducedMotion
                    )
                    loaded.hasBaseline -> SavedHero(
                        progress = loaded,
                        tracked = installedApps,
                        hasUsageAccess = hasUsageAccess,
                        largeFont = largeFont,
                        reducedMotion = reducedMotion,
                        onOpenDetails = { showSavedDetails = true },
                        onOpenTree = { showTreeSheet = true }
                    )
                    else -> Last24hHero(loaded, installedApps, hasUsageAccess, largeFont, reducedMotion, onOpenTree = { showTreeSheet = true })
                }

                // 3. The Limits pill, above the fold.
                if (!status.nearLimit) {
                    Spacer(Modifier.height(Spacing.s))
                    limitsRow()
                }

                // 4. One insight.
                if (loaded != null) {
                    Spacer(Modifier.height(Spacing.s))
                    InsightNote(loaded.insight, ::onInsightAction)
                }

                // 5. The last 24 hours, when it isn't already the hero.
                if (loaded != null && loaded.hasBaseline) {
                    Spacer(Modifier.height(Spacing.xxl))
                    Last24hSection(loaded, installedApps, largeFont)
                }

                // 6. The week and streak.
                Spacer(Modifier.height(Spacing.xxl))
                WeekSection(
                    dayHistory = weekHistory,
                    daysWithinLimit = daysWithinLimit,
                    daysExceededLimit = daysExceededLimit,
                    todayMs = nowMs,
                    largeFont = largeFont
                )

            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }

        if (showPauseSheet) {
            // Never "0 min": only a real figure for today, otherwise a calm fallback.
            val calmLine = when {
                progress == null -> "Take a breath."
                progress.hasBaseline -> savedHeadline(progress).let {
                    if (it.kind == HeadlineKind.TODAY) "You've saved ${formatSaved(it.ms)} today." else "Take a breath."
                }
                else -> "${formatUsage(progress.last24hMs).replaceFirstChar { it.uppercase() }} on your apps in the last 24 hours."
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
                },
                // Left out when the tree is already resting today for another reason.
                treeName = progress?.tree?.takeIf { it.todayRest == null }?.name,
                pausedTodayMs = remember(pauseLog, nowMs / 60_000L) {
                    val start = localDayStart(nowMs)
                    pausedMsOn(pauseLog, start, localDayStart(nowMs, daysAgo = -1), nowMs)
                }
            )
        }

        if (showSavedDetails) progress?.let { SavedDetailSheet(it, onDismiss = { showSavedDetails = false }) }

        if (showTreeSheet) {
            progress?.let {
                TreeSheet(
                    tree = it.tree,
                    onDismiss = { showTreeSheet = false },
                    onRename = viewModel::renameTree,
                    onSetBack = viewModel::setBackLimit
                )
            }
        }

        if (swapOnDemand) {
            SwapDialog(
                activity = swapActivity,
                deck = flashcardDeck,
                headline = "Your swap",
                subtitle = "",
                onClose = { swapOnDemand = false },
                onCompleted = viewModel::recordSwapCompleted
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
                },
                treeLine = progress?.tree?.let { raiseTreeLine(it.name, it.todayRest) }
            )
        }
    }
}
