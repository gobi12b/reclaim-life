package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.InsightKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Every glyph on Home is drawn, so nothing depends on the font or the OEM's emoji set, and the app
// still needs no icon library. The small ones are laid out on a 24-unit box with a 2-unit stroke.

/** A drawn chevron, pointing right by default; [rotation] 90 points it down, -90 up. Decorative. */
@Composable
internal fun Chevron(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, rotation: Float = 0f) {
    Canvas(modifier = modifier.size(size).rotate(rotation).clearAndSetSemantics { }) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.38f, this@Canvas.size.height * 0.24f)
            lineTo(this@Canvas.size.width * 0.64f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.38f, this@Canvas.size.height * 0.76f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A drawn gear for the Settings button. */
@Composable
internal fun GearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = 2.dp.toPx()
        drawCircle(color, radius = size.width * 0.28f, center = c, style = Stroke(stroke))
        drawCircle(color, radius = size.width * 0.1f, center = c, style = Stroke(stroke))
        repeat(8) { i ->
            val angle = Math.toRadians(i * 45.0)
            val inner = size.width * 0.3f
            val outer = size.width * 0.44f
            drawLine(
                color,
                Offset(c.x + inner * cos(angle).toFloat(), c.y + inner * sin(angle).toFloat()),
                Offset(c.x + outer * cos(angle).toFloat(), c.y + outer * sin(angle).toFloat()),
                strokeWidth = stroke * 1.6f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Two bars, the pause symbol, so a pause isn't carried by colour alone. Decorative unless labelled by the caller. */
@Composable
internal fun PauseBars(color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width * 0.22f
        val h = size.height * 0.7f
        val top = (size.height - h) / 2f
        drawRoundRect(color, Offset(size.width * 0.2f, top), Size(w, h), CornerRadius(w / 2))
        drawRoundRect(color, Offset(size.width * 0.58f, top), Size(w, h), CornerRadius(w / 2))
    }
}

/** A drawn "!" in an error disc, so the broken-counter banner doesn't rely on red alone. */
@Composable
internal fun AlertGlyph(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val disc = MaterialTheme.colorScheme.error
    val mark = MaterialTheme.colorScheme.onError
    Canvas(modifier = modifier.size(size).clearAndSetSemantics { contentDescription = "Warning" }) {
        val w = this.size.width
        drawCircle(disc)
        drawLine(mark, Offset(w / 2, w * 0.26f), Offset(w / 2, w * 0.56f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
        drawCircle(mark, radius = w * 0.07f, center = Offset(w / 2, w * 0.74f))
    }
}

internal enum class TrendDirection { UP, DOWN, SAME }

/** Up, down, or a flat line for "about the same" — the words beside it carry the meaning. Decorative. */
@Composable
internal fun TrendGlyph(direction: TrendDirection, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2 * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (direction) {
            TrendDirection.SAME -> drawLine(color, Offset(5 * u, 12 * u), Offset(19 * u, 12 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
            TrendDirection.UP, TrendDirection.DOWN -> {
                val up = direction == TrendDirection.UP
                val tipY = if (up) 4 * u else 20 * u
                val tailY = if (up) 20 * u else 4 * u
                val headY = if (up) 10 * u else 14 * u
                drawLine(color, Offset(12 * u, tailY), Offset(12 * u, tipY), strokeWidth = 2 * u, cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(6 * u, headY)
                    lineTo(12 * u, tipY)
                    lineTo(18 * u, headY)
                }
                drawPath(head, color, style = stroke)
            }
        }
    }
}

/** A check mark for a day within limit. Decorative: the cell says it in words. */
@Composable
internal fun CheckGlyph(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.5.dp) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val u = size.width / 24f
        val path = Path().apply {
            moveTo(5 * u, 12.5f * u)
            lineTo(10 * u, 17.5f * u)
            lineTo(19 * u, 7 * u)
        }
        drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A cross for a day over the limit: a shape cue instead of red. Decorative. */
@Composable
internal fun CrossGlyph(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.dp) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val u = size.width / 24f
        val s = strokeWidth.toPx()
        drawLine(color, Offset(5 * u, 5 * u), Offset(19 * u, 19 * u), strokeWidth = s, cap = StrokeCap.Round)
        drawLine(color, Offset(19 * u, 5 * u), Offset(5 * u, 19 * u), strokeWidth = s, cap = StrokeCap.Round)
    }
}

/** A single rising leaf: filled for the streak chip, stroked to match the insight glyphs. Decorative. */
@Composable
internal fun LeafGlyph(color: Color, modifier: Modifier = Modifier, filled: Boolean = true) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) { drawLeaf(color, filled) }
}

private fun DrawScope.drawLeaf(color: Color, filled: Boolean) {
    val u = size.width / 24f
    val stroke = Stroke(width = 2 * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val leaf = Path().apply {
        moveTo(6 * u, 18 * u)
        cubicTo(5 * u, 10 * u, 11 * u, 4 * u, 20 * u, 4 * u)
        cubicTo(20 * u, 13 * u, 14 * u, 19 * u, 6 * u, 18 * u)
        close()
    }
    if (filled) drawPath(leaf, color) else drawPath(leaf, color, style = stroke)
    drawLine(color, Offset(6 * u, 18 * u), Offset(3.5f * u, 20.5f * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
    if (!filled) drawLine(color, Offset(6 * u, 18 * u), Offset(15 * u, 9 * u), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
}

/**
 * The insight's icon: a sand disc with a drawn glyph instead of an emoji, so it looks the same on
 * every phone and doesn't compete with the hero's green. Reads the kind's description.
 */
@Composable
internal fun InsightGlyph(kind: InsightKind, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .clearAndSetSemantics { contentDescription = kind.iconDescription }
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val u = size.width / 24f
            val stroke = Stroke(width = 2 * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (kind) {
                InsightKind.SETTLING -> {
                    drawLine(color, Offset(12 * u, 21 * u), Offset(12 * u, 11 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                    val left = Path().apply {
                        moveTo(12 * u, 12 * u)
                        cubicTo(10 * u, 4.5f * u, 4.5f * u, 3.5f * u, 2.5f * u, 5.5f * u)
                        cubicTo(3.5f * u, 11 * u, 8 * u, 13.5f * u, 12 * u, 12 * u)
                    }
                    val right = Path().apply {
                        moveTo(12 * u, 12 * u)
                        cubicTo(14 * u, 4.5f * u, 19.5f * u, 3.5f * u, 21.5f * u, 5.5f * u)
                        cubicTo(20.5f * u, 11 * u, 16 * u, 13.5f * u, 12 * u, 12 * u)
                    }
                    drawPath(left, color, style = stroke)
                    drawPath(right, color, style = stroke)
                }
                InsightKind.APP_DRIVEN, InsightKind.CREPT_UP, InsightKind.HEAVIER -> {
                    listOf(9f, 15f).forEach { y -> drawPath(wave(u, y), color, style = stroke) }
                }
                InsightKind.CUT_BIG -> drawPath(star(u), color, style = stroke)
                InsightKind.CUT -> drawLeaf(color, filled = false)
                InsightKind.STEADY -> {
                    val stand = Path().apply {
                        moveTo(12 * u, 9 * u)
                        lineTo(7 * u, 20 * u)
                        lineTo(17 * u, 20 * u)
                        close()
                    }
                    drawPath(stand, color, style = stroke)
                    drawLine(color, Offset(3 * u, 7 * u), Offset(21 * u, 7 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                    drawLine(color, Offset(4 * u, 7 * u), Offset(4 * u, 11 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                    drawLine(color, Offset(20 * u, 7 * u), Offset(20 * u, 11 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** One and a half periods of a sine across the box, centred on [centerY]. */
private fun wave(u: Float, centerY: Float): Path = Path().apply {
    val steps = 24
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = (3 + 18 * t) * u
        val y = (centerY - 2.5f * sin(t * 3 * PI).toFloat()) * u
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/** A five-point star, point up. */
private fun star(u: Float): Path = Path().apply {
    val cx = 12 * u
    val cy = 12.8f * u
    for (i in 0 until 10) {
        val r = (if (i % 2 == 0) 9.5f else 4f) * u
        val a = -PI / 2 + i * PI / 5
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
