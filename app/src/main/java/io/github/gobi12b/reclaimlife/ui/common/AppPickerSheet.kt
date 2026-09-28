package io.github.gobi12b.reclaimlife.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.MAX_TRACKED_APPS
import io.github.gobi12b.reclaimlife.data.SUGGESTED_APPS
import io.github.gobi12b.reclaimlife.data.TargetApps
import io.github.gobi12b.reclaimlife.data.TrackedApp
import io.github.gobi12b.reclaimlife.data.toggleTrackedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val packageName: String, val label: String, val suggested: Boolean)

/** Launchable apps on the phone, popular feed apps first, then the rest alphabetically. */
private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val installed = pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName }
        .filter { it != context.packageName }
        .toSet()
    val suggested = SUGGESTED_APPS.map { it.first }.distinct().filter { it in installed }
    val others = (installed - suggested.toSet())
        .map { InstalledApp(it, appLabel(context, it), suggested = false) }
        .sortedBy { it.label.lowercase() }
    return suggested.map { InstalledApp(it, appLabel(context, it), suggested = true) } + others
}

/**
 * Pick the apps that get the open-pause and appear in the stats. Changes apply on Save; up to
 * [MAX_TRACKED_APPS], one per chart colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(current: List<TrackedApp>, onDismiss: () -> Unit, onSave: (List<TrackedApp>) -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    val full = pending.size >= MAX_TRACKED_APPS

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Your apps", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(
                "Each one shows up in your stats, and can get Pause before opening. " +
                    "Reels are counted in Instagram and YouTube only. Removing an app keeps its history.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Text(
                "${pending.size} of $MAX_TRACKED_APPS chosen" + if (full) " — remove one to add another" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            val list = apps
            if (list == null) {
                Text("Loading apps…", modifier = Modifier.padding(vertical = 24.dp))
            } else {
                val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp).padding(top = 4.dp)) {
                    items(filtered, key = { it.packageName }) { item ->
                        val checked = pending.any { it.packageName == item.packageName }
                        AppRow(
                            item = item,
                            checked = checked,
                            enabled = checked || !full,
                            onToggle = { pending = toggleTrackedApp(pending, item.packageName) }
                        )
                    }
                }
            }
            Row(modifier = Modifier.align(Alignment.End).padding(top = 12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onSave(pending) }, enabled = pending.isNotEmpty()) { Text("Save") }
            }
        }
    }
}

@Composable
private fun AppRow(item: InstalledApp, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val icon = rememberAppIcon(item.packageName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clearAndSetSemantics { }) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                if (TargetApps.countsReels(item.packageName)) "Reels and time" else "Time only",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
