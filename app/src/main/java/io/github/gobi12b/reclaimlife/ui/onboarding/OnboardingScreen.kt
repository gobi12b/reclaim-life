package io.github.gobi12b.reclaimlife.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.DEFAULT_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.DEFAULT_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.MAX_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.MAX_TRACKED_APPS
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.TargetApps
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.parseTrackedApps
import io.github.gobi12b.reclaimlife.data.preselectedApps
import io.github.gobi12b.reclaimlife.data.serializeTrackedApps
import io.github.gobi12b.reclaimlife.data.toggleTrackedApps
import io.github.gobi12b.reclaimlife.service.AccessibilityStatus
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.AccessibilityDisclosure
import io.github.gobi12b.reclaimlife.ui.common.AppPickerSheet
import io.github.gobi12b.reclaimlife.ui.common.CompactBrandMark
import io.github.gobi12b.reclaimlife.ui.common.HOURLY_LIMIT_PRESETS
import io.github.gobi12b.reclaimlife.ui.common.LimitPicker
import io.github.gobi12b.reclaimlife.ui.common.OptionCard
import io.github.gobi12b.reclaimlife.ui.common.SuggestedRow
import io.github.gobi12b.reclaimlife.ui.common.SwapPicker
import io.github.gobi12b.reclaimlife.ui.common.installedSuggestedApps
import io.github.gobi12b.reclaimlife.ui.common.rememberAccessibilityStatus
import io.github.gobi12b.reclaimlife.ui.common.rememberAppIcon
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.rememberHasUsageAccess
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Six steps — principle #17 already calls six long, so choosing apps came in by merging the limit
 * mode into the limit step (hourly sits under "More options"). The name lives on the welcome
 * screen rather than its own step.
 */
private enum class OnboardingStep(val index: Int) {
    WELCOME(0), APPS(1), LIMIT(2), SWAP(3), PERMISSION(4), READY(5)
}
private val STEP_COUNT = OnboardingStep.entries.size

/** Everything chosen during setup. */
data class OnboardingSetup(
    val limitMode: LimitMode,
    val dailyLimit: Int,
    val hourlyLimit: Int,
    val nickname: String,
    val activity: ReplacementActivity,
    val deck: FlashcardDeck,
    val apps: List<TrackedApp>,
    val gateEnabled: Boolean
)

@Composable
fun OnboardingFlow(onComplete: (OnboardingSetup) -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var limitMode by rememberSaveable { mutableStateOf(LimitMode.DAILY) }
    var dailyLimit by rememberSaveable { mutableIntStateOf(DEFAULT_DAILY_REEL_LIMIT) }
    var hourlyLimit by rememberSaveable { mutableIntStateOf(DEFAULT_HOURLY_REEL_LIMIT) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var swapActivity by rememberSaveable { mutableStateOf(ReplacementActivity.BREATHING) }
    var flashcardDeck by rememberSaveable { mutableStateOf(FlashcardDeck.CAPITALS) }
    // Null until the preselection runs once, so going Back and forth keeps what was picked.
    var appsRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var gateEnabled by rememberSaveable { mutableStateOf(true) }
    val apps = appsRaw?.let { parseTrackedApps(it) }.orEmpty()

    // System back walks back through the steps; on the first step it falls through and exits.
    val previous = OnboardingStep.entries.getOrNull(step.index - 1)
    val goBack: (() -> Unit)? = previous?.let { { step = it } }
    BackHandler(enabled = previous != null) { goBack?.invoke() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                nickname = nickname,
                onNicknameChange = { nickname = it },
                onNext = { step = OnboardingStep.APPS }
            )
            OnboardingStep.APPS -> AppsStep(
                apps = apps,
                initialized = appsRaw != null,
                onInitialize = { appsRaw = serializeTrackedApps(it) },
                onAppsChange = { appsRaw = serializeTrackedApps(it) },
                gateEnabled = gateEnabled,
                onGateChange = { gateEnabled = it },
                onNext = { step = OnboardingStep.LIMIT },
                onBack = goBack
            )
            OnboardingStep.LIMIT -> LimitStep(
                limitMode = limitMode,
                onModeChange = { limitMode = it },
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
                onFinish = {
                    onComplete(
                        OnboardingSetup(limitMode, dailyLimit, hourlyLimit, nickname, swapActivity, flashcardDeck, apps, gateEnabled)
                    )
                },
                onBack = goBack
            )
        }
    }
}

