package io.github.gobi12b.reclaimlife.ui.common

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.github.gobi12b.reclaimlife.data.TargetApps

// One colour per tracked-app slot (see TrackedApp), in a fixed order. Validated with the dataviz
// palette checker against the card surfaces in each theme: distinct for colour-blind readers and
// with normal vision. Some fall under 3:1 against the card, so a colour is always shown next to
// the app's name — never on its own.
private val SlotLight = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948)
)
private val SlotDark = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767)
)

@Composable
fun slotColor(slot: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = if (dark) SlotDark else SlotLight
    return palette[slot.mod(palette.size)]
}

/** The app's name as the phone shows it, falling back to a known name for uninstalled apps. */
fun appLabel(context: Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull() ?: TargetApps.labelFor(packageName)

@Composable
fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) { appLabel(context, packageName) }
}

/** The app's launcher icon, or null if it isn't installed. */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
}
