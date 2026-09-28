package io.github.gobi12b.reclaimlife.ui.gate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.DEFAULT_GATE_WAIT_MS
import io.github.gobi12b.reclaimlife.data.TreeState
import io.github.gobi12b.reclaimlife.data.formatProjection
import io.github.gobi12b.reclaimlife.data.formatUsage
import io.github.gobi12b.reclaimlife.data.treeNameInline
import io.github.gobi12b.reclaimlife.data.usageMsInWindow
import io.github.gobi12b.reclaimlife.service.buildTreeState
import io.github.gobi12b.reclaimlife.service.systemForegroundMs
import io.github.gobi12b.reclaimlife.ui.common.OwnScreens
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.common.appLabel
import io.github.gobi12b.reclaimlife.ui.common.rememberReducedMotion
import io.github.gobi12b.reclaimlife.ui.home.TreeScene
import io.github.gobi12b.reclaimlife.ui.theme.DisplayFamily
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                val tree by produceState<TreeState?>(initialValue = null) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { buildTreeState(app, System.currentTimeMillis(), hasBaseline = false) }.getOrNull()
                    }
                }
                BackHandler { skip() }
                GateContent(
                    appLabel = targetPackage?.let { appLabel(this@GateActivity, it) } ?: "this app",
                    usage = usage,
                    tree = tree,
                    waitMs = intent.getLongExtra(EXTRA_WAIT_MS, DEFAULT_GATE_WAIT_MS),
                    onSkip = { skip() },
                    onContinue = {
                        OwnScreens.recordGateDecision(targetPackage)
                        recordForInsights(skipped = false)
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

    /** Kept for the Insights page; runs on the app-wide scope so finishing doesn't cancel it. */
    private fun recordForInsights(skipped: Boolean) {
        val app = application as ReclaimLifeApp
        app.appScope.launch { app.reelUsageRepository.recordGateDecision(skipped) }
    }

    private fun skip() {
        OwnScreens.recordGateDecision(targetPackage)
        recordForInsights(skipped = true)
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        /** The quiet moment before the choice appears — 2, 5 or 10 seconds, set in Settings. */
        const val EXTRA_WAIT_MS = "wait_ms"
    }
}

/** Where the "last 24 hours" figure came from — Android's full record, or ReclaimLife's own timing. */
private data class GateUsage(val ms: Long, val fromSystem: Boolean)

@Composable
private fun GateContent(
    appLabel: String,
    usage: GateUsage?,
    tree: TreeState?,
    waitMs: Long,
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    // The tree droops a little while you decide, and perks up if you skip. Continuing adds nothing.
    var perked by remember { mutableStateOf(false) }
    // Starts upright, then sighs down a moment later, so the change is seen rather than just there.
    var drooping by remember { mutableStateOf(false) }
    LaunchedEffect(tree != null) {
        if (tree != null) {
            delay(900)
            drooping = true
        }
    }
    val scope = rememberCoroutineScope()
    val skipWithPerk: () -> Unit = {
        if (tree == null || perked) {
            onSkip()
        } else {
            perked = true
            scope.launch {
                delay(700)
                onSkip()
            }
        }
    }
    var pauseDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(waitMs)
        pauseDone = true
    }

    val buttonsAlpha by animateFloatAsState(
        targetValue = if (pauseDone) 1f else 0f,
        animationSpec = tween(900),
        label = "choice"
    )

    // One job: decide. Tree, question, the numbers in a sentence, then the choice. The choice fades
    // in over reserved space after the pause, so nothing above it moves.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Box(modifier = Modifier.fillMaxSize()) {
        CalmBackground()
        // Scrolls on small screens; the min height lets the spacers centre it on normal ones.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            if (tree != null) {
                TreeScene(tree, line = null, reducedMotion = rememberReducedMotion(), droop = if (drooping && !perked) 1f else 0f)
            } else {
                SwayingSprout()
            }

            Text(
                text = "Open $appLabel?",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DisplayFamily),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )
            usage?.let { UsageSentence(appLabel, it, Modifier.padding(top = 12.dp)) }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 32.dp)
                    .graphicsLayer { alpha = buttonsAlpha }
            ) {
                if (tree != null) {
                    Text(
                        text = "Skip, and ${treeNameInline(tree.name)} keeps growing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Button(
                    onClick = skipWithPerk,
                    enabled = pauseDone,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text("Skip")
                }
                TextButton(
                    onClick = onContinue,
                    enabled = pauseDone,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp)
                ) {
                    Text("Open $appLabel")
                }
            }
        }
        }
      }
    }
}

/**
 * The numbers as one plain sentence, figures in bold: the last 24 hours in this app, and what
 * that pace adds up to over a month and a year.
 */
@Composable
private fun UsageSentence(appLabel: String, usage: GateUsage, modifier: Modifier = Modifier) {
    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val text = buildAnnotatedString {
        if (usage.ms < 60_000L) {
            append("Light day on $appLabel so far.")
        } else {
            withStyle(strong) { append(formatUsage(usage.ms)) }
            append(" here in the last 24 hours.\nAt this pace, that's ")
            withStyle(strong) { append(formatProjection(usage.ms * 30)) }
            append(" a month and ")
            withStyle(strong) { append(formatProjection(usage.ms * 365)) }
            append(" a year.")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

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

/** The sprout, swaying a little like it's in a breeze, with ripples spreading out slowly behind it. Also the pause's calm screen. */
@Composable
internal fun SwayingSprout() {
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
