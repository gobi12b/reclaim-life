package io.github.gobi12b.reclaimlife.ui.limits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.MAX_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.MAX_HOURLY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.MIN_DAILY_REEL_LIMIT
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.ui.common.HOURLY_LIMIT_PRESETS
import io.github.gobi12b.reclaimlife.ui.common.LimitPicker
import io.github.gobi12b.reclaimlife.ui.common.SwapPicker
import io.github.gobi12b.reclaimlife.ui.replacement.SwapSession

/**
 * Editing happens in a sheet so today's count stays in view. There's no lecture on open; the
 * pushback against raising the limit happens once, at Save, only if they actually raise it.
 * [prefill] starts the picker somewhere else — the "Try 10 fewer reels" insight uses it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLimitSheet(
    dailyLimit: Int,
    todayCount: Int,
    isPaused: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    prefill: Int = dailyLimit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingLimit by remember { mutableIntStateOf(prefill.coerceAtLeast(MIN_DAILY_REEL_LIMIT)) }
    var confirmRaise by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Daily limit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "You've watched $todayCount today · current limit $dailyLimit",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            LimitPicker(
                value = pendingLimit,
                onValueChange = { pendingLimit = it },
                // While paused only lowering is allowed: a pause plus a raise is two escape
                // hatches at once.
                ceiling = if (isPaused) dailyLimit else maxOf(MAX_DAILY_REEL_LIMIT, dailyLimit)
            )
            if (isPaused) {
                Text(
                    text = "You can lower your limit while paused, not raise it.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Text(
                text = if (pendingLimit < dailyLimit) {
                    "Nice — smaller numbers get easier after a few days."
                } else {
                    "Go at your own pace — lower it once the current number feels easy."
                },
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    if (pendingLimit > dailyLimit) confirmRaise = true else onSave(pendingLimit)
                }) {
                    Text("Save")
                }
            }
        }
    }

    if (confirmRaise) {
        AlertDialog(
            onDismissRequest = { confirmRaise = false },
            title = { Text("Raise it to $pendingLimit?") },
            text = {
                Text(
                    "That's okay if today needs it. Smaller numbers usually get easier after a few days, " +
                        "so you can always bring it back down."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmRaise = false
                    onDismiss()
                }) {
                    Text("Keep $dailyLimit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmRaise = false
                    onSave(pendingLimit)
                }) {
                    Text("Raise it")
                }
            }
        )
    }
}

/** Same rules as the daily sheet: only lowering while paused, and raising asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHourlyLimitSheet(
    hourlyLimit: Int,
    isPaused: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingLimit by remember { mutableIntStateOf(hourlyLimit) }
    var confirmRaise by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Hourly limit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Counts reels in any rolling 60 minutes. Hit it and reels pause until the " +
                    "hour frees up — or right away after a 2-minute break.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            LimitPicker(
                value = pendingLimit,
                onValueChange = { pendingLimit = it },
                ceiling = if (isPaused) hourlyLimit else maxOf(MAX_HOURLY_REEL_LIMIT, hourlyLimit),
                presets = HOURLY_LIMIT_PRESETS,
                unitLabel = "reels / hour",
                fieldLabel = "Hourly limit"
            )
            if (isPaused) {
                Text(
                    text = "You can lower your limit while paused, not raise it.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Row(modifier = Modifier.padding(top = 16.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    if (pendingLimit > hourlyLimit) confirmRaise = true else onSave(pendingLimit)
                }) {
                    Text("Save")
                }
            }
        }
    }

    if (confirmRaise) {
        AlertDialog(
            onDismissRequest = { confirmRaise = false },
            title = { Text("Raise it to $pendingLimit an hour?") },
            text = {
                Text(
                    "That's okay if it's needed. The hourly limit helps keep one sitting from running long, " +
                        "and you can always bring it back down."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmRaise = false
                    onDismiss()
                }) {
                    Text("Keep $hourlyLimit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmRaise = false
                    onSave(pendingLimit)
                }) {
                    Text("Raise it")
                }
            }
        )
    }
}

/** Changing the swap isn't loosening anything, so it saves without a confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSwapSheet(
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    onDismiss: () -> Unit,
    onSave: (ReplacementActivity, FlashcardDeck) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingActivity by remember { mutableStateOf(activity) }
    var pendingDeck by remember { mutableStateOf(deck) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Your 2-minute swap", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "What you'll get instead of more reels when you reach a limit.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            SwapPicker(
                activity = pendingActivity,
                deck = pendingDeck,
                onActivityChange = { pendingActivity = it },
                onDeckChange = { pendingDeck = it },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.padding(top = 16.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onSave(pendingActivity, pendingDeck) }) { Text("Save") }
            }
        }
    }
}

/**
 * The chosen 2-minute swap, on demand — from "Try it", the insight's "Start a swap", or the Limits
 * screen when blocked. Until now a swap only ever started at a limit.
 */
@Composable
fun SwapDialog(
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    headline: String,
    subtitle: String,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            SwapSession(
                activity = activity,
                deck = deck,
                headline = headline,
                subtitle = subtitle,
                finishLabel = "Done",
                onFinish = onClose,
                onSkip = onClose,
                skipLabel = "Close"
            )
        }
    }
}
