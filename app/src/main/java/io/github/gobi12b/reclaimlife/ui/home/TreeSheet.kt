package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.GrowthCode
import io.github.gobi12b.reclaimlife.data.LimitKind
import io.github.gobi12b.reclaimlife.data.LimitRaise
import io.github.gobi12b.reclaimlife.data.RestReason
import io.github.gobi12b.reclaimlife.data.SPRIGS_PER_BRANCH
import io.github.gobi12b.reclaimlife.data.TREE_NAME_MAX
import io.github.gobi12b.reclaimlife.data.TreeState
import io.github.gobi12b.reclaimlife.data.nextStreakMilestone
import io.github.gobi12b.reclaimlife.data.treeNameInline
import io.github.gobi12b.reclaimlife.data.treeNameTitle
import io.github.gobi12b.reclaimlife.ui.common.Motion
import io.github.gobi12b.reclaimlife.ui.common.rememberReducedMotion
import io.github.gobi12b.reclaimlife.ui.theme.DisplayFamily
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import io.github.gobi12b.reclaimlife.ui.theme.reclaim
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Set it back to 30", "Set it back to 20 an hour", "Switch hourly back on". */
internal fun setBackLabel(raise: LimitRaise): String = when (raise.kind) {
    LimitKind.DAILY -> "Set it back to ${raise.backTo}"
    LimitKind.HOURLY -> "Set it back to ${raise.backTo} an hour"
    LimitKind.MODE -> "Switch ${raise.droppedLimit ?: "daily"} back on"
}

/**
 * Your tree, up close: its stage, what's happening today, how far to the next stage, the last
 * week, and what helps it grow. Opened from the hero's tree or its call-out row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TreeSheet(tree: TreeState, onDismiss: () -> Unit, onRename: (String) -> Unit, onSetBack: (LimitRaise) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val colors = MaterialTheme.reclaim
    var renaming by rememberSaveable { mutableStateOf(false) }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    val resting = tree.todayRest != null

    // Scroll leftovers stay in the page: handed to the sheet, a fling back to the top dragged the
    // whole sheet down and it snapped back, which read as a jump. The handle and scrim still close it.
    val keepScrollInPage = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available.copy(x = 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available.copy(x = 0f)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .nestedScroll(keepScrollInPage)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        treeNameTitle(tree.name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily),
                        modifier = Modifier.semantics { heading() }
                    )
                    // The growth plan sits behind the stage line, so the sheet stays about today.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radii.chip))
                            .clickable(onClickLabel = "Show growth plan", role = Role.Button) { showPlan = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            "${tree.stage.label} · stage ${tree.stage.ordinal + 1} of 10",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        InfoGlyph(scheme.onSurfaceVariant, Modifier.padding(start = 6.dp).size(16.dp))
                    }
                }
                TextButton(onClick = { renaming = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (tree.name.isBlank()) "Name it" else "Rename")
                }
            }

            // 2. The tree, bigger.
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .clip(RoundedCornerShape(Radii.panel))
                    .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
                    .clearAndSetSemantics {
                        contentDescription = "Drawing of ${treeNameInline(tree.name)}, a ${tree.stage.label.lowercase()}" +
                            if (resting) ", resting under the moon" else ""
                    }
            ) {
                HeroBackdrop(Modifier.matchParentSize(), resting = resting, reducedMotion = reducedMotion)
                GrowthTree(tree.stage, tree.details, resting = resting, reducedMotion = reducedMotion, size = TreeSize.SHEET, tappable = true, keepsakes = tree.keepsakes, sprigs = tree.sprigs)
            }

            // 3. Today.
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
                    CalloutGlyphMark(tree.callout.glyph, scheme.onSurfaceVariant, Modifier.size(20.dp))
                    Text(
                        tree.callout.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                val raise = tree.raise
                if (tree.todayRest == RestReason.LIMIT_RAISED && raise != null) {
                    TextButton(onClick = { onSetBack(raise) }, modifier = Modifier.heightIn(min = 48.dp).padding(start = 20.dp)) {
                        Text(setBackLabel(raise))
                    }
                }
            }

            // 4. The streak is what grows it, so it leads. Milestones stay a surprise; they live on Profile.
            StreakCard(tree, reducedMotion)

            // 5. The last 7 days.
            Column {
                SectionHeading("Last 7 days")
                GrowthStrip(tree, Modifier.padding(top = 12.dp))
                val closed = tree.last7.filter { !it.isToday && it.code != null }
                val grew = closed.count { it.code == GrowthCode.GREW }
                Text(
                    when {
                        grew == 0 -> "Resting. Stay under your limit to grow."
                        // Today is still open, so a full strip has 6 closed days to count.
                        closed.size >= 6 -> "Grew $grew of 6 days"
                        else -> "Grew $grew of ${closed.size} days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // 6. Footer.
            tree.plantedAtMs?.let { planted ->
                val date = remember(planted) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(planted)) }
                Text(
                    "Planted $date" + if (tree.grewDays > 0) " · grew on ${tree.grewDays} " + (if (tree.grewDays == 1) "day" else "days") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPlan) {
        AlertDialog(
            onDismissRequest = { showPlan = false },
            title = { Text("Growth plan") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Each day under your limit adds a leaf. Every 3rd day, a branch.", style = MaterialTheme.typography.bodyMedium)
                    val toBranch = SPRIGS_PER_BRANCH - tree.sprigs % SPRIGS_PER_BRANCH
                    if (tree.nextStage != null) {
                        Text(
                            "${tree.sprigs} " + (if (tree.sprigs == 1) "leaf" else "leaves") + " this stage · next branch in " +
                                (if (toBranch == 1) "1 day" else "$toBranch days"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                        )
                    }
                    GrowthTimeline(tree, reducedMotion, showHeading = false)
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 16.dp)) {
                        MoonGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp))
                        Text(
                            "It never shrinks. On off days, it rests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlan = false }) { Text("Got it") } }
        )
    }

    if (renaming) {
        RenameTreeDialog(
            current = tree.name,
            onSave = {
                onRename(it)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }
}

/**
 * The streak, front and centre: every day under the limit grows the tree, and the next streak
 * length adds a boost. Dots fill as the streak builds toward it.
 */
