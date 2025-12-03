package com.powerschedule.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Gradient Colors
val GradientStart = Color(0xFFB8E0E8)
val GradientMiddle = Color(0xFFC0E5DB)
val GradientEnd = Color(0xFFC8E6D5)

// Card Colors
val CardBackground = Color.White.copy(alpha = 0.85f)
val CardFooterBackground = Color(0xFFA8E6CF).copy(alpha = 0.5f)

// Status Colors
val StatusGreen = Color(0xFF4CAF50)
val StatusRed = Color(0xFFF44336)
val StatusRedLight = Color(0xFFFF5252)

// Text Colors
val TextPrimary = Color.Black
val TextSecondary = Color.Black.copy(alpha = 0.6f)
val TextTertiary = Color.Black.copy(alpha = 0.4f)

// Other
val NotificationOrange = Color(0xFFFF9500)

private val LightColorScheme = lightColorScheme(
    primary = StatusGreen,
    secondary = GradientStart,
    background = GradientStart,
    surface = CardBackground,
    onPrimary = Color.White,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusRed
)

@Composable
fun PowerScheduleTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}