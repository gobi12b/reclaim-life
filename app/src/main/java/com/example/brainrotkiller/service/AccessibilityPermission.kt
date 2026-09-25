package com.example.brainrotkiller.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * What the Home screen / widget need to know about the reel counter service:
 * - [ON]: turned on in Settings *and* actually bound by the system right now.
 * - [ENABLED_NOT_RUNNING]: the toggle is on, but the system isn't running it — usually it crashed
 *   or an OEM battery manager killed it. Android does not re-bind a crashed service by itself; the
 *   user has to toggle it off and on again.
 * - [OFF]: not turned on in Settings.
 */
enum class AccessibilityStatus { ON, ENABLED_NOT_RUNNING, OFF }

fun reelBlockerAccessibilityStatus(context: Context): AccessibilityStatus = when {
    isReelBlockerServiceRunning(context) -> AccessibilityStatus.ON
    isReelBlockerServiceEnabled(context) -> AccessibilityStatus.ENABLED_NOT_RUNNING
    else -> AccessibilityStatus.OFF
}

/**
 * Whether [ReelBlockerAccessibilityService] is turned on in system settings.
 *
 * The setting can hold our component in either the long form
 * (`com.example.brainrotkiller/com.example.brainrotkiller.service.ReelBlockerAccessibilityService`,
 * what the Settings app writes) or the short form
 * (`com.example.brainrotkiller/.service.ReelBlockerAccessibilityService`, what
 * AccessibilityManagerService writes whenever *it* re-persists the list). Matching only the long
 * form used to report "off" for a service that was still on — see [enabledServicesContain].
 */
fun isReelBlockerServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServicesContain(
        enabledServices,
        context.packageName,
        ReelBlockerAccessibilityService::class.java.name
    )
}

/**
 * Whether the system currently has [ReelBlockerAccessibilityService] bound. Unlike the Settings
 * string (which keeps listing a service after it crashes), this list only contains live services.
 */
fun isReelBlockerServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val className = ReelBlockerAccessibilityService::class.java.name
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            serviceInfo.packageName == context.packageName && serviceInfo.name == className
        }
}

/**
 * Parses the colon-separated ENABLED_ACCESSIBILITY_SERVICES value the same way the framework does
 * (ComponentName.unflattenFromString: a class starting with "." is relative to the package).
 * Kept free of Android types so it's unit-testable on the JVM.
 */
internal fun enabledServicesContain(setting: String?, packageName: String, className: String): Boolean {
    if (setting.isNullOrBlank()) return false
    return setting.split(':').any { entry ->
        val slash = entry.indexOf('/')
        if (slash <= 0) return@any false
        val pkg = entry.substring(0, slash).trim()
        var cls = entry.substring(slash + 1).trim()
        if (cls.startsWith(".")) cls = pkg + cls
        pkg == packageName && cls == className
    }
}
