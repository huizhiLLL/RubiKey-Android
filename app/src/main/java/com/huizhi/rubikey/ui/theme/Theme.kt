package com.huizhi.rubikey.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF171717)
private val InkMuted = Color(0xFF4B4B4B)
private val Paper = Color(0xFFFFFFFF)
private val SurfaceLow = Color(0xFFFAFAFA)
private val SurfaceBase = Color(0xFFF5F5F5)
private val SurfaceHigh = Color(0xFFF1F1F1)
private val SurfaceHighest = Color(0xFFECECEC)

private val RubiKeyLightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = SurfaceHighest,
    onPrimaryContainer = Ink,
    inversePrimary = Paper,
    secondary = InkMuted,
    onSecondary = Paper,
    secondaryContainer = SurfaceHighest,
    onSecondaryContainer = Ink,
    tertiary = InkMuted,
    onTertiary = Paper,
    tertiaryContainer = SurfaceHighest,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = InkMuted,
    surfaceDim = Color(0xFFE0E0E0),
    surfaceBright = Paper,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceBase,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    inverseSurface = Color(0xFF2B2B2B),
    inverseOnSurface = Color(0xFFF4F4F4),
    outline = Color(0xFFD1D1D1),
    outlineVariant = Color(0xFFE4E4E4),
    scrim = Color(0xFF000000),
    error = Color(0xFF9B2C2C),
    onError = Paper,
    errorContainer = Color(0xFFF9E5E5),
    onErrorContainer = Color(0xFF5C1515),
)

@Composable
fun RubiKeyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RubiKeyLightColorScheme,
        content = content,
    )
}
