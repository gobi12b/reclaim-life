package com.example.brainrotkiller.service

import com.example.brainrotkiller.data.TargetApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelSurfacesTest {

    @Test
    fun instagramCountsOnlyTheClipsViewerPager() {
        val ig = TargetApps.INSTAGRAM
        assertTrue(ReelSurfaces.isReelScroll(ig, listOf("com.instagram.android:id/clips_viewer_view_pager")))
        // Unnamed inner RecyclerView whose parent is the pager.
        assertTrue(ReelSurfaces.isReelScroll(ig, listOf(null, "com.instagram.android:id/clips_viewer_view_pager")))
        // Home feed, profile grid, stories ("reel_viewer" is Stories in Instagram), no IDs at all.
        assertFalse(ReelSurfaces.isReelScroll(ig, listOf("android:id/list")))
        assertFalse(ReelSurfaces.isReelScroll(ig, listOf("com.instagram.android:id/clips_tab_grid_recyclerview")))
        assertFalse(ReelSurfaces.isReelScroll(ig, listOf("com.instagram.android:id/reel_viewer_content_layout")))
        assertFalse(ReelSurfaces.isReelScroll(ig, listOf(null, null)))
        assertFalse(ReelSurfaces.isReelScroll(ig, emptyList()))
    }

    @Test
    fun youtubeCountsOnlyShortsReelViews() {
        val yt = TargetApps.YOUTUBE
        assertTrue(ReelSurfaces.isReelScroll(yt, listOf("com.google.android.youtube:id/reel_recycler")))
        assertFalse(ReelSurfaces.isReelScroll(yt, listOf("com.google.android.youtube:id/results")))
        assertFalse(ReelSurfaces.isReelScroll(yt, listOf(null)))
    }

    @Test
    fun otherAppsNeverCount() {
        assertFalse(ReelSurfaces.isReelScroll("com.other", listOf("com.instagram.android:id/clips_viewer_view_pager")))
    }
}

class ReelPageTrackerTest {

    @Test
    fun oneSwipeWithSeveralScrollEventsCountsOnce() {
        val tracker = ReelPageTracker()
        assertEquals(0, tracker.onScroll(0, 0, 0))    // baseline
        assertEquals(0, tracker.onScroll(0, 1, 50))   // mid-swipe
        assertEquals(0, tracker.onScroll(0, 1, 150))  // still mid-swipe
        assertEquals(1, tracker.onScroll(1, 1, 300))  // settled on the next reel
        assertEquals(0, tracker.onScroll(1, 1, 400))  // repeated settled event
    }

    @Test
    fun firstEventMidSwipeStillCountsTheSwipe() {
        val tracker = ReelPageTracker()
        assertEquals(0, tracker.onScroll(3, 4, 0))
        assertEquals(1, tracker.onScroll(4, 4, 200))
    }

    @Test
    fun smallNudgeThatSnapsBackDoesNotCountOnceABaselineExists() {
        val tracker = ReelPageTracker()
        assertEquals(0, tracker.onScroll(0, 0, 0))
        assertEquals(1, tracker.onScroll(1, 1, 100)) // swiped to page 1
        assertEquals(0, tracker.onScroll(1, 2, 500)) // nudge toward 2…
        assertEquals(0, tracker.onScroll(1, 1, 700)) // …snaps back
    }

    @Test
    fun knownTradeOff_firstEventOnAPageAboveZeroCountsEvenIfItWasANudge() {
        // A classic ViewPager can't tell "just swiped here" from "nudged and snapped back" on
        // the first event we see. Counting it is the lesser error: not counting misses every
        // first swipe after re-entering the viewer, which happened repeatedly on device.
        val tracker = ReelPageTracker()
        assertEquals(1, tracker.onScroll(2, 2, 0))
    }

    @Test
    fun droppedEventsCatchUpButAreCapped() {
        val tracker = ReelPageTracker(maxStep = 5)
        tracker.onScroll(0, 0, 0)
        assertEquals(2, tracker.onScroll(2, 2, 500))
        assertEquals(5, tracker.onScroll(40, 40, 900))
    }

    @Test
    fun classicViewPagerFirstEventIsAlreadyTheNewPage() {
        // Recorded on device: Reels tab, swipes 0→1→2 fire (1,1)(1,1)(1,1) then (2,2)(2,2).
        val tracker = ReelPageTracker()
        assertEquals(1, tracker.onScroll(1, 1, 0))
        assertEquals(0, tracker.onScroll(1, 1, 90))
        assertEquals(0, tracker.onScroll(1, 1, 210))
        assertEquals(1, tracker.onScroll(2, 2, 1200))
        assertEquals(0, tracker.onScroll(2, 2, 1300))
    }

    @Test
    fun returningToAViewerMidwayCountsTheFirstSwipe() {
        // Recorded on device: left Reels at page 4, came back and swiped to 5: (5,5)(5,5)(6,6).
        val tracker = ReelPageTracker()
        tracker.onScroll(4, 4, 0)
        tracker.reset()
        assertEquals(1, tracker.onScroll(5, 5, 1000))
        assertEquals(0, tracker.onScroll(5, 5, 1100))
        assertEquals(1, tracker.onScroll(6, 6, 2000))
    }

    @Test
    fun firstEventOnPageZeroIsOnlyABaseline() {
        val tracker = ReelPageTracker()
        assertEquals(0, tracker.onScroll(0, 0, 0))
        assertEquals(1, tracker.onScroll(1, 1, 500))
    }

    @Test
    fun resetStartsANewViewerFresh() {
        val tracker = ReelPageTracker()
        tracker.onScroll(0, 0, 0)
        tracker.onScroll(7, 7, 100)
        tracker.reset()
        assertEquals(0, tracker.onScroll(0, 0, 200)) // new viewer at item 0: baseline only
        assertEquals(1, tracker.onScroll(1, 1, 700))
    }

    @Test
    fun withoutItemPositionsFallsBackToDebounce() {
        val tracker = ReelPageTracker(debounceMs = 700)
        assertEquals(1, tracker.onScroll(-1, -1, 1_000))
        assertEquals(0, tracker.onScroll(-1, -1, 1_300))
        assertEquals(1, tracker.onScroll(-1, -1, 1_800))
    }
}
