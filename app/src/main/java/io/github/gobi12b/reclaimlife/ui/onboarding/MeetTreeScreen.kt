package io.github.gobi12b.reclaimlife.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.TREE_NAME_MAX
import io.github.gobi12b.reclaimlife.data.TreeStage
import io.github.gobi12b.reclaimlife.ui.common.Motion
import io.github.gobi12b.reclaimlife.ui.common.rememberReducedMotion
import io.github.gobi12b.reclaimlife.ui.home.GrowthTree
import io.github.gobi12b.reclaimlife.ui.home.HeroBackdrop
import io.github.gobi12b.reclaimlife.ui.home.MoonGlyph
import io.github.gobi12b.reclaimlife.ui.home.TreeSize
import io.github.gobi12b.reclaimlife.ui.theme.DisplayFamily
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import io.github.gobi12b.reclaimlife.ui.theme.reclaim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The growth path strip: always bigger left to right, so it never shows a shrink. */
private val GROWTH_PATH = listOf(TreeStage.SEED, TreeStage.SEEDLING, TreeStage.SAPLING, TreeStage.TREE, TreeStage.FULL_CANOPY)

/**
 * One screen after onboarding, shown once: what the tree is, how it grows, and an optional name.
 * Not an onboarding step (onboarding stays at six): one primary action, and back finishes it too.
 * [isUpgrade] is an existing install whose tree already grew from its history ([stage], [details]).
 */
@Composable
fun MeetTreeScreen(stage: TreeStage, details: Int, isUpgrade: Boolean, onDone: (name: String) -> Unit) {
    val colors = MaterialTheme.reclaim
    val reducedMotion = rememberReducedMotion()
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    fun finish() = onDone(trimmed)
    BackHandler { finish() }

    // A new tree's seed drops onto the hill, then grows in.
    val dropPx = with(LocalDensity.current) { 48.dp.toPx() }
    val drop = remember { Animatable(if (reducedMotion || isUpgrade) 0f else 1f) }
    LaunchedEffect(Unit) { if (drop.value > 0f) drop.animateTo(0f, tween(Motion.seedDropMs, easing = FastOutSlowInEasing)) }
    val cells = remember { GROWTH_PATH.map { Animatable(if (reducedMotion) 1f else 0f) } }
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect
        delay(400)
        cells.forEachIndexed { i, cell ->
            launch {
                delay(i * Motion.introStripStaggerMs.toLong())
                cell.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(Radii.hero))
                    .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
                    .clearAndSetSemantics { }
            ) {
                HeroBackdrop(Modifier.matchParentSize(), reducedMotion = reducedMotion)
                GrowthTree(
                    stage = if (isUpgrade) stage else TreeStage.SEED,
                    details = if (isUpgrade) details else 0,
                    resting = false,
                    reducedMotion = reducedMotion,
                    size = TreeSize.INTRO,
                    tappable = true,
                    modifier = Modifier.graphicsLayer { translationY = -dropPx * drop.value }
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Meet your tree",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily),
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isUpgrade) {
                    "It's grown from your days so far. It grows each day you stay under your limit."
                } else {
                    "It grows each day you stay under your limit."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = "Growth path: seed, seedling, sapling, tree, full canopy." }
            ) {
                GROWTH_PATH.forEachIndexed { i, pathStage ->
                    val shown = cells[i].value
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .graphicsLayer {
                                alpha = shown
                                scaleX = 0.85f + 0.15f * shown
                                scaleY = 0.85f + 0.15f * shown
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomCenter,
                            modifier = Modifier
                                .size(56.dp, 68.dp)
                                .background(colors.heroTop, RoundedCornerShape(12.dp))
                        ) {
                            GrowthTree(
                                pathStage,
                                pathStage.maxDetails,
                                resting = false,
                                reducedMotion = reducedMotion,
                                size = TreeSize.MINI,
                                animate = false
                            )
                        }
                        Text(
                            pathStage.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                MoonGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(20.dp))
                Text(
                    "It never shrinks. On off days, it rests.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(TREE_NAME_MAX) },
                label = { Text("Name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finish() }),
                supportingText = { Text("${name.length}/$TREE_NAME_MAX") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(
                    when {
                        isUpgrade && trimmed.isEmpty() -> "Let's grow"
                        isUpgrade -> "Let's grow, $trimmed"
                        trimmed.isEmpty() -> "Plant my tree"
                        else -> "Plant $trimmed"
                    },
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
