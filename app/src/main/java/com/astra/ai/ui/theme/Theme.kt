package com.astra.ai.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AstraCyan,
    onPrimary = AstraBackgroundDark,
    primaryContainer = AstraCyanDim,
    onPrimaryContainer = AstraTextPrimary,
    secondary = AstraViolet,
    onSecondary = AstraTextPrimary,
    secondaryContainer = AstraVioletDim,
    onSecondaryContainer = AstraTextPrimary,
    tertiary = AstraMagenta,
    onTertiary = AstraTextPrimary,
    background = AstraBackgroundDark,
    onBackground = AstraTextPrimary,
    surface = AstraSurfaceDark,
    onSurface = AstraTextPrimary,
    surfaceVariant = AstraSurfaceVariantDark,
    onSurfaceVariant = AstraTextSecondary,
    outline = AstraGlassBorderDark,
    outlineVariant = AstraGlassBorderSubtle,
    error = AstraRed,
    onError = AstraTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = AstraCyanDim,
    onPrimary = AstraSurfaceLight,
    primaryContainer = AstraCyan,
    onPrimaryContainer = AstraTextPrimaryLight,
    secondary = AstraViolet,
    onSecondary = AstraSurfaceLight,
    background = AstraBackgroundLight,
    onBackground = AstraTextPrimaryLight,
    surface = AstraSurfaceLight,
    onSurface = AstraTextPrimaryLight,
    surfaceVariant = AstraSurfaceVariantLight,
    onSurfaceVariant = AstraTextSecondaryLight,
    outline = AstraGlassBorderLight,
    outlineVariant = AstraGlassBorderSubtle,
    error = AstraRed,
    onError = AstraSurfaceLight
)

@Composable
fun AstraTheme(
    darkTheme: Boolean = true, // Futuristic AI assistant defaults to premium dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
