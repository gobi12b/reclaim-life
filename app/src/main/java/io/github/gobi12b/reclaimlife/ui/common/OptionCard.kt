package io.github.gobi12b.reclaimlife.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.data.FlashcardDeck
import io.github.gobi12b.reclaimlife.data.ReplacementActivity

/** A radio-button card: the whole card is the target, so TalkBack reads "Daily, selected" once. */
@Composable
fun OptionCard(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Choosing the 2-minute swap — shared by onboarding and Home so both read the same. */
@Composable
fun SwapPicker(
    activity: ReplacementActivity,
    deck: FlashcardDeck,
    onActivityChange: (ReplacementActivity) -> Unit,
    onDeckChange: (FlashcardDeck) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            ReplacementActivity.entries.forEach { option ->
                OptionCard(
                    title = "${option.emoji} ${option.label}",
                    description = option.blurb,
                    selected = option == activity,
                    onSelect = { onActivityChange(option) }
                )
            }
        }
        AnimatedVisibility(visible = activity == ReplacementActivity.FLASHCARDS) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text("Deck", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlashcardDeck.entries.forEach { option ->
                        FilterChip(
                            selected = option == deck,
                            onClick = { onDeckChange(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
        }
    }
}
