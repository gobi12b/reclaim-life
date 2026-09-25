package com.example.brainrotkiller.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.data.MAX_DAILY_REEL_LIMIT
import com.example.brainrotkiller.data.MIN_DAILY_REEL_LIMIT
import kotlinx.coroutines.delay

private const val LIMIT_STEP = 5
private val LIMIT_PRESETS = listOf(20, 50, 100, 200)

/** Next multiple of [LIMIT_STEP] above [value] (186 → 190), so stepping lands on round numbers. */
internal fun stepUp(value: Int, ceiling: Int): Int =
    ((value / LIMIT_STEP + 1) * LIMIT_STEP).coerceAtMost(ceiling)

/** Previous multiple of [LIMIT_STEP] below [value] (186 → 185), never under the minimum. */
internal fun stepDown(value: Int): Int =
    (((value - 1) / LIMIT_STEP) * LIMIT_STEP).coerceAtLeast(MIN_DAILY_REEL_LIMIT)

/**
 * Daily-limit picker: −/+ in steps of 5 (hold to repeat), quick presets, and the number itself is
 * typeable. Replaces a 1–1000 slider that was impossible to land on a value with, and whose
 * 1000 end anchored people high. [ceiling] is normally [MAX_DAILY_REEL_LIMIT]; callers pass a
 * higher one to keep an already-saved limit above the cap representable, or a lower one (the
 * current limit, while paused) to allow only lowering. Every input path — steppers, presets,
 * typing — is held to it.
 */
@Composable
fun LimitPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    ceiling: Int = MAX_DAILY_REEL_LIMIT
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            RepeatingStepButton(
                label = "−",
                description = "Decrease limit",
                enabled = value > MIN_DAILY_REEL_LIMIT,
                onStep = { onValueChange(stepDown(value)) }
            )
            LimitNumberField(
                value = value,
                onValueChange = { onValueChange(it.coerceIn(MIN_DAILY_REEL_LIMIT, ceiling)) },
                modifier = Modifier
                    .width(140.dp)
                    .padding(horizontal = 12.dp)
            )
            RepeatingStepButton(
                label = "+",
                description = "Increase limit",
                enabled = value < ceiling,
                onStep = { onValueChange(stepUp(value, ceiling)) }
            )
        }
        Text(
            text = "reels / day",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LIMIT_PRESETS.forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick = { onValueChange(preset) },
                    enabled = preset in MIN_DAILY_REEL_LIMIT..ceiling,
                    label = { Text("$preset") }
                )
            }
        }
    }
}

@Composable
private fun LimitNumberField(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Local text so the field can be briefly empty mid-edit; it snaps back to the real value
    // whenever that changes (steppers, presets, clamping) or the field loses focus.
    var text by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(4)
            text = digits
            digits.toIntOrNull()?.let(onValueChange)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        textStyle = TextStyle(
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .onFocusChanged { if (!it.isFocused) text = value.toString() }
            .semantics { contentDescription = "Daily limit, $value reels. Double tap to type a number." }
    )
}

@Composable
private fun RepeatingStepButton(label: String, description: String, enabled: Boolean, onStep: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        delay(400)
        while (true) {
            repeated = true
            currentOnStep()
            delay(70)
        }
    }

    FilledTonalIconButton(
        onClick = {
            // A hold already stepped on its own; the release click shouldn't add one more.
            if (!repeated) currentOnStep()
            repeated = false
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description }
    ) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}
