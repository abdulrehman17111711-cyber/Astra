package com.astra.ai.ui.screens.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.ai.ui.components.AstraBackground
import com.astra.ai.ui.theme.AstraCyan
import kotlinx.coroutines.delay

/**
 * Beautiful, futuristic Astra AI splash animation with kinetic orbital rings,
 * dynamic audio equalizer pulse, and seamless auto-transition into the main interface.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLaunched by remember { mutableStateOf(false) }

    val entranceScale by animateFloatAsState(
        targetValue = if (isLaunched) 1f else 0.70f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "SplashEntranceScale"
    )

    val entranceAlpha by animateFloatAsState(
        targetValue = if (isLaunched) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "SplashEntranceAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "SplashOrbitalAnim")

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SplashOrbitRotation"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SplashCorePulse"
    )

    LaunchedEffect(Unit) {
        isLaunched = true
        delay(1150L) // Quick, seamless transition without sluggish waiting
        onSplashComplete()
    }

    AstraBackground(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(entranceScale)
                    .alpha(entranceAlpha)
            ) {
                // Beautiful Futuristic Glowing Neural Orb
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer rotating kinetic ring
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(orbitAngle)
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    AstraCyan.copy(alpha = 0.8f),
                                    Color(0xFF00FFA3),
                                    Color.Transparent
                                )
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Counter-rotating inner secondary ring
                    Canvas(
                        modifier = Modifier
                            .size(140.dp)
                            .rotate(-orbitAngle * 1.4f)
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF7A00FF).copy(alpha = 0.6f),
                                    Color.Transparent,
                                    Color(0xFFFF007F).copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            ),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Breathing Center Glowing Core
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(corePulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AstraCyan,
                                        Color(0xFF0072FF),
                                        Color(0xFF050B14)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Astra AI",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "A S T R A",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 8.sp,
                    fontSize = 30.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "INTELLIGENT VOICE SYSTEM",
                    style = MaterialTheme.typography.labelMedium,
                    color = AstraCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
