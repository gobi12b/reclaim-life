package com.example.brainrotkiller.service

import android.content.Context
import android.provider.Settings

/** Whether [ReelBlockerAccessibilityService] is currently turned on in system settings. */
fun isReelBlockerServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${ReelBlockerAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}
