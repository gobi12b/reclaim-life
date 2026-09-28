package io.github.gobi12b.reclaimlife.ui.pause

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.CALM_SCREEN_FROM_PAUSE
import io.github.gobi12b.reclaimlife.data.INTENTION_MAX_CHARS
import io.github.gobi12b.reclaimlife.data.PauseDuration
import io.github.gobi12b.reclaimlife.data.PauseReason
import io.github.gobi12b.reclaimlife.data.pauseTreeLine
import io.github.gobi12b.reclaimlife.data.formatAvailableAt
import io.github.gobi12b.reclaimlife.data.intentionIsValid
import io.github.gobi12b.reclaimlife.ui.gate.SwayingSprout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PauseStep { CHOOSE, CALM, HOLD, INTENTION }

private const val HOLD_MS = 3_000
private const val HOLD_DRAIN_MS = 300
private const val CALM_SECONDS = 10

/**
 * Taking a break, with friction that scales with its length instead of a recording: a 3-second
 * hold for 15 minutes; a reason and a calm 10 seconds for an hour; a typed intention and a
 * one-minute delayed start for the rest of today. Silent, one-handed, and always time-boxed.
 *
 * [calmLine] is today's progress for the calm screen ("You've won back 42 min today."), or the last
 * 24 hours before saved figures exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    pausesToday: Int,
    restOfTodayAvailableAtMs: Long?,
    calmLine: String,
    onDismiss: () -> Unit,
    onStart: (PauseDuration, PauseReason) -> Unit,
    onScheduleRestOfToday: (PauseReason, String) -> Unit,
    /** The tree's name, or null to leave out the tree line (it's already resting today). */
    treeName: String? = null,
    pausedTodayMs: Long = 0L
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(PauseStep.CHOOSE) }
    var duration by remember { mutableStateOf(PauseDuration.FIFTEEN_MINUTES) }
    var reason by remember { mutableStateOf<PauseReason?>(null) }
    // From the 3rd pause in a day, every length gets the calm screen first.
    val calmForAll = pausesToday + 1 >= CALM_SCREEN_FROM_PAUSE

    fun afterCalm(): PauseStep = if (duration == PauseDuration.REST_OF_TODAY) PauseStep.INTENTION else PauseStep.HOLD

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            when (step) {
                PauseStep.CHOOSE -> ChooseStep(
                    duration = duration,
                    reason = reason,
                    restOfTodayAvailableAtMs = restOfTodayAvailableAtMs,
                    treeLine = treeName?.let { pauseTreeLine(it, duration, pausedTodayMs) },
                    onDurationChange = { duration = it },
                    onReasonChange = { reason = it },
                    onNext = {
                        step = when {
                            duration == PauseDuration.ONE_HOUR || calmForAll -> PauseStep.CALM
                            else -> afterCalm()
                        }
                    }
                )
                PauseStep.CALM -> CalmStep(
                    duration = duration,
                    calmLine = calmLine,
                    onStartHour = { onStart(PauseDuration.ONE_HOUR, reason ?: PauseReason.OTHER) },
                    // Switches the length without starting over: the calm part is done.
                    onMaybeFifteen = {
                        duration = PauseDuration.FIFTEEN_MINUTES
                        step = PauseStep.HOLD
                    },
                    onContinue = { step = afterCalm() }
                )
                PauseStep.HOLD -> HoldStep(onStart = { onStart(PauseDuration.FIFTEEN_MINUTES, reason ?: PauseReason.OTHER) })
                PauseStep.INTENTION -> IntentionStep(onStart = { onScheduleRestOfToday(reason ?: PauseReason.OTHER, it) })
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    Text(
        text = subtitle,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    )
}

