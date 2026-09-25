package com.example.brainrotkiller.service

import com.example.brainrotkiller.data.TargetApps

/**
 * Where reels actually live inside each target app, by view resource ID.
 *
 * Instagram: the Reels viewer (Reels tab, and any reel opened from feed / DMs / profile /
 * explore) is ClipsViewerFragment, whose vertical pager is `clips_viewer_view_pager` — Instagram
 * calls Reels "clips" internally. Its "reel_viewer_*" IDs are *Stories*, not Reels, and the home
 * feed, profile grid and explore grid are other lists entirely, so none of those match.
 *
 * YouTube: Shorts is internally "reel" (`reel_recycler`, `reel_player_page_container`, …).
 *
 * Anything that doesn't match is treated as "not reels": when detection is unsure, normal
 * scrolling is not counted.
 */
internal object ReelSurfaces {
    const val INSTAGRAM_REELS_PAGER = "com.instagram.android:id/clips_viewer_view_pager"
    const val YOUTUBE_SHORTS_PAGER = "com.google.android.youtube:id/reel_recycler"

    /** The view whose on-screen presence means "the user is looking at reels right now". */
    fun viewerIdFor(packageName: String?): String? = when (packageName) {
        TargetApps.INSTAGRAM -> INSTAGRAM_REELS_PAGER
        TargetApps.YOUTUBE -> YOUTUBE_SHORTS_PAGER
        else -> null
    }

    /**
     * Whether a scroll came from the reels pager itself. [idChain] is the scroll source's view ID
     * followed by its nearest ancestors' — a pager's scroll events can come from an unnamed inner
     * RecyclerView, so the named pager may be a parent rather than the source. A scroll inside
     * the comments sheet or a profile grid never has the pager in its chain.
     */
    fun isReelScroll(packageName: String?, idChain: List<String?>): Boolean = when (packageName) {
        TargetApps.INSTAGRAM -> idChain.any { it == INSTAGRAM_REELS_PAGER }
        TargetApps.YOUTUBE -> idChain.any { it != null && it.startsWith("com.google.android.youtube:id/reel_") }
        else -> false
    }
}

/**
 * Turns a pager's scroll events into "reels moved past". One swipe fires several scroll events
 * (mid-swipe, two pages visible; settled, one page), so counting events over-counts. Instead we
 * count only when the pager *settles* (first == last visible item) on a different item than
 * before — one swipe, one reel. If events were dropped and it settles two items on, that's two.
 *
 * If the app doesn't report item positions (-1), fall back to one count per [debounceMs].
 *
 * Instagram (verified on a device, IG 448): scroll source is `clips_viewer_view_pager`, an
 * androidx ViewPager; one swipe fires 2–3 scroll events all with from == to == the new page.
 */
internal class ReelPageTracker(private val debounceMs: Long = 700L, private val maxStep: Int = 5) {
    private var settledIndex: Int? = null
    private var lastFallbackCountMs = 0L

    /** Returns how many reels to count for this scroll event (usually 0 or 1). */
    fun onScroll(fromIndex: Int, toIndex: Int, nowMs: Long): Int {
        if (fromIndex < 0 || toIndex < 0) {
            if (nowMs - lastFallbackCountMs < debounceMs) return 0
            lastFallbackCountMs = nowMs
            return 1
        }
        val baseline = settledIndex
        if (baseline == null) {
            settledIndex = fromIndex
            // Instagram's Reels pager is a classic ViewPager: every scroll event of a swipe
            // already reports the *new* page (from == to), so the first event we see after
            // entering the viewer is itself a completed swipe — unless it's page 0, where no
            // swipe can have landed. (Confirmed on device: first events of 1,1 / 5,5 / 8,8.)
            // A RecyclerView pager's first event is usually mid-swipe (from != to) instead,
            // which just sets the baseline and counts when it settles.
            return if (fromIndex == toIndex && fromIndex > 0) 1 else 0
        }
        if (fromIndex != toIndex || fromIndex == baseline) return 0
        settledIndex = fromIndex
        return kotlin.math.abs(fromIndex - baseline).coerceAtMost(maxStep)
    }

    /** Call when the user leaves the reels viewer, so a newly opened viewer starts fresh. */
    fun reset() {
        settledIndex = null
    }
}
