package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.Keepsake
import io.github.gobi12b.reclaimlife.data.MAX_SPRIGS
import io.github.gobi12b.reclaimlife.data.SPRIGS_PER_BRANCH
import io.github.gobi12b.reclaimlife.data.TreeStage
import io.github.gobi12b.reclaimlife.ui.common.Motion
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimColors
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme
import io.github.gobi12b.reclaimlife.ui.theme.reclaim
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Where the tree is drawn. The canvas keeps the 112×148 unit ratio at every size. */
internal enum class TreeSize(val width: Dp, val height: Dp) {
    HERO(136.dp, 180.dp),
    HERO_COMPACT(104.dp, 138.dp),
    SHEET(168.dp, 222.dp),
    INTRO(168.dp, 222.dp),
    MINI(40.dp, 52.dp)
}

/**
 * The last tree state that grew on screen. Process-level on purpose: the tree grows in once per
 * visit (a cold start is a fresh visit), and again from the previous stage when it reaches a new one.
 */
private var lastAnimatedTree: Pair<TreeStage, Int>? = null

/** The ledger day whose celebration already played, so it plays once per process. */
private var celebratedKey: String? = null

private enum class TreeAnim { NONE, GROW, STAGE_UP, DETAILS }

private const val LIFE_CYCLE_MS = 6000
private const val POLLEN_COUNT = 4
private const val PI_F = Math.PI.toFloat()
private val DryLeaf = Color(0xFFB5A77A)

// Where daily growth goes on a plant: (position along the stem, side). Leaves sit low, clear of
// the stage's own leaves; branches alternate higher up.
private val SPRIG_SPOTS = listOf(
    0.18f to -1f, 0.24f to 1f, 0.34f to -1f, 0.42f to 1f, 0.5f to -1f, 0.6f to 1f,
    0.66f to -1f, 0.74f to 1f, 0.82f to -1f, 0.88f to 1f, 0.28f to -1f, 0.46f to 1f
)
private val BRANCH_SPOTS = listOf(0.36f to 1f, 0.52f to -1f, 0.68f to 1f, 0.8f to -1f)

// Trees: (height up the trunk as a fraction, direction) for each twig.
private val TWIG_SPOTS = listOf(0.35f to -1f, 0.42f to 1f, 0.5f to -1f, 0.56f to 1f)
private val WaterBlue = Color(0xFF7CC4E8)

/** Sun glow and hill behind the hero's text. Resting dims the sun and shows a moon. Decorative. */
@Composable
internal fun HeroBackdrop(modifier: Modifier = Modifier, resting: Boolean = false, reducedMotion: Boolean = false) {
    val colors = MaterialTheme.reclaim
    // Warm and visible on the light panel, only a hint in the dark.
    val glowAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.18f else 0.70f
    val rest by animateFloatAsState(
        targetValue = if (resting) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(Motion.fadeMs),
        label = "rest"
    )
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val radius = 88.dp.toPx()
        val center = Offset(w - 72.dp.toPx(), 44.dp.toPx())
        val alpha = glowAlpha * (1f - 0.6f * rest)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.sunGlow.copy(alpha = alpha), colors.sunGlow.copy(alpha = 0f)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        if (rest > 0f) drawPath(crescentPath(center, 11.dp.toPx()), colors.restMoon.copy(alpha = rest))
        val hill = Path().apply {
            moveTo(0f, h - 30.dp.toPx())
            quadraticTo(w * 0.55f, h - 64.dp.toPx(), w, h - 40.dp.toPx())
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hill, colors.heroGround)
    }
}

/**
 * Your tree at [stage] with [details] of its own. Decorative: the words beside it carry the
 * meaning. It grows in once per visit, from the previous stage (never from empty) when it moves
 * up, and new details scale in one by one. Resting draws exactly the same tree, only still.
 * [celebrate] sends a few leaves up once per [celebrateKey]; with [reducedMotion] it's all static.
 */
