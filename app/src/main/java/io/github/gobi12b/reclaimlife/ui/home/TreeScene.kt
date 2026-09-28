package io.github.gobi12b.reclaimlife.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.data.TreeState
import io.github.gobi12b.reclaimlife.data.treeNameTitle
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import io.github.gobi12b.reclaimlife.ui.theme.reclaim

/**
 * The user's own tree, in its little sky, for screens outside Home (the pause before opening and
 * the limit screens), with its name and one line about what this moment means for it.
 */
@Composable
internal fun TreeScene(tree: TreeState, line: String?, reducedMotion: Boolean, modifier: Modifier = Modifier, droop: Float = 0f) {
    val colors = MaterialTheme.reclaim
    val resting = tree.todayRest != null
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .size(200.dp, 210.dp)
                .clip(RoundedCornerShape(Radii.hero))
                .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
        ) {
            HeroBackdrop(Modifier.fillMaxSize(), resting = resting, reducedMotion = reducedMotion)
            GrowthTree(
                tree.stage,
                tree.details,
                resting = resting,
                reducedMotion = reducedMotion,
                size = TreeSize.HERO,
                tappable = true,
                keepsakes = tree.keepsakes,
                sprigs = tree.sprigs,
                droop = droop
            )
        }
        Text(
            treeNameTitle(tree.name) + if (tree.streak > 0) " · ${tree.streak}-day streak" else "",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (line != null) {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
