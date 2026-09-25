package com.example.brainrotkiller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrotkiller.data.DEFAULT_DAILY_REEL_LIMIT
import com.example.brainrotkiller.data.DayOutcome
import com.example.brainrotkiller.data.PauseDuration
import com.example.brainrotkiller.widget.refreshReelWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: BrainRotKillerApp get() = getApplication()

    init {
        // Close out yesterday into the within/exceeded tally before anything reads today's count,
        // so a day the app was never opened doesn't just get silently overwritten unrecorded.
        viewModelScope.launch {
            val limit = app.settingsRepository.dailyReelLimit.first()
            app.reelUsageRepository.closeOutPreviousDayIfNeeded(limit)
        }
        viewModelScope.launch { app.settingsRepository.migrateLegacyPauseIfNeeded() }
    }

    val onboardingComplete: StateFlow<Boolean?> = app.settingsRepository.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dailyReelLimit: StateFlow<Int> = app.settingsRepository.dailyReelLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_DAILY_REEL_LIMIT)

    val todayReelCount: StateFlow<Int> = app.reelUsageRepository.todayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val todayExtraAllowance: StateFlow<Int> = app.reelUsageRepository.todayExtraAllowance
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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

    fun completeOnboarding(limit: Int, nickname: String) {
        viewModelScope.launch { app.settingsRepository.completeOnboarding(limit, nickname) }
    }

    fun updateDailyLimit(limit: Int) {
        viewModelScope.launch {
            app.settingsRepository.setDailyReelLimit(limit)
            refreshReelWidget(app)
        }
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
