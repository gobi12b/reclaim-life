package io.github.gobi12b.reclaimlife.ui.common

import android.content.Context
import io.github.gobi12b.reclaimlife.data.EARLIER_TODAY_KEY
import io.github.gobi12b.reclaimlife.data.SUGGESTED_APPS

/** The name for a per-app reel row, including the "Earlier today" share from before the split. */
fun reelRowLabel(context: Context, key: String): String =
    if (key == EARLIER_TODAY_KEY) "Earlier today" else appLabel(context, key)

/**
 * Today's reels per app, biggest first, with "Earlier today" last — rows that add up to the total
 * everywhere it's shown.
 */
fun orderedReelCounts(counts: Map<String, Int>): List<Pair<String, Int>> =
    counts.filterValues { it > 0 }.entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.key == EARLIER_TODAY_KEY }.thenByDescending { it.value })
        .map { it.key to it.value }

/** "Instagram 18 · YouTube 12" — or null with no reels yet. */
fun perAppReelsLine(context: Context, counts: Map<String, Int>): String? =
    orderedReelCounts(counts).takeIf { it.isNotEmpty() }
        ?.joinToString(" · ") { (key, n) -> "${reelRowLabel(context, key)} $n" }

/** A suggested app as one row; TikTok's two package names share a row that tracks both. */
data class SuggestedRow(val label: String, val packages: List<String>)

/** The installed apps from [SUGGESTED_APPS], in that order. Work-profile and cloned apps aren't listed. */
fun installedSuggestedApps(context: Context): List<SuggestedRow> =
    SUGGESTED_APPS.filter { isInstalled(context, it.first) }
        .groupBy { it.second }
        .map { (label, entries) -> SuggestedRow(label, entries.map { it.first }.distinct()) }

/** Whether [packageName] is installed. An uninstalled tracked app is hidden from Home and Insights. */
fun isInstalled(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess
