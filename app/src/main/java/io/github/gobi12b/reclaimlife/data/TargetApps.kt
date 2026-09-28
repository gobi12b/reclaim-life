package io.github.gobi12b.reclaimlife.data

/**
 * The apps whose reels ReclaimLife can count (it reads their Reels / Shorts screens). Which apps
 * get the open-pause and stats is up to the user — see [TrackedApp].
 */
object TargetApps {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    val PACKAGES: Set<String> = setOf(INSTAGRAM, YOUTUBE)

    /** Whether reels are counted (and reel limits enforced) in this app. */
    fun countsReels(packageName: String?): Boolean = packageName in PACKAGES

    fun labelFor(packageName: String?): String = when (packageName) {
        INSTAGRAM -> "Instagram"
        YOUTUBE -> "YouTube"
        else -> SUGGESTED_APPS.firstOrNull { it.first == packageName }?.second ?: "this app"
    }
}