@Composable
internal fun GrowthTree(
    stage: TreeStage,
    details: Int,
    resting: Boolean,
    reducedMotion: Boolean,
    size: TreeSize,
    modifier: Modifier = Modifier,
    celebrate: Boolean = false,
    animate: Boolean = true,
    celebrateKey: String? = null,
    /** Tap for a bounce and a puff of leaves. Only where a tap does nothing else (the sheet, the intro). */
    tappable: Boolean = false,
    /** Streak visitors living on the tree. */
    keepsakes: List<Keepsake> = emptyList(),
    /** A gentle wilt (0–1) for the pause before opening; it perks back up when set to 0. */
    droop: Float = 0f,
    /** Daily leaves in this stage; every third brings a branch. */
    sprigs: Int = 0
) {
    val base = MaterialTheme.reclaim
    val still = !animate || reducedMotion
    val from = remember(stage, details) { if (still) null else lastAnimatedTree }
    val anim = remember(stage, details) {
        when {
            still -> TreeAnim.NONE
            from == null -> TreeAnim.GROW
            from.first != stage -> TreeAnim.STAGE_UP
            details > from.second -> TreeAnim.DETAILS
            else -> TreeAnim.NONE
        }
    }
    val newDetails = if (anim == TreeAnim.DETAILS && from != null) details - from.second else 0
    val detailsMs = Motion.treeDetailMs + 80 * (newDetails - 1).coerceAtLeast(0)
    val progress = remember(stage, details) { Animatable(if (anim == TreeAnim.NONE) 1f else 0f) }
    LaunchedEffect(stage, details) {
        if (animate) lastAnimatedTree = stage to details
        if (progress.value < 1f) {
            if (anim == TreeAnim.DETAILS) {
                progress.animateTo(1f, tween(detailsMs, easing = LinearEasing))
            } else {
                progress.animateTo(1f, tween(Motion.treeGrowMs, easing = FastOutSlowInEasing))
            }
        }
    }

    val celebration = remember { Animatable(0f) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(celebrate, celebrateKey) {
        if (!celebrate || still || celebrateKey == null || celebrateKey == celebratedKey) return@LaunchedEffect
        celebratedKey = celebrateKey
        snapshotFlow { progress.value }.first { it >= 1f }
        celebrating = true
        celebration.snapTo(0f)
        celebration.animateTo(1f, tween(Motion.treeCelebrateMs, easing = LinearEasing))
        celebrating = false
    }

    // One slow clock for the little life in it: leaf flutter, breathing, pollen, and z's while resting.
    val life: State<Float> = if (!still) {
        rememberInfiniteTransition(label = "life").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LIFE_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "lifePhase"
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }

    // Tap: squash, spring back, and the same leaf puff a celebration uses.
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val tapModifier = if (tappable && !still) {
        Modifier.pointerInput(Unit) {
            detectTapGestures {
                scope.launch {
                    bounce.snapTo(0.86f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 320f))
                }
                if (!celebrating) {
                    scope.launch {
                        celebrating = true
                        celebration.snapTo(0f)
                        celebration.animateTo(1f, tween(Motion.treeCelebrateMs, easing = LinearEasing))
                        celebrating = false
                    }
                }
            }
        }
    } else {
        Modifier
    }

    // Eases in slowly like a sigh, and springs back up when it goes to 0.
    val droopShown by animateFloatAsState(
        targetValue = droop,
        animationSpec = if (still) snap() else if (droop > 0f) tween(1600, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.35f, stiffness = 260f),
        label = "droop"
    )

    // Perking back up from a droop: a few drops of water fall onto it.
    val water = remember { Animatable(0f) }
    var wasDrooping by remember { mutableStateOf(false) }
    LaunchedEffect(droop) {
        if (droop > 0f) {
            wasDrooping = true
        } else if (wasDrooping && !still) {
            wasDrooping = false
            water.snapTo(0f)
            water.animateTo(1f, tween(900, easing = LinearEasing))
            water.snapTo(0f)
        }
    }

    // Growing trees sway a little; a resting one is still, which reads as calm.
    val sway: State<Float> = if (!resting && !still) {
        rememberInfiniteTransition(label = "sway").animateFloat(
            initialValue = -Motion.treeSwayDegrees,
            targetValue = Motion.treeSwayDegrees,
            animationSpec = infiniteRepeatable(tween(Motion.treeSwayMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "swayDegrees"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(
        modifier = modifier
            .size(size.width, size.height)
            .then(tapModifier)
            .graphicsLayer {
                // A drooping tree leans a little and sways less.
                rotationZ = sway.value * (1f - 0.25f * droopShown) - 7f * droopShown
                // Breathing: a slow, tiny stretch while growing; drooping makes it a slow sigh.
                // Drooping turns it into a slower, deeper sigh rather than stopping.
                val breath = when {
                    life.value < 0f || resting -> 1f
                    droopShown > 0.01f -> 1f + 0.035f * droopShown * sin(life.value * 4f * PI_F)
                    else -> 1f + 0.03f * sin(life.value * 6f * PI_F)
                }
                scaleY = breath * bounce.value * (1f - 0.05f * droopShown)
                scaleX = (2f - bounce.value) / breath.coerceAtLeast(0.5f)
                transformOrigin = TransformOrigin(0.5f, 142f / 148f)
            }
            .clearAndSetSemantics { }
    ) {
        val p = progress.value
        val phase = life.value
        // Thirsty leaves fade a little toward dry straw; watering brings the colour back.
        val colors = if (droopShown > 0.01f) {
            base.copy(
                plantLeaf = lerp(base.plantLeaf, DryLeaf, 0.35f * droopShown),
                plantLeafBright = lerp(base.plantLeafBright, DryLeaf, 0.35f * droopShown)
            )
        } else {
            base
        }
        when (anim) {
            TreeAnim.STAGE_UP -> {
                // The previous stage stays underneath at full size and fades out at the end.
                val fade = 1f - ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
                if (from != null && fade > 0f) {
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, this.size), Paint().apply { alpha = fade })
                    drawStage(from.first, from.second, 1f, colors) { 1f }
                    drawContext.canvas.restore()
                }
                drawStage(stage, details, p, colors, phase, droopShown, sprigs) { 1f }
            }
            TreeAnim.DETAILS -> {
                val first = from?.second ?: 0
                drawStage(stage, details, 1f, colors, phase, droopShown, sprigs) { i ->
                    if (i < first) 1f else ((p * detailsMs - (i - first) * 80f) / Motion.treeDetailMs).coerceIn(0f, 1f)
                }
            }
            else -> drawStage(stage, details, p, colors, phase, droopShown, sprigs) { 1f }
        }
        if (p >= 1f) drawKeepsakesOnTree(keepsakes, stage, details, phase)
        if (phase >= 0f && p >= 1f) {
            if (resting) drawSleepyZs(stage, details, phase, colors) else drawPollen(stage, details, phase, colors)
        }
        if (celebrating) drawCelebration(stage, details, celebration.value, colors)
        if (water.value > 0f) drawWaterDrops(stage, details, water.value)
    }
}

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** [phase] is the life clock (0–1), or negative when the tree is drawn still. */
private fun DrawScope.drawStage(
    stage: TreeStage,
    details: Int,
    p: Float,
    colors: ReclaimColors,
    phase: Float = -1f,
    droop: Float = 0f,
    sprigs: Int = 0,
    detailScale: (Int) -> Float
) {
    // Daily leaves grow in last, once the shape itself is in.
    val sprigGrow = clamp01((p - 0.8f) / 0.2f)
    val plant = plantShapeOf(stage)
    if (plant != null) {
        if (sprigs > 0 && !plant.oneSided) drawPlantSprigs(plant, sprigs, sprigGrow, colors, phase, droop)
        drawPlant(plant, p, colors, phase, droop)
        return
    }
    val tree = treeShapeOf(stage, details) ?: return
    val u = size.width / 112f
    fun at(x: Float, y: Float) = Offset(x * u, y * u)

    // Trunk.
    val trunk = tree.trunk
    val h = trunk.height * clamp01(p / 0.4f)
    if (h > 0f) {
        val path = Path().apply {
            moveTo((56 - trunk.baseHalf) * u, 142 * u)
            quadraticTo((56 - trunk.topHalf - 1) * u, (142 - h / 2) * u, (56 - trunk.topHalf) * u, (142 - h) * u)
            lineTo((56 + trunk.topHalf) * u, (142 - h) * u)
            quadraticTo((56 + trunk.topHalf + 1) * u, (142 - h / 2) * u, (56 + trunk.baseHalf) * u, 142 * u)
            close()
        }
        drawPath(path, colors.treeBark)
    }
    tree.branches.forEachIndexed { i, b ->
        val s = clamp01((p - 0.3f - i * 0.05f) / 0.2f)
        if (s > 0f) {
            drawLine(colors.treeBark, at(b.x1, b.y1), at(b.x1 + (b.x2 - b.x1) * s, b.y1 + (b.y2 - b.y1) * s), strokeWidth = 3.5f * u, cap = StrokeCap.Round)
        }
    }
    if (tree.roots && p >= 0.4f) {
        val roots = Stroke(width = 3f * u, cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(52 * u, 140 * u); quadraticTo(46 * u, 146 * u, 40 * u, 146 * u) }, colors.treeBark, style = roots)
        drawPath(Path().apply { moveTo(60 * u, 140 * u); quadraticTo(66 * u, 146 * u, 72 * u, 146 * u) }, colors.treeBark, style = roots)
    }
    // Leaf clusters bob a hair, each on its own beat, so the canopy feels soft.
    tree.clusters.forEachIndexed { i, blob ->
        val sag = droop * (2f + (blob.x - 56f).let { if (it < 0) -it else it } * 0.06f)
        val bob = if (phase >= 0f) blob.copy(y = blob.y + sag + 0.8f * sin((phase * 2f + i * 0.23f) * 2f * PI_F)) else blob.copy(y = blob.y + sag)
        drawBlob(bob, clamp01((p - 0.35f - i * 0.05f) / 0.3f), u, colors)
    }
    if (sprigs > 0) drawTreeSprigs(tree, sprigs, sprigGrow, colors, phase)
    // Marks and details come last; their stagger is capped so the last one still lands at p = 1.
    (tree.marks + tree.details).forEachIndexed { k, blob ->
        val grow = clamp01((p - 0.7f - minOf(k * 0.03f, 0.1f)) / 0.2f)
        val detail = k - tree.marks.size
        drawBlob(blob, grow * if (detail >= 0) detailScale(detail) else 1f, u, colors)
    }
}

private fun DrawScope.drawBlob(blob: Blob, scale: Float, u: Float, colors: ReclaimColors) {
    if (scale <= 0f) return
    val c = Offset(blob.x * u, blob.y * u)
    val s = scale * u
    when (blob.mark) {
        Mark.LEAF -> drawCircle(colors.plantLeaf, radius = blob.r * s, center = c)
        Mark.BRIGHT -> drawCircle(colors.plantLeafBright, radius = blob.r * s, center = c)
        Mark.BLOSSOM -> {
            drawCircle(colors.heroBottom.copy(alpha = 0.5f), radius = BLOSSOM_R * s, center = c)
            repeat(5) { k ->
                val a = Math.toRadians(-90.0 + k * 72.0)
                drawCircle(colors.treeBlossom, radius = 2.6f * s, center = c + Offset(cos(a).toFloat(), sin(a).toFloat()) * (3f * s))
            }
            drawCircle(colors.plantBloom, radius = 1.4f * s, center = c)
        }
        Mark.FRUIT -> {
            drawLine(colors.treeBark, c + Offset(0f, -4.5f * s), c + Offset(1f * s, -7f * s), strokeWidth = 1f * u, cap = StrokeCap.Round)
            drawCircle(colors.treeFruit, radius = FRUIT_R * s, center = c)
            drawCircle(colors.heroBottom, radius = FRUIT_R * s, center = c, style = Stroke(width = 1f * u))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 1.2f * s, center = c + Offset(-1.5f * s, -1.5f * s))
        }
    }
}

