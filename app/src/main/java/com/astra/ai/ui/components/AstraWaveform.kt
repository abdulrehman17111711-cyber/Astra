package com.astra.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astra.ai.ui.theme.AstraCyan
import kotlin.math.sin

/**
 * Animated audio waveform bars for real-time speech feedback in Listening & Speaking states.
 */
@Composable
fun AstraWaveform(
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    baseColor: Color = AstraCyan,
    audioLevel: Float = 0f,
    maxHeight: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnim")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveformPhase"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val offsetFactor = (i - barCount / 2f) / (barCount / 2f)
            val bellCurve = 1.0f - (offsetFactor * offsetFactor * 0.5f)
            val wave = (sin(phase + i * 0.7f) * 0.5f + 0.5f).coerceIn(0.15f, 1f)
            val combinedMultiplier = (wave * 0.6f + audioLevel * 0.8f).coerceIn(0.15f, 1.0f) * bellCurve
            val dynamicHeight = (maxHeight.value * combinedMultiplier).coerceAtLeast(4f).dp

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(dynamicHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(baseColor.copy(alpha = 0.5f + 0.5f * combinedMultiplier))
            )
        }
    }
}
