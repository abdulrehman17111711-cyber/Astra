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
import androidx.compose.ui.graphics.Path
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data model for dynamic glittering particles that twinkle and drift
 * around the fluid holographic nebula.
 */
private data class GlitterParticle(
    val baseAngle: Float,
    val distanceFactor: Float,
    val size: Float,
    val speedMultiplier: Float,
    val colorIndex: Int,
    val twinkleOffset: Float
)

/**
 * The Central "Astra Core" - Inspired by Tecno's flagship Ella Assistant.
 *
 * Replaces simple spinning circles with a vibrant, glittering, multi-color
 * holographic fluid morphing nebula:
 * - Multi-color iridescent aurora gradients (Neon Cyan, Electric Purple, Sunset Magenta, Solar Gold).
 * - Organic, sinusoidal liquid contour that morphs organically and reacts to voice amplitude.
 * - 32 drifting glittering starry sparkles that twinkle with fluid particle physics.
 * - Pulsing acoustic aura rings that expand smoothly during speech and listening.
 * - Specular crystalline glass iris highlight.
 */
@Composable
fun AstraCore(
    state: AssistantState,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TecnoEllaCoreTransition")

    // Fluid liquid contour morphing phases
    val fluidPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FluidMorphPhase1"
    )

    val fluidPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -(2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FluidMorphPhase2"
    )

    // Breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingPulse"
    )

    // Glitter twinkle cycle
    val glitterTwinkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GlitterTwinkle"
    )

    // Acoustic wave expansion for voice reactivity
    val acousticWaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AcousticWave"
    )

    // Core dynamic palette inspired by Tecno Ella
    val cyanGlow = Color(0xFF00F5FF)
    val purpleGlow = Color(0xFF9D00FF)
    val magentaGlow = Color(0xFFFF007F)
    val goldGlow = Color(0xFFFFD700)
    val emeraldGlow = Color(0xFF00FFA3)
    val coralRed = Color(0xFFFF3366)

    val (primaryColor, secondaryColor, accentColor) = when (state.type) {
        AssistantStateType.IDLE -> Triple(cyanGlow, purpleGlow, magentaGlow)
        AssistantStateType.ACTIVATING -> Triple(cyanGlow, magentaGlow, goldGlow)
        AssistantStateType.LISTENING -> Triple(cyanGlow, emeraldGlow, magentaGlow)
        AssistantStateType.PROCESSING -> Triple(purpleGlow, magentaGlow, cyanGlow)
        AssistantStateType.THINKING -> Triple(purpleGlow, goldGlow, magentaGlow)
        AssistantStateType.EXECUTING -> Triple(magentaGlow, goldGlow, cyanGlow)
        AssistantStateType.SPEAKING -> Triple(emeraldGlow, cyanGlow, goldGlow)
        AssistantStateType.WAITING_FOR_CONFIRMATION -> Triple(goldGlow, magentaGlow, Color.White)
        AssistantStateType.SUCCESS -> Triple(emeraldGlow, cyanGlow, Color.White)
        AssistantStateType.ERROR -> Triple(coralRed, goldGlow, magentaGlow)
    }

    val animatedPrimary by animateColorAsState(primaryColor, tween(400), label = "Primary")
    val animatedSecondary by animateColorAsState(secondaryColor, tween(400), label = "Secondary")
    val animatedAccent by animateColorAsState(accentColor, tween(400), label = "Accent")

    // Audio boost reacting to user or TTS speech
    val audioLevel = when (state) {
        is AssistantState.Listening -> state.audioLevel
        is AssistantState.Speaking -> 0.65f
        else -> 0f
    }
    val animatedAudioBoost by animateFloatAsState(
        targetValue = audioLevel * 0.4f,
        animationSpec = spring(),
        label = "AudioBoost"
    )

    // 32 deterministic glittering particles
    val particles = remember {
        List(32) { index ->
            val angle = (index * (360f / 32f) * (PI / 180f)).toFloat()
            val dist = 1.15f + (index % 5) * 0.12f
            val pSize = 1.8f + (index % 4) * 1.2f
            val speed = 0.6f + (index % 3) * 0.4f
            val colorIdx = index % 4
            val offset = (index * 0.23f) % 1.0f
            GlitterParticle(angle, dist, pSize, speed, colorIdx, offset)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Astra AI Core: ${state.type.name}. Tap to talk."
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
            val baseRadius = (this.size.minDimension / 3.2f) * (breathingPulse + animatedAudioBoost)

            // 1. Outermost Multi-Color Aurora Atmospheric Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimary.copy(alpha = 0.42f),
                        animatedSecondary.copy(alpha = 0.28f),
                        animatedAccent.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.1f
                ),
                radius = baseRadius * 2.1f,
                center = center
            )

            // 2. Sound-Reactive Expanding Acoustic Aurora Ripples
            if (state is AssistantState.Listening || state is AssistantState.Speaking || state is AssistantState.Activating) {
                drawAcousticAuraRipples(
                    center = center,
                    baseRadius = baseRadius,
                    progress = acousticWaveProgress,
                    primary = animatedPrimary,
                    secondary = animatedAccent,
                    audioLevel = audioLevel
                )
            }

            // 3. Glittering Starry Particles that Twinkle and Orbit
            drawGlitterParticles(
                center = center,
                baseRadius = baseRadius,
                particles = particles,
                twinkleProgress = glitterTwinkle,
                phase = fluidPhase1,
                primary = animatedPrimary,
                secondary = animatedSecondary,
                accent = animatedAccent,
                gold = goldGlow
            )

            // 4. Fluid Organic Liquid Waveform Core (Morphing Sinusoidal Blob)
            drawFluidNebulaBlob(
                center = center,
                baseRadius = baseRadius,
                phase1 = fluidPhase1,
                phase2 = fluidPhase2,
                audioBoost = animatedAudioBoost,
                primary = animatedPrimary,
                secondary = animatedSecondary,
                accent = animatedAccent
            )

            // 5. Secondary Iridescent Chromatic Border
            drawFluidNebulaBorder(
                center = center,
                baseRadius = baseRadius * 1.02f,
                phase1 = fluidPhase1 * 1.2f,
                phase2 = fluidPhase2 * 0.8f,
                audioBoost = animatedAudioBoost,
                primary = animatedPrimary,
                accent = animatedAccent
            )

            // 6. Crystalline Holographic Specular Iris Highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        animatedPrimary.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - baseRadius * 0.32f, center.y - baseRadius * 0.32f),
                    radius = baseRadius * 0.5f
                ),
                radius = baseRadius * 0.5f,
                center = Offset(center.x - baseRadius * 0.32f, center.y - baseRadius * 0.32f)
            )

            // 7. Center Quantum Glow Spark
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = 3.5.dp.toPx(),
                center = Offset(center.x - baseRadius * 0.28f, center.y - baseRadius * 0.28f)
            )
        }
    }
}