/**
 * A plant's daily growth, behind its main leaves: a small leaf low on the stem for each growing
 * day, alternating sides, and every third day a side branch with a leaf at its tip.
 */
private fun DrawScope.drawPlantSprigs(shape: PlantShape, sprigs: Int, grow: Float, colors: ReclaimColors, phase: Float, droop: Float) {
    if (grow <= 0f) return
    val u = size.width / 112f
    val h = shape.stemHeight
    val scale = (h / 112f).coerceIn(0.5f, 1f)
    fun at(t: Float) = stemPoint(h, t).let { (x, y) -> Offset(x * u, y * u) }
    val branches = sprigs / SPRIGS_PER_BRANCH
    repeat(branches.coerceAtMost(BRANCH_SPOTS.size)) { b ->
        val (t, side) = BRANCH_SPOTS[b]
        val from = at(t)
        val degrees = side * (68f + 12f * droop)
        val len = 16f * u * scale * grow
        val a = Math.toRadians(degrees.toDouble())
        val tip = from + Offset(sin(a).toFloat(), -cos(a).toFloat()) * len
        drawLine(colors.plantStem, from, tip, strokeWidth = 2.4f * u * scale, cap = StrokeCap.Round)
        val flutter = if (phase >= 0f) 8f * sin((phase * 3f + b * 0.4f) * 2f * PI_F) else 0f
        drawLeaf(tip, degrees - side * 30f + flutter, 11f * u * scale * grow, colors.plantLeafBright)
    }
    repeat(sprigs.coerceAtMost(SPRIG_SPOTS.size)) { k ->
        val (t, side) = SPRIG_SPOTS[k]
        val flutter = if (phase >= 0f) 10f * sin((phase * 3f + k * 0.27f) * 2f * PI_F) else 0f
        val degrees = side * (LEAF_DEGREES + 20f + 50f * droop) + flutter
        drawLeaf(at(t), degrees, 10f * u * scale * grow, if (k % 2 == 0) colors.plantLeaf else colors.plantLeafBright)
    }
}

