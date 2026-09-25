package com.example.brainrotkiller.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.example.brainrotkiller.data.PauseDuration
import java.io.File
import kotlinx.coroutines.delay

private const val PAUSE_PHRASE = "I want to rot my brain"

/**
 * Pausing shouldn't be a one-tap accident. Before it takes effect, they have to record themselves
 * saying it out loud and listen to the playback — hearing your own voice say something makes it
 * land differently than reading it. Every pause is time-boxed ([PauseDuration]) and auto-resumes.
 *
 * The mic permission is only requested when they tap "Start recording", so the system prompt
 * never covers this explanation before it's been read.
 */
@Composable
fun PauseRecordDialog(onDismiss: () -> Unit, onConfirmPause: (PauseDuration) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Set when they tapped Start before granting, so recording begins as soon as they allow it.
    var startAfterGrant by remember { mutableStateOf(false) }
    // "Don't allow" twice (or "don't ask again") means the system won't show the prompt anymore —
    // the only way forward is app settings.
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            startAfterGrant = false
            permanentlyDenied = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
        }
    }
    var duration by remember { mutableStateOf(PauseDuration.FIFTEEN_MINUTES) }
    val requiredRounds = if (duration == PauseDuration.REST_OF_TODAY) 2 else 1

    val audioFile = remember { File(context.cacheDir, "pause_confirm.3gp") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    /** Whether the *current* take has been played to the end (so it's already counted). */
    var hasPlayedBack by remember { mutableStateOf(false) }
    /**
     * Separate recordings that were each played back in full. "Rest of today" needs two — the
     * longest pause costs more — while 15 min / 1 hour need one. Replaying the same take again
     * doesn't count twice; a new take has to be recorded.
     */
    var roundsCompleted by remember { mutableIntStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        recordingSeconds = 0
        while (true) {
            delay(1000)
            recordingSeconds++
        }
    }

    fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        isPlaying = false
    }

    fun startRecording() {
        stopPlayback()
        errorText = null
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        val started = runCatching {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            newRecorder.setOutputFile(audioFile.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
        }.isSuccess
        if (started) {
            recorder = newRecorder
            isRecording = true
            hasPlayedBack = false
        } else {
            runCatching { newRecorder.release() }
            errorText = "Couldn't start recording. Check the mic permission and try again."
        }
    }

    fun stopRecording() {
        val stopped = runCatching {
            recorder?.stop()
            recorder?.release()
        }.isSuccess
        recorder = null
        isRecording = false
        hasRecording = stopped
        if (!stopped) errorText = "Recording didn't save — try again."
    }

    fun playBack() {
        errorText = null
        val newPlayer = MediaPlayer()
        val started = runCatching {
            newPlayer.setDataSource(audioFile.absolutePath)
            newPlayer.setOnCompletionListener {
                isPlaying = false
                if (!hasPlayedBack) roundsCompleted++
                hasPlayedBack = true
            }
            newPlayer.prepare()
            newPlayer.start()
        }.isSuccess
        if (started) {
            player = newPlayer
            isPlaying = true
        } else {
            runCatching { newPlayer.release() }
            errorText = "Couldn't play that back — try recording again."
        }
    }

    LaunchedEffect(hasPermission, startAfterGrant) {
        if (hasPermission && startAfterGrant) {
            startAfterGrant = false
            startRecording()
        }
    }

    fun onRecordTapped() {
        // Re-read it: they may have just granted it from app settings after a permanent denial.
        hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) permanentlyDenied = false
        when {
            isRecording -> stopRecording()
            hasPermission -> startRecording()
            else -> {
                startAfterGrant = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { player?.release() }
            runCatching { audioFile.delete() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Say it out loud", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Record yourself saying: “$PAUSE_PHRASE” — then play it back. " +
                        "Your future self will regret this.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                // What unlocks "Pause" is spelled out up front instead of a mystery-disabled button.
                // Picking "Rest of today" inserts the second record + play-back steps.
                val currentTake = hasRecording && !isRecording
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    ChecklistRow(number = 1, label = "Record it", done = roundsCompleted >= 1 || currentTake)
                    ChecklistRow(number = 2, label = "Play it back", done = roundsCompleted >= 1)
                    if (requiredRounds >= 2) {
                        ChecklistRow(
                            number = 3,
                            label = "Record it again",
                            done = roundsCompleted >= 2 || (roundsCompleted == 1 && currentTake && !hasPlayedBack)
                        )
                        ChecklistRow(number = 4, label = "Play that back too", done = roundsCompleted >= 2)
                    }
                    ChecklistRow(number = requiredRounds * 2 + 1, label = "Pick how long, then pause", done = false)
                }

                if (permanentlyDenied && !hasPermission) {
                    Text(
                        text = "Microphone access is blocked for ReclaimLife. Allow it in app settings to record.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text("Open app settings")
                    }
                }
                errorText?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (isRecording) {
                    RecordingIndicator(
                        seconds = recordingSeconds,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = ::onRecordTapped,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            isRecording -> "Stop recording"
                            hasRecording -> "Record again"
                            else -> "Start recording"
                        }
                    )
                }

                if (hasRecording && !isRecording) {
                    OutlinedButton(
                        onClick = { playBack() },
                        enabled = !isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(if (isPlaying) "Playing…" else "Play it back")
                    }
                }

                Text(
                    text = "Tracking turns itself back on after:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PauseDuration.entries.forEach { option ->
                        FilterChip(
                            selected = duration == option,
                            onClick = { duration = option },
                            label = { Text(option.label, fontSize = 12.sp) }
                        )
                    }
                }
                if (requiredRounds >= 2) {
                    Text(
                        text = "The rest of today costs more: two separate recordings, both played back.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirmPause(duration) },
                        enabled = roundsCompleted >= requiredRounds
                    ) {
                        Text("Pause · ${duration.label}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(number: Int, label: String, done: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .semantics { contentDescription = "Step $number, $label, " + if (done) "done" else "not done" }
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (done) "✓" else "$number",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun RecordingIndicator(seconds: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording-pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording-alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(dotAlpha)
                .background(Color.Red, CircleShape)
        )
        val mm = seconds / 60
        val ss = seconds % 60
        Text(
            text = "Recording… %d:%02d".format(mm, ss),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
