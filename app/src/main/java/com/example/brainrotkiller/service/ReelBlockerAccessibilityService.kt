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
import com.example.brainrotkiller.ui.gate.GateActivity
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
    private val effectiveLimit: Int get() = dailyLimit + extraAllowance

    private var lastCountedAtMs: Long = 0L
    private var lastBlockShownAtMs: Long = 0L
    private var lastForegroundPackage: String? = null

    private var overlayView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { overlayView?.visibility = View.GONE }

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
            }
        }
        serviceScope.launch {
            app.reelUsageRepository.todayCount.collectLatest {
                todayCount = it
                refreshOverlayText()
            }
        }
        serviceScope.launch {
            app.reelUsageRepository.todayExtraAllowance.collectLatest {
                extraAllowance = it
                refreshOverlayText()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()

        // Only a real window/app switch counts as "left the target app". Scroll and
        // content-changed events that momentarily report a different or null package
        // (ads, embedded surfaces, system UI blips during a fling) are noise, not a
        // real switch — reacting to them was what made the overlay flicker while scrolling.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val justOpened = packageName != lastForegroundPackage
            lastForegroundPackage = packageName

            if (!TargetApps.isTarget(packageName)) {
                scheduleHideCounterOverlay()
                return
            }
            showCounterOverlay()
            if (todayCount >= effectiveLimit) {
                showBlockScreenIfNeeded()
                return
            }
            if (justOpened) showGateScreen()
            return
        }

        if (!TargetApps.isTarget(packageName)) return
        showCounterOverlay()

        if (todayCount >= effectiveLimit) {
            showBlockScreenIfNeeded()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            registerReelScroll()
        }
    }

    /** The mandatory "here's today's count" check-in shown every time Instagram/YouTube opens. */
    private fun showGateScreen() {
        val intent = Intent(this, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
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
        mainHandler.removeCallbacks(hideOverlayRunnable)
        ensureOverlayView().visibility = View.VISIBLE
        refreshOverlayText()
    }

    /** Hides after a short grace period instead of instantly, so a single stray event doesn't flicker it. */
    private fun scheduleHideCounterOverlay() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.postDelayed(hideOverlayRunnable, HIDE_GRACE_MS)
    }

    private fun refreshOverlayText() {
        val view = overlayView ?: return
        if (view.visibility != View.VISIBLE) return
        val mood = Mood.forProgress(todayCount, effectiveLimit)
        view.text = "${mood.emoji} $todayCount / $effectiveLimit reels"
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(hideOverlayRunnable)
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
