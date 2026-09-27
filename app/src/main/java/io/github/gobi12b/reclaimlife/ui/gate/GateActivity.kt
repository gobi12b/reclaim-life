package io.github.gobi12b.reclaimlife.ui.gate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import io.github.gobi12b.reclaimlife.data.formatProjection
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.gobi12b.reclaimlife.service.systemForegroundMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.TargetApps
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.usageMsInWindow
import io.github.gobi12b.reclaimlife.ui.common.OwnScreens
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme
import kotlinx.coroutines.delay

/**
 * A short pause shown by [io.github.gobi12b.reclaimlife.service.ReelBlockerAccessibilityService]
 * each time Instagram or YouTube is opened. It leads with how long that app has been open over
 * the last 24 hours and what that pace adds up to, then a couple of quiet seconds of small prompts
 * to relax, then a choice to skip it or carry on. Launched the same way as the block screen (empty taskAffinity), so finishing it
 * reveals the app underneath. The choice is reported through [OwnScreens].
 */
class GateActivity : ComponentActivity() {

    private val targetPackage: String? get() = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ReclaimLifeApp
        setContent {
            ReclaimLifeTheme {
                // Android's own record when Usage access is allowed; ReclaimLife's timing otherwise.
                val usage by produceState<GateUsage?>(initialValue = null) {
                    val target = targetPackage.orEmpty()
                    value = withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        systemForegroundMs(this@GateActivity, target, now)?.let { GateUsage(it, fromSystem = true) }
                            ?: GateUsage(
                                usageMsInWindow(app.reelUsageRepository.appUsageSpans.first(), target, now),
                                fromSystem = false
                            )
                    }
                }
                BackHandler { skip() }
                GateContent(
                    appLabel = TargetApps.labelFor(targetPackage),
                    usage = usage,
                    onSkip = { skip() },
                    onContinue = {
                        OwnScreens.recordGateDecision(targetPackage)
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        OwnScreens.onScreenStarted()
    }

    // Leaving without choosing (Home, Recents) needs no answer: the service sees the app is gone.
    // If the app itself comes back over the gate instead, the service shows the gate again.
    override fun onStop() {
        super.onStop()
        OwnScreens.onScreenStopped()
        if (!isChangingConfigurations) finish()
    }

    private fun skip() {
        OwnScreens.recordGateDecision(targetPackage)
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        /** The quiet moment before the choice appears. */
        const val PAUSE_MS = 2_000L
    }
}

/** Small, concrete ways to come back to the room — shown one after another while the gate is open. */
private val RELAX_PROMPTS = listOf(
    "Look around you.",
    "Take a few slow breaths.",
    "Find three objects you can see.",
    "Notice something blue nearby.",
    "Listen for the quietest sound.",
    "Feel your feet on the floor.",
    "Drop your shoulders.",
    "Unclench your jaw.",
    "Look out of a window, if there's one near.",
    "Notice how you're sitting."
)

/** How long each prompt stays before the next moves in. */
private const val PROMPT_HOLD_MS = 2600L

/** Where the "last 24 hours" figure came from — Android's full record, or ReclaimLife's own timing. */
private data class GateUsage(val ms: Long, val fromSystem: Boolean)

@Composable
private fun GateContent(appLabel: String, usage: GateUsage?, onSkip: () -> Unit, onContinue: () -> Unit) {
    // A shuffled run of prompts, each moving in after the last, for as long as the screen is open.
    val prompts = remember { RELAX_PROMPTS.shuffled() }
    var promptIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PROMPT_HOLD_MS)
            promptIndex = (promptIndex + 1) % prompts.size
        }
    }
    var pauseDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(GateActivity.PAUSE_MS)
        pauseDone = true
    }

    // Surface supplies the theme's text colour; the calm background is drawn over its fill.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Box(modifier = Modifier.fillMaxSize()) {
        CalmBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stats lead: what the last 24 hours add up to, before anything else.
            AnimatedVisibility(
                visible = usage != null,
                enter = fadeIn(tween(800)) + slideInVertically(tween(900, easing = LinearOutSlowInEasing)) { -it / 3 }
            ) {
                usage?.let { UsageStats(appLabel = appLabel, usage = it) }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                SwayingSprout()
                Text(
                    text = "Take a second.",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                AnimatedContent(
                    targetState = prompts[promptIndex],
                    transitionSpec = {
                        (fadeIn(tween(700, delayMillis = 150)) +
                            slideInVertically(tween(900, easing = LinearOutSlowInEasing)) { it / 2 }) togetherWith
                            (fadeOut(tween(500)) + slideOutVertically(tween(700)) { -it / 2 })
                    },
                    label = "prompt",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) { prompt ->
                    Text(
                        text = prompt,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // The choice waits out the pause, so the calm part gets its moment first.
            AnimatedVisibility(
                visible = pauseDone,
                enter = fadeIn(tween(900)) + slideInVertically(tween(900, easing = LinearOutSlowInEasing)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Still want to open $appLabel?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now")
                    }
                    OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Continue to $appLabel")
                    }
                }
            }
        }
      }
    }
}

