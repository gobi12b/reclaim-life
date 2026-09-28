package io.github.gobi12b.reclaimlife

import android.app.Application
import io.github.gobi12b.reclaimlife.data.ReelUsageRepository
import io.github.gobi12b.reclaimlife.data.SettingsRepository
import io.github.gobi12b.reclaimlife.ui.home.deleteStalePauseRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReclaimLifeApp : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val reelUsageRepository: ReelUsageRepository by lazy { ReelUsageRepository(this) }
    /** For short writes that must outlive the screen that started them. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // A fresh process can't have a pause dialog open, so any recording on disk is stale.
        deleteStalePauseRecording(this)
    }
}
