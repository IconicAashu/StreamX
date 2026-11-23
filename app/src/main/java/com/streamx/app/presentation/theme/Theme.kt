package com.streamx.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class StreamXThemeColors(
    val background: Color,
    val cardSurface: Color,
    val secondarySurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val isDark: Boolean
)

val LightStreamXColors = StreamXThemeColors(
    background = Color(0xFFFFFFFF),
    cardSurface = Color(0xFFFFFFFF),
    secondarySurface = Color(0xFFF5F6F9),
    textPrimary = Color(0xFF121214),
    textSecondary = Color(0xFF86868B),
    border = Color(0xFFE5E5EA),
    isDark = false
)

val DarkStreamXColors = StreamXThemeColors(
    background = Color(0xFF0F0E17),
    cardSurface = Color(0xFF1B1A28),
    secondarySurface = Color(0xFF242234),
    textPrimary = Color(0xFFF4F4F6),
    textSecondary = Color(0xFFA1A1AA),
    border = Color(0xFF2E2C40),
    isDark = true
)

val LocalStreamXColors = staticCompositionLocalOf { LightStreamXColors }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccentRoyal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5F6F9),
    onPrimaryContainer = Color(0xFF121214),
    secondary = PrimaryAccentLight,
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF121214),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121214),
    surfaceVariant = Color(0xFFF5F6F9),
    onSurfaceVariant = Color(0xFF86868B),
    outline = Color(0xFFE5E5EA)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccentRoyal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF242234),
    onPrimaryContainer = Color(0xFFF4F4F6),
    secondary = PrimaryAccentLight,
    onSecondary = Color.White,
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF1B1A28),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF242234),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF2E2C40)
)

@Composable
fun STREAMXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val streamXColors = if (darkTheme) DarkStreamXColors else LightStreamXColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalStreamXColors provides streamXColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StreamXTypography,
            shapes = StreamXShapes,
            content = content
        )
    }
}