/**
 * A tree's daily growth: new leaves around the canopy's edge, and every third day a twig
 * reaching out from the trunk with a small tuft.
 */
private fun DrawScope.drawTreeSprigs(tree: TreeShape, sprigs: Int, grow: Float, colors: ReclaimColors, phase: Float) {
    if (grow <= 0f) return
    val u = size.width / 112f
    val branches = sprigs / SPRIGS_PER_BRANCH
    repeat(branches.coerceAtMost(TWIG_SPOTS.size)) { b ->
        val tw = TWIG_SPOTS[b]
        val from = Offset(56f * u, (142f - tree.trunk.height * tw.first) * u)
        val tip = from + Offset(tw.second * 22f * u * grow, -12f * u * grow)
        drawLine(colors.treeBark, from, tip, strokeWidth = 2.6f * u, cap = StrokeCap.Round)
        drawCircle(colors.plantLeafBright, radius = 6f * u * grow, center = tip)
    }
    repeat(sprigs.coerceAtMost(MAX_SPRIGS)) { k ->
        val cluster = tree.clusters[k % tree.clusters.size]
        val angle = Math.toRadians((-90.0 + (k * 137.5) % 360.0))
        val edge = Offset(cluster.x + cos(angle).toFloat() * cluster.r, cluster.y + sin(angle).toFloat() * cluster.r) * u
        val flutter = if (phase >= 0f) 10f * sin((phase * 3f + k * 0.21f) * 2f * PI_F) else 0f
        val outward = Math.toDegrees(angle).toFloat() + 90f
        drawLeaf(edge, outward + flutter, 8f * u * grow, if (k % 2 == 0) colors.plantLeafBright else colors.plantLeaf)
    }
}

