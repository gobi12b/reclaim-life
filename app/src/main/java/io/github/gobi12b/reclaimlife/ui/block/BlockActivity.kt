package io.github.gobi12b.reclaimlife.ui.block

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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.Mood
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.formatPauseRemaining
import io.github.gobi12b.reclaimlife.ui.common.OwnScreens
import io.github.gobi12b.reclaimlife.ui.common.SproutBadge
import io.github.gobi12b.reclaimlife.ui.common.perAppReelsLine
import io.github.gobi12b.reclaimlife.ui.replacement.SwapSession
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Shown by [io.github.gobi12b.reclaimlife.service.ReelBlockerAccessibilityService] when a limit is
 * reached. Habit replacement rather than a bare stop: it opens straight into the 2-minute swap
 * the user picked, then a short summary. Launched over Instagram/YouTube directly (not a system
 * overlay, and with an empty taskAffinity so finishing it reveals whatever app was underneath),
 * so it needs no extra "draw over other apps" permission.
 *
 * The hourly limit ([EXTRA_HOURLY_UNBLOCK_AT_MS] set) has no "a few more" ladder: finishing the
 * swap is the break, so it starts the hour afresh and goes back to the app. Skipping it leaves a
 * countdown until the limit lifts on its own.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val limit = intent.getIntExtra(EXTRA_DAILY_LIMIT, 0)
        val hourlyLimit = intent.getIntExtra(EXTRA_HOURLY_LIMIT, 0)
        val hourlyUnblockAtMs = intent.getLongExtra(EXTRA_HOURLY_UNBLOCK_AT_MS, 0L)
        val app = application as ReclaimLifeApp
        setContent {
            ReclaimLifeTheme {
                val nickname by app.settingsRepository.nickname.collectAsState(initial = "")
                // Null until loaded, so the swap doesn't start as the default and then jump.
                val activity by app.settingsRepository.replacementActivity.collectAsState(initial = null)
                val deck by app.settingsRepository.flashcardDeck.collectAsState(initial = null)
                BackHandler { goHome() }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val loadedActivity = activity
                    val loadedDeck = deck
                    if (loadedActivity != null && loadedDeck != null) {
                        BlockFlow(
                            dailyLimit = limit,
                            hourlyLimit = hourlyLimit,
                            hourlyUnblockAtMs = hourlyUnblockAtMs,
                            nickname = nickname,
                            activity = loadedActivity,
                            deck = loadedDeck,
                            onGoHome = { goHome() },
                            onContinueToApp = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        OwnScreens.onScreenStarted()
    }

    override fun onStop() {
        super.onStop()
        OwnScreens.onScreenStopped()
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
        const val EXTRA_HOURLY_LIMIT = "hourly_limit"
        const val EXTRA_HOURLY_UNBLOCK_AT_MS = "hourly_unblock_at_ms"
        /**
         * Reels granted by each "a few more" ask, in order. They shrink while what's asked grows:
         * the first two follow a finished 2-minute swap, the last a short walk.
         */
        val EXTRA_GRANTS = listOf(10, 5, 5)
        const val WALK_TARGET_STEPS = 200
        /** When steps can't be counted (permission denied / no sensor), the walk is a timed wait instead. */
        const val WALK_FALLBACK_SECONDS = 120
        val MAX_EXTRA_ASKS = EXTRA_GRANTS.size
    }
}

/**
 * SWAP → SUMMARY is the normal path. Asking for more on the daily summary walks a ladder
 * ([BlockActivity.EXTRA_GRANTS]): the first two extras follow a finished 2-minute swap — the
 * opening one counts, otherwise it's [Stage.EARN_SWAP] — and the last follows a short walk.
 */
private enum class Stage { SWAP, SUMMARY, EARN_SWAP, WALK }

@Composable
private fun BlockFlow(
    dailyLimit: Int,
    hourlyLimit: Int,
    hourlyUnblockAtMs: Long,
    nickname: String,
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    onGoHome: () -> Unit,
    onContinueToApp: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ReclaimLifeApp
    val scope = rememberCoroutineScope()
    val name = nickname.trim()
    val hourly = hourlyUnblockAtMs > 0L

    var stage by remember { mutableStateOf(Stage.SWAP) }
    var swapFinished by remember { mutableStateOf(false) }
    val attemptsToday by app.reelUsageRepository.todayExtraAttempts.collectAsState(initial = 0)
    val todayCount by app.reelUsageRepository.todayCount.collectAsState(initial = dailyLimit)
    val extraAllowance by app.reelUsageRepository.todayExtraAllowance.collectAsState(initial = 0)
    val countsByApp by app.reelUsageRepository.todayCountsByApp.collectAsState(initial = emptyMap())

    fun grantAndContinue(amount: Int) {
        scope.launch {
            app.reelUsageRepository.grantExtraAndGet(amount)
            onContinueToApp()
        }
    }

    /** The hourly block's way through: a finished swap was the break, so the hour starts afresh. */
    fun takeBreakAndContinue() {
        scope.launch {
            app.reelUsageRepository.clearHourlyWindow()
            onContinueToApp()
        }
    }

    val nextGrant = BlockActivity.EXTRA_GRANTS.getOrElse(attemptsToday) { 0 }
    val nextNeedsWalk = attemptsToday >= BlockActivity.MAX_EXTRA_ASKS - 1

    fun onAskForMore() {
        when {
            nextNeedsWalk -> stage = Stage.WALK
            swapFinished -> grantAndContinue(nextGrant)
            else -> stage = Stage.EARN_SWAP
        }
    }

    when (stage) {
        Stage.SWAP -> SwapSession(
            activity = activity,
            deck = deck,
            headline = if (hourly) "Time for a little break" else "That's your reels for today",
            subtitle = if (hourly) {
                "2 minutes of something better, then reels are yours again" + if (name.isEmpty()) "." else ", $name."
            } else {
                "Here's 2 minutes of something better instead" + if (name.isEmpty()) "." else ", $name."
            },
            finishLabel = if (hourly) "Back to reels" else "Done",
            onFinish = {
                if (hourly) {
                    takeBreakAndContinue()
                } else {
                    swapFinished = true
                    stage = Stage.SUMMARY
                }
            },
            onSkip = { stage = Stage.SUMMARY }
        )
        Stage.SUMMARY -> if (hourly) {
            HourlySummary(
                hourlyLimit = hourlyLimit,
                unblockAtMs = hourlyUnblockAtMs,
                activity = activity,
                onGoHome = onGoHome,
                onTakeBreak = { stage = Stage.SWAP },
                onContinueToApp = onContinueToApp
            )
        } else {
            DailySummary(
                dailyLimit = dailyLimit,
                todayCount = todayCount,
                extraAllowance = extraAllowance,
                perAppLine = perAppReelsLine(context, countsByApp),
                name = name,
                swapFinished = swapFinished,
                activity = activity,
                attemptsToday = attemptsToday,
                nextGrant = nextGrant,
                nextNeedsWalk = nextNeedsWalk,
                onGoHome = onGoHome,
                onAnotherSwap = { stage = Stage.SWAP },
                onAskForMore = ::onAskForMore
            )
        }
        // No skip here: the two minutes are what the extra is for. "Never mind" still leaves.
        Stage.EARN_SWAP -> SwapSession(
            activity = activity,
            deck = deck,
            headline = "One more swap first",
            subtitle = "Spend 2 minutes on this, and $nextGrant more reels are yours.",
            finishLabel = "Get my $nextGrant reels",
            onFinish = { grantAndContinue(nextGrant) },
            onSkip = { stage = Stage.SUMMARY },
            skipLabel = "Never mind"
        )
        Stage.WALK -> WalkContent(
            name = name,
            grant = nextGrant,
            onBail = { stage = Stage.SUMMARY },
            onWalkComplete = { grantAndContinue(nextGrant) }
        )
    }
}

@Composable
private fun SummaryScaffold(content: @Composable () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SproutBadge(modifier = Modifier.padding(bottom = 12.dp), size = 56.dp)
            content()
        }
    }
}

