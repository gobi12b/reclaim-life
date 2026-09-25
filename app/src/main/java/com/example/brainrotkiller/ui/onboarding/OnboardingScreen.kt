package com.example.brainrotkiller.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.example.brainrotkiller.data.DEFAULT_DAILY_REEL_LIMIT
import com.example.brainrotkiller.service.AccessibilityStatus
import com.example.brainrotkiller.ui.common.CompactBrandMark
import com.example.brainrotkiller.ui.common.LimitPicker
import com.example.brainrotkiller.ui.common.rememberAccessibilityStatus

/** Welcome and the accountability pledge are one step: the pledge is the welcome. */
private enum class OnboardingStep(val index: Int) {
    WELCOME(0), NICKNAME(1), SET_LIMIT(2), PERMISSION(3), READY(4)
}
private val STEP_COUNT = OnboardingStep.entries.size

@Composable
fun OnboardingFlow(onComplete: (dailyLimit: Int, nickname: String) -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var dailyLimit by rememberSaveable { mutableIntStateOf(DEFAULT_DAILY_REEL_LIMIT) }
    var nickname by rememberSaveable { mutableStateOf("") }

    // System back walks back through the steps; on the first step it falls through and exits.
    val previous = OnboardingStep.entries.getOrNull(step.index - 1)
    val goBack: (() -> Unit)? = previous?.let { { step = it } }
    BackHandler(enabled = previous != null) { goBack?.invoke() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onNext = { step = OnboardingStep.NICKNAME })
            OnboardingStep.NICKNAME -> NicknameStep(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                onNext = { step = OnboardingStep.SET_LIMIT },
                onBack = goBack
            )
            OnboardingStep.SET_LIMIT -> SetLimitStep(
                dailyLimit = dailyLimit,
                onLimitChange = { dailyLimit = it },
                onNext = { step = OnboardingStep.PERMISSION },
                onBack = goBack
            )
            OnboardingStep.PERMISSION -> PermissionStep(onNext = { step = OnboardingStep.READY }, onBack = goBack)
            OnboardingStep.READY -> ReadyStep(
                nickname = nickname,
                onFinish = { onComplete(dailyLimit, nickname) },
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
private fun WelcomeStep(onNext: () -> Unit) {
    StepScaffold(
        stepIndex = OnboardingStep.WELCOME.index,
        title = "Stop the brain rot.",
        body = "ReclaimLife helps you start living again — on your terms, not the algorithm's. " +
            "It only works if you're honest with yourself: the number you're about to pick isn't a " +
            "target, it's a promise. When ReclaimLife says you've hit it, that's you keeping your word.",
        primaryLabel = "I understand — let's set it up",
        onPrimary = onNext
    )
}

@Composable
private fun NicknameStep(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    StepScaffold(
        stepIndex = OnboardingStep.NICKNAME.index,
        title = "What should we call you?",
        body = "Just a first name or nickname — ReclaimLife will use it when it talks to you, " +
            "instead of talking at you like every other app.",
        primaryLabel = if (nickname.isBlank()) "Skip" else "Continue",
        onPrimary = onNext,
        onBack = onBack,
        extraContent = {
            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 20) onNicknameChange(it) },
                placeholder = { Text("Your name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    )
}

@Composable
private fun SetLimitStep(
    dailyLimit: Int,
    onLimitChange: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    StepScaffold(
        stepIndex = OnboardingStep.SET_LIMIT.index,
        title = "How many reels a day?",
        body = "Be liberal with this number — pick something you can actually stick to today. " +
            "You don't have to fix everything on day one. You can bring it down day by day as it gets easier.",
        primaryLabel = "Continue",
        onPrimary = onNext,
        onBack = onBack,
        extraContent = {
            LimitPicker(
                value = dailyLimit,
                onValueChange = onLimitChange,
                modifier = Modifier.padding(bottom = 32.dp)
            )
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

// TODO(policy): needs Play Accessibility API policy / legal review before release
/**
 * Prominent disclosure for the Accessibility API. Every claim here must stay true of the code:
 * the service only acts on Instagram/YouTube events, stores nothing but a daily count, and the
 * app has no INTERNET permission at all.
 */
@Composable
private fun AccessibilityDisclosure(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DisclosureItem(
                title = "What it reads",
                body = "Scrolling and screen layout inside Instagram and YouTube only — enough to " +
                    "tell a reel swipe from normal browsing. Events from other apps are ignored, " +
                    "apart from noticing you've left, to hide the counter."
            )
            DisclosureItem(
                title = "What it never reads",
                body = "Your messages, what you type, passwords, or what's in the reels. " +
                    "No screen content is saved — only today's count."
            )
            DisclosureItem(
                title = "Where it goes",
                body = "Nowhere. ReclaimLife has no internet access; everything stays on this phone.",
                last = true
            )
        }
    }
}

@Composable
private fun DisclosureItem(title: String, body: String, last: Boolean = false) {
    Column(modifier = Modifier.padding(bottom = if (last) 0.dp else 12.dp)) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ReadyStep(nickname: String, onFinish: () -> Unit, onBack: (() -> Unit)?) {
    val name = nickname.trim()
    StepScaffold(
        stepIndex = OnboardingStep.READY.index,
        title = if (name.isEmpty()) "Go live your life." else "Go live your life, $name.",
        body = "You're set up. From here it's on you — be true to life, not to the feed. " +
            "You can always adjust your daily limit later, but the goal is fewer reels, not more excuses.",
        primaryLabel = "Start living",
        onPrimary = onFinish,
        onBack = onBack
    )
}
