package com.example.brainrotkiller.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlinx.coroutines.delay

private const val PAUSE_PHRASE = "I want to rot my brain"

/**
 * Pausing shouldn't be a one-tap accident. Before it takes effect, they have to record themselves
 * saying it out loud and listen to the playback — hearing your own voice say something makes it
 * land differently than reading it.
 */
@Composable
fun PauseRecordDialog(onDismiss: () -> Unit, onConfirmPause: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val audioFile = remember { File(context.cacheDir, "pause_confirm.3gp") }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasPlayedBack by remember { mutableStateOf(false) }
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
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Say it out loud", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Record yourself saying: “$PAUSE_PHRASE” — then play it back. " +
                        "Your future self will regret this.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                )

                if (!hasPermission) {
                    Text(
                        text = "Microphone permission is needed to record.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
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
                    onClick = { if (isRecording) stopRecording() else startRecording() },
                    enabled = hasPermission,
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

                Row(modifier = Modifier.padding(top = 16.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirmPause,
                        enabled = hasPlayedBack
                    ) {
                        Text("Pause anyway")
                    }
                }
            }
        }
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
