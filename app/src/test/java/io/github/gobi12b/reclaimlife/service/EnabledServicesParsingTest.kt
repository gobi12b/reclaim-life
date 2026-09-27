package io.github.gobi12b.reclaimlife.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledServicesParsingTest {

    private val pkg = "io.github.gobi12b.reclaimlife"
    private val cls = "io.github.gobi12b.reclaimlife.service.ReelBlockerAccessibilityService"

    @Test
    fun longForm_asWrittenBySettingsApp_matches() {
        assertTrue(enabledServicesContain("$pkg/$cls", pkg, cls))
    }

    @Test
    fun shortForm_asWrittenByAccessibilityManagerService_matches() {
        // This is the P0 #1 regression: the old exact-string check returned false here.
        assertTrue(enabledServicesContain("$pkg/.service.ReelBlockerAccessibilityService", pkg, cls))
    }

    @Test
    fun matchesAmongOtherServices() {
        val setting = "com.google.android.marvin.talkback/.TalkBackService:" +
            "$pkg/.service.ReelBlockerAccessibilityService:com.other/.Svc"
        assertTrue(enabledServicesContain(setting, pkg, cls))
    }

    @Test
    fun missingOrEmpty_isOff() {
        assertFalse(enabledServicesContain(null, pkg, cls))
        assertFalse(enabledServicesContain("", pkg, cls))
        assertFalse(enabledServicesContain("com.other/.Svc", pkg, cls))
    }

    @Test
    fun differentPackageWithSameClass_isNotUs() {
        assertFalse(enabledServicesContain("com.evil/.service.ReelBlockerAccessibilityService", pkg, cls))
        assertFalse(enabledServicesContain("com.evil/$cls", pkg, cls))
        // The pre-rename class name no longer exists in this app.
        assertFalse(
            enabledServicesContain(
                "$pkg/com.example.brainrotkiller.service.ReelBlockerAccessibilityService", pkg, cls,
            ),
        )
    }
}
