package com.astra.ai.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Dark Theme Void & Surface Palette
val AstraBackgroundDark = Color(0xFF050811)
val AstraBackgroundDarkVariant = Color(0xFF090D1A)
val AstraSurfaceDark = Color(0xFF0D1424)
val AstraSurfaceVariantDark = Color(0xFF152035)

// Glassmorphic Surface & Border Colors
val AstraGlassSurfaceDark = Color(0x22131D33)
val AstraGlassSurfaceHoverDark = Color(0x351F2E4F)
val AstraGlassBorderDark = Color(0x2600E5FF)
val AstraGlassBorderSubtle = Color(0x1AFFFFFF)

// Accent Glowing Cyber Colors
val AstraCyan = Color(0xFF00E5FF)
val AstraCyanGlow = Color(0x6600E5FF)
val AstraCyanDim = Color(0xFF008394)

val AstraViolet = Color(0xFF8B5CF6)
val AstraVioletGlow = Color(0x668B5CF6)
val AstraVioletDim = Color(0xFF5B21B6)

val AstraMagenta = Color(0xFFEC4899)
val AstraMagentaGlow = Color(0x66EC4899)

val AstraEmerald = Color(0xFF10B981)
val AstraEmeraldGlow = Color(0x6610B981)

val AstraAmber = Color(0xFFF59E0B)
val AstraAmberGlow = Color(0x66F59E0B)

val AstraRed = Color(0xFFEF4444)
val AstraRedGlow = Color(0x66EF4444)

// High-contrast clean typography colors
val AstraTextPrimary = Color(0xFFF8FAFC)
val AstraTextSecondary = Color(0xFF94A3B8)
val AstraTextTertiary = Color(0xFF64748B)

// Light Theme Palette
val AstraBackgroundLight = Color(0xFFF4F6FB)
val AstraSurfaceLight = Color(0xFFFFFFFF)
val AstraSurfaceVariantLight = Color(0xFFE2E8F0)
val AstraGlassSurfaceLight = Color(0xCCFFFFFF)
val AstraGlassBorderLight = Color(0x1F008394)
val AstraTextPrimaryLight = Color(0xFF0F172A)
val AstraTextSecondaryLight = Color(0xFF475569)

// Multi-color Gradients
val AstraCoreIdleGradient = Brush.radialGradient(
    colors = listOf(AstraCyan, AstraViolet, Color(0xFF091226))
)

val AstraCoreListeningGradient = Brush.radialGradient(
    colors = listOf(Color.White, AstraCyan, AstraViolet)
)

val AstraCoreThinkingGradient = Brush.radialGradient(
    colors = listOf(AstraViolet, AstraMagenta, AstraCyanDim)
)

val AstraCoreSpeakingGradient = Brush.radialGradient(
    colors = listOf(Color.White, AstraEmerald, AstraCyan)
)

val AstraGlassGradient = Brush.linearGradient(
    colors = listOf(
        Color(0x30FFFFFF),
        Color(0x0AFFFFFF),
        Color(0x05FFFFFF)
    )
)
