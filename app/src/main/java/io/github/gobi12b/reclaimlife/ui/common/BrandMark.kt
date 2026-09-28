package io.github.gobi12b.reclaimlife.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The sunrise mark. Geometry is on the 108-unit adaptive-icon grid and matches
// res/drawable/ic_launcher_background.xml + ic_launcher_foreground.xml, so the app and the
// launcher icon are the same picture.
private val SkyTop = Color(0xFFFFD86B)
private val SkyMid = Color(0xFFFF9A62)
private val SkyBottom = Color(0xFFF2706A)
private val Sun = Color(0xEBFFF3C4)
private val Hill = Color(0xFF2F7D3F)
private val LeafDeep = Color(0xFF3FA65A)
private val LeafBright = Color(0xFF9BDB5E)

/** The launcher icon's safe zone — the part of the 108 grid every icon mask shows. */
private const val VIEW_START = 21f
private const val VIEW_SIZE = 66f

private val hillPath = Path().apply {
    moveTo(6f, 81f)
    quadraticTo(54f, 62f, 102f, 81f)
    lineTo(102f, 108f)
    lineTo(6f, 108f)
    close()
}
private val leftLeaf = Path().apply {
    moveTo(54f, 57f)
    cubicTo(47f, 42f, 36f, 40f, 31f, 44f)
    cubicTo(33f, 54f, 43f, 60f, 54f, 57f)
    close()
}
private val rightLeaf = Path().apply {
    moveTo(54f, 57f)
    cubicTo(61f, 42f, 72f, 40f, 77f, 44f)
    cubicTo(75f, 54f, 65f, 60f, 54f, 57f)
    close()
}

private fun DrawScope.drawSunrise() {
    val k = size.minDimension / VIEW_SIZE
    scale(k, pivot = Offset.Zero) {
        translate(-VIEW_START, -VIEW_START) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to SkyTop, 0.55f to SkyMid, 1f to SkyBottom,
                    startY = 0f, endY = 108f
                ),
                topLeft = Offset.Zero,
                size = Size(108f, 108f)
            )
            drawCircle(Sun, radius = 17f, center = Offset(54f, 52f))
            drawPath(hillPath, Hill)
            drawRoundRect(Hill, topLeft = Offset(51.8f, 55f), size = Size(4.4f, 25f), cornerRadius = CornerRadius(2.2f))
            drawPath(leftLeaf, LeafDeep)
            drawPath(rightLeaf, LeafBright)
        }
    }
}

/** The colourful sunrise mark in a circle — used anywhere the mark stands alone. */
@Composable
fun SproutBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription = "ReclaimLife" }
    ) { drawSunrise() }
}

/** Small brand lockup — sprout mark + wordmark + tagline — reused across onboarding and home. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        SproutBadge(size = 32.dp)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = "ReclaimLife",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Take your time back",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Top-bar-sized lockup for screens where the brand isn't the point. The full [BrandMark] stays
 * for first-run onboarding.
 */
@Composable
fun CompactBrandMark(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        SproutBadge(size = 24.dp)
        Text(
            text = "ReclaimLife",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
