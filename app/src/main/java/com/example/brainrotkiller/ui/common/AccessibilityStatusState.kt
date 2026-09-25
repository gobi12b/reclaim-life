package com.example.brainrotkiller.ui.common

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.brainrotkiller.service.AccessibilityStatus
import com.example.brainrotkiller.service.reelBlockerAccessibilityStatus
import kotlinx.coroutines.delay

/** How long a freshly enabled service gets to bind before we call it "not running". */
private const val BIND_GRACE_MS = 2_000L

/**
 * Live [AccessibilityStatus] for the reel counter. Re-checked on every resume, whenever the
 * enabled-services setting changes, and whenever the system's accessibility state changes — so
 * it reflects reality while the screen is open, not just the moment it was last resumed.
 */
@Composable
fun rememberAccessibilityStatus(): AccessibilityStatus {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val settingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { tick++ }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            settingObserver
        )
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { tick++ }
        manager?.addAccessibilityStateChangeListener(stateListener)
        onDispose {
            context.contentResolver.unregisterContentObserver(settingObserver)
            manager?.removeAccessibilityStateChangeListener(stateListener)
        }
    }

    var status by remember { mutableStateOf(reelBlockerAccessibilityStatus(context)) }
    LaunchedEffect(tick) {
        val now = reelBlockerAccessibilityStatus(context)
        // Enabled-but-unbound is also what the first second after flipping the toggle looks
        // like, so give the system a moment to bind before showing the "not running" state.
        if (now == AccessibilityStatus.ENABLED_NOT_RUNNING && status != AccessibilityStatus.ENABLED_NOT_RUNNING) {
            delay(BIND_GRACE_MS)
            status = reelBlockerAccessibilityStatus(context)
        } else {
            status = now
        }
    }
    return status
}
