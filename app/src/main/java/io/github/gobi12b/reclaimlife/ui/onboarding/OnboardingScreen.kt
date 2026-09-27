package io.github.gobi12b.reclaimlife.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.DEFAULT_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.DEFAULT_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.MAX_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.service.AccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.AccessibilityDisclosure
import io.github.gobi12b.reclaimlife.ui.common.CompactBrandMark
import io.github.gobi12b.reclaimlife.ui.common.HOURLY_LIMIT_PRESETS
import io.github.gobi12b.reclaimlife.ui.common.LimitPicker
import io.github.gobi12b.reclaimlife.ui.common.OptionCard
import io.github.gobi12b.reclaimlife.ui.common.SwapPicker
import io.github.gobi12b.reclaimlife.ui.common.rememberAccessibilityStatus

/**
 * Six steps: the (optional) name lives on the welcome screen rather than its own step, which is
 * what makes room for choosing the swap without lengthening onboarding.
 */
private enum class OnboardingStep(val index: Int) {
    WELCOME(0), LIMIT_MODE(1), SET_LIMIT(2), SWAP(3), PERMISSION(4), READY(5)
}
private val STEP_COUNT = OnboardingStep.entries.size

@Composable
fun OnboardingFlow(
    onComplete: (
        mode: LimitMode,
        dailyLimit: Int,
        hourlyLimit: Int,
        nickname: String,
        activity: ReplacementActivity,
        deck: FlashcardDeck
    ) -> Unit
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var limitMode by rememberSaveable { mutableStateOf(LimitMode.DAILY) }
    var dailyLimit by rememberSaveable { mutableIntStateOf(DEFAULT_DAILY_REEL_LIMIT) }
    var hourlyLimit by rememberSaveable { mutableIntStateOf(DEFAULT_HOURLY_REEL_LIMIT) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var swapActivity by rememberSaveable { mutableStateOf(ReplacementActivity.BREATHING) }
    var flashcardDeck by rememberSaveable { mutableStateOf(FlashcardDeck.CAPITALS) }

    // System back walks back through the steps; on the first step it falls through and exits.
    val previous = OnboardingStep.entries.getOrNull(step.index - 1)
    val goBack: (() -> Unit)? = previous?.let { { step = it } }
    BackHandler(enabled = previous != null) { goBack?.invoke() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                onNext = { step = OnboardingStep.LIMIT_MODE }
            )
            OnboardingStep.LIMIT_MODE -> LimitModeStep(
                limitMode = limitMode,
                onModeChange = { limitMode = it },
                onNext = { step = OnboardingStep.SET_LIMIT },
                onBack = goBack
            )
            OnboardingStep.SET_LIMIT -> SetLimitStep(
                limitMode = limitMode,
                dailyLimit = dailyLimit,
                onLimitChange = { dailyLimit = it },
                hourlyLimit = hourlyLimit,
                onHourlyLimitChange = { hourlyLimit = it },
                onNext = { step = OnboardingStep.SWAP },
                onBack = goBack
            )
            OnboardingStep.SWAP -> SwapStep(
                activity = swapActivity,
                deck = flashcardDeck,
                onActivityChange = { swapActivity = it },
                onDeckChange = { flashcardDeck = it },
                onNext = { step = OnboardingStep.PERMISSION },
                onBack = goBack
            )
            OnboardingStep.PERMISSION -> PermissionStep(onNext = { step = OnboardingStep.READY }, onBack = goBack)
            OnboardingStep.READY -> ReadyStep(
                nickname = nickname,
                activity = swapActivity,
                onFinish = { onComplete(limitMode, dailyLimit, hourlyLimit, nickname, swapActivity, flashcardDeck) },
                onBack = goBack
            )
        }
    }
}

