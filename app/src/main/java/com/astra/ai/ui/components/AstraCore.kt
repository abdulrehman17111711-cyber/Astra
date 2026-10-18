package com.astra.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astra.ai.assistant.AssistantState
import com.astra.ai.assistant.AssistantStateType
import com.astra.ai.ui.theme.AstraAmber
import com.astra.ai.ui.theme.AstraCyan
import com.astra.ai.ui.theme.AstraEmerald
import com.astra.ai.ui.theme.AstraMagenta
import com.astra.ai.ui.theme.AstraRed
import com.astra.ai.ui.theme.AstraViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Central "Astra Core" - A futuristic, voice-first intelligent assistant sphere.
 * Incorporates layered radial gradients, multiple glowing rings, state-dependent particle
 * movement, breathing animations, and acoustic soundwaves.
 */
@Composable
fun AstraCore(
    state: AssistantState,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AstraCoreInfinite")

    // Slow organic breathing animation
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingScale"
    )

    // Fast rotation for thinking / executing energy
    val continuousRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CoreRotation"
    )

    // Acoustic wave oscillation for speaking / listening
    val acousticPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AcousticPulse"
    )

    // Target colors based on state
    val targetPrimaryColor = when (state.type) {
        AssistantStateType.IDLE -> AstraCyan
        AssistantStateType.ACTIVATING -> AstraCyan
        AssistantStateType.LISTENING -> AstraCyan
        AssistantStateType.PROCESSING -> AstraViolet
        AssistantStateType.THINKING -> AstraViolet
        AssistantStateType.EXECUTING -> AstraMagenta
        AssistantStateType.SPEAKING -> AstraEmerald
        AssistantStateType.WAITING_FOR_CONFIRMATION -> AstraAmber
        AssistantStateType.SUCCESS -> AstraEmerald
        AssistantStateType.ERROR -> AstraRed
    }

    val targetSecondaryColor = when (state.type) {
        AssistantStateType.IDLE -> AstraViolet
        AssistantStateType.ACTIVATING -> Color.White
        AssistantStateType.LISTENING -> Color.White
        AssistantStateType.PROCESSING -> AstraMagenta
        AssistantStateType.THINKING -> AstraMagenta
        AssistantStateType.EXECUTING -> AstraAmber
        AssistantStateType.SPEAKING -> AstraCyan
        AssistantStateType.WAITING_FOR_CONFIRMATION -> Color.White
        AssistantStateType.SUCCESS -> Color.White
        AssistantStateType.ERROR -> AstraAmber
    }

    // Smooth state color transitions
    val primaryColor by animateColorAsState(
        targetValue = targetPrimaryColor,
        animationSpec = tween(durationMillis = 400),
        label = "PrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = targetSecondaryColor,
        animationSpec = tween(durationMillis = 400),
        label = "SecondaryColor"
    )

    // Dynamic scale boost based on audio level in listening mode
    val audioLevel = if (state is AssistantState.Listening) state.audioLevel else 0f
    val dynamicAudioBoost by animateFloatAsState(
        targetValue = audioLevel * 0.35f,
        animationSpec = spring(),
        label = "AudioBoost"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Astra Core: State ${state.type.name}. Tap to interact."
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = size / 2),
                onClick = onCoreClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 3.4f
            val currentScale = (breathingScale + dynamicAudioBoost).coerceIn(0.85f, 1.45f)
            val coreRadius = baseRadius * currentScale

            // 1. Layered Outermost Ambient Atmospheric Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 2.2f
                ),
                radius = coreRadius * 2.2f,
                center = center
            )

            // 2. State-dependent outer elements
            when (state.type) {
                AssistantStateType.ACTIVATING -> {
                    drawListeningSoundRings(
                        center = center,
                        coreRadius = coreRadius * 0.95f,
                        pulseProgress = acousticPulse,
                        primaryColor = primaryColor,
                        audioLevel = 0.25f
                    )
                }

                AssistantStateType.LISTENING -> {
                    drawListeningSoundRings(
                        center = center,
                        coreRadius = coreRadius,
                        pulseProgress = acousticPulse,
                        primaryColor = primaryColor,
                        audioLevel = audioLevel
                    )
                }

                AssistantStateType.PROCESSING -> {
                    drawThinkingEnergyVortex(
                        center = center,
                        coreRadius = coreRadius,
                        rotation = continuousRotation * 1.2f,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                AssistantStateType.THINKING -> {
                    drawThinkingEnergyVortex(
                        center = center,
                        coreRadius = coreRadius,
                        rotation = continuousRotation,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                AssistantStateType.EXECUTING -> {
                    drawExecutingActionRings(
                        center = center,
                        coreRadius = coreRadius,
                        rotation = continuousRotation,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                AssistantStateType.SPEAKING -> {
                    drawSpeakingAcousticWaves(
                        center = center,
                        coreRadius = coreRadius,
                        pulseProgress = acousticPulse,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                AssistantStateType.WAITING_FOR_CONFIRMATION -> {
                    drawConfirmationBeacon(
                        center = center,
                        coreRadius = coreRadius,
                        rotation = continuousRotation * 0.5f,
                        color = primaryColor
                    )
                }

                AssistantStateType.SUCCESS -> {
                    drawSuccessBloom(
                        center = center,
                        coreRadius = coreRadius,
                        color = primaryColor
                    )
                }

                AssistantStateType.ERROR -> {
                    drawErrorHalo(
                        center = center,
                        coreRadius = coreRadius,
                        pulseProgress = acousticPulse,
                        color = primaryColor
                    )
                }

                AssistantStateType.IDLE -> {
                    drawIdleOrbitalRings(
                        center = center,
                        coreRadius = coreRadius,
                        rotation = continuousRotation * 0.4f,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }
            }

            // 3. Middle Resonant Ring (Glass-like border glow)
            drawCircle(
                color = primaryColor.copy(alpha = 0.45f),
                radius = coreRadius * 1.15f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Primary Core Glowing Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryColor,
                        secondaryColor.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.4f)
                    ),
                    center = Offset(center.x - coreRadius * 0.15f, center.y - coreRadius * 0.15f),
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // 5. Specular Holographic Lens Highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - coreRadius * 0.35f, center.y - coreRadius * 0.35f),
                    radius = coreRadius * 0.45f
                ),
                radius = coreRadius * 0.45f,
                center = Offset(center.x - coreRadius * 0.35f, center.y - coreRadius * 0.35f)
            )

            // 6. Subtle Orbiting Micro-Energy Satellites (3 particles around core)
            drawOrbitingSatellites(
                center = center,
                coreRadius = coreRadius,
                rotation = continuousRotation,
                color = primaryColor
            )
        }
    }
}

private fun DrawScope.drawIdleOrbitalRings(
    center: Offset,
    coreRadius: Float,
    rotation: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val ringRadius = coreRadius * 1.35f
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.7f),
                Color.Transparent,
                secondaryColor.copy(alpha = 0.5f),
                Color.Transparent,
                primaryColor.copy(alpha = 0.7f)
            ),
            center = center
        ),
        radius = ringRadius,
        center = center,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), rotation)
        )
    )
}

