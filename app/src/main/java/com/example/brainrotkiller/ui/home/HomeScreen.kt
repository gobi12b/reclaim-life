package com.example.brainrotkiller.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.brainrotkiller.service.isReelBlockerServiceEnabled
import com.example.brainrotkiller.ui.common.BrandMark

@Composable
fun HomeScreen(
    dailyLimit: Int,
    todayCount: Int,
    onLimitChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var pendingLimit by remember(dailyLimit) { mutableIntStateOf(dailyLimit) }
    var isEditingLimit by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(isReelBlockerServiceEnabled(context)) }
    val reachedLimit = todayCount >= dailyLimit

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isReelBlockerServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(modifier = Modifier.padding(bottom = 28.dp))
            Text(
                text = "Today",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$todayCount / $dailyLimit reels",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            LinearProgressIndicator(
                progress = { (todayCount.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            Text(
                text = if (reachedLimit) {
                    "You've hit today's limit. That's the whole point — go live your life."
                } else {
                    "Be true to life, not the algorithm. You're the one keeping score."
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (!isEditingLimit) {
                Text(
                    text = "Daily limit: $dailyLimit reels",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = {
                        pendingLimit = dailyLimit
                        isEditingLimit = true
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                ) {
                    Text("Edit limit")
                }
            } else {
                Text(
                    text = "New limit: $pendingLimit reels",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Improve day by day — lower it once the current number feels easy.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }
        }
    }
}
