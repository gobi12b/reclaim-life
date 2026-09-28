package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackedAppsTest {

    @Test
    fun defaultsToInstagramAndYouTube() {
        assertEquals(DEFAULT_TRACKED_APPS, parseTrackedApps(null))
    }

    @Test
    fun removingAnAppKeepsTheOthersColours() {
        val three = toggleTrackedApp(DEFAULT_TRACKED_APPS, "com.facebook.katana")
        assertEquals(TrackedApp("com.facebook.katana", 2), three.last())
        val withoutInstagram = toggleTrackedApp(three, TargetApps.INSTAGRAM)
        assertEquals(listOf(TrackedApp(TargetApps.YOUTUBE, 1), TrackedApp("com.facebook.katana", 2)), withoutInstagram)
        // The next app takes the freed slot 0 rather than repainting anyone.
        assertEquals(TrackedApp("com.reddit.frontpage", 0), toggleTrackedApp(withoutInstagram, "com.reddit.frontpage").first())
    }

    @Test
    fun cannotPassTheMaximum() {
        var apps = emptyList<TrackedApp>()
        repeat(MAX_TRACKED_APPS) { apps = toggleTrackedApp(apps, "app.$it") }
        assertEquals(MAX_TRACKED_APPS, apps.size)
        assertEquals(apps, toggleTrackedApp(apps, "one.too.many"))
    }

    @Test
    fun roundTripsAndDropsBadEntries() {
        val apps = listOf(TrackedApp("a.b", 0), TrackedApp("c.d", 3))
        assertEquals(apps, parseTrackedApps(serializeTrackedApps(apps)))
        assertEquals(listOf(TrackedApp("a.b", 0)), parseTrackedApps("a.b:0,bad,c.d:99,a.b:4"))
        assertEquals(emptyList<TrackedApp>(), parseTrackedApps(""))
    }
}