/**
 * Draws the morphing, organic liquid nebula blob using parametric polar coordinates.
 * Gives the assistant a fluid, alive, liquid-light appearance rather than a rigid circle.
 */
private fun DrawScope.drawFluidNebulaBlob(
    center: Offset,
    baseRadius: Float,
    phase1: Float,
    phase2: Float,
    audioBoost: Float,
    primary: Color,
    secondary: Color,
    accent: Color
) {
    val path = Path()
    val steps = 64
    val stepAngle = (2 * PI / steps).toFloat()

    for (i in 0..steps) {
        val theta = i * stepAngle
        // Harmonic sinusoidal liquid deformation
        val deformation = 0.07f * sin(3 * theta + phase1) +
                0.04f * cos(5 * theta - phase2) +
                audioBoost * 0.35f * sin(2 * theta + phase1 * 1.5f)
        val r = baseRadius * (1f + deformation)
        val x = center.x + r * cos(theta)
        val y = center.y + r * sin(theta)

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                primary.copy(alpha = 0.90f),
                secondary.copy(alpha = 0.85f),
                accent.copy(alpha = 0.65f),
                Color(0xFF05081A)
            ),
            center = Offset(center.x - baseRadius * 0.15f, center.y - baseRadius * 0.15f),
            radius = baseRadius * 1.15f
        )
    )
}