@Composable
private fun StepProgress(stepIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.semantics { stateDescription = "Step ${stepIndex + 1} of $STEP_COUNT" }) {
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
    primaryEnabled: Boolean = true,
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
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth()) {
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
 * Which apps to help with. Installed apps from the suggested list come first (Instagram and
 * YouTube preselected); "Other apps" opens the full picker. One primary action: Continue. The
 * Pause before opening switch is a clearly secondary row under the list.
 */
@Composable
private fun AppsStep(
    apps: List<TrackedApp>,
    initialized: Boolean,
    onInitialize: (List<TrackedApp>) -> Unit,
    onAppsChange: (List<TrackedApp>) -> Unit,
    gateEnabled: Boolean,
    onGateChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    val suggested by produceState<List<SuggestedRow>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { installedSuggestedApps(context) }
    }
    LaunchedEffect(suggested) {
        val rows = suggested ?: return@LaunchedEffect
        if (!initialized) onInitialize(preselectedApps(rows.flatMap { it.packages }.toSet()))
    }
    var showPicker by remember { mutableStateOf(false) }
    val full = apps.size >= MAX_TRACKED_APPS
    val chosen = apps.map { it.packageName }.toSet()
    // Apps added from "Other apps" get their own rows, so they can be unticked here too.
    val suggestedPackages = suggested.orEmpty().flatMap { it.packages }.toSet()
    val otherRows = apps.filter { it.packageName !in suggestedPackages }.map { SuggestedRow("", listOf(it.packageName)) }

    StepScaffold(
        stepIndex = OnboardingStep.APPS.index,
        title = "Which apps pull you in?",
        body = "Pick the ones you'd like a little help with. You can change this anytime.",
        primaryLabel = "Continue",
        primaryEnabled = apps.isNotEmpty(),
        onPrimary = onNext,
        onBack = onBack,
        extraContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                val rows = suggested
                if (rows == null) {
                    Text("Looking for your apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (rows.isEmpty()) {
                        OtherAppsRow(filled = true, onClick = { showPicker = true })
                    }
                    (rows + otherRows).forEach { row ->
                        val checked = row.packages.any { it in chosen }
                        OnboardingAppRow(
                            row = row,
                            slot = apps.firstOrNull { it.packageName in row.packages }?.slot,
                            checked = checked,
                            enabled = checked || apps.size + row.packages.size <= MAX_TRACKED_APPS,
                            onToggle = { onAppsChange(toggleTrackedApps(apps, row.packages)) }
                        )
                    }
                    if (rows.isNotEmpty()) OtherAppsRow(filled = false, onClick = { showPicker = true })
                }
                val helper = when {
                    apps.isEmpty() -> "Pick at least one app to continue."
                    full -> "8 is the most, untick one to swap."
                    else -> null
                }
                if (helper != null) {
                    Text(helper, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .toggleable(value = gateEnabled, role = Role.Switch, onValueChange = onGateChange)
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Take a short breath before opening these apps", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "A 2-second pause and a look at your day. You can turn it off anytime.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = gateEnabled, onCheckedChange = null)
                }
            }
        }
    )

    if (showPicker) {
        AppPickerSheet(
            current = apps,
            onDismiss = { showPicker = false },
            onSave = {
                onAppsChange(it)
                showPicker = false
            }
        )
    }
}