/** Stages 0–4, as the old plant drew them: the stem grows along its final curve, then the leaves. */
private fun DrawScope.drawPlant(shape: PlantShape, progress: Float, colors: ReclaimColors, phase: Float = -1f, droop: Float = 0f) {
    val u = size.width / 112f
    val h = shape.stemHeight
    val base = Offset(56 * u, 142 * u)
    val control = Offset(48 * u, (142 - h / 2) * u)
    fun pointAt(t: Float): Offset = stemPoint(h, t).let { (x, y) -> Offset(x * u, y * u) }

    val grown = (progress / 0.5f).coerceIn(0f, 1f)
    if (grown > 0f) {
        val stem = Path().apply {
            moveTo(base.x, base.y)
            val c = base + (control - base) * grown
            val end = pointAt(grown)
            quadraticTo(c.x, c.y, end.x, end.y)
        }
        drawPath(stem, colors.plantStem, style = Stroke(width = shape.stemStroke * u, cap = StrokeCap.Round))
    }

    shape.leaves.forEachIndexed { i, (position, length) ->
        // A leaf never shows ahead of the stem that carries it.
        val scale = if (grown < position) 0f else ((progress - 0.3f - i * 0.1f) / 0.3f).coerceIn(0f, 1f)
        if (scale <= 0f) return@forEachIndexed
        val at = pointAt(position)
        // Each leaf flutters on its own offset beat.
        val flutter = if (phase >= 0f) 12f * sin((phase * 3f + i * 0.31f) * 2f * PI_F) else 0f
        val flutter2 = if (phase >= 0f) 12f * sin((phase * 3f + i * 0.31f + 0.5f) * 2f * PI_F) else 0f
        // Drooping lowers the leaves toward horizontal, and they flutter less.
        // Thirsty leaves hang lower and rise and fall slowly, like a sigh.
        val sigh = if (phase >= 0f) 8f * droop * sin(phase * 4f * PI_F) else 0f
        val down = 60f * droop + sigh
        val calm = 1f - 0.5f * droop
        if (!shape.oneSided) drawLeaf(at, -LEAF_DEGREES - down + flutter * calm, length * u * scale, colors.plantLeaf)
        drawLeaf(at, LEAF_DEGREES + down + flutter2 * calm, length * u * scale, colors.plantLeafBright)
    }

    if (shape.oneSided) {
        val seed = Size(16 * u, 11 * u)
        drawOval(colors.plantBloom, topLeft = Offset(base.x - seed.width / 2, base.y - 2 * u - seed.height / 2), size = seed)
    }
}

