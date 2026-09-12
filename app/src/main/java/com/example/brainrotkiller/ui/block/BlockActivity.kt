package com.example.brainrotkiller.ui.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.ui.common.BrandMark
import com.example.brainrotkiller.ui.theme.BrainRotKillerTheme

/**
 * Full-screen stop sign shown by [com.example.brainrotkiller.service.ReelBlockerAccessibilityService]
 * once the daily reel limit is hit. Launched over Instagram/YouTube directly (not a system overlay),
 * so it needs no extra "draw over other apps" permission.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val limit = intent.getIntExtra(EXTRA_DAILY_LIMIT, 0)
        setContent {
            BrainRotKillerTheme {
                BackHandler { goHome() }
                BlockScreen(dailyLimit = limit, onDone = { goHome() })
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    companion object {
        const val EXTRA_DAILY_LIMIT = "daily_limit"
    }
}

@Composable
private fun BlockScreen(dailyLimit: Int, onDone: () -> Unit) {
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
                BrandMark(modifier = Modifier.padding(bottom = 24.dp))
                Text(
                    text = "☀",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Daily limit reached",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "You set your limit at $dailyLimit reels today, and you hit it. That number only means something if you actually stop here.",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Text(
                    text = "Accountability is the whole point. Go live your life today — not the algorithm's version of it. Tomorrow you get to decide the number again.",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(onClick = onDone) {
                    Text("Go live my life")
                }
            }
        }
    }
}
