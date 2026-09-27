package io.github.gobi12b.reclaimlife.ui.replacement

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.JOURNAL_PROMPTS
import io.github.gobi12b.reclaimlife.data.ReplacementActivity
import io.github.gobi12b.reclaimlife.data.SWAP_SECONDS
import kotlinx.coroutines.delay

/**
 * A full-screen 2-minute swap: header, a gentle timer, the activity itself, then [finishLabel]
 * once the time is up. [onSkip] (when given) lets someone leave early — only the "earn an extra"
 * swap omits it, since there the two minutes are the point.
 */
@Composable
fun SwapSession(
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    headline: String,
    subtitle: String,
    finishLabel: String,
    onFinish: () -> Unit,
    onSkip: (() -> Unit)?,
    skipLabel: String = "Skip for now"
) {
    var secondsLeft by remember { mutableIntStateOf(SWAP_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    val done = secondsLeft == 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = activity.emoji,
            fontSize = 36.sp,
            modifier = Modifier.clearAndSetSemantics { }
        )
        Text(
            text = headline,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = subtitle,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        LinearProgressIndicator(
            progress = { 1f - secondsLeft.toFloat() / SWAP_SECONDS },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (done) "2 minutes — nicely done" else "%d:%02d left".format(secondsLeft / 60, secondsLeft % 60),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )

        when (activity) {
            ReplacementActivity.BREATHING -> BreathingExercise()
            ReplacementActivity.FLASHCARDS -> FlashcardDeckView(deck)
            ReplacementActivity.JOURNALING -> JournalPrompt()
        }

        Button(
            onClick = onFinish,
            enabled = done,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text(if (done) finishLabel else "Keep going — almost there")
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp)) {
                Text(skipLabel)
            }
        }
    }
}

/** In for 4, out for 6 — a slower out-breath is what does the calming. Twelve rounds is two minutes. */
@Composable
private fun BreathingExercise() {
    val scale = remember { Animatable(0.55f) }
    var breathingIn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            breathingIn = true
            scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
            breathingIn = false
            scale.animateTo(0.55f, tween(6000, easing = FastOutSlowInEasing))
        }
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        )
        Text(
            text = if (breathingIn) "Breathe in…" else "Breathe out…",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun FlashcardDeckView(deck: FlashcardDeck) {
    val cards = remember(deck) { deck.cards.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val card = cards[index % cards.size]

    Text(deck.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card(
        onClick = { flipped = !flipped },
        colors = CardDefaults.cardColors(
            containerColor = if (flipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(top = 8.dp)
            .semantics { onClick(label = if (flipped) "Show question" else "Show answer") { flipped = !flipped; true } }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .padding(24.dp)
        ) {
            Text(
                text = if (flipped) card.back else card.front,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text(
                text = if (flipped) "Tap to flip back" else "Tap to see the answer",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = "Card ${index % cards.size + 1} of ${cards.size}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = {
            index++
            flipped = false
        }) {
            Text("Next card →")
        }
    }
}

/** Nothing typed here is stored — it's for the writing, not the record. */
@Composable
private fun JournalPrompt() {
    val prompts = remember { JOURNAL_PROMPTS.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }

    Text(
        text = prompts[index % prompts.size],
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Write whatever comes to mind…") },
        minLines = 5,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Just for you — it isn't saved.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Just for you, it isn't saved." }
        )
        TextButton(onClick = { index++ }) { Text("Different prompt") }
    }
}
