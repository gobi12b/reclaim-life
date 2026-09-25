package com.example.brainrotkiller.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.example.brainrotkiller.BrainRotKillerApp
import com.example.brainrotkiller.data.Mood
import com.example.brainrotkiller.data.TargetApps
import com.example.brainrotkiller.data.formatPauseRemaining
import com.example.brainrotkiller.ui.block.BlockActivity
import com.example.brainrotkiller.widget.refreshReelWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches Instagram Reels / YouTube Shorts and counts each reel swiped past. Only the reels
 * viewer counts ([ReelSurfaces]); a swipe is one reel when the pager settles on a new item
 * ([ReelPageTracker]). While that viewer is on screen, a small live counter badge is drawn over
 * it via the accessibility-overlay window type, which needs no extra "draw over other apps"
 * permission. View IDs are only reported because the config sets flagReportViewIds.
 */
class ReelBlockerAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var dailyLimit: Int = Int.MAX_VALUE
    private var todayCount: Int = 0
    private var extraAllowance: Int = 0
    private var pausedUntilMs: Long = 0L
    /** Read at every event rather than cached, so a pause ends on time without anything writing to storage. */
    private val trackingPaused: Boolean get() = System.currentTimeMillis() < pausedUntilMs
    private val effectiveLimit: Int get() = dailyLimit + extraAllowance

    private var lastBlockShownAtMs: Long = 0L
    private val pageTracker = ReelPageTracker(debounceMs = SCROLL_DEBOUNCE_MS)

    // Reels-viewer presence is looked up in the window's node tree, which is too costly to do on
    // every content-changed event; the answer is reused briefly and dropped on window changes.
    private var surfaceCheckedAtMs: Long = 0L
    private var surfaceCheckedPackage: String? = null
    private var onReelSurfaceCached: Boolean = false

    private var overlayView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val leaveSessionRunnable = Runnable {
        overlayView?.visibility = View.GONE
        pageTracker.reset()
    }
    /** Fires when a pause runs out, so the badge/widget flip back to counting without waiting for an event. */
    private val pauseEndedRunnable = Runnable {
        refreshOverlayText()
        serviceScope.launch { refreshReelWidget(this@ReelBlockerAccessibilityService) }
    }
    /** Keeps the badge's pause countdown moving while it's on screen. */
    private val pauseTickRunnable = object : Runnable {
        override fun run() {
            refreshOverlayText()
            if (trackingPaused && overlayView?.visibility == View.VISIBLE) {
                mainHandler.postDelayed(this, PAUSE_TICK_MS)
            }
        }
    }

    private val windowManager: WindowManager by lazy { getSystemService(WindowManager::class.java) }
    private val app: BrainRotKillerApp get() = application as BrainRotKillerApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            val limit = app.settingsRepository.dailyReelLimit.first()
            app.reelUsageRepository.closeOutPreviousDayIfNeeded(limit)
        }
        serviceScope.launch { app.settingsRepository.migrateLegacyPauseIfNeeded() }
        serviceScope.launch {
            app.settingsRepository.dailyReelLimit.collectLatest {
                dailyLimit = it
                refreshOverlayText()
                refreshReelWidget(this@ReelBlockerAccessibilityService)
            }
        }
        serviceScope.launch {
            app.reelUsageRepository.todayCount.collectLatest {
                todayCount = it
                refreshOverlayText()
                refreshReelWidget(this@ReelBlockerAccessibilityService)
            }
        }
        serviceScope.launch {
            app.reelUsageRepository.todayExtraAllowance.collectLatest {
                extraAllowance = it
                refreshOverlayText()
                refreshReelWidget(this@ReelBlockerAccessibilityService)
            }
        }
        serviceScope.launch {
            app.settingsRepository.pausedUntilMs.collectLatest {
                pausedUntilMs = it
                mainHandler.removeCallbacks(pauseEndedRunnable)
                val remaining = it - System.currentTimeMillis()
                if (remaining > 0) mainHandler.postDelayed(pauseEndedRunnable, remaining)
                refreshOverlayText()
                startPauseTickIfNeeded()
                refreshReelWidget(this@ReelBlockerAccessibilityService)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()

        // Our own Gate/Block screens briefly taking focus over Instagram/YouTube isn't a real
        // app switch — ignore them entirely so they can't influence session tracking.
        if (packageName == this.packageName) return

        if (!TargetApps.isTarget(packageName)) {
            // Don't immediately treat this as "left the target app": a transient non-target
            // event (ad surface, share-sheet helper, system UI blip) shouldn't end the session.
            // Only actually leaves after a grace period with no target-app event arriving.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                scheduleLeaveSession()
            }
            return
        }

        // The badge and the counting follow the same signal: only while the Reels / Shorts viewer
        // is on screen. Feed, profiles, explore, stories and DMs neither show it nor count.
        val onReels = isOnReelSurface(packageName, event)
        if (onReels) showCounterOverlay() else scheduleLeaveSession()

        if (trackingPaused) return

        // Blocking is deliberately app-wide, not reels-only: once over the limit, the stop screen
        // takes over Instagram/YouTube wherever you are in it.
        if (todayCount >= effectiveLimit) {
            showBlockScreenIfNeeded()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && onReels) {
            val isReelScroll = ReelSurfaces.isReelScroll(packageName, idChainOf(event.source))
            val reels = if (isReelScroll) {
                pageTracker.onScroll(event.fromIndex, event.toIndex, System.currentTimeMillis())
            } else {
                0
            }
            if (reels > 0) registerReels(reels)
        }
    }

    /**
     * Whether the Reels (Instagram) / Shorts (YouTube) viewer is visible in the active window —
     * see [ReelSurfaces]. Fails closed: no window, no match or an error all mean "not reels".
     */
    private fun isOnReelSurface(packageName: String?, event: AccessibilityEvent): Boolean {
        val viewerId = ReelSurfaces.viewerIdFor(packageName) ?: return false
        val now = System.currentTimeMillis()
        val fresh = event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName == surfaceCheckedPackage &&
            now - surfaceCheckedAtMs < SURFACE_CHECK_TTL_MS
        if (fresh) return onReelSurfaceCached

        val found = runCatching {
            rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewerId)?.any { it.isVisibleToUser } == true
        }.getOrDefault(false)
        surfaceCheckedAtMs = now
        surfaceCheckedPackage = packageName
        onReelSurfaceCached = found
        return found
    }

    /** The node's view ID plus up to [ID_CHAIN_DEPTH] ancestors' (needs flagReportViewIds). */
    private fun idChainOf(node: android.view.accessibility.AccessibilityNodeInfo?): List<String?> {
        val ids = mutableListOf<String?>()
        var current = node
        var depth = 0
        while (current != null && depth <= ID_CHAIN_DEPTH) {
            ids += current.viewIdResourceName
            current = current.parent
            depth++
        }
        return ids
    }

    private fun registerReels(reels: Int) {
        serviceScope.launch {
            val newCount = app.reelUsageRepository.incrementAndGet(reels)
            todayCount = newCount
            refreshOverlayText()
            if (newCount >= effectiveLimit) {
                showBlockScreenIfNeeded()
            }
        }
    }

    private fun showBlockScreenIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastBlockShownAtMs < BLOCK_REDISPLAY_COOLDOWN_MS) return
        lastBlockShownAtMs = now

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockActivity.EXTRA_DAILY_LIMIT, dailyLimit)
        }
        startActivity(intent)
    }

    private fun ensureOverlayView(): TextView {
        overlayView?.let { return it }

        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28 * density
                setColor(Color.argb(210, 20, 20, 28))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (48 * density).toInt()
        }
        windowManager.addView(view, params)
        overlayView = view
        return view
    }

    private fun showCounterOverlay() {
        mainHandler.removeCallbacks(leaveSessionRunnable)
        ensureOverlayView().visibility = View.VISIBLE
        refreshOverlayText()
        startPauseTickIfNeeded()
    }

    private fun startPauseTickIfNeeded() {
        mainHandler.removeCallbacks(pauseTickRunnable)
        if (trackingPaused && overlayView?.visibility == View.VISIBLE) {
            mainHandler.postDelayed(pauseTickRunnable, PAUSE_TICK_MS)
        }
    }

    /** Ends the session (and hides the badge) after a grace period, so a stray blip doesn't end it early. */
    private fun scheduleLeaveSession() {
        mainHandler.removeCallbacks(leaveSessionRunnable)
        mainHandler.postDelayed(leaveSessionRunnable, HIDE_GRACE_MS)
    }

    private fun refreshOverlayText() {
        val view = overlayView ?: return
        if (view.visibility != View.VISIBLE) return
        if (trackingPaused) {
            view.text = "😢 Paused · ${formatPauseRemaining(pausedUntilMs - System.currentTimeMillis())}"
            return
        }
        val mood = Mood.forProgress(todayCount, effectiveLimit)
        // Same shape as Home and the widget: the total you're blocked at, with any extra called
        // out, so "190" never appears without explaining where the extra 4 came from.
        val extra = if (extraAllowance > 0) " (+$extraAllowance)" else ""
        view.text = "${mood.emoji} $todayCount / $effectiveLimit$extra reels"
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(leaveSessionRunnable)
        mainHandler.removeCallbacks(pauseEndedRunnable)
        mainHandler.removeCallbacks(pauseTickRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        serviceJob.cancel()
    }

    companion object {
        private const val SCROLL_DEBOUNCE_MS = 700L
        private const val BLOCK_REDISPLAY_COOLDOWN_MS = 1500L
        private const val HIDE_GRACE_MS = 1200L
        private const val PAUSE_TICK_MS = 1000L
        private const val SURFACE_CHECK_TTL_MS = 300L
        private const val ID_CHAIN_DEPTH = 3
    }
}
