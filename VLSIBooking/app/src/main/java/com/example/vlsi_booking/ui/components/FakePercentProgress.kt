package com.example.vlsi_booking.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Determinate progress bar with a "fake" staged progression:
 * 0% -> 10% -> 30% -> 50% -> 60%, then stays at 60% until [active] becomes false.
 * When the work completes, it jumps to 100% and disappears shortly after.
 */
@Composable
fun FakePercentProgressBar(
    active: Boolean,
    modifier: Modifier = Modifier,
    bottomSpacing: Dp = 0.dp,
    showDelayMs: Long = 0L,
    stageDelayMs: Long = 10_000L,
) {
    var visible by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active) {
        if (active) {
            // Don't flash the indicator for quick requests.
            visible = false
            progress = 0f

            if (showDelayMs > 0) {
                delay(showDelayMs)
            }

            if (!isActive) return@LaunchedEffect

            visible = true

            // Staged "fake" progression (slow enough to communicate wake-up time).
            delay(stageDelayMs)
            progress = 0.10f
            delay(stageDelayMs)
                progress = 0.30f
            delay(stageDelayMs)
                progress = 0.50f
                delay(stageDelayMs)
            progress = 0.60f

            // Hold at 60% until the request completes.
            while (isActive) {
                delay(250)
            }
        } else if (visible) {
            // Completion phase: 100% then fade out.
            progress = 1.0f
            delay(350)
            visible = false
            progress = 0f
        }
    }

    if (!visible) return

    Column(modifier = modifier) {
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        if (bottomSpacing.value > 0f) {
            Spacer(Modifier.height(bottomSpacing))
        }
    }
}
