package io.github.gobi12b.reclaimlife.ui.limits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gobi12b.reclaimlife.MainViewModel
import io.github.gobi12b.reclaimlife.data.EARLIER_TODAY_KEY
import io.github.gobi12b.reclaimlife.data.Mood
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.data.limitStatus
import io.github.gobi12b.reclaimlife.ui.block.BlockActivity
import io.github.gobi12b.reclaimlife.ui.common.orderedReelCounts
import io.github.gobi12b.reclaimlife.ui.common.reelRowLabel
import io.github.gobi12b.reclaimlife.ui.common.slotColor
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Reels left, one tap from Home. Remaining leads here — "20 reels left today" — with the per-app
 * split under it and the limit and swap editors (moved from Home, unchanged, "Keep N" and all).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitsScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dailyLimit by viewModel.dailyReelLimit.collectAsStateWithLifecycle()
    val hourlyLimit by viewModel.hourlyReelLimit.collectAsStateWithLifecycle()
    val limitMode by viewModel.limitMode.collectAsStateWithLifecycle()
    val recentReelTimes by viewModel.recentReelTimes.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayReelCount.collectAsStateWithLifecycle()
    val countsByApp by viewModel.todayCountsByApp.collectAsStateWithLifecycle()
    val extraAllowance by viewModel.todayExtraAllowance.collectAsStateWithLifecycle()
    val extraAttempts by viewModel.todayExtraAttempts.collectAsStateWithLifecycle()
    val swapActivity by viewModel.replacementActivity.collectAsStateWithLifecycle()
    val flashcardDeck by viewModel.flashcardDeck.collectAsStateWithLifecycle()
    val pausedFromMs by viewModel.pausedFromMs.collectAsStateWithLifecycle()
    val pausedUntilMs by viewModel.pausedUntilMs.collectAsStateWithLifecycle()
    val trackedApps by viewModel.trackedApps.collectAsStateWithLifecycle()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    val isPaused = nowMs in pausedFromMs until pausedUntilMs
    val status = limitStatus(limitMode, dailyLimit, extraAllowance, todayCount, hourlyLimit, recentReelTimes, nowMs)

    var editDaily by remember { mutableStateOf(false) }
    var editHourly by remember { mutableStateOf(false) }
    var editSwap by remember { mutableStateOf(false) }
    var swapOnDemand by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Limits") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val resumesAt = remember(pausedUntilMs) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntilMs)) }
            val headline = when {
                isPaused -> "Paused · back at $resumesAt"
                status.hourlyUnblockAtMs != null && !status.dailyReached ->
                    "Back in ${formatPauseRemaining(status.hourlyUnblockAtMs - nowMs)}, or after a 2-minute swap"
                status.hourlyOnly -> "${status.left} reels left this hour"
                status.dailyReached -> "0 left today"
                else -> "${status.left} reels left today"
            }
            val detail = buildString {
                if (status.hourlyOnly) {
                    append("${status.used} of ${status.total} in the last 60 min · $todayCount today")
                } else {
                    append("${status.used} of ${status.total} used")
                    if (limitMode.usesHourly) append(" · hourly: ${status.thisHour} of $hourlyLimit this hour")
                }
            }
            LimitsHero(
                used = status.used,
                total = status.total,
                headline = headline,
                detail = detail,
                extraChip = if (extraAllowance > 0 && !status.hourlyOnly) "+$extraAllowance extra today" else null,
                dimmed = isPaused
            )
            if (status.blocked && !isPaused) {
                Button(onClick = { swapOnDemand = true }, modifier = Modifier.fillMaxWidth()) { Text("Start a swap") }
            }

            // The split adds up to the total: "Earlier today" carries reels from before it existed.
            val rows = orderedReelCounts(countsByApp)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Reels today by app", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    if (rows.isEmpty()) {
                        Text("No reels yet today.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    rows.forEach { (key, n) ->
                        val slot = trackedApps.firstOrNull { it.packageName == key }?.slot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) { }
                        ) {
                            if (slot != null) Box(Modifier.size(10.dp).background(slotColor(slot), CircleShape)) else Spacer(Modifier.width(10.dp))
                            Text(
                                reelRowLabel(context, key),
                                fontSize = 15.sp,
                                color = if (key == EARLIER_TODAY_KEY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp).weight(1f)
                            )
                            Text("$n", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (limitMode.usesDaily) {
                        Text(
                            "Extras used today: $extraAttempts of ${BlockActivity.MAX_EXTRA_ASKS}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    if (limitMode.usesDaily) {
                        EditRow("Daily limit", "$dailyLimit reels") { editDaily = true }
                    }
                    if (limitMode.usesHourly) {
                        if (limitMode.usesDaily) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        EditRow("Hourly limit", "$hourlyLimit reels / hour") { editHourly = true }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    EditRow("2-minute swap", swapActivity.label) { editSwap = true }
                }
            }
            Text(
                "How you limit (daily, hourly or both) is in Settings.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
            }
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
            }
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
    if (swapOnDemand) {
        SwapDialog(
            activity = swapActivity,
            deck = flashcardDeck,
            headline = "Your 2-minute swap",
            subtitle = "Something better for the next two minutes.",
            onClose = { swapOnDemand = false }
        )
    }
}

@Composable
private fun EditRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Change $title", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onClick) { Text("Edit") }
    }
}

/** The ring from the old Home hero: what's left leads, used/total and the mood beside it. */
@Composable
private fun LimitsHero(used: Int, total: Int, headline: String, detail: String, extraChip: String?, dimmed: Boolean) {
    val mood = remember(used, total) { Mood.forProgress(used, total) }
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    val start = MaterialTheme.colorScheme.primaryContainer
    val end = lerp(start, MaterialTheme.colorScheme.tertiaryContainer, 0.55f)
    val track = onColor.copy(alpha = 0.14f)
    val fill = MaterialTheme.colorScheme.primary
    val progress = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(start, end)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .alpha(if (dimmed) 0.5f else 1f)
                    .clearAndSetSemantics { contentDescription = "$used of $total reels used. Mood: ${mood.label}" }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (progress > 0f) drawArc(fill, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mood.emoji, fontSize = 28.sp)
                    Text("$used / $total", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = onColor)
                }
            }
            Column(modifier = Modifier.padding(start = 18.dp)) {
                Text(headline, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = onColor)
                Text(
                    detail,
                    fontSize = 13.sp,
                    color = onColor.copy(alpha = if (dimmed) 0.55f else 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                // The extra allowance is spelled out wherever the total appears.
                if (extraChip != null) {
                    Text(
                        text = extraChip,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
