package com.example.brainrotkiller.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.example.brainrotkiller.BrainRotKillerApp
import com.example.brainrotkiller.data.TargetApps
import com.example.brainrotkiller.ui.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
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

    private var lastCountedAtMs: Long = 0L
    private var lastBlockShownAtMs: Long = 0L

    private var overlayView: TextView? = null

    private val windowManager: WindowManager by lazy { getSystemService(WindowManager::class.java) }
    private val app: BrainRotKillerApp get() = application as BrainRotKillerApp

    override fun onServiceConnected() {
        super.onServiceConnected()
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (!TargetApps.isTarget(packageName)) {
            hideCounterOverlay()
            return
        }
        showCounterOverlay()

        if (todayCount >= dailyLimit) {
            showBlockScreenIfNeeded()
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            registerReelScroll()
        }
    }

    private fun registerReelScroll() {
        val now = System.currentTimeMillis()
        if (now - lastCountedAtMs < SCROLL_DEBOUNCE_MS) return
        lastCountedAtMs = now

        serviceScope.launch {
            val newCount = app.reelUsageRepository.incrementAndGet()
            todayCount = newCount
            refreshOverlayText()
            if (newCount >= dailyLimit) {
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
            textSize = 14f
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * density
                setColor(Color.argb(200, 20, 20, 28))
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
        ensureOverlayView().visibility = View.VISIBLE
        refreshOverlayText()
    }

    private fun hideCounterOverlay() {
        overlayView?.visibility = View.GONE
    }

    private fun refreshOverlayText() {
        val view = overlayView ?: return
        if (view.visibility != View.VISIBLE) return
        view.text = "$todayCount / $dailyLimit reels"
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        serviceJob.cancel()
    }

    companion object {
        private const val SCROLL_DEBOUNCE_MS = 700L
        private const val BLOCK_REDISPLAY_COOLDOWN_MS = 1500L
    }
}
