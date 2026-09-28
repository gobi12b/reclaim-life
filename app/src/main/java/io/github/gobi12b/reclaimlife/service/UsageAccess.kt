package io.github.gobi12b.reclaimlife.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.github.gobi12b.reclaimlife.data.AppStretch
import io.github.gobi12b.reclaimlife.data.ForegroundEvent
import io.github.gobi12b.reclaimlife.data.USAGE_WINDOW_MS
import io.github.gobi12b.reclaimlife.data.UsageSpan
import io.github.gobi12b.reclaimlife.data.foregroundStretches

/**
 * Android's own record of which app was in front, readable once "Usage access" is allowed. It
 * covers the whole 24 hours, including time before ReclaimLife was installed or while its service
 * wasn't running — so when it's allowed, the gate uses it instead of ReclaimLife's own timing.
 */
fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun usageAccessSettingsIntent(): Intent =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Milliseconds [packageName] was in front over the last 24 hours, or null without usage access. */
fun systemForegroundMs(context: Context, packageName: String, nowMs: Long): Long? =
    systemStretches(context, setOf(packageName), nowMs - USAGE_WINDOW_MS, nowMs)?.sumOf { it.endMs - it.startMs }

/**
 * When each of [packages] was in front between [sinceMs] and [nowMs], from Android's usage log.
 * Null without usage access.
 */
fun systemStretches(context: Context, packages: Set<String>, sinceMs: Long, nowMs: Long): List<AppStretch>? {
    if (!hasUsageAccess(context)) return null
    val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
    // Read a little before the window so a session already running at its start is found.
    val usageEvents = runCatching { manager.queryEvents(sinceMs - LOOKBACK_MS, nowMs) }.getOrNull() ?: return null
    val events = packages.associateWith { mutableListOf<ForegroundEvent>() }
    val event = UsageEvents.Event()
    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        val list = events[event.packageName] ?: continue
        @Suppress("DEPRECATION") // MOVE_TO_* share their values with ACTIVITY_RESUMED/PAUSED.
        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> list += ForegroundEvent(event.timeStamp, resumed = true)
            UsageEvents.Event.MOVE_TO_BACKGROUND -> list += ForegroundEvent(event.timeStamp, resumed = false)
        }
    }
    return events.flatMap { (pkg, list) ->
        foregroundStretches(list, sinceMs, nowMs).map { AppStretch(pkg, it.first, it.last) }
    }
}

/**
 * Time in [packages] since [sinceMs]: Android's record when Usage access is allowed, otherwise
 * ReclaimLife's own [spans]. The Boolean says which it was.
 */
fun appStretches(
    context: Context,
    packages: Set<String>,
    spans: List<UsageSpan>,
    sinceMs: Long,
    nowMs: Long
): Pair<List<AppStretch>, Boolean> {
    systemStretches(context, packages, sinceMs, nowMs)?.let { return it to true }
    return spans.filter { it.packageName in packages }.map { AppStretch(it.packageName, it.startMs, it.endMs) } to false
}

private const val LOOKBACK_MS = 3 * 60 * 60_000L