@Composable
private fun StreakCard(tree: TreeState, reducedMotion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val goal = nextStreakMilestone(tree.streak)
    val toGo = goal - tree.streak
    // Dots count toward the next boost from the previous one, so 7 → 14 shows 7 dots, not 14.
    val from = when (goal) {
        3 -> 0
        7 -> 3
        14 -> 7
        30 -> 14
        else -> goal - 30
    }
    val span = goal - from
    val done = tree.streak - from
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer, RoundedCornerShape(Radii.panel))
            .padding(20.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (tree.streak == 0) {
                    "No streak yet. Stay under your limit today to start one."
                } else {
                    "${tree.streak}-day streak. $toGo more " + (if (toGo == 1) "day" else "days") +
                        " under your limit for a growth boost."
                }
            }
    ) {
        if (tree.streak == 0) {
            Text(
                "Start a streak",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily),
                color = scheme.onPrimaryContainer
            )
        } else Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${tree.streak}",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DisplayFamily),
                color = scheme.onPrimaryContainer
            )
            Text(
                "day streak",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        Text(
            if (tree.streak == 0) "Stay under your limit today to grow ${treeNameInline(tree.name)}."
            else "Keep it going to grow ${treeNameInline(tree.name)}.",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (span in 1..14) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth()
            ) {
                repeat(span) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(
                                if (i < done) scheme.primary else scheme.onPrimaryContainer.copy(alpha = 0.18f),
                                RoundedCornerShape(Radii.bar)
                            )
                    )
                }
            }
        }
        val surprise = tree.nextKeepsake
        if (surprise != null) {
            // Something is coming, but not what: milestones are a surprise.
            val daysLeft = (surprise.streak - tree.streak).coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                StarGlyph(scheme.onPrimaryContainer, Modifier.size(16.dp))
                Text(
                    (if (daysLeft == 1) "1 more day" else "$daysLeft more days") + " in a row for a surprise",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                StarGlyph(scheme.onPrimaryContainer, Modifier.size(16.dp))
                Text(
                    (if (toGo == 1) "1 more day" else "$toGo more days") + " for a $goal-day boost",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** "~3 days" for a timeline row. */
private fun etaText(days: Int): String = if (days <= 1) "1 day" else "~$days days"

/**
 * A vertical line of stages: the current one, a bar to the next, and the next few ahead with day
 * estimates. Growth only moves forward, so every row is a goal.
 */
@Composable
private fun GrowthTimeline(tree: TreeState, reducedMotion: Boolean, showHeading: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val ahead = tree.upcoming.take(3).let { list ->
        val last = tree.upcoming.lastOrNull()
        if (last != null && last !in list) list + last else list
    }
    Column {
        if (showHeading) SectionHeading("Growth path")
        Column(modifier = Modifier.padding(top = if (showHeading) 12.dp else 0.dp)) {
            TimelineRow(
                done = true,
                isLast = ahead.isEmpty(),
                title = tree.stage.label,
                trailing = "Now",
                description = "Now: ${tree.stage.label}"
            ) {
                val fraction = tree.fractionToNext
                if (fraction != null) {
                    val shown by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = if (reducedMotion) snap() else tween(Motion.gaugeMs),
                        label = "toNext"
                    )
                    Canvas(
                        modifier = Modifier
                            .padding(top = 6.dp, bottom = 4.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(Radii.bar))
                            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
                    ) {
                        drawRect(scheme.surfaceContainerHigh)
                        drawRect(scheme.primary, size = size.copy(width = size.width * shown))
                    }
                }
            }
            ahead.forEachIndexed { i, (stage, days) ->
                // Dashed where stages are left out between this row and the next.
                val skipped = i < ahead.lastIndex && ahead[i + 1].first.ordinal - stage.ordinal > 1
                TimelineRow(
                    done = false,
                    isLast = i == ahead.lastIndex,
                    dashed = skipped,
                    title = stage.label,
                    trailing = "in " + etaText(days),
                    description = "${stage.label} in about $days growing " + if (days == 1) "day" else "days"
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    done: Boolean,
    isLast: Boolean,
    title: String,
    trailing: String,
    description: String,
    dashed: Boolean = false,
    extra: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outlineVariant
    val dot = if (done) scheme.primary else scheme.outline
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(modifier = Modifier.width(20.dp).fillMaxHeight()) {
            val cx = size.width / 2
            val cy = 11.dp.toPx()
            val r = 6.dp.toPx()
            if (!isLast) {
                drawLine(
                    line,
                    start = androidx.compose.ui.geometry.Offset(cx, cy + r),
                    end = androidx.compose.ui.geometry.Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                )
            }
            if (done) {
                drawCircle(dot, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))
            } else {
                drawCircle(dot, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(2.dp.toPx()))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description }
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (done) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = if (done) FontWeight.SemiBold else null
                )
            }
            extra?.invoke()
        }
    }
}

@Composable
private fun GrowthStrip(tree: TreeState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val parse = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val weekday = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val resting = tree.todayRest != null
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = modifier.fillMaxWidth()) {
        tree.last7.forEach { cell ->
            val day = runCatching { parse.parse(cell.dateKey)?.let(weekday::format) }.getOrNull() ?: cell.dateKey
            val description = when {
                cell.isToday -> if (resting) "Today, resting" else "Today, growing"
                cell.code == GrowthCode.GREW -> "$day, grew"
                cell.code != null -> "$day, rested"
                else -> "$day, not planted yet"
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(40.dp)
                    .clearAndSetSemantics { contentDescription = description }
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        when {
                            cell.isToday -> {
                                val stroke = 2.5.dp.toPx()
                                drawCircle(scheme.primary, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                            }
                            cell.code == GrowthCode.GREW -> drawCircle(scheme.primary)
                            cell.code != null -> {
                                val stroke = 1.5.dp.toPx()
                                drawCircle(scheme.surfaceContainerHigh)
                                drawCircle(scheme.outline, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                            }
                            else -> {
                                val stroke = 1.5.dp.toPx()
                                val dash = 3.dp.toPx()
                                drawCircle(
                                    scheme.outline,
                                    radius = (12.dp.toPx() - stroke) / 2,
                                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                                )
                            }
                        }
                    }
                    when {
                        cell.isToday && resting -> MoonGlyph(scheme.onSurfaceVariant, Modifier.size(14.dp))
                        cell.isToday -> LeafGlyph(scheme.onSurfaceVariant, Modifier.size(14.dp))
                        cell.code == GrowthCode.GREW -> LeafGlyph(scheme.onPrimary, Modifier.size(18.dp))
                        cell.code != null -> MoonGlyph(scheme.onSurfaceVariant, Modifier.size(14.dp))
                    }
                }
                Text(
                    text = day.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (cell.isToday) FontWeight.Bold else null,
                    color = if (cell.isToday) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** Name (or rename) the tree. Blank clears the name; it's trimmed and capped at 16. */
@Composable
internal fun RenameTreeDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name your tree") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(TREE_NAME_MAX) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text.trim()) }),
                supportingText = { Text("${text.length}/$TREE_NAME_MAX") }
            )
        },
        confirmButton = { Button(onClick = { onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
