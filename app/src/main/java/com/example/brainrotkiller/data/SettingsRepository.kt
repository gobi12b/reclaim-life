package com.example.brainrotkiller.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** The lowest limit we'll let someone set. Below this the feature stops meaning anything. */
const val MIN_DAILY_REEL_LIMIT = 1
const val DEFAULT_DAILY_REEL_LIMIT = 30

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DAILY_REEL_LIMIT = intPreferencesKey("daily_reel_limit")
        val NICKNAME = stringPreferencesKey("nickname")
        val TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
    }

    val onboardingComplete: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val dailyReelLimit: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.DAILY_REEL_LIMIT] ?: DEFAULT_DAILY_REEL_LIMIT }

    /** Empty string means no nickname was given — callers fall back to generic phrasing. */
    val nickname: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.NICKNAME] ?: "" }

    /** A deliberate, guilt-gated escape hatch — counting/blocking is skipped entirely while paused. */
    val trackingPaused: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.TRACKING_PAUSED] ?: false }

    suspend fun setDailyReelLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT) }
    }

    suspend fun setTrackingPaused(paused: Boolean) {
        context.settingsDataStore.edit { it[Keys.TRACKING_PAUSED] = paused }
    }

    suspend fun completeOnboarding(limit: Int, nickname: String) {
        context.settingsDataStore.edit {
            it[Keys.DAILY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            it[Keys.NICKNAME] = nickname.trim()
            it[Keys.ONBOARDING_COMPLETE] = true
        }
    }
}