@Composable
private fun DailySummary(
    dailyLimit: Int,
    todayCount: Int,
    extraAllowance: Int,
    perAppLine: String?,
    name: String,
    swapFinished: Boolean,
    activity: ReplacementActivity,
    attemptsToday: Int,
    nextGrant: Int,
    nextNeedsWalk: Boolean,
    onGoHome: () -> Unit,
    onAnotherSwap: () -> Unit,
    onAskForMore: () -> Unit
) {
    val canAskForMore = attemptsToday < BlockActivity.MAX_EXTRA_ASKS
    SummaryScaffold {
        Text(
            text = Mood.DONE.emoji,
            fontSize = 32.sp,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clearAndSetSemantics { contentDescription = "Mood: ${Mood.DONE.label}" }
        )
        Text(
            text = "$todayCount / ${dailyLimit + extraAllowance} reels" +
                if (extraAllowance > 0) " ($dailyLimit + $extraAllowance extra)" else "",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // The same split as Home and Limits, so the total never looks like it came from nowhere.
        if (perAppLine != null) {
            Text(
                text = perAppLine,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (swapFinished) "Nice — that was 2 minutes for you." else "That's your reels for today.",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "The rest of today is yours" + (if (name.isEmpty()) "" else ", $name") +
                ". Reels will be here tomorrow.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
        )
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
            Text("Back to my day")
        }
        OutlinedButton(onClick = onAnotherSwap, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Another 2 minutes of ${activity.label.lowercase()}")
        }
        if (canAskForMore) {
            TextButton(onClick = onAskForMore, modifier = Modifier.padding(top = 8.dp)) {
                Text("I'd like $nextGrant more")
            }
            // The ladder is spelled out so what the next extra asks is known before tapping.
            Text(
                text = "Extras today: $attemptsToday of ${BlockActivity.MAX_EXTRA_ASKS} — " + when {
                    nextNeedsWalk -> "the last one comes after a short walk."
                    swapFinished -> "your 2 minutes earned the next one."
                    else -> "the next one comes after a 2-minute swap."
                },
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = "That's all the extras for today. See you tomorrow 🌱",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun HourlySummary(
    hourlyLimit: Int,
    unblockAtMs: Long,
    activity: ReplacementActivity,
    onGoHome: () -> Unit,
    onTakeBreak: () -> Unit,
    onContinueToApp: () -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(unblockAtMs) {
        while (nowMs < unblockAtMs) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    val cooledDown = nowMs >= unblockAtMs
    val unblockAt = remember(unblockAtMs) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(unblockAtMs)) }

    SummaryScaffold {
        Text(
            text = "$hourlyLimit reels in the last hour",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Reels are taking a short break.",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = if (cooledDown) {
                "They're ready again whenever you are."
            } else {
                "They'll be back at $unblockAt — or sooner, after a 2-minute break."
            },
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        if (!cooledDown) {
            Text(
                text = formatPauseRemaining(unblockAtMs - nowMs),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
            Text("Back to my day")
        }
        if (cooledDown) {
            OutlinedButton(onClick = onContinueToApp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Back to reels")
            }
        } else {
            OutlinedButton(onClick = onTakeBreak, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("2 minutes of ${activity.label.lowercase()}, then reels")
            }
        }
    }
}

@Composable
private fun WalkContent(name: String, grant: Int, onBail: () -> Unit, onWalkComplete: () -> Unit) {
    val context = LocalContext.current
    val hasRecognitionPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    var permissionGranted by remember { mutableStateOf(hasRecognitionPermission) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    // Asked only from the "Count my steps" button, under text saying why, so the system prompt
    // never appears before the reason for it (the walk) has been read.

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

    val canCountSteps = permissionGranted && sensorAvailable
    // Without step counting, "I'm done" used to be tappable immediately — a one-tap bypass of the
    // last gate. It now waits out a fixed walk-length timer instead.
    var fallbackSecondsLeft by remember { mutableIntStateOf(BlockActivity.WALK_FALLBACK_SECONDS) }
    LaunchedEffect(canCountSteps) {
        if (canCountSteps) return@LaunchedEffect
        while (fallbackSecondsLeft > 0) {
            delay(1000)
            fallbackSecondsLeft--
        }
    }
    val walkDone = if (canCountSteps) stepsWalked >= target else fallbackSecondsLeft == 0

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
                text = "Stretch your legs" + if (name.isEmpty()) "" else ", $name",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Take a short $target-step walk — around the room or down the street. When you're " +
                    "back, $grant more reels are yours. This is today's last extra.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            if (canCountSteps) {
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
                    text = if (!sensorAvailable) {
                        "This phone can't count steps, so just enjoy the walk — this unlocks when the timer runs out."
                    } else {
                        "To count your $target steps, ReclaimLife needs the Physical Activity permission. " +
                            "Steps are only counted on this screen and never saved. Or skip it and just " +
                            "walk — this unlocks when the timer runs out."
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (sensorAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("Count my steps")
                    }
                }
                Text(
                    text = "%d:%02d".format(fallbackSecondsLeft / 60, fallbackSecondsLeft % 60),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            Button(
                onClick = onWalkComplete,
                enabled = walkDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (walkDone) "Walk done — continue" else "Keep walking…")
            }
            TextButton(onClick = onBail, modifier = Modifier.padding(top = 8.dp)) {
                Text("Maybe later")
            }
        }
    }
}
