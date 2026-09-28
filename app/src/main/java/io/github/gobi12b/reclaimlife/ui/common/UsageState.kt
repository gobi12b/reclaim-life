package io.github.gobi12b.reclaimlife.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import io.github.gobi12b.reclaimlife.service.Progress
import io.github.gobi12b.reclaimlife.service.computeProgress
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.AppStretch
import io.github.gobi12b.reclaimlife.data.DEFAULT_TRACKED_APPS
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.USAGE_WINDOW_MS
import io.github.gobi12b.reclaimlife.service.appStretches
import io.github.gobi12b.reclaimlife.service.hasUsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Time in the chosen apps over the last [days] days, as loaded for Home and Insights. */
data class LoadedUsage(
    val stretches: List<AppStretch>,
    /** From Android's full record (Usage access allowed) rather than ReclaimLife's own timing. */
    val fromSystem: Boolean,
    val loadedAtMs: Long
) {
    /** Milliseconds [packageName] was in front over the 24 hours before [loadedAtMs]. */
    fun last24hMs(packageName: String): Long {
        val windowStart = loadedAtMs - USAGE_WINDOW_MS
        return stretches.filter { it.packageName == packageName }
            .sumOf { (minOf(it.endMs, loadedAtMs) - maxOf(it.startMs, windowStart)).coerceAtLeast(0L) }
    }
}

/**
 * Loads app time off the main thread, and again every time the screen resumes — so coming back
 * from Settings after allowing Usage access, or from Instagram, shows fresh numbers. Null while
 * the first load runs.
 */
@Composable
fun rememberUsage(days: Int, packages: Set<String>): LoadedUsage? {
    val context = LocalContext.current
    val app = context.applicationContext as ReclaimLifeApp
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val usage by produceState<LoadedUsage?>(initialValue = null, resumes, days, packages) {
        value = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val (stretches, fromSystem) = appStretches(
                context,
                packages,
                app.reelUsageRepository.appUsageSpans.first(),
                sinceMs = now - days * USAGE_WINDOW_MS,
                nowMs = now
            )
            LoadedUsage(stretches, fromSystem, now)
        }
    }
    return usage
}

/**
 * Time won back ([Progress]) — the same calculation the widget uses. Recomputed on every resume,
 * once a minute, and whenever one of [keys] changes (a new reel, a pause). The last value stays
 * while the next is worked out, so the card doesn't flash; null only before the first.
 */
@Composable
fun rememberProgress(vararg keys: Any?): Progress? {
    val app = LocalContext.current.applicationContext as ReclaimLifeApp
    val resumes = rememberResumeCount()
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minute++
        }
    }
    var progress by remember { mutableStateOf<Progress?>(null) }
    LaunchedEffect(resumes, minute, *keys) { progress = computeProgress(app) }
    return progress
}

@Composable
private fun rememberResumeCount(): Int {
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumes
}

/** The apps the user chose to watch, in slot (colour) order. */
@Composable
fun rememberTrackedApps(): List<TrackedApp> {
    val app = LocalContext.current.applicationContext as ReclaimLifeApp
    val apps by app.settingsRepository.trackedApps.collectAsState(initial = DEFAULT_TRACKED_APPS)
    return apps
}

/** Whether Usage access is allowed, re-checked on every resume. */
@Composable
fun rememberHasUsageAccess(): Boolean {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(resumes) { hasUsageAccess(context) }
}
