package com.example.brainrotkiller.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.data.MIN_DAILY_REEL_LIMIT
import com.example.brainrotkiller.data.Mood
import com.example.brainrotkiller.service.isReelBlockerServiceEnabled
import com.example.brainrotkiller.ui.common.BrandMark

@Composable
fun HomeScreen(
    dailyLimit: Int,
    todayCount: Int,
    extraAllowance: Int,
    nickname: String,
    daysWithinLimit: Int,
    daysExceededLimit: Int,
    onLimitChange: (Int) -> Unit
) {
    val name = nickname.trim()
    val context = LocalContext.current
    var pendingLimit by remember(dailyLimit) { mutableIntStateOf(dailyLimit) }
    var isEditingLimit by remember { mutableStateOf(false) }
    var showEditIntroDialog by remember { mutableStateOf(false) }
    val effectiveLimit = dailyLimit + extraAllowance
    val reachedLimit = todayCount >= effectiveLimit

    // Re-derived (not cached) on every resume and every reel count change, so a permission
    // granted outside this composable — in system Settings, or by the app updating — is
    // reflected immediately instead of leaving a stale "off" nudge on screen.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val serviceEnabled = remember(resumeTick, todayCount) { isReelBlockerServiceEnabled(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(modifier = Modifier.padding(bottom = 28.dp))

            if (!isEditingLimit) {
                val mood = remember(todayCount, effectiveLimit) { Mood.forProgress(todayCount, effectiveLimit) }
                Text(text = mood.emoji, fontSize = 40.sp)
                Text(
                    text = "Today · ${mood.label}",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$todayCount / $effectiveLimit reels",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                LinearProgressIndicator(
                    progress = { (todayCount.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Text(
                    text = when {
                        reachedLimit && name.isNotEmpty() ->
                            "You've hit today's limit, $name. That's the whole point — go live your life."
                        reachedLimit -> "You've hit today's limit. That's the whole point — go live your life."
                        name.isNotEmpty() -> "Be true to life, not the algorithm, $name. You're the one keeping score."
                        else -> "Be true to life, not the algorithm. You're the one keeping score."
                    },
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = "Daily limit: $dailyLimit reels",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = {
                        pendingLimit = dailyLimit
                        isEditingLimit = true
                        showEditIntroDialog = true
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                ) {
                    Text("Edit limit")
                }

                if (daysWithinLimit > 0 || daysExceededLimit > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        DayTally(
                            count = daysWithinLimit,
                            label = if (daysWithinLimit == 1) "day within limit" else "days within limit",
                            color = MaterialTheme.colorScheme.primary
                        )
                        DayTally(
                            count = daysExceededLimit,
                            label = if (daysExceededLimit == 1) "day over limit" else "days over limit",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Text(
                    text = "New limit: $pendingLimit reels",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when {
                        pendingLimit > dailyLimit ->
                            "Going up isn't progress — that's the algorithm talking, not you. " +
                                "You'll feel worse tonight, not better."
                        pendingLimit < dailyLimit ->
                            "That's the direction. Smaller numbers get easier by day three."
                        else -> "Improve day by day — lower it once the current number feels easy."
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = if (pendingLimit > dailyLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Slider(
                    value = pendingLimit.toFloat(),
                    onValueChange = { pendingLimit = it.toInt() },
                    valueRange = MIN_DAILY_REEL_LIMIT.toFloat()..150f,
                    steps = 148,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Row(modifier = Modifier.padding(bottom = 24.dp)) {
                    TextButton(onClick = { isEditingLimit = false }) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        onLimitChange(pendingLimit)
                        isEditingLimit = false
                    }) {
                        Text("Save")
                    }
                }
            }

            if (showEditIntroDialog) {
                AlertDialog(
                    onDismissRequest = { showEditIntroDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showEditIntroDialog = false }) {
                            Text("Got it")
                        }
                    },
                    title = { Text("Improve day by day") },
                    text = {
                        Text(
                            "Lower it once the current number feels easy. This only works if the " +
                                "number keeps getting smaller — not bigger."
                        )
                    }
                )
            }

            if (!serviceEnabled) {
                Text(
                    text = "Accessibility access is off — reels won't be counted or blocked until it's on.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Turn on Accessibility access")
                }
                Text(
                    text = "In Accessibility, find ReclaimLife under Downloaded apps and turn it on. " +
                        "If the switch won't move: Settings → Apps → ReclaimLife → ⋮ menu → " +
                        "Allow restricted settings, then try again.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DayTally(count: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            text = label,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