/** Where the top of the tree is, in pixels. */
private fun DrawScope.treeTop(stage: TreeStage, details: Int): Offset {
    val u = size.width / 112f
    val plant = plantShapeOf(stage)
    return if (plant != null) {
        stemPoint(plant.stemHeight, 1f).let { (x, y) -> Offset(x * u, y * u) }
    } else {
        treeShapeOf(stage, details)!!.top.let { Offset(it.x * u, it.y * u) }
    }
}

/** A few specks of pollen drifting up past the top, each fading in and out on its own loop. */
private fun DrawScope.drawPollen(stage: TreeStage, details: Int, phase: Float, colors: ReclaimColors) {
    val u = size.width / 112f
    val top = treeTop(stage, details)
    repeat(POLLEN_COUNT) { i ->
        val t = (phase * 2f + i / POLLEN_COUNT.toFloat()) % 1f
        val x = top.x + (i - (POLLEN_COUNT - 1) / 2f) * 12f * u + 4f * u * sin((t + i * 0.2f) * 2f * PI_F)
        val y = top.y + 18f * u - t * 44f * u
        val alpha = sin(t * PI_F) * 0.7f
        drawCircle(colors.plantBloom.copy(alpha = alpha), radius = (1.2f + (i % 2) * 0.6f) * u, center = Offset(x, y))
    }
}

