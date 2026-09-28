package io.github.gobi12b.reclaimlife.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.gobi12b.reclaimlife.data.Keepsake
import io.github.gobi12b.reclaimlife.data.TreeState
import io.github.gobi12b.reclaimlife.data.formatSaved
import io.github.gobi12b.reclaimlife.data.treeNameTitle
import io.github.gobi12b.reclaimlife.ui.common.rememberProgress
import io.github.gobi12b.reclaimlife.ui.common.rememberReducedMotion
import io.github.gobi12b.reclaimlife.ui.home.GrowthTree
import io.github.gobi12b.reclaimlife.ui.home.HeroBackdrop
import io.github.gobi12b.reclaimlife.ui.home.KeepsakeIcon
import io.github.gobi12b.reclaimlife.ui.home.SectionHeading
import io.github.gobi12b.reclaimlife.ui.home.TreeSize
import io.github.gobi12b.reclaimlife.ui.theme.DisplayFamily
import io.github.gobi12b.reclaimlife.ui.theme.Radii
import io.github.gobi12b.reclaimlife.ui.theme.reclaim
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the share sheet carries along with the picture. The irony is the point. */
private const val SHARE_CAPTION = "I grew a tree instead of scrolling reels. Yes, I'm posting it on social media."

/**
 * Your tree and everything it has earned: a card that doubles as the share image, the
 * milestones (earned ones named, the rest still a surprise), and a Share button.
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val progress = rememberProgress()
    val reducedMotion = rememberReducedMotion()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val card = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        val tree = progress?.tree ?: return@Column
        val savedMs = progress.sinceStartedMs.takeIf { progress.hasBaseline && it >= 60_000L }

        // Recorded as it draws, so Share can save exactly what's on screen.
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .drawWithContent {
                    card.record { this@drawWithContent.drawContent() }
                    drawLayer(card)
                }
        ) {
            ShareCard(tree, savedMs, reducedMotion)
        }

        Button(
            onClick = {
                if (sharing) return@Button
                sharing = true
                scope.launch {
                    runCatching { shareImage(context, card.toImageBitmap().asAndroidBitmap()) }
                    sharing = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 52.dp)
        ) {
            Text("Share my tree")
        }

        Spacer(Modifier.height(32.dp))
        MilestoneGrid(tree, reducedMotion)
        Spacer(Modifier.height(24.dp))
    }
}

/** The picture people share: the tree in its sky, its name, and a few honest numbers. */
@Composable
private fun ShareCard(tree: TreeState, savedMs: Long?, reducedMotion: Boolean) {
    val colors = MaterialTheme.reclaim
    val planted = remember(tree.plantedAtMs) {
        tree.plantedAtMs?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    }
    // A solid margin around the rounded card, so the saved image has no transparent corners.
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(4.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.hero))
                .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
        ) {
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxWidth().height(240.dp)) {
                HeroBackdrop(Modifier.fillMaxSize(), resting = tree.todayRest != null, reducedMotion = true)
                GrowthTree(
                    tree.stage,
                    tree.details,
                    resting = tree.todayRest != null,
                    reducedMotion = reducedMotion,
                    size = TreeSize.SHEET,
                    keepsakes = tree.keepsakes,
                    sprigs = tree.sprigs
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.heroGround)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    treeNameTitle(tree.name),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily),
                    color = colors.onHero
                )
                Text(
                    tree.stage.label + (planted?.let { " · planted $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroMuted
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    CardStat(tree.grewDays.toString(), if (tree.grewDays == 1) "day grown" else "days grown")
                    CardStat(tree.streak.toString(), "day streak")
                    if (savedMs != null) CardStat(formatSaved(savedMs), "saved")
                }
                Text(
                    "ReclaimLife",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onHeroMuted,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun CardStat(value: String, label: String) {
    val colors = MaterialTheme.reclaim
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.onHero)
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onHeroMuted)
    }
}

/** Earned milestones by name; the rest are "?", because what comes next is a surprise. */
@Composable
private fun MilestoneGrid(tree: TreeState, reducedMotion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeading("Milestones", Modifier.weight(1f))
        Text(
            "${tree.keepsakes.size} of ${Keepsake.entries.size}",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant
        )
    }
    Keepsake.entries.chunked(4).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            row.forEach { k ->
                val earned = k in tree.keepsakes
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = if (earned) "${k.label}, on your tree" else "A surprise, not earned yet" }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (earned) scheme.primaryContainer else scheme.surfaceContainerHigh,
                                RoundedCornerShape(Radii.panel)
                            )
                    ) {
                        if (earned) {
                            KeepsakeIcon(k, earned = true, silhouette = null, reducedMotion = reducedMotion, size = 48.dp)
                        } else {
                            Text("?", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        if (earned) k.label else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp).width(80.dp)
                    )
                }
            }
            // Keep the last row's tiles the same width as the rows above.
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Saves the card to the cache and opens the share sheet with it. Nothing is sent by ReclaimLife itself. */
private suspend fun shareImage(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "my-tree.png").also { out ->
            val soft = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            FileOutputStream(out).use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, SHARE_CAPTION)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your tree"))
}
