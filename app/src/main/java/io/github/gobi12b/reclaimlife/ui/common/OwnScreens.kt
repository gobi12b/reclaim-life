package io.github.gobi12b.reclaimlife.ui.common

/**
 * What the accessibility service can't see for itself. Its config limits it to Instagram and
 * YouTube, so while our Gate/Block screens are in front it reads no window at all — which on its
 * own would look like leaving the app. These screens run in the service's process and report here.
 */
object OwnScreens {
    @Volatile private var visibleCount = 0

    /** A Gate or Block screen of ours is on screen. */
    val anyVisible: Boolean get() = visibleCount > 0

    fun onScreenStarted() { visibleCount++ }
    fun onScreenStopped() { visibleCount = (visibleCount - 1).coerceAtLeast(0) }

    /** The app the gate's choice was last made for, until the service picks it up. */
    @Volatile private var gateDecisionFor: String? = null

    fun recordGateDecision(packageName: String?) { gateDecisionFor = packageName }

    /** Whether a choice was made on the gate for [packageName]; clears it once read. */
    fun takeGateDecision(packageName: String): Boolean {
        if (gateDecisionFor != packageName) return false
        gateDecisionFor = null
        return true
    }
}
