package io.github.gobi12b.reclaimlife.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/** Pause before opening waits this long before Skip / Continue appear, unless changed. */
const val DEFAULT_GATE_WAIT_MS = 2_000L
val GATE_WAIT_OPTIONS_MS = listOf(2_000L, 5_000L, 10_000L)

/** Onboarding with the "Your apps" step. Installs finished before it get a Home card instead. */
const val CURRENT_ONBOARDING_VERSION = 2

/** Trimmed and capped at [TREE_NAME_MAX]; blank clears the name. */
fun cleanTreeName(name: String): String = name.trim().take(TREE_NAME_MAX).trim()

/** Today's insight, frozen for the day so it doesn't flicker as minutes tick. */
data class CachedInsight(val dateKey: String, val insight: HomeInsight)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ONBOARDING_VERSION = intPreferencesKey("onboarding_version")
        val APPS_CARD_DISMISSED = booleanPreferencesKey("onboarding_apps_card_dismissed")
        val DAILY_REEL_LIMIT = intPreferencesKey("daily_reel_limit")
        val NICKNAME = stringPreferencesKey("nickname")
        /** Legacy open-ended pause flag; only read to migrate it into [PAUSED_UNTIL_MS]. */
        val LEGACY_TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
        val PAUSED_FROM_MS = longPreferencesKey("paused_from_ms")
        val PAUSED_UNTIL_MS = longPreferencesKey("paused_until_ms")
        val PAUSE_KIND = stringPreferencesKey("pause_kind")
        val HOURLY_REEL_LIMIT = intPreferencesKey("hourly_reel_limit")
        val LIMIT_MODE = stringPreferencesKey("limit_mode")
        val REPLACEMENT_ACTIVITY = stringPreferencesKey("replacement_activity")
        val FLASHCARD_DECK = stringPreferencesKey("flashcard_deck")
        val TRACKED_APPS = stringPreferencesKey("tracked_apps")
        val GATE_ENABLED = booleanPreferencesKey("gate_enabled")
        val GATE_DISABLED_APPS = stringSetPreferencesKey("gate_disabled_apps")
        val GATE_WAIT_MS = longPreferencesKey("gate_wait_ms")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val BASELINES = stringPreferencesKey("baseline_by_app")
        val BASELINE_RESET_AT = longPreferencesKey("baseline_reset_at")
        val INSIGHT_DATE = stringPreferencesKey("insight_date")
        val INSIGHT_KIND = stringPreferencesKey("insight_kind")
        val INSIGHT_TEXT = stringPreferencesKey("insight_text")
        val INSIGHT_ACTION = stringPreferencesKey("insight_action")
        val INSIGHT_ACTION_LABEL = stringPreferencesKey("insight_action_label")
        val INSIGHT_ACTION_APP = stringPreferencesKey("insight_action_app")
        /** Every change to a limit or the mode, for two weeks — see [LimitChange]. Values only. */
        val LIMIT_CHANGE_LOG = stringPreferencesKey("limit_change_log")
        val TREE_NAME = stringPreferencesKey("tree_name")
        val TREE_INTRO_SEEN = booleanPreferencesKey("tree_intro_seen")
        val TREE_PLANTED_AT_MS = longPreferencesKey("tree_planted_at_ms")
        val TREE_BEST_STREAK = intPreferencesKey("tree_best_streak")
    }

    val onboardingComplete: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    /**
     * The one-time "Check which apps ReclaimLife helps with" card: for installs that finished the
     * older onboarding (no apps step), until dismissed — dismissing is permanent.
     */
    val showAppsCard: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.ONBOARDING_COMPLETE] == true &&
            (it[Keys.ONBOARDING_VERSION] ?: 1) < CURRENT_ONBOARDING_VERSION &&
            it[Keys.APPS_CARD_DISMISSED] != true
    }

    suspend fun dismissAppsCard() {
        context.settingsDataStore.edit { it[Keys.APPS_CARD_DISMISSED] = true }
    }

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

    /** Apps that can get Pause before opening and appear in the stats. Instagram and YouTube until changed. */
    val trackedApps: Flow<List<TrackedApp>> =
        context.settingsDataStore.data.map { parseTrackedApps(it[Keys.TRACKED_APPS]) }

    /** An app removed from tracking loses its Pause before opening entry; its history stays. */
    suspend fun setTrackedApps(apps: List<TrackedApp>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TRACKED_APPS] = serializeTrackedApps(apps)
            val kept = apps.map { it.packageName }.toSet()
            prefs[Keys.GATE_DISABLED_APPS] = prefs[Keys.GATE_DISABLED_APPS].orEmpty().intersect(kept)
        }
    }

    /** Pause before opening, the main switch. On unless turned off; existing installs keep it on. */
    val gateEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.GATE_ENABLED] ?: true }

    /** Tracked apps whose own Pause before opening switch is off. */
    val gateDisabledApps: Flow<Set<String>> =
        context.settingsDataStore.data.map { it[Keys.GATE_DISABLED_APPS].orEmpty() }

    val gateWaitMs: Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.GATE_WAIT_MS]?.takeIf { it in GATE_WAIT_OPTIONS_MS } ?: DEFAULT_GATE_WAIT_MS }

    suspend fun setGateEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GATE_ENABLED] = enabled }
    }

    suspend fun setGateForApp(packageName: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val disabled = prefs[Keys.GATE_DISABLED_APPS].orEmpty()
            prefs[Keys.GATE_DISABLED_APPS] = if (enabled) disabled - packageName else disabled + packageName
        }
    }

    /** Insight #2's offer: the main switch on if needed, plus that app's. Returns what to restore on Undo. */
    suspend fun turnOnGateFor(packageName: String): Pair<Boolean, Boolean> {
        var before = true to true
        context.settingsDataStore.edit { prefs ->
            val disabled = prefs[Keys.GATE_DISABLED_APPS].orEmpty()
            before = (prefs[Keys.GATE_ENABLED] ?: true) to (packageName !in disabled)
            prefs[Keys.GATE_ENABLED] = true
            prefs[Keys.GATE_DISABLED_APPS] = disabled - packageName
        }
        return before
    }

    suspend fun setGateWaitMs(waitMs: Long) {
        context.settingsDataStore.edit { it[Keys.GATE_WAIT_MS] = waitMs }
    }

    /** "My day starts at": the hour the ReclaimLife day turns over (see [reclaimDayStart]). */
    val dayStartHour: Flow<Int> =
        context.settingsDataStore.data.map { (it[Keys.DAY_START_HOUR] ?: DEFAULT_DAY_START_HOUR).coerceIn(0, 23) }

    suspend fun setDayStartHour(hour: Int) {
        context.settingsDataStore.edit { it[Keys.DAY_START_HOUR] = hour.coerceIn(0, 23) }
    }

    /** Per-app baselines for time saved — see [Baseline]. */
    val baselines: Flow<Map<String, Baseline>> =
        context.settingsDataStore.data.map { parseBaselines(it[Keys.BASELINES]) }

    suspend fun setBaselines(baselines: Map<String, Baseline>) {
        context.settingsDataStore.edit { it[Keys.BASELINES] = serializeBaselines(baselines) }
    }

    /** Adds baselines for apps that have none yet; an app that already has one keeps it. */
    suspend fun addBaselines(added: Map<String, Baseline>) {
        if (added.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val current = parseBaselines(prefs[Keys.BASELINES])
            prefs[Keys.BASELINES] = serializeBaselines(added + current)
        }
    }

    val baselineResetAtMs: Flow<Long> = context.settingsDataStore.data.map { it[Keys.BASELINE_RESET_AT] ?: 0L }

    suspend fun resetBaselines(baselines: Map<String, Baseline>, nowMs: Long) {
        context.settingsDataStore.edit {
            it[Keys.BASELINES] = serializeBaselines(baselines)
            it[Keys.BASELINE_RESET_AT] = nowMs
        }
    }

    val cachedInsight: Flow<CachedInsight?> = context.settingsDataStore.data.map { prefs ->
        val date = prefs[Keys.INSIGHT_DATE] ?: return@map null
        val kind = InsightKind.entries.firstOrNull { it.name == prefs[Keys.INSIGHT_KIND] } ?: return@map null
        val action = InsightAction.entries.firstOrNull { it.name == prefs[Keys.INSIGHT_ACTION] } ?: InsightAction.NONE
        CachedInsight(
            date,
            HomeInsight(kind, prefs[Keys.INSIGHT_TEXT].orEmpty(), action, prefs[Keys.INSIGHT_ACTION_LABEL], prefs[Keys.INSIGHT_ACTION_APP])
        )
    }

    suspend fun cacheInsight(dateKey: String, insight: HomeInsight) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.INSIGHT_DATE] = dateKey
            prefs[Keys.INSIGHT_KIND] = insight.kind.name
            prefs[Keys.INSIGHT_TEXT] = insight.text
            prefs[Keys.INSIGHT_ACTION] = insight.action.name
            insight.actionLabel?.let { prefs[Keys.INSIGHT_ACTION_LABEL] = it } ?: prefs.remove(Keys.INSIGHT_ACTION_LABEL)
            insight.actionApp?.let { prefs[Keys.INSIGHT_ACTION_APP] = it } ?: prefs.remove(Keys.INSIGHT_ACTION_APP)
        }
    }

    /** Empty string means no nickname was given — callers fall back to generic phrasing. */
    val nickname: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.NICKNAME] ?: "" }

    /**
     * Counting and blocking are skipped from [pausedFromMs] until this epoch-millis time. 0 (or
     * anything in the past) means tracking is on. Being a timestamp rather than a flag is what
     * makes pauses auto-resume: nothing has to write "unpaused".
     */
    val pausedUntilMs: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.PAUSED_UNTIL_MS] ?: 0L }

    /**
     * When the pause starts. Usually when it was confirmed; Rest of today starts
     * [REST_OF_TODAY_DELAY_MS] later, and can be cancelled until then.
     */
    val pausedFromMs: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.PAUSED_FROM_MS] ?: 0L }

    /** Which length the current pause is — Pause before opening still shows except during Rest of today. */
    val pauseKind: Flow<PauseDuration?> = context.settingsDataStore.data.map { prefs ->
        PauseDuration.entries.firstOrNull { it.name == prefs[Keys.PAUSE_KIND] }
    }

    suspend fun setDailyReelLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            val to = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            val from = prefs[Keys.DAILY_REEL_LIMIT] ?: DEFAULT_DAILY_REEL_LIMIT
            prefs[Keys.DAILY_REEL_LIMIT] = to
            if (to != from) logLimitChange(prefs, LimitKind.DAILY, from.toString(), to.toString())
        }
    }

    suspend fun setHourlyReelLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            val to = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            val from = prefs[Keys.HOURLY_REEL_LIMIT] ?: DEFAULT_HOURLY_REEL_LIMIT
            prefs[Keys.HOURLY_REEL_LIMIT] = to
            if (to != from) logLimitChange(prefs, LimitKind.HOURLY, from.toString(), to.toString())
        }
    }

    suspend fun setLimitMode(mode: LimitMode) {
        context.settingsDataStore.edit { prefs ->
            val from = LimitMode.fromStored(prefs[Keys.LIMIT_MODE])
            prefs[Keys.LIMIT_MODE] = mode.name
            if (mode != from) logLimitChange(prefs, LimitKind.MODE, from.name, mode.name)
        }
    }

    /** Written in the same edit as the value, so the log and the setting never disagree. */
    private fun logLimitChange(prefs: MutablePreferences, kind: LimitKind, from: String, to: String) {
        val now = System.currentTimeMillis()
        val log = parseLimitChanges(prefs[Keys.LIMIT_CHANGE_LOG]) + LimitChange(now, kind, from, to)
        prefs[Keys.LIMIT_CHANGE_LOG] = serializeLimitChanges(log, now)
    }

    /** Limit changes over the last two weeks, oldest first. A raise not put back rests the tree for the day. */
    val limitChanges: Flow<List<LimitChange>> =
        context.settingsDataStore.data.map { parseLimitChanges(it[Keys.LIMIT_CHANGE_LOG]) }

    /** The tree's name, trimmed; empty means none ("Your tree"). */
    val treeName: Flow<String> = context.settingsDataStore.data.map { it[Keys.TREE_NAME].orEmpty() }

    val treeIntroSeen: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.TREE_INTRO_SEEN] ?: false }

    val treePlantedAtMs: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.TREE_PLANTED_AT_MS] }

    /** Meet your tree is done: the name (optional), seen, and the planting day (kept if already set). */
    suspend fun completeTreeIntro(name: String, nowMs: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TREE_NAME] = cleanTreeName(name)
            prefs[Keys.TREE_INTRO_SEEN] = true
            if (prefs[Keys.TREE_PLANTED_AT_MS] == null) prefs[Keys.TREE_PLANTED_AT_MS] = nowMs
        }
    }

    /** The longest streak ever reached; keepsakes come from it, so it only goes up. */
    val treeBestStreak: Flow<Int> = context.settingsDataStore.data.map { it[Keys.TREE_BEST_STREAK] ?: 0 }

    suspend fun raiseTreeBestStreak(streak: Int) {
        context.settingsDataStore.edit { if (streak > (it[Keys.TREE_BEST_STREAK] ?: 0)) it[Keys.TREE_BEST_STREAK] = streak }
    }

    suspend fun setTreeName(name: String) {
        context.settingsDataStore.edit { it[Keys.TREE_NAME] = cleanTreeName(name) }
    }

    /** Upgrades date the tree from their first ledger day. Only sets it once. */
    suspend fun setTreePlantedAtIfUnset(atMs: Long) {
        context.settingsDataStore.edit { if (it[Keys.TREE_PLANTED_AT_MS] == null) it[Keys.TREE_PLANTED_AT_MS] = atMs }
    }

    suspend fun setSwap(activity: ReplacementActivity, deck: FlashcardDeck) {
        context.settingsDataStore.edit {
            it[Keys.REPLACEMENT_ACTIVITY] = activity.name
            it[Keys.FLASHCARD_DECK] = deck.name
        }
    }

    suspend fun startPause(fromMs: Long, untilMs: Long, kind: PauseDuration) {
        context.settingsDataStore.edit {
            it[Keys.PAUSED_FROM_MS] = fromMs
            it[Keys.PAUSED_UNTIL_MS] = untilMs
            it[Keys.PAUSE_KIND] = kind.name
        }
    }

    suspend fun resumeTracking() {
        context.settingsDataStore.edit {
            it[Keys.PAUSED_FROM_MS] = 0L
            it[Keys.PAUSED_UNTIL_MS] = 0L
            it.remove(Keys.PAUSE_KIND)
        }
    }

    /**
     * Builds before time-boxed pauses stored an open-ended boolean. Anyone paused that way is
     * resumed immediately — an indefinite pause is exactly the forgotten state this replaced.
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
        deck: FlashcardDeck,
        apps: List<TrackedApp>,
        gateEnabled: Boolean
    ) {
        context.settingsDataStore.edit {
            it[Keys.REPLACEMENT_ACTIVITY] = activity.name
            it[Keys.FLASHCARD_DECK] = deck.name
            it[Keys.LIMIT_MODE] = mode.name
            it[Keys.DAILY_REEL_LIMIT] = limit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            it[Keys.HOURLY_REEL_LIMIT] = hourlyLimit.coerceAtLeast(MIN_DAILY_REEL_LIMIT)
            it[Keys.NICKNAME] = nickname.trim()
            it[Keys.TRACKED_APPS] = serializeTrackedApps(apps)
            it[Keys.GATE_ENABLED] = gateEnabled
            it[Keys.ONBOARDING_VERSION] = CURRENT_ONBOARDING_VERSION
            it[Keys.ONBOARDING_COMPLETE] = true
        }
    }
}