private fun DrawScope.drawListeningSoundRings(
    center: Offset,
    coreRadius: Float,
    pulseProgress: Float,
    primaryColor: Color,
    audioLevel: Float
) {
    // 3 expanding concentric acoustic waves
    for (i in 0..2) {
        val waveProg = (pulseProgress + i * 0.33f) % 1.0f
        val radius = coreRadius * (1.15f + waveProg * (0.8f + audioLevel))
        val alpha = ((1.0f - waveProg) * 0.6f).coerceIn(0f, 1f)

        drawCircle(
            color = primaryColor.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = (2.5f - waveProg * 1.5f).dp.toPx())
        )
    }
}

private fun DrawScope.drawThinkingEnergyVortex(
    center: Offset,
    coreRadius: Float,
    rotation: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    // Fast clockwise energy ring
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                primaryColor,
                secondaryColor,
                Color.Transparent,
                primaryColor
            ),
            center = center
        ),
        radius = coreRadius * 1.35f,
        center = center,
        style = Stroke(
            width = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), rotation * 2f)
        )
    )

    // Counter-clockwise outer energy strand
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                secondaryColor,
                Color.Transparent,
                primaryColor,
                secondaryColor
            ),
            center = center
        ),
        radius = coreRadius * 1.55f,
        center = center,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 24f), -rotation * 1.5f)
        )
    )
}

