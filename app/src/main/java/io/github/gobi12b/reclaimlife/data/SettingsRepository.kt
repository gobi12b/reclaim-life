package io.github.gobi12b.reclaimlife.data

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

/** Whether it's enforced at all is [LimitMode]; this is just the number. */
const val DEFAULT_HOURLY_REEL_LIMIT = 30
const val MAX_HOURLY_REEL_LIMIT = 120

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DAILY_REEL_LIMIT = intPreferencesKey("daily_reel_limit")
        val NICKNAME = stringPreferencesKey("nickname")
        /** Legacy open-ended pause flag; only read to migrate it into [PAUSED_UNTIL_MS]. */
        val LEGACY_TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
        val PAUSED_UNTIL_MS = longPreferencesKey("paused_until_ms")
        val HOURLY_REEL_LIMIT = intPreferencesKey("hourly_reel_limit")
        val LIMIT_MODE = stringPreferencesKey("limit_mode")
        val REPLACEMENT_ACTIVITY = stringPreferencesKey("replacement_activity")
        val FLASHCARD_DECK = stringPreferencesKey("flashcard_deck")
    }

    val onboardingComplete: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val dailyReelLimit: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.DAILY_REEL_LIMIT] ?: DEFAULT_DAILY_REEL_LIMIT }

    /** Reels allowed in any rolling 60 minutes — only enforced when [limitMode] uses it. */
    val hourlyReelLimit: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.HOURLY_REEL_LIMIT] ?: DEFAULT_HOURLY_REEL_LIMIT }

    /** Daily-only unless the user picks otherwise, which keeps existing installs unchanged. */
    val limitMode: Flow<LimitMode> =
        context.settingsDataStore.data.map { LimitMode.fromStored(it[Keys.LIMIT_MODE]) }

    /** The 2-minute swap offered when a limit is reached. */
    val replacementActivity: Flow<ReplacementActivity> =
        context.settingsDataStore.data.map { ReplacementActivity.fromStored(it[Keys.REPLACEMENT_ACTIVITY]) }

    /** Only used when [replacementActivity] is flashcards, but kept so switching back remembers it. */
    val flashcardDeck: Flow<FlashcardDeck> =
        context.settingsDataStore.data.map { FlashcardDeck.fromStored(it[Keys.FLASHCARD_DECK]) }

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

    suspend fun setHourlyReelLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.HOURLY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT) }
    }

    suspend fun setLimitMode(mode: LimitMode) {
        context.settingsDataStore.edit { it[Keys.LIMIT_MODE] = mode.name }
    }

    suspend fun setSwap(activity: ReplacementActivity, deck: FlashcardDeck) {
        context.settingsDataStore.edit {
            it[Keys.REPLACEMENT_ACTIVITY] = activity.name
            it[Keys.FLASHCARD_DECK] = deck.name
        }
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

    suspend fun completeOnboarding(
        mode: LimitMode,
        limit: Int,
        hourlyLimit: Int,
        nickname: String,
        activity: ReplacementActivity,
        deck: FlashcardDeck
    ) {
        context.settingsDataStore.edit {
            it[Keys.REPLACEMENT_ACTIVITY] = activity.name
            it[Keys.FLASHCARD_DECK] = deck.name
            it[Keys.LIMIT_MODE] = mode.name
            it[Keys.DAILY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            it[Keys.HOURLY_REEL_LIMIT] = hourlyLimit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            it[Keys.NICKNAME] = nickname.trim()
            it[Keys.ONBOARDING_COMPLETE] = true
        }
    }
}
