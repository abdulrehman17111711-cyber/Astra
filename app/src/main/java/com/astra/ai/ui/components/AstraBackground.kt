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
import androidx.compose.ui.graphics.StrokeCap
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraMagenta
import com.astra.ai.ui.theme.AstraViolet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class DynamicParticle(
    val normalizedX: Float,
    val normalizedY: Float,
    val baseRadius: Float,
    val speedFactor: Float,
    val alphaOffset: Float,
    val driftAmplitudeX: Float,
    val driftAmplitudeY: Float,
    val pulseSpeed: Float,
    val isConstellationNode: Boolean
)

/**
 * Polished, fluid dynamic atmospheric background for Astra AI.
 * Features 54 interactive ambient micro-particles, shimmering constellation
 * connection filaments, and continuous multi-phase ambient nebula gradients.
 */
@Composable
fun AstraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AstraFluidBackground")

    // Primary cyclical progress for nebula drift
    val nebulaProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "NebulaDrift"
    )

    // Secondary pulse for particle twinkle and cosmic breath
    val cosmicPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CosmicPulse"
    )

    // Pre-calculate 54 deterministic dynamic particles across the canvas
    val particles = remember {
        List(54) { i ->
            val angleSeed = (i * 137.5f) * (Math.PI / 180.0) // Golden ratio dispersion
            val normX = ((sin(angleSeed) * 0.46 + 0.50).toFloat()).coerceIn(0.04f, 0.96f)
            val normY = ((cos(angleSeed * 1.3) * 0.46 + 0.50).toFloat()).coerceIn(0.04f, 0.96f)
            
            DynamicParticle(
                normalizedX = normX,
                normalizedY = normY,
                baseRadius = 1.0f + (i % 4) * 0.65f,
                speedFactor = 0.4f + (i % 6) * 0.22f,
                alphaOffset = (i * 0.23f) % 1.0f,
                driftAmplitudeX = 16f + (i % 5) * 6f,
                driftAmplitudeY = 22f + (i % 7) * 8f,
                pulseSpeed = 0.8f + (i % 3) * 0.5f,
                isConstellationNode = (i % 3 == 0)
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

            val nebulaAngle = nebulaProgress * 2f * Math.PI.toFloat()
            val pulseAngle = cosmicPulse * 2f * Math.PI.toFloat()

            // 2. Ambient Gradient Blob 1 (Electric Cyan glow near top left / center)
            val blob1X = width * (0.32f + 0.16f * cos(nebulaAngle))
            val blob1Y = height * (0.24f + 0.12f * sin(nebulaAngle * 0.8f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AstraCyan.copy(alpha = 0.15f),
                        AstraCyan.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(blob1X, blob1Y),
                    radius = width * 0.85f
                ),
                radius = width * 0.85f,
                center = Offset(blob1X, blob1Y)
            )

            // 3. Ambient Gradient Blob 2 (Celestial Violet glow near center right)
            val blob2X = width * (0.68f - 0.18f * sin(nebulaAngle * 1.1f))
            val blob2Y = height * (0.58f + 0.14f * cos(nebulaAngle * 0.9f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AstraViolet.copy(alpha = 0.16f),
                        AstraViolet.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(blob2X, blob2Y),
                    radius = width * 0.90f
                ),
                radius = width * 0.90f,
                center = Offset(blob2X, blob2Y)
            )

            // 4. Ambient Gradient Blob 3 (Emerald & Neon Cyan pulse near bottom)
            val blob3X = width * (0.50f + 0.14f * sin(nebulaAngle * 0.7f))
            val blob3Y = height * (0.86f - 0.08f * cos(nebulaAngle * 1.3f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00FFA3).copy(alpha = 0.08f),
                        AstraMagenta.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(blob3X, blob3Y),
                    radius = width * 0.70f
                ),
                radius = width * 0.70f,
                center = Offset(blob3X, blob3Y)
            )

            // 5. Compute real-time positions for 54 particles
            val calculatedPositions = particles.map { p ->
                val shiftX = sin(nebulaAngle * p.speedFactor + p.alphaOffset * 6.28f) * p.driftAmplitudeX
                val shiftY = cos(nebulaAngle * p.speedFactor * 0.85f + p.alphaOffset * 6.28f) * p.driftAmplitudeY
                val px = (p.normalizedX * width + shiftX).coerceIn(0f, width)
                val py = (p.normalizedY * height + shiftY).coerceIn(0f, height)
                
                val alpha = (0.20f + 0.40f * sin(pulseAngle * p.pulseSpeed + p.alphaOffset * 6.28f)).coerceIn(0.08f, 0.75f)
                Triple(Offset(px, py), alpha, p)
            }

            // 6. Draw dynamic constellation lines between nearby node particles
            val thresholdDistance = width * 0.22f
            for (i in 0 until calculatedPositions.size) {
                val nodeA = calculatedPositions[i]
                if (!nodeA.third.isConstellationNode) continue

                for (j in (i + 1) until calculatedPositions.size) {
                    val nodeB = calculatedPositions[j]
                    if (!nodeB.third.isConstellationNode) continue

                    val dx = nodeA.first.x - nodeB.first.x
                    val dy = nodeA.first.y - nodeB.first.y
                    val dist = sqrt(dx * dx + dy * dy)

                    if (dist < thresholdDistance) {
                        val lineAlpha = (1f - dist / thresholdDistance) * 0.18f * nodeA.second
                        drawLine(
                            color = AstraCyan.copy(alpha = lineAlpha),
                            start = nodeA.first,
                            end = nodeB.first,
                            strokeWidth = 1.0f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 7. Render 54 shimmering particles
            calculatedPositions.forEach { (pos, alpha, p) ->
                val particleColor = when {
                    p.isConstellationNode -> AstraCyan.copy(alpha = alpha)
                    p.baseRadius > 2f -> Color(0xFF00FFA3).copy(alpha = alpha * 0.85f)
                    else -> Color.White.copy(alpha = alpha)
                }

                drawCircle(
                    color = particleColor,
                    radius = p.baseRadius,
                    center = pos
                )

                // Soft outer aura on larger key nodes
                if (p.isConstellationNode && alpha > 0.45f) {
                    drawCircle(
                        color = AstraCyan.copy(alpha = alpha * 0.25f),
                        radius = p.baseRadius * 3.2f,
                        center = pos
                    )
                }
            }
        }

        content()
    }
}