private fun DrawScope.drawExecutingActionRings(
    center: Offset,
    coreRadius: Float,
    rotation: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    // Geared progress action ring
    drawCircle(
        color = primaryColor.copy(alpha = 0.7f),
        radius = coreRadius * 1.3f,
        center = center,
        style = Stroke(
            width = 4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), rotation * 1.2f)
        )
    )

    // Outer orbital scanner arc
    drawCircle(
        color = secondaryColor.copy(alpha = 0.5f),
        radius = coreRadius * 1.5f,
        center = center,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 60f), -rotation * 0.8f)
        )
    )
}

private fun DrawScope.drawSpeakingAcousticWaves(
    center: Offset,
    coreRadius: Float,
    pulseProgress: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    for (i in 0..2) {
        val waveProg = (pulseProgress + i * 0.33f) % 1.0f
        val radius = coreRadius * (1.2f + waveProg * 0.65f)
        val alpha = ((1.0f - waveProg) * 0.55f).coerceIn(0f, 1f)

        drawCircle(
            color = if (i % 2 == 0) primaryColor.copy(alpha = alpha) else secondaryColor.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

private fun DrawScope.drawConfirmationBeacon(
    center: Offset,
    coreRadius: Float,
    rotation: Float,
    color: Color
) {
    // Pulsing diamond / caution beacon perimeter
    drawCircle(
        color = color.copy(alpha = 0.7f),
        radius = coreRadius * 1.35f,
        center = center,
        style = Stroke(
            width = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f), rotation)
        )
    )
}

private fun DrawScope.drawSuccessBloom(
    center: Offset,
    coreRadius: Float,
    color: Color
) {
    // Radiant emerald bloom ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.4f),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * 1.8f
        ),
        radius = coreRadius * 1.8f,
        center = center
    )
}

private fun DrawScope.drawErrorHalo(
    center: Offset,
    coreRadius: Float,
    pulseProgress: Float,
    color: Color
) {
    val radius = coreRadius * (1.25f + pulseProgress * 0.3f)
    drawCircle(
        color = color.copy(alpha = (1.0f - pulseProgress) * 0.7f),
        radius = radius,
        center = center,
        style = Stroke(width = 3.dp.toPx())
    )
}

private fun DrawScope.drawOrbitingSatellites(
    center: Offset,
    coreRadius: Float,
    rotation: Float,
    color: Color
) {
    val angles = listOf(
        Math.toRadians(rotation.toDouble()),
        Math.toRadians((rotation + 120f).toDouble()),
        Math.toRadians((rotation + 240f).toDouble())
    )

    angles.forEachIndexed { index, rad ->
        val orbitDist = coreRadius * (1.28f + index * 0.08f)
        val px = center.x + (orbitDist * cos(rad)).toFloat()
        val py = center.y + (orbitDist * sin(rad)).toFloat()

        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = 2.5.dp.toPx(),
            center = Offset(px, py)
        )

        // Subtle glow around satellite
        drawCircle(
            color = color.copy(alpha = 0.45f),
            radius = 6.dp.toPx(),
            center = Offset(px, py)
        )
    }
}
