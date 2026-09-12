package com.example.brainrotkiller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrotkiller.data.DEFAULT_DAILY_REEL_LIMIT
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: BrainRotKillerApp get() = getApplication()

    val onboardingComplete: StateFlow<Boolean?> = app.settingsRepository.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dailyReelLimit: StateFlow<Int> = app.settingsRepository.dailyReelLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_DAILY_REEL_LIMIT)

    val todayReelCount: StateFlow<Int> = app.reelUsageRepository.todayCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun completeOnboarding(limit: Int) {
        viewModelScope.launch { app.settingsRepository.completeOnboarding(limit) }
    }

    fun updateDailyLimit(limit: Int) {
        viewModelScope.launch { app.settingsRepository.setDailyReelLimit(limit) }
    }
}
