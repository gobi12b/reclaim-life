package io.github.gobi12b.reclaimlife

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gobi12b.reclaimlife.data.Baseline
import io.github.gobi12b.reclaimlife.data.DEFAULT_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.DEFAULT_DAY_START_HOUR
import io.github.gobi12b.reclaimlife.data.DEFAULT_GATE_WAIT_MS
import io.github.gobi12b.reclaimlife.data.DEFAULT_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.DEFAULT_TRACKED_APPS
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.GateDecision
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.PauseDuration
import io.github.gobi12b.reclaimlife.data.PauseEntry
import io.github.gobi12b.reclaimlife.data.PauseReason
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.service.syncProgressData
import io.github.gobi12b.reclaimlife.widget.refreshReelWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: ReclaimLifeApp get() = getApplication()
    private val settings get() = app.settingsRepository
    private val usage get() = app.reelUsageRepository

    init {
        // Close out yesterday into the within/exceeded tally before anything reads today's count,
        // so a day the app was never opened doesn't just get silently overwritten unrecorded.
        viewModelScope.launch {
            val limit = settings.dailyReelLimit.first()
            val mode = settings.limitMode.first()
            usage.closeOutPreviousDayIfNeeded(limit, mode)
        }
        viewModelScope.launch { settings.migrateLegacyPauseIfNeeded() }
        // Existing installs start recording daily usage (and can take a baseline) from the upgrade.
        viewModelScope.launch { if (settings.onboardingComplete.first()) syncProgressData(app) }
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val onboardingComplete: StateFlow<Boolean?> = settings.onboardingComplete.state(null)
    val showAppsCard: StateFlow<Boolean> = settings.showAppsCard.state(false)
    val dailyReelLimit: StateFlow<Int> = settings.dailyReelLimit.state(DEFAULT_DAILY_REEL_LIMIT)
    val hourlyReelLimit: StateFlow<Int> = settings.hourlyReelLimit.state(DEFAULT_HOURLY_REEL_LIMIT)
    val limitMode: StateFlow<LimitMode> = settings.limitMode.state(LimitMode.DAILY)
    val recentReelTimes: StateFlow<List<Long>> = usage.recentReelTimes.state(emptyList())
    val todayReelCount: StateFlow<Int> = usage.todayCount.state(0)
    val todayCountsByApp: StateFlow<Map<String, Int>> = usage.todayCountsByApp.state(emptyMap())
    val todayExtraAllowance: StateFlow<Int> = usage.todayExtraAllowance.state(0)
    val todayExtraAttempts: StateFlow<Int> = usage.todayExtraAttempts.state(0)
    val replacementActivity: StateFlow<ReplacementActivity> = settings.replacementActivity.state(ReplacementActivity.BREATHING)
    val flashcardDeck: StateFlow<FlashcardDeck> = settings.flashcardDeck.state(FlashcardDeck.CAPITALS)
    val nickname: StateFlow<String> = settings.nickname.state("")
    val daysWithinLimit: StateFlow<Int> = usage.daysWithinLimit.state(0)
    val daysExceededLimit: StateFlow<Int> = usage.daysExceededLimit.state(0)
    val trackedApps: StateFlow<List<TrackedApp>> = settings.trackedApps.state(DEFAULT_TRACKED_APPS)
    val gateEnabled: StateFlow<Boolean> = settings.gateEnabled.state(true)
    val gateDisabledApps: StateFlow<Set<String>> = settings.gateDisabledApps.state(emptySet())
    val gateWaitMs: StateFlow<Long> = settings.gateWaitMs.state(DEFAULT_GATE_WAIT_MS)
    val gateDecisions: StateFlow<List<GateDecision>> = usage.gateDecisions.state(emptyList())
    val dayStartHour: StateFlow<Int> = settings.dayStartHour.state(DEFAULT_DAY_START_HOUR)
    val baselines: StateFlow<Map<String, Baseline>> = settings.baselines.state(emptyMap())
    val baselineResetAtMs: StateFlow<Long> = settings.baselineResetAtMs.state(0L)

    /** Epoch millis tracking resumes at; in the past means not paused. The UI ticks against it. */
    val pausedUntilMs: StateFlow<Long> = settings.pausedUntilMs.state(0L)
    /** When the pause starts — in the future during Rest of today's one-minute delayed start. */
    val pausedFromMs: StateFlow<Long> = settings.pausedFromMs.state(0L)
    val pauseLog: StateFlow<List<PauseEntry>> = usage.pauseLog.state(emptyList())
    val pauseIntention: StateFlow<String?> = app.pauseIntentionStore.intention.state(null)
    val dayHistory: StateFlow<Map<String, DayOutcome>> = usage.dayHistory.state(emptyMap())

    fun completeOnboarding(
        mode: LimitMode,
        limit: Int,
        hourlyLimit: Int,
        nickname: String,
        activity: ReplacementActivity,
        deck: FlashcardDeck,
        apps: List<TrackedApp>,
        gateEnabled: Boolean
    ) {
        viewModelScope.launch {
            settings.completeOnboarding(mode, limit, hourlyLimit, nickname, activity, deck, apps, gateEnabled)
            // Takes the baseline now if Usage access was allowed during setup.
            syncProgressData(app)
        }
    }

    fun updateDailyLimit(limit: Int) {
        viewModelScope.launch {
            settings.setDailyReelLimit(limit)
            refreshReelWidget(app)
        }
    }

    fun updateHourlyLimit(limit: Int) {
        viewModelScope.launch { settings.setHourlyReelLimit(limit) }
    }

    fun updateLimitMode(mode: LimitMode) {
        viewModelScope.launch {
            settings.setLimitMode(mode)
            refreshReelWidget(app)
        }
    }

    fun updateTrackedApps(apps: List<TrackedApp>) {
        viewModelScope.launch {
            settings.setTrackedApps(apps)
            // An app added later gets its own baseline, by the same rules.
            syncProgressData(app)
        }
    }

    fun updateSwap(activity: ReplacementActivity, deck: FlashcardDeck) {
        viewModelScope.launch { settings.setSwap(activity, deck) }
    }

    fun setGateEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setGateEnabled(enabled) }
    }

    fun setGateForApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch { settings.setGateForApp(packageName, enabled) }
    }

    fun setGateWaitMs(waitMs: Long) {
        viewModelScope.launch { settings.setGateWaitMs(waitMs) }
    }

    /** Insight #2's button. [onDone] gets an Undo that puts both switches back as they were. */
    fun turnOnGateFor(packageName: String, onDone: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val (mainWasOn, appWasOn) = settings.turnOnGateFor(packageName)
            onDone {
                viewModelScope.launch {
                    settings.setGateEnabled(mainWasOn)
                    settings.setGateForApp(packageName, appWasOn)
                }
            }
        }
    }

    fun setDayStartHour(hour: Int) {
        viewModelScope.launch { settings.setDayStartHour(hour) }
    }

    fun resetBaselines(baselines: Map<String, Baseline>) {
        viewModelScope.launch { settings.resetBaselines(baselines, System.currentTimeMillis()) }
    }

    fun dismissAppsCard() {
        viewModelScope.launch { settings.dismissAppsCard() }
    }

    fun startPause(duration: PauseDuration, reason: PauseReason) {
        viewModelScope.launch { app.pauseController.start(duration, reason) }
    }

    fun scheduleRestOfToday(reason: PauseReason, intention: String) {
        viewModelScope.launch { app.pauseController.scheduleRestOfToday(reason, intention) }
    }

    fun cancelPendingPause() {
        viewModelScope.launch { app.pauseController.cancelPending() }
    }

    fun resumeTracking() {
        viewModelScope.launch { app.pauseController.resume() }
    }
}
