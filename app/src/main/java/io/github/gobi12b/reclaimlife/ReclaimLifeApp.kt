package io.github.gobi12b.reclaimlife

import android.app.Application
import android.content.Context
import io.github.gobi12b.reclaimlife.data.DailyUsageRepository
import io.github.gobi12b.reclaimlife.data.PauseIntentionStore
import io.github.gobi12b.reclaimlife.data.ReelUsageRepository
import io.github.gobi12b.reclaimlife.data.SettingsRepository
import io.github.gobi12b.reclaimlife.service.PauseController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReclaimLifeApp : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val reelUsageRepository: ReelUsageRepository by lazy { ReelUsageRepository(this) }
    val dailyUsageRepository: DailyUsageRepository by lazy { DailyUsageRepository(this) }
    val pauseIntentionStore: PauseIntentionStore by lazy { PauseIntentionStore(this) }
    val pauseController: PauseController by lazy { PauseController(this) }
    /** For short writes that must outlive the screen that started them. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        deleteStalePauseRecording(this)
        appScope.launch { pauseController.clearExpired() }
    }
}

/**
 * Pausing used to need a voice recording, kept in cacheDir while its dialog was open. That flow is
 * gone; a take left behind by an older version is deleted on first launch.
 */
private fun deleteStalePauseRecording(context: Context) {
    runCatching { File(context.cacheDir, "pause_confirm.3gp").delete() }
}
