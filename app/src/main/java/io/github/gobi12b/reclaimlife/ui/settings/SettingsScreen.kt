package io.github.gobi12b.reclaimlife.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gobi12b.reclaimlife.MainViewModel
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.Baseline
import io.github.gobi12b.reclaimlife.data.DAY_MS
import io.github.gobi12b.reclaimlife.data.GATE_WAIT_OPTIONS_MS
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.MAX_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.TargetApps
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.baselineResetAvailableAt
import io.github.gobi12b.reclaimlife.data.baselineTotalMinutes
import io.github.gobi12b.reclaimlife.data.raiseTreeLine
import io.github.gobi12b.reclaimlife.service.proposeBaselineReset
import io.github.gobi12b.reclaimlife.service.usageAccessSettingsIntent
import io.github.gobi12b.reclaimlife.ui.common.AppPickerSheet
import io.github.gobi12b.reclaimlife.ui.common.PrivacyPolicyLink
import io.github.gobi12b.reclaimlife.ui.common.isInstalled
import io.github.gobi12b.reclaimlife.ui.common.rememberAppIcon
import io.github.gobi12b.reclaimlife.ui.common.rememberAppLabel
import io.github.gobi12b.reclaimlife.ui.common.rememberHasUsageAccess
import io.github.gobi12b.reclaimlife.ui.common.rememberTreeToday
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import io.github.gobi12b.reclaimlife.ui.home.Chevron
import io.github.gobi12b.reclaimlife.ui.limits.EditHourlyLimitSheet
import io.github.gobi12b.reclaimlife.ui.limits.EditLimitSheet
import io.github.gobi12b.reclaimlife.ui.limits.EditSwapSheet
import io.github.gobi12b.reclaimlife.ui.limits.SwapDialog
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Everything you set up: your apps and Pause before opening, how "time saved" is measured, and
 * your limits and swap. Loosening anything here gets the same pattern as raising a limit — a
 * confirm with "Keep it on" as the main button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as ReclaimLifeApp
    val scope = rememberCoroutineScope()
    val trackedApps by viewModel.trackedApps.collectAsStateWithLifecycle()
    val gateEnabled by viewModel.gateEnabled.collectAsStateWithLifecycle()
    val gateDisabled by viewModel.gateDisabledApps.collectAsStateWithLifecycle()
    val gateWaitMs by viewModel.gateWaitMs.collectAsStateWithLifecycle()
    val decisions by viewModel.gateDecisions.collectAsStateWithLifecycle()
    val dayStartHour by viewModel.dayStartHour.collectAsStateWithLifecycle()
    val baselines by viewModel.baselines.collectAsStateWithLifecycle()
    val baselineResetAt by viewModel.baselineResetAtMs.collectAsStateWithLifecycle()
    val limitMode by viewModel.limitMode.collectAsStateWithLifecycle()
    val dailyLimit by viewModel.dailyReelLimit.collectAsStateWithLifecycle()
    val hourlyLimit by viewModel.hourlyReelLimit.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayReelCount.collectAsStateWithLifecycle()
    val swapActivity by viewModel.replacementActivity.collectAsStateWithLifecycle()
    val flashcardDeck by viewModel.flashcardDeck.collectAsStateWithLifecycle()
    val pausedFromMs by viewModel.pausedFromMs.collectAsStateWithLifecycle()
    val pausedUntilMs by viewModel.pausedUntilMs.collectAsStateWithLifecycle()
    val hasUsageAccess = rememberHasUsageAccess()
    val isPaused = System.currentTimeMillis() in pausedFromMs until pausedUntilMs
    val treeToday = rememberTreeToday(dailyLimit, hourlyLimit, limitMode, pausedFromMs, pausedUntilMs)

    var showPicker by remember { mutableStateOf(false) }
    /** Turning off the last Pause before opening: null for the main switch, else that app. */
    var confirmGateOff by remember { mutableStateOf<String?>(null) }
    var showConfirmGateOff by remember { mutableStateOf(false) }
    var resetProposal by remember { mutableStateOf<Map<String, Baseline>?>(null) }
    var resetUnavailable by remember { mutableStateOf(false) }
    var editDaily by remember { mutableStateOf(false) }
    var editHourly by remember { mutableStateOf(false) }
    var editSwap by remember { mutableStateOf(false) }
    var tryingSwap by remember { mutableStateOf(false) }

    val appsWithGate = trackedApps.count { it.packageName !in gateDisabled }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                        Text("←", fontSize = 22.sp, modifier = Modifier.clearAndSetSemantics { })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Your apps")
            SettingsCard {
                SwitchRow(
                    title = "Pause before opening",
                    subtitle = "A short breath first.",
                    checked = gateEnabled,
                    onCheckedChange = { on ->
                        if (on) viewModel.setGateEnabled(true) else {
                            confirmGateOff = null
                            showConfirmGateOff = true
                        }
                    }
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp).alpha(if (gateEnabled) 1f else 0.5f)) {
                    Text("Wait time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GATE_WAIT_OPTIONS_MS.forEach { ms ->
                            FilterChip(
                                selected = gateWaitMs == ms,
                                enabled = gateEnabled,
                                onClick = { viewModel.setGateWaitMs(ms) },
                                label = { Text("${ms / 1000} s") }
                            )
                        }
                    }
                }
                Divider()
                trackedApps.forEach { tracked ->
                    AppGateRow(
                        app = tracked,
                        mainOn = gateEnabled,
                        checked = tracked.packageName !in gateDisabled,
                        onCheckedChange = { on ->
                            when {
                                on -> viewModel.setGateForApp(tracked.packageName, true)
                                // The last app still pausing gets the same confirm as the main
                                // switch, so it can't be sidestepped one app at a time.
                                appsWithGate <= 1 -> {
                                    confirmGateOff = tracked.packageName
                                    showConfirmGateOff = true
                                }
                                else -> viewModel.setGateForApp(tracked.packageName, false)
                            }
                        }
                    )
                }
                if (!gateEnabled) {
                    Text(
                        "Turn on to choose apps.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                TextButton(onClick = { showPicker = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Add or remove apps")
                }
            }

            SectionTitle("Time saved")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My day starts at", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Late scrolling counts toward the day before.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HourStepper(dayStartHour, viewModel::setDayStartHour)
                }
                Divider()
                val resetAvailableAt = baselineResetAvailableAt(baselineResetAt, System.currentTimeMillis())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your baseline", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                baselines.isEmpty() && !hasUsageAccess -> "Needs Usage access."
                                baselines.isEmpty() -> "Needs 3 days of phone history."
                                else -> "${baselineTotalMinutes(baselines.filterKeys { k -> trackedApps.any { it.packageName == k } }.values)} min a day" +
                                    (resetAvailableAt?.let { " · reset on ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}" } ?: "")
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when {
                        !hasUsageAccess -> TextButton(onClick = { runCatching { context.startActivity(usageAccessSettingsIntent()) } }) { Text("Allow") }
                        baselines.isNotEmpty() -> TextButton(
                            onClick = {
                                scope.launch {
                                    val proposal = proposeBaselineReset(app)
                                    if (proposal == null) resetUnavailable = true else resetProposal = proposal
                                }
                            },
                            enabled = resetAvailableAt == null
                        ) { Text("Reset") }
                    }
                }
            }

            SectionTitle("Limits and swap")
            SetupCard(
                limitMode = limitMode,
                isPaused = isPaused,
                dailyLimit = dailyLimit,
                hourlyLimit = hourlyLimit,
                swapActivity = swapActivity,
                swapDetail = if (swapActivity == ReplacementActivity.FLASHCARDS) " · ${flashcardDeck.label}" else "",
                onLimitModeChange = viewModel::updateLimitMode,
                onEditDaily = { editDaily = true },
                onLowerDailyToCap = { viewModel.updateDailyLimit(MAX_DAILY_REEL_LIMIT) },
                onEditHourly = { editHourly = true },
                onChangeSwap = { editSwap = true },
                onTrySwap = { tryingSwap = true },
                dropTreeLine = treeToday?.let { raiseTreeLine(it.name, it.rest, dropping = true) }
            )
            PrivacyPolicyLink(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (showPicker) {
        AppPickerSheet(
            current = trackedApps,
            onDismiss = { showPicker = false },
            onSave = {
                viewModel.updateTrackedApps(it)
                showPicker = false
            }
        )
    }

    if (showConfirmGateOff) {
        val weekAgo = System.currentTimeMillis() - 7 * DAY_MS
        val skipped = decisions.count { it.skipped && it.timeMs >= weekAgo }
        val target = confirmGateOff
        AlertDialog(
            onDismissRequest = { showConfirmGateOff = false },
            title = { Text("Turn off Pause before opening?") },
            text = {
                Text(
                    if (skipped > 0) {
                        "You skipped $skipped " + (if (skipped == 1) "open" else "opens") + " this week."
                    } else {
                        "It gives you a moment to choose."
                    }
                )
            },
            // "Keep it on" leads, in neutral colours — no red for loosening.
            confirmButton = { Button(onClick = { showConfirmGateOff = false }) { Text("Keep it on") } },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmGateOff = false
                    if (target == null) viewModel.setGateEnabled(false) else viewModel.setGateForApp(target, false)
                }) { Text("Turn off") }
            }
        )
    }

    resetProposal?.let { proposal ->
        val tracked = trackedApps.map { it.packageName }.toSet()
        val old = baselineTotalMinutes(baselines.filterKeys { it in tracked }.values)
        val new = baselineTotalMinutes(proposal.values)
        AlertDialog(
            onDismissRequest = { resetProposal = null },
            title = { Text("Reset your baseline?") },
            text = {
                Text(
                    "$old → $new min a day, from your last 14 days. Once every 30 days."
                )
            },
            confirmButton = { Button(onClick = { resetProposal = null }) { Text("Keep $old") } },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resetBaselines(baselines + proposal)
                    resetProposal = null
                }) { Text("Reset") }
            }
        )
    }

    if (resetUnavailable) {
        AlertDialog(
            onDismissRequest = { resetUnavailable = false },
            title = { Text("Not enough history yet") },
            text = { Text("Needs Usage access and 3 days of history.") },
            confirmButton = { Button(onClick = { resetUnavailable = false }) { Text("OK") } }
        )
    }

    if (editDaily) {
        EditLimitSheet(
            dailyLimit = dailyLimit,
            todayCount = todayCount,
            isPaused = isPaused,
            onDismiss = { editDaily = false },
            onSave = {
                viewModel.updateDailyLimit(it)
                editDaily = false
            },
            treeLine = treeToday?.let { raiseTreeLine(it.name, it.rest) }
        )
    }
    if (editHourly) {
        EditHourlyLimitSheet(
            hourlyLimit = hourlyLimit,
            isPaused = isPaused,
            onDismiss = { editHourly = false },
            onSave = {
                viewModel.updateHourlyLimit(it)
                editHourly = false
            },
            treeLine = treeToday?.let { raiseTreeLine(it.name, it.rest) }
        )
    }
    if (editSwap) {
        EditSwapSheet(
            activity = swapActivity,
            deck = flashcardDeck,
            onDismiss = { editSwap = false },
            onSave = { activity, deck ->
                viewModel.updateSwap(activity, deck)
                editSwap = false
            }
        )
    }
    if (tryingSwap) {
        SwapDialog(
            activity = swapActivity,
            deck = flashcardDeck,
            headline = "Try your swap",
            subtitle = "What you'll get at your limit.",
            onClose = { tryingSwap = false },
            onCompleted = viewModel::recordSwapCompleted
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, top = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One tracked app: icon, name with its colour dot, what's measured, and its own switch. */
@Composable
private fun AppGateRow(app: TrackedApp, mainOn: Boolean, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val label = rememberAppLabel(app.packageName)
    val icon = rememberAppIcon(app.packageName)
    val installed = remember(app.packageName) { isInstalled(context, app.packageName) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // TalkBack: "Pause before opening Instagram, on".
            .semantics(mergeDescendants = true) { contentDescription = "Pause before opening $label" }
            .toggleable(value = checked && mainOn, enabled = mainOn, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clearAndSetSemantics { }) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(slotColor(app.slot), CircleShape))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                when {
                    !installed -> "Not installed"
                    TargetApps.countsReels(app.packageName) -> "Reels and time"
                    else -> "Time only"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = mainOn)
    }
}

/** A typeable-free stepper for the hour — never a slider. */
@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onChange((hour + 23) % 24) },
            modifier = Modifier.size(40.dp).semantics { contentDescription = "Earlier" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text("−", modifier = Modifier.clearAndSetSemantics { }) }
        Text(
            "%02d:00".format(hour),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp).padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        OutlinedButton(
            onClick = { onChange((hour + 1) % 24) },
            modifier = Modifier.size(40.dp).semantics { contentDescription = "Later" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text("+", modifier = Modifier.clearAndSetSemantics { }) }
    }
}