/**
 * The last 24 hours, and what that pace adds up to over a month and a year — framed as time that
 * can be taken back, not as a telling-off. The numbers count up as they arrive.
 */
@Composable
private fun UsageStats(appLabel: String, usage: GateUsage) {
    val countUp = remember { Animatable(0f) }
    LaunchedEffect(usage.ms) { countUp.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    val shownMs = (usage.ms * countUp.value).toLong()
    val meaningful = usage.ms >= 60_000L

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .semantics(mergeDescendants = true) { }
        ) {
            Text(
                text = "Last 24 hours on $appLabel",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatUsage(shownMs),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (meaningful) {
                Text(
                    text = "At this pace, that's",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ProjectionTile(value = formatProjection(shownMs * 30), label = "a month", modifier = Modifier.weight(1f))
                    ProjectionTile(value = formatProjection(shownMs * 365), label = "a year", modifier = Modifier.weight(1f))
                }
                Text(
                    text = "Time you can take back, one skip at a time.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp)
                )
            } else {
                Text(
                    text = "A light day so far — nice.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!usage.fromSystem) {
                Text(
                    text = "Counted since ReclaimLife started timing. Allow Usage access on its Home screen for the full 24 hours.",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ProjectionTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(
            text = "≈ $value",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A slowly shifting wash of colour with a few soft specks of light drifting up through it. */
@Composable
private fun CalmBackground() {
    val surface = MaterialTheme.colorScheme.surface
    val tint = MaterialTheme.colorScheme.primary
    val speck = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "calm")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "drift"
    )
    val specks = remember { List(14) { Random(it * 7919) }.map { Triple(it.nextFloat(), it.nextFloat(), 2f + it.nextFloat() * 4f) } }

    Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics { }) {
        drawRect(
            Brush.verticalGradient(
                0f to lerp(surface, tint, 0.04f + 0.08f * shift),
                0.55f to surface,
                1f to lerp(surface, tint, 0.16f - 0.08f * shift)
            )
        )
        specks.forEachIndexed { i, (x, startY, radius) ->
            // Each speck rises at its own pace and fades in and out along the way.
            val progress = (startY + drift * (0.6f + 0.1f * (i % 4))) % 1f
            val y = size.height * (1f - progress)
            val sway = sin((progress * 2f * PI + i).toFloat()) * 18.dp.toPx()
            val alpha = sin((progress * PI).toFloat()) * 0.35f
            drawCircle(speck.copy(alpha = alpha), radius = radius.dp.toPx(), center = Offset(size.width * x + sway, y))
        }
    }
}

/** The sprout, swaying a little like it's in a breeze, with ripples spreading out slowly behind it. */
@Composable
private fun SwayingSprout() {
    val ripple = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "sprout")
    val sway by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway"
    )
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5400, easing = LinearEasing)),
        label = "wave"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val maxRadius = size.minDimension / 2f
            repeat(RIPPLES) { i ->
                val t = (wave + i / RIPPLES.toFloat()) % 1f
                drawCircle(
                    color = ripple.copy(alpha = 0.28f * (1f - t)),
                    radius = maxRadius * (0.3f + 0.7f * t),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        SproutBadge(
            size = 64.dp,
            modifier = Modifier.graphicsLayer {
                rotationZ = sway
                translationY = -6.dp.toPx() * float
            }
        )
    }
}

private const val RIPPLES = 3
