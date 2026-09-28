package io.github.gobi12b.reclaimlife.data

/**
 * An app the user chose to watch: it gets the open-pause screen and appears in the stats.
 * [slot] picks its chart colour and is kept for as long as the app stays chosen, so removing one
 * app never repaints the others.
 */
data class TrackedApp(val packageName: String, val slot: Int)

/** Most apps anyone can pick — one per colour in the validated chart palette. */
const val MAX_TRACKED_APPS = 8

/** Popular feed apps offered first in the picker (only the installed ones are shown). */
val SUGGESTED_APPS: List<Pair<String, String>> = listOf(
    TargetApps.INSTAGRAM to "Instagram",
    TargetApps.YOUTUBE to "YouTube",
    "com.facebook.katana" to "Facebook",
    "com.zhiliaoapp.musically" to "TikTok",
    "com.ss.android.ugc.trill" to "TikTok",
    "com.snapchat.android" to "Snapchat",
    "com.twitter.android" to "X",
    "com.reddit.frontpage" to "Reddit",
    "com.instagram.barcelona" to "Threads",
    "com.pinterest" to "Pinterest",
    "com.linkedin.android" to "LinkedIn"
)

val DEFAULT_TRACKED_APPS = listOf(TrackedApp(TargetApps.INSTAGRAM, 0), TrackedApp(TargetApps.YOUTUBE, 1))

/**
 * The chosen apps after adding or removing: survivors keep their slot, a newcomer takes the lowest
 * free one. Returns [current] unchanged when adding would pass [MAX_TRACKED_APPS].
 */
fun toggleTrackedApp(current: List<TrackedApp>, packageName: String): List<TrackedApp> {
    if (current.any { it.packageName == packageName }) return current.filterNot { it.packageName == packageName }
    if (current.size >= MAX_TRACKED_APPS) return current
    val used = current.map { it.slot }.toSet()
    val slot = (0 until MAX_TRACKED_APPS).first { it !in used }
    return (current + TrackedApp(packageName, slot)).sortedBy { it.slot }
}

internal fun parseTrackedApps(raw: String?): List<TrackedApp> {
    if (raw == null) return DEFAULT_TRACKED_APPS
    return raw.split(',').mapNotNull { entry ->
        val parts = entry.split(':')
        val slot = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        if (parts[0].isBlank() || slot !in 0 until MAX_TRACKED_APPS) null else TrackedApp(parts[0], slot)
    }.distinctBy { it.packageName }.sortedBy { it.slot }
}

internal fun serializeTrackedApps(apps: List<TrackedApp>): String =
    apps.joinToString(",") { "${it.packageName}:${it.slot}" }