/**
 * How to limit, the numbers, and the swap. Dropping a limit that's enforced now is a loosening,
 * so it's confirmed (with "Keep" as the main button) and not allowed while paused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupCard(
    limitMode: LimitMode,
    isPaused: Boolean,
    dailyLimit: Int,
    hourlyLimit: Int,
    swapActivity: ReplacementActivity,
    swapDetail: String,
    onLimitModeChange: (LimitMode) -> Unit,
    onEditDaily: () -> Unit,
    onLowerDailyToCap: () -> Unit,
    onEditHourly: () -> Unit,
    onChangeSwap: () -> Unit,
    onTrySwap: () -> Unit,
    /** "Fern rests for today if you switch.", or null when the tree is already resting. */
    dropTreeLine: String?
) {
    var confirmMode by remember { mutableStateOf<LimitMode?>(null) }

    SettingsCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Reel limit by", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                LimitMode.entries.forEachIndexed { index, mode ->
                    val loosens = limitMode.loosensTo(mode)
                    SegmentedButton(
                        selected = mode == limitMode,
                        onClick = {
                            when {
                                mode == limitMode -> Unit
                                loosens -> confirmMode = mode
                                else -> onLimitModeChange(mode)
                            }
                        },
                        enabled = mode == limitMode || !(isPaused && loosens),
                        shape = SegmentedButtonDefaults.itemShape(index, LimitMode.entries.size)
                    ) { Text(mode.label) }
                }
            }
            Text(
                text = when (limitMode) {
                    LimitMode.DAILY -> "One limit per day."
                    LimitMode.HOURLY -> "Resets every hour."
                    LimitMode.BOTH -> "Hourly and daily."
                } + if (isPaused && limitMode != LimitMode.BOTH) " Paused: you can only add a limit." else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (limitMode.usesDaily) {
            Divider()
            SettingRow(title = "Daily limit", value = "$dailyLimit reels", onClick = onEditDaily)
            // Limits saved above the cap (from the old 1–1000 slider) are kept, never forced
            // down — just a gentle, one-tap nudge toward the range the picker now offers.
            if (dailyLimit > MAX_DAILY_REEL_LIMIT) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
                    Text(
                        text = "Try $MAX_DAILY_REEL_LIMIT or under.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onLowerDailyToCap) { Text("Lower to $MAX_DAILY_REEL_LIMIT") }
                }
            }
        }
        if (limitMode.usesHourly) {
            Divider()
            SettingRow(title = "Hourly limit", value = "$hourlyLimit reels / hour", onClick = onEditHourly)
        }
        Divider()
        SettingRow(
            title = "Swap",
            value = "${swapActivity.emoji} ${swapActivity.label}$swapDetail",
            onClick = onChangeSwap,
            trailing = { TextButton(onClick = onTrySwap) { Text("Try it") } }
        )
    }

    confirmMode?.let { target ->
        val dropped = if (limitMode.usesDaily && !target.usesDaily) "daily" else "hourly"
        AlertDialog(
            onDismissRequest = { confirmMode = null },
            title = { Text("Drop the $dropped limit?") },
            text = {
                Column {
                    Text(
                        if (dropped == "daily") {
                            "Hours can add up without it."
                        } else {
                            "It keeps one sitting short."
                        }
                    )
                    dropTreeLine?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
                }
            },
            confirmButton = {
                Button(onClick = { confirmMode = null }) { Text("Keep ${limitMode.label.lowercase()}") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmMode = null
                    onLimitModeChange(target)
                }) {
                    Text("Switch")
                }
            }
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Change $title", onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        if (trailing != null) {
            trailing()
        } else {
            Chevron(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