@Composable
private fun ChooseStep(
    duration: PauseDuration,
    reason: PauseReason?,
    restOfTodayAvailableAtMs: Long?,
    treeLine: String?,
    onDurationChange: (PauseDuration) -> Unit,
    onReasonChange: (PauseReason) -> Unit,
    onNext: () -> Unit
) {
    val restLocked = restOfTodayAvailableAtMs != null
    val availability = restOfTodayAvailableAtMs?.let { formatAvailableAt(it, System.currentTimeMillis()) }
    SheetTitle("Pause tracking?", "Turns back on by itself.")

    Text("How long", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PauseDuration.entries.forEach { option ->
            val locked = option == PauseDuration.REST_OF_TODAY && restLocked
            FilterChip(
                selected = duration == option,
                // Not an M3 disabled chip: those drop out of focus, and TalkBack users need to hear why.
                onClick = { if (!locked) onDurationChange(option) },
                label = { Text(option.label) },
                modifier = Modifier
                    .alpha(if (locked) 0.5f else 1f)
                    .semantics {
                        if (locked && availability != null) {
                            disabled()
                            stateDescription = availability
                        }
                    }
            )
        }
    }
    if (availability != null) {
        Text(
            text = "Rest of today: $availability. Once every 3 days.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
    // Follows the selected length, so the choice is made knowing what the tree does.
    treeLine?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }

    Text(
        "What's it for?",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PauseReason.entries.forEach { option ->
            FilterChip(selected = reason == option, onClick = { onReasonChange(option) }, label = { Text(option.label) })
        }
    }

    Button(
        onClick = onNext,
        enabled = reason != null,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
    ) {
        Text(
            when (duration) {
                PauseDuration.FIFTEEN_MINUTES -> "Take 15 minutes"
                PauseDuration.ONE_HOUR -> "Take an hour"
                PauseDuration.REST_OF_TODAY -> "Take the rest of today"
            }
        )
    }
    if (reason == null) {
        Text("Pick one to continue.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ten calm seconds with the day's progress in view, before the pause can start. */
@Composable
private fun CalmStep(
    duration: PauseDuration,
    calmLine: String,
    onStartHour: () -> Unit,
    onMaybeFifteen: () -> Unit,
    onContinue: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(CALM_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    val hour = duration == PauseDuration.ONE_HOUR
    SheetTitle(if (hour) "An hour off?" else "A calm moment first", "Turns back on by itself.")
    Box(contentAlignment = Alignment.Center) {
        SwayingSprout()
        if (secondsLeft > 0) {
            Text(
                text = "$secondsLeft",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .semantics { contentDescription = "$secondsLeft seconds" }
            )
        }
    }
    Text(
        text = calmLine + if (hour) " Want the full hour?" else "",
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 12.dp)
    )
    val ready = secondsLeft == 0
    if (hour) {
        Button(onClick = onStartHour, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (ready) "Start my hour" else "Start my hour in $secondsLeft")
        }
        OutlinedButton(onClick = onMaybeFifteen, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Maybe 15 min")
        }
    } else {
        Button(onClick = onContinue, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (ready) "Continue" else "Continue in $secondsLeft")
        }
    }
}

/**
 * Hold for 3 seconds while a ring fills. Letting go early drains it back, with no error text.
 * With TalkBack or Switch Access on, a hold can't be done reliably, so it becomes a 3-second
 * countdown and then a Start button.
 */
@Composable
private fun HoldStep(onStart: () -> Unit) {
    SheetTitle("Taking 15 minutes.", "Hold to start.")
    if (rememberNeedsHoldAlternative()) {
        var secondsLeft by remember { mutableIntStateOf(HOLD_MS / 1000) }
        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
        }
        Button(
            onClick = onStart,
            enabled = secondsLeft == 0,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(if (secondsLeft == 0) "Start 15 minute pause" else "Start 15 minute pause in $secondsLeft")
        }
    } else {
        HoldToStartButton(onComplete = onStart)
    }
}

@Composable
private fun HoldToStartButton(onComplete: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val fill = MaterialTheme.colorScheme.primary
    var fillJob by remember { mutableStateOf<Job?>(null) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(148.dp)
            .semantics { contentDescription = "Hold to start" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    fillJob?.cancel()
                    fillJob = scope.launch {
                        val remaining = ((1f - progress.value) * HOLD_MS).toInt()
                        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        fillJob?.cancel()
                        fillJob = scope.launch { progress.animateTo(0f, tween(HOLD_DRAIN_MS)) }
                    }
                })
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(fill, -90f, 360f * progress.value, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        ) {
            Text("Hold", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** TalkBack (touch exploration) or Switch Access is on: holds are replaced by a countdown. */
@Composable
private fun rememberNeedsHoldAlternative(): Boolean {
    val context = LocalContext.current
    return remember {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return@remember false
        manager.isTouchExplorationEnabled ||
            manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id.contains("switchaccess", ignoreCase = true) }
    }
}

/**
 * Rest of today asks for a plan. It shows on the Home banner while the pause runs and is deleted
 * when it ends — it's never kept in the pause log.
 */
@Composable
private fun IntentionStep(onStart: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = intentionIsValid(text)
    SheetTitle("What's the plan?", "Just for you. Deleted after.")
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= INTENTION_MAX_CHARS) text = it },
        placeholder = { Text("Movie night with friends") },
        supportingText = { Text("At least 3 words · ${text.length}/$INTENTION_MAX_CHARS") },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = "Starts in a minute. You can cancel.",
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    Spacer(Modifier.size(12.dp))
    Button(onClick = { onStart(text.trim()) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
        Text("Start in a minute")
    }
}
