package io.github.gobi12b.reclaimlife

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gobi12b.reclaimlife.data.DEFAULT_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.DEFAULT_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.PauseDuration
import io.github.gobi12b.reclaimlife.widget.refreshReelWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: ReclaimLifeApp get() = getApplication()

    init {
        // Close out yesterday into the within/exceeded tally before anything reads today's count,
        // so a day the app was never opened doesn't just get silently overwritten unrecorded.
        viewModelScope.launch {
            val limit = app.settingsRepository.dailyReelLimit.first()
            val mode = app.settingsRepository.limitMode.first()
            app.reelUsageRepository.closeOutPreviousDayIfNeeded(limit, mode)
        }
        viewModelScope.launch { app.settingsRepository.migrateLegacyPauseIfNeeded() }
    }

    val onboardingComplete: StateFlow<Boolean?> = app.settingsRepository.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dailyReelLimit: StateFlow<Int> = app.settingsRepository.dailyReelLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_DAILY_REEL_LIMIT)

    val hourlyReelLimit: StateFlow<Int> = app.settingsRepository.hourlyReelLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_HOURLY_REEL_LIMIT)

    val limitMode: StateFlow<LimitMode> = app.settingsRepository.limitMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, LimitMode.DAILY)

    val recentReelTimes: StateFlow<List<Long>> = app.reelUsageRepository.recentReelTimes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayReelCount: StateFlow<Int> = app.reelUsageRepository.todayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val todayExtraAllowance: StateFlow<Int> = app.reelUsageRepository.todayExtraAllowance
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val replacementActivity: StateFlow<ReplacementActivity> = app.settingsRepository.replacementActivity
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReplacementActivity.BREATHING)

    val flashcardDeck: StateFlow<FlashcardDeck> = app.settingsRepository.flashcardDeck
        .stateIn(viewModelScope, SharingStarted.Eagerly, FlashcardDeck.CAPITALS)

    val nickname: StateFlow<String> = app.settingsRepository.nickname
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val daysWithinLimit: StateFlow<Int> = app.reelUsageRepository.daysWithinLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val daysExceededLimit: StateFlow<Int> = app.reelUsageRepository.daysExceededLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Epoch millis tracking resumes at; in the past means not paused. The UI ticks against it. */
    val pausedUntilMs: StateFlow<Long> = app.settingsRepository.pausedUntilMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val dayHistory: StateFlow<Map<String, DayOutcome>> = app.reelUsageRepository.dayHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun completeOnboarding(
        mode: LimitMode,
        limit: Int,
        hourlyLimit: Int,
        nickname: String,
        activity: ReplacementActivity,
        deck: FlashcardDeck
    ) {
        viewModelScope.launch {
            app.settingsRepository.completeOnboarding(mode, limit, hourlyLimit, nickname, activity, deck)
        }
    }

    fun updateDailyLimit(limit: Int) {
        viewModelScope.launch {
            app.settingsRepository.setDailyReelLimit(limit)
            refreshReelWidget(app)
        }
    }

    fun updateHourlyLimit(limit: Int) {
        viewModelScope.launch { app.settingsRepository.setHourlyReelLimit(limit) }
    }

    fun updateLimitMode(mode: LimitMode) {
        viewModelScope.launch {
            app.settingsRepository.setLimitMode(mode)
            refreshReelWidget(app)
        }
    }

    fun updateSwap(activity: ReplacementActivity, deck: FlashcardDeck) {
        viewModelScope.launch { app.settingsRepository.setSwap(activity, deck) }
    }

    fun pauseTracking(duration: PauseDuration) {
        viewModelScope.launch {
            app.settingsRepository.pauseUntil(duration.endsAt(System.currentTimeMillis()))
            refreshReelWidget(app)
        }
    }

    fun resumeTracking() {
        viewModelScope.launch {
            app.settingsRepository.resumeTracking()
            refreshReelWidget(app)
        }
    }
}
