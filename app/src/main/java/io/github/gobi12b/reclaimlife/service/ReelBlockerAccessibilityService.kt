package io.github.gobi12b.reclaimlife.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.R
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.DEFAULT_GATE_WAIT_MS
import io.github.gobi12b.reclaimlife.data.DEFAULT_TRACKED_APPS
import io.github.gobi12b.reclaimlife.data.PauseDuration
import io.github.gobi12b.reclaimlife.data.TargetApps
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.hourlyUnblockAt
import io.github.gobi12b.reclaimlife.data.reelsInWindow
import io.github.gobi12b.reclaimlife.ui.block.BlockActivity
import io.github.gobi12b.reclaimlife.ui.common.OwnScreens
import io.github.gobi12b.reclaimlife.ui.gate.GateActivity
import io.github.gobi12b.reclaimlife.widget.refreshReelWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watches Instagram Reels / YouTube Shorts and counts each reel swiped past. Only the reels
 * viewer counts ([ReelSurfaces]); a swipe is one reel when the pager settles on a new item
 * ([ReelPageTracker]). While that viewer is on screen, a small live counter badge is drawn over
 * it via the accessibility-overlay window type, which needs no extra "draw over other apps"
 * permission. View IDs are only reported because the config sets flagReportViewIds.
 *
 * Opening any app the user chose to watch starts an app session, which first shows the
 * [GateActivity] pause. Events come only from those apps (packageNames, set from settings), so
 * leaving them produces no event here. Instead, during a session, [foregroundCheckRunnable] looks
 * at which app is in front — adding up time spent for the stats — and ends the session once it's
 * neither a chosen app nor ReclaimLife.
 */
class ReelBlockerAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var dailyLimit: Int = Int.MAX_VALUE
    private var todayCount: Int = 0
    private var extraAllowance: Int = 0
    private var pausedFromMs: Long = 0L
    private var pausedUntilMs: Long = 0L
    private var pauseKind: PauseDuration? = null
    /**
     * Read at every event rather than cached, so a pause starts and ends on time without anything
     * writing to storage (Rest of today starts a minute after it's confirmed).
     */
    private val trackingPaused: Boolean get() = System.currentTimeMillis().let { it >= pausedFromMs && it < pausedUntilMs }
    private var gateEnabled: Boolean = true
    private var gateDisabledApps: Set<String> = emptySet()
    private var gateWaitMs: Long = DEFAULT_GATE_WAIT_MS
    private val effectiveLimit: Int get() = dailyLimit + extraAllowance
    private var limitMode: LimitMode = LimitMode.DAILY
    /** The apps the user chose: they get the open-pause and their time is tracked. */
    private var trackedPackages: Set<String> = DEFAULT_TRACKED_APPS.map { it.packageName }.toSet()
    /** Reels allowed per rolling hour. Extras granted on the block screen don't apply to it. */
    private var hourlyLimit: Int = 0
    /** 0 (never blocks — see [hourlyUnblockAt]) unless the mode enforces the hourly limit. */
    private val enforcedHourlyLimit: Int get() = if (limitMode.usesHourly) hourlyLimit else 0
    private val dailyLimitReached: Boolean get() = limitMode.usesDaily && todayCount >= effectiveLimit
    private var recentReelTimes: List<Long> = emptyList()

    private var lastBlockShownAtMs: Long = 0L

    // An app session runs from opening a chosen app until something else is in front; our own
    // Gate/Block screens on top of it don't end it.
    private var sessionPackage: String? = null
    private var awaySinceMs: Long = 0L
    /** The gate is up for this session and no choice has been made on it yet. */
    private var gatePending: Boolean = false
    private var gateShownAtMs: Long = 0L
    private var usageStretchStartMs: Long = 0L
    private var usageStretchLastSeenMs: Long = 0L
    private val pageTracker = ReelPageTracker(debounceMs = SCROLL_DEBOUNCE_MS)

    // Reels-viewer presence is looked up in the window's node tree, which is too costly to do on
    // every content-changed event; the answer is reused briefly and dropped on window changes.
    private var surfaceCheckedAtMs: Long = 0L
    private var surfaceCheckedPackage: String? = null
    private var onReelSurfaceCached: Boolean = false

    private var overlayView: LinearLayout? = null
    private var counterText: TextView? = null
    private var counterPlant: CounterPlantView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val leaveSessionRunnable = Runnable {
        overlayView?.visibility = View.GONE
        pageTracker.reset()
    }
    /**
     * Fires when a pause starts or runs out, so the badge/widget flip without waiting for an
     * event — and a finished Rest of today's intention is deleted right away.
     */
    private val pauseEdgeRunnable = Runnable {
        refreshOverlayText()
        startPauseTickIfNeeded()
        serviceScope.launch {
            app.pauseController.clearExpired()
            refreshReelWidget(this@ReelBlockerAccessibilityService)
        }
    }
    /** A sign of life every few minutes, so a reboot's gap can be told apart from a quiet phone. */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                app.reelUsageRepository.markServiceAlive(System.currentTimeMillis(), bootTimeMs())
                // Closes finished days and settles baselines even if ReclaimLife itself isn't opened.
                runCatching { syncProgressData(app) }
            }
            mainHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }
    /** During an app session, adds up time in front and notices the user has switched away. */
    private val foregroundCheckRunnable = object : Runnable {
        override fun run() {
            val session = sessionPackage ?: return
            val now = System.currentTimeMillis()
            // Only the front window's package name is read, and only the chosen apps' windows can
            // be read at all — our own Gate/Block screens read as no window, so they report in via
            // OwnScreens and count as staying. Anything else, or no window, counts as leaving.
            val front = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                ?: if (OwnScreens.anyVisible) packageName else null
            if (gatePending && OwnScreens.takeGateDecision(session)) gatePending = false
            if (front == session && gatePending) {
                // The app came back over the gate before a choice was made (Instagram does this
                // while starting up) — put the gate back in front of it.
                flushUsage(session)
                if (now - gateShownAtMs >= GATE_RESHOW_MS) showGate(session)
            } else if (front == session) {
                trackUsage(session, now)
            } else {
                flushUsage(session)
            }
            if (front == packageName || front in trackedPackages) {
                awaySinceMs = 0L
            } else if (awaySinceMs == 0L) {
                awaySinceMs = now
            } else if (now - awaySinceMs >= HIDE_GRACE_MS) {
                // A grace period, so a stray blip doesn't end the session early.
                endAppSession()
                return
            }
            mainHandler.postDelayed(this, FOREGROUND_CHECK_MS)
        }
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
    private val app: ReclaimLifeApp get() = application as ReclaimLifeApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            app.reelUsageRepository.markServiceConnected(System.currentTimeMillis(), bootTimeMs())
            mainHandler.removeCallbacks(heartbeatRunnable)
            mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_MS)
        }
        serviceScope.launch {
            val limit = app.settingsRepository.dailyReelLimit.first()
            val mode = app.settingsRepository.limitMode.first()
            app.reelUsageRepository.closeOutPreviousDayIfNeeded(limit, mode)
        }
        serviceScope.launch { app.settingsRepository.migrateLegacyPauseIfNeeded() }
        serviceScope.launch {
            app.settingsRepository.trackedApps.collectLatest { apps ->
                trackedPackages = apps.map { it.packageName }.toSet()
                // Listen to exactly the chosen apps. The config's static list is only the default.
                serviceInfo = serviceInfo?.apply {
                    packageNames = trackedPackages.ifEmpty { TargetApps.PACKAGES }.toTypedArray()
                }
                sessionPackage?.let { if (it !in trackedPackages) endAppSession() }
            }
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
            app.settingsRepository.limitMode.collectLatest {
                limitMode = it
                refreshOverlayText()
                refreshReelWidget(this@ReelBlockerAccessibilityService)
            }
        }
        serviceScope.launch {
            app.settingsRepository.hourlyReelLimit.collectLatest {
                hourlyLimit = it
                refreshOverlayText()
            }
        }
        serviceScope.launch {
            app.reelUsageRepository.recentReelTimes.collectLatest {
                recentReelTimes = it
                refreshOverlayText()
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
            combine(
                app.settingsRepository.pausedFromMs,
                app.settingsRepository.pausedUntilMs,
                app.settingsRepository.pauseKind
            ) { from, until, kind -> Triple(from, until, kind) }.collectLatest { (from, until, kind) ->
                pausedFromMs = from
                pausedUntilMs = until
                pauseKind = kind
                mainHandler.removeCallbacks(pauseEdgeRunnable)
                val now = System.currentTimeMillis()
                // The next edge: the delayed start of Rest of today, or the end.
                val nextEdge = listOf(from, until).filter { it > now }.minOrNull()
                if (nextEdge != null) mainHandler.postDelayed(pauseEdgeRunnable, nextEdge - now)
                refreshOverlayText()
                startPauseTickIfNeeded()
                refreshReelWidget(this@ReelBlockerAccessibilityService)
            }
        }
        serviceScope.launch { app.settingsRepository.gateEnabled.collectLatest { gateEnabled = it } }
        serviceScope.launch { app.settingsRepository.gateDisabledApps.collectLatest { gateDisabledApps = it } }
        serviceScope.launch { app.settingsRepository.gateWaitMs.collectLatest { gateWaitMs = it } }
    }

    /** Switched off by the user: tracking is off until it's back (a crash never gets here). */
    override fun onUnbind(intent: Intent?): Boolean {
        app.appScope.launch { app.reelUsageRepository.markServiceDisconnected(System.currentTimeMillis()) }
        return super.onUnbind(intent)
    }

    /** Wall-clock time of the last boot, to notice the phone was off between two signs of life. */
    private fun bootTimeMs(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()

        // Our own Gate/Block screens briefly taking focus over a chosen app isn't a real
        // app switch — ignore them entirely so they can't influence session tracking.
        if (packageName == this.packageName) return

        // The service info's packageNames already filters these out; this just keeps it explicit.
        if (packageName == null || packageName !in trackedPackages) return

        // Opening the app (rather than moving around inside it) starts a session behind the gate.
        if (packageName != sessionPackage && isInFront(packageName) && startAppSession(packageName)) return

        // The badge and the counting follow the same signal: only while the Reels / Shorts viewer
        // is on screen. Feed, profiles, explore, stories and DMs neither show it nor count.
        val onReels = isOnReelSurface(packageName, event)
        if (onReels) showCounterOverlay() else scheduleLeaveSession()

        // Only Instagram and YouTube have reels to count and limits to enforce; other chosen apps
        // just get the open-pause and their time tracked.
        if (trackingPaused || !TargetApps.countsReels(packageName)) return

        // Blocking is deliberately app-wide, not reels-only: once over the limit, the stop screen
        // takes over Instagram/YouTube wherever you are in it.
        if (dailyLimitReached) {
            showBlockScreenIfNeeded()
            return
        }

        // Same app-wide reach as the daily block, but it lifts on its own as the hour rolls on.
        hourlyUnblockAt(recentReelTimes, enforcedHourlyLimit, System.currentTimeMillis())?.let {
            showBlockScreenIfNeeded(hourlyUnblockAtMs = it)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && onReels) {
            val isReelScroll = ReelSurfaces.isReelScroll(packageName, idChainOf(event.source))
            val reels = if (isReelScroll) {
                pageTracker.onScroll(event.fromIndex, event.toIndex, System.currentTimeMillis())
            } else {
                0
            }
            if (reels > 0) registerReels(packageName, reels)
        }
    }

    private fun isInFront(packageName: String): Boolean =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() == packageName

    /** Starts a session for [target] and shows the gate if it's due. Returns whether it was shown. */
    private fun startAppSession(target: String): Boolean {
        endAppSession()
        val now = System.currentTimeMillis()
        sessionPackage = target
        awaySinceMs = 0L
        mainHandler.postDelayed(foregroundCheckRunnable, FOREGROUND_CHECK_MS)

        // Pause before opening is the user's choice: the main switch, then each app's own. It
        // also steps aside when the block screen is about to take over. A 15 min or 1 hour pause
        // stops counting and blocking, but the gate still shows — it's a breath, not a limit.
        // Rest of today is different: movie night shouldn't be interrupted on every open.
        if (!gateEnabled || target in gateDisabledApps) return false
        if (trackingPaused && pauseKind == PauseDuration.REST_OF_TODAY) return false
        val blocked = !trackingPaused && TargetApps.countsReels(target) &&
            (dailyLimitReached || hourlyUnblockAt(recentReelTimes, enforcedHourlyLimit, now) != null)
        if (blocked) return false

        OwnScreens.takeGateDecision(target) // drop any leftover choice from an earlier gate
        gatePending = true
        showGate(target)
        return true
    }

    private fun showGate(target: String) {
        gateShownAtMs = System.currentTimeMillis()
        startActivity(Intent(this, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(GateActivity.EXTRA_TARGET_PACKAGE, target)
            putExtra(GateActivity.EXTRA_WAIT_MS, gateWaitMs)
        })
    }

    private fun endAppSession() {
        val session = sessionPackage ?: return
        flushUsage(session)
        sessionPackage = null
        gatePending = false
        mainHandler.removeCallbacks(foregroundCheckRunnable)
        mainHandler.removeCallbacks(leaveSessionRunnable)
        leaveSessionRunnable.run()
    }

    /** Extends the current stretch of time in front, saving it every [USAGE_FLUSH_MS] so little is lost. */
    private fun trackUsage(target: String, now: Long) {
        if (usageStretchStartMs == 0L) usageStretchStartMs = now
        usageStretchLastSeenMs = now
        if (now - usageStretchStartMs >= USAGE_FLUSH_MS) {
            flushUsage(target)
            usageStretchStartMs = now
            usageStretchLastSeenMs = now
        }
    }

    private fun flushUsage(target: String) {
        val start = usageStretchStartMs
        val end = usageStretchLastSeenMs
        usageStretchStartMs = 0L
        if (start in 1 until end) {
            serviceScope.launch { app.reelUsageRepository.addAppUsage(target, start, end) }
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

    private fun registerReels(packageName: String, reels: Int) {
        serviceScope.launch {
            // Read fresh rather than from the collected fields, which start unset: the first reel of
            // a new day also closes yesterday's outcome against these.
            val limit = app.settingsRepository.dailyReelLimit.first()
            val mode = app.settingsRepository.limitMode.first()
            val newCount = app.reelUsageRepository.incrementAndGet(packageName, reels, limit, mode)
            todayCount = newCount
            // The stored times arrive via the flow shortly; mirror them now so the block is immediate.
            val now = System.currentTimeMillis()
            recentReelTimes = recentReelTimes + List(reels) { now }
            refreshOverlayText()
            if (dailyLimitReached) {
                showBlockScreenIfNeeded()
            } else {
                hourlyUnblockAt(recentReelTimes, enforcedHourlyLimit, now)?.let { showBlockScreenIfNeeded(hourlyUnblockAtMs = it) }
            }
        }
    }

    /** [hourlyUnblockAtMs] > 0 shows the hourly cooldown instead of the daily stop. */
    private fun showBlockScreenIfNeeded(hourlyUnblockAtMs: Long = 0L) {
        val now = System.currentTimeMillis()
        if (now - lastBlockShownAtMs < BLOCK_REDISPLAY_COOLDOWN_MS) return
        lastBlockShownAtMs = now

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockActivity.EXTRA_DAILY_LIMIT, dailyLimit)
            putExtra(BlockActivity.EXTRA_HOURLY_LIMIT, hourlyLimit)
            putExtra(BlockActivity.EXTRA_HOURLY_UNBLOCK_AT_MS, hourlyUnblockAtMs)
        }
        startActivity(intent)
    }

    private fun ensureOverlayView(): LinearLayout {
        overlayView?.let { return it }

        val density = resources.displayMetrics.density
        val plant = CounterPlantView(this)
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding((8 * density).toInt(), 0, 0, 0)
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(plant)
            addView(text)
            setPadding((16 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
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
        counterText = text
        counterPlant = plant
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

    /** Hides the badge after a grace period, so a stray blip doesn't hide it early. */
    private fun scheduleLeaveSession() {
        mainHandler.removeCallbacks(leaveSessionRunnable)
        mainHandler.postDelayed(leaveSessionRunnable, HIDE_GRACE_MS)
    }

    private fun refreshOverlayText() {
        val container = overlayView ?: return
        val view = counterText ?: return
        if (container.visibility != View.VISIBLE) return
        if (trackingPaused) {
            counterPlant?.setProgress(0f)
            view.text = getString(R.string.overlay_paused, formatPauseRemaining(pausedUntilMs - System.currentTimeMillis()))
            return
        }
        val thisHour = reelsInWindow(recentReelTimes, System.currentTimeMillis()).size
        if (!limitMode.usesDaily) {
            counterPlant?.setProgress(if (hourlyLimit > 0) thisHour.toFloat() / hourlyLimit else 0f)
            view.text = getString(R.string.overlay_hourly_only, thisHour, hourlyLimit)
            return
        }
        // The plant shows how close the limit is; the text keeps the exact numbers.
        counterPlant?.setProgress(
            maxOf(
                if (effectiveLimit > 0) todayCount.toFloat() / effectiveLimit else 0f,
                if (limitMode.usesHourly && hourlyLimit > 0) thisHour.toFloat() / hourlyLimit else 0f
            )
        )
        // Same shape as Home and the widget: the total you're blocked at, with any extra called
        // out, so "190" never appears without explaining where the extra 4 came from.
        val dailyText = if (extraAllowance > 0) {
            resources.getQuantityString(
                R.plurals.overlay_count_with_extra, effectiveLimit, todayCount, effectiveLimit, extraAllowance
            )
        } else {
            resources.getQuantityString(R.plurals.overlay_count, effectiveLimit, todayCount, effectiveLimit)
        }
        view.text = if (limitMode.usesHourly) {
            dailyText + "\n" + getString(R.string.overlay_hourly, thisHour, hourlyLimit)
        } else {
            dailyText
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(leaveSessionRunnable)
        mainHandler.removeCallbacks(pauseEdgeRunnable)
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.removeCallbacks(pauseTickRunnable)
        mainHandler.removeCallbacks(foregroundCheckRunnable)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        counterText = null
        counterPlant = null
        serviceJob.cancel()
    }

    companion object {
        private const val SCROLL_DEBOUNCE_MS = 700L
        private const val BLOCK_REDISPLAY_COOLDOWN_MS = 1500L
        private const val HIDE_GRACE_MS = 1200L
        private const val PAUSE_TICK_MS = 1000L
        private const val FOREGROUND_CHECK_MS = 500L
        private const val SURFACE_CHECK_TTL_MS = 300L
        /** Don't relaunch the gate faster than it can come up. */
        private const val GATE_RESHOW_MS = 1000L
        private const val USAGE_FLUSH_MS = 60_000L
        private const val HEARTBEAT_MS = 5 * 60_000L
        private const val ID_CHAIN_DEPTH = 3
    }
}
