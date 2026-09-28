package io.github.gobi12b.reclaimlife.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Durations for Home's few animations (named as in the Home spec). Motion is a small gift, never
 * something to wait for.
 */
object Motion {
    const val plantGrowMs: Int = 700
    const val fadeMs: Int = 200
    const val expandMs: Int = 250
    const val gaugeMs: Int = 300
    /** Per half-cycle, reversing between [skeletonAlphaLow] and [skeletonAlphaHigh]. */
    const val skeletonPulseMs: Int = 900
    const val skeletonAlphaLow: Float = 0.55f
    const val skeletonAlphaHigh: Float = 0.85f
    /** The skeleton's alpha when animations are off. */
    const val skeletonAlphaStatic: Float = 0.7f
}

/**
 * True when "Remove animations" is on (animator scale 0), so every Home animation snaps to its end
 * state. Re-read on resume, since the user flips it in Settings while the app is in the background.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(resumes) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