/** Resting: three little z's float up from the top, growing as they rise. Sleepy, not sad. */
private fun DrawScope.drawSleepyZs(stage: TreeStage, details: Int, phase: Float, colors: ReclaimColors) {
    val u = size.width / 112f
    val top = treeTop(stage, details)
    repeat(3) { i ->
        val t = (phase * 1.5f + i / 3f) % 1f
        val half = (2.2f + t * 2.6f) * u
        val c = Offset(top.x + 10f * u + t * 16f * u + 2f * u * sin(t * 2f * PI_F), top.y - 2f * u - t * 30f * u)
        val z = Path().apply {
            moveTo(c.x - half, c.y - half)
            lineTo(c.x + half, c.y - half)
            lineTo(c.x - half, c.y + half)
            lineTo(c.x + half, c.y + half)
        }
        drawPath(
            z,
            colors.onHeroMuted.copy(alpha = sin(t * PI_F) * 0.8f),
            style = Stroke(width = 1.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Three drops falling from above onto the top of the tree, for perking up after a droop. */
private fun DrawScope.drawWaterDrops(stage: TreeStage, details: Int, t: Float) {
    val u = size.width / 112f
    val top = treeTop(stage, details)
    repeat(3) { i ->
        val local = ((t - i * 0.15f) / 0.7f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@repeat
        val x = top.x + (i - 1) * 10f * u
        val y = top.y - 40f * u + local * 44f * u
        val alpha = if (local > 0.85f) (1f - local) / 0.15f else 1f
        val r = 2.2f * u
        val drop = Path().apply {
            moveTo(x, y - 2.6f * r)
            quadraticTo(x + 1.2f * r, y - 0.6f * r, x + r, y)
            quadraticTo(x, y + 1.2f * r, x - r, y)
            quadraticTo(x - 1.2f * r, y - 0.6f * r, x, y - 2.6f * r)
            close()
        }
        drawPath(drop, WaterBlue.copy(alpha = alpha))
    }
}

/** Eight small leaves rising from the top of the canopy and fading — a new stage or a streak boost. */
private fun DrawScope.drawCelebration(stage: TreeStage, details: Int, t: Float, colors: ReclaimColors) {
    val u = size.width / 112f
    val origin = treeTop(stage, details)
    val alpha = 1f - t
    repeat(8) { i ->
        val degrees = -60f + i * 120f / 7f
        val a = Math.toRadians(degrees.toDouble())
        val at = origin + Offset(sin(a).toFloat(), -cos(a).toFloat()) * (28f * u * t)
        val color = if (i % 2 == 0) colors.plantLeafBright else colors.plantBloom
        drawLeaf(at, degrees, 5f * u, color.copy(alpha = alpha))
    }
}

/** A leaf from [at], [degrees] off vertical (negative leans left), [length] long, 0.42 as wide. */
private fun DrawScope.drawLeaf(at: Offset, degrees: Float, length: Float, color: Color) {
    if (length <= 0f) return
    val theta = Math.toRadians(degrees.toDouble())
    val tip = at + Offset(sin(theta).toFloat(), -cos(theta).toFloat()) * length
    val d = tip - at
    val len = hypot(d.x, d.y)
    val n = Offset(-d.y / len, d.x / len) * (0.42f * length / 2)
    val path = Path().apply {
        moveTo(at.x, at.y)
        val c1 = at + d * 0.25f + n
        val c2 = at + d * 0.75f + n
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        val c3 = at + d * 0.75f - n
        val c4 = at + d * 0.25f - n
        cubicTo(c3.x, c3.y, c4.x, c4.y, at.x, at.y)
        close()
    }
    drawPath(path, color)
}

@Preview(name = "Stages, light", widthDp = 1200)
@Preview(name = "Stages, dark", widthDp = 1200, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TreeStagesPreview() {
    ReclaimLifeTheme {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false, true).forEach { full ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TreeStage.entries.forEach { stage ->
                        Box(modifier = Modifier.width(116.dp).height(152.dp), contentAlignment = Alignment.BottomCenter) {
                            HeroPanelBackground(Modifier.fillMaxSize(), resting = stage.ordinal % 3 == 2)
                            GrowthTree(
                                stage,
                                if (full) stage.maxDetails else 0,
                                resting = false,
                                reducedMotion = true,
                                size = TreeSize.HERO_COMPACT
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The hero's gradient and backdrop, for the preview only. */
@Composable
private fun HeroPanelBackground(modifier: Modifier, resting: Boolean) {
    val colors = MaterialTheme.reclaim
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom))) }
        HeroBackdrop(Modifier.fillMaxSize(), resting = resting, reducedMotion = true)
    }
}
