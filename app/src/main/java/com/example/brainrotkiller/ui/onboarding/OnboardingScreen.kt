package com.example.brainrotkiller.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.brainrotkiller.data.DEFAULT_DAILY_REEL_LIMIT
import com.example.brainrotkiller.data.MIN_DAILY_REEL_LIMIT
import com.example.brainrotkiller.service.isReelBlockerServiceEnabled
import com.example.brainrotkiller.ui.common.BrandMark

private enum class OnboardingStep(val index: Int) {
    WELCOME(0), NICKNAME(1), SET_LIMIT(2), ACCOUNTABILITY(3), PERMISSION(4), READY(5)
}
private const val STEP_COUNT = 6

@Composable
fun OnboardingFlow(onComplete: (dailyLimit: Int, nickname: String) -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var dailyLimit by rememberSaveable { mutableIntStateOf(DEFAULT_DAILY_REEL_LIMIT) }
    var nickname by rememberSaveable { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onNext = { step = OnboardingStep.NICKNAME })
            OnboardingStep.NICKNAME -> NicknameStep(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                onNext = { step = OnboardingStep.SET_LIMIT }
            )
            OnboardingStep.SET_LIMIT -> SetLimitStep(
                dailyLimit = dailyLimit,
                onLimitChange = { dailyLimit = it },
                onNext = { step = OnboardingStep.ACCOUNTABILITY }
            )
            OnboardingStep.ACCOUNTABILITY -> AccountabilityStep(onNext = { step = OnboardingStep.PERMISSION })
            OnboardingStep.PERMISSION -> PermissionStep(onNext = { step = OnboardingStep.READY })
            OnboardingStep.READY -> ReadyStep(nickname = nickname, onFinish = { onComplete(dailyLimit, nickname) })
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
    extraContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BrandMark(modifier = Modifier.padding(bottom = 12.dp))
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
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepScaffold(
        stepIndex = OnboardingStep.WELCOME.index,
        title = "Stop the brain rot.",
        body = "ReclaimLife exists for one reason: to help you start living again — not living on the algorithm's terms. " +
            "You're about to set your own rules for how much of your day the feed gets to take.",
        primaryLabel = "Let's set it up",
        onPrimary = onNext
    )
}

@Composable
private fun NicknameStep(nickname: String, onNicknameChange: (String) -> Unit, onNext: () -> Unit) {
    StepScaffold(
        stepIndex = OnboardingStep.NICKNAME.index,
        title = "What should we call you?",
        body = "Just a first name or nickname — ReclaimLife will use it when it talks to you, " +
            "instead of talking at you like every other app.",
        primaryLabel = "Continue",
        onPrimary = onNext,
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
private fun SetLimitStep(dailyLimit: Int, onLimitChange: (Int) -> Unit, onNext: () -> Unit) {
    StepScaffold(
        stepIndex = OnboardingStep.SET_LIMIT.index,
        title = "How many reels a day?",
        body = "Be liberal with this number — pick something you can actually stick to today. " +
            "You don't have to fix everything on day one. You can bring it down day by day as it gets easier.",
        primaryLabel = "Continue",
        onPrimary = onNext,
        extraContent = {
            Text(
                text = "$dailyLimit reels / day",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = dailyLimit.toFloat(),
                onValueChange = { onLimitChange(it.toInt()) },
                valueRange = MIN_DAILY_REEL_LIMIT.toFloat()..150f,
                steps = 148,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
        }
    )
}

@Composable
private fun AccountabilityStep(onNext: () -> Unit) {
    StepScaffold(
        stepIndex = OnboardingStep.ACCOUNTABILITY.index,
        title = "Accountability is everything.",
        body = "This only works if you're honest with yourself. The number you picked isn't a target — " +
            "it's a promise. When ReclaimLife says you've hit your limit, that's you, being true to your " +
            "own life, not to the algorithm's.",
        primaryLabel = "I understand",
        onPrimary = onNext
    )
}

@Composable
private fun PermissionStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val enabled = remember(resumeTick) { isReelBlockerServiceEnabled(context) }

    // Once they flip it on in Settings and come back, move on automatically instead of
    // making them tap Continue for something we already know is done.
    LaunchedEffect(enabled) { if (enabled) onNext() }

    StepScaffold(
        stepIndex = OnboardingStep.PERMISSION.index,
        title = "One permission to make it real.",
        body = "To actually count reels and stop you when you hit your limit, ReclaimLife needs " +
            "Accessibility access. It only ever looks at Instagram and YouTube to count scrolling — nothing else.",
        primaryLabel = if (enabled) "Continue" else "Skip for now",
        onPrimary = onNext,
        extraContent = {
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(if (enabled) "Permission granted ✓" else "Open Accessibility settings")
            }
            if (!enabled) {
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
    )
}

@Composable
private fun ReadyStep(nickname: String, onFinish: () -> Unit) {
    val name = nickname.trim()
    StepScaffold(
        stepIndex = OnboardingStep.READY.index,
        title = if (name.isEmpty()) "Go live your life." else "Go live your life, $name.",
        body = "You're set up. From here it's on you — be true to life, not to the feed. " +
            "You can always adjust your daily limit later, but the goal is fewer reels, not more excuses.",
        primaryLabel = "Start living",
        onPrimary = onFinish
    )
}
