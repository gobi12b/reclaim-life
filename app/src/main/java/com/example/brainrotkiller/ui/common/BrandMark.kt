package com.example.brainrotkiller.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The app's sprout mark — a stem with two leaves, standing for growth rather than the feed. */
@Composable
fun SproutGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val unit = size.width * 0.9f
        val cx = size.width / 2f
        val cy = size.height / 2f + unit * 0.12f

        val stemW = unit * 0.16f
        val stemTop = cy - unit * 0.12f
        val stemBottom = cy + unit * 0.62f
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - stemW / 2f, stemTop),
            size = Size(stemW, stemBottom - stemTop),
            cornerRadius = CornerRadius(stemW / 2f)
        )

        val leafLen = unit * 0.95f
        val leafW = unit * 0.42f
        val attachY = stemTop - unit * 0.05f

        fun leaf(offsetX: Float, angleDeg: Float) {
            val pivot = Offset(cx + offsetX, attachY)
            rotate(angleDeg, pivot = pivot) {
                drawOval(
                    color = color,
                    topLeft = Offset(pivot.x - leafLen / 2f, pivot.y - leafW / 2f),
                    size = Size(leafLen, leafW)
                )
            }
        }
        leaf(-unit * 0.30f, -38f)
        leaf(unit * 0.30f, 38f)
    }
}

/** The sprout mark on a solid circle — same green-and-white look as the launcher icon, used anywhere the mark stands alone. */
@Composable
fun SproutBadge(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SproutGlyph(modifier = Modifier.size(size * 0.64f), color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Small brand lockup — sprout mark + wordmark + tagline — reused across onboarding and home. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        SproutBadge(size = 28.dp)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = "ReclaimLife",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Brain Rot Killer",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Top-bar-sized lockup for screens where the brand isn't the point (Home). The full [BrandMark]
 * stays for first-run onboarding; everywhere else the sprout's mood carries the personality.
 */
@Composable
fun CompactBrandMark(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        SproutBadge(size = 20.dp)
        Text(
            text = "ReclaimLife",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
