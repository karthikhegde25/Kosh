package com.kosh.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Restrained neutrals with a single blue accent taken from the Kosh logo.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF001A43),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE1E5EE),
    onSurfaceVariant = Color(0xFF444852),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF0F46B8),
    onPrimaryContainer = Color(0xFFD9E4FF),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE3E5EB),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE3E5EB),
    surfaceVariant = Color(0xFF2A2D36),
    onSurfaceVariant = Color(0xFFC3C6D0),
)

@Composable
fun KoshTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