/**
 * Draws the chromatic iridescent fluid outer edge.
 */
private fun DrawScope.drawFluidNebulaBorder(
    center: Offset,
    baseRadius: Float,
    phase1: Float,
    phase2: Float,
    audioBoost: Float,
    primary: Color,
    accent: Color
) {
    val path = Path()
    val steps = 64
    val stepAngle = (2 * PI / steps).toFloat()

    for (i in 0..steps) {
        val theta = i * stepAngle
        val deformation = 0.08f * sin(3 * theta + phase1) +
                0.05f * cos(4 * theta + phase2) +
                audioBoost * 0.35f * sin(2 * theta + phase1)
        val r = baseRadius * (1f + deformation)
        val x = center.x + r * cos(theta)
        val y = center.y + r * sin(theta)

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(
        path = path,
        brush = Brush.sweepGradient(
            colors = listOf(
                primary.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.9f),
                accent.copy(alpha = 0.75f),
                Color(0xFFFFD700).copy(alpha = 0.8f),
                primary.copy(alpha = 0.85f)
            ),
            center = center
        ),
        style = Stroke(width = 2.5.dp.toPx())
    )
}

/**
 * Draws expanding acoustic aurora ripples during speech and voice capture.
 */
private fun DrawScope.drawAcousticAuraRipples(
    center: Offset,
    baseRadius: Float,
    progress: Float,
    primary: Color,
    secondary: Color,
    audioLevel: Float
) {
    for (i in 0..2) {
        val p = (progress + i * 0.33f) % 1.0f
        val rippleRadius = baseRadius * (1.1f + p * (0.85f + audioLevel * 0.5f))
        val alpha = ((1.0f - p) * 0.55f).coerceIn(0f, 1f)
        val strokeColor = if (i % 2 == 0) primary else secondary

        drawCircle(
            color = strokeColor.copy(alpha = alpha),
            radius = rippleRadius,
            center = center,
            style = Stroke(width = (3.dp.toPx() * (1f - p * 0.6f)).coerceAtLeast(1f))
        )
    }
}

/**
 * Draws 32 drifting, twinkling starry glitter particles around the core.
 */
private fun DrawScope.drawGlitterParticles(
    center: Offset,
    baseRadius: Float,
    particles: List<GlitterParticle>,
    twinkleProgress: Float,
    phase: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    gold: Color
) {
    val palette = listOf(primary, secondary, accent, gold)

    particles.forEach { p ->
        // Drift angle with speed
        val currentAngle = p.baseAngle + (phase * p.speedMultiplier * 0.3f)
        val dist = baseRadius * (p.distanceFactor + 0.05f * sin(phase * 2f + p.twinkleOffset * 10f))
        val px = center.x + dist * cos(currentAngle)
        val py = center.y + dist * sin(currentAngle)

        // Shimmer / Twinkle intensity
        val twinkle = ((sin((twinkleProgress + p.twinkleOffset) * 2 * PI) + 1f) / 2f).toFloat()
        val particleAlpha = (0.25f + twinkle * 0.75f).coerceIn(0f, 1f)
        val color = palette[p.colorIndex % palette.size]

        // Sparkle core
        drawCircle(
            color = Color.White.copy(alpha = particleAlpha),
            radius = (p.size * (0.7f + twinkle * 0.5f)).dp.toPx(),
            center = Offset(px, py)
        )

        // Outer glitter halo
        drawCircle(
            color = color.copy(alpha = particleAlpha * 0.45f),
            radius = (p.size * 2.8f).dp.toPx(),
            center = Offset(px, py)
        )
    }
}
