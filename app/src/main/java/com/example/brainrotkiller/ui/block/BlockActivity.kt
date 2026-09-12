package com.example.brainrotkiller.ui.block

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.BrainRotKillerApp
import com.example.brainrotkiller.ui.common.BrandMark
import com.example.brainrotkiller.ui.common.SproutBadge
import com.example.brainrotkiller.ui.theme.BrainRotKillerTheme
import kotlinx.coroutines.launch

/**
 * Full-screen stop sign shown by [com.example.brainrotkiller.service.ReelBlockerAccessibilityService]
 * once the daily reel limit is hit. Launched over Instagram/YouTube directly (not a system overlay,
 * and with an empty taskAffinity so finishing it reveals whatever app was underneath), so it needs
 * no extra "draw over other apps" permission.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val limit = intent.getIntExtra(EXTRA_DAILY_LIMIT, 0)
        val app = application as BrainRotKillerApp
        setContent {
            BrainRotKillerTheme {
                val nickname by app.settingsRepository.nickname.collectAsState(initial = "")
                BackHandler { goHome() }
                BlockScreen(
                    dailyLimit = limit,
                    nickname = nickname,
                    onGoHome = { goHome() },
                    onContinueToApp = { finish() }
                )
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
        const val FIRST_GRANT_AMOUNT = 10
        const val GUILT_GRANT_AMOUNT = 2
        const val WALK_GRANT_AMOUNT = 50
        const val WALK_TARGET_STEPS = 200
    }
}

private enum class Stage { BLOCKED, GUILT, WALK }

@Composable
private fun BlockScreen(
    dailyLimit: Int,
    nickname: String,
    onGoHome: () -> Unit,
    onContinueToApp: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BrainRotKillerApp
    val scope = rememberCoroutineScope()
    val name = nickname.trim()

    var stage by remember { mutableStateOf(Stage.BLOCKED) }
    val attemptsToday by app.reelUsageRepository.todayExtraAttempts.collectAsState(initial = 0)

    fun grantAndContinue(amount: Int) {
        scope.launch {
            app.reelUsageRepository.grantExtraAndGet(amount)
            onContinueToApp()
        }
    }

    fun onAskForMore() {
        when (attemptsToday) {
            0 -> grantAndContinue(BlockActivity.FIRST_GRANT_AMOUNT)
            1 -> stage = Stage.GUILT
            else -> stage = Stage.WALK
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (stage) {
            Stage.BLOCKED -> BlockedContent(
                dailyLimit = dailyLimit,
                name = name,
                attemptsToday = attemptsToday,
                onGoHome = onGoHome,
                onAskForMore = ::onAskForMore
            )
            Stage.GUILT -> GuiltContent(
                dailyLimit = dailyLimit,
                name = name,
                onBail = onGoHome,
                onGrantAnyway = { grantAndContinue(BlockActivity.GUILT_GRANT_AMOUNT) }
            )
            Stage.WALK -> WalkContent(
                name = name,
                onBail = onGoHome,
                onWalkComplete = { grantAndContinue(BlockActivity.WALK_GRANT_AMOUNT) }
            )
        }
    }
}

@Composable
private fun BlockedContent(
    dailyLimit: Int,
    name: String,
    attemptsToday: Int,
    onGoHome: () -> Unit,
    onAskForMore: () -> Unit
) {
    val subject = if (name.isEmpty()) "You" else "$name, you"
    val canAskForMore = attemptsToday < 3
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
            SproutBadge(modifier = Modifier.padding(bottom = 16.dp), size = 56.dp)
            Text(
                text = "Daily limit reached",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$subject set today's limit at $dailyLimit reels, and hit it. " +
                    "That number only means something if you actually stop here.",
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
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
                Text("Go live my life")
            }
            if (canAskForMore) {
                TextButton(onClick = onAskForMore, modifier = Modifier.padding(top = 8.dp)) {
                    Text("I really need a few more")
                }
            } else {
                Text(
                    text = "You already used today's last extra — that was final. " +
                        "The only way to get more now is to go turn off Accessibility for ReclaimLife yourself.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun GuiltContent(dailyLimit: Int, name: String, onBail: () -> Unit, onGrantAnyway: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Be honest with yourself" + if (name.isEmpty()) "." else ", $name.",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "You said $dailyLimit. One more reel is never just one more — it's how 20 minutes " +
                    "turns into another 3 hours you won't get back. You're one tap away from binge-watching " +
                    "again, not from living.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
            )
            Button(onClick = onBail, modifier = Modifier.fillMaxWidth()) {
                Text("You're right — I'll stop")
            }
            TextButton(onClick = onGrantAnyway, modifier = Modifier.padding(top = 8.dp)) {
                Text("Give me 2 more anyway")
            }
        }
    }
}

@Composable
private fun WalkContent(name: String, onBail: () -> Unit, onWalkComplete: () -> Unit) {
    val context = LocalContext.current
    val hasRecognitionPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    var permissionGranted by remember { mutableStateOf(hasRecognitionPermission) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    val target = BlockActivity.WALK_TARGET_STEPS
    var stepsWalked by remember { mutableIntStateOf(0) }
    var sensorAvailable by remember { mutableStateOf(true) }

    if (permissionGranted) {
        DisposableEffect(Unit) {
            val sensorManager = context.getSystemService(SensorManager::class.java)
            val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (sensor == null) {
                sensorAvailable = false
                onDispose {}
            } else {
                var baseline = -1f
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val total = event.values.firstOrNull() ?: return
                        if (baseline < 0f) baseline = total
                        stepsWalked = (total - baseline).toInt().coerceAtLeast(0)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                onDispose { sensorManager.unregisterListener(listener) }
            }
        }
    }

    val walkDone = stepsWalked >= target

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Prove it" + if (name.isEmpty()) "." else ", $name.",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "If you really need more, walk $target steps first. Not a scroll — an actual walk. " +
                    "Come back when you're done and ${BlockActivity.WALK_GRANT_AMOUNT} reels will be waiting for you. " +
                    "This is the last one today — after this, the only way to get more is to go turn off " +
                    "Accessibility for ReclaimLife yourself.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            if (permissionGranted && sensorAvailable) {
                Text(
                    text = "$stepsWalked / $target steps",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { (stepsWalked.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            } else {
                Text(
                    text = "We can't count steps without the Physical Activity permission — " +
                        "go for a short walk anyway, then tell us you're done.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            Button(
                onClick = onWalkComplete,
                enabled = !permissionGranted || !sensorAvailable || walkDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (permissionGranted && sensorAvailable && !walkDone) "Keep walking…" else "I'm done — continue")
            }
            TextButton(onClick = onBail, modifier = Modifier.padding(top = 8.dp)) {
                Text("Never mind, I'll stop")
            }
        }
    }
}
