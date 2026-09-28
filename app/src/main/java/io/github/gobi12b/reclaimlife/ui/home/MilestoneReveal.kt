package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.Keepsake
import io.github.gobi12b.reclaimlife.data.keepsakeRevealLine
import io.github.gobi12b.reclaimlife.ui.theme.DisplayFamily

/** The one-time surprise when a milestone joins the tree: it pops in, with one line and one button. */
@Composable
internal fun MilestoneReveal(k: Keepsake, treeName: String, reducedMotion: Boolean, onDone: () -> Unit) {
    val pop = remember(k) { Animatable(if (reducedMotion) 1f else 0.3f) }
    LaunchedEffect(k) { if (!reducedMotion) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f)) }
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Text("Lovely") } },
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "New milestone",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    KeepsakeIcon(k, earned = true, silhouette = null, reducedMotion = reducedMotion, size = 84.dp)
                }
                Text(k.label, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily))
                Text(
                    keepsakeRevealLine(k, treeName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    )
}
