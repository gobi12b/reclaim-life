package com.example.brainrotkiller.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** The lowest limit we'll let someone set. Below this the feature stops meaning anything. */
const val MIN_DAILY_REEL_LIMIT = 1
const val DEFAULT_DAILY_REEL_LIMIT = 30
/**
 * Highest limit the pickers offer. A limit someone already saved above this (from the old
 * 1–1000 slider) is left alone — the pickers just won't step *up* past the cap.
 */
const val MAX_DAILY_REEL_LIMIT = 300

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DAILY_REEL_LIMIT = intPreferencesKey("daily_reel_limit")
        val NICKNAME = stringPreferencesKey("nickname")
        /** Legacy open-ended pause flag; only read to migrate it into [PAUSED_UNTIL_MS]. */
        val LEGACY_TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
        val PAUSED_UNTIL_MS = longPreferencesKey("paused_until_ms")
    }

    val onboardingComplete: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val dailyReelLimit: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.DAILY_REEL_LIMIT] ?: DEFAULT_DAILY_REEL_LIMIT }

    /** Empty string means no nickname was given — callers fall back to generic phrasing. */
    val nickname: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.NICKNAME] ?: "" }

    /**
     * A deliberate, guilt-gated escape hatch — counting/blocking is skipped entirely until this
     * epoch-millis time. 0 (or anything in the past) means tracking is on. Being a timestamp
     * rather than a flag is what makes pauses auto-resume: nothing has to write "unpaused".
     */
    val pausedUntilMs: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.PAUSED_UNTIL_MS] ?: 0L }

    suspend fun setDailyReelLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT) }
    }

    suspend fun pauseUntil(untilMs: Long) {
        context.settingsDataStore.edit { it[Keys.PAUSED_UNTIL_MS] = untilMs }
    }

    suspend fun resumeTracking() {
        context.settingsDataStore.edit { it[Keys.PAUSED_UNTIL_MS] = 0L }
    }

    /**
     * Builds before time-boxed pauses stored an open-ended boolean. Anyone paused that way is
     * resumed immediately — an indefinite pause is exactly the forgotten state this replaced.
     * Pausing again goes through the normal record-and-play-back flow with a chosen duration.
     */
    suspend fun migrateLegacyPauseIfNeeded() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.LEGACY_TRACKING_PAUSED] == null) return@edit
            prefs.remove(Keys.LEGACY_TRACKING_PAUSED)
        }
    }

    suspend fun completeOnboarding(limit: Int, nickname: String) {
        context.settingsDataStore.edit {
            it[Keys.DAILY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            it[Keys.NICKNAME] = nickname.trim()
            it[Keys.ONBOARDING_COMPLETE] = true
        }
    }
}
