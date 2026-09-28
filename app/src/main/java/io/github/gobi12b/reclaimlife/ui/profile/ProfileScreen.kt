package io.github.gobi12b.reclaimlife.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.Keepsake
import io.github.gobi12b.reclaimlife.data.TreeState
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
import java.text.NumberFormat
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
    val settings = (context.applicationContext as ReclaimLifeApp).settingsRepository
    val nickname by settings.nickname.collectAsState(initial = "")
    val photoVersion by settings.profilePhotoVersion.collectAsState(initial = 0L)
    val photo by produceState<ImageBitmap?>(null, photoVersion) {
        value = if (photoVersion > 0L) ProfilePhoto.load(context)?.asImageBitmap() else null
    }
    var editing by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { if (ProfilePhoto.save(context, uri)) settings.setProfilePhotoVersion(System.currentTimeMillis()) }
    }
    val choosePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ProfileHeader(nickname.trim(), photo, onPhoto = choosePhoto, onEdit = { editing = true })
        val tree = progress?.tree ?: return@Column
        val savedMs = progress.sinceStartedMs.takeIf { progress.hasBaseline && it >= 60_000L }

        // Recorded as it draws, so Share saves exactly what's on screen. The rounded clip sits
        // outside the recording: on screen it's a card, in the image it's full-bleed.
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(Radii.hero))
                .drawWithContent {
                    card.record { this@drawWithContent.drawContent() }
                    drawLayer(card)
                }
        ) {
            ShareCard(tree, savedMs, nickname.trim(), photo, reducedMotion)
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

    if (editing) {
        EditProfileDialog(
            current = nickname,
            hasPhoto = photo != null,
            onSave = { name ->
                scope.launch { settings.setNickname(name) }
                editing = false
            },
            onChangePhoto = {
                editing = false
                choosePhoto()
            },
            onRemovePhoto = {
                ProfilePhoto.delete(context)
                scope.launch { settings.setProfilePhotoVersion(0L) }
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
}

/** Photo and name, top of the tab. Tapping the photo picks a new one; Edit changes the name. */
@Composable
private fun ProfileHeader(name: String, photo: ImageBitmap?, onPhoto: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Avatar(
            name,
            photo,
            size = 64.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = if (photo == null) "Add a photo" else "Change photo", role = Role.Button, onClick = onPhoto)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                name.ifEmpty { "Your profile" },
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily),
                modifier = Modifier.semantics { heading() }
            )
            if (photo == null) {
                Text("Tap the circle to add a photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
    }
}

/** The photo in a circle, or the name's first letter when there's no photo. */
@Composable
internal fun Avatar(name: String, photo: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.primaryContainer)
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "+",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    current: String,
    hasPhoto: Boolean,
    onSave: (String) -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 20) text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = onChangePhoto) { Text(if (hasPhoto) "Change photo" else "Add photo") }
                    if (hasPhoto) TextButton(onClick = onRemovePhoto) { Text("Remove photo") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The picture people share, in a fixed 4:5 portrait so it posts cleanly. The tree fills the
 * top; below it one story: a cheer, the minutes saved (or days grown before there's a
 * baseline), and one quiet line of detail.
 */
@Composable
private fun ShareCard(tree: TreeState, savedMs: Long?, nickname: String, photo: ImageBitmap?, reducedMotion: Boolean) {
    val colors = MaterialTheme.reclaim
    val minutes = savedMs?.let { it / 60_000L }
    val detail = buildList {
        if (minutes != null) add(if (tree.grewDays == 1) "1 day grown" else "${tree.grewDays} days grown")
        if (tree.streak > 0) add("${tree.streak}-day streak")
        add(treeNameTitle(tree.name) + " the " + tree.stage.label.lowercase())
    }.joinToString(" · ")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
    ) {
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            if (photo != null) Avatar(nickname, photo, size = 40.dp, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                if (nickname.isEmpty()) "You're awesome!" else "$nickname, you're awesome!",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onHeroMuted,
                textAlign = TextAlign.Center
            )
            Text(
                if (minutes != null) NumberFormat.getIntegerInstance().format(minutes) + if (minutes == 1L) " minute" else " minutes"
                else if (tree.grewDays == 1) "1 day" else "${tree.grewDays} days",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = DisplayFamily),
                color = colors.onHero,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                if (minutes != null) "saved from scrolling" else "of growing instead of scrolling",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onHero
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onHeroMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                "ReclaimLife",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = DisplayFamily),
                color = colors.onHeroMuted,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
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
