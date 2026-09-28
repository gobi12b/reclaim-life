package io.github.gobi12b.reclaimlife.data

/**
 * Today's reels per app. The daily limit is on the total ([totalReels]), so splitting the count
 * changes nothing about when the block screen shows.
 *
 * Reels counted before the split existed carry no app. On upgrade day they sit under
 * [EARLIER_TODAY_KEY], shown as "Earlier today", so the per-app rows still add up to the total;
 * the key goes away with the midnight reset.
 */
const val EARLIER_TODAY_KEY = "~earlier"

fun totalReels(counts: Map<String, Int>): Int = counts.values.sum()

/** `com.instagram.android=18;com.google.android.youtube=12`. */
internal fun parseReelCounts(raw: String?): Map<String, Int> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split('=')
        val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        if (parts.size != 2 || parts[0].isBlank() || count <= 0) null else parts[0] to count
    }.toMap()

internal fun serializeReelCounts(counts: Map<String, Int>): String =
    counts.filterValues { it > 0 }.entries.joinToString(";") { "${it.key}=${it.value}" }

/**
 * Today's counts from what's stored. [byAppRaw] null means this install has never written the
 * split, so a legacy combined [legacyTotal] becomes the "Earlier today" share.
 */
internal fun reelCountsFromStored(byAppRaw: String?, legacyTotal: Int?): Map<String, Int> = when {
    byAppRaw != null -> parseReelCounts(byAppRaw)
    (legacyTotal ?: 0) > 0 -> mapOf(EARLIER_TODAY_KEY to legacyTotal!!)
    else -> emptyMap()
}

/** Reels per app for the last few closed reel days: `2026-09-27=pkg:18,pkg:12;2026-09-28=…`. */
internal fun parseReelsByDay(raw: String?): Map<String, Map<String, Int>> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split('=')
        if (parts.size != 2 || parts[0].length != 10) return@mapNotNull null
        parts[0] to parts[1].split(',').mapNotNull { pair ->
            val kv = pair.split(':')
            val n = kv.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (kv.size != 2 || kv[0].isBlank()) null else kv[0] to n
        }.toMap()
    }.toMap()

/** Keeps the newest [keep] days; yyyy-MM-dd sorts as a string. */
internal fun serializeReelsByDay(byDay: Map<String, Map<String, Int>>, keep: Int = REELS_BY_DAY_KEEP): String =
    byDay.entries.sortedBy { it.key }.takeLast(keep).joinToString(";") { (date, counts) ->
        "$date=" + counts.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

/** Long enough for the daily-usage recorder to pick a day up after a week with the phone off. */
internal const val REELS_BY_DAY_KEEP = 10