/** TalkBack reads each row as "Instagram, reels and time, checked". */
@Composable
private fun OnboardingAppRow(row: SuggestedRow, slot: Int?, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val pkg = row.packages.first()
    val icon = rememberAppIcon(pkg)
    val label = row.label.ifEmpty { rememberAppLabel(pkg) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clearAndSetSemantics { }) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                // The chart colour it'll have everywhere, always beside the name, never alone.
                if (slot != null) {
                    Box(Modifier.padding(start = 8.dp).size(8.dp).background(slotColor(slot), CircleShape).clearAndSetSemantics { })
                }
            }
            Text(
                if (row.packages.any { TargetApps.countsReels(it) }) "Reels and time" else "Time only",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun OtherAppsRow(filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (filled) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (filled) 16.dp else 0.dp, vertical = 12.dp)
    ) {
        Text("Other apps ›", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (filled) {
            Text(
                "None of the usual apps are here. Add the ones you use.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * The limit, with Daily preselected. Hourly and Both sit under "More options" — an expandable row
 * with a chevron and an expanded/collapsed state, not a text link — so most people need one tap.
 */
@Composable
private fun LimitStep(
    limitMode: LimitMode,
    onModeChange: (LimitMode) -> Unit,
    dailyLimit: Int,
    onLimitChange: (Int) -> Unit,
    hourlyLimit: Int,
    onHourlyLimitChange: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: (() -> Unit)?
) {
    var moreOptions by rememberSaveable { mutableStateOf(limitMode != LimitMode.DAILY) }
    StepScaffold(
        stepIndex = OnboardingStep.LIMIT.index,
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
                LimitPicker(value = dailyLimit, onValueChange = onLimitChange, modifier = Modifier.padding(bottom = 16.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (moreOptions) "Collapse" else "Expand") { moreOptions = !moreOptions }
                    .semantics { stateDescription = if (moreOptions) "Expanded" else "Collapsed" }
                    .padding(vertical = 12.dp)
            ) {
                Text("More options", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (moreOptions) "▴" else "▾", fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { })
            }
            AnimatedVisibility(visible = moreOptions) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
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
                    if (limitMode.usesHourly) {
                        LimitPicker(
                            value = hourlyLimit,
                            onValueChange = onHourlyLimitChange,
                            ceiling = MAX_HOURLY_REEL_LIMIT,
                            presets = HOURLY_LIMIT_PRESETS,
                            unitLabel = "reels / hour",
                            fieldLabel = "Hourly limit"
                        )
                    }
                    // Not blocked — just said, so nobody sets up a limit that silently never does anything.
                    if (limitMode == LimitMode.BOTH && hourlyLimit >= dailyLimit) {
                        Text(
                            text = "Your hourly limit is at or above your daily one, so it'll never kick in.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    )
}

/**
 * Accessibility first (it's what counts and pauses), then Usage access, offered as what unlocks
 * time saved. Usage access stays skippable. Both live in this one step.
 */
@Composable
private fun PermissionStep(onNext: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val status = rememberAccessibilityStatus()
    val enabled = status != AccessibilityStatus.OFF
    val hasUsageAccess = rememberHasUsageAccess()
    // Only auto-advance on the off → on transition, so stepping Back onto this screen with a
    // permission already granted doesn't bounce straight forward again.
    val enabledOnEntry = remember { enabled }
    val usageOnEntry = remember { hasUsageAccess }
    var accessibilityDone by rememberSaveable { mutableStateOf(enabled) }
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) { if (enabled && !enabledOnEntry) accessibilityDone = true }
    LaunchedEffect(hasUsageAccess) { if (hasUsageAccess && !usageOnEntry && accessibilityDone) onNext() }

    if (!accessibilityDone) {
        StepScaffold(
            stepIndex = OnboardingStep.PERMISSION.index,
            title = "One permission to make it real.",
            body = "To count reels and give you a moment before opening your apps, ReclaimLife needs " +
                "Accessibility access. Here's exactly what that means:",
            // Grant is the primary action; the button itself is the explicit consent to the
            // disclosure above it (Play's prominent-disclosure requirement for the Accessibility API).
            primaryLabel = "Agree & open Accessibility settings",
            onPrimary = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onBack = onBack,
            secondaryLabel = "Not now",
            onSecondary = { accessibilityDone = true },
            extraContent = {
                AccessibilityDisclosure(modifier = Modifier.padding(bottom = 16.dp))
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
        )
    } else {
        StepScaffold(
            stepIndex = OnboardingStep.PERMISSION.index,
            title = "See how much time you win back.",
            body = "With Usage access, ReclaimLife reads how long your apps were open over the last two " +
                "weeks, so it can show the time you save from here on. It only looks at the apps you " +
                "chose, and nothing leaves your phone.",
            primaryLabel = if (hasUsageAccess) "Continue" else "Allow Usage access",
            onPrimary = { if (hasUsageAccess) onNext() else runCatching { context.startActivity(usageAccessSettingsIntent()) } },
            onBack = { if (enabled) onBack?.invoke() else accessibilityDone = false },
            secondaryLabel = if (hasUsageAccess) null else "Not now",
            onSecondary = onNext,
            extraContent = {
                Text(
                    text = if (enabled) "Accessibility access is on ✓" else "Accessibility is off for now — you can turn it on from Home.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        )
    }
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
            "You can change it anytime from Settings.",
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
            "you. Home shows the time you win back; your apps, limits and swap are in Settings.",
        primaryLabel = "Let's go",
        onPrimary = onFinish,
        onBack = onBack
    )
}
