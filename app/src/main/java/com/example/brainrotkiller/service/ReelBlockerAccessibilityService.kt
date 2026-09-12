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
 * Watches Instagram/YouTube for reel-feed scrolling and counts each scroll as one reel watched.
 * Exact reel boundaries aren't exposed by these apps, so a debounced scroll event is the
 * closest reliable proxy for "the user swiped to the next reel". While a target app is in the
 * foreground, a small live counter badge is drawn over it via the accessibility-overlay window
 * type, which needs no extra "draw over other apps" permission.
 */
class ReelBlockerAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var dailyLimit: Int = Int.MAX_VALUE
    private var todayCount: Int = 0
    private var extraAllowance: Int = 0
    private var trackingPaused: Boolean = false
    private val effectiveLimit: Int get() = dailyLimit + extraAllowance

    private var lastCountedAtMs: Long = 0L
    private var lastBlockShownAtMs: Long = 0L
    private var lastVisibleReelChild: android.view.accessibility.AccessibilityNodeInfo? = null

    private var overlayView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val leaveSessionRunnable = Runnable { overlayView?.visibility = View.GONE }

    private val windowManager: WindowManager by lazy { getSystemService(WindowManager::class.java) }
    private val app: BrainRotKillerApp get() = application as BrainRotKillerApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            val limit = app.settingsRepository.dailyReelLimit.first()
            app.reelUsageRepository.closeOutPreviousDayIfNeeded(limit)
        }
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
            app.settingsRepository.trackingPaused.collectLatest {
                trackingPaused = it
                refreshOverlayText()
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

        showCounterOverlay()

        if (trackingPaused) return

        if (todayCount >= effectiveLimit) {
            showBlockScreenIfNeeded()
            return
        }

        if (packageName == TargetApps.YOUTUBE) {
            val reelNode = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(
                "com.google.android.youtube:id/reel_recycler"
            )?.firstOrNull()
            val visibleChild = reelNode?.let { parent ->
                (0 until parent.childCount).mapNotNull { parent.getChild(it) }.firstOrNull { it.isVisibleToUser }
            }
            if (visibleChild != null && visibleChild != lastVisibleReelChild) {
                android.util.Log.d(
                    "ReelDebug",
                    "REEL_CHILD_CHANGED prevWasNull=${lastVisibleReelChild == null} " +
                        "childClass=${visibleChild.className} childText=${visibleChild.text} " +
                        "childDesc=${visibleChild.contentDescription}"
                )
                lastVisibleReelChild = visibleChild
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isReelSurface(packageName, event)) {
            registerReelScroll()
        }
    }

    /**
     * YouTube is one app with many scrollable surfaces (home feed, search, comments, Shorts) —
     * counting every scroll in the whole app was both over-counting from normal video browsing
     * (blocking regular YouTube use) and, since that noise ate the debounce window, sometimes
     * missing genuine Shorts swipes. YouTube's Shorts implementation is internally still called
     * "reel" (confirmed via the live view hierarchy: reel_recycler, reel_player_page_container,
     * reel_watch_fragment_root, …), so only scrolls sourced from a "reel_" view actually count.
     * Instagram's Reels tab isn't similarly scoped — its counting already works from any scroll
     * while the Reels surface is frontmost, so it's left as-is to avoid regressing it.
     */
    private fun isReelSurface(packageName: String?, event: AccessibilityEvent): Boolean {
        if (packageName != TargetApps.YOUTUBE) return true
        val resourceId = event.source?.viewIdResourceName ?: return false
        return resourceId.contains("reel_")
    }

    private fun registerReelScroll() {
        val now = System.currentTimeMillis()
        if (now - lastCountedAtMs < SCROLL_DEBOUNCE_MS) return
        lastCountedAtMs = now

        serviceScope.launch {
            val newCount = app.reelUsageRepository.incrementAndGet()
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
            view.text = "😢 Paused"
            return
        }
        val mood = Mood.forProgress(todayCount, effectiveLimit)
        view.text = "${mood.emoji} $todayCount / $effectiveLimit reels"
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(leaveSessionRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        serviceJob.cancel()
    }

    companion object {
        private const val SCROLL_DEBOUNCE_MS = 700L
        private const val BLOCK_REDISPLAY_COOLDOWN_MS = 1500L
        private const val HIDE_GRACE_MS = 1200L
    }
}
