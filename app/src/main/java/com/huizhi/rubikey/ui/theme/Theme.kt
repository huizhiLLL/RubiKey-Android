package com.huizhi.rubikey.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RubiKeyLightColorScheme = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4B4B4B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF4B4B4B),
    outline = Color(0xFFD1D1D1),
    error = Color(0xFF9B2C2C),
    onError = Color(0xFFFFFFFF),
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
