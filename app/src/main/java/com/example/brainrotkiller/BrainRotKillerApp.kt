package com.example.brainrotkiller

import android.app.Application
import com.example.brainrotkiller.data.ReelUsageRepository
import com.example.brainrotkiller.data.SettingsRepository

class BrainRotKillerApp : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val reelUsageRepository: ReelUsageRepository by lazy { ReelUsageRepository(this) }
}
