package com.example.brainrotkiller.ui.gate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.BrainRotKillerApp
import com.example.brainrotkiller.data.Mood
import com.example.brainrotkiller.ui.common.BrandMark
import com.example.brainrotkiller.ui.theme.BrainRotKillerTheme

/**
 * A quick, mandatory check-in shown by [com.example.brainrotkiller.service.ReelBlockerAccessibilityService]
 * every time Instagram or YouTube is opened — so the count is always in front of you without having
 * to remember to open ReclaimLife separately. Tapping through reveals the app underneath (taskAffinity
 * is empty on this activity, same trick as BlockActivity).
 */
class GateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BrainRotKillerApp
        setContent {
            BrainRotKillerTheme {
                val dailyLimit by app.settingsRepository.dailyReelLimit.collectAsState(initial = 0)
                val todayCount by app.reelUsageRepository.todayCount.collectAsState(initial = 0)
                val extraAllowance by app.reelUsageRepository.todayExtraAllowance.collectAsState(initial = 0)
                val nickname by app.settingsRepository.nickname.collectAsState(initial = "")

                BackHandler { finish() }
                GateScreen(
                    dailyLimit = dailyLimit + extraAllowance,
                    todayCount = todayCount,
                    nickname = nickname.trim(),
                    onContinue = { finish() }
                )
            }
        }
    }
}

@Composable
private fun GateScreen(dailyLimit: Int, todayCount: Int, nickname: String, onContinue: () -> Unit) {
    val remaining = (dailyLimit - todayCount).coerceAtLeast(0)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandMark(modifier = Modifier.padding(bottom = 20.dp))
                Text(text = Mood.forProgress(todayCount, dailyLimit).emoji, fontSize = 36.sp)
                Text(
                    text = if (nickname.isEmpty()) "Before you scroll…" else "Before you scroll, $nickname…",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "$todayCount / $dailyLimit reels today",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                )
                LinearProgressIndicator(
                    progress = { if (dailyLimit <= 0) 0f else (todayCount.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                Text(
                    text = "$remaining left before you're done for today. Make them count.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
        }
    }
}
