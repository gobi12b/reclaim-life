package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.Keepsake
import io.github.gobi12b.reclaimlife.data.TreeStage
import kotlin.math.abs
import kotlin.math.sin

// Keepsake colours are their own: a ladybug is red in any theme.
private val LadybugRed = Color(0xFFE0483E)
private val Ink = Color(0xFF2A2522)
private val WingA = Color(0xFFF2A65A)
private val WingB = Color(0xFFF7D774)
private val Straw = Color(0xFF9C6B3F)
private val StrawLight = Color(0xFFC49A6C)
private val EggBlue = Color(0xFFBFE3F0)
private val BirdBody = Color(0xFF6FA8DC)
private val BirdBelly = Color(0xFFF6E7C8)
private val Beak = Color(0xFFF2B233)
private val Glow = Color(0xFFFFF2A8)
private val LanternRed = Color(0xFFD9573B)

private const val TAU = (2 * Math.PI).toFloat()

/**
 * Draws [k] centred on [c]. [u] is the tree unit in px, [phase] the life clock (0–1, negative for
 * still), and [tint] turns it into a flat silhouette for ones not earned yet.
 */
internal fun DrawScope.drawKeepsake(k: Keepsake, c: Offset, u: Float, phase: Float, tint: Color? = null) {
    val moving = phase >= 0f
    fun col(real: Color) = tint ?: real
    when (k) {
        Keepsake.LADYBUG -> {
            // Scuttles a little side to side, with a head bob.
            val c = if (moving) c + Offset(1.4f * u * sin(phase * 4f * TAU), 0f) else c
            val bob = if (moving) 0.5f * u * sin(phase * 16f * TAU) else 0f
            drawCircle(col(Ink), radius = 2.2f * u, center = c + Offset(4.2f * u, -0.4f * u + bob))
            drawCircle(col(LadybugRed), radius = 4f * u, center = c)
            if (tint == null) {
                drawLine(Ink, c + Offset(0f, -4f * u), c + Offset(0f, 4f * u), strokeWidth = 0.7f * u)
                listOf(Offset(-1.8f, -1.6f), Offset(1.8f, -1.2f), Offset(-1.6f, 1.8f), Offset(1.9f, 2f)).forEach {
                    drawCircle(Ink, radius = 0.9f * u, center = c + it * u)
                }
            }
        }
        Keepsake.BUTTERFLY -> {
            // Wings open and close quickly; the body stays put.
            val flap = if (moving) 0.35f + 0.65f * abs(sin(phase * 24f * TAU)) else 1f
            listOf(-1f, 1f).forEach { side ->
                drawOval(col(WingA), topLeft = c + Offset(if (side < 0) -5.5f * u * flap else 0f, -5f * u), size = Size(5.5f * u * flap, 5.5f * u))
                drawOval(col(WingB), topLeft = c + Offset(if (side < 0) -4f * u * flap else 0f, 0f), size = Size(4f * u * flap, 4f * u))
            }
            drawLine(col(Ink), c + Offset(0f, -4f * u), c + Offset(0f, 4f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        }
        Keepsake.NEST -> {
            // The eggs take turns giving a little wobble, like something's about to hatch.
            val beat = if (moving) (phase * 3f) % 1f else 1f
            fun wobble(i: Int): Float {
                val t = ((beat - i * 0.5f + 1f) % 1f)
                return if (t < 0.2f) 0.9f * u * sin(t / 0.2f * 3f * TAU) else 0f
            }
            drawCircle(col(EggBlue), radius = 2.2f * u, center = c + Offset(-2f * u + wobble(0), -2.2f * u))
            drawCircle(col(EggBlue), radius = 2.2f * u, center = c + Offset(2.2f * u + wobble(1), -2.4f * u))
            val bowl = Path().apply {
                moveTo(c.x - 7f * u, c.y - 2f * u)
                quadraticTo(c.x, c.y + 7f * u, c.x + 7f * u, c.y - 2f * u)
                close()
            }
            drawPath(bowl, col(Straw))
            if (tint == null) {
                drawLine(StrawLight, c + Offset(-5f * u, 0f), c + Offset(5f * u, 0.4f * u), strokeWidth = 0.7f * u, cap = StrokeCap.Round)
            }
        }
        Keepsake.SONGBIRD -> {
            // A little hop every few seconds, and a note when it lands.
            val hopT = if (moving) (phase * 3f) % 1f else 1f
            val hop = if (hopT < 0.12f) sin(hopT / 0.12f * Math.PI.toFloat()) * 3f * u else 0f
            val b = c - Offset(0f, hop)
            drawCircle(col(BirdBody), radius = 4.2f * u, center = b)
            drawCircle(col(BirdBody), radius = 2.8f * u, center = b + Offset(3.2f * u, -3.4f * u))
            drawOval(col(BirdBelly), topLeft = b + Offset(-1.5f * u, -0.5f * u), size = Size(4.5f * u, 3.8f * u))
            val beak = Path().apply {
                moveTo(b.x + 5.6f * u, b.y - 4f * u)
                lineTo(b.x + 8f * u, b.y - 3.3f * u)
                lineTo(b.x + 5.6f * u, b.y - 2.6f * u)
                close()
            }
            drawPath(beak, col(Beak))
            drawLine(col(BirdBody), b + Offset(-3.5f * u, 0f), b + Offset(-6.5f * u, -2.5f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            if (tint == null) drawCircle(Ink, radius = 0.7f * u, center = b + Offset(3.8f * u, -4f * u))
            if (moving && hopT in 0.15f..0.6f) {
                val t = (hopT - 0.15f) / 0.45f
                val n = b + Offset(6f * u + t * 4f * u, -8f * u - t * 8f * u)
                val a = sin(t * Math.PI.toFloat())
                drawCircle(Ink.copy(alpha = a * 0.7f), radius = 1.2f * u, center = n)
                drawLine(Ink.copy(alpha = a * 0.7f), n + Offset(1.1f * u, 0f), n + Offset(1.1f * u, -4f * u), strokeWidth = 0.6f * u)
            }
        }
        Keepsake.FIREFLIES -> {
            repeat(3) { i ->
                val t = if (moving) phase * 2f + i * 0.33f else i * 0.33f
                val p = c + Offset(sin(t * TAU) * 5f * u + (i - 1) * 4f * u, sin(t * 2f * TAU) * 3f * u - i * 2f * u)
                val pulse = if (moving) 0.45f + 0.55f * abs(sin((phase * 5f + i * 0.3f) * TAU)) else 1f
                if (tint == null) drawCircle(Glow.copy(alpha = 0.35f * pulse), radius = 3.2f * u, center = p)
                drawCircle(col(Glow).copy(alpha = if (tint == null) pulse else 1f), radius = 1.3f * u, center = p)
            }
        }
        Keepsake.LANTERN -> {
            val pulse = if (moving) 0.7f + 0.3f * sin(phase * 4f * TAU) else 1f
            if (tint == null) drawCircle(Glow.copy(alpha = 0.3f * pulse), radius = 8f * u, center = c + Offset(0f, -4f * u))
            drawLine(col(Ink), c + Offset(0f, -12f * u), c + Offset(0f, -9f * u), strokeWidth = 0.8f * u, cap = StrokeCap.Round)
            drawOval(col(LanternRed), topLeft = c + Offset(-4.5f * u, -9.5f * u), size = Size(9f * u, 11f * u))
            if (tint == null) drawOval(Glow.copy(alpha = pulse), topLeft = c + Offset(-2f * u, -7f * u), size = Size(4f * u, 6f * u))
            drawLine(col(Ink), c + Offset(-2.5f * u, 1.5f * u), c + Offset(2.5f * u, 1.5f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        }
    }
}

/** Where each keepsake lives on the tree, in 112×148 units. Small plants keep them on the ground. */
private fun keepsakeSpot(k: Keepsake, stage: TreeStage, details: Int): Offset {
    val tree = if (plantShapeOf(stage) == null) treeShapeOf(stage, details) else null
    return when (k) {
        Keepsake.LADYBUG -> Offset(74f, 139f)
        Keepsake.BUTTERFLY -> Offset(80f, (tree?.top?.y ?: 100f) - 4f)
        Keepsake.NEST -> if (tree != null) Offset(56f, 142f - tree.trunk.height + 2f) else Offset(34f, 139f)
        Keepsake.SONGBIRD -> if (tree != null) Offset(tree.top.x - 4f, tree.top.y - tree.top.r - 3f) else Offset(20f, 138f)
        Keepsake.FIREFLIES -> Offset(30f, (tree?.top?.y ?: 104f) + 10f)
        Keepsake.LANTERN -> Offset(94f, 141f)
    }
}

/** The earned keepsakes, drawn on the tree after everything else so they sit on top. */
internal fun DrawScope.drawKeepsakesOnTree(keepsakes: List<Keepsake>, stage: TreeStage, details: Int, phase: Float) {
    val u = size.width / 112f
    keepsakes.forEach { k ->
        var spot = keepsakeSpot(k, stage, details)
        if (phase >= 0f) {
            spot += when (k) {
                Keepsake.LADYBUG -> Offset(3f * sin(phase * TAU), 0f)
                Keepsake.BUTTERFLY -> Offset(14f * sin(phase * 2f * TAU), 7f * sin(phase * 4f * TAU))
                else -> Offset.Zero
            }
        }
        drawKeepsake(k, Offset(spot.x * u, spot.y * u), u, phase)
    }
}

/** One milestone on its own, for the streak card and the shelf. All of them move; [silhouette] flattens unearned ones. */
@Composable
internal fun KeepsakeIcon(
    k: Keepsake,
    earned: Boolean,
    /** Null draws it in colour even when not earned (still, no motion). */
    silhouette: Color?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    // Read only while drawing, so the icon redraws each frame without recomposing the page around it.
    val phase: State<Float> = if (!reducedMotion) {
        rememberInfiniteTransition(label = "keepsake").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
            label = "keepsakePhase"
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }
    Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
        // Icons are drawn at a fixed scale: about 20 units across the box.
        val u = this.size.minDimension / 22f
        val c = Offset(this.size.width / 2, this.size.height / 2 + when (k) {
            Keepsake.LANTERN -> 4f * u
            Keepsake.NEST -> 1f * u
            else -> 0f
        })
        drawKeepsake(k, c, u, phase.value, tint = if (earned) null else silhouette)
    }
}