@Composable
private fun StepProgress(stepIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        for (i in 0 until STEP_COUNT) {
            Box(
                modifier = Modifier
                    .padding(end = if (i == STEP_COUNT - 1) 0.dp else 6.dp)
                    .size(width = if (i == stepIndex) 20.dp else 6.dp, height = 6.dp)
                    .background(
                        color = if (i <= stepIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
private fun StepScaffold(
    stepIndex: Int,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    extraContent: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Text("← Back")
            }
        }
        // Centered when it fits, scrollable when it doesn't (the permission disclosure is long).
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompactBrandMark(modifier = Modifier.padding(bottom = 16.dp))
            StepProgress(stepIndex = stepIndex, modifier = Modifier.padding(bottom = 28.dp))
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            extraContent?.invoke()
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel)
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 4.dp)) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(nickname: String, onNicknameChange: (String) -> Unit, onNext: () -> Unit) {
    StepScaffold(
        stepIndex = OnboardingStep.WELCOME.index,
        title = "Take your time back.",
        body = "ReclaimLife counts the reels you watch. When you reach the limit you choose, it swaps " +
            "the next scroll for 2 minutes of something better — a breather, a few flashcards, or a " +
            "quick journal note.",
        primaryLabel = "Let's set it up",
        onPrimary = onNext,
        extraContent = {
            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 20) onNicknameChange(it) },
                label = { Text("What should we call you? (optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    )
}

/**
 * One tap to get through: Daily is preselected, so this step only costs time for people who
 * actually want something else. It's switchable later from Home's "Limit by".
 */
@Composable
private fun LimitModeStep(
    limitMode: LimitMode,
    onModeChange: (LimitMode) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    StepScaffold(
        stepIndex = OnboardingStep.LIMIT_MODE.index,
        title = "How do you want to limit?",
        body = "Pick what fits how you scroll. You can switch any time from Home.",
        primaryLabel = "Continue",
        onPrimary = onNext,
        onBack = onBack,
        extraContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .selectableGroup()
            ) {
                LimitMode.entries.forEach { mode ->
                    OptionCard(
                        title = mode.label,
                        description = when (mode) {
                            LimitMode.DAILY -> "One number for the whole day."
                            LimitMode.HOURLY -> "Reels for the hour, then a 2-minute break before any more."
                            LimitMode.BOTH -> "A daily cap, plus a 2-minute break whenever an hour's reels run out."
                        },
                        selected = mode == limitMode,
                        onSelect = { onModeChange(mode) }
                    )
                }
            }
        }
    )
}

@Composable
private fun SetLimitStep(
    limitMode: LimitMode,
    dailyLimit: Int,
    onLimitChange: (Int) -> Unit,
    hourlyLimit: Int,
    onHourlyLimitChange: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    StepScaffold(
        stepIndex = OnboardingStep.SET_LIMIT.index,
        title = when (limitMode) {
            LimitMode.DAILY -> "How many reels a day?"
            LimitMode.HOURLY -> "How many reels an hour?"
            LimitMode.BOTH -> "Set your two limits."
        },
        body = "Pick " + (if (limitMode == LimitMode.BOTH) "numbers that feel" else "a number that feels") +
            " doable today — there's no wrong answer. You can bring it down bit by bit as it gets easier.",
        primaryLabel = "Continue",
        onPrimary = onNext,
        onBack = onBack,
        extraContent = {
            if (limitMode.usesDaily) {
                LimitPicker(
                    value = dailyLimit,
                    onValueChange = onLimitChange,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
            if (limitMode.usesHourly) {
                LimitPicker(
                    value = hourlyLimit,
                    onValueChange = onHourlyLimitChange,
                    ceiling = MAX_HOURLY_REEL_LIMIT,
                    presets = HOURLY_LIMIT_PRESETS,
                    unitLabel = "reels / hour",
                    fieldLabel = "Hourly limit",
                    modifier = Modifier.padding(bottom = if (limitMode == LimitMode.BOTH) 12.dp else 32.dp)
                )
            }
            // Not blocked — just said, so nobody sets up a limit that silently never does anything.
            if (limitMode == LimitMode.BOTH && hourlyLimit >= dailyLimit) {
                Text(
                    text = "Your hourly limit is at or above your daily one, so it'll never kick in.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            } else if (limitMode == LimitMode.BOTH) {
                Spacer(Modifier.size(20.dp))
            }
        }
    )
}

@Composable
private fun PermissionStep(onNext: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val status = rememberAccessibilityStatus()
    val enabled = status != AccessibilityStatus.OFF
    // Only auto-advance on the off → on transition, so stepping Back onto this screen with the
    // permission already granted doesn't bounce straight forward again.
    val enabledOnEntry = remember { enabled }
    var showHelp by remember { mutableStateOf(false) }

    // Once they flip it on in Settings and come back, move on automatically instead of
    // making them tap Continue for something we already know is done.
    LaunchedEffect(enabled) { if (enabled && !enabledOnEntry) onNext() }

    StepScaffold(
        stepIndex = OnboardingStep.PERMISSION.index,
        title = "One permission to make it real.",
        body = "To count reels and stop you at your limit, ReclaimLife needs Accessibility access. " +
            "Here's exactly what that means:",
        // Grant is the primary action; the button itself is the explicit consent to the
        // disclosure above it (Play's prominent-disclosure requirement for the Accessibility API).
        primaryLabel = if (enabled) "Continue" else "Agree & open Accessibility settings",
        onPrimary = {
            if (enabled) onNext() else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onBack = onBack,
        secondaryLabel = if (enabled) null else "Not now",
        onSecondary = onNext,
        extraContent = {
            AccessibilityDisclosure(modifier = Modifier.padding(bottom = 16.dp))
            if (enabled) {
                Text(
                    text = "Permission granted ✓",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text("Switch won't move?" + if (showHelp) " ▴" else " ▾")
                }
                if (showHelp) {
                    Text(
                        text = "In Accessibility, find ReclaimLife under Downloaded apps and turn it on. " +
                            "If the switch won't move: Settings → Apps → ReclaimLife → ⋮ menu → " +
                            "Allow restricted settings, then try again.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    )
}

/** Picked up front so reaching a limit leads somewhere good, not just to a stop. */
@Composable
private fun SwapStep(
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    onActivityChange: (ReplacementActivity) -> Unit,
    onDeckChange: (FlashcardDeck) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    StepScaffold(
        stepIndex = OnboardingStep.SWAP.index,
        title = "Pick your 2-minute swap.",
        body = "When you reach your limit, this is what you'll get instead of more reels. " +
            "You can change it anytime from Home.",
        primaryLabel = "Continue",
        onPrimary = onNext,
        onBack = onBack,
        extraContent = {
            SwapPicker(
                activity = activity,
                deck = deck,
                onActivityChange = onActivityChange,
                onDeckChange = onDeckChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
        }
    )
}

@Composable
private fun ReadyStep(
    nickname: String,
    activity: ReplacementActivity,
    onFinish: () -> Unit,
    onBack: (() -> Unit)?
) {
    val name = nickname.trim()
    StepScaffold(
        stepIndex = OnboardingStep.READY.index,
        title = if (name.isEmpty()) "You're all set." else "You're all set, $name.",
        body = "When you reach your limit, 2 minutes of ${activity.label.lowercase()} will be ready for " +
            "you. You can change your limits or your swap anytime from Home.",
        primaryLabel = "Let's go",
        onPrimary = onFinish,
        onBack = onBack
    )
}
