package io.github.gobi12b.reclaimlife.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.gobi12b.reclaimlife.MainActivity
import io.github.gobi12b.reclaimlife.R
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.PauseDuration
import io.github.gobi12b.reclaimlife.data.PauseEntry
import io.github.gobi12b.reclaimlife.data.PauseReason
import io.github.gobi12b.reclaimlife.data.REST_OF_TODAY_DELAY_MS
import io.github.gobi12b.reclaimlife.data.nextLocalMidnight
import io.github.gobi12b.reclaimlife.widget.refreshReelWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starting, cancelling and ending the tracking pause, in one place so the Home sheet and the
 * notification's Cancel do exactly the same thing. Every pause is time-boxed and logged with its
 * reason; the typed intention of Rest of today is kept apart and deleted when the pause ends.
 */
class PauseController(private val app: ReclaimLifeApp) {

    suspend fun start(duration: PauseDuration, reason: PauseReason, nowMs: Long = System.currentTimeMillis()) {
        val until = duration.endsAt(nowMs)
        app.settingsRepository.startPause(nowMs, until, duration)
        app.reelUsageRepository.addPause(PauseEntry(nowMs, until, reason, duration))
        refreshReelWidget(app)
    }

    /**
     * Rest of today starts [REST_OF_TODAY_DELAY_MS] from now and can be cancelled until then —
     * from Home, or from a notification if notifications are already allowed.
     */
    suspend fun scheduleRestOfToday(reason: PauseReason, intention: String, nowMs: Long = System.currentTimeMillis()) {
        val from = nowMs + REST_OF_TODAY_DELAY_MS
        val until = nextLocalMidnight(from)
        app.pauseIntentionStore.set(intention)
        app.settingsRepository.startPause(from, until, PauseDuration.REST_OF_TODAY)
        app.reelUsageRepository.addPause(PauseEntry(from, until, reason, PauseDuration.REST_OF_TODAY))
        showDelayedStartNotification(app, from, intention)
        refreshReelWidget(app)
    }

    /** Cancels a Rest of today that hasn't started yet. It never happened, so the budget is refunded. */
    suspend fun cancelPending(nowMs: Long = System.currentTimeMillis()) {
        val from = app.settingsRepository.pausedFromMs.first()
        cancelDelayedStartNotification(app)
        if (from <= nowMs) return
        app.reelUsageRepository.removePauseStartingAt(from)
        app.settingsRepository.resumeTracking()
        app.pauseIntentionStore.clear()
        refreshReelWidget(app)
    }

    /** "Resume now": one tap, no confirm — tightening needs no friction. Rest of today isn't refunded. */
    suspend fun resume(nowMs: Long = System.currentTimeMillis()) {
        app.reelUsageRepository.endPausesAt(nowMs)
        app.settingsRepository.resumeTracking()
        app.pauseIntentionStore.clear()
        cancelDelayedStartNotification(app)
        refreshReelWidget(app)
    }

    /** Deletes the intention once its pause is over. Called at start-up and when a pause runs out. */
    suspend fun clearExpired(nowMs: Long = System.currentTimeMillis()) {
        val until = app.settingsRepository.pausedUntilMs.first()
        if (until <= nowMs) app.pauseIntentionStore.clear()
    }

    companion object {
        private const val CHANNEL_ID = "pause_start"
        private const val NOTIFICATION_ID = 42

        private fun canNotify(context: Context): Boolean {
            val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /**
         * Mirrors the Home countdown, only if notifications are already allowed — we never ask for
         * the permission just for this. Private visibility keeps the intention off the lock screen.
         */
        @SuppressLint("MissingPermission") // canNotify checks it
        private fun showDelayedStartNotification(context: Context, startsAtMs: Long, intention: String) {
            if (!canNotify(context)) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Pause starting", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "A heads-up before Rest of today starts."
                        setSound(null, null)
                    }
                )
            }
            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val cancel = PendingIntent.getBroadcast(
                context, 0, Intent(context, PauseCancelReceiver::class.java), immutable
            )
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), immutable)
            fun builder(text: String) = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Rest of today starts in a minute")
                .setContentText(text)
                .setWhen(startsAtMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(startsAtMs - System.currentTimeMillis())
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
            val notification = builder("Enjoy: $intention")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(builder("Tracking pauses soon").build())
                .addAction(0, "Cancel", cancel)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        }

        fun cancelDelayedStartNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}

/** The notification's Cancel during Rest of today's delayed start. */
class PauseCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ReclaimLifeApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.pauseController.cancelPending()
            } finally {
                pending.finish()
            }
        }
    }
}
