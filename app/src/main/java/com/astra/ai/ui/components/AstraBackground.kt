package com.astra.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraMagenta
import com.astra.ai.ui.theme.AstraViolet
import kotlin.math.cos
import kotlin.math.sin

private data class AmbientParticle(
    val normalizedX: Float,
    val normalizedY: Float,
    val baseRadius: Float,
    val speedFactor: Float,
    val alphaOffset: Float
)

/**
 * Animated dynamic atmospheric background for Astra AI.
 * Uses lightweight sinusoidal motion for soft ambient glowing blobs and drifting micro-particles.
 * Optimized for high performance and low battery consumption on Android.
 */
@Composable
fun AstraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AstraAmbientAnim")

    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AmbientBlobProgress"
    )

    // Pre-calculated deterministic floating particles (18 particles)
    val particles = remember {
        List(18) { index ->
            AmbientParticle(
                normalizedX = (index * 0.17f + 0.05f) % 1.0f,
                normalizedY = (index * 0.23f + 0.11f) % 1.0f,
                baseRadius = 1.2f + (index % 3) * 0.8f,
                speedFactor = 0.5f + (index % 4) * 0.25f,
                alphaOffset = (index * 0.35f) % 1.0f
            )
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Deep solid background canvas
            drawRect(color = backgroundColor)

            val angle = animationProgress * 2f * Math.PI.toFloat()

            // 2. Ambient Gradient Blob 1 (Electric Cyan glow near top left / center)
            val blob1X = width * (0.35f + 0.18f * cos(angle))
            val blob1Y = height * (0.28f + 0.14f * sin(angle * 0.8f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AstraCyan.copy(alpha = 0.14f),
                        AstraCyan.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(blob1X, blob1Y),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(blob1X, blob1Y)
            )

            // 3. Ambient Gradient Blob 2 (Celestial Violet glow near center right)
            val blob2X = width * (0.65f - 0.20f * sin(angle * 1.1f))
            val blob2Y = height * (0.62f + 0.16f * cos(angle * 0.9f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AstraViolet.copy(alpha = 0.16f),
                        AstraViolet.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(blob2X, blob2Y),
                    radius = width * 0.85f
                ),
                radius = width * 0.85f,
                center = Offset(blob2X, blob2Y)
            )

            // 4. Ambient Gradient Blob 3 (Subtle Magenta / Void glow at bottom)
            val blob3X = width * (0.45f + 0.15f * sin(angle * 0.7f))
            val blob3Y = height * (0.85f - 0.10f * cos(angle * 1.3f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AstraMagenta.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = Offset(blob3X, blob3Y),
                    radius = width * 0.60f
                ),
                radius = width * 0.60f,
                center = Offset(blob3X, blob3Y)
            )

            // 5. Drifting micro-particles
            particles.forEach { p ->
                val particleShiftX = sin(angle * p.speedFactor + p.alphaOffset * 6.28f) * 24f
                val particleShiftY = cos(angle * p.speedFactor * 0.8f + p.alphaOffset * 6.28f) * 36f
                val px = (p.normalizedX * width + particleShiftX).coerceIn(0f, width)
                val py = (p.normalizedY * height + particleShiftY).coerceIn(0f, height)
                val alpha = (0.25f + 0.45f * sin(angle * 2f + p.alphaOffset * 6.28f)).coerceIn(0.1f, 0.7f)

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = p.baseRadius,
                    center = Offset(px, py)
                )
            }
        }

        content()
    }
}
