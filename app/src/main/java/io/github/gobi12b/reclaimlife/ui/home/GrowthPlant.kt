package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.ui.common.Motion
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimColors
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme
import io.github.gobi12b.reclaimlife.ui.theme.reclaim
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * How far the hero's plant has grown. Keyed to time won back *since you started*, which only goes
 * up, so the plant never shrinks — a reward, not a report.
 */
internal enum class GrowthStage(val thresholdMs: Long, val label: String) {
    SEED(0L, "Seed"),
    SPROUT(30 * 60_000L, "Sprout"),
    SEEDLING(3 * 3_600_000L, "Seedling"),
    YOUNG(12 * 3_600_000L, "Young plant"),
    SAPLING(24 * 3_600_000L, "Sapling"),
    BLOOM(72 * 3_600_000L, "In bloom")
}

internal fun growthStage(sinceStartedMs: Long): GrowthStage =
    GrowthStage.entries.last { sinceStartedMs >= it.thresholdMs || it == GrowthStage.SEED }

/** The stage after [stage], or null in full bloom. */
internal fun nextStage(stage: GrowthStage): GrowthStage? = GrowthStage.entries.getOrNull(stage.ordinal + 1)

/** One stage's drawing, in plant units (the canvas is 112 units wide). Leaves are (position on stem, length). */
private class PlantShape(val stemHeight: Float, val leaves: List<Pair<Float, Float>>, val stemStroke: Float = 4f)

private val SAPLING_LEAVES = listOf(0.30f to 20f, 0.55f to 24f, 0.80f to 28f, 1f to 30f)

private fun shapeOf(stage: GrowthStage): PlantShape = when (stage) {
    GrowthStage.SEED -> PlantShape(10f, listOf(1f to 12f))
    GrowthStage.SPROUT -> PlantShape(40f, listOf(1f to 26f))
    GrowthStage.SEEDLING -> PlantShape(64f, listOf(0.55f to 22f, 1f to 28f))
    GrowthStage.YOUNG -> PlantShape(88f, listOf(0.40f to 20f, 0.70f to 24f, 1f to 28f))
    GrowthStage.SAPLING -> PlantShape(112f, SAPLING_LEAVES, stemStroke = 5f)
    GrowthStage.BLOOM -> PlantShape(120f, SAPLING_LEAVES, stemStroke = 5f)
}

/**
 * The last stage that grew on screen. Process-level on purpose: the plant grows once per visit
 * (a cold start is a fresh visit) and again whenever it reaches a new stage, not on every recompose.
 */
private var lastAnimatedStage: GrowthStage? = null

/** Sun glow and hill behind the hero's text. Decorative. */
@Composable
internal fun HeroBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.reclaim
    // Warm and visible on the light panel, only a hint in the dark.
    val glowAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.18f else 0.70f
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val radius = 88.dp.toPx()
        val center = Offset(w - 72.dp.toPx(), 44.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.sunGlow.copy(alpha = glowAlpha), colors.sunGlow.copy(alpha = 0f)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
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
 * The drawn plant for [stage], 112×148dp (88×116dp when [compact]). Decorative: the hero's text
 * carries the meaning. Grows in once per stage; with [reducedMotion] it's drawn fully grown.
 */
@Composable
internal fun GrowthPlant(stage: GrowthStage, reducedMotion: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.reclaim
    val progress = remember(stage) {
        Animatable(if (reducedMotion || stage == lastAnimatedStage) 1f else 0f)
    }
    LaunchedEffect(stage) {
        lastAnimatedStage = stage
        if (progress.value < 1f) progress.animateTo(1f, tween(Motion.plantGrowMs, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier = modifier
            .size(if (compact) 88.dp else 112.dp, if (compact) 116.dp else 148.dp)
            .clearAndSetSemantics { }
    ) {
        drawPlant(stage, if (reducedMotion) 1f else progress.value, colors)
    }
}

private fun DrawScope.drawPlant(stage: GrowthStage, progress: Float, colors: ReclaimColors) {
    val u = size.width / 112f
    val shape = shapeOf(stage)
    val h = shape.stemHeight
    val base = Offset(56 * u, 142 * u)
    val control = Offset(48 * u, (142 - h / 2) * u)
    val tip = Offset(56 * u, (142 - h) * u)
    fun pointAt(t: Float): Offset {
        val a = (1 - t) * (1 - t)
        val b = 2 * (1 - t) * t
        val c = t * t
        return Offset(a * base.x + b * control.x + c * tip.x, a * base.y + b * control.y + c * tip.y)
    }

    // The stem grows along its final curve: the first [grown] of it, by splitting the quadratic.
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
        // The seed's first leaf opens on one side only, like the brand mark's first sprout.
        if (stage != GrowthStage.SEED) drawLeaf(at, -50f, length * u * scale, colors.plantLeaf)
        drawLeaf(at, 50f, length * u * scale, colors.plantLeafBright)
    }

    if (stage == GrowthStage.SEED) {
        val seed = Size(16 * u, 11 * u)
        drawOval(colors.plantBloom, topLeft = Offset(base.x - seed.width / 2, base.y - 2 * u - seed.height / 2), size = seed)
    }

    if (stage == GrowthStage.BLOOM) {
        val bloom = ((progress - 0.8f) / 0.2f).coerceIn(0f, 1f)
        if (bloom > 0f) {
            repeat(5) { k ->
                val angle = Math.toRadians(-90.0 + k * 72.0)
                val centre = tip + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (7 * u * bloom)
                drawCircle(colors.plantBloom, radius = 6 * u * bloom, center = centre)
            }
            drawCircle(colors.heroTop, radius = 4 * u * bloom, center = tip)
        }
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

@Preview(name = "Stages, light", widthDp = 720)
@Preview(name = "Stages, dark", widthDp = 720, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GrowthStagesPreview() {
    ReclaimLifeTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GrowthStage.entries.forEach { stage ->
                Box(modifier = Modifier.width(116.dp).height(184.dp), contentAlignment = Alignment.BottomCenter) {
                    HeroPanelBackground(Modifier.fillMaxSize())
                    GrowthPlant(stage, reducedMotion = true)
                }
            }
        }
    }
}

/** The hero's gradient and backdrop, for the preview only. */
@Composable
private fun HeroPanelBackground(modifier: Modifier) {
    val colors = MaterialTheme.reclaim
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom))) }
        HeroBackdrop(Modifier.fillMaxSize())
    }
}
