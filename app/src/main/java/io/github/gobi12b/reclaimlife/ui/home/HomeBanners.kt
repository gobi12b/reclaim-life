package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.service.AccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.AccessibilityConsentDialog
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import java.text.DateFormat
import java.util.Date

/**
 * Red only for a genuine fault (the counter isn't running), gold for a state to notice (a pause),
 * neutral for a one-off nudge. Never red for usage going up.
 */
internal enum class BannerTone { ALERT, PAUSE, NEUTRAL }

@Composable
private fun BannerTone.container(): Color = when (this) {
    BannerTone.ALERT -> MaterialTheme.colorScheme.errorContainer
    BannerTone.PAUSE -> MaterialTheme.colorScheme.tertiaryContainer
    BannerTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun BannerTone.content(): Color = when (this) {
    BannerTone.ALERT -> MaterialTheme.colorScheme.onErrorContainer
    BannerTone.PAUSE -> MaterialTheme.colorScheme.onTertiaryContainer
    BannerTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}

/**
 * An alert on Home. Stacked, not side by side: at 200% font a Row squeezed the text into a narrow
 * column beside the button, so glyph and title share a line, the body sits below and the actions
 * end the banner.
 */
@Composable
internal fun HomeBanner(
    tone: BannerTone,
    glyph: (@Composable () -> Unit)?,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(Radii.panel),
        color = tone.container(),
        contentColor = tone.content(),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) {
                    glyph()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = if (glyph != null) 10.dp else 0.dp)
                        .semantics { heading() }
                )
            }
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), content = actions)
        }
    }
}

/**
 * The counter is off or stopped: the one filled button on Home. Same disclosure and explicit
 * consent as onboarding before Settings opens (Play's prominent-disclosure rule covers every path,
 * including "Not now" users and restarts).
 */
@Composable
internal fun AccessibilityBanner(status: AccessibilityStatus, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    val notRunning = status == AccessibilityStatus.ENABLED_NOT_RUNNING
    val onColor = MaterialTheme.colorScheme.onErrorContainer
    HomeBanner(
        tone = BannerTone.ALERT,
        glyph = { AlertGlyph() },
        title = if (notRunning) "The reel counter stopped" else "Reels aren't being counted",
        body = if (notRunning) {
            "Accessibility is on for ReclaimLife, but Android isn't running it — often after a crash " +
                "or battery saver. Turn it off and on again to restart counting. Time saved leaves these hours out."
        } else {
            "Accessibility access for ReclaimLife is off, so nothing is counted or blocked. " +
                "Time saved leaves these hours out."
        },
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Button(onClick = { showConsent = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (notRunning) "Restart it in Accessibility settings" else "Turn on Accessibility access")
        }
        TextButton(
            onClick = { showHelp = !showHelp },
            colors = ButtonDefaults.textButtonColors(contentColor = onColor),
            modifier = Modifier.semantics { stateDescription = if (showHelp) "Expanded" else "Collapsed" }
        ) {
            Text(if (notRunning) "Keeps stopping?" else "Switch won't move?")
            Chevron(onColor, rotation = if (showHelp) -90f else 90f)
        }
        AnimatedVisibility(visible = showHelp, enter = expandEnter(reducedMotion), exit = expandExit(reducedMotion)) {
            Text(
                text = if (notRunning) {
                    "Some phones stop background services to save battery. Settings → Apps → " +
                        "ReclaimLife → Battery → Unrestricted keeps the counter running."
                } else {
                    "In Accessibility, find ReclaimLife under Downloaded apps and turn it on. " +
                        "If the switch won't move: Settings → Apps → ReclaimLife → ⋮ menu → " +
                        "Allow restricted settings, then try again."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showConsent) {
        AccessibilityConsentDialog(onDismiss = { showConsent = false })
    }
}

/** The pause symbol, labelled for TalkBack so the state isn't carried by colour alone. */
@Composable
private fun PausedGlyph() {
    PauseBars(
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.size(24.dp).clearAndSetSemantics { contentDescription = "Paused" }
    )
}

/** Outlined, not filled: Home keeps its one filled button for a broken counter. */
@Composable
private fun ColumnScope.PauseAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onTertiaryContainer
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.align(Alignment.End)
    ) { Text(label) }
}

/**
 * Gold, not grey: a pause is a state to notice, and "Resume now" is the one thing to do about it.
 * Rest of today shows its intention: "Enjoy: Movie night with friends · back at 00:00".
 */
@Composable
internal fun PausedBanner(remainingMs: Long, resumesAtMs: Long, intention: String?, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val resumesAt = remember(resumesAtMs) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(resumesAtMs)) }
    HomeBanner(
        tone = BannerTone.PAUSE,
        glyph = { PausedGlyph() },
        title = "Paused · ${formatPauseRemaining(remainingMs)} left",
        body = if (intention.isNullOrBlank()) {
            "Reels aren't counted or blocked. Back on by itself at $resumesAt."
        } else {
            "Enjoy: $intention · back at $resumesAt"
        },
        modifier = modifier
    ) {
        PauseAction("Resume now", onResume)
    }
}

/** Rest of today's one-minute delayed start, cancellable until it runs out. */
@Composable
internal fun PendingPauseBanner(startsInMs: Long, intention: String?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    HomeBanner(
        tone = BannerTone.PAUSE,
        glyph = { PausedGlyph() },
        title = "Rest of today starts in ${formatPauseRemaining(startsInMs)}",
        body = if (intention.isNullOrBlank()) "Changed your mind? Cancel keeps tracking on." else "Enjoy: $intention",
        modifier = modifier
    ) {
        PauseAction("Cancel", onCancel, Modifier.semantics { contentDescription = "Cancel Rest of today" })
    }
}

/** For installs from before the apps step: shown once, and dismissing it is permanent. */
@Composable
internal fun AppsCheckBanner(onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radii.panel),
        color = BannerTone.NEUTRAL.container(),
        contentColor = BannerTone.NEUTRAL.content(),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onOpen)
            ) {
                Text(
                    text = "Check which apps ReclaimLife helps with",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Chevron(MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
